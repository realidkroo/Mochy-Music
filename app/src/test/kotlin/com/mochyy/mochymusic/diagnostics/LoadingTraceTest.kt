package com.mochyy.mochymusic.diagnostics

import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import org.junit.Assert.*
import org.junit.Test

class LoadingTraceTest {
    private class RecordingSink : LoadingTrace.Sink {
        val starts = mutableListOf<Pair<Int, LoadingPhase>>()
        val completions = mutableListOf<LoadingTrace.Completion>()
        override fun begin(id: Int, phase: LoadingPhase) { starts += id to phase }
        override fun end(completion: LoadingTrace.Completion) { completions += completion }
    }

    @Test fun measuresElapsedMonotonicTimeAndReturnsTheBlockValue() {
        val sink = RecordingSink()
        var now = 100L
        val trace = LoadingTrace(true, { now }, sink)
        val result = trace.measure(LoadingPhase.HOME_LOCAL_DATABASE) { now = 160L; 42 }
        assertEquals(42, result)
        val completed = sink.completions.single()
        assertEquals(60L, completed.durationNanos)
        assertEquals(LoadingOutcome.SUCCESS, completed.outcome)
        assertEquals(sink.starts.single().first, completed.id)
        assertEquals(LoadingPhase.HOME_LOCAL_DATABASE, completed.phase)
    }

    @Test fun failureClosesTheSpanAndRethrowsTheSameException() {
        val sink = RecordingSink()
        val trace = LoadingTrace(true, { 100L }, sink)
        val failure = IllegalStateException("sensitive details must never reach the sink")
        try {
            trace.measure(LoadingPhase.HOME_CACHE_RESTORE) { throw failure }
            fail("Expected the operation to fail")
        } catch (caught: IllegalStateException) { assertSame(failure, caught) }
        assertEquals(LoadingOutcome.ERROR, sink.completions.single().outcome)
    }

    @Test fun cancellationClosesTheSpanAndPropagatesCancellation() {
        val sink = RecordingSink()
        val trace = LoadingTrace(true, { 100L }, sink)
        val cancelled = CancellationException("cancelled request details")
        try {
            trace.measure(LoadingPhase.HOME_FEED_REQUEST) { throw cancelled }
            fail("Expected cancellation")
        } catch (caught: CancellationException) { assertSame(cancelled, caught) }
        assertEquals(LoadingOutcome.CANCELLED, sink.completions.single().outcome)
    }

    @Test fun disabledTracingRunsOperationWithoutReadingClockOrTouchingSink() {
        val sink = object : LoadingTrace.Sink {
            override fun begin(id: Int, phase: LoadingPhase) = error("Disabled sink invoked")
            override fun end(completion: LoadingTrace.Completion) = error("Disabled sink invoked")
        }
        val trace = LoadingTrace(false, { error("Disabled clock invoked") }, sink)
        assertEquals(7, trace.measure(LoadingPhase.APP_INITIALIZE) { 7 })
        trace.start(LoadingPhase.IMAGE_REQUEST).finish(LoadingOutcome.ERROR)
    }

    @Test fun resultFailureIsNotReportedAsSuccessfulRequest() {
        val sink = RecordingSink()
        val trace = LoadingTrace(true, { 100L }, sink)
        val failure = IllegalArgumentException("private response")
        val result = trace.measureResult(LoadingPhase.HOME_FEED_REQUEST) { Result.failure<Int>(failure) }
        assertSame(failure, result.exceptionOrNull())
        assertEquals(LoadingOutcome.ERROR, sink.completions.single().outcome)
    }

    @Test fun cancellationWrappedInResultStillPropagatesAndClosesSpan() {
        val sink = RecordingSink()
        val trace = LoadingTrace(true, { 100L }, sink)
        val cancelled = CancellationException("private response")
        try {
            trace.measureResult(LoadingPhase.HOME_FEED_REQUEST) { Result.failure<Int>(cancelled) }
            fail("Expected cancellation")
        } catch (caught: CancellationException) { assertSame(cancelled, caught) }
        assertEquals(LoadingOutcome.CANCELLED, sink.completions.single().outcome)
    }

    @Test fun competingTerminalCallbacksCloseOnceAndKeepTheFirstOutcome() {
        val sink = RecordingSink()
        var now = 10L
        val trace = LoadingTrace(true, { now }, sink)
        val span = trace.start(LoadingPhase.IMAGE_REQUEST)
        now = 40L
        span.finish(LoadingOutcome.SUCCESS, ImageOrigin.MEMORY)
        span.finish(LoadingOutcome.CANCELLED)
        span.finish(LoadingOutcome.ERROR)
        assertEquals(1, sink.completions.size)
        assertEquals(30L, sink.completions.single().durationNanos)
        assertEquals(ImageOrigin.MEMORY, sink.completions.single().origin)
        assertEquals(LoadingOutcome.SUCCESS, sink.completions.single().outcome)
    }

    @Test fun concurrentTerminalCallbacksDoNotDuplicateCompletion() {
        val sink = RecordingSink()
        val trace = LoadingTrace(true, { 100L }, sink)
        val span = trace.start(LoadingPhase.IMAGE_FETCH)
        val start = CountDownLatch(1)
        val threads = List(8) {
            Thread { start.await(); span.finish(LoadingOutcome.CANCELLED) }.apply { start() }
        }
        start.countDown()
        threads.forEach { it.join() }
        assertEquals(1, sink.completions.size)
        assertEquals(LoadingOutcome.CANCELLED, sink.completions.single().outcome)
    }
}

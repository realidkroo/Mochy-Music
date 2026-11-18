package com.mochyy.mochymusic.diagnostics

import android.os.Build
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import com.mochyy.mochymusic.BuildConfig

/** Local diagnostics only. Normal release/debug variants neither read the clock nor emit events. */
object LoadingDiagnostics {
    val enabled: Boolean = BuildConfig.BUILD_TYPE == "profileable"

    val trace = LoadingTrace(enabled, SystemClock::elapsedRealtimeNanos, object : LoadingTrace.Sink {
        override fun begin(id: Int, phase: LoadingPhase) {
            // Async sections survive coroutine dispatcher changes. Older Android versions retain logs.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Trace.beginAsyncSection("MochyLoading/${phase.name}", id)
            }
        }

        override fun end(completion: LoadingTrace.Completion) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Trace.endAsyncSection("MochyLoading/${completion.phase.name}", completion.id)
            }
            // Do not use Timber: profileable is intentionally non-debug and has no DebugTree.
            Log.i("MochyLoading", "phase=${completion.phase.name} id=${completion.id} " +
                "start_ns=${completion.startedNanos} duration_ns=${completion.durationNanos} " +
                "outcome=${completion.outcome.name} origin=${completion.origin.name}")
        }
    })
}

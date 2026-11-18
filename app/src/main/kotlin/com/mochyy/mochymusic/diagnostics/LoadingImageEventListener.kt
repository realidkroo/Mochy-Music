package com.mochyy.mochymusic.diagnostics

import coil3.EventListener
import coil3.decode.DataSource
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult

/** Created only for profileable. Never reads or retains request URLs, keys, or image metadata. */
class LoadingImageEventListener(private val trace: LoadingTrace) : EventListener() {
    // Factory creation precedes lifecycle/size waits, including failures before onStart.
    private val requestSpan = trace.start(LoadingPhase.IMAGE_REQUEST)
    private var fetchSpan: LoadingTrace.Span? = null
    private var decodeSpan: LoadingTrace.Span? = null
    private var origin = ImageOrigin.UNKNOWN

    @Synchronized
    override fun fetchStart(request: ImageRequest, fetcher: Fetcher, options: Options) {
        fetchSpan = trace.start(LoadingPhase.IMAGE_FETCH)
    }

    @Synchronized
    override fun fetchEnd(request: ImageRequest, fetcher: Fetcher, options: Options, result: FetchResult?) {
        origin = when (result) {
            is SourceFetchResult -> result.dataSource.origin()
            is ImageFetchResult -> result.dataSource.origin()
            null -> ImageOrigin.UNKNOWN // a fetcher can decline and let the next fetcher try
        }
        fetchSpan?.finish(origin = origin)
        fetchSpan = null
    }

    @Synchronized
    override fun decodeStart(request: ImageRequest, decoder: Decoder, options: Options) {
        decodeSpan = trace.start(LoadingPhase.IMAGE_DECODE)
    }

    @Synchronized
    override fun decodeEnd(request: ImageRequest, decoder: Decoder, options: Options, result: DecodeResult?) {
        decodeSpan?.finish(origin = origin)
        decodeSpan = null
    }

    override fun onSuccess(request: ImageRequest, result: SuccessResult) =
        finish(LoadingOutcome.SUCCESS, result.dataSource.origin())

    override fun onError(request: ImageRequest, result: ErrorResult) = finish(LoadingOutcome.ERROR)

    override fun onCancel(request: ImageRequest) = finish(LoadingOutcome.CANCELLED)

    @Synchronized
    private fun finish(outcome: LoadingOutcome, resultOrigin: ImageOrigin = origin) {
        fetchSpan?.finish(outcome, resultOrigin)
        decodeSpan?.finish(outcome, resultOrigin)
        fetchSpan = null
        decodeSpan = null
        // Coil has delivered to its target; this does not assert that a frame has drawn the image.
        requestSpan.finish(outcome, resultOrigin)
    }

    private fun DataSource.origin(): ImageOrigin = when (this) {
        DataSource.MEMORY_CACHE -> ImageOrigin.MEMORY_CACHE
        DataSource.MEMORY -> ImageOrigin.MEMORY
        DataSource.DISK -> ImageOrigin.DISK
        DataSource.NETWORK -> ImageOrigin.NETWORK
    }
}

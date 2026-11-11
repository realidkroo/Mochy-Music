package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: String? = null,
    val buildId: String? = null,
    val cronetVersion: String? = null,
    val packageName: String? = null,
    val friendlyName: String? = null,
    val loginSupported: Boolean = false,
    val loginRequired: Boolean = false,
    val useSignatureTimestamp: Boolean = false,
    val isEmbedded: Boolean = false,
    val useWebPoTokens: Boolean = false,
    // where this client's api lives: the tv and visionos apps talk to www.youtube.com, and a request signed for the music origin is refused there
    val apiOrigin: String = ORIGIN_YOUTUBE_MUSIC,
    val apiFormatVersion: String = "1",
    val clientScreen: String? = null,
    val platform: String? = null,
    // mints its own visitor id through visitor_id instead of borrowing the web player's, and gets a fresh one after a bot check
    val ownVisitorId: Boolean = false,
) {
    // the native apps send no origin or referer, a browser-looking header on an app client is one more thing that gives it away
    val isNativeApp: Boolean get() = platform != null

    fun toContext(locale: YouTubeLocale, visitorData: String?, dataSyncId: String?) = Context(
        client = Context.Client(
            clientName = clientName,
            clientVersion = clientVersion,
            clientScreen = clientScreen,
            platform = platform,
            osName = osName,
            osVersion = osVersion,
            deviceMake = deviceMake,
            deviceModel = deviceModel,
            androidSdkVersion = androidSdkVersion,
            gl = locale.gl,
            hl = locale.hl,
            visitorData = visitorData,
            // sent the way newpipe sends it, only for the clients described as fully as newpipe describes them
            utcOffsetMinutes = if (clientScreen != null) 0 else null,
        ),
        user = Context.User(
            onBehalfOfUser = if (loginSupported) dataSyncId else null
        ),
    )

    companion object {
        const val USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        const val ORIGIN_YOUTUBE = "https://www.youtube.com"
        const val ORIGIN_YOUTUBE_MUSIC = "https://music.youtube.com"
        const val REFERER_YOUTUBE_MUSIC = "$ORIGIN_YOUTUBE_MUSIC/"
        const val API_URL_YOUTUBE_MUSIC = "$ORIGIN_YOUTUBE_MUSIC/youtubei/v1/"

        val WEB = YouTubeClient(
            clientName = "WEB",
            clientVersion = "2.20260708.00.00",
            clientId = "1",
            userAgent = USER_AGENT_WEB,
        )

        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20260707.12.00",
            clientId = "67",
            userAgent = USER_AGENT_WEB,
            loginSupported = true,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
        )

        val WEB_CREATOR = YouTubeClient(
            clientName = "WEB_CREATOR",
            clientVersion = "1.20260708.06.00",
            clientId = "62",
            userAgent = USER_AGENT_WEB,
            loginSupported = true,
            loginRequired = true,
            useSignatureTimestamp = true,
        )

        val TVHTML5 = YouTubeClient(
            clientName = "TVHTML5",
            clientVersion = "7.20260707.07.00",
            clientId = "7",
            userAgent = "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 (KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15",
            loginSupported = true,
            loginRequired = true,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
        )

        // the older tv app as a samsung tv, yt-dlp's first client for signed-in users: with the account's cookies it plays age-restricted and premium-only songs with ciphered links and needs no potoken
        val TVHTML5_SAMSUNG = YouTubeClient(
            clientName = "TVHTML5",
            clientVersion = "5.20260707",
            clientId = "7",
            userAgent = "Mozilla/5.0 (SMART-TV; Linux; Tizen 2.4.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/2.4.0 TV Safari/538.1",
            osName = "Tizen",
            osVersion = "2.4.0",
            deviceMake = "Samsung",
            deviceModel = "UKS9800",
            friendlyName = "TV (Samsung)",
            apiOrigin = ORIGIN_YOUTUBE,
            loginSupported = true,
            loginRequired = true,
            useSignatureTimestamp = true,
        )

        // the same older tv app without a make, kept as a second ask because its "the page needs to be reloaded" comes and goes
        val TVHTML5_DOWNGRADED = YouTubeClient(
            clientName = "TVHTML5",
            clientVersion = "5.20260707",
            clientId = "7",
            userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
            friendlyName = "TV (older)",
            apiOrigin = ORIGIN_YOUTUBE,
            loginSupported = true,
            loginRequired = true,
            useSignatureTimestamp = true,
        )

        // youtube's embedded player with the account's cookies: no potoken needed, says no only where the owner turned embedding off
        val WEB_EMBEDDED_PLAYER = YouTubeClient(
            clientName = "WEB_EMBEDDED_PLAYER",
            clientVersion = "2.20260708.00.00",
            clientId = "56",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.5 Safari/605.1.15,gzip(gfe)",
            friendlyName = "Embedded player",
            apiOrigin = ORIGIN_YOUTUBE,
            loginSupported = true,
            loginRequired = true,
            useSignatureTimestamp = true,
            isEmbedded = true,
        )

        val TVHTML5_SIMPLY_EMBEDDED_PLAYER = YouTubeClient(
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            clientId = "85",
            userAgent = "Mozilla/5.0 (PlayStation; PlayStation 4/12.02) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15",
            loginSupported = true,
            loginRequired = false,
            useSignatureTimestamp = true,
            isEmbedded = true,
        )

        val IOS = YouTubeClient(
            clientName = "IOS",
            clientVersion = "21.03.1",
            clientId = "5",
            userAgent = "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)",
            osVersion = "18.2.22C152",
        )

        val MOBILE = YouTubeClient(
            clientName = "ANDROID",
            clientVersion = "21.26.364",
            clientId = "3",
            userAgent = "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip",
            osName = "Android",
            osVersion = "11",
            loginSupported = true,
            useSignatureTimestamp = true
        )

        val ANDROID_NO_SDK = YouTubeClient(
            clientName = "ANDROID",
            clientVersion = "21.26.364",
            clientId = "3",
            userAgent = "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip",
            osName = "Android",
            osVersion = "11",
            friendlyName = "Android No SDK",
            loginSupported = false,
            useSignatureTimestamp = false
        )

        val ANDROID_VR_NO_AUTH = YouTubeClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.61.48",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Oculus Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
            loginSupported = false,
            useSignatureTimestamp = false
        )

        val ANDROID_VR_1_61_48 = YouTubeClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.61.48",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
            osName = "Android",
            osVersion = "12",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = "32",
            buildId = "SQ3A.220605.009.A1",
            cronetVersion = "132.0.6808.3",
            packageName = "com.google.android.apps.youtube.vr.oculus",
            friendlyName = "Android VR 1.61",
            loginSupported = false,
            useSignatureTimestamp = false
        )

        val ANDROID_VR_1_65_10 = YouTubeClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.65.10",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            osName = "Android",
            osVersion = "12L",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = "32",
            buildId = "SQ3A.220605.009.A1",
            packageName = "com.google.android.apps.youtube.vr.oculus",
            friendlyName = "Android VR 1.65",
            loginSupported = false,
            useSignatureTimestamp = false
        )

        val ANDROID_VR_1_43_32 = YouTubeClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.43.32",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.43.32 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)",
            osName = "Android",
            osVersion = "12",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = "32",
            buildId = "SQ3A.220605.009.A1",
            cronetVersion = "107.0.5284.2",
            packageName = "com.google.android.apps.youtube.vr.oculus",
            friendlyName = "Android VR 1.43",
            loginSupported = false,
            useSignatureTimestamp = false
        )

        val ANDROID_CREATOR = YouTubeClient(
            clientName = "ANDROID_CREATOR",
            clientVersion = "25.03.101",
            clientId = "14",
            userAgent = "com.google.android.apps.youtube.creator/25.03.101 (Linux; U; Android 15; en_US; Pixel 9 Pro Fold; Build/AP3A.241005.015.A2; Cronet/132.0.6779.0)",
            osName = "Android",
            osVersion = "15",
            deviceMake = "Google",
            deviceModel = "Pixel 9 Pro Fold",
            androidSdkVersion = "35",
            buildId = "AP3A.241005.015.A2",
            cronetVersion = "132.0.6779.0",
            packageName = "com.google.android.apps.youtube.creator",
            friendlyName = "Android Studio",
            loginSupported = true,
            useSignatureTimestamp = true
        )

        // the youtube app on apple vision pro, described the way newpipe describes it (version, device, os, user agent, www.youtube.com, api format 2, its own visitor id). it used to pose as a desktop safari on music.youtube.com with version 0.1, which is nothing youtube has ever shipped
        val VISIONOS = YouTubeClient(
            clientName = "VISIONOS",
            clientVersion = "1.04",
            clientId = "101",
            userAgent = "com.google.visionos.youtube/1.04(RealityDevice17,1; U; CPU visionOS 26_6_0 like Mac OS X; US)",
            osName = "visionOS",
            osVersion = "26.6.0.23O770",
            deviceMake = "Apple",
            deviceModel = "RealityDevice17,1",
            friendlyName = "visionOS",
            apiOrigin = ORIGIN_YOUTUBE,
            apiFormatVersion = "2",
            clientScreen = "WATCH",
            platform = "MOBILE",
            ownVisitorId = true,
            loginSupported = false,
            useSignatureTimestamp = false
        )

        val IPADOS = YouTubeClient(
            clientName = "IOS",
            clientVersion = "21.03.3",
            clientId = "5",
            userAgent = "com.google.ios.youtube/21.03.3 (iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)",
            osName = "iPadOS",
            osVersion = "17.7.10.21H450",
            deviceMake = "Apple",
            deviceModel = "iPad7,6",
            friendlyName = "iPadOS",
            loginSupported = false,
            useSignatureTimestamp = false,
            packageName = "com.google.ios.youtube"
        )
    }
}

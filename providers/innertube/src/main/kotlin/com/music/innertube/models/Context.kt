package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Context(
    val client: Client,
    val thirdParty: ThirdParty? = null,
    val request: Request = Request(),
    val user: User = User()
) {
    @Serializable
    data class Client(
        val clientName: String,
        val clientVersion: String,
        // the native apps say which screen they're on and that they're a phone-like device, a request without them doesn't look like the app
        val clientScreen: String? = null,
        val platform: String? = null,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val androidSdkVersion: String? = null,
        val gl: String,
        val hl: String,
        val visitorData: String?,
        val utcOffsetMinutes: Int? = null,
    )

    @Serializable
    data class ThirdParty(
        val embedUrl: String,
    )

    @Serializable
    data class Request(
        val internalExperimentFlags: Array<String> = emptyArray(),
        val useSsl: Boolean = true,
    )

    @Serializable
    data class User(
        val lockedSafetyMode: Boolean = false,
        val onBehalfOfUser: String? = null,
    )
}

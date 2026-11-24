// StringUtils.kt

package com.mochyy.mochymusic.utils

import java.math.BigInteger
import java.security.MessageDigest

fun makeTimeString(duration: Long?): String {
    if (duration == null || duration < 0) return ""
    var sec = duration / 1000
    val day = sec / 86400
    sec %= 86400
    val hour = sec / 3600
    sec %= 3600
    val minute = sec / 60
    sec %= 60
    return when {
        day > 0 -> "%d:%02d:%02d:%02d".format(day, hour, minute, sec)
        hour > 0 -> "%d:%02d:%02d".format(hour, minute, sec)
        else -> "%d:%02d".format(minute, sec)
    }
}

fun md5(str: String): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(str.toByteArray())).toString(16).padStart(32, '0')
}

fun joinByBullet(vararg str: String?) =
    str
        .filterNot {
            it.isNullOrEmpty()
        }.joinToString(separator = " • ")

// the id as a youtube video id (11 letters, digits, - or _), null for local files and anything else
fun youtubeIdOrNull(id: String?): String? = id?.takeIf { YouTubeIdPattern.matches(it) }

private val YouTubeIdPattern = Regex("[A-Za-z0-9_-]{11}")

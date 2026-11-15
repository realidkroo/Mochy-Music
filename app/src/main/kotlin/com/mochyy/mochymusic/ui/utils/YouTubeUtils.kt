// YouTubeUtils.kt

package com.mochyy.mochymusic.ui.utils

// built once, because resize() runs for every thumbnail on screen and compiling a regex each call is wasted work
private val YtimgQualityRegex = Regex("(default|mqdefault|hqdefault|sddefault|maxresdefault)\\.jpg")
private val GoogleUserContentRegex = Regex("https://lh\\d\\.googleusercontent\\.com/.*")

fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this

    if (this.contains("i.ytimg.com")) {
        val targetQuality = if (width != null && width >= 1200) "maxresdefault.jpg" else "hqdefault.jpg"
        return this.replace(YtimgQualityRegex, targetQuality)
    }

    if (this.contains("googleusercontent.com") && this.contains("=w")) {
        val baseUrl = this.split("=w")[0]
        val w = width ?: 0
        val h = height ?: width ?: 0

        return "$baseUrl=w$w-h$h-p-l90-rj"
    }

    if (this.contains("yt3.ggpht.com")) {
        // only the size after "=" is swapped: cutting at "-s" too broke every picture whose own id has "-s" in it
        return "${this.substringBefore("=")}=s${width ?: height}"
    }

    GoogleUserContentRegex.matchEntire(this)?.let {
        val w = width ?: 0
        val h = height ?: width ?: 0
        return "${this.split("=")[0]}=w$w-h$h-p-l90-rj"
    }

    return this
}

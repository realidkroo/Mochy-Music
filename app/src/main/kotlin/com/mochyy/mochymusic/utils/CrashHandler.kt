// CrashHandler.kt

package com.mochyy.mochymusic.utils

import android.content.Context
import android.os.Build
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.modules.security.SecretGuard
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class CrashHandler private constructor(
    private val applicationContext: Context
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashLog = sanitizeCrashLog(buildCrashLog(throwable))
            Timber.e("App crashed:\n%s", crashLog)
            // Timber isn't planted in release builds, and the process is killed right after: without
            // this a release crash left nothing behind at all. Log.e is kept by R8.
            android.util.Log.e("MochyCrash", crashLog)
            // and on the phone, because logcat has long moved on by the time a crash gets looked at.
            // Read it back with `adb shell run-as <package> cat files/last_crash.txt`.
            runCatching { java.io.File(applicationContext.filesDir, LAST_CRASH_FILE).writeText(crashLog) }

            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        } catch (e: Exception) {

            Timber.e(e, "Error handling crash")
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildCrashLog(throwable: Throwable): String {
        val stackTrace = StringWriter().apply {
            throwable.printStackTrace(PrintWriter(this))
        }.toString()

        return buildString {
            appendLine("mochy Crash Report")
            appendLine("=".repeat(50))
            appendLine()
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Device: ${Build.MODEL}")
            appendLine("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine()
            appendLine("=".repeat(50))
            appendLine("Stacktrace:")
            appendLine("=".repeat(50))
            appendLine()
            append(stackTrace)
        }
    }

    companion object {
        const val EXTRA_CRASH_LOG = "crash_log"
        const val LAST_CRASH_FILE = "last_crash.txt"

        private val AuthHeaderPattern = Regex(
            """(?i)(Authorization|Proxy-Authorization|X-Mochy-Auth|X-Mochy-Key)\s*[:=]\s*([^\r\n,]+)""",
        )
        private val BearerOrBasicPattern = Regex(
            """(?i)\b(Bearer|Basic|SAPISIDHASH|DeepL-Auth-Key)\s+[A-Za-z0-9._~+/=-]{6,}""",
        )
        private val CookieHeaderPattern = Regex(
            """(?i)\b(Cookie|Set-Cookie)\s*[:=]\s*([^\r\n]+)""",
        )
        private val SensitiveParamPattern = Regex(
            """(?i)\b(SAPISID|PAPISID|HSID|SSID|APISID|LOGIN_INFO|__Secure-[13]P(?:API)?SID(?:CC)?|sp_dc|access_token|refresh_token|client_secret|code_verifier|api[_-]?key|password|passwd|session_?token|media-user-token)\s*[:=]\s*([^\s;&"',}\r\n]+)""",
        )
        private val UrlCredsPattern = Regex("""(https?://)([^:@/\s]+):([^@/\s]+)@""")

        /** Scrubs registered secrets and common credential/cookie/token patterns before logs hit disk or logcat. */
        internal fun sanitizeCrashLog(raw: String): String {
            var out = SecretGuard.redact(raw)
            out = AuthHeaderPattern.replace(out) { "${it.groupValues[1]}: [REDACTED]" }
            out = BearerOrBasicPattern.replace(out) { "${it.groupValues[1]} [REDACTED]" }
            out = CookieHeaderPattern.replace(out) { "${it.groupValues[1]}: [REDACTED]" }
            out = SensitiveParamPattern.replace(out) { "${it.groupValues[1]}=[REDACTED]" }
            out = UrlCredsPattern.replace(out) { "${it.groupValues[1]}[REDACTED]@" }
            return out
        }

        fun install(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            Timber.d("CrashHandler installed")
        }
    }
}

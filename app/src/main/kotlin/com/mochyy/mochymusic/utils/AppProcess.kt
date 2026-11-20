package com.mochyy.mochymusic.utils

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File

internal fun isMainProcess(context: Context): Boolean {
    val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Application.getProcessName()
    } else {
        runCatching { File("/proc/self/cmdline").readText().substringBefore('\u0000').trim() }.getOrNull()
    }
    return name.isNullOrEmpty() || name == context.packageName
}

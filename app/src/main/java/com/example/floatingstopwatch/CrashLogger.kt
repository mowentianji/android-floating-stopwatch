package com.example.floatingstopwatch

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(context, thread, throwable)
            } catch (_: Throwable) {
            } finally {
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.getExternalFilesDir(null), "crash_logs")
        if (!dir.exists()) dir.mkdirs()

        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(dir, "crash_$ts.txt")

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        val content = buildString {
            appendLine("time=$ts")
            appendLine("thread=${thread.name}")
            appendLine("package=${context.packageName}")
            appendLine("appVersion=1.0")
            appendLine("sdkInt=${Build.VERSION.SDK_INT}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("brand=${Build.BRAND}")
            appendLine("fingerprint=${Build.FINGERPRINT}")
            appendLine()
            appendLine(sw.toString())
        }

        file.writeText(content)
    }
}

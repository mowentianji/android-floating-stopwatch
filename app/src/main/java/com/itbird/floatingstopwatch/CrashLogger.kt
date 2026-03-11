package com.itbird.floatingstopwatch

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream
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

    fun recordNow(context: Context, thread: Thread, throwable: Throwable) {
        writeCrash(context, thread, throwable)
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val fileName = "floating_stopwatch_crash_$ts.txt"

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeToDownloadsMediaStore(context, fileName, content)
        } else {
            writeToLegacyDownloads(fileName, content)
        }
    }

    private fun writeToDownloadsMediaStore(context: Context, fileName: String, content: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { output ->
            output.write(content.toByteArray())
            output.flush()
        }
    }

    private fun writeToLegacyDownloads(fileName: String, content: String) {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        File(dir, fileName).writeText(content)
    }
}

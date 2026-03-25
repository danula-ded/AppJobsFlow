package com.github.jobsflow.appjobsflow.logging

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppFileLogger {
    private const val LOG_DIR_NAME = "logs"
    private const val LOG_FILE_NAME = "appjobsflow.log"
    private const val MAX_LOG_FILE_BYTES = 2 * 1024 * 1024 // 2MB
    private val tsFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = Mutex()

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        i("AppFileLogger", "File logger initialized")
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        append("D", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        append("I", tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        append("W", tag, message)
    }

    fun e(tag: String, message: String) {
        Log.e(tag, message)
        append("E", tag, message)
    }

    private fun append(level: String, tag: String, message: String) {
        val context = appContext ?: return
        val safeMessage = message.replace('\n', ' ').trim()
        val line = "${tsFormat.format(Date())} [$level/$tag] $safeMessage\n"

        runBlocking(Dispatchers.IO) {
            lock.withLock {
                try {
                    val file = getLogFile(context)
                    rotateIfNeeded(file)
                    file.appendText(line)
                } catch (_: Exception) {
                    // never crash app because of logging
                }
            }
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (!file.exists() || file.length() <= MAX_LOG_FILE_BYTES) return
        val bytes = file.readBytes()
        val keepTail = bytes.copyOfRange((bytes.size - (MAX_LOG_FILE_BYTES / 2)).coerceAtLeast(0), bytes.size)
        file.writeBytes(keepTail)
    }

    private fun getLogFile(context: Context): File {
        val dir = File(context.filesDir, LOG_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, LOG_FILE_NAME)
        if (!file.exists()) file.createNewFile()
        return file
    }

    fun getLogFilePath(context: Context): String = getLogFile(context).absolutePath

    fun getLogFileSizeBytes(context: Context): Long = getLogFile(context).length()

    fun clearLogFile(context: Context) {
        runBlocking(Dispatchers.IO) {
            lock.withLock {
                val file = getLogFile(context)
                file.writeText("")
            }
        }
        i("AppFileLogger", "Log file cleared")
    }

    fun prepareExportFile(context: Context): File {
        val source = getLogFile(context)
        val exportDir = File(context.cacheDir, LOG_DIR_NAME)
        if (!exportDir.exists()) exportDir.mkdirs()
        val exportName = "appjobsflow-log-${System.currentTimeMillis()}.txt"
        val out = File(exportDir, exportName)
        source.copyTo(out, overwrite = true)
        return out
    }
}

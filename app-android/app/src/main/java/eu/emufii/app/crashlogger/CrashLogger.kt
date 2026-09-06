package eu.emufii.app.crashlogger

import android.content.Context
import android.util.Log
import eu.emufii.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CrashLog(
    val fileName: String,
    val timestamp: Long,
    val content: String,
    val preview: String
)

/**
 * Catches every uncaught exception, writes it to filesDir/crashes/, and hands it back to
 * the platform handler so Android still shows the crash dialog and closes the process.
 * Keeps the ten most recent reports and nothing older.
 */
object CrashLogger {
    private const val TAG = "CrashLogger"
    private const val CRASH_DIR = "crashes"
    private const val MAX_CRASH_FILES = 10

    private lateinit var defaultHandler: Thread.UncaughtExceptionHandler
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
        defaultHandler = requireNotNull(Thread.getDefaultUncaughtExceptionHandler()) {
            "Default exception handler cannot be null"
        }

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                logCrash(throwable, thread)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log crash", e)
            }
            defaultHandler.uncaughtException(thread, throwable)
        }
    }

    private fun logCrash(throwable: Throwable, thread: Thread) {
        val timestamp = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        val fileName = "crash_${dateFormat.format(Date(timestamp))}.txt"

        val crashDir = File(appContext.filesDir, CRASH_DIR)
        if (!crashDir.exists()) crashDir.mkdirs()

        val crashFile = File(crashDir, fileName)

        try {
            val stackTrace = getStackTraceString(throwable)
            val crashReport = buildCrashReport(timestamp, thread, stackTrace)
            crashFile.writeText(crashReport)
            cleanupOldCrashes(crashDir)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash log", e)
        }
    }

    private fun buildCrashReport(timestamp: Long, thread: Thread, stackTrace: String): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return buildString {
            appendLine("=== CRASH REPORT ===")
            appendLine("Timestamp: ${dateFormat.format(Date(timestamp))}")
            appendLine("App: ${appContext.getString(R.string.app_name)}")
            appendLine("Version: ${getAppVersion()}")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine()
            appendLine("=== STACK TRACE ===")
            appendLine(stackTrace)
        }
    }

    private fun getStackTraceString(throwable: Throwable): String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        throwable.printStackTrace(printWriter)
        return stringWriter.toString()
    }

    private fun getAppVersion(): String = try {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "Unknown"
    } catch (_: Exception) {
        "Unknown"
    }

    private fun cleanupOldCrashes(crashDir: File) {
        val crashFiles = crashDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        if (crashFiles.size > MAX_CRASH_FILES) {
            crashFiles.drop(MAX_CRASH_FILES).forEach { it.delete() }
        }
    }

    suspend fun getCrashLogs(): List<CrashLog> = withContext(Dispatchers.IO) {
        val crashDir = File(appContext.filesDir, CRASH_DIR)
        if (!crashDir.exists()) return@withContext emptyList()

        val crashFiles = crashDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

        crashFiles.mapNotNull { file ->
            try {
                val content = file.readText()
                CrashLog(
                    fileName = file.name,
                    timestamp = file.lastModified(),
                    content = content,
                    preview = extractPreview(content)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read crash file: ${file.name}", e)
                null
            }
        }
    }

    private fun extractPreview(content: String): String {
        val lines = content.lines()
        val stackTraceIndex = lines.indexOfFirst { it.contains("=== STACK TRACE ===") }
        if (stackTraceIndex != -1 && stackTraceIndex + 1 < lines.size) {
            val firstErrorLine = lines[stackTraceIndex + 1]
            return firstErrorLine.take(100) + if (firstErrorLine.length > 100) "..." else ""
        }
        return ""
    }

    suspend fun deleteCrashLog(fileName: String) = withContext(Dispatchers.IO) {
        val file = File(File(appContext.filesDir, CRASH_DIR), fileName)
        if (file.exists()) file.delete()
    }

    suspend fun clearAllCrashLogs() = withContext(Dispatchers.IO) {
        File(appContext.filesDir, CRASH_DIR).listFiles()?.forEach { it.delete() }
    }
}

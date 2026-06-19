package com.alqasrhall.booking

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom Application class for the Circular Palace Hall Booking App.
 * Configures the global uncaught exception handler to prevent silent crashes and
 * display an interactive recovery/debug reporting interface instead.
 */
class MyApplication : Application() {

    override fun onCreate() {
        Log.e("APP_TRACE", "MyApplication Started")
        super.onCreate()
        
        Log.e("APP_TRACE", "Installing Crash Handler")
        // Setup global Exception Handler to intercept any unhandled thread errors/exceptions.
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Log the crash internally first with CRASH_HANDLER tag.
                Log.e("CRASH_HANDLER", "Global Crash Intercepted in thread [${thread.name}]", throwable)
                
                // Write detailed crash log files to local private folder context.filesDir/crash_logs/
                val logFile = saveCrashLog(this, thread, throwable)
                
                // Set up alarm recovery to launch CrashActivity safely (Android 15 compliant)
                val intent = Intent(this, CrashActivity::class.java).apply {
                    putExtra("crash_file_path", logFile?.absolutePath)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                
                Log.e("CRASH_HANDLER", "Launching CrashActivity")
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    99999,
                    intent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )

                // Schedule the alarm to trigger after 500 milliseconds (giving current process time to die)
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 500, pendingIntent)
                Log.e("CRASH_HANDLER", "CrashActivity launch intent sent successfully")
                
            } catch (e: Throwable) {
                Log.e("CRASH_HANDLER", "Error during crash serialization", e)
            } finally {
                Log.e("CRASH_HANDLER", "Delegating to default uncaught exception handler instead of killing process")
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
        Log.e("APP_TRACE", "Crash Handler Installed")
    }

    /**
     * Gathers environment attributes, serializes stack trace, and stores the details 
     * in a standard JSON format inside the private application files directory.
     */
    private fun saveCrashLog(context: Context, thread: Thread, throwable: Throwable): File? {
        return try {
            val crashLogsDir = File(context.filesDir, "crash_logs")
            if (!crashLogsDir.exists()) {
                crashLogsDir.mkdirs()
            }

            // Extract traceback string representation
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stackTraceStr = sw.toString()

            // Gather timestamp elements
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSS", Locale.getDefault())
            val timeString = dateFormat.format(Date())

            // Compile JSON object with comprehensive details
            val crashJson = JSONObject().apply {
                put("exception_type", throwable.javaClass.name)
                put("exception_message", throwable.message ?: "No error message provided.")
                put("exception_cause", throwable.cause?.toString() ?: "No nested cause element.")
                put("thread_name", thread.name)
                put("timestamp", timeString)
                
                // Device hardware fingerprints
                put("device_brand", Build.BRAND)
                put("device_model", Build.MODEL)
                put("device_manufacturer", Build.MANUFACTURER)
                put("device_board", Build.BOARD)
                put("device_product", Build.PRODUCT)
                put("device_hardware", Build.HARDWARE)
                
                // Operating System details
                put("android_release", Build.VERSION.RELEASE)
                put("android_sdk", Build.VERSION.SDK_INT)
                put("app_package", context.packageName)
                
                // Full serialized traceback string
                put("stack_trace", stackTraceStr)
            }

            // Persistence
            val logFile = File(crashLogsDir, "latest_crash.json")
            FileWriter(logFile).use { writer ->
                writer.write(crashJson.toString())
            }
            
            // Also store a timestamped historical file
            val historyFile = File(crashLogsDir, "crash_${System.currentTimeMillis()}.json")
            FileWriter(historyFile).use { writer ->
                writer.write(crashJson.toString())
            }

            logFile
        } catch (e: Exception) {
            Log.e("MyApplication", "Failed to save crash log file", e)
            null
        }
    }
}

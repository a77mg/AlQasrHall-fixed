package com.alqasrhall.booking

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alqasrhall.booking.ui.theme.MyApplicationTheme
import org.json.JSONObject
import java.io.File

/**
 * Custom Crash Activity that presents an elegant error reporting screen.
 * Avoids silent app closures and helps administrators/developers debug issues quickly.
 */
class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge immersive style
        enableEdgeToEdge()

        // Read and parse crash log info from stored file
        val filePath = intent.getStringExtra("crash_file_path")
        val crashData = parseCrashFile(filePath)

        setContent {
            // Re-use our app's visual theme (forces luxury dark background and crimson highlights)
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CrashScreen(
                        crashInfo = crashData,
                        onCopy = { copyToClipboard(crashData) },
                        onShare = { shareCrashReport(crashData) },
                        onRestart = { restartApp() }
                    )
                }
            }
        }
    }

    /**
     * Reads the written JSON file payload and converts it into a structural Map representation.
     */
    private fun parseCrashFile(filePath: String?): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val file = if (!filePath.isNullOrBlank()) File(filePath) else null
            val resolvedFile = if (file != null && file.exists()) {
                file
            } else {
                // Fallback to latest JSON file if unspecified or missing
                val dir = File(filesDir, "crash_logs")
                dir.listFiles()?.filter { it.extension == "json" }
                    ?.maxByOrNull { it.lastModified() }
            }

            if (resolvedFile != null && resolvedFile.exists()) {
                val content = resolvedFile.readText()
                val json = JSONObject(content)
                
                // Parse attributes
                result["exception_type"] = json.optString("exception_type", "UnknownException")
                result["exception_message"] = json.optString("exception_message", "No message details.")
                result["exception_cause"] = json.optString("exception_cause", "None detected.")
                result["thread_name"] = json.optString("thread_name", "main")
                result["timestamp"] = json.optString("timestamp", "N/A")
                result["device_brand"] = json.optString("device_brand", "Unknown")
                result["device_model"] = json.optString("device_model", "Unknown")
                result["android_release"] = json.optString("android_release", "Unknown")
                result["android_sdk"] = json.optString("android_sdk", "Unknown")
                result["stack_trace"] = json.optString("stack_trace", "No trace available.")
            } else {
                result["exception_type"] = "UnknownCrash"
                result["exception_message"] = "Crucial crash state dump file was not resolved."
                result["stack_trace"] = "Null StackTrace State."
            }
        } catch (e: Exception) {
            result["exception_type"] = "ExceptionInCrashReporter"
            result["exception_message"] = "Could not parse crash log directory: ${e.message}"
            result["stack_trace"] = e.stackTraceToString()
        }
        return result
    }

    /**
     * Copy the formatted error log to clipboard.
     */
    private fun copyToClipboard(data: Map<String, String>) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val rawLog = buildRawReport(data)
            val clip = ClipData.newPlainText("صالة القصر - تعطل التطبيق", rawLog)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "تم نسخ تقرير التعطل إلى الحافظة بنجاح", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "فشل في نسخ التقرير", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Share the error report using Android's standard share sheets.
     */
    private fun shareCrashReport(data: Map<String, String>) {
        try {
            val raw = buildRawReport(data)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "تقرير تعطل تطبيق صالة القصر الدائري")
                putExtra(Intent.EXTRA_TEXT, raw)
            }
            startActivity(Intent.createChooser(intent, "إرسال التقرير عبر:"))
        } catch (e: Exception) {
            Toast.makeText(this, "فشل في مشاركة التقرير", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Securely restarts the main flow of the booking application.
     */
    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    /**
     * Builds a comprehensive textual report suitable for developer channels & copy-paste.
     */
    private fun buildRawReport(data: Map<String, String>): String {
        return """
            --- REPORT FOR BOOKING HALL CRITICAL CRASH ---
            Timestamp: ${data["timestamp"]}
            Exception Type: ${data["exception_type"]}
            Message: ${data["exception_message"]}
            Cause: ${data["exception_cause"]}
            Thread: ${data["thread_name"]}
            
            [SYSTEM DETAILS]
            Brand: ${data["device_brand"]}
            Model: ${data["device_model"]}
            Android Version: Build ${data["android_release"]} (SDK ${data["android_sdk"]})
            
            [STACK TRACE]
            ${data["stack_trace"]}
            ---------------------------------------------
        """.trimIndent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashScreen(
    crashInfo: Map<String, String>,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRestart: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "تفاصيل تعطل النظام",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Visual indicators & Error Icons
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "أيقونة حدوث خطأ",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(45.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bilingual warning message
            Text(
                text = "عذراً، حدث خطأ غير متوقع!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "لقد منعت أنظمة الأمان الانهيار الكامل وتأمين قاعدة البيانات والملفات المسجلة بنجاح.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            // Critical Exception Details Container
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Exception Type
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "نوع الاستثناء / Exception:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = crashInfo["exception_type"] ?: "N/A",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.5f)
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Message Details
                    Text(
                        text = "تفاصيل الخطأ / Error Message:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = crashInfo["exception_message"] ?: "No details available.",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Diagnostic info
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("منصة الجوال / Device", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            Text("${crashInfo["device_model"]} (${crashInfo["device_brand"]})", fontSize = 11.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("إصدار أندرويد / Android OS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            Text("Build ${crashInfo["android_release"]} (API ${crashInfo["android_sdk"]})", fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Recovery Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Secondary Button for Clipboard Copy
                OutlinedButton(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "نسخ التقرير")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("نسخ التقرير", fontSize = 12.sp)
                }

                // Secondary Button for Sharing
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "مشاركة")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مشاركة", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary System Restart Button
            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(imageVector = Icons.Default.RestartAlt, contentDescription = "تشغيل التطبيق من جديد")
                Spacer(modifier = Modifier.width(8.dp))
                Text("إعادة تشغيل التطبيق", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Detailed Traceback header
            Text(
                text = "السجل البرمجي المفصل (أرجو تزويد التقني به):",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Interactive Monospace stacktrace field with scrolling/horizontal scaling support
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 500.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                val traceScroll = rememberScrollState()
                Text(
                    text = crashInfo["stack_trace"] ?: "No stack trace recorded.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFFEF9A9A),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(traceScroll)
                )
            }
        }
    }
}

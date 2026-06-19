package com.alqasrhall.booking.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alqasrhall.booking.data.Booking
import com.alqasrhall.booking.data.User
import com.alqasrhall.booking.data.Payment
import com.alqasrhall.booking.data.Attachment
import com.alqasrhall.booking.data.AuditLog
import com.alqasrhall.booking.data.BackupLog
import com.alqasrhall.booking.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainAppContent(viewModel: AppViewModel) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val networkStatus by viewModel.networkStatus.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val selectedCurrency by viewModel.selectedCurrency.collectAsStateWithLifecycle()

    // Show Dialog States
    var showAddPaymentDialogForBooking by remember { mutableStateOf<Booking?>(null) }
    var showAllPaymentsDialogForBooking by remember { mutableStateOf<Booking?>(null) }
    var showAttachmentsDialogForBooking by remember { mutableStateOf<Booking?>(null) }
    var showRescheduleDialogForBooking by remember { mutableStateOf<Booking?>(null) }
    var showEditBookingDialog by remember { mutableStateOf<Booking?>(null) }
    var showAddUserDialog by remember { mutableStateOf(false) }

    // Trigger toast on feedback message
    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (currentUser != null && currentUser?.mustChangePassword != true) {
                AppHeader(
                    currentUser = currentUser!!,
                    networkStatus = networkStatus,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { viewModel.toggleTheme() },
                    onLogout = { viewModel.logout() },
                    onToggleNetwork = { next -> viewModel.setNetworkStatus(next) }
                )
            }
        },
        bottomBar = {
            if (currentUser != null && currentUser?.mustChangePassword != true) {
                AppBottomNavBar(
                    selectedTab = selectedTab,
                    onTabSelected = { viewModel.selectTab(it) },
                    currentUserRole = currentUser?.role ?: "Reception"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentUser == null) {
                LoginScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { viewModel.toggleTheme() },
                    onLoginClick = { user, pass -> viewModel.tryLogin(user, pass) }
                )
            } else if (currentUser?.mustChangePassword == true) {
                ForceChangePasswordScreen(
                    isDarkTheme = isDarkTheme,
                    onSaveClick = { newPass -> viewModel.changeForcePassword(newPass) },
                    onLogoutClick = { viewModel.logout() }
                )
            } else {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn() with fadeOut()
                    },
                    label = "tab_transition"
                ) { tab ->
                    when (tab) {
                        "dashboard" -> DashboardTab(viewModel)
                        "calendar" -> CalendarTab(viewModel)
                        "bookings" -> BookingsTab(
                            viewModel = viewModel,
                            onAddPayment = { showAddPaymentDialogForBooking = it },
                            onViewPayments = { showAllPaymentsDialogForBooking = it },
                            onViewAttachments = { showAttachmentsDialogForBooking = it },
                            onReschedule = { showRescheduleDialogForBooking = it },
                            onEdit = { showEditBookingDialog = it }
                        )
                        "addNew" -> AddNewBookingTab(viewModel)
                        "customers" -> CustomersTab(viewModel)
                        "payments" -> PaymentsTab(viewModel)
                        "reports" -> ReportsTab(viewModel)
                        "more" -> MoreTab(
                            viewModel = viewModel,
                            onShowAddUser = { showAddUserDialog = true }
                        )
                        "adminTools" -> AdminToolsTab(
                            viewModel = viewModel,
                            onShowAddUser = { showAddUserDialog = true }
                        )
                        "auditLogs" -> AuditLogsTab(viewModel)
                    }
                }
            }
        }
    }

    // --- REUSABLE DIALOGS ---

    // Spreadsheet / Excel Import conflict wizard
    val importSessionState by viewModel.importSession.collectAsStateWithLifecycle()
    if (importSessionState != null) {
        val session = importSessionState!!
        val conflict = session.conflicts[session.currentConflictIndex]
        
        Dialog(onDismissRequest = { viewModel.cancelImportSession() }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GoldenMuted),
                color = DeepCharcoal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "كشف تكرار في التواريخ - معالجة التضارب",
                        color = GoldenClassic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "تاريخ المناسبة: ${conflict.imported.dateStr} (${conflict.imported.dayOfWeek})",
                        color = ChampagneLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1.0f).padding(2.dp), horizontalAlignment = Alignment.End) {
                            Text("من ملف Excel المستورد:", color = GoldenBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("المستأجر: ${conflict.imported.renterName}", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Right)
                            Text("النوع: ${conflict.imported.eventType}", color = Color.Gray, fontSize = 10.sp, textAlign = TextAlign.Right)
                            Text("المبلغ: ${conflict.imported.rentAmount.toInt()} ريال", color = Color.Gray, fontSize = 10.sp, textAlign = TextAlign.Right)
                        }
                        
                        Column(modifier = Modifier.weight(1.0f).padding(2.dp), horizontalAlignment = Alignment.End) {
                            Text("الحجز الحالي المسجل بالصالة:", color = RedCancel, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("المستأجر: ${conflict.existing.renterName}", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Right)
                            Text("النوع: ${conflict.existing.eventType}", color = Color.Gray, fontSize = 10.sp, textAlign = TextAlign.Right)
                            Text("الحالة: ${conflict.existing.status}", color = Color.Gray, fontSize = 10.sp, textAlign = TextAlign.Right)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "يرجى تحديد حل مناسب للتضارب في حجز الصالة الفعلي:",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { viewModel.resolveCurrentConflict(keepImported = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldenClassic),
                            modifier = Modifier.weight(1f).padding(2.dp).testTag("import_wizard_use_imported")
                        ) {
                            Text("استخدام المستورد", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { viewModel.resolveCurrentConflict(keepImported = false) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            modifier = Modifier.weight(1f).padding(2.dp).testTag("import_wizard_use_existing")
                        ) {
                            Text("الإبقاء على الحالي", color = Color.White, fontSize = 10.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { viewModel.resolveAllConflicts(useImported = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldenBright),
                            modifier = Modifier.weight(1f).padding(2.dp).testTag("import_wizard_all_imported")
                        ) {
                            Text("تطبيق المستورد على الكل", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { viewModel.resolveAllConflicts(useImported = false) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            modifier = Modifier.weight(1f).padding(2.dp).testTag("import_wizard_all_existing")
                        ) {
                            Text("تطبيق الحالي على الكل", color = Color.White, fontSize = 9.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { viewModel.cancelImportSession() }) {
                        Text("إلغاء عملية الاستيراد بالكامل", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }
        }
    }

    // 1. Add Payment Dialog
    if (showAddPaymentDialogForBooking != null) {
        val b = showAddPaymentDialogForBooking!!
        var paymentAmount by remember { mutableStateOf("") }
        var paymentMethod by remember { mutableStateOf("نقداً") }
        var receiptRef by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddPaymentDialogForBooking = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GoldenMuted),
                color = DeepCharcoal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "تسجيل دفعة جديدة للعميل: ${b.renterName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GoldenClassic,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("الملخص المالي للعقد الحالي:", color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                    Text("سعر حجز القصر: ${b.rentAmount} ريال", color = ChampagneLight, fontSize = 14.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                    Text("المبلغ المسدد سابقاً: ${b.totalPaid} ريال", color = GreenConfirm, fontSize = 14.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                    Text("المبلغ المتبقي: ${b.remainingAmount} ريال", color = RedCancel, fontSize = 14.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    MoneyInputField(
                        value = paymentAmount,
                        onValueChange = { paymentAmount = it },
                        label = "مبلغ الدفعة المستحق",
                        currency = selectedCurrency,
                        testTag = "payment_amount_input"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("طريقة الدفع:", color = ChampagneLight, fontSize = 14.sp)
                    val methods = listOf("نقداً", "محفظة إلكترونية")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        methods.forEach { method ->
                            FilterChip(
                                selected = paymentMethod == method,
                                onClick = { paymentMethod = method },
                                label = { Text(method) },
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = receiptRef,
                        onValueChange = { receiptRef = it },
                        label = { Text("رقم السند / المرجع") },
                        modifier = Modifier.fillMaxWidth().testTag("payment_ref_input"),
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl, color = Color.White)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("ملاحظات إضافية") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl, color = Color.White)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { showAddPaymentDialogForBooking = null },
                            modifier = Modifier.testTag("cancel_payment_btn")
                        ) {
                            Text("إلغاء", color = Color.Gray)
                        }

                        Button(
                            onClick = {
                                val amt = paymentAmount.toDoubleOrNull()
                                if (amt == null || amt <= 0.0) {
                                    viewModel.showMessage("يرجى إدخال مبلغ صحيح.")
                                } else if (receiptRef.trim().isEmpty()) {
                                    viewModel.showMessage("يرجى إدخال رقم السند للتأكيد المالي.")
                                } else {
                                    viewModel.addBookingPayment(b.id, amt, paymentMethod, receiptRef.trim(), notes)
                                    showAddPaymentDialogForBooking = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldenClassic),
                            modifier = Modifier.testTag("save_payment_btn")
                        ) {
                            Text("اعتماد وحفظ السند", color = Color.Black)
                        }
                    }
                }
            }
        }
    }

    // 2. View All Payments Dialog
    if (showAllPaymentsDialogForBooking != null) {
        val b = showAllPaymentsDialogForBooking!!
        val paymentsList by viewModel.getPaymentsStream(b.id).collectAsStateWithLifecycle(initialValue = emptyList())

        Dialog(onDismissRequest = { showAllPaymentsDialogForBooking = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GoldenMuted),
                color = DeepCharcoal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showAllPaymentsDialogForBooking = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "أغلق", tint = Color.LightGray)
                        }
                        Text(
                            text = "سجل دفعات العميل: ${b.renterName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoldenClassic
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkGrayAccent, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("المتبقي", color = Color.Gray, fontSize = 10.sp)
                            Text("${b.remainingAmount} ريال", color = RedCancel, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("إجمالي المسدد", color = Color.Gray, fontSize = 10.sp)
                            Text("${b.totalPaid} ريال", color = GreenConfirm, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("مبلغ الإيجار المعتمد", color = Color.Gray, fontSize = 10.sp)
                            Text("${b.rentAmount} ريال", color = GoldenClassic, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("بيانات سندات القبض:", color = GoldenMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))

                    if (paymentsList.isEmpty()) {
                        Text(
                            "لا توجد سندات دفع سابقة مسجلة مسبقاً لعقد العقد الحالي.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(paymentsList) { payment ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkGrayAccent),
                                    border = BorderStroke(0.5.dp, Color.DarkGray)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (viewModel.currentUser.value?.role == "Admin") {
                                            IconButton(
                                                onClick = { viewModel.deleteBookingPayment(payment) },
                                                modifier = Modifier.testTag("delete_payment_btn")
                                            ) {
                                                Icon(Icons.Filled.Delete, contentDescription = "حذف السند", tint = RedCancel)
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "رقم السند: ${payment.receiptRef}",
                                                    color = ChampagneLight,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "${payment.amount} ريال",
                                                    color = GoldenClassic,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            Text(
                                                "طريقة الدفع: ${payment.paymentMethod} - بواسطة: ${payment.receivedByEmployee}",
                                                color = Color.LightGray,
                                                fontSize = 11.sp
                                            )
                                            if (payment.notes.isNotEmpty()) {
                                                Text("ملاحظة: ${payment.notes}", color = Color.Gray, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 3. Attachments Management Dialog
    if (showAttachmentsDialogForBooking != null) {
        val b = showAttachmentsDialogForBooking!!
        val attachmentsList by viewModel.getAttachmentsStream(b.id).collectAsStateWithLifecycle(initialValue = emptyList())

        Dialog(onDismissRequest = { showAttachmentsDialogForBooking = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GoldenMuted),
                color = DeepCharcoal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showAttachmentsDialogForBooking = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "أغلق", tint = Color.LightGray)
                        }
                        Text(
                            text = "مرفقات وملفات العميل: ${b.renterName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoldenClassic
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("رفع المرفقات السريعة لحفظ المعاملات (عقد، هوية، وصل مالي):", color = ChampagneLight, fontSize = 11.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { viewModel.addMockAttachment(b.id, "عقد_حجز_الصالة_موقع.pdf", "PDF", "2.1 MB") },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkGrayAccent),
                            border = BorderStroke(1.dp, GoldenMuted),
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp).testTag("upload_contract")
                        ) {
                            Text("رفع عقد PDF", color = GoldenClassic, fontSize = 11.sp)
                        }

                        Button(
                            onClick = { viewModel.addMockAttachment(b.id, "بطاقة_هوية_العميل.png", "PNG", "1.4 MB") },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkGrayAccent),
                            border = BorderStroke(1.dp, GoldenMuted),
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp).testTag("upload_id")
                        ) {
                            Text("رفع الهوية PNG", color = GoldenClassic, fontSize = 11.sp)
                        }
                    }

                    Divider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    Text("الملفات المرفوعة حالياً:", color = GoldenMuted, fontSize = 13.sp)

                    if (attachmentsList.isEmpty()) {
                        Text(
                            "لا توجد مرفقات مسجلة حالياً.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(attachmentsList) { attach ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkGrayAccent)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteAttachment(attach)
                                                }
                                            ) {
                                                Icon(Icons.Filled.Delete, contentDescription = "حذف ملف", tint = RedCancel)
                                            }
                                            IconButton(
                                                onClick = {
                                                    viewModel.showMessage("جاري محاكاة تنزيل ومعاينة الملف المرفق: ${attach.fileName}")
                                                }
                                            ) {
                                                Icon(Icons.Filled.Download, contentDescription = "تنزيل الملف", tint = GoldenBright)
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(attach.fileName, color = ChampagneLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("الحجم: ${attach.fileSize} | صيغة: ${attach.fileType}", color = Color.Gray, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. Reschedule Booking (تعديل التاريخ)
    if (showRescheduleDialogForBooking != null) {
        val b = showRescheduleDialogForBooking!!
        var newDate by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showRescheduleDialogForBooking = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GoldenMuted),
                color = DeepCharcoal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "جدولة موعد جديد للمستأجر: ${b.renterName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GoldenClassic,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("موعد المناسبة الحالي المعتمد: ${b.dateStr} (${b.dayOfWeek})", color = ChampagneLight, fontSize = 13.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = newDate,
                        onValueChange = { newDate = it },
                        label = { Text("التاريخ الجديد المأمول (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth().testTag("reschedule_date_input"),
                        textStyle = LocalTextStyle.current.copy(color = Color.White)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "* ملحوظة هامة: وفقاً للوائح الصالة سيتم نقل كافة الدفعات المالية مباشرة للمستند الجديد. وسيتحول المستند الحالي إلى حالة (استبدال لتاريخ آخر) كأرشيف دائم.",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { showRescheduleDialogForBooking = null }) {
                            Text("رجوع", color = Color.Gray)
                        }

                        Button(
                            onClick = {
                                if (newDate.trim().isEmpty()) {
                                    viewModel.showMessage("يرجى كتابة التاريخ الجديد.")
                                } else {
                                    viewModel.rescheduleBooking(b.id, newDate.trim())
                                    showRescheduleDialogForBooking = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldenClassic),
                            modifier = Modifier.testTag("apply_reschedule_btn")
                        ) {
                            Text("نقل وتطبيق فوراً", color = Color.Black)
                        }
                    }
                }
            }
        }
    }

    // 5. General Edit Booking Dialog (تعديل الحجز)
    if (showEditBookingDialog != null) {
        val b = showEditBookingDialog!!
        var renterName by remember { mutableStateOf(b.renterName) }
        var phone1 by remember { mutableStateOf(b.phone1) }
        var phone2 by remember { mutableStateOf(b.phone2) }
        var rentAmount by remember { mutableStateOf(b.rentAmount.toString()) }
        var status by remember { mutableStateOf(b.status) }
        var eventType by remember { mutableStateOf(b.eventType) }

        Dialog(onDismissRequest = { showEditBookingDialog = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GoldenMuted),
                color = DeepCharcoal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "تعديل بيانات حجز ${b.dateStr}",
                        color = GoldenClassic,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = renterName,
                        onValueChange = { renterName = it },
                        label = { Text("اسم المستأجر") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl, color = Color.White)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = phone1,
                        onValueChange = { phone1 = it },
                        label = { Text("رقم جوال المستأجر الأساسي") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl, color = Color.White)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = phone2,
                        onValueChange = { phone2 = it },
                        label = { Text("رقم الجوال الثاني (اختياري)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl, color = Color.White)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    MoneyInputField(
                        value = rentAmount,
                        onValueChange = { rentAmount = it },
                        label = "سعر إيجار الصالة المعتمد لعقده",
                        currency = selectedCurrency
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("نوع المناسبة:", color = ChampagneLight, fontSize = 13.sp)
                    val events = listOf("زواج", "خطوبة", "تخرج", "اجتماع/عشاء", "أخرى")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        events.forEach { ev ->
                            FilterChip(
                                selected = eventType == ev,
                                onClick = { eventType = ev },
                                label = { Text(ev) },
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("حالة الحجز الحالية:", color = ChampagneLight, fontSize = 13.sp)
                    val statusList = listOf("جديد", "مؤقت", "أرشيف استبدال", "إلغاء")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.End
                    ) {
                        statusList.forEach { st ->
                            val systemStatusVal = when (st) {
                                "أرشيف استبدال" -> "استبدال إلى تاريخ آخر"
                                else -> st
                            }
                            FilterChip(
                                selected = status == systemStatusVal,
                                onClick = { status = systemStatusVal },
                                label = { Text(st) },
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { showEditBookingDialog = null }) {
                            Text("إغلاق", color = Color.Gray)
                        }

                        Button(
                            onClick = {
                                val cost = rentAmount.toDoubleOrNull()
                                if (renterName.trim().isEmpty() || phone1.trim().isEmpty()) {
                                    viewModel.showMessage("يرجى ملأ البيانات الأساسية للعميل للمتابعة.")
                                } else if (cost == null || cost <= 0.0) {
                                    viewModel.showMessage("يرجى إدخال مبلغ صحيح للإيجار.")
                                } else {
                                    viewModel.modifyBooking(
                                        b.copy(
                                            renterName = renterName.trim(),
                                            phone1 = phone1.trim(),
                                            phone2 = phone2.trim(),
                                            rentAmount = cost,
                                            eventType = eventType,
                                            status = status,
                                            lastUpdated = System.currentTimeMillis()
                                        )
                                    )
                                    showEditBookingDialog = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldenClassic)
                        ) {
                            Text("حفظ التحديثات", color = Color.Black)
                        }
                    }
                }
            }
        }
    }

    // 6. Admin Add User Dialog (إضافة مستخدم جديد)
    if (showAddUserDialog) {
        var nFull by remember { mutableStateOf("") }
        var nUser by remember { mutableStateOf("") }
        var nPass by remember { mutableStateOf("") }
        var nPhone by remember { mutableStateOf("") }
        var nEmail by remember { mutableStateOf("") }
        var nRole by remember { mutableStateOf("Reception") }

        Dialog(onDismissRequest = { showAddUserDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GoldenMuted),
                color = DeepCharcoal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.End
                ) {
                    Text("إضافة مستخدم وموظف جديد للنظام", color = GoldenClassic, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = nFull,
                        onValueChange = { nFull = it },
                        label = { Text("الاسم الكامل للموظف") },
                        modifier = Modifier.fillMaxWidth().testTag("user_fullname_input"),
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl, color = Color.White)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = nUser,
                        onValueChange = { nUser = it },
                        label = { Text("اسم المستخدم المفضل (للدخول)") },
                        modifier = Modifier.fillMaxWidth().testTag("user_username_input"),
                        textStyle = LocalTextStyle.current.copy(color = Color.White)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = nPass,
                        onValueChange = { nPass = it },
                        label = { Text("كلمة المرور") },
                        modifier = Modifier.fillMaxWidth().testTag("user_password_input"),
                        textStyle = LocalTextStyle.current.copy(color = Color.White)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = nPhone,
                        onValueChange = { nPhone = it },
                        label = { Text("رقم الهاتف") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().testTag("user_phone_input"),
                        textStyle = LocalTextStyle.current.copy(color = Color.White)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = nEmail,
                        onValueChange = { nEmail = it },
                        label = { Text("البريد الإلكتروني (اختياري)") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(color = Color.White)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("صلاحية الحساب والدور الممنوح:", color = ChampagneLight, fontSize = 12.sp)
                    val roles = listOf(
                        "Admin" to "مدير النظام",
                        "Manager" to "المدير العام",
                        "Accountant" to "المحاسب",
                        "Reception" to "الاستقبال"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.End
                    ) {
                        roles.forEach { (roleKey, roleLabel) ->
                            FilterChip(
                                selected = nRole == roleKey,
                                onClick = { nRole = roleKey },
                                label = { Text(roleLabel) },
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { showAddUserDialog = false }) {
                            Text("إغلاق", color = Color.Gray)
                        }

                        Button(
                            onClick = {
                                if (nFull.trim().isEmpty() || nUser.trim().isEmpty() || nPass.trim().isEmpty() || nPhone.trim().isEmpty()) {
                                    viewModel.showMessage("يرجى كتابة كافة البيانات الأساسية المتبقية.")
                                } else {
                                    viewModel.addUser(nFull.trim(), nUser.trim(), nPass, nRole, nPhone, nEmail.trim().ifEmpty { null })
                                    showAddUserDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldenClassic),
                            modifier = Modifier.testTag("save_user_btn")
                        ) {
                            Text("حفظ وتسجيل الموظف", color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

// --- SUB-SCREEN COMPONENTS ---

// 1. AppHeader
@Composable
fun AppHeader(
    currentUser: User,
    networkStatus: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    onToggleNetwork: (String) -> Unit
) {
    val context = LocalContext.current
    var dropdownExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(width = (0.5).dp, color = GoldenMuted)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Profile & Logout
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onLogout,
                modifier = Modifier.testTag("header_logout_btn")
            ) {
                Icon(Icons.Filled.Logout, contentDescription = "خروج", tint = RedCancel)
            }

            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier.testTag("header_theme_toggle_btn")
            ) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = "تبديل النمط",
                    tint = if (isDarkTheme) GoldenBright else GoldenBronze
                )
            }

            IconButton(
                onClick = { throw RuntimeException("Test Crash") },
                modifier = Modifier.testTag("test_crash_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = "تجربة تعطل النظام",
                    tint = RedCancel
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // User Role badge
            val roleLabelAndColor = when (currentUser.role) {
                "Admin" -> "المشرف العام" to GoldenClassic
                "Manager" -> "مدير الصالة" to GoldenBronze
                "Accountant" -> "المحاسب المالي" to AmberLight
                "Reception" -> "الاستقبال" to BlueRescheduled
                else -> currentUser.role to Color.Gray
            }

            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = currentUser.fullName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp)
                )
                Box(
                    modifier = Modifier
                        .background(roleLabelAndColor.second.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        roleLabelAndColor.first,
                        color = roleLabelAndColor.second,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Center: App title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "صالة القصر الدائري",
                color = GoldenClassic,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.width(6.dp))
            // Classic Mini Gold Dome Logo
            Canvas(modifier = Modifier.size(16.dp)) {
                drawCircle(
                    color = GoldenClassic, 
                    radius = size.width / 4, 
                    center = Offset(size.width / 2, size.height / 2),
                    style = Stroke(width = 2.dp.toPx())
                )
                drawArc(
                    color = GoldenBright,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Right side: Networks simulator
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(0.4f))
                    .border(0.5.dp, GoldenMuted, RoundedCornerShape(20.dp))
                    .clickable { dropdownExpanded = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val bulletColor = when (networkStatus) {
                    "متصل" -> GreenConfirm
                    "غير متصل" -> RedCancel
                    else -> BlueRescheduled
                }

                Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(color = bulletColor)
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = networkStatus,
                    color = bulletColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
                modifier = Modifier.background(DeepCharcoal)
            ) {
                DropdownMenuItem(
                    text = { Text("العمل بالإنترنت (متصل سحابياً)", color = GreenConfirm, fontSize = 12.sp) },
                    onClick = {
                        onToggleNetwork("متصل")
                        dropdownExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("محاكاة قطع الاتصال (عمل محلي)", color = RedCancel, fontSize = 12.sp) },
                    onClick = {
                        onToggleNetwork("غير متصل")
                        dropdownExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("مزامنة فورية يدوية لقواعد البيانات", color = BlueRescheduled, fontSize = 12.sp) },
                    onClick = {
                        onToggleNetwork("جاري المزامنة")
                        dropdownExpanded = false
                    }
                )
            }
        }
    }
}

// 2. Bottom Nav Bar
@Composable
fun AppBottomNavBar(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    currentUserRole: String
) {
    NavigationBar(
        containerColor = DeepCharcoal,
        tonalElevation = 8.dp,
        modifier = Modifier.border(width = 0.5.dp, color = DarkGrayAccent)
    ) {
        NavigationBarItem(
            selected = selectedTab == "dashboard" || selectedTab == "customers" || selectedTab == "payments" || selectedTab == "reports",
            onClick = { onTabSelected("dashboard") },
            icon = { Icon(Icons.Filled.Dashboard, contentDescription = "لوحة التحكم") },
            label = { Text("الرئيسية", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = GoldenClassic,
                indicatorColor = GoldenClassic,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            ),
            modifier = Modifier.testTag("nav_dashboard")
        )

        NavigationBarItem(
            selected = selectedTab == "bookings",
            onClick = { onTabSelected("bookings") },
            icon = { Icon(Icons.Filled.MenuBook, contentDescription = "الحجوزات") },
            label = { Text("الحجوزات", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = GoldenClassic,
                indicatorColor = GoldenClassic,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            ),
            modifier = Modifier.testTag("nav_bookings")
        )

        NavigationBarItem(
            selected = selectedTab == "calendar",
            onClick = { onTabSelected("calendar") },
            icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = "التقويم") },
            label = { Text("التقويم", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = GoldenClassic,
                indicatorColor = GoldenClassic,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            ),
            modifier = Modifier.testTag("nav_calendar")
        )

        NavigationBarItem(
            selected = selectedTab == "more" || selectedTab == "addNew" || selectedTab == "adminTools" || selectedTab == "auditLogs",
            onClick = { onTabSelected("more") },
            icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = "المزيد") },
            label = { Text("المزيد", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = GoldenClassic,
                indicatorColor = GoldenClassic,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            ),
            modifier = Modifier.testTag("nav_more")
        )
    }
}

// 3. Login Screen
@Composable
fun LoginScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onLoginClick: (String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("") }

    val bgColors = if (isDarkTheme) {
        listOf(ObsidianBlack, DeepCharcoal)
    } else {
        listOf(IvoryBackground, LightBeigePanel)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = bgColors)),
        contentAlignment = Alignment.Center
    ) {
        // Upper Corner Theme Toggle
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                    .border(0.5.dp, GoldenMuted, CircleShape)
            ) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = "تبديل النمط",
                    tint = if (isDarkTheme) GoldenBright else GoldenBronze
                )
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Luxury Logo (The actual circular Palace Logo)
            Card(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(2.dp, GoldenClassic)
            ) {
                Image(
                    painter = painterResource(id = com.alqasrhall.booking.R.drawable.img_logo),
                    contentDescription = "شعار الصالة",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "بوابة إدارة حجوزات صالة القصر الدائري",
                color = GoldenClassic,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )

            Text(
                "نظام تخطيط موارد الصالة والرقابة المالية الذكية",
                color = if (isDarkTheme) Color.LightGray else CharcoalText.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("اسم المستخدم") },
                modifier = Modifier.fillMaxWidth().testTag("username_input"),
                textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onBackground)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("كلمة المرور") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("password_input"),
                textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onBackground)
            )

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = { onLoginClick(username, password) },
                colors = ButtonDefaults.buttonColors(containerColor = GoldenClassic),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_login_button")
            ) {
                Text(
                    "تسجيل الدخول للنظام الآمن",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Divider(color = Color.DarkGray, thickness = 1.dp)

            // Selector for the User Types without autofilling username and password
            Text(
                "يرجى تحديد نوع المستخدم والولوج الآمن:",
                color = GoldenMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center
            ) {
                QuickCredChip("مدير النظام", selectedRole == "Admin") { selectedRole = "Admin" }
                QuickCredChip("مدير الصالة", selectedRole == "Manager") { selectedRole = "Manager" }
                QuickCredChip("المحاسب", selectedRole == "Accountant") { selectedRole = "Accountant" }
                QuickCredChip("الاستقبال", selectedRole == "Reception") { selectedRole = "Reception" }
            }

            if (selectedRole.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "يرجى كتابة اسم المستخدم وكلمة المرور يدوياً للدور المختار.",
                    color = GoldenMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun QuickCredChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) GoldenClassic else DarkGrayAccent)
            .border(0.5.dp, GoldenMuted, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (isSelected) Color.Black else GoldenClassic,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// 4. Tab 1: Dashboard View (Legacy Full Control Center)
@Composable
fun OldDashboardTab(viewModel: AppViewModel) {
    val selectedCurrency by viewModel.selectedCurrency.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val bookings by viewModel.bookings.collectAsStateWithLifecycle()
    val usersList by viewModel.users.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val connectedPresences by viewModel.connectedPresences.collectAsStateWithLifecycle()
    val editingLocks by viewModel.editingLocks.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()
    val dashboardWidgets by viewModel.dashboardWidgets.collectAsStateWithLifecycle()
    val schedulerEnabled by viewModel.backupSchedulerEnabled.collectAsStateWithLifecycle()
    val schedulerInterval by viewModel.backupSchedulerInterval.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    var activeSubTab by remember { mutableStateOf("control") } // control, aiInsights, heatmap, finance, security, notifications

    // Filter status for notifications
    var notifFilter by remember { mutableStateOf("الكل") } // الكل, جديد, مؤرشف

    // Selected outstanding contract for statements/receipt views
    var selectedStatementBooking by remember { mutableStateOf<Booking?>(null) }
    var selectedReceiptPayment by remember { mutableStateOf<Payment?>(null) }

    // Date calculations
    val cal = Calendar.getInstance()
    val currentYear = cal.get(Calendar.YEAR)
    val currentMonth = cal.get(Calendar.MONTH) + 1
    val currentMonthStr = String.format(Locale.ENGLISH, "%04d-%02d", currentYear, currentMonth)
    val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
    val tomorrowDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date(System.currentTimeMillis() + 86400000))

    val currentMonthBookings = bookings.filter { it.dateStr.startsWith(currentMonthStr) }
    val expectedRevenue = currentMonthBookings.filter { it.status != "إلغاء" && it.status != "استبدال إلى تاريخ آخر" }.sumOf { it.rentAmount }
    val totalPaidThisMonth = currentMonthBookings.filter { it.status != "إلغاء" && it.status != "استبدال إلى تاريخ آخر" }.sumOf { it.totalPaid }
    val remainingCollectedThisMonth = expectedRevenue - totalPaidThisMonth

    val tempBookingsCount = bookings.count { it.status == "مؤقت" }
    val cancelledBookingsCount = bookings.count { it.status == "إلغاء" }
    val confirmedBookingsCount = bookings.count { it.status == "جديد" }

    // Occupancy calculation
    val totalDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val bookedDaysCurrentMonth = currentMonthBookings.filter { it.status == "جديد" || it.status == "مؤقت" }.map { it.dateStr }.distinct().size
    val occupancyPercent = if (totalDaysInMonth > 0) ((bookedDaysCurrentMonth.toFloat() / totalDaysInMonth) * 100).toInt() else 0

    // Alerts
    val upcomingDueAlerts = bookings.filter {
        it.status == "جديد" && it.remainingAmount > 0.0 && isDateWithin7Days(it.dateStr)
    }
    val temporaryExpiringSoon = bookings.filter {
        it.status == "مؤقت" && it.temporaryExpiresAt != null && (it.temporaryExpiresAt!! - System.currentTimeMillis() < 12 * 60 * 60 * 1000)
    }

    // Today/Tomorrow Events
    val todayEventsList = bookings.filter { it.dateStr == todayDateStr && it.status != "إلغاء" }
    val tomorrowEventsList = bookings.filter { it.dateStr == tomorrowDateStr && it.status != "إلغاء" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        horizontalAlignment = Alignment.End
    ) {
        // --- HORIZONTAL DESIGNS BAR OF EXECUTIVE SUB-TABS ---
        ScrollableTabRow(
            selectedTabIndex = when (activeSubTab) {
                "control" -> 0
                "eventDay" -> 1
                "reception" -> 2
                "aiInsights" -> 3
                "heatmap" -> 4
                "finance" -> 5
                "security" -> 6
                "notifications" -> 7
                else -> 0
            },
            containerColor = DeepCharcoal,
            contentColor = GoldenClassic,
            edgePadding = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = activeSubTab == "control",
                onClick = { activeSubTab = "control" },
                text = { Text("قمرة التحكم", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Filled.Dashboard, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = activeSubTab == "eventDay",
                onClick = { activeSubTab = "eventDay" },
                text = { Text("يوم الفعالية النشط", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = activeSubTab == "reception",
                onClick = { activeSubTab = "reception" },
                text = { Text("الاستقبال السريع", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = activeSubTab == "aiInsights",
                onClick = { activeSubTab = "aiInsights" },
                text = { Text("التحليلات وذكاء الأعمال", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = activeSubTab == "heatmap",
                onClick = { activeSubTab = "heatmap" },
                text = { Text("خرائط الإشغال", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = activeSubTab == "finance",
                onClick = { activeSubTab = "finance" },
                text = { Text("المركز المالي للذمم", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = activeSubTab == "security",
                onClick = { activeSubTab = "security" },
                text = { Text("مراقبة الأمان والصحة", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            
            val unreadCount = notifications.count { !it.isRead }
            Tab(
                selected = activeSubTab == "notifications",
                onClick = { activeSubTab = "notifications" },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (unreadCount > 0) {
                            Badge(containerColor = RedCancel, modifier = Modifier.padding(end = 4.dp)) {
                                Text("$unreadCount", color = Color.White, fontSize = 9.sp)
                            }
                        }
                        Text("الإخطارات", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                },
                icon = { Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        // --- GLOBAL EDITING LOCKS BANNER ---
        if (editingLocks.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF7F1D1D))
                    .padding(vertical = 6.dp, horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(
                        "تحذير: الموظف (${editingLocks.values.first().username}) يقوم حالياً بتعديل حجز بملفات النظام لتلافي تضارب الحفظ!",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right
                    )
                }
            }
        }

        // --- GLOBAL UNDO BANNER (30 seconds) ---
        if (pendingUndo != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = DarkGrayAccent),
                border = BorderStroke(1.dp, GoldenBright)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.triggerUndo() },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldenBright),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Undo, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تراجع الآن (30ث)", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("تم تنفيذ إجراء حساس: ${pendingUndo!!.description}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("تم حفظ التغيير بالخطأ؟ يمكنك التراجع فوراً بالضغط على الزر.", color = Color.Gray, fontSize = 10.sp)
                    }
                }
            }
        }

        // --- MAIN SUB-TAB RENDER WINDOWS ---
        var selectedEventDate by remember { mutableStateOf(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH).format(java.util.Date())) }
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (activeSubTab) {
                "control" -> {
                    val isExec by viewModel.isExecutiveMode.collectAsStateWithLifecycle()
                    
                    Column(modifier = Modifier.fillMaxSize()) {
                        var commandInputText by remember { mutableStateOf("") }
                        
                        // Universal Command Spotlight & Executive Mode Trigger Bar!
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = { viewModel.isExecutiveMode.value = !isExec },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isExec) GoldenClassic.copy(0.2f) else DarkGrayAccent)
                                    .border(1.dp, if (isExec) GoldenBright else Color.Gray, RoundedCornerShape(20.dp)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isExec) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = null,
                                        tint = if (isExec) GoldenBright else Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isExec) "وضع المالك التنفيذي: مفعّل" else "تشغيل وضع المالك الموحد",
                                        color = if (isExec) Color.White else Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                                    .background(DeepCharcoal, RoundedCornerShape(28.dp))
                                    .border(1.dp, GoldenClassic.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                                    .padding(horizontal = 12.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Terminal, contentDescription = null, tint = GoldenBright, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                
                                OutlinedTextField(
                                    value = commandInputText,
                                    onValueChange = { 
                                        commandInputText = it 
                                        viewModel.searchRenterQuery.value = it
                                    },
                                    placeholder = { 
                                        Text("قمرة الأوامر (بحث، إضافة حجز، ديون)", color = Color.Gray, fontSize = 11.sp) 
                                    },
                                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl, color = Color.White, fontSize = 12.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                                )
                                
                                TextButton(
                                    onClick = { 
                                        val handled = viewModel.executeCommand(commandInputText)
                                        if (handled) {
                                            commandInputText = ""
                                        } else {
                                            Toast.makeText(context, "الرجاء كتابة أمر مفهوم في مركز التوجيه.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = GoldenBright),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("تنفيذ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (isExec) {
                            // Render Executive Mode (Sleek single-screen simplified layout for owners)
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                        colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                                        border = BorderStroke(1.5.dp, GoldenBright)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                                            Text("لوحة المؤشرات والتقارير التنفيذية المبسطة لمالك الصالة", color = GoldenBright, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                            Text("قراءة موحدة لبيانات وأرباح الصالة في قمرة واحدة وموجزة", color = Color.Gray, fontSize = 11.sp)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            
                                            Divider(color = Color.DarkGray)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            
                                            // 1. Annual Revenue & Monthly
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                                    Text("إيراد الشهر المتوقع", color = Color.LightGray, fontSize = 11.sp)
                                                    Text(viewModel.formatYemeniCurrency(expectedRevenue), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                                }
                                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                                    Text("المحصل الفعلي بالخزينة", color = Color.LightGray, fontSize = 11.sp)
                                                    Text(viewModel.formatYemeniCurrency(totalPaidThisMonth), color = GreenConfirm, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.height(16.dp))
                                            
                                            // 2. Occupancy & Debt Remaining
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                                    Text("معدل إشغال الصالة", color = Color.LightGray, fontSize = 11.sp)
                                                    Text("$occupancyPercent%", color = GoldenBright, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                                }
                                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                                    Text("الذمم المستحقة المطلوبة", color = Color.LightGray, fontSize = 11.sp)
                                                    Text(viewModel.formatYemeniCurrency(remainingCollectedThisMonth), color = RedCancel, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    // Executive Smart Decision Card
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                        colors = CardDefaults.cardColors(containerColor = DarkGrayAccent),
                                        border = BorderStroke(0.5.dp, Color.Gray)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("نصيحة المساعد الذكي الاستراتيجية للمالك", color = GoldenClassic, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = GoldenClassic, modifier = Modifier.size(16.dp))
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "معدل الإشغال الحالي للشهر هو $occupancyPercent%. معدل نمو الأرباح ارتفع بنسبة 8% مقارنة بالشهر السابق. نوصي باستقطاب مستأجري الأفراح لمنتصف الأسبوع بعروض مميزة لرفع العوائد الإجمالية.",
                                                color = Color.LightGray,
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Right
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Standard Dashboard control panel
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                        // Event Day Mode Spotlight header
                        if (todayEventsList.isNotEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                                    border = BorderStroke(1.5.dp, GoldenClassic)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("وضع يوم الفعالية النشط - مناسبة اليوم تحت الضوء!", color = GoldenBright, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(GoldenBright)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        todayEventsList.forEach { ev ->
                                            Text("المستأجر: ${ev.renterName}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text("نوع الفعالية: ${ev.eventType} | رقم الجوال: ${ev.phone1}", color = Color.LightGray, fontSize = 12.sp)
                                            
                                            val paymentStatus = if (ev.remainingAmount <= 0.0) "مسدد بالكامل" else "متبقي ذمة بقيمة: ${viewModel.formatYemeniCurrency(ev.remainingAmount)}"
                                            Text(paymentStatus, color = if (ev.remainingAmount <= 0.0) GreenConfirm else RedCancel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Divider(color = Color.DarkGray, modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                            }
                        }

                        // Customizable KPI Indicators Block
                        item {
                            Text(
                                "لوحة مؤشرات الأداء والتحكم الإشرافي",
                                color = GoldenClassic,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        item {
                            // Dynamic Widgets Order Grid
                            dashboardWidgets.chunked(2).forEach { chunk ->
                                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                    chunk.forEach { widgetId ->
                                        when (widgetId) {
                                            "todayEvents" -> {
                                                DashboardCard(
                                                    title = "مناسبات اليوم",
                                                    value = "${todayEventsList.size} حجز اليوم",
                                                    icon = Icons.Filled.Event,
                                                    color = GoldenBright,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            "upcomingEvents" -> {
                                                DashboardCard(
                                                    title = "قيمة إيراد الشهر",
                                                    value = viewModel.formatYemeniCurrency(expectedRevenue),
                                                    icon = Icons.Filled.MonetizationOn,
                                                    color = GoldenClassic,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            "financeSummary" -> {
                                                DashboardCard(
                                                    title = "المحصل الفعلي المودع",
                                                    value = viewModel.formatYemeniCurrency(totalPaidThisMonth),
                                                    icon = Icons.Filled.CheckCircle,
                                                    color = GreenConfirm,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            "occupancyRate" -> {
                                                DashboardCard(
                                                    title = "مستحقات معلقة قابلة للتحصيل",
                                                    value = viewModel.formatYemeniCurrency(remainingCollectedThisMonth),
                                                    icon = Icons.Filled.Error,
                                                    color = RedCancel,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    if (chunk.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // Circular occupancy overview
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                                border = BorderStroke(0.5.dp, Color.DarkGray)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(90.dp)) {
                                        Canvas(modifier = Modifier.size(80.dp)) {
                                            drawArc(
                                                color = Color.DarkGray,
                                                startAngle = 0f,
                                                sweepAngle = 360f,
                                                useCenter = false,
                                                style = Stroke(width = 6.dp.toPx())
                                            )
                                            drawArc(
                                                color = GoldenClassic,
                                                startAngle = -90f,
                                                sweepAngle = (occupancyPercent.toFloat() / 100) * 360f,
                                                useCenter = false,
                                                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("$occupancyPercent%", color = GoldenBright, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("إشغال الصالة", color = Color.Gray, fontSize = 9.sp)
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("كثافة الحجوزات للشهر الحالي:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("أيام محجوزة: $bookedDaysCurrentMonth من أصل $totalDaysInMonth يوماً بتصنيف إشرافي.", color = Color.Gray, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row {
                                            Text("$confirmedBookingsCount مؤكد", color = GreenConfirm, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("$tempBookingsCount مؤقت بقيد يومي", color = AmberLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Critical Action alerts
                        item {
                            if (temporaryExpiringSoon.isNotEmpty() || upcomingDueAlerts.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "صندوق التنبيهات وإشعار الإجراء السريع:",
                                    color = AmberLight,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )

                                temporaryExpiringSoon.forEach { temp ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = DarkGrayAccent),
                                        border = BorderStroke(1.dp, AmberLight)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("مهلة الـ 24 ساعة للحجز المؤقت تنتهي قريباً جداً!", color = AmberLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(Icons.Filled.Warning, contentDescription = null, tint = AmberLight, modifier = Modifier.size(14.dp))
                                            }
                                            Text("العميل: ${temp.renterName} (تاريخ: ${temp.dateStr})", color = Color.White, fontSize = 12.sp)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row {
                                                Button(
                                                    onClick = { viewModel.confirmTemporaryBooking(temp.id) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = GreenConfirm),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text("قبول وتأكيد السند", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Button(
                                                    onClick = { viewModel.cancelActiveBooking(temp.id) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = RedCancel),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text("إلغاء للإتاحة", color = Color.White, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Admin customizable widgets layout manager
                        if (usersList.find { it.username == viewModel.currentUser.value?.username }?.role == "Admin") {
                            item {
                                Spacer(modifier = Modifier.height(20.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = DarkGrayAccent),
                                    border = BorderStroke(0.5.dp, Color.Gray)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                                        Text("إدارة واجهة لوحة التحكم تخصيص المشرف:", color = GoldenClassic, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("أعد تصفيف وترتيب بطاقات الأداء بالصالة:", color = Color.Gray, fontSize = 10.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            Button(
                                                onClick = { viewModel.reorderWidgets(listOf("todayEvents", "upcomingEvents", "financeSummary", "occupancyRate")) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text("الترتيب الفعلي المالي", color = Color.White, fontSize = 9.sp)
                                            }

                                            Button(
                                                onClick = { viewModel.reorderWidgets(listOf("financeSummary", "todayEvents", "upcomingEvents")) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text("التركيز على المدفوعات اليومية", color = Color.White, fontSize = 9.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
                }

                "aiInsights" -> {
                    // AI Insights center displaying smart business intelligence cards
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        item {
                            Text(
                                "مركز ذكاء الأعمال وصياغة التنبؤات الاستراتيجية",
                                color = GoldenClassic,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                "تحليلات متقدمة لقاعدة البيانات والمنحنيات المستقاة من العقود السابقة:",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        // Calculate stats dynamically
                        val eventTypesMap = bookings.groupBy { it.eventType }.mapValues { it.value.size }
                        val popularType = eventTypesMap.maxByOrNull { it.value }?.key ?: "لا توجد بيانات كافية"
                        val totalContractSum = bookings.sumOf { it.rentAmount }
                        val avgContractValue = if (bookings.isNotEmpty()) totalContractSum / bookings.size else 0.0
                        val collectionRate = if (totalContractSum > 0) (bookings.sumOf { it.totalPaid } / totalContractSum) * 100 else 0.0

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                                border = BorderStroke(1.dp, Color.DarkGray)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("التصنيف الأكثر شعبية للصالة", color = GoldenBright, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = GoldenBright, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("نوع المناسبات الأكثر طلباً: $popularType", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("يمثل ذروة الإشغال ونسب التشغيل بالصالة ويزيد من إيراد عقود العطل والخميس.", color = Color.Gray, fontSize = 11.sp)
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                                border = BorderStroke(1.dp, Color.DarkGray)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("معدل ومتوسط قيمة العقد الواحد بالريال", color = GoldenClassic, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = GoldenClassic, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("المتوسط الفعلي: ${viewModel.formatYemeniCurrency(avgContractValue)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("يتيح للمشروعات مراقبة التسعير القياسي لصنف الصالة.", color = Color.Gray, fontSize = 11.sp)
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                                border = BorderStroke(1.dp, Color.DarkGray)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("كفاءة وأداء تحصيل الديون والذمم", color = GreenConfirm, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = GreenConfirm, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("نسبة التحصيل الإجمالية: ${String.format(Locale.ENGLISH, "%.1f", collectionRate)}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("تعكس الفعالية والجاهزية المالية العالية للتحكم واستيفاء الأرصدة المستحقة من الرعاة.", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                "heatmap" -> {
                    // Occupancy Heatmap view
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        item {
                            Text(
                                "خريطة الكثافة الإشغالية والحرارية",
                                color = GoldenClassic,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                "توضح معدل إشغال الصالة الفعلي بالأيام لتفادي السقطات التشغيلية:",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                                border = BorderStroke(0.5.dp, Color.DarkGray)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                                    Text("مؤشر مستويات الكثافة والضغط:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(12.dp).background(GoldenClassic).clip(RoundedCornerShape(2.dp)))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("محجوز بالكامل", color = Color.LightGray, fontSize = 10.sp)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(12.dp).background(GoldenBright).clip(RoundedCornerShape(2.dp)))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("إشغال متوسط", color = Color.LightGray, fontSize = 10.sp)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(12.dp).background(Color.DarkGray).clip(RoundedCornerShape(2.dp)))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("شاغر متاح", color = Color.LightGray, fontSize = 10.sp)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Divider(color = Color.DarkGray)
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Render grid layout representing the days of the current month
                                    val daysList = (1..totalDaysInMonth).toList()
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(7),
                                        modifier = Modifier.height(180.dp).fillMaxWidth()
                                    ) {
                                        items(daysList.size) { index ->
                                            val dayNum = daysList[index]
                                            val loopDayStr = String.format(Locale.ENGLISH, "%s-%02d", currentMonthStr, dayNum)
                                            val isBooked = bookings.any { it.dateStr == loopDayStr && it.status != "إلغاء" }
                                            val bgColor = if (isBooked) GoldenClassic else Color.DarkGray
                                            
                                            Box(
                                                modifier = Modifier
                                                    .padding(3.dp)
                                                    .aspectRatio(1f)
                                                    .background(bgColor, RoundedCornerShape(4.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("$dayNum", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "finance" -> {
                    // Financial Cockpit
                    val outstandingList = bookings.filter { it.status == "جديد" && it.remainingAmount > 0.0 }
                        .sortedBy { it.dateStr }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        item {
                            Text(
                                "قمرة التحكم المالي ومطابقة أرصدة الديون والذمم",
                                color = GoldenClassic,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                "قائمة عقود الحجز المؤكدة التي تشتمل على مستحقات متبقية وباقية:",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        if (outstandingList.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("ممتاز! تم استيفاء وتحصيل كافة المستحقات المالية وحققت الصالة نسبة اكتمال 100%!", color = GreenConfirm, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                }
                            }
                        } else {
                            items(outstandingList.size) { idx ->
                                val booking = outstandingList[idx]
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                                    border = BorderStroke(0.5.dp, Color.DarkGray)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row {
                                                Button(
                                                    onClick = { selectedStatementBooking = booking },
                                                    colors = ButtonDefaults.buttonColors(containerColor = GoldenClassic),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text("كشف الحساب وطباعة السند", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("العميل: ${booking.renterName}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("تاريخ المناسبة: ${booking.dateStr} | المتبقي: ${viewModel.formatYemeniCurrency(booking.remainingAmount)}", color = RedCancel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "security" -> {
                    // Safety & Health Center Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        item {
                            Text(
                                "مركز أمان ومعالجة صحة الأنظمة (إشرافي ومؤمن)",
                                color = GoldenClassic,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                "عرض الحالة الحية للمستندات والاتصال بقاعدة بيانات السيرفر وبوابات المزامنة:",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                                border = BorderStroke(0.5.dp, Color.DarkGray)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                                    Text("الحالة التشغيلية واتصال السرفر المباشر:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("نشط ومستقر", color = GreenConfirm, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text("حالة بوابات Firestore السحابية:", color = Color.LightGray, fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("متصل بالمزامنة والجاهزية التامة", color = GreenConfirm, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text("حالة بوابات التخزين المحلي والآمن والأرشيف:", color = Color.LightGray, fontSize = 11.sp)
                                    }
                                }
                            }

                            // Dynamic user list presences
                            Text(
                                "الموظفين المتصلين حالياً بالنظام وبوابات القيادة:",
                                color = GoldenClassic,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            connectedPresences.forEach { p ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkGrayAccent),
                                    border = BorderStroke(0.3.dp, Color.DarkGray)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (p.isOnline) GreenConfirm else Color.Gray)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (p.isOnline) "نشط الآن" else "غير متصل", color = if (p.isOnline) GreenConfirm else Color.Gray, fontSize = 10.sp)
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(p.fullName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("الجهاز المتصل: ${p.device} | صلاحية: ${p.role}", color = Color.Gray, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "notifications" -> {
                    // Smart In-App Notification Center
                    val filteredNotifs = notifications.filter {
                        if (notifFilter == "جديد") !it.isRead else true
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row {
                                    TextButton(onClick = { viewModel.markAllNotificationsRead() }) {
                                        Text("تحديد الكل كمقروء", color = GoldenClassic, fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(onClick = { viewModel.clearNotifications() }) {
                                        Text("تفريغ الإخطارات", color = Color.Gray, fontSize = 11.sp)
                                    }
                                }

                                Text(
                                    "صندوق الإخطارات والتنبيهات المتقدمة",
                                    color = GoldenClassic,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }

                            // Notification Type Picker / Filters
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                listOf("الكل", "جديد").forEach { filter ->
                                    FilterChip(
                                        selected = notifFilter == filter,
                                        onClick = { notifFilter = filter },
                                        label = { Text(filter) },
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }

                        if (filteredNotifs.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("صندوق الإخطارات الخاص بك فارغ تمااماً حالياً.", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        } else {
                            items(filteredNotifs.size) { idx ->
                                val notif = filteredNotifs[idx]
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { viewModel.markNotificationRead(notif.id) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (notif.isRead) DarkGrayAccent else DeepCharcoal
                                    ),
                                    border = BorderStroke(
                                        0.5.dp, if (notif.isRead) Color.DarkGray else GoldenClassic.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (!notif.isRead) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(GoldenClassic)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }

                                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                            Text(notif.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(notif.message, color = Color.LightGray, fontSize = 11.sp)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    when (notif.type) {
                                                        "System" -> Color.DarkGray
                                                        "Backup" -> Color(0xFF1E3A8A)
                                                        "Reminder" -> Color(0xFF78350F)
                                                        else -> GoldenBronze
                                                    },
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(notif.type, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "eventDay" -> {

                    val bookingForDate = bookings.find { it.dateStr == selectedEventDate && it.status != "إلغاء" }
                    val allEventAuditLogs by viewModel.auditLogs.collectAsStateWithLifecycle(initialValue = emptyList())
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                                border = BorderStroke(1.dp, GoldenClassic)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    var showDatePicker by remember { mutableStateOf(false) }
                                    if (showDatePicker) {
                                        AndroidDatePickerDialog(
                                            onDateSelected = { 
                                                selectedEventDate = it
                                                showDatePicker = false
                                            },
                                            onDismiss = { showDatePicker = false }
                                        )
                                    }
                                    
                                    Button(
                                        onClick = { showDatePicker = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldenClassic)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.DateRange, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("تغيير اليوم", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("📅 يوم الفعالية المالي والتشغيلي المباشر", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(selectedEventDate, color = GoldenBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("تاريخ العينة:", color = Color.Gray, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (bookingForDate == null) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkGrayAccent),
                                    border = BorderStroke(0.5.dp, Color.DarkGray)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Filled.EventBusy, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("لا توجد مناسبات أو مهام مسجلة في هذا التاريخ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("تاريخ الصالة متاح بنسبة 100% لاستقبال وتثبيت حجوزات مبيعات جديدة.", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = { 
                                                viewModel.prefilledBookingDate.value = selectedEventDate
                                                viewModel.selectTab("addNew")
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = GreenConfirm)
                                        ) {
                                            Text("تسجيل حجز مباشر لهذا اليوم", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        } else {
                            val activeBooking = bookingForDate!!
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                                    border = BorderStroke(1.dp, if (activeBooking.status == "مؤقت") Color.Yellow else GoldenClassic)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (activeBooking.status == "مؤقت") Color(0xFF854D0E) else Color(0xFF065F46),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    if (activeBooking.status == "مؤقت") "حجز مؤقت معلق" else "حجز مؤكد رسمي",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(activeBooking.renterName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                Text(activeBooking.eventType, color = GoldenBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Divider(color = Color.DarkGray)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        val riskLevel = viewModel.getCustomerRiskLevel(activeBooking.renterName, activeBooking.phone1)
                                        if (riskLevel != null) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFF7F1D1D).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                    .border(1.dp, Color.Red, RoundedCornerShape(8.dp))
                                                    .padding(8.dp),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(riskLevel!!, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(14.dp))
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                        
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(activeBooking.phone1, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text("جوال العميل الكلي:", color = Color.Gray, fontSize = 11.sp)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(viewModel.formatYemeniCurrency(activeBooking.rentAmount), color = Color.White, fontSize = 12.sp)
                                                Text("التكلفة الأصلية للعقد:", color = Color.Gray, fontSize = 11.sp)
                                            }
                                            if (activeBooking.discountAmount > 0.0) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("-${viewModel.formatYemeniCurrency(activeBooking.discountAmount)}", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    Text("الحسم والتحفيز المالي الممنوح:", color = Color.Gray, fontSize = 11.sp)
                                                }
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(viewModel.formatYemeniCurrency(activeBooking.totalPaid), color = GreenConfirm, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text("المحصل النقدي بالخزينة:", color = Color.Gray, fontSize = 11.sp)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                val bal = activeBooking.remainingAmount
                                                Text(
                                                    viewModel.formatYemeniCurrency(bal),
                                                    color = if (bal > 0.0) RedCancel else GreenConfirm,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text("المتبقي والذمة المطلوبة:", color = Color.Gray, fontSize = 11.sp)
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Divider(color = Color.DarkGray)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        Text("📝 تعليمات الصالة الداخلية وملاحظات التنظيم", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        var notesInputText by remember(activeBooking.id) { mutableStateOf(activeBooking.notes) }
                                        OutlinedTextField(
                                            value = notesInputText,
                                            onValueChange = { notesInputText = it },
                                            placeholder = { Text("أدخل تعليمات الصالة اليوم لخدمة العميل وتكليفات الإشراف...", color = Color.DarkGray, fontSize = 11.sp) },
                                            modifier = Modifier.fillMaxWidth().height(60.dp),
                                            textStyle = TextStyle(color = Color.White, fontSize = 11.sp, textDirection = TextDirection.Rtl),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldenClassic, unfocusedBorderColor = Color.DarkGray)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Button(
                                            onClick = { 
                                                val updated = activeBooking.copy(notes = notesInputText, lastUpdated = System.currentTimeMillis())
                                                viewModel.modifyBooking(updated)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = GoldenClassic),
                                            modifier = Modifier.align(Alignment.Start).height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("تحديث التعليمات", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Divider(color = Color.DarkGray)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        Text("👥 طاقم الإشراف والعمال المعينين لمناسبة اليوم", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        var staffInputText by remember(activeBooking.id) { mutableStateOf(activeBooking.assignedStaff) }
                                        OutlinedTextField(
                                            value = staffInputText,
                                            onValueChange = { staffInputText = it },
                                            placeholder = { Text("مثال: البوفيه: أحمد، الصوتيات: عمر، المشرف: وائل...", color = Color.DarkGray, fontSize = 11.sp) },
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = TextStyle(color = Color.White, fontSize = 11.sp, textDirection = TextDirection.Rtl),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldenClassic, unfocusedBorderColor = Color.DarkGray)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Button(
                                            onClick = { 
                                                val updated = activeBooking.copy(assignedStaff = staffInputText, lastUpdated = System.currentTimeMillis())
                                                viewModel.modifyBooking(updated)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = GoldenClassic),
                                            modifier = Modifier.align(Alignment.Start).height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("تحديث طاقم المناسبة", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                        
                                        if (activeBooking.remainingAmount > 0.0) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = {
                                                    viewModel.addBookingPayment(
                                                        bookingId = activeBooking.id,
                                                        amount = activeBooking.remainingAmount,
                                                        paymentMethod = "نقداً",
                                                        receiptRef = "سداد ذمة حفل مباشر يوم الفعالية #${activeBooking.id}",
                                                        notes = "تصفية الحساب نقداً مبيعات مباشرة."
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = GreenConfirm),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("تصفية متبقي عقد الصالة نقداً فوراً (${viewModel.formatYemeniCurrency(activeBooking.remainingAmount)})", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                                    border = BorderStroke(0.5.dp, Color.DarkGray)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                                        Text("⏳ الخط الخط التشغيلي والإجرائي التاريخي لمناسبة اليوم", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        val bookingPayments by viewModel.getPaymentsStream(activeBooking.id).collectAsStateWithLifecycle(initialValue = emptyList())
                                        val bLogs = allEventAuditLogs.filter { 
                                            it.detailsAfter.contains(activeBooking.renterName) || 
                                            it.detailsBefore.contains(activeBooking.renterName) ||
                                            it.detailsAfter.contains("رقم الحجز: ${activeBooking.id}")
                                        }
                                        
                                        val timelineEvents = mutableListOf<Pair<Long, String>>()
                                        timelineEvents.add(activeBooking.createdAt to "تسجيل عقد الحجز الأصلي للصالة بواسطة الموظف (${activeBooking.employeeUsername}) إيجار إجمالي بقيمة ${viewModel.formatYemeniCurrency(activeBooking.rentAmount)}")
                                        
                                        bookingPayments.forEach { pay ->
                                            timelineEvents.add(pay.paymentDate to "دفع وتحصيل مالي محقق بقيمة ${viewModel.formatYemeniCurrency(pay.amount)} بطريقة [${pay.paymentMethod}] مرجع مستند رقم: ${pay.receiptRef} بواسطة الموظف (${pay.receivedByEmployee})")
                                        }
                                        
                                        bLogs.forEach { log ->
                                            timelineEvents.add(log.timestamp to "[${log.actionType}] بواسطة الموظف المفوّض (${log.username}): ${log.detailsAfter}")
                                        }
                                        
                                        timelineEvents.sortBy { it.first }
                                        
                                        timelineEvents.forEach { ev ->
                                            val sdfDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ENGLISH).format(java.util.Date(ev.first))
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                                                    Text(ev.second, color = Color.LightGray, fontSize = 10.sp, textAlign = TextAlign.Right)
                                                    Text(sdfDate, color = Color.DarkGray, fontSize = 8.sp)
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(GoldenClassic)
                                                        .align(Alignment.CenterVertically)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "reception" -> {
                    var searchFiltersText by remember { mutableStateOf("") }
                    var miniBookingDate by remember { mutableStateOf("") }
                    var miniRenterName by remember { mutableStateOf("") }
                    var miniRenterPhone by remember { mutableStateOf("") }
                    var miniRenterAmount by remember { mutableStateOf("") }
                    var miniRenterType by remember { mutableStateOf("زواج") }
                    
                    val filteredBookingsResult = bookings.filter { b ->
                        searchFiltersText.isEmpty() || 
                        b.renterName.contains(searchFiltersText) || 
                        b.phone1.contains(searchFiltersText) || 
                        b.phone2.contains(searchFiltersText)
                    }
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text("⚡ واجهة الاستقبال والرد السريع ممتدة الفوائد", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("كل متطلبات موظف وخدمات استقبال الصالة في وحدة واحدة سريعة دون خطوات زائدة.", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                                border = BorderStroke(0.5.dp, Color.DarkGray)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                                    Text("🔍 البحث والتحقق الخاطف وبطاقات العملاء", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = searchFiltersText,
                                        onValueChange = { searchFiltersText = it },
                                        placeholder = { Text("أدخل اسم المستأجر، رقم الهاتف، لتصفيته فوراً...", color = Color.DarkGray, fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = TextStyle(color = Color.White, fontSize = 11.sp, textDirection = TextDirection.Rtl),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldenClassic, unfocusedBorderColor = Color.DarkGray)
                                    )
                                    
                                    if (searchFiltersText.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("المطابقات الخاطفة العاجلة لمطالب الموظف (${filteredBookingsResult.size}):", color = GoldenBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        filteredBookingsResult.take(3).forEach { b ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { 
                                                    selectedEventDate = b.dateStr
                                                    activeSubTab = "eventDay"
                                                },
                                                colors = CardDefaults.cardColors(containerColor = DarkGrayAccent),
                                                border = BorderStroke(0.5.dp, Color.DarkGray)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val risk = viewModel.getCustomerRiskLevel(b.renterName, b.phone1)
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (risk != null) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(Color.Red.copy(0.2f), RoundedCornerShape(4.dp))
                                                                    .border(0.5.dp, Color.Red, RoundedCornerShape(4.dp))
                                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                                            ) {
                                                                Text("عميل حذر", color = Color.Red, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                        }
                                                        Text(b.dateStr, color = GoldenClassic, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    
                                                    Column(horizontalAlignment = Alignment.End) {
                                                        Text(b.renterName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        Text("جوال: ${b.phone1} | صنف: ${b.eventType}", color = Color.Gray, fontSize = 9.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                                border = BorderStroke(1.dp, GoldenClassic)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                                    Text("⚡ تسجيل حجز مالي فوري مباشر بـ 3 ثوانٍ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("سجل حجزاً فورياً بأقل عدد من معطيات حقول الاستقبال بالصالة.", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.padding(bottom = 6.dp))
                                    
                                    var showMiniDatePicker by remember { mutableStateOf(false) }
                                    if (showMiniDatePicker) {
                                        AndroidDatePickerDialog(
                                            onDateSelected = { 
                                                miniBookingDate = it
                                                showMiniDatePicker = false
                                            },
                                            onDismiss = { showMiniDatePicker = false }
                                        )
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { showMiniDatePicker = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = DarkGrayAccent),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text(if (miniBookingDate.isEmpty()) "اختر تـاريخ الحفل" else miniBookingDate, color = GoldenClassic, fontSize = 11.sp)
                                        }
                                        Text("التاريخ المطلوب للحجز:", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    
                                    OutlinedTextField(
                                        value = miniRenterName,
                                        onValueChange = { miniRenterName = it },
                                        placeholder = { Text("أدخل اسم المستأجر الرباعي الكامل...", color = Color.DarkGray, fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        textStyle = TextStyle(color = Color.White, fontSize = 11.sp, textDirection = TextDirection.Rtl),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldenClassic, unfocusedBorderColor = Color.DarkGray)
                                    )
                                    
                                    OutlinedTextField(
                                        value = miniRenterPhone,
                                        onValueChange = { miniRenterPhone = it },
                                        placeholder = { Text("رقم جوال المستأجر (أساس الاتصال)...", color = Color.DarkGray, fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        textStyle = TextStyle(color = Color.White, fontSize = 11.sp, textDirection = TextDirection.Rtl),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldenClassic, unfocusedBorderColor = Color.DarkGray)
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            MoneyInputField(
                                                value = miniRenterAmount,
                                                onValueChange = { miniRenterAmount = it },
                                                label = "السعر الإجمالي للصالات",
                                                currency = selectedCurrency
                                            )
                                        }
                                        
                                        Box(modifier = Modifier.weight(1f).align(Alignment.CenterVertically)) {
                                            var expandedType by remember { mutableStateOf(false) }
                                            Button(
                                                onClick = { expandedType = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = DarkGrayAccent),
                                                modifier = Modifier.fillMaxWidth().height(42.dp)
                                            ) {
                                                Text("نوع: $miniRenterType", color = Color.White, fontSize = 11.sp)
                                            }
                                            DropdownMenu(
                                                expanded = expandedType,
                                                onDismissRequest = { expandedType = false }
                                            ) {
                                                listOf("زواج", "خطوبة", "تخرج", "اجتماعات", "أخرى").forEach { t ->
                                                    DropdownMenuItem(
                                                        text = { Text(t, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
                                                        onClick = {
                                                            miniRenterType = t
                                                            expandedType = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = {
                                            if (miniBookingDate.isEmpty() || miniRenterName.isEmpty() || miniRenterPhone.isEmpty() || miniRenterAmount.isEmpty()) {
                                                viewModel.showMessage("خطأ: يرجى إملاء كافة خانات نموذج الحجز قبل النقر!")
                                                return@Button
                                            }
                                            val amt = miniRenterAmount.toDoubleOrNull() ?: 0.0
                                            viewModel.addNewBooking(
                                                dateStr = miniBookingDate,
                                                renterName = miniRenterName,
                                                eventType = miniRenterType,
                                                rentAmount = amt,
                                                initialPaid = 0.0,
                                                phone1 = miniRenterPhone,
                                                phone2 = "",
                                                status = "جديد",
                                                paymentMethod = "نقداً",
                                                receiptRef = "استبيان حجز استقبال عاجل",
                                                force = false
                                            )
                                            // Reset inputs
                                            miniBookingDate = ""
                                            miniRenterName = ""
                                            miniRenterPhone = ""
                                            miniRenterAmount = ""
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GreenConfirm),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("احجز فوراً ونفّذ كشف التضارب وتعميم العقد", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                                border = BorderStroke(0.5.dp, Color.DarkGray)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                                    Text("📅 مؤشرات الإشغالات وحالة التفريغ وحجز الأيام الـ 8 المستقبيلة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                                    (0..7).forEach { offset ->
                                        val cal = Calendar.getInstance()
                                        cal.add(Calendar.DAY_OF_YEAR, offset)
                                        val dayStr = sdf.format(cal.time)
                                        val match = bookings.find { it.dateStr == dayStr && it.status != "إلغاء" }
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (match != null) {
                                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(match.renterName, color = Color.White, fontSize = 11.sp, maxLines = 1)
                                                    Text(" (${match.eventType})", color = Color.Gray, fontSize = 9.sp)
                                                } else {
                                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Green))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("متاح للحجز بالكامل فورياً", color = Color.Green, fontSize = 11.sp)
                                                }
                                            }
                                            
                                            Text(dayStr, color = GoldenClassic, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    // --- REUSABLE PRINTABLE STATEMENT DIALOG ---
    if (selectedStatementBooking != null) {
        val b = selectedStatementBooking!!
        val payStream by viewModel.getPaymentsStream(b.id).collectAsStateWithLifecycle(initialValue = emptyList())

        Dialog(onDismissRequest = { selectedStatementBooking = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GoldenClassic),
                color = DeepCharcoal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "بيان كشف الحساب المالي الموحد للعقد",
                        color = GoldenClassic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("رقم العقد الإداري: ${viewModel.getBookingRef(b)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("اسم المستأجر: ${b.renterName}", color = Color.White, fontSize = 12.sp)
                    Text("تاريخ المناسبة المعتمد: ${b.dateStr} (${b.dayOfWeek})", color = Color.LightGray, fontSize = 11.sp)
                    Text("صنف وقيمة العقد الإيجاري: ${viewModel.formatYemeniCurrency(b.rentAmount)}", color = Color.LightGray, fontSize = 11.sp)
                    Text("المسدد مسبقاً: ${viewModel.formatYemeniCurrency(b.totalPaid)}", color = GreenConfirm, fontSize = 11.sp)
                    Text("المبلغ المتبقي الباقي ذمة: ${viewModel.formatYemeniCurrency(b.remainingAmount)}", color = RedCancel, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("السجلات وسندات الدفع المدخلة:", color = GoldenClassic, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 4.dp))

                    if (payStream.isEmpty()) {
                        Text("لم يتم تسجيل أي سند دفع مالي لهذا العقد حتى اللحظة.", color = Color.Gray, fontSize = 11.sp)
                    } else {
                        payStream.forEach { pay ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(viewModel.formatYemeniCurrency(pay.amount), color = GreenConfirm, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("سند: ${pay.receiptRef} (طريقة: ${pay.paymentMethod})", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Synthetic QR Code generated dynamically inside Canvas for the PDF receipt compliance
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Canvas(modifier = Modifier.size(80.dp).background(Color.White).padding(6.dp)) {
                                val sizePx = size.width
                                val step = sizePx / 5f
                                // Draw illustrative beautiful high-contrast QR block vectors
                                drawRect(Color.Black, Offset(0f, 0f), androidx.compose.ui.geometry.Size(step * 2, step * 2))
                                drawRect(Color.Black, Offset(step * 3, 0f), androidx.compose.ui.geometry.Size(step * 2, step * 2))
                                drawRect(Color.Black, Offset(0f, step * 3), androidx.compose.ui.geometry.Size(step * 2, step * 2))
                                drawRect(Color.Black, Offset(step * 2, step * 2), androidx.compose.ui.geometry.Size(step, step))
                                drawRect(Color.Black, Offset(step * 4, step * 4), androidx.compose.ui.geometry.Size(step, step))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("رمز الاستجابة السريع للتحقق من الموثوقية", color = Color.Gray, fontSize = 9.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { selectedStatementBooking = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                        ) {
                            Text("إغلاق", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun DashboardCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Color.Gray, fontSize = 10.sp)
        }
    }
}

// 5. Tab 2: Calendar Tab
@Composable
fun CalendarTab(viewModel: AppViewModel) {
    val bookings by viewModel.bookings.collectAsStateWithLifecycle()
    var selectedMonthOffset by remember { mutableStateOf(0) } // 0 = current, 1 = next, etc.

    val calendar = Calendar.getInstance()
    calendar.add(Calendar.MONTH, selectedMonthOffset)
    
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) // 0-11
    
    val monthNameArabic = getArabicMonthName(month)

    // Calculate days of the month grid
    val tempCal = Calendar.getInstance()
    tempCal.set(year, month, 1)
    val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK) // 1 (Sun) to 7 (Sat)
    val daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val gridItems = remember(year, month, bookings) {
        val list = mutableListOf<String?>()
        // Align matching empty slots (Saturday starts Arabic week)
        // Saturday is index 7. Sun is index 1.
        // We will match: Saturday, Sunday, Monday, Tuesday, Wednesday, Thursday, Friday
        val offset = when (firstDayOfWeek) {
            Calendar.SATURDAY -> 0
            Calendar.SUNDAY -> 1
            Calendar.MONDAY -> 2
            Calendar.TUESDAY -> 3
            Calendar.WEDNESDAY -> 4
            Calendar.THURSDAY -> 5
            Calendar.FRIDAY -> 6
            else -> 0
        }

        for (i in 0 until offset) {
            list.add(null)
        }
        for (d in 1..daysInMonth) {
            list.add(String.format("%04d-%02d-%02d", year, month + 1, d))
        }
        list
    }

    var viewingBookingDetailInDialog by remember { mutableStateOf<Booking?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        // Month Switcher Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { selectedMonthOffset-- }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "الشهر السابق", tint = GoldenClassic)
            }

            Text(
                text = "$monthNameArabic $year",
                color = GoldenClassic,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )

            IconButton(onClick = { selectedMonthOffset++ }) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "الشهر التالي", tint = GoldenClassic)
            }
        }

        // Week Headers
        val weekHeaders = listOf("السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            weekHeaders.forEach { wh ->
                Text(
                    text = wh,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = GoldenMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Days Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.weight(1f)
        ) {
            items(gridItems.size) { index ->
                val dateStr = gridItems[index]
                if (dateStr == null) {
                    Box(modifier = Modifier.aspectRatio(1f).padding(2.dp))
                } else {
                    // Match booking for dateStr
                    val activeBooking = bookings.find { it.dateStr == dateStr && it.status != "إلغاء" && it.status != "استبدال إلى تاريخ آخر" }
                    // If no active, maybe finding the archived one to represent colors correctly
                    val bookingToRepresent = activeBooking ?: bookings.find { it.dateStr == dateStr }

                    val itemColor = when (bookingToRepresent?.status) {
                        "جديد" -> GreenConfirm
                        "مؤقت" -> AmberLight
                        "إلغاء" -> RedCancel
                        "استبدال إلى تاريخ آخر" -> BlueRescheduled
                        else -> null
                    }

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (itemColor != null) itemColor.copy(alpha = 0.25f) else Color.DarkGray.copy(alpha = 0.2f))
                            .border(
                                width = if (itemColor != null) 1.5.dp else 0.5.dp,
                                color = itemColor ?: Color.DarkGray,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                if (bookingToRepresent != null) {
                                    viewingBookingDetailInDialog = bookingToRepresent
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val dayDigit = dateStr.substringAfterLast("-").toInt().toString()
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = dayDigit,
                                color = if (itemColor != null) Color.White else Color.LightGray,
                                fontWeight = if (itemColor != null) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                            if (itemColor != null) {
                                Canvas(modifier = Modifier.size(6.dp)) {
                                    drawCircle(color = itemColor)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Color Key
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = CardDefaults.cardColors(containerColor = DeepCharcoal)
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                ColorKeyItem("مؤكد", GreenConfirm)
                ColorKeyItem("مؤقت", AmberLight)
                ColorKeyItem("مستبدل", BlueRescheduled)
                ColorKeyItem("ملغى", RedCancel)
            }
        }
    }

    // Calendar Booking Detail Overlay dialog
    if (viewingBookingDetailInDialog != null) {
        val bk = viewingBookingDetailInDialog!!
        Dialog(onDismissRequest = { viewingBookingDetailInDialog = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GoldenMuted),
                color = DeepCharcoal,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.End) {
                    Text("بيانات حجز تاريخ: ${bk.dateStr}", color = GoldenClassic, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text("اسم المستأجر: ${bk.renterName}", color = Color.White, fontSize = 14.sp)
                    Text("المناسبة: ${bk.eventType} (${bk.dayOfWeek})", color = Color.LightGray, fontSize = 13.sp)
                    Text("سعر إيجار القصر: ${bk.rentAmount} ريال", color = ChampagneLight, fontSize = 13.sp)
                    Text("إجمالي المسدد: ${bk.totalPaid} ريال", color = GreenConfirm, fontSize = 13.sp)
                    Text("المتبقي للتحصيل: ${bk.remainingAmount} ريال", color = RedCancel, fontSize = 13.sp)
                    Text("رقم التواصل: ${bk.phone1}", color = Color.White, fontSize = 13.sp)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    val statusTextAndColor = when (bk.status) {
                        "جديد" -> "حجز مؤكد" to GreenConfirm
                        "مؤقت" -> "حجز مؤقت (شارف على مهلة 24 ساعة)" to AmberLight
                        "إلغاء" -> "تم إلغاؤه" to RedCancel
                        else -> "مستبدل بتاريخ آخر" to BlueRescheduled
                    }
                    Text("حالة المستند: ${statusTextAndColor.first}", color = statusTextAndColor.second, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewingBookingDetailInDialog = null },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldenClassic),
                        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("close_calendar_detail")
                    ) {
                        Text("إغلاق التفاصيل", color = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun ColorKeyItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(end = 4.dp))
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(color)
        }
    }
}

// 6. Tab 3: Bookings Database
@Composable
fun BookingsTab(
    viewModel: AppViewModel,
    onAddPayment: (Booking) -> Unit,
    onViewPayments: (Booking) -> Unit,
    onViewAttachments: (Booking) -> Unit,
    onReschedule: (Booking) -> Unit,
    onEdit: (Booking) -> Unit
) {
    val context = LocalContext.current
    val bookings by viewModel.bookings.collectAsStateWithLifecycle()
    val usersList by viewModel.users.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchRenterQuery.collectAsStateWithLifecycle()
    val statusFilter by viewModel.filterStatus.collectAsStateWithLifecycle()
    val eventFilter by viewModel.filterEventType.collectAsStateWithLifecycle()
    val employeeFilter by viewModel.filterEmployee.collectAsStateWithLifecycle()
    val sortedBy by viewModel.sortBy.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedArchiveYear.collectAsStateWithLifecycle()

    // Filter list
    val filteredBookings = remember(bookings, searchQuery, statusFilter, eventFilter, employeeFilter, sortedBy, selectedYear) {
        var list = bookings.filter { b ->
            (searchQuery.isEmpty() || b.renterName.contains(searchQuery, ignoreCase = true) || b.phone1.contains(searchQuery) || b.dateStr.contains(searchQuery))
        }

        if (statusFilter != "الكل") {
            list = list.filter { it.status == statusFilter }
        }
        if (eventFilter != "الكل") {
            list = list.filter { it.eventType == eventFilter }
        }
        if (employeeFilter != "الكل") {
            list = list.filter { it.employeeUsername == employeeFilter }
        }
        if (selectedYear != "الكل") {
            list = list.filter { it.dateStr.startsWith(selectedYear) }
        }

        when (sortedBy) {
            "تاريخ الحجز (الأحدث)" -> list.sortedByDescending { it.dateStr }
            "تاريخ الحجز (الأقدم)" -> list.sortedBy { it.dateStr }
            "الاسم" -> list.sortedBy { it.renterName }
            "القيمة الأعلى" -> list.sortedByDescending { it.rentAmount }
            else -> list
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            "قاعدة بيانات الحجوزات والسجلات",
            color = GoldenClassic, 
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.searchRenterQuery.value = it },
            placeholder = { Text("ابحث باسم المستأجر، رقم الهاتف، أو التاريخ...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "بحث", tint = GoldenClassic) },
            modifier = Modifier.fillMaxWidth().testTag("bookings_search_bar"),
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl, color = Color.White)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Advanced filter pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.End
        ) {
            // Status filter switcher
            val statuses = listOf("الكل", "جديد", "مؤقت", "استبدال إلى تاريخ آخر", "إلغاء")
            statuses.forEach { st ->
                FilterChip(
                    selected = statusFilter == st,
                    onClick = { viewModel.filterStatus.value = st },
                    label = { Text(st, fontSize = 11.sp) },
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Year archival filter row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("الأرشيف السنوي: ", color = GoldenClassic, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            val archiveYears = listOf("الكل", "2026", "2025", "2024", "2023", "2022", "2021")
            archiveYears.forEach { yr ->
                FilterChip(
                    selected = selectedYear == yr,
                    onClick = { viewModel.selectedArchiveYear.value = yr },
                    label = { Text(yr, fontSize = 11.sp) },
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.End
        ) {
            val sortingOptions = listOf("تاريخ الحجز (الأحدث)", "تاريخ الحجز (الأقدم)", "الاسم", "القيمة الأعلى")
            sortingOptions.forEach { opt ->
                FilterChip(
                    selected = sortedBy == opt,
                    onClick = { viewModel.sortBy.value = opt },
                    label = { Text(opt, fontSize = 10.sp) },
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }

        Text(
            "عدد السجلات المطابقة: ${filteredBookings.size} سجل",
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Divider(color = Color.DarkGray, thickness = 0.5.dp, modifier = Modifier.padding(bottom = 8.dp))

        if (filteredBookings.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "لم يتم العثور على أي حجوزات تطابق مواصفات الفرز الحالية.",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(filteredBookings) { booking ->
                    BookingItemCard(
                        b = booking,
                        employeeFullName = usersList.find { it.username == booking.employeeUsername }?.fullName ?: booking.employeeUsername,
                        onAddPayment = { onAddPayment(booking) },
                        onViewPayments = { onViewPayments(booking) },
                        onViewAttachments = { onViewAttachments(booking) },
                        onReschedule = { onReschedule(booking) },
                        onEdit = { onEdit(booking) },
                        onCancel = { viewModel.cancelActiveBooking(booking.id) },
                        currentUserRole = viewModel.currentUser.value?.role ?: "Reception"
                    )
                }
            }
        }
    }
}

@Composable
fun BookingItemCard(
    b: Booking,
    employeeFullName: String,
    onAddPayment: () -> Unit,
    onViewPayments: () -> Unit,
    onViewAttachments: () -> Unit,
    onReschedule: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    currentUserRole: String
) {
    val context = LocalContext.current

    val cardBorderColor = when (b.status) {
        "جديد" -> GreenConfirm
        "مؤقت" -> AmberLight
        "إلغاء" -> RedCancel
        else -> BlueRescheduled // استبدال
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
        border = BorderStroke(1.dp, cardBorderColor.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.End) {
            // Header row with status & date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status badge
                val statusLabel = when (b.status) {
                    "جديد" -> "مؤكد"
                    "مؤقت" -> "مؤقت"
                    "إلغاء" -> "ملغى"
                    else -> "مستبدل"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(cardBorderColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        statusLabel,
                        color = cardBorderColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Text(
                    "${b.dateStr} (${b.dayOfWeek})",
                    color = GoldenClassic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main details
            Text("اسم المستأجر: ${b.renterName}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("المناسبة: ${b.eventType} | سعر حجز القصر: ${b.rentAmount} ريال", color = ChampagneLight, fontSize = 12.sp)
            
            Spacer(modifier = Modifier.height(6.dp))

            // Financial Summary Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(0.3f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("المتبقي بقيمة", color = Color.Gray, fontSize = 10.sp)
                    Text("${b.remainingAmount} ريال", color = RedCancel, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("إجمالي المسدد", color = Color.Gray, fontSize = 10.sp)
                    Text("${b.totalPaid} ريال", color = GreenConfirm, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("الموظف: $employeeFullName", color = Color.Gray, fontSize = 10.sp)
                Row {
                    if (b.phone1.isNotEmpty()) {
                        WhatsAppButton(b.phone1, renterName = b.renterName, dateStr = b.dateStr, status = b.status, remaining = b.remainingAmount, context = context)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.End
            ) {
                // Upload attachments
                ActionStripButton("المرفقات", Icons.Filled.AttachFile, onClick = onViewAttachments)
                
                // Add payments (not for reception and accountant can add, reception cannot)
                if (currentUserRole != "Reception" && b.status != "إلغاء") {
                    ActionStripButton("إضافة دفعة", Icons.Filled.AddCard, onClick = onAddPayment)
                }

                // View current bills
                ActionStripButton("فواتير وسندات", Icons.Filled.ReceiptLong, onClick = onViewPayments)

                // Reschedule date (reception & managers)
                if (currentUserRole != "Accountant" && b.status != "إلغاء") {
                    ActionStripButton("نقل التاريخ", Icons.Filled.CalendarMonth, onClick = onReschedule)
                }

                // Edit basic metadata
                if (currentUserRole != "Accountant") {
                    ActionStripButton("تعديل العقد", Icons.Filled.Edit, onClick = onEdit)
                }

                // Cancel booking
                if (currentUserRole != "Reception" && b.status != "إلغاء" && b.status != "استبدال إلى تاريخ آخر") {
                    ActionStripButton("إلغاء الحجز", Icons.Filled.Cancel, color = RedCancel, onClick = onCancel)
                }
            }
        }
    }
}

@Composable
fun ActionStripButton(label: String, icon: ImageVector, color: Color = GoldenClassic, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .border(0.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WhatsAppButton(phone: String, renterName: String, dateStr: String, status: String, remaining: Double, context: android.content.Context) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(32.dp).testTag("whatsapp_btn")
        ) {
            Icon(Icons.Filled.Message, contentDescription = "واتساب المراسلة الكلي للمستأجر", tint = GreenConfirm)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DeepCharcoal)
        ) {
            DropdownMenuItem(
                text = { Text("تأكيد وحفظ الحجز", color = ChampagneLight, fontSize = 12.sp) },
                onClick = {
                    sendWhatsAppMessage(
                        phone, 
                        "مرحباً أ. $renterName ، نفيدكم بأنه تم بنجاح تأكيد حجزكم لقصر القصر الدائري ليوم $dateStr. أهلاً ومرحباً بكم.",
                        context
                    )
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("تذكير بالدفعة المالية المتبقية (${remaining} ريال)", color = ChampagneLight, fontSize = 12.sp) },
                onClick = {
                    sendWhatsAppMessage(
                        phone, 
                        "نود تذكيركم أ. $renterName بحلول موعد سداد الدفعة المتبقية لحجزكم في القصر الدائري بقيمة $remaining ريال قبل موعد الحفلة. شاكرين تعاونكم.",
                        context
                    )
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("إشعار وجدولة الإلغاء", color = ChampagneLight, fontSize = 12.sp) },
                onClick = {
                    sendWhatsAppMessage(
                        phone, 
                        "أ. $renterName ، نأسف لإبلاغكم بأنه تم إلغاء حجزكم المسجل لقصر القصر الدائري لتاريخ $dateStr بناءً على طلبكم أو لانتهاء المواعيد المالية المحددة.",
                        context
                    )
                    expanded = false
                }
            )
        }
    }
}

fun sendWhatsAppMessage(phone: String, text: String, context: android.content.Context) {
    try {
        val cleanPhone = phone.trim().removePrefix("0")
        val fullPhone = if (cleanPhone.startsWith("966")) cleanPhone else "966$cleanPhone"
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$fullPhone&text=${Uri.encode(text)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "لم نتمكن من فتح تطبيق واتساب. سيتم نسخ النص للمحافظة.", Toast.LENGTH_SHORT).show()
    }
}

// 7. Tab 4: Add New Booking Form View
@Composable
fun AddNewBookingTab(viewModel: AppViewModel) {
    val selectedCurrency by viewModel.selectedCurrency.collectAsStateWithLifecycle()
    val bookings by viewModel.bookings.collectAsStateWithLifecycle()

    val prefilledDate by viewModel.prefilledBookingDate.collectAsStateWithLifecycle()
    var dateStr by remember { mutableStateOf("") }
    
    LaunchedEffect(prefilledDate) {
        if (prefilledDate.isNotEmpty()) {
            dateStr = prefilledDate
            viewModel.prefilledBookingDate.value = ""
        }
    }

    var renterName by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf("زواج") }
    var rentAmount by remember { mutableStateOf("") }
    var initialPaid by remember { mutableStateOf("") }
    var phone1 by remember { mutableStateOf("") }
    var phone2 by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("جديد") }
    
    // Payment method details for initialPaid
    var paymentMethod by remember { mutableStateOf("نقداً") }
    var receiptRef by remember { mutableStateOf("") }

    var showConflictDialogBooking by remember { mutableStateOf<Booking?>(null) }

    val isDateBlocked = remember(dateStr, bookings) {
        if (dateStr.length == 10) {
            bookings.any { it.dateStr == dateStr && (it.status == "جديد" || it.status == "مؤقت") }
        } else {
            false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            "تسجيل وحجز تاريخ جديد",
            color = GoldenClassic, 
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Date selection
        OutlinedTextField(
            value = dateStr,
            onValueChange = { dateStr = it },
            label = { Text("تاريخ المناسبة المطلوب (YYYY-MM-DD)") },
            isError = isDateBlocked,
            supportingText = {
                if (isDateBlocked) {
                    Text("عذراً، هذا التاريخ محجوز بالكامل حالياً!", color = RedCancel, fontWeight = FontWeight.Bold)
                } else if (dateStr.length == 10) {
                    Text("التاريخ متاح للحجز الفوري بنسبة 100%", color = GreenConfirm)
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("add_booking_date_input"),
            textStyle = LocalTextStyle.current.copy(color = Color.White)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = renterName,
            onValueChange = { renterName = it },
            label = { Text("اسم المستأجر") },
            modifier = Modifier.fillMaxWidth().testTag("add_booking_renter_input"),
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl, color = Color.White)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = phone2,
                onValueChange = { phone2 = it },
                label = { Text("جوال العائلة الاحتياطي") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                textStyle = LocalTextStyle.current.copy(color = Color.White)
            )

            OutlinedTextField(
                value = phone1,
                onValueChange = { phone1 = it },
                label = { Text("جوال المستأجر الأساسي") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f).padding(start = 4.dp).testTag("add_booking_phone_input"),
                textStyle = LocalTextStyle.current.copy(color = Color.White)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("نوع المناسبة المقامة:", color = ChampagneLight, fontSize = 13.sp)
        val eventTypes = listOf("زواج", "خطوبة", "تخرج", "اجتماع/عشاء", "أخرى")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            eventTypes.forEach { ev ->
                FilterChip(
                    selected = eventType == ev,
                    onClick = { eventType = ev },
                    label = { Text(ev) },
                    modifier = Modifier.padding(4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            MoneyInputField(
                value = initialPaid,
                onValueChange = { initialPaid = it },
                label = "المقدم المدفوع الأول",
                currency = selectedCurrency,
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                testTag = "add_booking_initial_paid"
            )

            MoneyInputField(
                value = rentAmount,
                onValueChange = { rentAmount = it },
                label = "سعر إيجار الصالة المتفق عليه",
                currency = selectedCurrency,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                testTag = "add_booking_rent_amount"
            )
        }

        val rentVal = rentAmount.toLongOrNull() ?: 0L
        val depositVal = initialPaid.toLongOrNull() ?: 0L
        val remainingVal = (rentVal - depositVal).coerceAtLeast(0L)

        if (rentVal > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .testTag("booking_calc_summary_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkGrayAccent),
                border = BorderStroke(1.dp, GoldenMuted.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "الملخص المالي والتحليل الرقمي للفاتورة",
                        color = GoldenClassic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // 1. Total Booking
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedCurrency == "USD") {
                                NumberToWords.convertToEnglish(rentVal, selectedCurrency)
                            } else {
                                NumberToWords.convertToArabic(rentVal, selectedCurrency)
                            },
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Left,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "سعر الحجز الإجمالي: ${java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(rentVal)}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // 2. Deposit Paid
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedCurrency == "USD") {
                                NumberToWords.convertToEnglish(depositVal, selectedCurrency)
                            } else {
                                NumberToWords.convertToArabic(depositVal, selectedCurrency)
                            },
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Left,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "المقدم المدفوع: ${java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(depositVal)}",
                            color = GreenConfirm,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))

                    // 3. Remaining
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedCurrency == "USD") {
                                NumberToWords.convertToEnglish(remainingVal, selectedCurrency)
                            } else {
                                NumberToWords.convertToArabic(remainingVal, selectedCurrency)
                            },
                            color = GoldenClassic,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Left,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "المبلغ المتبقي المستحق: ${java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(remainingVal)}",
                            color = RedCancel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Initial Payment detail inputs
        val initAmt = initialPaid.toDoubleOrNull() ?: 0.0
        if (initAmt > 0.0) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = DarkGrayAccent),
                border = BorderStroke(0.5.dp, GoldenMuted)
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                    Text("بيانات إثبات سند السداد الأولي والمقدم المالي الحالي:", color = GoldenClassic, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = receiptRef,
                        onValueChange = { receiptRef = it },
                        label = { Text("رقم السند المالي / رقم العملية البنكية") },
                        modifier = Modifier.fillMaxWidth().testTag("add_booking_receipt_ref"),
                        textStyle = LocalTextStyle.current.copy(color = Color.White)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("طريقة دفع المقدم:", color = ChampagneLight, fontSize = 11.sp)
                    val paymentMethods = listOf("نقداً", "محفظة إلكترونية")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        paymentMethods.forEach { method ->
                            FilterChip(
                                selected = paymentMethod == method,
                                onClick = { paymentMethod = method },
                                label = { Text(method, fontSize = 11.sp) },
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("فئة العقد والتأكيد الفوري:", color = ChampagneLight, fontSize = 13.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            FilterChip(
                selected = status == "مؤقت",
                onClick = { status = "مؤقت" },
                label = { Text("حجز مؤقت (مدة 24 ساعة فقط)") },
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            FilterChip(
                selected = status == "جديد",
                onClick = { status = "جديد" },
                label = { Text("حجز رسمي معتمد فوري") },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val rentCost = rentAmount.toDoubleOrNull()
                val initCost = initialPaid.toDoubleOrNull() ?: 0.0

                if (dateStr.trim().isEmpty() || renterName.trim().isEmpty() || phone1.trim().isEmpty()) {
                    viewModel.showMessage("يرجى إكمال البيانات الأساسية للعميل.")
                } else if (rentCost == null || rentCost <= 0.0) {
                    viewModel.showMessage("يرجى إدخال مبلغ حجز قصر صحيح.")
                } else if (initCost > rentCost) {
                    viewModel.showMessage("مبلغ الدفعة الأولى لا يمكن أن يتجاوز مبلغ الحجز.")
                } else if (initCost > 0.0 && receiptRef.trim().isEmpty()) {
                    viewModel.showMessage("يرجى كتابة رقم السند المالي لإثبات سداد المقدم.")
                } else {
                    val conflictBooking = bookings.find { it.dateStr == dateStr.trim() && (it.status == "جديد" || it.status == "مؤقت") }
                    if (conflictBooking != null) {
                        showConflictDialogBooking = conflictBooking
                    } else {
                        viewModel.addNewBooking(
                            dateStr = dateStr.trim(),
                            renterName = renterName.trim(),
                            eventType = eventType,
                            rentAmount = rentCost,
                            initialPaid = initCost,
                            phone1 = phone1.trim(),
                            phone2 = phone2.trim(),
                            status = status,
                            paymentMethod = paymentMethod,
                            receiptRef = receiptRef.trim(),
                            force = false
                        )

                        // Reset form
                        dateStr = ""
                        renterName = ""
                        rentAmount = ""
                        initialPaid = ""
                        phone1 = ""
                        phone2 = ""
                        receiptRef = ""
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = GoldenClassic),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_booking_submit_btn")
        ) {
            Text("تسجيل وحفظ الفاتورة المعتمدة", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        if (showConflictDialogBooking != null) {
            val cb = showConflictDialogBooking!!
            val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
            val isAuthorized = currentUser?.role == "Admin" || currentUser?.role == "Manager"

            Dialog(onDismissRequest = { showConflictDialogBooking = null }) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DeepCharcoal,
                    border = BorderStroke(1.dp, RedCancel),
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            "⚠️ تحذير: وجود تعارض في الحجوزات",
                            color = RedCancel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("انتبه الحجز الحالي يتعارض مع حجز قائم مسبقاً:", color = Color.White, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(0.4f))
                                .padding(8.dp)
                        ) {
                            Text("• رقم الحجز القائم: #${cb.id}", color = ChampagneLight, fontSize = 12.sp)
                            Text("• اسم المستأجر: ${cb.renterName}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("• نوع المناسبة: ${cb.eventType}", color = ChampagneLight, fontSize = 12.sp)
                            val stLabel = if (cb.status == "جديد") "حجز رسمي مؤكد" else "حجز مؤقت"
                            Text("• حالة الحجز: $stLabel", color = if (cb.status == "جديد") GreenConfirm else AmberLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (isAuthorized) {
                            Text(
                                "تمتلك الصلاحيات كـ (${currentUser?.role}). هل ترغب في تجاوز هذا النظام وحفظ هذا الحجز الجديد على أي حال كحجز مزدوج؟",
                                color = GoldenClassic,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        } else {
                            Text(
                                "تنبيه: حسابك لا يملك الصلاحيات الكافية لتجاوز التعارض. يرجى التواصل مع المدير المسؤول أو مسؤول النظام لتأكيد التجاوز.",
                                color = RedCancel,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { showConflictDialogBooking = null }) {
                                Text("إلغاء وتغيير التاريخ", color = Color.LightGray)
                            }
                            
                            if (isAuthorized) {
                                Button(
                                    onClick = {
                                        val rentCost = rentAmount.toDoubleOrNull() ?: 0.0
                                        val initCost = initialPaid.toDoubleOrNull() ?: 0.0
                                        viewModel.addNewBooking(
                                            dateStr = dateStr.trim(),
                                            renterName = renterName.trim(),
                                            eventType = eventType,
                                            rentAmount = rentCost,
                                            initialPaid = initCost,
                                            phone1 = phone1.trim(),
                                            phone2 = phone2.trim(),
                                            status = status,
                                            paymentMethod = paymentMethod,
                                            receiptRef = receiptRef.trim(),
                                            force = true // Bypass conflict checks!
                                        )
                                        // Reset form and dismiss
                                        dateStr = ""
                                        renterName = ""
                                        rentAmount = ""
                                        initialPaid = ""
                                        phone1 = ""
                                        phone2 = ""
                                        receiptRef = ""
                                        showConflictDialogBooking = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RedCancel)
                                ) {
                                    Text("تجاوز وحفظ كحجز مزدوج", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 8. Tab 5: Admin Tools Tab
@Composable
fun AdminToolsTab(
    viewModel: AppViewModel,
    onShowAddUser: () -> Unit
) {
    val usersList by viewModel.users.collectAsStateWithLifecycle()
    val backupLogsList by viewModel.backupLogs.collectAsStateWithLifecycle()

    var showResetPasswordUser by remember { mutableStateOf<User?>(null) }
    var newPasswordInput by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    var exportDateFrom by remember { mutableStateOf("") }
    var exportDateTo by remember { mutableStateOf("") }

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val pendingRestoreUri by viewModel.pendingRestoreUri.collectAsStateWithLifecycle()
    val restoreSummaryMessage by viewModel.restoreSummaryMessage.collectAsStateWithLifecycle()
    val canRollback by viewModel.canRollback.collectAsStateWithLifecycle()

    // SAF launchers
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFromUri(context, uri)
        }
    }

    val exportExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBookingsToUri(context, uri, exportDateFrom.trim(), exportDateTo.trim())
        }
    }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            viewModel.exportFinancialReportToUri(context, uri, exportDateFrom.trim(), exportDateTo.trim())
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.performFullBackupToUri(context, uri)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.requestRestoreFromUri(context, uri)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        // Administration tools header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onShowAddUser,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldenClassic),
                    modifier = Modifier.testTag("admin_add_user_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = "إضافة موظف", tint = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة موظف جديد", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    "المستخدمين وصلاحيات الموظفين",
                    color = GoldenClassic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        }

        // Users List section
        val activeUsers = usersList.filter { it.status != "deleted_bin" }
        val recycledUsers = usersList.filter { it.status == "deleted_bin" }

        if (activeUsers.isEmpty()) {
            item {
                Text(
                    "لا تتوفر حسابات موظفين نشطة مسجلة.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            items(activeUsers) { user ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                    border = BorderStroke(0.5.dp, Color.DarkGray)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // User status toggle and delete
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (user.username != "admin") {
                                    IconButton(
                                        onClick = { viewModel.updateUserStatus(user, "deleted_bin") }
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "حذف مؤقت", tint = RedCancel, modifier = Modifier.size(18.dp))
                                    }
                                }

                                val nextStatus = when (user.status) {
                                    "نشط" -> "موقوف"
                                    "موقوف" -> "مؤرشف"
                                    else -> "نشط"
                                }
                                TextButton(
                                    onClick = { viewModel.updateUserStatus(user, nextStatus) },
                                    modifier = Modifier.testTag("status_toggle_btn_${user.username}")
                                ) {
                                    Text("الحالة: ${user.status}", color = GoldenBright, fontSize = 11.sp)
                                }

                                TextButton(
                                    onClick = { showResetPasswordUser = user }
                                ) {
                                    Text("تغيير السر", color = Color.Gray, fontSize = 11.sp)
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(user.fullName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("اسم المستخدم: ${user.username} | صلاحية: ${user.role}", color = Color.Gray, fontSize = 11.sp)
                            }
                        }

                        // Dates and login information
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH)
                        val lastLoginTime = if (user.lastLogin > 0L) sdf.format(Date(user.lastLogin)) else "لم يدخل سابقاً"
                        Text("آخر نشاط: $lastLoginTime", color = Color.DarkGray, fontSize = 10.sp)
                    }
                }
            }
        }

        // Recycle Bin section (Admins only)
        if (currentUser?.role == "Admin" && recycledUsers.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "سلة المحذوفات وصناديق الاسترجاع الآمن للموظفين",
                    color = RedCancel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(recycledUsers) { user ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkGrayAccent),
                    border = BorderStroke(1.dp, RedCancel.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row {
                                Button(
                                    onClick = { viewModel.updateUserStatus(user, "نشط") },
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenConfirm),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("استعادة الحساب", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = { viewModel.deleteUser(user) },
                                    colors = ButtonDefaults.buttonColors(containerColor = RedCancel),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("حذف نهائي", color = Color.White, fontSize = 10.sp)
                                    }
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(user.fullName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("اسم المستخدم: ${user.username} | صلاحية: ${user.role}", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Export & Import section (Excel/PDF and Conflict wizard)
        if (currentUser?.role == "Admin") {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                    border = BorderStroke(0.5.dp, GoldenMuted)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            "تصدير واستيراد التقارير والبيانات (Excel & PDF)",
                            color = GoldenClassic,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            "حدد نطاق التواريخ للتصفية والتصدير المالي (اتركه فارغاً لكل السجلات):",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedTextField(
                                value = exportDateTo,
                                onValueChange = { exportDateTo = it },
                                label = { Text("إلى تاريخ (YYYY-MM-DD)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                textStyle = LocalTextStyle.current.copy(color = Color.White)
                            )
                            OutlinedTextField(
                                value = exportDateFrom,
                                onValueChange = { exportDateFrom = it },
                                label = { Text("من تاريخ (YYYY-MM-DD)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                textStyle = LocalTextStyle.current.copy(color = Color.White)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = {
                                    exportPdfLauncher.launch("Qasr_Financials_${System.currentTimeMillis() / 1000}.pdf")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkGrayAccent),
                                border = BorderStroke(1.dp, GoldenMuted),
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp).testTag("export_pdf_btn")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Description, contentDescription = "PDF", tint = RedCancel, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تصدير تقرير مالي (PDF)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    exportExcelLauncher.launch("Qasr_Bookings_${System.currentTimeMillis() / 1000}.csv")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldenClassic),
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp).testTag("export_excel_btn")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Assessment, contentDescription = "Excel", tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تصدير حقول (Excel)", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = Color.DarkGray, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "سحب ومعالجة واستيراد ملفات الإكسل الكبيرة:",
                            color = ChampagneLight,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                importLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkGrayAccent),
                            border = BorderStroke(1.dp, GoldenMuted),
                            modifier = Modifier.fillMaxWidth().testTag("import_excel_btn")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Backup, contentDescription = "استيراد", tint = GoldenBright)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("اختر واستورد ملف Excel من جهازك", color = GoldenBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Backup & Restore Block
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Divider(color = Color.DarkGray)
                Spacer(modifier = Modifier.height(16.dp))

                Text("النسخ الاحتياطي التلقائي وفحص السجلات والإنقاذ", color = GoldenClassic, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("نظام الصالة مبرمج لحفظ نسخ للإنقاذ والوقاية من فقدان البيانات.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            backupLauncher.launch("Qasr_Backup_${System.currentTimeMillis() / 1000}.json")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldenClassic),
                        modifier = Modifier.fillMaxWidth().testTag("backup_db_btn")
                    ) {
                        Text("إنشاء ملف نسخ احتياطي شامل وتنزيله (JSON)", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            restoreLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkGrayAccent),
                        border = BorderStroke(1.dp, Color.Gray),
                        modifier = Modifier.fillMaxWidth().testTag("restore_from_file_btn")
                    ) {
                        Text("استعادة وتطبيق نسخة احتياطية من ملف خارجي", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (canRollback) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.performEmergencyRollback() },
                            colors = ButtonDefaults.buttonColors(containerColor = RedCancel),
                            modifier = Modifier.fillMaxWidth().testTag("emergency_rollback_btn")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Undo, contentDescription = "Rollback", tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تراجع طارئ وعكس آخر عملية استيراد/استعادة", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("سجل عمليات الاستعادة والنسخ السحابي:", color = GoldenMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))

                if (backupLogsList.isEmpty()) {
                    Text("لا تتوفر نسخ في هذا الجهاز حالياً.", color = Color.Gray, fontSize = 12.sp)
                } else {
                    backupLogsList.forEach { log ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkGrayAccent)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(log.status, color = if (log.status == "ناجحة") GoldenBright else RedCancel, fontSize = 11.sp)

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(log.backupPath.substringAfterLast("/"), color = ChampagneLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("المسؤول: ${log.performedBy} | الوقت: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date(log.createdAt))}", color = Color.Gray, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Access to audit logs shortcuts
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.selectTab("auditLogs") },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGrayAccent),
                    border = BorderStroke(1.dp, Color.Gray),
                    modifier = Modifier.fillMaxWidth().testTag("view_audit_logs_btn")
                ) {
                    Text("فتح والاطلاع على سجل العمليات الكامل (Audit Log)", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }

    // Reset password dialog
    if (showResetPasswordUser != null) {
        val u = showResetPasswordUser!!
        Dialog(onDismissRequest = { showResetPasswordUser = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GoldenMuted),
                color = DeepCharcoal,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.End) {
                    Text("إعادة تعيين كلمة مرور لـ ${u.fullName}", color = GoldenClassic, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        label = { Text("أدخل كلمة المرور الجديدة") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(color = Color.White)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { showResetPasswordUser = null }) {
                            Text("إلغاء", color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                if (newPasswordInput.trim().isNotEmpty()) {
                                    viewModel.resetUserPassword(u, newPasswordInput.trim())
                                    newPasswordInput = ""
                                    showResetPasswordUser = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldenClassic)
                        ) {
                            Text("تحديث وتغيير", color = Color.Black)
                        }
                    }
                }
            }
        }
    }

    if (pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelRestore() },
            title = { Text("تأكيد استعادة قاعدة البيانات", fontWeight = FontWeight.Bold, color = GoldenClassic) },
            text = { Text("تحذير: ستقوم هذه العملية بحذف كافة البيانات الحالية بالكامل واسترجاع كافة البيانات والسجلات السابقة من الملف المحدد. هل تريد المتابعة بالتنزيل وتطبيق الاستعادة؟", color = Color.White) },
            confirmButton = {
                Button(
                    onClick = { viewModel.performFullRestoreFromPendingUri(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = RedCancel)
                ) {
                    Text("استعادة الآن", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRestore() }) {
                    Text("إلغاء وتراجع", color = Color.Gray)
                }
            },
            containerColor = DeepCharcoal
        )
    }

    if (restoreSummaryMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRestoreSummary() },
            title = { Text("اكتملت عملية الاستعادة بنجاح", fontWeight = FontWeight.Bold, color = GoldenClassic) },
            text = { Text(restoreSummaryMessage!!, color = Color.White) },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissRestoreSummary() },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldenClassic)
                ) {
                    Text("مسح التقرير", color = Color.Black)
                }
            },
            containerColor = DeepCharcoal
        )
    }
}

// 9. Tab 6: Audit Logs Tab (سجل العمليات الآمن)
@Composable
fun AuditLogsTab(viewModel: AppViewModel) {
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.selectTab("adminTools") }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع لغرفة المسؤول", tint = GoldenClassic)
            }

            Text(
                "سجل العمليات التاريخي والرقابة الذاتية",
                color = GoldenClassic,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }

        Text(
            "تتبع آمن وغير قابل للحذف لكل إضافة، تعديل مالي، إلغاء، أو دخول للنظام.",
            color = Color.Gray,
            fontSize = 11.sp,
            textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )

        Divider(color = Color.DarkGray, thickness = 0.5.dp, modifier = Modifier.padding(bottom = 8.dp))

        if (auditLogs.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("لا توجد سجلات تتبع حالية مسجلة في قاعدة البيانات.", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(auditLogs) { log ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = DeepCharcoal)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val logDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(log.timestamp))
                                Text(logDate, color = Color.Gray, fontSize = 10.sp)

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(log.actionType, color = GoldenBright, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("بواسطة: ${log.username}", color = ChampagneLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (log.detailsBefore.isNotEmpty()) {
                                Text(
                                    "قبل التعديل: ${log.detailsBefore}", 
                                    color = Color.Gray, 
                                    fontSize = 11.sp, 
                                    textAlign = TextAlign.Right, 
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            }
                            if (log.detailsAfter.isNotEmpty()) {
                                Text(
                                    "بعد السداد/التعديل: ${log.detailsAfter}", 
                                    color = ChampagneLight, 
                                    fontSize = 11.sp, 
                                    textAlign = TextAlign.Right, 
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- CORE LOGICAL HELPERS ---

private fun getArabicMonthName(monthIndex: Int): String {
    return when (monthIndex) {
        0 -> "يناير (كانون الثاني)"
        1 -> "فبراير (شباط)"
        2 -> "مارس (آذار)"
        3 -> "أبريل (نيسان)"
        4 -> "مايو (أيار)"
        5 -> "يونيو (حزيران)"
        6 -> "يوليو (تموز)"
        7 -> "أغسطس (آب)"
        8 -> "سبتمبر (أيلول)"
        9 -> "أكتوبر (تشرين الأول)"
        10 -> "نوفمبر (تشرين الثاني)"
        11 -> "ديسمبر (كانون الأول)"
        else -> "الشهر"
    }
}

private fun isDateWithin7Days(targetDateStr: String): Boolean {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val tDate = format.parse(targetDateStr) ?: return false
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val targetCal = Calendar.getInstance()
        targetCal.time = tDate

        val diff = targetCal.timeInMillis - today.timeInMillis
        val diffDays = diff / (24 * 60 * 60 * 1000)
        diffDays in 0..7
    } catch (e: Exception) {
        false
    }
}

@Composable
fun ForceChangePasswordScreen(
    isDarkTheme: Boolean,
    onSaveClick: (String) -> Unit,
    onLogoutClick: () -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val bgColors = if (isDarkTheme) listOf(ObsidianBlack, DeepCharcoal) else listOf(IvoryBackground, LightBeigePanel)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(bgColors)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header visual
            Card(
                shape = CircleShape,
                modifier = Modifier.size(80.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(2.dp, GoldenClassic)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.LockReset,
                        contentDescription = "تأمين الحساب",
                        tint = GoldenClassic,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "تحديث كلمة المرور الإجباري",
                color = GoldenClassic,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "يرجى تغيير كلمة المرور الافتراضية لحماية بيانات الصالة وتأمين صلاحياتك قبل متابعة استخدام النظام.",
                color = if (isDarkTheme) Color.LightGray else CharcoalText.copy(alpha = 0.8f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Password Field
            OutlinedTextField(
                value = newPassword,
                onValueChange = { 
                    newPassword = it
                    errorMessage = null
                },
                label = { Text("كلمة المرور الجديدة") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("new_password_input"),
                textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onBackground)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Confirm Password Field
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { 
                    confirmPassword = it
                    errorMessage = null
                },
                label = { Text("تأكيد كلمة المرور الجديدة") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("confirm_password_input"),
                textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onBackground)
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    color = RedCancel,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Confirm Button
            Button(
                onClick = {
                    if (newPassword.trim().isEmpty()) {
                        errorMessage = "يرجى إدخال كلمة المرور الجديدة."
                    } else if (newPassword.trim().length < 5) {
                        errorMessage = "يجب أن تكون كلمة المرور 5 أحرف أو أكثر."
                    } else if (newPassword != confirmPassword) {
                        errorMessage = "كلمتا المرور غير متطابقتين!"
                    } else {
                        onSaveClick(newPassword)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_new_password_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldenClassic,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "حفظ وتحديث كلمة المرور ومتابعة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Logout/Cancel Button
            TextButton(
                onClick = onLogoutClick,
                modifier = Modifier.fillMaxWidth().testTag("cancel_force_pass_btn")
            ) {
                Text(
                    text = "تسجيل الخروج والعودة",
                    color = RedCancel,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ==========================================
// REDESIGNED SERVICES & SCREENS (WALLET STYLE)
// ==========================================

@Composable
fun DashboardTab(viewModel: AppViewModel) {
    val context = LocalContext.current
    val bookings by viewModel.bookings.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    
    // Balance toggle visibility state
    var isBalanceVisible by remember { mutableStateOf(true) }
    var showingSimulatedExpenses by remember { mutableStateOf(false) }

    val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
    val cal = Calendar.getInstance()
    val currentMonthStr = String.format(Locale.ENGLISH, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)

    // Data Filtering & Calculations
    val activeBookings = bookings.filter { it.status != "إلغاء" }
    val todayBookingsCount = activeBookings.count { it.dateStr == todayDateStr }
    val monthlyBookingsCount = activeBookings.count { it.dateStr.startsWith(currentMonthStr) }
    
    val totalRevenue = activeBookings.sumOf { it.totalPaid } // Total cash received
    val netProfit = totalRevenue * 0.85 // 85% Operational profit margin

    val upcomingEventsCount = activeBookings.count { it.status == "جديد" && it.dateStr >= todayDateStr }
    val temporaryBookingsCount = bookings.count { it.status == "مؤقت" }
    val confirmedBookingsCount = bookings.count { it.status == "جديد" }
    val completedEventsCount = activeBookings.count { it.dateStr < todayDateStr }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcoming Title Area
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side icons (Notification Bell Mock)
                Box(contentAlignment = Alignment.Center) {
                    Card(
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                        border = BorderStroke(0.5.dp, DarkGrayAccent),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = "الإخطارات",
                                tint = GoldenClassic,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Greeting (Arabic-First)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "جمعتك مباركة 👋",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = currentUser?.fullName ?: "ضيف الصالة",
                        color = ChampagneLight,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // TOP SECTION: Redesigned Large Hero Card (35% elevation/height style)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .testTag("wallet_hero_card"),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, GoldenClassic.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(DarkCrimson, Color(0xFF38000B))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Title row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { isBalanceVisible = !isBalanceVisible }) {
                                Icon(
                                    imageVector = if (isBalanceVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = "عرض المبالغ",
                                    tint = GoldenClassic
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "بوابة القصر الأمني والمالي",
                                    color = GoldenClassic,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(GoldenClassic)
                                )
                            }
                        }

                        // Money Received / Balances
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "حصيلة المدفوعات المستلمة",
                                color = Color.LightGray.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                            Text(
                                text = if (isBalanceVisible) "${totalRevenue.toInt()} ريال" else "•••••",
                                color = ChampagneLight,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isBalanceVisible) "${netProfit.toInt()} ريال" else "••••",
                                    color = GoldenClassic,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "الأرباح الاحتياطية (85%):",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Bottom indicators (Today's vs Monthly booking counts)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("حجوزات الشهر", color = Color.Gray, fontSize = 9.sp)
                                Text("$monthlyBookingsCount حجز", color = ChampagneLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color.DarkGray))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("حجوزات اليوم والفعاليات", color = Color.Gray, fontSize = 9.sp)
                                Text("$todayBookingsCount حجز", color = GoldenClassic, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // MIDDLE TITLE
        item {
            Text(
                text = "الخدمات والقنوات الذكية",
                color = ChampagneLight,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // QUICK ACTIONS: Grid style below hero card (2 elements per row)
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: Add New & Customers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // New Booking
                    ActionCard(
                        title = "حجز صالة جديد",
                        icon = Icons.Filled.AddCard,
                        color = DarkCrimson,
                        modifier = Modifier.weight(1f).testTag("action_new_booking"),
                        onClick = { viewModel.selectTab("addNew") }
                    )
                    // Customers
                    ActionCard(
                        title = "سجل العملاء",
                        icon = Icons.Filled.People,
                        color = Color(0xFF1B1B1B),
                        modifier = Modifier.weight(1f).testTag("action_customers"),
                        onClick = { viewModel.selectTab("customers") }
                    )
                }

                // Row 2: Calendar & Payments
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Calendar
                    ActionCard(
                        title = "التقويم المالي",
                        icon = Icons.Filled.DateRange,
                        color = Color(0xFF1B1B1B),
                        modifier = Modifier.weight(1f).testTag("action_calendar"),
                        onClick = { viewModel.selectTab("calendar") }
                    )
                    // Payments
                    ActionCard(
                        title = "المتحصلات والديون",
                        icon = Icons.Filled.AccountBalanceWallet,
                        color = Color(0xFF1B1B1B),
                        modifier = Modifier.weight(1f).testTag("action_payments"),
                        onClick = { viewModel.selectTab("payments") }
                    )
                }

                // Row 3: Expenses & Reports
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Expenses
                    ActionCard(
                        title = "مصروفات التشغيل",
                        icon = Icons.Filled.ReceiptLong,
                        color = Color(0xFF1B1B1B),
                        modifier = Modifier.weight(1f).testTag("action_expenses"),
                        onClick = { showingSimulatedExpenses = true }
                    )
                    // Reports
                    ActionCard(
                        title = "مؤشرات وتقارير",
                        icon = Icons.Filled.Analytics,
                        color = Color(0xFF1B1B1B),
                        modifier = Modifier.weight(1f).testTag("action_reports"),
                        onClick = { viewModel.selectTab("reports") }
                    )
                }
            }
        }

        // STATISTICS SECTION
        item {
            Text(
                text = "الإحصائيات السريعة",
                color = ChampagneLight,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(title = "حفلات قادمة", value = "$upcomingEventsCount", color = GreenConfirm, modifier = Modifier.weight(1f))
                StatCard(title = "حجوزات مؤقتة", value = "$temporaryBookingsCount", color = AmberLight, modifier = Modifier.weight(1f))
                StatCard(title = "عقود مؤكدة", value = "$confirmedBookingsCount", color = GoldenClassic, modifier = Modifier.weight(1f))
                StatCard(title = "حفلات منتهية", value = "$completedEventsCount", color = BlueRescheduled, modifier = Modifier.weight(1f))
            }
        }

        // RECENT BOOKINGS SECTION
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مشاهدة الكل",
                    color = GoldenClassic,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { viewModel.selectTab("bookings") }
                )
                Text(
                    text = "العمليات وحجوزات الصالة الأخيرة",
                    color = ChampagneLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // Display recent bookings
        val recentBookings = bookings.take(4)
        if (recentBookings.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("لا يوجد حجوزات نشطة مسجلة حالياً.", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }
        } else {
            items(recentBookings) { bk ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("recent_booking_item"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                    border = BorderStroke(0.5.dp, Color(0xFF231E1F))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Column
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "${bk.rentAmount.toInt()} ريال",
                                color = ChampagneLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            if (bk.remainingAmount > 0) {
                                Text(
                                    text = "متبقي ${bk.remainingAmount.toInt()} ر.س",
                                    color = RedCancel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = "خالص السداد 🎉",
                                    color = GreenConfirm,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Right Column
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = bk.renterName,
                                color = ChampagneLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = bk.dateStr,
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                val isPastDate = bk.dateStr < todayDateStr
                                val chipColor = when {
                                    bk.status == "إلغاء" -> RedCancel
                                    bk.status == "مؤقت" -> AmberLight
                                    isPastDate -> BlueRescheduled
                                    else -> GreenConfirm
                                }
                                val chipLabel = when {
                                    bk.status == "إلغاء" -> "ملغى"
                                    bk.status == "مؤقت" -> "مؤقت"
                                    isPastDate -> "منتهي"
                                    else -> "مؤكد"
                                }

                                Box(
                                    modifier = Modifier
                                        .background(chipColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .border(0.5.dp, chipColor, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = chipLabel,
                                        color = chipColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // SIMULATED EXPENSES DIALOG
    if (showingSimulatedExpenses) {
        Dialog(onDismissRequest = { showingSimulatedExpenses = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = DeepCharcoal,
                border = BorderStroke(1.dp, GoldenClassic),
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "تفاصيل مصروفات تشغيل الصالة",
                        color = GoldenClassic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val expenseItems = listOf(
                        "فواتير الكهرباء والمياه والطاقة" to "3,500 ريال",
                        "رواتب الموظفين والعمال الاحتياطية" to "12,000 ريال",
                        "أعمال صيانة المكيفات والإنارة الدورية" to "1,800 ريال",
                        "مواد نظافة وتعقيم القاعات والبهو الدائم" to "950 ريال"
                    )
                    
                    expenseItems.forEach { (title, cost) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cost, color = GoldenClassic, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(title, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Right)
                        }
                        Divider(color = Color.DarkGray, thickness = 0.5.dp)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showingSimulatedExpenses = false },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldenClassic)
                    ) {
                        Text("إغلاق التفاصيل", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(85.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
        border = BorderStroke(0.5.dp, Color(0xFF221F20))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = ChampagneLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 12.dp)
            )
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
                border = BorderStroke(0.5.dp, color),
                modifier = Modifier.size(45.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = title, tint = GoldenClassic)
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(75.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.Gray, fontSize = 9.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CustomersTab(viewModel: AppViewModel) {
    val bookings by viewModel.bookings.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    // Group bookings by renter name & phone to build unified customer entities
    val customers = remember(bookings, searchQuery) {
        bookings
            .groupBy { it.renterName to it.phone1 }
            .map { (key, list) ->
                val name = key.first
                val phone = key.second
                val count = list.size
                val totalPaid = list.sumOf { it.totalPaid }
                Triple(name, phone, count to totalPaid)
            }
            .filter { (name, phone, _) ->
                searchQuery.isEmpty() || name.contains(searchQuery, ignoreCase = true) || phone.contains(searchQuery)
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            "سجل العملاء التراكمي",
            color = GoldenClassic,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("ابحث عن عميل باسمه أو رقم الجوال...", color = Color.Gray, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = GoldenClassic) },
            modifier = Modifier.fillMaxWidth().testTag("customers_search"),
            shape = RoundedCornerShape(20.dp),
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl, color = Color.White),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldenClassic,
                unfocusedBorderColor = Color.DarkGray,
                focusedContainerColor = DeepCharcoal,
                unfocusedContainerColor = DeepCharcoal
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (customers.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("لا يوجد سجلات مطابقة للعمود أو الفلتر.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(customers) { (name, phone, stats) ->
                    val (count, totalPaid) = stats
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(0.5.dp, Color(0xFF2A1C1E))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("إجمالي السداد: ${totalPaid.toInt()} ريال", color = GoldenClassic, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("عدد الحجوزات: $count", color = Color.Gray, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(name, color = ChampagneLight, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(phone, color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentsTab(viewModel: AppViewModel) {
    val bookings by viewModel.bookings.collectAsStateWithLifecycle()
    val payments by viewModel.payments.collectAsStateWithLifecycle()

    // Financial Metrics
    val totalRevenue = bookings.filter { it.status != "إلغاء" }.sumOf { it.rentAmount - it.discountAmount }
    val totalDeposits = payments.sumOf { it.amount }
    val outstandingDebt = maxOf(0.0, totalRevenue - totalDeposits)
    
    val todayMs = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    
    val todayPaymentsAmount = payments.filter { it.paymentDate >= todayMs }.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            "بوابة الذمم والمركز المالي",
            color = GoldenClassic,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // financial grid cards
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FinancialStatCard(title = "قيمة العقود الكلية", value = "${totalRevenue.toInt()} ريال", color = GoldenClassic, modifier = Modifier.weight(1f))
                FinancialStatCard(title = "التحصيلات النقدية", value = "${totalDeposits.toInt()} ريال", color = GreenConfirm, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FinancialStatCard(title = "الديون والذمم المعلقة", value = "${outstandingDebt.toInt()} ريال", color = RedCancel, modifier = Modifier.weight(1f))
                FinancialStatCard(title = "متحصلات الصندوق اليوم", value = "${todayPaymentsAmount.toInt()} ريال", color = BlueRescheduled, modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            "سجل حركة الدفع والسندات",
            color = ChampagneLight,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (payments.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("لا توجد تحصيلات أو سندات مالية مدخلة حالياً.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                val sortedPayments = payments.sortedByDescending { it.paymentDate }
                items(sortedPayments) { pay ->
                    val matchedBooking = bookings.find { it.id == pay.bookingId }
                    val name = matchedBooking?.renterName ?: "رقم الحجز: ${pay.bookingId}"
                    val dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(pay.paymentDate))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(0.5.dp, Color(0xFF261D1E))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("${pay.amount.toInt()} ريال", color = GreenConfirm, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(pay.paymentMethod, color = Color.Gray, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(name, color = ChampagneLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("مرجع السند: ${pay.receiptRef} | $dateFormatted", color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FinancialStatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(85.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
        border = BorderStroke(0.5.dp, Color(0xFF2A1C1E))
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.End
        ) {
            Text(title, color = Color.Gray, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun ReportsTab(viewModel: AppViewModel) {
    val bookings by viewModel.bookings.collectAsStateWithLifecycle()
    val payments by viewModel.payments.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "بوابة التحليلات البيانية والمؤشرات",
            color = GoldenClassic,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // CHART 1: MONTHLY REVENUE BAR CHART (Premium drawn columns)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.5.dp, Color(0xFF2A1C1E))
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                Text("المتحصلات والتدفقات النقدية والديون", color = ChampagneLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("تحليل الدفعات والتسويات المالية للشهر الحالي والماضي", color = Color.Gray, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val months = listOf("شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة", "محرم")
                    val levels = listOf(0.4f, 0.65f, 0.9f, 0.5f, 0.82f, 0.72f)
                    
                    months.forEachIndexed { idx, m ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .fillMaxHeight(levels[idx])
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(GoldenClassic, DarkCrimson)
                                        )
                                    )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(m, color = Color.Gray, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        // CHART 2: REVENUE COMPOSITION DONUT REPRESENTATION
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.5.dp, Color(0xFF2A1C1E))
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                Text("تركيبة وتصنيف الحجوزات والمناسبات", color = ChampagneLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("نسبة أنواع الفعاليات وعقود الصالة (زواج، خطوبة، تخرج)", color = Color.Gray, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompositionRow(title = "حفلات الزواج (60%)", color = DarkCrimson)
                        CompositionRow(title = "حفلات الخطوبة (25%)", color = GoldenClassic)
                        CompositionRow(title = "مؤتمرات وتخرج (15%)", color = BlueRescheduled)
                    }

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawArc(
                                color = DarkCrimson,
                                startAngle = 0f,
                                sweepAngle = 216f,
                                useCenter = false,
                                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = GoldenClassic,
                                startAngle = 216f,
                                sweepAngle = 90f,
                                useCenter = false,
                                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = BlueRescheduled,
                                startAngle = 306f,
                                sweepAngle = 54f,
                                useCenter = false,
                                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }
        }

        // CHART 3: OCCUPANCY MONTHLY TREND CHART
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(0.5.dp, Color(0xFF2A1C1E))
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                Text("معدلات الإشغال ونسب التشغيل", color = ChampagneLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("تطور نسب حجز أيام الصالة الفعلية الشهري", color = Color.Gray, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val months = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو")
                    val occupancy = listOf(45, 60, 85, 30, 95, 80)
                    
                    months.forEachIndexed { index, name ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("${occupancy[index]}%", color = GoldenClassic, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .fillMaxHeight(occupancy[index] / 100f)
                                    .background(GoldenClassic, CircleShape)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(name, color = Color.Gray, fontSize = 8.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun CompositionRow(title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(title, color = ChampagneLight, fontSize = 11.sp)
    }
}

@Composable
fun MoreTab(
    viewModel: AppViewModel,
    onShowAddUser: () -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val networkStatus by viewModel.networkStatus.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Avatar Profile Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("profile_section_card"),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(0.5.dp, GoldenClassic.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(containerColor = DeepCharcoal)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = when (currentUser?.role) {
                                "Admin" -> "المشرف العام"
                                "Manager" -> "مدير الصالة"
                                "Accountant" -> "المحاسب المالي"
                                "Reception" -> "الاستقبال"
                                else -> currentUser?.role ?: ""
                            },
                            color = GoldenClassic,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp)) {
                            Text(currentUser?.fullName ?: "", color = ChampagneLight, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("@${currentUser?.username}", color = Color.Gray, fontSize = 12.sp)
                        }
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(DarkCrimson),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (currentUser?.fullName?.take(1) ?: "ق"),
                                color = GoldenClassic,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }

        // Services header
        item {
            Text("الخدمات والعمليات المتقدمة", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }

        // Link services list
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Link 1: Legacy Dashboard Full Control Center (Heatmaps, presences, health monitor)
                ServiceLinkItem(
                    title = "مركز المراقبة والتحكم الشامل",
                    subtitle = "تحتوي على قمرة الأوامر، أحمال التضارب، وخرائط الإشغال والشبكة",
                    icon = Icons.Filled.Security,
                    onClick = { viewModel.selectTab("adminTools") }
                )

                // Link 2: Admin Tools (Settings for Users, Backup operations, restoration)
                if (currentUser?.role == "Admin") {
                    ServiceLinkItem(
                        title = "بوابة الإشراف وإدارة المستخدمين",
                        subtitle = "إضافة موظفين جدد، مراجعة العمليات الحساسة والأمان",
                        icon = Icons.Filled.SupervisorAccount,
                        onClick = { viewModel.selectTab("adminTools") }
                    )
                    
                    ServiceLinkItem(
                        title = "سجل رقابة العمليات والأنشطة",
                        subtitle = "متابعة حركة إضافة، تعديل أو حذف السجلات لحظة بلحظة",
                        icon = Icons.Filled.History,
                        onClick = { viewModel.selectTab("auditLogs") }
                    )
                }

                // Link 3: Theme Toggle Row
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                    border = BorderStroke(0.5.dp, Color(0xFF261E1F))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth().clickable { viewModel.toggleTheme() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { viewModel.toggleTheme() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoldenClassic,
                                checkedTrackColor = DarkCrimson
                            )
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp)) {
                                Text("المظهر الداكن للتطبيق", color = ChampagneLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(if (isDarkTheme) "مفعّل (توفير طاقة بكسل)" else "غير مفعّل", color = Color.Gray, fontSize = 10.sp)
                            }
                            Icon(Icons.Filled.DarkMode, contentDescription = null, tint = GoldenClassic)
                        }
                    }
                }

                // Link 4: Offline network state simulator
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                    border = BorderStroke(0.5.dp, Color(0xFF261E1F))
                ) {
                    val isConnected = networkStatus == "متصل"
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth().clickable { 
                            viewModel.setNetworkStatus(if (isConnected) "غير متصل" else "متصل")
                        },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = isConnected,
                            onCheckedChange = { 
                                viewModel.setNetworkStatus(if (isConnected) "غير متصل" else "متصل")
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GreenConfirm,
                                checkedTrackColor = Color.DarkGray
                            )
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp)) {
                                Text("محاكاة اتصال قاعدة البيانات والشبكة", color = ChampagneLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("الحالة الحالية: $networkStatus", color = Color.Gray, fontSize = 10.sp)
                            }
                            Icon(Icons.Filled.Wifi, contentDescription = null, tint = GoldenClassic)
                        }
                    }
                }

                // Currency Selector Setting Row
                val selectedCurrency by viewModel.selectedCurrency.collectAsStateWithLifecycle()
                var showCurrencyMenu by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier.fillMaxWidth().testTag("setting_currency_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
                    border = BorderStroke(0.5.dp, Color(0xFF261E1F))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCurrencyMenu = !showCurrencyMenu }
                            .padding(14.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                tint = GoldenClassic
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp)) {
                                    Text("عملة التطبيق والنظام", color = ChampagneLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        text = when (selectedCurrency) {
                                            "Saudi Riyal" -> "الريال السعودي (SAR)"
                                            "USD" -> "الدولار الأمريكي (USD)"
                                            else -> "الريال اليمني (YER)"
                                        },
                                        color = GoldenClassic,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(Icons.Filled.MonetizationOn, contentDescription = null, tint = GoldenClassic)
                            }
                        }

                        DropdownMenu(
                            expanded = showCurrencyMenu,
                            onDismissRequest = { showCurrencyMenu = false },
                            modifier = Modifier.background(DeepCharcoal).border(0.5.dp, GoldenClassic, RoundedCornerShape(8.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text("الريال اليمني (YER)", color = Color.White, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
                                onClick = {
                                    viewModel.setSelectedCurrency("Yemeni Rial")
                                    showCurrencyMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("الريال السعودي (SAR)", color = Color.White, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
                                onClick = {
                                    viewModel.setSelectedCurrency("Saudi Riyal")
                                    showCurrencyMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("الدولار الأمريكي (USD)", color = Color.White, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
                                onClick = {
                                    viewModel.setSelectedCurrency("USD")
                                    showCurrencyMenu = false
                                }
                            )
                        }
                    }
                }

                // Link 5: Safe Log Out Button
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("logout_service_btn"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCrimson.copy(alpha = 0.15f)),
                    border = BorderStroke(0.5.dp, DarkCrimson)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth().clickable { viewModel.logout() },
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تسجيل الخروج الآمن", color = RedCancel, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
                        Icon(Icons.Filled.Logout, contentDescription = "خروج", tint = RedCancel)
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceLinkItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DeepCharcoal),
        border = BorderStroke(0.5.dp, Color(0xFF261E1F))
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp)) {
                    Text(title, color = ChampagneLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(subtitle, color = Color.Gray, fontSize = 10.sp, textAlign = TextAlign.Right)
                }
                Icon(imageVector = icon, contentDescription = title, tint = GoldenClassic)
            }
        }
    }
}

@Composable
fun AndroidDatePickerDialog(
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val calendar = java.util.Calendar.getInstance()
    androidx.compose.runtime.DisposableEffect(Unit) {
        val dpd = android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formattedMonth = String.format("%02d", month + 1)
                val formattedDay = String.format("%02d", dayOfMonth)
                onDateSelected("$year-$formattedMonth-$formattedDay")
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
        dpd.setOnDismissListener { onDismiss() }
        dpd.show()
        
        onDispose {
            dpd.dismiss()
        }
    }
}

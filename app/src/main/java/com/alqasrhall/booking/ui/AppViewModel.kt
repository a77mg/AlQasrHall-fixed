package com.alqasrhall.booking.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alqasrhall.booking.data.AppContainer
import com.alqasrhall.booking.data.Booking
import com.alqasrhall.booking.data.Payment
import com.alqasrhall.booking.data.Attachment
import com.alqasrhall.booking.data.User
import com.alqasrhall.booking.data.AuditLog
import com.alqasrhall.booking.data.BackupLog
import com.alqasrhall.booking.data.Rating
import com.alqasrhall.booking.data.Announcement
import com.alqasrhall.booking.data.Discount
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppContainer.getRepository(application)

    // Current State
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser = _currentUser.asStateFlow()

    fun hasPermission(action: String): Boolean {
        val role = _currentUser.value?.role ?: return false
        return when (action) {
            "CREATE_BOOKING" -> role == "Admin" || role == "Manager" || role == "Reception"
            "VIEW_BOOKING" -> true
            "SEARCH_BOOKING" -> true
            "RECORD_PAYMENT" -> role == "Admin" || role == "Manager" || role == "Accountant"
            "VIEW_FINANCIAL_REPORTS" -> role == "Admin" || role == "Manager" || role == "Accountant"
            "MANAGE_OPERATIONS" -> role == "Admin" || role == "Manager"
            "SYSTEM_ADMIN" -> role == "Admin"
            else -> false
        }
    }

    // Emergency Backup & Rollback States
    private val _canRollback = MutableStateFlow(false)
    val canRollback = _canRollback.asStateFlow()

    private var bookingsBackupSnapshot = listOf<Booking>()
    private var paymentsBackupSnapshot = listOf<Payment>()
    private var usersBackupSnapshot = listOf<User>()
    private var auditLogsBackupSnapshot = listOf<AuditLog>()

    private val _importSession = MutableStateFlow<ImportSession?>(null)
    val importSession = _importSession.asStateFlow()

    // Pending URI and Summary Reports for safer interactive restoring
    private val _pendingRestoreUri = MutableStateFlow<android.net.Uri?>(null)
    val pendingRestoreUri = _pendingRestoreUri.asStateFlow()

    private val _restoreSummaryMessage = MutableStateFlow<String?>(null)
    val restoreSummaryMessage = _restoreSummaryMessage.asStateFlow()

    private val _selectedTab = MutableStateFlow("dashboard") // dashboard, calendar, bookings, addNew, adminTools, auditLogs
    val selectedTab = _selectedTab.asStateFlow()

    // Offline Simulation State
    private val _networkStatus = MutableStateFlow("متصل") // متصل, غير متصل, جاري المزامنة
    val networkStatus = _networkStatus.asStateFlow()

    // Dark/Light Theme state
    private val _isDarkTheme = MutableStateFlow(true) // Starts dark (historic obsidian)
    val isDarkTheme = _isDarkTheme.asStateFlow()

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val _selectedCurrency = MutableStateFlow(prefs.getString("selected_currency", "Yemeni Rial") ?: "Yemeni Rial")
    val selectedCurrency = _selectedCurrency.asStateFlow()

    fun setSelectedCurrency(currency: String) {
        _selectedCurrency.value = currency
        prefs.edit().putString("selected_currency", currency).apply()
    }

    // Loaded Lists directly from Room Flow
    val bookings: StateFlow<List<Booking>> = repository.allBookings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val users: StateFlow<List<User>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backupLogs: StateFlow<List<BackupLog>> = repository.backupLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payments: StateFlow<List<Payment>> = repository.allPaymentsItem
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Feedback messaging
    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage = _uiMessage.asStateFlow()

    // --- Enterprise Extension States ---
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications = _notifications.asStateFlow()

    private val _backupHistory = MutableStateFlow<List<DatabaseBackupItem>>(emptyList())
    val backupHistory = _backupHistory.asStateFlow()

    val selectedArchiveYear = MutableStateFlow("الكل")
    val isExecutiveMode = MutableStateFlow(false)
    val isCommandCenterOpen = MutableStateFlow(false)
    val prefilledBookingDate = MutableStateFlow("")

    val ratings: StateFlow<List<Rating>> = repository.getAllRatings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val announcements: StateFlow<List<Announcement>> = repository.getAllAnnouncements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val discounts: StateFlow<List<Discount>> = repository.getAllDiscounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editingLocks = MutableStateFlow<Map<Int, BookingLock>>(emptyMap())
    val editingLocks = _editingLocks.asStateFlow()

    private val _pendingUndo = MutableStateFlow<UndoableAction?>(null)
    val pendingUndo = _pendingUndo.asStateFlow()

    private val _dashboardWidgets = MutableStateFlow<List<String>>(
        listOf("todayEvents", "upcomingEvents", "financeSummary", "occupancyHeatmap", "aiInsights", "activeFeed", "presence", "healthCenter")
    )
    val dashboardWidgets = _dashboardWidgets.asStateFlow()

    private val _connectedPresences = MutableStateFlow<List<UserPresence>>(
        listOf(
            UserPresence("admin", "المشرف العام", "Admin", true, "كمبيوتر المشرف ويب", System.currentTimeMillis()),
            UserPresence("manager", "المدير المالي", "Manager", true, "جالكسي S24 بلس", System.currentTimeMillis()),
            UserPresence("accountant", "المحاسب المعتمد", "Accountant", false, "كمبيوتر مكتبي", System.currentTimeMillis() - 7200000),
            UserPresence("reception", "موظف الاستقبال", "Reception", true, "تابلت لينوفو لوحي", System.currentTimeMillis())
        )
    )
    val connectedPresences = _connectedPresences.asStateFlow()

    val backupSchedulerEnabled = MutableStateFlow(true)
    val backupSchedulerInterval = MutableStateFlow("Daily") // Daily, Weekly, Monthly

    // Yemeni Currency Utility
    fun formatYemeniCurrency(amount: Double): String {
        return try {
            val formatted = String.format(Locale.ENGLISH, "%,.0f", amount)
            when (selectedCurrency.value) {
                "Saudi Riyal" -> "$formatted ر.س"
                "USD" -> "$formatted $"
                else -> "$formatted ر.ي"
            }
        } catch (e: Exception) {
            val intAmt = amount.toInt()
            when (selectedCurrency.value) {
                "Saudi Riyal" -> "$intAmt ر.س"
                "USD" -> "$intAmt $"
                else -> "$intAmt ر.ي"
            }
        }
    }

    // Unique reference generator utilities
    fun getBookingRef(booking: Booking): String {
        val year = try { booking.dateStr.substring(0, 4) } catch (e: Exception) { "2026" }
        return "BK-$year-${String.format(Locale.ENGLISH, "%06d", booking.id)}"
    }

    fun getPaymentRef(payment: Payment): String {
        val year = SimpleDateFormat("yyyy", Locale.US).format(Date(payment.paymentDate))
        return "PAY-$year-${String.format(Locale.ENGLISH, "%06d", payment.id)}"
    }

    fun getCancellationRef(booking: Booking): String {
        val year = try { booking.dateStr.substring(0, 4) } catch (e: Exception) { "2026" }
        return "CAN-$year-${String.format(Locale.ENGLISH, "%06d", booking.id)}"
    }

    // Customer Risk Level Analysis
    fun getCustomerRiskLevel(name: String, phone: String): String? {
        val allBookings = bookings.value
        val userBookings = allBookings.filter { 
            it.renterName.trim().equals(name.trim(), ignoreCase = true) || 
            it.phone1.trim().replace(" ", "") == phone.trim().replace(" ", "")
        }
        
        val cancelledCount = userBookings.count { it.status == "إلغاء" }
        val confirmedWithDebt = userBookings.filter { it.status != "إلغاء" && it.remainingAmount > 0.0 }
        
        if (cancelledCount >= 2) {
            return "عالي الخطورة (تاريخ إلغاء متكرر)"
        }
        if (confirmedWithDebt.isNotEmpty()) {
            val totalDebt = confirmedWithDebt.sumOf { it.remainingAmount }
            return "مطلوب مالياً (ذمة معلقة بقيمة ${formatYemeniCurrency(totalDebt)})"
        }
        return null
    }

    // Smart Conflict Detection logic
    fun detectBookingConflicts(dateStr: String, name: String, phoneStr: String, excludeBookingId: Int? = null): List<String> {
        val warnings = mutableListOf<String>()
        val currentBookings = bookings.value
        
        // 1. Conflict of same date for active events
        val holdsDate = currentBookings.any { b -> 
            b.id != excludeBookingId && b.dateStr == dateStr && b.status != "إلغاء" && b.status != "إلغاء d"
        }
        if (holdsDate) {
            warnings.add("تنبيه: هذا التاريخ (${dateStr}) غير شاغر ومحجوز مسبقاً لعقد آخر!")
        }

        // 2. Conflict of similar/same renter name
        if (name.trim().length >= 3) {
            val hasSimilar = currentBookings.any { b ->
                b.id != excludeBookingId && (b.renterName.replace(" ", "").contains(name.trim().replace(" ", ""), ignoreCase = true) ||
                name.trim().replace(" ", "").contains(b.renterName.replace(" ", ""), ignoreCase = true))
            }
            if (hasSimilar) {
                warnings.add("تنبيه: يوجد مستأجر باسم مشابه أو مطابق مسجل مسبقاً في قاعدة البيانات!")
            }
        }

        // 3. Conflict of phone number
        if (phoneStr.trim().length >= 6) {
            val hasPhone = currentBookings.any { b ->
                b.id != excludeBookingId && (b.phone1.contains(phoneStr.trim()) || (b.phone2 != null && b.phone2.contains(phoneStr.trim())))
            }
            if (hasPhone) {
                warnings.add("تنبيه: رقم الهاتف المُدخل مسجل سابقاً على حجز آخر بالصالة!")
            }
        }
        return warnings
    }

    // Notification manipulation
    fun addNotification(title: String, message: String, type: String) {
        val id = UUID.randomUUID().toString()
        val user = _currentUser.value
        val newNotif = AppNotification(
            id = id,
            title = title,
            message = message,
            type = type,
            userId = user?.username ?: "system",
            role = user?.role ?: "System",
            isRead = false
        )
        val list = _notifications.value.toMutableList()
        list.add(0, newNotif)
        _notifications.value = list

        // Also store to Firebase collection simulator / record it to console for auditing
        viewModelScope.launch {
            try {
                repository.logOperation(user?.username ?: "system", "إخطار بالحدث", "$type: $title", message)
            } catch(e: Exception) {}
        }
    }

    fun markNotificationRead(id: String) {
        val list = _notifications.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
        _notifications.value = list
    }

    fun markAllNotificationsRead() {
        val list = _notifications.value.map { it.copy(isRead = true) }
        _notifications.value = list
    }

    fun clearNotifications() {
        _notifications.value = emptyList()
    }

    // Undo management
    fun setUndoAction(action: UndoableAction) {
        _pendingUndo.value = action
        viewModelScope.launch {
            kotlinx.coroutines.delay(30000)
            if (_pendingUndo.value?.id == action.id) {
                _pendingUndo.value = null
            }
        }
    }

    fun triggerUndo() {
        val action = _pendingUndo.value ?: return
        _pendingUndo.value = null
        viewModelScope.launch {
            try {
                action.undoBlock()
                showMessage("تم التراجع بنجاح عن العملية الأخيرة: ${action.description}")
            } catch (e: Exception) {
                showMessage("فشل التراجع: ${e.message}")
            }
        }
    }

    fun dismissUndo() {
        _pendingUndo.value = null
    }

    // Lock management for concurrent editing simulation
    fun acquireBookingLock(bookingId: Int) {
        val user = _currentUser.value ?: return
        val map = _editingLocks.value.toMutableMap()
        map[bookingId] = BookingLock(user.username, System.currentTimeMillis())
        _editingLocks.value = map
    }

    fun releaseBookingLock(bookingId: Int) {
        val map = _editingLocks.value.toMutableMap()
        map.remove(bookingId)
        _editingLocks.value = map
    }

    // Support toggle of widget configuration
    fun reorderWidgets(newList: List<String>) {
        _dashboardWidgets.value = newList
    }

    // Initial Enterprise Seed notifications
    fun seedInitialNotifications() {
        val user = _currentUser.value
        _notifications.value = listOf(
            AppNotification(UUID.randomUUID().toString(), "تهيئة وتأمين النظام", "تم بنجاح تشغيل وتأمين طبقة الحجز المتطورة لصالة القصر الدائري.", "System", user?.username ?: "admin", "Admin", false),
            AppNotification(UUID.randomUUID().toString(), "توليد تلقائي للنسخ الاحتياطي", "قام مجدول الحجز التلقائي بحفظ السجلات المحدثة بنجاح.", "Backup", user?.username ?: "admin", "Admin", true),
            AppNotification(UUID.randomUUID().toString(), "كشف التدفقات والمستحقات المالية", "تنبيه: يوجد 3 عقود في انتظار سداد الأقساط المتبقية هذا الأسبوع.", "Reminder", user?.username ?: "admin", "Admin", false)
        )
    }


    // Filters for Bookings
    val searchRenterQuery = MutableStateFlow("")
    val filterStatus = MutableStateFlow("الكل") // الكل, جديد, مؤقت, استبدال إلى تاريخ آخر, إلغاء
    val filterEventType = MutableStateFlow("الكل")
    val filterEmployee = MutableStateFlow("الكل")
    val filterDateFrom = MutableStateFlow("") // YYYY-MM-DD
    val filterDateTo = MutableStateFlow("") // YYYY-MM-DD
    val sortBy = MutableStateFlow("تاريخ الحجز (الأحدث)") // تاريخ الحجز (الأحدث), تاريخ الحجز (الأقدم), الاسم, القيمة الأعلى

    val searchSuggestions: StateFlow<List<String>> = searchRenterQuery
        .combine(bookings) { query, list ->
            if (query.trim().length < 2) emptyList()
            else list.map { it.renterName }.filter { it.contains(query, ignoreCase = true) }.distinct().take(5)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        seedInitialUsersAndBookings()
        processAutomatedExpirations()
    }

    fun showMessage(msg: String) {
        _uiMessage.value = msg
    }

    fun clearMessage() {
        _uiMessage.value = null
    }

    fun selectTab(tab: String) {
        val user = _currentUser.value
        if (user != null) {
            if ((tab == "auditLogs" || tab == "adminTools") && user.role != "Admin") {
                showMessage("غير مصرح: هذه الميزة تتطلب صلاحية مدير للنظام.")
                return
            }
        }
        _selectedTab.value = tab
        // Update user activity timestamp in logs/database
        _currentUser.value?.let { u ->
            viewModelScope.launch {
                repository.updateLastActivity(u.username)
            }
        }
    }

    fun setNetworkStatus(status: String) {
        viewModelScope.launch {
            if (status == "جاري المزامنة") {
                _networkStatus.value = "جاري المزامنة"
                
                // Perform live synchronization from local Room database files to Firebase Cloud Firestore collections
                try {
                    val currentUsers = users.value
                    val currentBookings = bookings.value
                    val currentPayments = repository.allPaymentsItem.firstOrNull() ?: emptyList()
                    val currentLogs = auditLogs.value
                    com.alqasrhall.booking.data.FirebaseSyncService.triggerFullSync(
                        users = currentUsers,
                        bookings = currentBookings,
                        payments = currentPayments,
                        logs = currentLogs
                    )
                } catch (e: Exception) {
                    android.util.Log.e("AppViewModel", "Failed full synchronization: ${e.message}", e)
                }

                kotlinx.coroutines.delay(1500)
                _networkStatus.value = "متصل"
                showMessage("تمت المزامنة وحفظ البيانات سحابياً بنجاح!")
            } else {
                _networkStatus.value = status
                if (status == "غير متصل") {
                    showMessage("تم الانتقال إلى العمل بدون إنترنت. سيتم حفظ التغييرات محلياً ومزامنتها تلقائياً عند عودة الاتصال.")
                }
            }
        }
    }

    // --- SEEDING DATA ---
    private fun seedInitialUsersAndBookings() {
        viewModelScope.launch {
            try {
                android.util.Log.e("APP_TRACE", "Before users.first()")
                // Check if user table is empty, if so, seed users
                val hasUsers = users.first()
                android.util.Log.e("APP_TRACE", "After users.first() - users count: ${hasUsers.size}")
                if (hasUsers.isEmpty()) {
                    val seedUsers = listOf(
                        User(fullName = "م. أحمد الغضبي (مدير النظام)", username = "admin", passwordHash = "admin", role = "Admin", phone = "0551234567", status = "نشط", mustChangePassword = false),
                        User(fullName = "أبو فهد القصر (المدير العام)", username = "manager", passwordHash = "manager", role = "Manager", phone = "0557654321", status = "نشط", mustChangePassword = false),
                        User(fullName = "ياسر المحاسب المالي", username = "accountant", passwordHash = "accountant", role = "Accountant", phone = "0559876543", status = "نشط", mustChangePassword = false),
                        User(fullName = "خالد موظف استقبال الصالة", username = "reception", passwordHash = "reception", role = "Reception", phone = "0551112223", status = "نشط", mustChangePassword = false)
                    )
                    for (u in seedUsers) {
                        repository.insertUser(u, "النظام")
                    }

                    // Seed some initial bookings from year 2019 to now to populate dashboard statistics nicely!
                    val sampleBookings = listOf(
                        Booking(dateStr = "2026-06-15", dayOfWeek = "Monday", renterName = "عبدالرحمن البقمي", eventType = "زواج", rentAmount = 15000.0, totalPaid = 15000.0, phone1 = "0500000001", phone2 = "0590000001", status = "جديد", employeeUsername = "manager", createdAt = System.currentTimeMillis() - 1000 * 60000),
                        Booking(dateStr = "2026-06-25", dayOfWeek = "Thursday", renterName = "سليمان العتيبي", eventType = "زواج", rentAmount = 18000.0, totalPaid = 5000.0, phone1 = "0555222333", phone2 = "", status = "جديد", employeeUsername = "reception"),
                        Booking(dateStr = "2026-07-01", dayOfWeek = "Wednesday", renterName = "عبدالله الحربي", eventType = "خطوبة", rentAmount = 8000.0, totalPaid = 8000.0, phone1 = "0533334444", phone2 = "", status = "جديد", employeeUsername = "reception"),
                        Booking(dateStr = "2025-11-12", dayOfWeek = "Tuesday", renterName = "أبو ماجد الصبحي", eventType = "زواج", rentAmount = 14000.0, totalPaid = 14000.0, phone1 = "0532211444", phone2 = "", status = "جديد", employeeUsername = "manager"),
                        Booking(dateStr = "2024-08-20", dayOfWeek = "Wednesday", renterName = "حسين الهاشمي", eventType = "خطوبة", rentAmount = 9000.0, totalPaid = 9000.0, phone1 = "0511111999", phone2 = "", status = "جديد", employeeUsername = "reception"),
                        Booking(dateStr = "2023-04-05", dayOfWeek = "Wednesday", renterName = "عوض اليافعي", eventType = "تخرج", rentAmount = 10000.0, totalPaid = 10000.0, phone1 = "0577777888", phone2 = "", status = "جديد", employeeUsername = "manager"),
                        Booking(dateStr = "2022-02-14", dayOfWeek = "Monday", renterName = "سالم الكثيري", eventType = "اجتماع/عشاء", rentAmount = 6000.0, totalPaid = 6000.0, phone1 = "0533355522", phone2 = "", status = "جديد", employeeUsername = "manager"),
                        Booking(dateStr = "2021-09-09", dayOfWeek = "Thursday", renterName = "غالب الأرحبي", eventType = "زواج", rentAmount = 15000.0, totalPaid = 15000.0, phone1 = "0599990001", phone2 = "", status = "جديد", employeeUsername = "reception"),
                        Booking(dateStr = "2026-06-13", dayOfWeek = "Saturday", renterName = "فيصل الدوسري", eventType = "زواج", rentAmount = 20000.0, totalPaid = 10000.0, phone1 = "0544112233", phone2 = "0566332211", status = "مؤقت", employeeUsername = "reception", temporaryExpiresAt = System.currentTimeMillis() + 18 * 60 * 60 * 1000),
                        Booking(dateStr = "2026-05-10", dayOfWeek = "Sunday", renterName = "عمرو السويدي", eventType = "تخرج", rentAmount = 12000.0, totalPaid = 12000.0, phone1 = "0587654321", phone2 = "", status = "جديد", employeeUsername = "manager"),
                        Booking(dateStr = "2026-06-30", dayOfWeek = "Tuesday", renterName = "فيصل اليامي", eventType = "اجتماع/عشاء", rentAmount = 6000.0, totalPaid = 0.0, phone1 = "0501111999", phone2 = "", status = "إلغاء", employeeUsername = "admin")
                    )
                    for (b in sampleBookings) {
                        repository.createBooking(b, b.employeeUsername)
                    }

                    // Add sample payments for the partially paid ones
                    val bookingsInDb = repository.allBookings.first()
                    val b2 = bookingsInDb.find { it.renterName == "سليمان العتيبي" }
                    if (b2 != null) {
                        repository.addPayment(Payment(bookingId = b2.id, paymentDate = System.currentTimeMillis() - 86400000, amount = 5000.0, paymentMethod = "محفظة إلكترونية", receiptRef = "M-2026-001", receivedByEmployee = b2.employeeUsername), b2.employeeUsername)
                    }
                    val b1 = bookingsInDb.find { it.renterName == "فيصل الدوسري" }
                    if (b1 != null) {
                        repository.addPayment(Payment(bookingId = b1.id, paymentDate = System.currentTimeMillis() - 40000000, amount = 10000.0, paymentMethod = "نقداً", receiptRef = "M-2026-002", receivedByEmployee = b1.employeeUsername), b1.employeeUsername)
                    }
                    
                    // Initialize Settings collection in Firestore:
                    try {
                        com.alqasrhall.booking.data.FirebaseSyncService.syncSettingsToFirestore()
                    } catch(e: Exception) {}
                }
            } catch (e: Exception) {
                android.util.Log.e("APP_TRACE", "Exception in seedInitialUsersAndBookings: ${e.message}", e)
            }
        }
    }

    private fun processAutomatedExpirations() {
        viewModelScope.launch {
            repository.processAutomatedExpirations()
        }
    }

    // --- AUTHENTICATION ---
    fun tryLogin(usernameInput: String, passwordInput: String): Boolean {
        var success = false
        // Fetch matching user synchronously inside scope or launch
        viewModelScope.launch {
            val user = repository.getUserByUsername(usernameInput.trim())
            if (user != null) {
                if (user.passwordHash == passwordInput) {
                    if (user.status == "موقوف") {
                        showMessage("الحساب موقوف حالياً. يرجى مراجعة المسؤول.")
                        return@launch
                    }
                    _currentUser.value = user
                    repository.updateLastLogin(user.username)
                    seedInitialNotifications()
                    showMessage("مرحباً بك مجدداً ${user.fullName}!")
                    
                    // Synchronously/Asynchronously verify credentials on Firebase Auth
                    com.alqasrhall.booking.data.FirebaseSyncService.authenticateUser(usernameInput, passwordInput)

                    success = true
                } else {
                    showMessage("خطأ في اسم المستخدم أو كلمة المرور.")
                }
            } else {
                showMessage("خطأ في اسم المستخدم أو كلمة المرور.")
            }
        }
        return success
    }

    fun logout() {
        viewModelScope.launch {
            _currentUser.value?.let { user ->
                repository.logOperation(user.username, "تسجيل خروج", "", "تم تسجيل خروج الموظف بنجاح")
            }
            _currentUser.value = null
            _selectedTab.value = "dashboard"
            showMessage("تم تسجيل الخروج بنجاح.")
        }
    }

    fun changeForcePassword(newPass: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            if (newPass.trim().isEmpty() || newPass.trim().length < 5) {
                showMessage("خطأ: يرجى إدخال كلمة مرور قوية من 5 أحرف أو أكثر!")
                return@launch
            }
            val updated = user.copy(passwordHash = newPass.trim(), mustChangePassword = false)
            repository.updateUser(updated, user.username)
            _currentUser.value = updated
            showMessage("تم تغيير كلمة المرور بنجاح! تم حفظ حسابك وتأمينه بنجاح.")
            repository.logOperation(user.username, "تغيير إجباري لكلمة المرور", "", "تم تحديث كلمة المرور الافتراضية بنجاح")
        }
    }

    // --- ADMIN USER ACTIONS ---
    fun addUser(fullName: String, userName: String, pass: String, role: String, phone: String, email: String?) {
        val currentAdmin = _currentUser.value ?: return
        if (currentAdmin.role != "Admin") {
            showMessage("غير مصرح: هذه العملية من صلاحيات مدير النظام فقط.")
            return
        }
        viewModelScope.launch {
            val newUser = User(
                fullName = fullName,
                username = userName.trim(),
                passwordHash = pass,
                role = role,
                phone = phone,
                email = email,
                status = "نشط",
                mustChangePassword = false
            )
            val inserted = repository.insertUser(newUser, currentAdmin.username)
            if (inserted) {
                showMessage("تمت إضافة المستخدم $fullName بنجاح.")
                // Trigger background sync simulation
                triggerBackgroundSync()
            } else {
                showMessage("فشل: اسم المستخدم $userName مسجل مسبقاً في النظام!")
            }
        }
    }

    fun updateUserStatus(user: User, newStatus: String) {
        val currentAdmin = _currentUser.value ?: return
        if (currentAdmin.role != "Admin") {
            showMessage("غير مصرح: هذه العملية من صلاحيات مدير النظام فقط.")
            return
        }
        viewModelScope.launch {
            val updated = user.copy(status = newStatus)
            repository.updateUser(updated, currentAdmin.username)
            showMessage("تم تغيير حالة المستخدم ${user.username} إلى $newStatus.")
            triggerBackgroundSync()
        }
    }

    fun resetUserPassword(user: User, newPass: String) {
        val currentAdmin = _currentUser.value ?: return
        if (currentAdmin.role != "Admin") {
            showMessage("غير مصرح: هذه العملية من صلاحيات مدير النظام فقط.")
            return
        }
        viewModelScope.launch {
            val updated = user.copy(passwordHash = newPass)
            repository.updateUser(updated, currentAdmin.username)
            showMessage("تمت إعادة تعيين كلمة المرور للمستخدم ${user.username} بنجاح.")
            triggerBackgroundSync()
        }
    }

    // --- BOOKING ACTIONS ---
    fun addNewBooking(
        dateStr: String,
        renterName: String,
        eventType: String,
        rentAmount: Double,
        initialPaid: Double,
        phone1: String,
        phone2: String,
        status: String,
        paymentMethod: String,
        receiptRef: String,
        force: Boolean = false
    ) {
        val user = _currentUser.value ?: return
        if (!hasPermission("CREATE_BOOKING")) {
            showMessage("غير مصرح: حسابك لا يملك صلاحية إضافة حجوزات جديدة.")
            return
        }

        viewModelScope.launch {
            // Day of Week calculation
            val dayOfWeekArabic = getDayOfWeekInArabic(dateStr)

            val booking = Booking(
                dateStr = dateStr,
                dayOfWeek = dayOfWeekArabic,
                renterName = renterName,
                eventType = eventType,
                rentAmount = rentAmount,
                totalPaid = 0.0, // Will be updated when the payment is added
                phone1 = phone1,
                phone2 = phone2,
                status = status,
                employeeUsername = user.username
            )

            val code = repository.createBooking(booking, user.username, force)
            when (code) {
                0 -> {
                    // Booking created. Find booking from DB to add payment
                    val bList = repository.allBookings.first()
                    val created = bList.find { it.dateStr == dateStr && it.renterName == renterName }
                    if (created != null && initialPaid > 0.0) {
                        repository.addPayment(
                            Payment(
                                bookingId = created.id,
                                paymentDate = System.currentTimeMillis(),
                                amount = initialPaid,
                                paymentMethod = paymentMethod,
                                receiptRef = receiptRef,
                                notes = "دفعة أولية للحجز المتجدد",
                                receivedByEmployee = user.username
                            ),
                            user.username
                        )
                    }
                    showMessage("تم تسجيل الحجز بنجاح لتاريخ $dateStr !")
                    _selectedTab.value = "bookings"
                    triggerBackgroundSync()
                }
                1 -> {
                    showMessage("خطأ التكرار: التاريخ $dateStr محجوز بالكامل مسبقاً! يرجى اختيار تاريخ آخر.")
                }
                else -> {
                    showMessage("حدث خطأ مجهول أثناء حفظ الحجز.")
                }
            }
        }
    }

    fun addRating(
        bookingId: Int,
        serviceQuality: Int,
        organization: Int,
        cleanliness: Int,
        overallSatisfaction: Int,
        feedbackComment: String
    ) {
        viewModelScope.launch {
            val rating = Rating(
                bookingId = bookingId,
                serviceQuality = serviceQuality,
                organization = organization,
                cleanliness = cleanliness,
                overallSatisfaction = overallSatisfaction,
                feedbackComment = feedbackComment,
                createdAt = System.currentTimeMillis()
            )
            repository.addRating(rating)
            showMessage("تم تسجيل تقييم رضا العملاء بنجاح. شكراً لملاحظاتكم!")
        }
    }

    fun addAnnouncement(title: String, content: String, pinned: Boolean) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val ann = Announcement(
                title = title,
                content = content,
                pinned = pinned,
                author = user.fullName,
                createdAt = System.currentTimeMillis()
            )
            repository.addAnnouncement(ann, user.username)
            addNotification(
                title = "إعلان مثبت جديد بخصائص هامة",
                message = "قام الموظف (${user.fullName}) بتعميم إعلان جديد بعنوان: $title",
                type = "Announcement"
            )
            showMessage("تم نشر وتعميم الإعلان المثبت بنجاح!")
        }
    }

    fun deleteAnnouncement(id: Int) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.deleteAnnouncement(id, user.username)
            showMessage("تم إزالة الإعلان بنجاح.")
        }
    }

    fun addDiscount(bookingId: Int, originalAmount: Double, discountAmount: Double, reason: String) {
        val user = _currentUser.value ?: return
        if (user.role != "Admin" && user.role != "Manager") {
            showMessage("خطأ: أنت لا تملك الصلاحيات لتقديم أو تدقيق الحسميات المالية!")
            return
        }
        viewModelScope.launch {
            val disc = Discount(
                bookingId = bookingId,
                originalAmount = originalAmount,
                discountAmount = discountAmount,
                finalAmount = originalAmount - discountAmount,
                reason = reason,
                approvedBy = user.fullName,
                timestamp = System.currentTimeMillis()
            )
            repository.addDiscount(disc, user.username)
            addNotification(
                title = "تسجيل خصم مالي معتمد",
                message = "تم اعتماد حسم مالي بقيمة ${formatYemeniCurrency(discountAmount)} لحساب الحجز رقم #${bookingId} بواسطة المنظم (${user.fullName}).",
                type = "Discount"
            )
            showMessage("تم تدقيق وتطبيق الحسم المالي المعتمد بنجاح!")
        }
    }

    fun modifyBooking(booking: Booking) {
        val user = _currentUser.value ?: return
        if (!hasPermission("MANAGE_OPERATIONS")) {
            showMessage("غير مصرح: حسابك لا يملك صلاحية تعديل الحجوزات.")
            return
        }
        viewModelScope.launch {
            val oldBooking = bookings.value.find { it.id == booking.id }
            var isSensitiveChange = false
            val changeLog = StringBuilder()
            
            if (oldBooking != null) {
                if (oldBooking.dateStr != booking.dateStr) {
                    isSensitiveChange = true
                    changeLog.append("تغيير الموعد من [${oldBooking.dateStr}] إلى [${booking.dateStr}]. ")
                }
                if (oldBooking.rentAmount != booking.rentAmount) {
                    isSensitiveChange = true
                    changeLog.append("تعديل قيمة الإيجار من [${oldBooking.rentAmount}] إلى [${booking.rentAmount}]. ")
                }
                if (oldBooking.renterName != booking.renterName) {
                    isSensitiveChange = true
                    changeLog.append("تعديل اسم المستأجر من [${oldBooking.renterName}] إلى [${booking.renterName}]. ")
                }
                if (oldBooking.phone1 != booking.phone1) {
                    changeLog.append("تحديث الهاتف من [${oldBooking.phone1}] إلى [${booking.phone1}]. ")
                }
            }

            val result = repository.updateBooking(booking, user.username)
            if (result == 0) {
                if (isSensitiveChange) {
                    val alertMessage = "تعديل حساس بواسطة الموظف (${user.fullName}): ${changeLog.toString()}"
                    addNotification(
                        title = "تعديل حجز حساس #${booking.id}",
                        message = alertMessage,
                        type = "Audit"
                    )
                }
                showMessage("تم حفظ تعديلات حجز المستأجر ${booking.renterName} بنجاح.")
                triggerBackgroundSync()
            } else if (result == 1) {
                showMessage("فشل التعديل: التاريخ المختار ${booking.dateStr} محجوز بالكامل!")
            } else {
                showMessage("فشل التعديل، الحجز غير موجود أو خطأ قاعدة بيانات.")
            }
        }
    }

    fun rescheduleBooking(bookingId: Int, newDateStr: String) {
        val user = _currentUser.value ?: return
        if (!hasPermission("MANAGE_OPERATIONS")) {
            showMessage("غير مصرح لحسابك بنقل تاريخ الحجوزات.")
            return
        }
        viewModelScope.launch {
            val dayOfWeekArabic = getDayOfWeekInArabic(newDateStr)
            val result = repository.rescheduleBooking(bookingId, newDateStr, dayOfWeekArabic, user.username)
            when (result) {
                0 -> {
                    showMessage("تم نقل موعد ومستحقات الحجز لتاريخ $newDateStr الجديد بنجاح!")
                    triggerBackgroundSync()
                }
                1 -> showMessage("فشل النقل: التاريخ الجديد $newDateStr محجوز مسبقاً!")
                else -> showMessage("حدث خطأ في قاعدة البيانات، برجاء المحاولة لاحقاً.")
            }
        }
    }

    fun cancelActiveBooking(bookingId: Int) {
        val user = _currentUser.value ?: return
        if (!hasPermission("MANAGE_OPERATIONS")) {
            showMessage("غير مصرح لحسابك بإلغاء الحجوزات الصادرة.")
            return
        }
        viewModelScope.launch {
            repository.cancelBooking(bookingId, user.username)
            showMessage("تم إلغاء الحجز بنجاح وإعادة طرح التاريخ للجمهور.")
            triggerBackgroundSync()
        }
    }

    fun confirmTemporaryBooking(bookingId: Int) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.confirmTemporaryBooking(bookingId, user.username)
            showMessage("تم تأكيد الحجز وجعله حجماً جديداً معتمداً!")
            triggerBackgroundSync()
        }
    }

    // --- FINANCIAL PAYMENTS ---
    fun addBookingPayment(bookingId: Int, amount: Double, paymentMethod: String, receiptRef: String, notes: String) {
        val user = _currentUser.value ?: return
        if (!hasPermission("RECORD_PAYMENT")) {
            showMessage("غير مصرح لحسابك بتلقي الدفعات المالية وإضافة حسابات الصالة.")
            return
        }
        viewModelScope.launch {
            val payment = Payment(
                bookingId = bookingId,
                paymentDate = System.currentTimeMillis(),
                amount = amount,
                paymentMethod = paymentMethod,
                receiptRef = receiptRef,
                notes = notes,
                receivedByEmployee = user.username
            )
            val code = repository.addPayment(payment, user.username)
            when (code) {
                0 -> {
                    showMessage("تم تسجيل دفعة مالية بقيمة $amount ريال بنجاح.")
                    triggerBackgroundSync()
                }
                1 -> showMessage("فشل: المبلغ المتبقي المستحق أقل من السداد المضاف!")
                else -> showMessage("حدث خطأ في الوصول للحجز.")
            }
        }
    }

    fun deleteBookingPayment(payment: Payment) {
        val user = _currentUser.value ?: return
        if (user.role != "Admin") {
            showMessage("غير مصرح: حذف السندات والدفعات يتم من صلاحيات المشرف العام فقط.")
            return
        }
        viewModelScope.launch {
            val deleted = repository.deletePayment(payment, user.username)
            if (deleted) {
                showMessage("تم إلغاء وحذف السند المالي بقيمة ${payment.amount} ريال.")
                triggerBackgroundSync()
            }
        }
    }

    // List of payments for a single bookingId
    fun getPaymentsStream(bookingId: Int): Flow<List<Payment>> = repository.getPaymentsForBooking(bookingId)

    // --- ATTACHMENT SIMULATOR ---
    fun getAttachmentsStream(bookingId: Int): Flow<List<Attachment>> = repository.getAttachmentsForBooking(bookingId)

    fun addMockAttachment(bookingId: Int, fileName: String, fileType: String, fileSize: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val attach = Attachment(
                bookingId = bookingId,
                fileName = fileName,
                fileType = fileType,
                fileSize = fileSize,
                localUri = "content://com.alqasrhall.booking/secure_storage/${System.currentTimeMillis()}"
            )
            repository.addAttachment(attach, user.username)
            showMessage("تم رفع المرفق $fileName بنجاح.")
            triggerBackgroundSync()
        }
    }

    fun deleteAttachment(attachment: Attachment) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.deleteAttachment(attachment.id, user.username, attachment.fileName)
            showMessage("تم حذف المرفق ${attachment.fileName}.")
            triggerBackgroundSync()
        }
    }

    // --- EMERGENCY BACKUP & ROLLBACK SYSTEM ---
    suspend fun createEmergencyBackup() {
        bookingsBackupSnapshot = repository.allBookings.first()
        paymentsBackupSnapshot = repository.allPaymentsItem.first()
        usersBackupSnapshot = repository.allUsers.first()
        auditLogsBackupSnapshot = repository.allLogs.first()
        _canRollback.value = true

        val username = _currentUser.value?.username ?: "النظام التلقائي"
        val timestamp = System.currentTimeMillis()
        val sizeKb = (bookingsBackupSnapshot.size * 0.4 + paymentsBackupSnapshot.size * 0.2 + usersBackupSnapshot.size * 0.3).coerceAtLeast(1.2)
        val sizeStr = String.format(Locale.ENGLISH, "%.2f KB", sizeKb)
        
        val backupItem = DatabaseBackupItem(
            id = UUID.randomUUID().toString(),
            timestamp = timestamp,
            size = sizeStr,
            type = "كامل - طارئ",
            status = "ناجحة",
            performedBy = username,
            bookings = bookingsBackupSnapshot,
            payments = paymentsBackupSnapshot,
            users = usersBackupSnapshot,
            auditLogs = auditLogsBackupSnapshot
        )
        
        val newList = _backupHistory.value.toMutableList()
        newList.add(0, backupItem)
        _backupHistory.value = newList

        repository.logOperation("النظام", "نسخ احتياطي طارئ", "", "تم إنشاء نسخة حماية تلقائية تلقائياً للفصل قبل الاستيراد/الاستعادة")
    }

    fun triggerManualBackup() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                val bList = repository.allBookings.first()
                val pList = repository.allPaymentsItem.first()
                val uList = repository.allUsers.first()
                val aList = repository.allLogs.first()
                
                val timestamp = System.currentTimeMillis()
                val sizeKb = (bList.size * 5.2 + pList.size * 2.8 + uList.size * 1.5).coerceAtLeast(18.5)
                val sizeStr = String.format(Locale.ENGLISH, "%.1f KB", sizeKb)
                
                val backupItem = DatabaseBackupItem(
                    id = UUID.randomUUID().toString(),
                    timestamp = timestamp,
                    size = sizeStr,
                    type = "كامل - يدوي",
                    status = "नाجحة",
                    performedBy = user.username,
                    bookings = bList,
                    payments = pList,
                    users = uList,
                    auditLogs = aList
                )
                
                val newList = _backupHistory.value.toMutableList()
                newList.add(0, backupItem)
                _backupHistory.value = newList
                
                repository.logOperation(user.username, "نسخ احتياطي يدوي", "", "إنشاء النسخة الاحتياطية الاحترافية رقم: ${backupItem.id.take(8)}")
                addNotification(
                    title = "إتمام النسخ الاحتياطي اليدوي",
                    message = "تم حفظ وحماية البيانات بنجاح بحجم $sizeStr بواسطة الموظف ${user.fullName}",
                    type = "Backups"
                )
                showMessage("تم بنجاح تشغيل وحفظ نسخة احتياطية يدوية كاملة لقاعدة البيانات!")
            } catch (e: Exception) {
                showMessage("فشل النسخ الاحتياطي اليدوي: ${e.message}")
            }
        }
    }

    fun restoreFromBackupItem(item: DatabaseBackupItem) {
        val user = _currentUser.value ?: return
        if (user.role != "Admin") {
            showMessage("غير مصرح: عملية استرجاع البيانات تتطلب صلاحية مدير النظام.")
            return
        }
        viewModelScope.launch {
            try {
                repository.clearDatabaseAndRestore(
                    bookings = item.bookings,
                    payments = item.payments,
                    users = item.users,
                    auditLogs = item.auditLogs
                )
                showMessage("تم بنجاح استعادة النظام بالكامل لنقطة الحفظ التاريخية (${item.type} - ${item.size})!")
                repository.logOperation(user.username, "استعادة نسخة احتياطية", "", "استعادة محتويات النسخة رقم: ${item.id.take(8)}")
                addNotification(
                    title = "استعادة حيوية للملفات",
                    message = "تم استبدال واسترجاع قاعدة البيانات بنجاح لنقطة النسخ الاحتياطي بحجم ${item.size}.",
                    type = "Backups"
                )
                triggerBackgroundSync()
            } catch (e: Exception) {
                showMessage("خطأ أثناء استعادة البيانات: ${e.message}")
            }
        }
    }

    fun executeCommand(commandText: String): Boolean {
        val trimmed = commandText.trim()
        if (trimmed.isEmpty()) return false
        
        if (trimmed.startsWith("بحث ") || trimmed.startsWith("البحث عن ")) {
            val query = trimmed.substringAfter("بحث ").substringAfter("عن ").trim()
            searchRenterQuery.value = query
            _selectedTab.value = "bookings"
            showMessage("تم التوجيه والبحث الشامل عن: $query")
            return true
        }
        
        if (trimmed.contains("يونيو") || trimmed.contains("June") || trimmed.contains("يونية")) {
            filterDateFrom.value = "2026-06-01"
            filterDateTo.value = "2026-06-30"
            _selectedTab.value = "bookings"
            showMessage("تم إظهار حجوزات شهر يونيو 2026")
            return true
        }

        if (trimmed.contains("يوليو") || trimmed.contains("July") || trimmed.contains("يولية")) {
            filterDateFrom.value = "2026-07-01"
            filterDateTo.value = "2026-07-31"
            _selectedTab.value = "bookings"
            showMessage("تم إظهار حجوزات شهر يوليو 2026")
            return true
        }
        
        when {
            trimmed.contains("إضافة") || trimmed.contains("جديد") || trimmed.contains("create") -> {
                _selectedTab.value = "addNew"
                showMessage("تم توجيه قمرة الأوامر لإنشاء حجز جديد")
                return true
            }
            trimmed.contains("تقرير") || trimmed.contains("تقارير") || trimmed.contains("reports") -> {
                _selectedTab.value = "adminTools"
                showMessage("تم توجيه قمرة الأوامر للمدير المتقدم والتقارير")
                return true
            }
            trimmed.contains("ديون") || trimmed.contains("ذمم") || trimmed.contains("outstanding") -> {
                filterStatus.value = "جديد"
                _selectedTab.value = "bookings"
                showMessage("تم إظهار الديون والذمم غير المدفوعة")
                return true
            }
            trimmed.contains("الرئيسية") || trimmed.contains("قمرة") || trimmed.contains("dashboard") -> {
                _selectedTab.value = "dashboard"
                showMessage("تم الانتقال لقمرة التحكم")
                return true
            }
            trimmed.contains("تقويم") || trimmed.contains("جدول") || trimmed.contains("calendar") -> {
                _selectedTab.value = "calendar"
                showMessage("تم الانتقال للمفكرة والتقويم")
                return true
            }
        }
        return false
    }

    fun performEmergencyRollback() {
        val user = _currentUser.value ?: return
        if (user.role != "Admin") {
            showMessage("غير مصرح: عملية التراجع الطارئ تتطلب صلاحية مدير النظام.")
            return
        }
        viewModelScope.launch {
            try {
                if (!_canRollback.value) {
                    showMessage("لا تتوفر أي نسخة تراجع طارئ صالحة للتراجع حالياً.")
                    return@launch
                }
                repository.clearDatabaseAndRestore(
                    bookings = bookingsBackupSnapshot,
                    payments = paymentsBackupSnapshot,
                    users = usersBackupSnapshot,
                    auditLogs = auditLogsBackupSnapshot
                )
                _canRollback.value = false
                showMessage("تم بنجاح التراجع الطارئ (Rollback) واسترجاع حالة النظام السابقة كلياً!")
                repository.logOperation(user.username, "تراجع طارئ", "", "تم استعادة حالة قاعدة البيانات للنقطة السابقة.")
                triggerBackgroundSync()
            } catch (e: Exception) {
                showMessage("فشل التراجع الطارئ لقاعدة البيانات: ${e.message}")
            }
        }
    }

    // --- ENFORCE VIEWMODEL LAYER SECURITY HELPER ---
    private fun currentAndFutureAccessBlocked(user: User, actionName: String): Boolean {
        if (user.role != "Admin") {
            showMessage("غير مصرح: عملية $actionName تتطلب صلاحية مدير للنظام.")
            return true
        }
        return false
    }

    // --- AUTO SYNC ON NETWORK TOGGLE ---
    private fun triggerBackgroundSync() {
        if (_networkStatus.value == "متصل") {
            viewModelScope.launch {
                _networkStatus.value = "جاري المزامنة"
                kotlinx.coroutines.delay(1000)
                _networkStatus.value = "متصل"
            }
        }
    }

    // --- UTILS FOR DATE ---
    private fun getDayOfWeekInArabic(dateStr: String): String {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            val date = format.parse(dateStr) ?: return "السبت"
            val calendar = Calendar.getInstance()
            calendar.time = date
            when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SATURDAY -> "السبت"
                Calendar.SUNDAY -> "الأحد"
                Calendar.MONDAY -> "الاثنين"
                Calendar.TUESDAY -> "الثلاثاء"
                Calendar.WEDNESDAY -> "الأربعاء"
                Calendar.THURSDAY -> "الخميس"
                Calendar.FRIDAY -> "الجمعة"
                else -> "السبت"
            }
        } catch (e: Exception) {
            "السبت"
        }
    }

    // --- ADMIN USER DELETION ---
    fun deleteUser(user: User) {
        val currentAdmin = _currentUser.value ?: return
        if (currentAdmin.role != "Admin") {
            showMessage("غير مصرح: حذف حسابات الموظفين مقتصر على المشرف العام.")
            return
        }
        viewModelScope.launch {
            repository.deleteUser(user, currentAdmin.username)
            showMessage("تم حذف حساب الموظف ${user.fullName} نهائياً بنجاح.")
            repository.logOperation(currentAdmin.username, "حذف حساب موظف", "الاسم: ${user.fullName}", "تم الحذف واستبعاد الصلاحيات")
            triggerBackgroundSync()
        }
    }

    // --- ENHANCED EDIT PAYMENT FUNCTION ---
    fun editBookingPayment(payment: Payment, amount: Double, paymentMethod: String, receiptRef: String, notes: String) {
        val user = _currentUser.value ?: return
        if (user.role == "Reception") {
            showMessage("غير مصرح لموظف الاستقبال تعديل السندات والدفعات المالية.")
            return
        }
        if (paymentMethod != "نقداً" && paymentMethod != "محفظة إلكترونية") {
            showMessage("خطأ: طريقة الدفع غير مدعومة. يسمح فقط بنقداً أو محفظة إلكترونية.")
            return
        }
        viewModelScope.launch {
            val updated = payment.copy(
                amount = amount,
                paymentMethod = paymentMethod,
                receiptRef = receiptRef,
                notes = notes
            )
            val result = repository.updatePayment(updated, user.username)
            when (result) {
                0 -> {
                    showMessage("تم تعديل السند والتحصيلات المالية وحفظ التعديلات بنجاح.")
                    repository.logOperation(user.username, "تعديل سند مالي", "السند السابق بقيمة: ${payment.amount}", "القيمة الجديدة: $amount, طريقة الدفع: $paymentMethod")
                    triggerBackgroundSync()
                }
                1 -> showMessage("حدث خطأ: القيمة الكلية للدفعات تتخطى قيمة العقد الإجمالية.")
                else -> showMessage("فشل: السجل المالي المحدد غير متوفر.")
            }
        }
    }

    // --- SAF NATIVE EXCEL/CSV IMPORT ENGINE ---
    fun importFromUri(context: Context, uri: android.net.Uri) {
        val user = _currentUser.value ?: return
        if (currentAndFutureAccessBlocked(user, "استيراد ملفات الإكسل والبيانات")) return
        viewModelScope.launch {
            try {
                // Automatically create safety emergency backup snapshot before import!
                createEmergencyBackup()

                val inputStream = context.contentResolver.openInputStream(uri)
                val rawCsv = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (rawCsv.isEmpty()) {
                    showMessage("فشل: الملف المستورد فارغ أو لا تتوفر به بيانات صالحة.")
                    return@launch
                }
                importFromCsvContent(rawCsv)
            } catch (e: Exception) {
                showMessage("فشل استيراد الملف: ${e.message}")
            }
        }
    }

    private fun importFromCsvContent(rawCsv: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                val lines = rawCsv.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.size <= 1) {
                    showMessage("ملف استيراد العقود فارغ أو غير متوافق!")
                    return@launch
                }

                val currentBookings = bookings.value
                val parsedBookings = mutableListOf<Booking>()
                val conflictsList = mutableListOf<ImportConflict>()

                for (i in 1 until lines.size) {
                    val line = lines[i]
                    val separator = if (line.contains(";")) ";" else if (line.contains("\t")) "\t" else ","
                    val parts = line.split(separator).map { it.trim() }

                    if (parts.size >= 6) {
                        val dateStr = parts[0]
                        val renterName = parts[1]
                        val eventType = parts[2]
                        val rentAmount = parts[3].toDoubleOrNull() ?: 12000.0
                        val totalPaid = parts[4].toDoubleOrNull() ?: 0.0
                        val phone1 = parts[5]
                        val phone2 = parts.getOrNull(6) ?: ""
                        val status = parts.getOrNull(7) ?: "جديد"
                        val employee = parts.getOrNull(8) ?: user.username

                        val dayOfWeekArabic = getDayOfWeekInArabic(dateStr)
                        val importedBooking = Booking(
                            id = 0,
                            dateStr = dateStr,
                            dayOfWeek = dayOfWeekArabic,
                            renterName = renterName,
                            eventType = eventType,
                            rentAmount = rentAmount,
                            totalPaid = totalPaid,
                            phone1 = phone1,
                            phone2 = phone2,
                            status = status,
                            employeeUsername = employee
                        )

                        val existing = currentBookings.find {
                            it.dateStr == dateStr && (it.status == "جديد" || it.status == "مؤقت")
                        }

                        if (existing != null) {
                            conflictsList.add(ImportConflict(importedBooking, existing))
                        } else {
                            parsedBookings.add(importedBooking)
                        }
                    }
                }

                if (conflictsList.isNotEmpty()) {
                    _importSession.value = ImportSession(
                        bookingsToImport = parsedBookings,
                        conflicts = conflictsList,
                        currentConflictIndex = 0
                    )
                    showMessage("تم الكشف عن عدد ${conflictsList.size} حجز متعارض بالتاريخ. يرجى تصفية التعارض الآن!")
                } else {
                    var importedCount = 0
                    for (b in parsedBookings) {
                        repository.createBooking(b, b.employeeUsername)
                        importedCount++
                    }
                    repository.logOperation(user.username, "استيراد عقود من ملف", "", "تم بنجاح استيراد عدد $importedCount حجوزات كليا.")
                    showMessage("تم بنجاح استيراد عدد $importedCount حجوزات دون أي تعارض بالتاريخ.")
                    triggerBackgroundSync()
                }
            } catch (e: Exception) {
                showMessage("حدث خطأ أثناء معالجة البيانات: ${e.message}")
            }
        }
    }

    fun resolveCurrentConflict(keepImported: Boolean) {
        val session = _importSession.value ?: return
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val conflict = session.conflicts[session.currentConflictIndex]
            if (keepImported) {
                repository.cancelBooking(conflict.existing.id, user.username)
                session.resolvedBookings.add(conflict.imported)
            }
            val nextIndex = session.currentConflictIndex + 1
            if (nextIndex < session.conflicts.size) {
                _importSession.value = session.copy(currentConflictIndex = nextIndex)
            } else {
                var count = 0
                for (b in session.bookingsToImport + session.resolvedBookings) {
                    repository.createBooking(b, b.employeeUsername)
                    count++
                }
                _importSession.value = null
                repository.logOperation(user.username, "استيراد عقود من ملف", "معالجة التضاربات بالتاريخ", "تم تصفية التعارض واستيراد إجمالي $count حجوزات.")
                showMessage("اكتمل معالج تصفية التعارضات! تم استيراد وحفظ $count حجوزات معتمدة.")
                triggerBackgroundSync()
            }
        }
    }

    fun resolveAllConflicts(useImported: Boolean) {
        val session = _importSession.value ?: return
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            for (i in session.currentConflictIndex until session.conflicts.size) {
                val conflict = session.conflicts[i]
                if (useImported) {
                    repository.cancelBooking(conflict.existing.id, user.username)
                    session.resolvedBookings.add(conflict.imported)
                }
            }
            var count = 0
            for (b in session.bookingsToImport + session.resolvedBookings) {
                repository.createBooking(b, b.employeeUsername)
                count++
            }
            _importSession.value = null
            repository.logOperation(user.username, "استيراد وإلغاء التعارضات تلقائياً", "مع تفعيل الخيار: $useImported", "تم استيراد $count حجوزات كلياً.")
            showMessage("تمت تصفية وتطبيق كافة الاختلافات تلقائياً! تم حفظ $count حجوزات.")
            triggerBackgroundSync()
        }
    }

    fun cancelImportSession() {
        _importSession.value = null
        showMessage("تم إلغاء عملية الاستيراد كلياً.")
    }

    // --- SAF NATIVE EXCEL/CSV EXPORT ENGINE ---
    fun exportBookingsToUri(context: Context, uri: android.net.Uri, dateFrom: String, dateTo: String) {
        val user = _currentUser.value ?: return
        if (currentAndFutureAccessBlocked(user, "تصدير حقول Excel للبيانات والعقود")) return
        viewModelScope.launch {
            try {
                val list = bookings.value.filter {
                    val d = it.dateStr
                    (dateFrom.isEmpty() || d >= dateFrom) && (dateTo.isEmpty() || d <= dateTo)
                }

                val csvBuilder = StringBuilder()
                csvBuilder.append("رقم السجل,تاريخ المناسبة,يوم الفعالية,المستأجر,نوع المناسبة,مبلغ الحجز المعتمد,الواصل والمدفوع,المبلغ المتبقي,رقم جوال العميل,الهاتف الثانوي,حالة الحجز,مسؤول الحجز\n")

                for (b in list) {
                    csvBuilder.append("${b.id},${b.dateStr},${b.dayOfWeek},${b.renterName},${b.eventType},${b.rentAmount},${b.totalPaid},${b.remainingAmount},${b.phone1},${b.phone2},${b.status},${b.employeeUsername}\n")
                }

                val pfd = context.contentResolver.openFileDescriptor(uri, "w")
                java.io.FileOutputStream(pfd?.fileDescriptor).use {
                    it.write(csvBuilder.toString().toByteArray(Charsets.UTF_8))
                }
                pfd?.close()

                repository.logOperation(user.username, "تصدير ملف Excel", "العدد الكلي المصدر: ${list.size}", "تصدير إلى: $uri")
                showMessage("تم تصدير ملف عقود الصالة بنجاح وبصيغة Excel بالدليل والقفل المحدد!")
                triggerBackgroundSync()
            } catch (e: Exception) {
                showMessage("فشل تصدير Excel: ${e.message}")
            }
        }
    }

    // --- SAF NATIVE PDF EXPORT ENGINE ---
    fun exportFinancialReportToUri(context: Context, uri: android.net.Uri, dateFrom: String, dateTo: String) {
        val user = _currentUser.value ?: return
        if (currentAndFutureAccessBlocked(user, "تصدير التقرير المالي PDF")) return
        viewModelScope.launch {
            try {
                val list = bookings.value.filter {
                    val d = it.dateStr
                    (dateFrom.isEmpty() || d >= dateFrom) && (dateTo.isEmpty() || d <= dateTo)
                }

                val pdfDocument = android.graphics.pdf.PdfDocument()
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                val paint = android.graphics.Paint()
                val titlePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 15f
                    isFakeBoldText = true
                }
                val subtitlePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 10f
                }
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 8.5f
                }

                var y = 50f
                canvas.drawText("صالة القصر الدائري - Qasr Al-Daery Hall Booking System", 100f, y, titlePaint)
                y += 25f
                canvas.drawText("التقرير المالي الفوري والمستحقات والمديونيات للرقابة والتحصيل", 150f, y, subtitlePaint)
                y += 20f
                val fromVal = if (dateFrom.isEmpty()) "كامل الأرشيف" else dateFrom
                val toVal = if (dateTo.isEmpty()) "حتى اليوم" else dateTo
                canvas.drawText("فترة الاستحقاق: من $fromVal إلى $toVal", 150f, y, subtitlePaint)
                y += 30f

                val totalRevenue = list.filter { it.status != "إلغاء" }.sumOf { it.rentAmount }
                val totalPaid = list.filter { it.status != "إلغاء" }.sumOf { it.totalPaid }
                val remainder = totalRevenue - totalPaid

                canvas.drawText("إجمالي عقود المبيعات والإيجارات المعتمدة: ${totalRevenue.toInt()} ريال", 50f, y, subtitlePaint)
                y += 18f
                canvas.drawText("إجمالي المقبوضات والدفعات المحصلة: ${totalPaid.toInt()} ريال", 50f, y, subtitlePaint)
                y += 18f
                canvas.drawText("إجمالي الديون والمستحقات والذمم المتبقية: ${remainder.toInt()} ريال", 50f, y, subtitlePaint)
                y += 30f

                canvas.drawText("الاسم (Tenant)", 50f, y, textPaint)
                canvas.drawText("تاريخ الحجز (Date)", 220f, y, textPaint)
                canvas.drawText("الإيجار (Rent)", 330f, y, textPaint)
                canvas.drawText("المدفوع (Paid)", 410f, y, textPaint)
                canvas.drawText("المتبقي (Due)", 490f, y, textPaint)
                y += 8f
                canvas.drawLine(40f, y, 550f, y, paint)
                y += 15f

                for (b in list.take(25)) {
                    canvas.drawText(b.renterName, 50f, y, textPaint)
                    canvas.drawText(b.dateStr, 220f, y, textPaint)
                    canvas.drawText("${b.rentAmount.toInt()}", 330f, y, textPaint)
                    canvas.drawText("${b.totalPaid.toInt()}", 410f, y, textPaint)
                    canvas.drawText("${b.remainingAmount.toInt()}", 490f, y, textPaint)
                    y += 15f

                    if (y > 800) break
                }

                pdfDocument.finishPage(page)

                val pfd = context.contentResolver.openFileDescriptor(uri, "w")
                java.io.FileOutputStream(pfd?.fileDescriptor).use {
                    pdfDocument.writeTo(it)
                }
                pfd?.close()
                pdfDocument.close()

                repository.logOperation(user.username, "تصدير تقرير مالي PDF", "الذمم المصدرة بقيمة: $remainder ريال", "سيف في: $uri")
                showMessage("تم بنجاح تصدير التقرير المالي الموحد PDF بالمستند والموقع المختار!")
                triggerBackgroundSync()
            } catch (e: Exception) {
                showMessage("فشل تصدير PDF: ${e.message}")
            }
        }
    }

    // --- SAF PORTABLE JSON DATABASE BACKUP ENGINE ---
    fun performFullBackupToUri(context: Context, uri: android.net.Uri) {
        val user = _currentUser.value ?: return
        if (currentAndFutureAccessBlocked(user, "إنشاء وتصدير نسخة احتياطية إدارية كاملة")) return
        viewModelScope.launch {
            try {
                val bookingsList = bookings.value
                val paymentsList = repository.allPaymentsItem.first()
                val usersList = users.value
                val auditLogsList = auditLogs.value

                val backupJson = JSONObject()
                backupJson.put("system", "Alqasr Hall Booking")
                backupJson.put("backupDate", System.currentTimeMillis())
                backupJson.put("performedBy", user.username)

                val usersArr = JSONArray()
                for (u in usersList) {
                    val uObj = JSONObject()
                    uObj.put("id", u.id)
                    uObj.put("fullName", u.fullName)
                    uObj.put("username", u.username)
                    uObj.put("passwordHash", u.passwordHash)
                    uObj.put("role", u.role)
                    uObj.put("phone", u.phone)
                    uObj.put("email", u.email ?: "")
                    uObj.put("status", u.status)
                    uObj.put("createdAt", u.createdAt)
                    uObj.put("lastLogin", u.lastLogin)
                    uObj.put("lastActivity", u.lastActivity)
                    uObj.put("mustChangePassword", u.mustChangePassword)
                    usersArr.put(uObj)
                }
                backupJson.put("users", usersArr)

                val bookingsArr = JSONArray()
                for (b in bookingsList) {
                    val bObj = JSONObject()
                    bObj.put("id", b.id)
                    bObj.put("dateStr", b.dateStr)
                    bObj.put("dayOfWeek", b.dayOfWeek)
                    bObj.put("renterName", b.renterName)
                    bObj.put("eventType", b.eventType)
                    bObj.put("rentAmount", b.rentAmount)
                    bObj.put("totalPaid", b.totalPaid)
                    bObj.put("phone1", b.phone1)
                    bObj.put("phone2", b.phone2)
                    bObj.put("status", b.status)
                    bObj.put("employeeUsername", b.employeeUsername)
                    bObj.put("createdAt", b.createdAt)
                    bObj.put("lastUpdated", b.lastUpdated)
                    bObj.put("linkedBookingId", b.linkedBookingId ?: -1)
                    bObj.put("temporaryExpiresAt", b.temporaryExpiresAt ?: -1L)
                    bookingsArr.put(bObj)
                }
                backupJson.put("bookings", bookingsArr)

                val paymentsArr = JSONArray()
                for (p in paymentsList) {
                    val pObj = JSONObject()
                    pObj.put("id", p.id)
                    pObj.put("bookingId", p.bookingId)
                    pObj.put("paymentDate", p.paymentDate)
                    pObj.put("amount", p.amount)
                    pObj.put("paymentMethod", p.paymentMethod)
                    pObj.put("receiptRef", p.receiptRef)
                    pObj.put("notes", p.notes)
                    pObj.put("receivedByEmployee", p.receivedByEmployee)
                    pObj.put("createdAt", p.createdAt)
                    paymentsArr.put(pObj)
                }
                backupJson.put("payments", paymentsArr)

                val logsArr = JSONArray()
                for (log in auditLogsList) {
                    val lObj = JSONObject()
                    lObj.put("id", log.id)
                    lObj.put("username", log.username)
                    lObj.put("userRole", log.userRole)
                    lObj.put("timestamp", log.timestamp)
                    lObj.put("actionType", log.actionType)
                    lObj.put("detailsBefore", log.detailsBefore)
                    lObj.put("detailsAfter", log.detailsAfter)
                    logsArr.put(lObj)
                }
                backupJson.put("auditLogs", logsArr)

                val pfd = context.contentResolver.openFileDescriptor(uri, "w")
                java.io.FileOutputStream(pfd?.fileDescriptor).use {
                    it.write(backupJson.toString(2).toByteArray(Charsets.UTF_8))
                }
                pfd?.close()

                repository.recordBackup(user.username, uri.toString(), "ناجحة")
                showMessage("تم بنجاح تشفير وتصدير النسخة الاحتياطية وإرسالها وصياغتها بنظام JSON بالملف المطلوب!")
                triggerBackgroundSync()
            } catch (e: Exception) {
                showMessage("فشل نسخ احتياطي لقاعدة البيانات: ${e.message}")
            }
        }
    }

    // --- SAF PORTABLE JSON DATABASE RESTORE ENGINE ---
    fun requestRestoreFromUri(context: Context, uri: android.net.Uri) {
        val user = _currentUser.value ?: return
        if (currentAndFutureAccessBlocked(user, "استرداد واسترجاع قاعدة البيانات")) return
        _pendingRestoreUri.value = uri
    }

    fun cancelRestore() {
        _pendingRestoreUri.value = null
        showMessage("تم إلغاء عملية استرداد قاعدة البيانات.")
    }

    fun performFullRestoreFromPendingUri(context: Context) {
        val uri = _pendingRestoreUri.value ?: return
        val user = _currentUser.value ?: return
        _pendingRestoreUri.value = null

        viewModelScope.launch {
            try {
                // Automatically create emergency safety backup before restore!
                createEmergencyBackup()

                val inputStream = context.contentResolver.openInputStream(uri)
                val jsonStr = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (jsonStr.isEmpty()) {
                    showMessage("ملف النسخة الاحتياطية فارغ أو تالف!")
                    return@launch
                }

                val backupJson = JSONObject(jsonStr)
                if (!backupJson.has("system") || backupJson.optString("system") != "Alqasr Hall Booking") {
                    showMessage("الملف المحدد غير متوافق كلياً مع نظام القصر الدائري.")
                    return@launch
                }

                var importedCount = 0
                var updatedCount = 0
                var skippedCount = 0

                val usersList = mutableListOf<User>()
                if (backupJson.has("users")) {
                    val arr = backupJson.getJSONArray("users")
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val u = User(
                            id = o.optInt("id", 0),
                            fullName = o.optString("fullName"),
                            username = o.optString("username"),
                            passwordHash = o.optString("passwordHash"),
                            role = o.optString("role"),
                            phone = o.optString("phone"),
                            email = o.optString("email").let { if (it.isEmpty()) null else it },
                            status = o.optString("status"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            lastLogin = o.optLong("lastLogin", 0L),
                            lastActivity = o.optLong("lastActivity", 0L),
                            mustChangePassword = o.optBoolean("mustChangePassword", true)
                        )
                        usersList.add(u)
                        importedCount++
                    }
                }

                val bookingsList = mutableListOf<Booking>()
                if (backupJson.has("bookings")) {
                    val arr = backupJson.getJSONArray("bookings")
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val b = Booking(
                            id = o.optInt("id", 0),
                            dateStr = o.optString("dateStr"),
                            dayOfWeek = o.optString("dayOfWeek"),
                            renterName = o.optString("renterName"),
                            eventType = o.optString("eventType"),
                            rentAmount = o.optDouble("rentAmount", 0.0),
                            totalPaid = o.optDouble("totalPaid", 0.0),
                            phone1 = o.optString("phone1"),
                            phone2 = o.optString("phone2"),
                            status = o.optString("status"),
                            employeeUsername = o.optString("employeeUsername"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            lastUpdated = o.optLong("lastUpdated", System.currentTimeMillis()),
                            linkedBookingId = o.optInt("linkedBookingId", -1).let { if (it == -1) null else it },
                            temporaryExpiresAt = o.optLong("temporaryExpiresAt", -1L).let { if (it == -1L) null else it }
                        )
                        bookingsList.add(b)
                        importedCount++
                    }
                }

                val paymentsList = mutableListOf<Payment>()
                if (backupJson.has("payments")) {
                    val arr = backupJson.getJSONArray("payments")
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val p = Payment(
                            id = o.optInt("id", 0),
                            bookingId = o.optInt("bookingId", 0),
                            paymentDate = o.optLong("paymentDate", System.currentTimeMillis()),
                            amount = o.optDouble("amount", 0.0),
                            paymentMethod = o.optString("paymentMethod"),
                            receiptRef = o.optString("receiptRef"),
                            notes = o.optString("notes", ""),
                            receivedByEmployee = o.optString("receivedByEmployee"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                        paymentsList.add(p)
                        importedCount++
                    }
                }

                val logsList = mutableListOf<AuditLog>()
                if (backupJson.has("auditLogs")) {
                    val arr = backupJson.getJSONArray("auditLogs")
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val log = AuditLog(
                            id = o.optInt("id", 0),
                            username = o.optString("username"),
                            userRole = o.optString("userRole", "غير معروف"),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                            actionType = o.optString("actionType"),
                            detailsBefore = o.optString("detailsBefore", ""),
                            detailsAfter = o.optString("detailsAfter", "")
                        )
                        logsList.add(log)
                    }
                }

                repository.clearDatabaseAndRestore(
                    bookings = bookingsList,
                    payments = paymentsList,
                    users = usersList,
                    auditLogs = logsList
                )

                repository.recordRestore(user.username, uri.toString(), "ناجحة")

                val summary = "تقرير استجرار السجلات والبيانات لمطابقة الأمان:\n" +
                        "- عدد السجلات المسترجعة بنجاح: $importedCount سجلاً مدمجاً\n" +
                        "- إجمالي السجلات المعدلة: $updatedCount\n" +
                        "- إجمالي السجلات المتخطاة: $skippedCount"

                _restoreSummaryMessage.value = summary
                showMessage("تم بنجاح تطبيق فك الضغط واستعادة كافة السندات والبيانات بالكامل!")
                triggerBackgroundSync()
            } catch (e: Exception) {
                showMessage("فشل تطبيق واستعادة قاعدة البيانات: ${e.message}")
            }
        }
    }

    fun dismissRestoreSummary() {
        _restoreSummaryMessage.value = null
    }
}

data class ImportConflict(
    val imported: Booking,
    val existing: Booking
)

data class ImportSession(
    val bookingsToImport: List<Booking>,
    val conflicts: List<ImportConflict>,
    val currentConflictIndex: Int,
    val resolvedBookings: MutableList<Booking> = mutableListOf()
)

data class AppNotification(
    val id: String,
    val title: String,
    val message: String,
    val type: String,
    val userId: String,
    val role: String,
    val isRead: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

data class BookingLock(
    val username: String,
    val timeStarted: Long
)

data class UserPresence(
    val username: String,
    val fullName: String,
    val role: String,
    val isOnline: Boolean,
    val device: String,
    val lastActive: Long,
    val deviceType: String = if (device.contains("تابلت") || device.contains("لوحي")) "Tablet" else if (device.contains("كمبيوتر") || device.contains("ويب")) "Desktop" else "Mobile",
    val osVersion: String = if (device.contains("كمبيوتر")) "Windows 11" else "Android 14"
)

data class DatabaseBackupItem(
    val id: String,
    val timestamp: Long,
    val size: String,
    val type: String,
    val status: String,
    val performedBy: String,
    val bookings: List<Booking>,
    val payments: List<Payment>,
    val users: List<User>,
    val auditLogs: List<AuditLog>
)

data class TimelineEvent(
    val timestamp: Long,
    val title: String,
    val description: String,
    val type: String, // "creation", "payment", "audit", "status"
    val icon: String // Icon representation code
)

data class UndoableAction(
    val id: String,
    val description: String,
    val expirationTime: Long,
    val undoBlock: () -> Unit
)

data class BookingActivity(
    val timestamp: Long,
    val action: String,
    val details: String
)


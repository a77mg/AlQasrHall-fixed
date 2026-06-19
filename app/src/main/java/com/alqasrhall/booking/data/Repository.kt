package com.alqasrhall.booking.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Repository(private val db: AppDatabase) {

    val allBookings: Flow<List<Booking>> = db.bookingDao().getAllBookings()
    val allUsers: Flow<List<User>> = db.userDao().getAllUsers()
    val allPaymentsItem: Flow<List<Payment>> = db.paymentDao().getAllPayments()
    val allLogs: Flow<List<AuditLog>> = db.auditLogDao().getAllLogs()
    val backupLogs: Flow<List<BackupLog>> = db.backupLogDao().getLatest30BackupLogs()

    // --- USER MANAGEMENT ---
    suspend fun getUserByUsername(username: String): User? {
        return db.userDao().getUserByUsername(username)
    }

    suspend fun insertUser(user: User, adminUsername: String): Boolean {
        val existing = db.userDao().getUserByUsername(user.username)
        if (existing != null) return false
        val insertedId = db.userDao().insertUser(user)
        val finalUser = user.copy(id = insertedId.toInt())
        FirebaseSyncService.syncUserToFirestore(finalUser)
        logOperation(
            username = adminUsername,
            action = "إضافة مستخدم",
            before = "",
            after = "اسم المستخدم: ${user.username}, الاسم: ${user.fullName}, الصلاحية: ${user.role}"
        )
        return true
    }

    suspend fun updateUser(user: User, adminUsername: String) {
        val before = db.userDao().getUserByUsername(user.username)
        db.userDao().updateUser(user)
        FirebaseSyncService.syncUserToFirestore(user)
        logOperation(
            username = adminUsername,
            action = "تعديل مستخدم",
            before = before?.let { "الاسم: ${it.fullName}, الحالة: ${it.status}, الصلاحية: ${it.role}" } ?: "",
            after = "الاسم: ${user.fullName}, الحالة: ${user.status}, الصلاحية: ${user.role}"
        )
    }

    suspend fun updateLastLogin(username: String) {
        val user = db.userDao().getUserByUsername(username)
        if (user != null) {
            val updated = user.copy(
                lastLogin = System.currentTimeMillis(),
                lastActivity = System.currentTimeMillis()
            )
            db.userDao().updateUser(updated)
            FirebaseSyncService.syncUserToFirestore(updated)
            logOperation(username, "تسجيل دخول", "", "تم تسجيل دخول المستخدم")
        }
    }

    suspend fun updateLastActivity(username: String) {
        val user = db.userDao().getUserByUsername(username)
        if (user != null) {
            val updated = user.copy(lastActivity = System.currentTimeMillis())
            db.userDao().updateUser(updated)
            FirebaseSyncService.syncUserToFirestore(updated)
        }
    }

    // --- BOOKING OPERATIONS ---
    // Returns status: 0 = Success, 1 = Date Blocked (Double booking), 2 = Database Error or Invalid
    suspend fun createBooking(booking: Booking, creatorUsername: String, force: Boolean = false): Int {
        // 1. Prevent duplicate booking if not forced
        if (!force) {
            val blocking = db.bookingDao().getBlockingBookingForDate(booking.dateStr)
            if (blocking != null) {
                return 1 // Duplicate/Double-booking blocked!
            }
        }

        // Calculate expires_at for temporary booking
        val bookingToSave = if (booking.status == "مؤقت") {
            booking.copy(temporaryExpiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000))
        } else {
            booking
        }

        val bookingId = db.bookingDao().insertBooking(bookingToSave)
        val finalBooking = bookingToSave.copy(id = bookingId.toInt())
        FirebaseSyncService.syncBookingToFirestore(finalBooking)
        
        logOperation(
            username = creatorUsername,
            action = "إضافة حجز جديد",
            before = "",
            after = "رقم الحجز: $bookingId, المستأجر: ${booking.renterName}, التاريخ: ${booking.dateStr}, الحالة: ${booking.status}"
        )
        return 0
    }

    suspend fun updateBooking(booking: Booking, updaterUsername: String): Int {
        val oldBooking = db.bookingDao().getBookingById(booking.id) ?: return 2
        
        // If date has changed, check duplicates for the new date
        if (oldBooking.dateStr != booking.dateStr) {
            val blocking = db.bookingDao().getBlockingBookingForDate(booking.dateStr)
            if (blocking != null && blocking.id != booking.id) {
                return 1 // Date blocked by another active booking!
            }
        }

        // Maintain temporary booking expiration logic
        var finalBooking = booking
        if (booking.status == "مؤقت" && oldBooking.status != "مؤقت") {
            finalBooking = booking.copy(temporaryExpiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000))
        } else if (booking.status != "مؤقت") {
            finalBooking = booking.copy(temporaryExpiresAt = null)
        }

        db.bookingDao().updateBooking(finalBooking)
        FirebaseSyncService.syncBookingToFirestore(finalBooking)
        
        logOperation(
            username = updaterUsername,
            action = "تعديل حجز",
            before = "المستأجر: ${oldBooking.renterName}, التاريخ: ${oldBooking.dateStr}, الحالة: ${oldBooking.status}",
            after = "المستأجر: ${finalBooking.renterName}, التاريخ: ${finalBooking.dateStr}, الحالة: ${finalBooking.status}"
        )
        return 0
    }

    // Changing Date (Rescheduling)
    // 0 = Success, 1 = New Date Blocked, 2 = Old Booking not found
    suspend fun rescheduleBooking(bookingId: Int, newDateStr: String, newDayOfWeek: String, employeeUsername: String): Int {
        val oldBooking = db.bookingDao().getBookingById(bookingId) ?: return 2

        // 1. Check if new date is blocked
        val blocking = db.bookingDao().getBlockingBookingForDate(newDateStr)
        if (blocking != null) {
            return 1
        }

        // 2. Change state of old booking to: "استبدال إلى تاريخ آخر"
        val updatedOldBooking = oldBooking.copy(
            status = "استبدال إلى تاريخ آخر",
            lastUpdated = System.currentTimeMillis()
        )
        db.bookingDao().updateBooking(updatedOldBooking)
        FirebaseSyncService.syncBookingToFirestore(updatedOldBooking)

        // 3. Create a new booking on the new date with old booking's info
        val newBooking = Booking(
            dateStr = newDateStr,
            dayOfWeek = newDayOfWeek,
            renterName = oldBooking.renterName,
            eventType = oldBooking.eventType,
            rentAmount = oldBooking.rentAmount,
            totalPaid = oldBooking.totalPaid, // Transfer payments
            phone1 = oldBooking.phone1,
            phone2 = oldBooking.phone2,
            status = "جديد",
            employeeUsername = employeeUsername,
            linkedBookingId = oldBooking.id // Keep reference
        )
        val newId = db.bookingDao().insertBooking(newBooking)
        val finalNewBooking = newBooking.copy(id = newId.toInt())
        FirebaseSyncService.syncBookingToFirestore(finalNewBooking)

        // Link new id back to the old booking
        val fullyLinkedOldBooking = updatedOldBooking.copy(linkedBookingId = newId.toInt())
        db.bookingDao().updateBooking(fullyLinkedOldBooking)
        FirebaseSyncService.syncBookingToFirestore(fullyLinkedOldBooking)

        // 4. Update payments to associate with the new ID
        val payments = db.paymentDao().getPaymentsForBookingSync(oldBooking.id)
        for (payment in payments) {
            val newPayId = db.paymentDao().insertPayment(payment.copy(id = 0, bookingId = newId.toInt()))
            FirebaseSyncService.syncPaymentToFirestore(payment.copy(id = newPayId.toInt(), bookingId = newId.toInt()))
        }

        logOperation(
            username = employeeUsername,
            action = "تغيير تاريخ المناسبة",
            before = "الحجز #${oldBooking.id}, التاريخ القديم: ${oldBooking.dateStr}",
            after = "التاريخ الجديد: $newDateStr, الحجز الجديد #${newId}"
        )
        return 0
    }

    // Cancel Booking
    suspend fun cancelBooking(bookingId: Int, employeeUsername: String) {
        val oldBooking = db.bookingDao().getBookingById(bookingId) ?: return
        val updated = oldBooking.copy(
            status = "إلغاء",
            lastUpdated = System.currentTimeMillis(),
            temporaryExpiresAt = null
        )
        db.bookingDao().updateBooking(updated)
        FirebaseSyncService.syncBookingToFirestore(updated)
        logOperation(
            username = employeeUsername,
            action = "إلغاء حجز",
            before = "المستأجر: ${oldBooking.renterName}, الحالة: ${oldBooking.status}",
            after = "الحالة: إلغاء"
        )
    }

    // Confirm Temporary Booking
    suspend fun confirmTemporaryBooking(bookingId: Int, employeeUsername: String) {
        val oldBooking = db.bookingDao().getBookingById(bookingId) ?: return
        val updated = oldBooking.copy(
            status = "جديد",
            lastUpdated = System.currentTimeMillis(),
            temporaryExpiresAt = null
        )
        db.bookingDao().updateBooking(updated)
        FirebaseSyncService.syncBookingToFirestore(updated)
        logOperation(
            username = employeeUsername,
            action = "تأكيد حجز مؤقت",
            before = "الحالة: مؤقت",
            after = "الحالة: جديد"
        )
    }

    // Check & Expire Temporary Bookings (Can be called on app launch/resume)
    suspend fun processAutomatedExpirations() {
        val bookings = db.bookingDao().getAllBookings().firstOrNull() ?: return
        val currentTime = System.currentTimeMillis()
        for (booking in bookings) {
            if (booking.status == "مؤقت" && booking.temporaryExpiresAt != null) {
                if (currentTime > booking.temporaryExpiresAt) {
                    val expired = booking.copy(
                        status = "إلغاء",
                        temporaryExpiresAt = null,
                        lastUpdated = currentTime
                    )
                    db.bookingDao().updateBooking(expired)
                    FirebaseSyncService.syncBookingToFirestore(expired)
                    logOperation(
                        username = "النظام التلقائي",
                        action = "إلغاء تلقائي للحجز المؤقت",
                        before = "مهلة الحجز المؤقت: ${booking.temporaryExpiresAt}",
                        after = "الحالة: إلغاء (بسبب انتهاء مهلة 24 ساعة)"
                    )
                }
            }
        }
    }

    // --- PAYMENTS ---
    fun getPaymentsForBooking(bookingId: Int): Flow<List<Payment>> {
        return db.paymentDao().getPaymentsForBooking(bookingId)
    }

    // Returns: 0 = Success, 1 = Error: payment amount exceeds rent amount, 2 = Booking not found
    suspend fun addPayment(payment: Payment, employeeUsername: String): Int {
        val booking = db.bookingDao().getBookingById(payment.bookingId) ?: return 2

        val newTotalPaid = booking.totalPaid + payment.amount
        if (newTotalPaid > booking.rentAmount) {
            return 1 // Prevent payment from exceeding total rent price
        }

        // Insert payment
        val payId = db.paymentDao().insertPayment(payment)
        val finalPayment = payment.copy(id = payId.toInt())
        FirebaseSyncService.syncPaymentToFirestore(finalPayment)

        // Update booking's cached total paid
        val updatedBooking = booking.copy(
            totalPaid = newTotalPaid,
            lastUpdated = System.currentTimeMillis()
        )
        db.bookingDao().updateBooking(updatedBooking)
        FirebaseSyncService.syncBookingToFirestore(updatedBooking)

        logOperation(
            username = employeeUsername,
            action = "إضافة دفعة مالية",
            before = "المدفوع قبل: ${booking.totalPaid}",
            after = "مبلغ الدفعة: ${payment.amount}, طريقة الدفع: ${payment.paymentMethod}, الإجمالي المدفوع: $newTotalPaid"
        )
        return 0
    }

    suspend fun deletePayment(payment: Payment, employeeUsername: String): Boolean {
        val booking = db.bookingDao().getBookingById(payment.bookingId) ?: return false
        
        db.paymentDao().deletePayment(payment)
        // Since we deleted it, the easiest way to reflect deletion is syncing a tombstone or we can rely on our updated booking totals.
        // Let's at least sync the updated booking.
        
        val newTotalPaid = maxOf(0.0, booking.totalPaid - payment.amount)
        val updatedBooking = booking.copy(
            totalPaid = newTotalPaid,
            lastUpdated = System.currentTimeMillis()
        )
        db.bookingDao().updateBooking(updatedBooking)
        FirebaseSyncService.syncBookingToFirestore(updatedBooking)

        logOperation(
            username = employeeUsername,
            action = "حذف دفعة مالية",
            before = "مبلغ الدفعة المحذوفة: ${payment.amount}, سند: ${payment.receiptRef}",
            after = "المدفوع الجديد: $newTotalPaid"
        )
        return true
    }

    // --- ATTACHMENTS ---
    fun getAttachmentsForBooking(bookingId: Int): Flow<List<Attachment>> {
        return db.attachmentDao().getAttachmentsForBooking(bookingId)
    }

    suspend fun addAttachment(attachment: Attachment, employeeUsername: String) {
        db.attachmentDao().insertAttachment(attachment)
        logOperation(
            username = employeeUsername,
            action = "رفع مرفق",
            before = "",
            after = "اسم الملف: ${attachment.fileName}, النوع: ${attachment.fileType}, حجم الملف: ${attachment.fileSize}"
        )
    }

    suspend fun deleteAttachment(id: Int, employeeUsername: String, attachmentInfo: String) {
        db.attachmentDao().deleteAttachmentById(id)
        logOperation(
            username = employeeUsername,
            action = "حذف مرفق",
            before = "مرفق: $attachmentInfo",
            after = "تم الحذف"
        )
    }

    // --- BACKUPS & IMPORT/EXPORT ---
    suspend fun recordBackup(performedBy: String, path: String, status: String) {
        db.backupLogDao().insertBackupLog(
            BackupLog(
                backupPath = path,
                status = status,
                performedBy = performedBy
            )
        )
        logOperation(
            username = performedBy,
            action = "نسخ احتياطي",
            before = "",
            after = "حفظ في: $path, الحالة: $status"
        )
    }

    suspend fun recordRestore(performedBy: String, path: String, status: String) {
        logOperation(
            username = performedBy,
            action = "استعادة حجز",
            before = "",
            after = "استعادة من: $path, الحالة: $status"
        )
    }

    suspend fun clearDatabaseAndRestore(
        bookings: List<Booking>,
        payments: List<Payment>,
        users: List<User>,
        auditLogs: List<AuditLog>
    ) {
        db.clearAllTables()
        for (u in users) {
            db.userDao().insertUser(u)
        }
        for (b in bookings) {
            db.bookingDao().insertBooking(b)
        }
        for (p in payments) {
            db.paymentDao().insertPayment(p)
        }
        for (log in auditLogs) {
            db.auditLogDao().insertLog(log)
        }
    }

    suspend fun deleteUser(user: User, adminUsername: String) {
        db.userDao().deleteUser(user)
        logOperation(
            username = adminUsername,
            action = "حذف مستخدم",
            before = "الاسم: ${user.fullName}, الصلاحية: ${user.role}",
            after = "تم الحذف نهائياً"
        )
    }

    suspend fun updatePayment(payment: Payment, updaterUsername: String): Int {
        val oldPayment = db.paymentDao().getPaymentsForBookingSync(payment.bookingId).find { it.id == payment.id } ?: return 2
        val booking = db.bookingDao().getBookingById(payment.bookingId) ?: return 2
        
        val diff = payment.amount - oldPayment.amount
        val newTotalPaid = booking.totalPaid + diff
        if (newTotalPaid > booking.rentAmount) {
            return 1
        }
        
        db.paymentDao().updatePayment(payment)
        FirebaseSyncService.syncPaymentToFirestore(payment)
        
        val updatedBooking = booking.copy(
            totalPaid = newTotalPaid,
            lastUpdated = System.currentTimeMillis()
        )
        db.bookingDao().updateBooking(updatedBooking)
        FirebaseSyncService.syncBookingToFirestore(updatedBooking)
        
        logOperation(
            username = updaterUsername,
            action = "تعديل دفعة مالية",
            before = "المبلغ السابق: ${oldPayment.amount}, سند: ${oldPayment.receiptRef}",
            after = "المبلغ الجديد: ${payment.amount}, سند: ${payment.receiptRef}, طريقة الدفع: ${payment.paymentMethod}"
        )
        return 0
    }

    // --- UTILITIES ---
    suspend fun logOperation(username: String, action: String, before: String = "", after: String = "") {
        val userRole = if (username == "النظام") {
            "النظام"
        } else if (username == "النظام التلقائي") {
            "النظام التلقائي"
        } else {
            getUserByUsername(username)?.role ?: "غير معروف"
        }
        val logObj = AuditLog(
            username = username,
            userRole = userRole,
            actionType = action,
            detailsBefore = before,
            detailsAfter = after
        )
        val logId = db.auditLogDao().insertLog(logObj)
        FirebaseSyncService.syncAuditLogToFirestore(logObj.copy(id = logId.toInt()))
    }

    // --- RATINGS OPERATIONS ---
    fun getAllRatings(): Flow<List<Rating>> = db.ratingDao().getAllRatings()

    suspend fun addRating(rating: Rating) {
        db.ratingDao().insertRating(rating)
        logOperation(
            username = "النظام الذاتي",
            action = "تسجيل تقييم رضا العملاء",
            before = "",
            after = "رقم الحجز: ${rating.bookingId}, الخدمة: ${rating.serviceQuality}, التنظيم: ${rating.organization}, النظافة: ${rating.cleanliness}, العام: ${rating.overallSatisfaction}"
        )
    }

    // --- ANNOUNCEMENTS OPERATIONS ---
    fun getAllAnnouncements(): Flow<List<Announcement>> = db.announcementDao().getAllAnnouncements()

    suspend fun addAnnouncement(announcement: Announcement, creator: String) {
        db.announcementDao().insertAnnouncement(announcement)
        logOperation(
            username = creator,
            action = "إضافة إعلان مثبّت",
            before = "",
            after = "العنوان: ${announcement.title}, المحتوى: ${announcement.content}"
        )
    }

    suspend fun deleteAnnouncement(id: Int, username: String) {
        db.announcementDao().deleteAnnouncementById(id)
        logOperation(
            username = username,
            action = "حذف إعلان مثبّت",
            before = "رقم الإعلان: $id",
            after = ""
        )
    }

    // --- DISCOUNTS OPERATIONS ---
    fun getAllDiscounts(): Flow<List<Discount>> = db.discountDao().getAllDiscounts()

    suspend fun addDiscount(discount: Discount, approvedBy: String) {
        db.discountDao().insertDiscount(discount)
        
        // Let's load the booking and apply the discount to its rent Amount!
        val booking = db.bookingDao().getBookingById(discount.bookingId)
        if (booking != null) {
            val updatedBooking = booking.copy(
                discountAmount = discount.discountAmount,
                lastUpdated = System.currentTimeMillis()
            )
            db.bookingDao().updateBooking(updatedBooking)
        }
        
        logOperation(
            username = approvedBy,
            action = "تسجيل وتدقيق خصم مالي",
            before = "السعر الأصلي: ${discount.originalAmount}",
            after = "مبلغ الخصم: ${discount.discountAmount}, السعر النهائي: ${discount.finalAmount}, السبب: ${discount.reason}"
        )
    }
}

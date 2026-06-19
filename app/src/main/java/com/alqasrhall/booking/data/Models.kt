package com.alqasrhall.booking.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fullName: String,
    val username: String, // Keep unique index or check uniqueness manually
    val passwordHash: String,
    val role: String, // Admin, Manager, Accountant, Reception
    val phone: String,
    val email: String? = null,
    val status: String, // نشط, موقوف, مؤرشف
    val createdAt: Long = System.currentTimeMillis(),
    val lastLogin: Long = 0L,
    val lastActivity: Long = 0L,
    val mustChangePassword: Boolean = true // Flag to force password change!
)

@Entity(tableName = "bookings")
data class Booking(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateStr: String, // YYYY-MM-DD
    val dayOfWeek: String, // السبت، الأحد...
    val renterName: String,
    val eventType: String, // زواج، خطوبة، تخرج، اجتماعات، أخرى
    val rentAmount: Double,
    val totalPaid: Double = 0.0,
    val phone1: String,
    val phone2: String,
    val status: String, // جديد، مؤقت، استبدال إلى تاريخ آخر، إلغاء
    val employeeUsername: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val linkedBookingId: Int? = null, // ربط السجلين عند تغيير التاريخ
    val temporaryExpiresAt: Long? = null, // مهلة الحجز المؤقت (24 ساعة)
    val notes: String = "",
    val assignedStaff: String = "",
    val discountAmount: Double = 0.0
) {
    val remainingAmount: Double
        get() = maxOf(0.0, (rentAmount - discountAmount) - totalPaid)
}

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookingId: Int,
    val paymentDate: Long,
    val amount: Double,
    val paymentMethod: String, // نقدي، شبكة، تحويل بنكي، شيك
    val receiptRef: String, // رقم السند / المرجع
    val notes: String = "",
    val receivedByEmployee: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "attachments")
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookingId: Int,
    val fileName: String,
    val fileType: String, // PDF, JPG, PNG
    val fileSize: String,
    val localUri: String, // simulated or actual file paths
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val userRole: String = "غير معروف",
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String, // تسجيل دخول، إضافة حجز، إلغاء، تغيير تاريخ...
    val detailsBefore: String = "",
    val detailsAfter: String = ""
)

@Entity(tableName = "backup_logs")
data class BackupLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val backupPath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String, // ناجحة، فشلت
    val performedBy: String
)

@Entity(tableName = "ratings")
data class Rating(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookingId: Int,
    val serviceQuality: Int, // 1 to 5 stars
    val organization: Int, // 1 to 5 stars
    val cleanliness: Int, // 1 to 5 stars
    val overallSatisfaction: Int, // 1 to 5 stars
    val feedbackComment: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "announcements")
data class Announcement(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val pinned: Boolean = true,
    val author: String = "المدير العام",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "discounts")
data class Discount(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookingId: Int,
    val originalAmount: Double,
    val discountAmount: Double,
    val finalAmount: Double,
    val reason: String,
    val approvedBy: String,
    val timestamp: Long = System.currentTimeMillis()
)

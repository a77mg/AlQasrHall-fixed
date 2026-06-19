package com.alqasrhall.booking.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)
}

@Dao
interface BookingDao {
    @Query("SELECT * FROM bookings ORDER BY dateStr DESC")
    fun getAllBookings(): Flow<List<Booking>>

    @Query("SELECT * FROM bookings WHERE id = :id LIMIT 1")
    suspend fun getBookingById(id: Int): Booking?

    @Query("SELECT * FROM bookings WHERE dateStr = :dateStr AND (status = 'جديد' OR status = 'مؤقت') LIMIT 1")
    suspend fun getBlockingBookingForDate(dateStr: String): Booking?

    @Query("SELECT * FROM bookings WHERE dateStr = :dateStr")
    suspend fun getBookingsForDate(dateStr: String): List<Booking>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooking(booking: Booking): Long

    @Update
    suspend fun updateBooking(booking: Booking)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE bookingId = :bookingId ORDER BY paymentDate DESC")
    fun getPaymentsForBooking(bookingId: Int): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE bookingId = :bookingId ORDER BY paymentDate DESC")
    suspend fun getPaymentsForBookingSync(bookingId: Int): List<Payment>

    @Query("SELECT * FROM payments ORDER BY paymentDate DESC")
    fun getAllPayments(): Flow<List<Payment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment): Long

    @Update
    suspend fun updatePayment(payment: Payment)

    @Delete
    suspend fun deletePayment(payment: Payment)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE bookingId = :bookingId ORDER BY createdAt DESC")
    fun getAttachmentsForBooking(bookingId: Int): Flow<List<Attachment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: Attachment): Long

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachmentById(id: Int)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLog): Long
}

@Dao
interface BackupLogDao {
    @Query("SELECT * FROM backup_logs ORDER BY createdAt DESC")
    fun getAllBackupLogs(): Flow<List<BackupLog>>

    @Query("SELECT * FROM backup_logs ORDER BY createdAt DESC LIMIT 30")
    fun getLatest30BackupLogs(): Flow<List<BackupLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupLog(log: BackupLog): Long
}

@Dao
interface RatingDao {
    @Query("SELECT * FROM ratings ORDER BY createdAt DESC")
    fun getAllRatings(): Flow<List<Rating>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRating(rating: Rating): Long
}

@Dao
interface AnnouncementDao {
    @Query("SELECT * FROM announcements ORDER BY pinned DESC, createdAt DESC")
    fun getAllAnnouncements(): Flow<List<Announcement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnouncement(announcement: Announcement): Long

    @Query("DELETE FROM announcements WHERE id = :id")
    suspend fun deleteAnnouncementById(id: Int)
}

@Dao
interface DiscountDao {
    @Query("SELECT * FROM discounts ORDER BY timestamp DESC")
    fun getAllDiscounts(): Flow<List<Discount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiscount(discount: Discount): Long
}

@Database(
    entities = [
        User::class,
        Booking::class,
        Payment::class,
        Attachment::class,
        AuditLog::class,
        BackupLog::class,
        Rating::class,
        Announcement::class,
        Discount::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun bookingDao(): BookingDao
    abstract fun paymentDao(): PaymentDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun backupLogDao(): BackupLogDao
    abstract fun ratingDao(): RatingDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun discountDao(): DiscountDao
}

package com.alqasrhall.booking.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object FirebaseSyncService {
    private const val TAG = "FirebaseSyncService"
    
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun initialize(context: Context) {
        try {
            // Guarantee manual FirebaseApp initialization
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
                Log.d(TAG, "Manual FirebaseApp initialization completed successfully.")
            }
            // Enable Firestore Offline Persistence explicitly:
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            firestore.firestoreSettings = settings
            Log.d(TAG, "Firebase initialized on Android successfully with offline persistence. Project: alqasrhall-d5c66")
        } catch (e: Throwable) {
            Log.e(TAG, "Error configuring offline persistence or initializing Firebase: ${e.message}", e)
        }
    }

    // Authenticate user with Firebase matching local credentials.
    // Derived email system lets us map username (e.g., "admin") cleanly.
    fun authenticateUser(username: String, pass: String, onComplete: (Boolean) -> Unit = {}) {
        val email = "${username.trim()}@qasrhall.com"
        val password = pass.trim()
        
        // Passwords must be at least 6 characters in Firebase Auth
        val safePassword = if (password.length >= 6) password else "${password}qasrhall"

        auth.signInWithEmailAndPassword(email, safePassword).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Firebase Authentication successful for user: $username")
                onComplete(true)
            } else {
                Log.w(TAG, "Firebase Sign-in failed. Attempting automatic account provisioning.")
                auth.createUserWithEmailAndPassword(email, safePassword).addOnCompleteListener { createCtx ->
                    if (createCtx.isSuccessful) {
                        Log.d(TAG, "Firebase account created and logged in for: $username")
                        onComplete(true)
                    } else {
                        Log.e(TAG, "Firebase Auth integration error: ${createCtx.exception?.message}")
                        onComplete(false)
                    }
                }
            }
        }
    }

    // Sync user info
    fun syncUserToFirestore(user: User) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = hashMapOf(
                    "id" to user.id,
                    "fullName" to user.fullName,
                    "username" to user.username,
                    "role" to user.role,
                    "phone" to user.phone,
                    "email" to user.email,
                    "status" to user.status,
                    "createdAt" to user.createdAt,
                    "lastLogin" to user.lastLogin,
                    "lastActivity" to user.lastActivity,
                    "mustChangePassword" to user.mustChangePassword
                )
                firestore.collection("users").document(user.username)
                    .set(data)
                    .addOnSuccessListener {
                        Log.d(TAG, "User ${user.username} synced to Firestore successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Failed to sync user: ${e.message}, saved offline")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during user sync: ${e.message}", e)
            }
        }
    }

    // Sync booking
    fun syncBookingToFirestore(booking: Booking) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = hashMapOf(
                    "id" to booking.id,
                    "dateStr" to booking.dateStr,
                    "dayOfWeek" to booking.dayOfWeek,
                    "renterName" to booking.renterName,
                    "eventType" to booking.eventType,
                    "rentAmount" to booking.rentAmount,
                    "totalPaid" to booking.totalPaid,
                    "phone1" to booking.phone1,
                    "phone2" to booking.phone2,
                    "status" to booking.status,
                    "employeeUsername" to booking.employeeUsername,
                    "createdAt" to booking.createdAt,
                    "lastUpdated" to booking.lastUpdated,
                    "linkedBookingId" to booking.linkedBookingId,
                    "temporaryExpiresAt" to booking.temporaryExpiresAt
                )
                firestore.collection("bookings").document(booking.id.toString())
                    .set(data)
                    .addOnSuccessListener {
                        Log.d(TAG, "Booking #${booking.id} synced to Firestore successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Firebase offline synchronization queued for booking: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Sync booking error: ${e.message}")
            }
        }
    }

    // Sync payments
    fun syncPaymentToFirestore(payment: Payment) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = hashMapOf(
                    "id" to payment.id,
                    "bookingId" to payment.bookingId,
                    "paymentDate" to payment.paymentDate,
                    "amount" to payment.amount,
                    "paymentMethod" to payment.paymentMethod,
                    "receiptRef" to payment.receiptRef,
                    "notes" to payment.notes,
                    "receivedByEmployee" to payment.receivedByEmployee,
                    "createdAt" to payment.createdAt
                )
                firestore.collection("payments").document(payment.id.toString())
                    .set(data)
                    .addOnSuccessListener {
                        Log.d(TAG, "Payment #${payment.id} synced to Firestore successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Firebase offline persistence queued for payment: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Sync payment error: ${e.message}")
            }
        }
    }

    // Sync audit log
    fun syncAuditLogToFirestore(log: AuditLog) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = hashMapOf(
                    "id" to log.id,
                    "username" to log.username,
                    "userRole" to log.userRole,
                    "timestamp" to log.timestamp,
                    "actionType" to log.actionType,
                    "detailsBefore" to log.detailsBefore,
                    "detailsAfter" to log.detailsAfter
                )
                firestore.collection("audit_logs").document(log.id.toString())
                    .set(data)
                    .addOnSuccessListener {
                        Log.d(TAG, "Audit log #${log.id} synced to Firestore successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Firebase offline persistence queued for audit log: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Sync audit log error: ${e.message}")
            }
        }
    }

    // Sync settings
    fun syncSettingsToFirestore(hallName: String = "صالة القصر الدائري", currency: String = "الريال اليمني (YER)") {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = hashMapOf(
                    "hallName" to hallName,
                    "currency" to currency,
                    "lastUpdated" to System.currentTimeMillis()
                )
                firestore.collection("settings").document("hall_config")
                    .set(data)
                    .addOnSuccessListener {
                        Log.d(TAG, "Settings synced to Firestore successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed syncing settings to Firestore: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Sync settings error: ${e.message}")
            }
        }
    }

    // Bulk upload from local DB helper
    fun triggerFullSync(
        users: List<User>,
        bookings: List<Booking>,
        payments: List<Payment>,
        logs: List<AuditLog>
    ) {
        Log.d(TAG, "Starting Full Sync to Firebase Cloud Firestore...")
        users.forEach { syncUserToFirestore(it) }
        bookings.forEach { syncBookingToFirestore(it) }
        payments.forEach { syncPaymentToFirestore(it) }
        logs.forEach { syncAuditLogToFirestore(it) }
        syncSettingsToFirestore()
    }
}

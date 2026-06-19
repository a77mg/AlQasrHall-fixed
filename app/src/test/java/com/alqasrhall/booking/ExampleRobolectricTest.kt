package com.alqasrhall.booking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alqasrhall.booking.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("صالة القصر الدائري", appName)
  }

  @Test
  fun `test AppViewModel instantiation`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    try {
        println("====== TRACING START: Instantiating AppViewModel ======")
        val vm = com.alqasrhall.booking.ui.AppViewModel(context)
        println("====== TRACING SUCCESS: AppViewModel Instantiated: $vm ======")
    } catch(e: Throwable) {
        println("====== TRACING CRASH DETECTED ======")
        e.printStackTrace()
        throw e
    }
  }

  @Test
  fun `test FirebaseSyncService initialization`() {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    try {
        println("====== TRACING START: Initializing Firebase ======")
        com.alqasrhall.booking.data.FirebaseSyncService.initialize(context)
        println("====== TRACING SUCCESS: Firebase Initialized successfully. ======")
    } catch(e: Throwable) {
        println("====== TRACING CRASH DETECTED ======")
        e.printStackTrace()
        throw e
    }
  }
}

package com.alqasrhall.booking

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alqasrhall.booking.data.FirebaseSyncService
import com.alqasrhall.booking.ui.AppViewModel
import com.alqasrhall.booking.ui.MainAppContent
import com.alqasrhall.booking.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.e("APP_TRACE", "MainActivity Started")
        super.onCreate(savedInstanceState)
        
        Log.e("APP_TRACE", "Before Firebase Init")
        // Initialize Firebase SDK with offline persistence
        FirebaseSyncService.initialize(applicationContext)
        Log.e("APP_TRACE", "After Firebase Init")

        // Enable Edge-to-Edge full content bleeding
        enableEdgeToEdge()
        Log.e("APP_TRACE", "Before Compose")
        setContent {
            val appViewModel: AppViewModel = viewModel()
            val isDarkTheme by appViewModel.isDarkTheme.collectAsStateWithLifecycle()
            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    MainAppContent(viewModel = appViewModel)
                }
            }
        }
        Log.e("APP_TRACE", "After Compose")
    }
}

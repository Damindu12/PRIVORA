package com.privora.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.privora.app.service.AppLockService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startForegroundAppLockService()

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF090D16)
            ) {
                // Jetpack Compose Navigation Graph
            }
        }
    }

    private fun startForegroundAppLockService() {
        val intent = Intent(this, AppLockService::class.java)
        try {
            startForegroundService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

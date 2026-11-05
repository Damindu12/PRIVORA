package com.privora.app

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import com.privora.app.data.LockedAppsRepository
import com.privora.app.security.SecurityRepository
import com.privora.app.service.PrivoraAccessibilityService
import com.privora.app.ui.HomeScreen

class MainActivity : ComponentActivity() {

    private lateinit var security: SecurityRepository
    private lateinit var lockedRepository: LockedAppsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        security = SecurityRepository.getInstance(this)
        lockedRepository = LockedAppsRepository(this)

        setContent {
            MaterialTheme {
                Surface(color = Color(0xFF090D16)) {
                    HomeScreen(
                        packageManager = packageManager,
                        security = security,
                        lockedRepository = lockedRepository,

                        onUsageAccess = {
                            openAccessibilitySettings()
                        },

                        onProtectionToggle = { enabled ->
                            if (enabled) {
                                if (isAccessibilityEnabled()) {
                                    security.setMasterProtectionActive(true)
                                    true
                                } else {
                                    openAccessibilitySettings()
                                    false
                                }
                            } else {
                                security.setMasterProtectionActive(false)
                                lockedRepository.clearAllUnlocked()
                                true
                            }
                        }
                    )
                }
            }
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        )
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = ComponentName(
            this,
            PrivoraAccessibilityService::class.java
        )

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any {
            it.equals(
                service.flattenToString(),
                ignoreCase = true
            )
        }
    }
}
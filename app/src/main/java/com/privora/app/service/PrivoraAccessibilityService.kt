package com.privora.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.privora.app.data.LockedAppsRepository
import com.privora.app.security.SecurityRepository
import com.privora.app.ui.LockScreenActivity

class PrivoraAccessibilityService : AccessibilityService() {

    private lateinit var repository: LockedAppsRepository
    private lateinit var security: SecurityRepository

    private var lockScreenShowing = false
    private var lockedTargetPackage: String? = null
    private var lastForegroundPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = LockedAppsRepository(this)
        security = SecurityRepository.getInstance(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        if (!::repository.isInitialized || !::security.isInitialized) return

        val packageName = event.packageName?.toString() ?: return

        // Ignore PRIVORA's own lock and capture screens.
        if (packageName == applicationContext.packageName) return

        val previousPackage = lastForegroundPackage

        if (
            previousPackage != null &&
            previousPackage != packageName
        ) {
            repository.clearUnlocked(previousPackage)

            if (previousPackage == lockedTargetPackage) {
                lockedTargetPackage = null
                lockScreenShowing = false
            }
        }

        lastForegroundPackage = packageName

        if (!security.isMasterProtectionActive()) return

        if (!repository.isAppLocked(packageName)) return

        if (repository.isTemporarilyUnlocked(packageName)) {
            if (packageName == lockedTargetPackage) {
                lockScreenShowing = false
                lockedTargetPackage = null
            }
            return
        }

        if (
            lockScreenShowing &&
            lockedTargetPackage == packageName
        ) return

        lockedTargetPackage = packageName
        lockScreenShowing = true

        val lockIntent = Intent(
            this,
            LockScreenActivity::class.java
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra(
                LockScreenActivity.EXTRA_PACKAGE_NAME,
                packageName
            )
        }

        try {
            startActivity(lockIntent)
        } catch (e: Exception) {
            lockScreenShowing = false
            lockedTargetPackage = null
            e.printStackTrace()
        }
    }

    override fun onInterrupt() {
        lockScreenShowing = false
        lockedTargetPackage = null
        lastForegroundPackage = null
    }
}
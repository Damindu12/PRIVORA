package com.privora.app.data

import android.content.Context

class LockedAppsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(
        "privora_locked_apps",
        Context.MODE_PRIVATE
    )

    fun getLockedApps(): Set<String> {
        return prefs.getStringSet(
            "locked_packages",
            emptySet()
        )?.toSet() ?: emptySet()
    }

    fun setLockedApps(packages: Set<String>) {
        prefs.edit()
            .putStringSet("locked_packages", packages.toSet())
            .apply()
    }

    fun isAppLocked(packageName: String): Boolean {
        return getLockedApps().contains(packageName)
    }

    fun addLockedApp(packageName: String) {
        setLockedApps(getLockedApps() + packageName)
    }

    fun removeLockedApp(packageName: String) {
        setLockedApps(getLockedApps() - packageName)
        clearUnlocked(packageName)
    }

    // Unlock remains active while the protected app stays in use.
    fun markUnlocked(packageName: String) {
        prefs.edit()
            .putBoolean("unlocked_$packageName", true)
            .apply()
    }

    fun isTemporarilyUnlocked(packageName: String): Boolean {
        return prefs.getBoolean("unlocked_$packageName", false)
    }

    fun clearUnlocked(packageName: String) {
        prefs.edit()
            .remove("unlocked_$packageName")
            .apply()
    }

    fun clearAllUnlocked() {
        val editor = prefs.edit()

        prefs.all.keys
            .filter { it.startsWith("unlocked_") }
            .forEach { editor.remove(it) }

        editor.apply()
    }
}
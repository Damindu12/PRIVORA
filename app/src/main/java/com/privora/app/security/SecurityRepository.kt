package com.privora.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * PRIVORA Hardware Security & Storage Manager
 * Uses Android Keystore (AES-256-GCM) - Zero plain-text credentials.
 * Supports:
 * - 4 to 6 digit Numeric PIN or Alphanumeric Password
 * - User-created personal recovery secret key
 * - 3-attempt Biometric lockout with "Try PIN or password" redirection
 * - Dual-Camera intruder capture (Front selfie + Rear environment photo)
 */
class SecurityRepository private constructor(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            "privora_secure_vault",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: SecurityRepository? = null

        fun getInstance(context: Context): SecurityRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurityRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun isConfigured(): Boolean = prefs.contains("credential_hash")

    fun getLockType(): String = prefs.getString("lock_type", "pin") ?: "pin"

    fun getPinLength(): Int = prefs.getInt("pin_length", 4)

    /**
     * Saves master credential (PIN 4-6 numbers or Password) with random 16-byte salt and SHA-256
     */
    fun saveCredential(type: String, credential: String, pinLength: Int = 4) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hash = computeSha256("$saltHex:$credential:PRIVORA_PEPPER")

        prefs.edit()
            .putString("lock_type", type)
            .putInt("pin_length", pinLength)
            .putString("credential_salt", saltHex)
            .putString("credential_hash", hash)
            .apply()
    }

    fun verifyCredential(candidate: String): Boolean {
        val saltHex = prefs.getString("credential_salt", null) ?: return false
        val storedHash = prefs.getString("credential_hash", null) ?: return false
        val computed = computeSha256("$saltHex:$candidate:PRIVORA_PEPPER")
        return computed == storedHash
    }

    /**
     * User-created personal secret recovery key (Never auto-generated without user choice)
     */
    fun savePersonalRecoveryKey(customKey: String) {
        val trimmed = customKey.trim()
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hash = computeSha256("$saltHex:$trimmed:PRIVORA_RECOVERY")

        prefs.edit()
            .putString("recovery_salt", saltHex)
            .putString("recovery_hash", hash)
            .putString("recovery_display", trimmed)
            .apply()
    }

    fun verifyPersonalRecoveryKey(candidate: String): Boolean {
        val trimmed = candidate.trim()
        val saltHex = prefs.getString("recovery_salt", null) ?: return false
        val storedHash = prefs.getString("recovery_hash", null) ?: return false
        val computed = computeSha256("$saltHex:$trimmed:PRIVORA_RECOVERY")
        return computed == storedHash
    }

    /**
     * Biometric 3-failure lockout rules:
     * Exceeding 3 biometric attempts locks out sensor and redirects to PIN or Password.
     */
    fun getBiometricFailures(): Int = prefs.getInt("bio_fail_count", 0)

    fun recordBiometricFailure(): Int {
        val current = getBiometricFailures() + 1
        prefs.edit().putInt("bio_fail_count", current).apply()
        return current
    }

    fun isBiometricLockedOut(): Boolean = getBiometricFailures() >= 3

    fun resetBiometricFailures() {
        prefs.edit().putInt("bio_fail_count", 0).apply()
    }

    /**
     * Dual-Camera Intruder Capture Configuration (Front Selfie + Rear Environment Photo)
     */
    fun isDualCameraCaptureEnabled(): Boolean = prefs.getBoolean("dual_camera_capture", true)

    fun setDualCameraCaptureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("dual_camera_capture", enabled).apply()
    }

    fun isIntruderSelfieEnabled(): Boolean = prefs.getBoolean("intruder_selfie_enabled", true)

    fun setIntruderSelfieEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("intruder_selfie_enabled", enabled).apply()
    }

    private fun computeSha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isMasterProtectionActive(): Boolean = prefs.getBoolean("master_protection", true)

    fun setMasterProtectionActive(active: Boolean) {
        prefs.edit().putBoolean("master_protection", active).apply()
    }

    fun getLockedPackageNames(): Set<String> {
        return prefs.getStringSet("locked_packages", emptySet()) ?: emptySet()
    }

    fun setLockedPackageNames(packages: Set<String>) {
        prefs.edit().putStringSet("locked_packages", packages).apply()
    }

    fun getRelockDelayMillis(): Long {
        return when (prefs.getString("relock_interval", "immediate")) {
            "delay_1m" -> 60_000L
            "delay_5m" -> 300_000L
            else -> 0L
        }
    }
}

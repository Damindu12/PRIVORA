package com.privora.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

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
                INSTANCE ?: SecurityRepository(
                    context.applicationContext
                ).also { INSTANCE = it }
            }
        }

        private const val HASH_ITERATIONS = 120_000
        private const val HASH_LENGTH = 256
    }

    private fun createSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashSecret(secret: String, saltHex: String): String {
        val salt = ByteArray(saltHex.length / 2) {
            saltHex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }

        val spec = PBEKeySpec(
            secret.toCharArray(),
            salt,
            HASH_ITERATIONS,
            HASH_LENGTH
        )

        return try {
            val factory = SecretKeyFactory.getInstance(
                "PBKDF2WithHmacSHA256"
            )
            factory.generateSecret(spec).encoded.joinToString("") {
                "%02x".format(it)
            }
        } finally {
            spec.clearPassword()
        }
    }

    private fun verifyHash(
        candidate: String,
        saltKey: String,
        hashKey: String
    ): Boolean {
        val salt = prefs.getString(saltKey, null) ?: return false
        val savedHash = prefs.getString(hashKey, null) ?: return false

        val candidateHash = hashSecret(candidate, salt)

        return MessageDigest.isEqual(
            candidateHash.toByteArray(Charsets.UTF_8),
            savedHash.toByteArray(Charsets.UTF_8)
        )
    }

    // PIN / Password

    fun isConfigured(): Boolean {
        return prefs.contains("credential_hash")
    }

    fun getLockType(): String {
        return prefs.getString("lock_type", "pin") ?: "pin"
    }

    fun getPinLength(): Int {
        return prefs.getInt("pin_length", 4)
    }

    fun saveCredential(
        type: String,
        credential: String,
        pinLength: Int = 4
    ) {
        val salt = createSalt()
        val hash = hashSecret(credential, salt)

        prefs.edit()
            .putString("lock_type", type)
            .putInt("pin_length", pinLength)
            .putString("credential_salt", salt)
            .putString("credential_hash", hash)
            .apply()
    }

    fun verifyCredential(candidate: String): Boolean {
        return verifyHash(
            candidate,
            "credential_salt",
            "credential_hash"
        )
    }

    // Personal Recovery Key

    fun hasRecoveryKey(): Boolean {
        return prefs.contains("recovery_hash")
    }

    fun savePersonalRecoveryKey(customKey: String) {
        val key = customKey.trim()
        require(key.length >= 8) {
            "Recovery key must contain at least 8 characters"
        }

        val salt = createSalt()
        val hash = hashSecret(key, salt)

        prefs.edit()
            .putString("recovery_salt", salt)
            .putString("recovery_hash", hash)
            .apply()
    }

    fun verifyPersonalRecoveryKey(candidate: String): Boolean {
        return verifyHash(
            candidate.trim(),
            "recovery_salt",
            "recovery_hash"
        )
    }

    // Biometric settings and failed-attempt count

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean("biometric_enabled", false)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean("biometric_enabled", enabled)
            .apply()
    }

    fun getBiometricFailures(): Int {
        return prefs.getInt("bio_fail_count", 0)
    }

    fun recordBiometricFailure(): Int {
        val count = getBiometricFailures() + 1
        prefs.edit().putInt("bio_fail_count", count).apply()
        return count
    }

    fun isBiometricLockedOut(): Boolean {
        return getBiometricFailures() >= 3
    }

    fun resetBiometricFailures() {
        prefs.edit().putInt("bio_fail_count", 0).apply()
    }

    // Intruder capture settings

    fun isIntruderSelfieEnabled(): Boolean {
        return prefs.getBoolean("intruder_selfie_enabled", false)
    }

    fun setIntruderSelfieEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean("intruder_selfie_enabled", enabled)
            .apply()
    }

    fun isDualCameraCaptureEnabled(): Boolean {
        return prefs.getBoolean("dual_camera_capture", false)
    }

    fun setDualCameraCaptureEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean("dual_camera_capture", enabled)
            .apply()
    }

    fun getWrongPinAttempts(): Int {
        return prefs.getInt("wrong_pin_attempts", 0)
    }

    fun recordWrongPinAttempt(): Int {
        val count = getWrongPinAttempts() + 1
        prefs.edit()
            .putInt("wrong_pin_attempts", count)
            .apply()
        return count
    }

    fun shouldCaptureIntruder(): Boolean {
        return getWrongPinAttempts() >= 4 &&
            !prefs.getBoolean("intruder_capture_done", false) &&
            (isIntruderSelfieEnabled() || isDualCameraCaptureEnabled())
    }

    fun markIntruderCaptureDone() {
        prefs.edit()
            .putBoolean("intruder_capture_done", true)
            .apply()
    }

    fun resetWrongPinAttempts() {
        prefs.edit()
            .putInt("wrong_pin_attempts", 0)
            .putBoolean("intruder_capture_done", false)
            .apply()
    }

    // Main protection switch

    fun isMasterProtectionActive(): Boolean {
        return prefs.getBoolean("master_protection", false)
    }

    fun setMasterProtectionActive(active: Boolean) {
        prefs.edit()
            .putBoolean("master_protection", active)
            .apply()
    }

    // Relock preference

    fun setRelockInterval(value: String) {
        val allowed = setOf("immediate", "delay_1m", "delay_5m")
        require(value in allowed)

        prefs.edit()
            .putString("relock_interval", value)
            .apply()
    }

    fun getRelockInterval(): String {
        return prefs.getString("relock_interval", "immediate")
            ?: "immediate"
    }

    fun getRelockDelayMillis(): Long {
        return when (getRelockInterval()) {
            "delay_1m" -> 60_000L
            "delay_5m" -> 300_000L
            else -> 0L
        }
    }
}
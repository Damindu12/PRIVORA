package com.privora.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.privora.app.data.LockedAppsRepository
import com.privora.app.security.SecurityRepository

class LockScreenActivity : FragmentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "locked_package_name"
    }

    private lateinit var security: SecurityRepository
    private lateinit var lockedRepository: LockedAppsRepository

    private var targetPackage: String = ""

    private var biometricLockedOut by mutableStateOf(false)
    private var activeBiometricPrompt: BiometricPrompt? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""

        if (targetPackage.isBlank()) {
            finish()
            return
        }

        security = SecurityRepository.getInstance(this)
        lockedRepository = LockedAppsRepository(this)

        biometricLockedOut = security.isBiometricLockedOut()

        setContent {
            LockScreenContent()
        }
    }

    private fun unlock() {
        lockedRepository.markUnlocked(targetPackage)
        security.resetWrongPinAttempts()
        security.resetBiometricFailures()
        finish()
    }

    private fun startIntruderCaptureIfNeeded() {
        if (!security.shouldCaptureIntruder()) return

        security.markIntruderCaptureDone()

        try {
            startActivity(
                Intent(this, IntruderCaptureActivity::class.java).apply {
                    putExtra(EXTRA_PACKAGE_NAME, targetPackage)
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showBiometricPrompt() {
        if (security.isBiometricLockedOut()) {
            biometricLockedOut = true
            return
        }

        val manager = BiometricManager.from(this)

        if (
            manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            return
        }

        val executor = ContextCompat.getMainExecutor(this)

        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    activeBiometricPrompt = null
                    unlock()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()

                    val failures = security.recordBiometricFailure()

                    if (failures >= 3) {
                        biometricLockedOut = true
                        activeBiometricPrompt?.cancelAuthentication()
                        activeBiometricPrompt = null

                        android.widget.Toast.makeText(
                            this@LockScreenActivity,
                            "Biometric locked. Please use your PIN.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    activeBiometricPrompt = null
                }
            }
        )

        activeBiometricPrompt = prompt

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock PRIVORA")
            .setSubtitle("Verify your identity")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()

        prompt.authenticate(promptInfo)
    }

    @Composable
    private fun LockScreenContent() {
        var pin by remember { mutableStateOf("") }
        var recoveryKey by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        var showRecovery by remember { mutableStateOf(false) }

        val biometricEnabled = security.isBiometricEnabled()

        val biometricAvailable = BiometricManager.from(this)
            .canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) == BiometricManager.BIOMETRIC_SUCCESS

        BackHandler(enabled = true) {}

        MaterialTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF090D16)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "PRIVORA",
                        color = Color(0xFFB69CFF),
                        style = MaterialTheme.typography.headlineLarge
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "App Locked",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (!showRecovery) {
                        OutlinedTextField(
                            value = pin,
                            onValueChange = {
                                if (it.length <= 32) {
                                    pin = it
                                    error = ""
                                }
                            },
                            label = { Text("PIN / Password") },
                            singleLine = true,
                            visualTransformation =
                                PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (security.verifyCredential(pin)) {
                                    unlock()
                                } else {
                                    val attempts =
                                        security.recordWrongPinAttempt()

                                    pin = ""
                                    error = "Incorrect PIN. Attempt $attempts."

                                    startIntruderCaptureIfNeeded()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = pin.isNotEmpty()
                        ) {
                            Text("UNLOCK")
                        }

                        if (biometricEnabled && biometricAvailable) {
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = { showBiometricPrompt() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !biometricLockedOut
                            ) {
                                Text(
                                    if (biometricLockedOut) {
                                        "Biometric locked - use PIN"
                                    } else {
                                        "UNLOCK WITH BIOMETRICS"
                                    }
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                showRecovery = true
                                error = ""
                            }
                        ) {
                            Text("Use Recovery Key")
                        }
                    } else {
                        Text(
                            text = "Enter your Personal Recovery Key",
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = recoveryKey,
                            onValueChange = {
                                recoveryKey = it
                                error = ""
                            },
                            label = { Text("Recovery Key") },
                            singleLine = true,
                            visualTransformation =
                                PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (
                                    security.verifyPersonalRecoveryKey(
                                        recoveryKey
                                    )
                                ) {
                                    unlock()
                                } else {
                                    error = "Incorrect Recovery Key"
                                    recoveryKey = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = recoveryKey.isNotBlank()
                        ) {
                            Text("RECOVER ACCESS")
                        }

                        TextButton(
                            onClick = {
                                showRecovery = false
                                recoveryKey = ""
                                error = ""
                            }
                        ) {
                            Text("Back to PIN")
                        }
                    }

                    if (error.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = error,
                            color = Color(0xFFFF7777)
                        )
                    }
                }
            }
        }
    }
}
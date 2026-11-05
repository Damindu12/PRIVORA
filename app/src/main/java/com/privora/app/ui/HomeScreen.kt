package com.privora.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.privora.app.data.AppInfo
import com.privora.app.data.LockedAppsRepository
import com.privora.app.security.SecurityRepository

@Composable
fun HomeScreen(
    packageManager: PackageManager,
    security: SecurityRepository,
    lockedRepository: LockedAppsRepository,
    onProtectionToggle: (Boolean) -> Boolean,
    onUsageAccess: () -> Unit
) {
    val context = LocalContext.current

    var pin by remember { mutableStateOf("") }
    var recoveryKey by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    var configured by remember {
        mutableStateOf(security.isConfigured())
    }

    var apps by remember {
        mutableStateOf<List<AppInfo>>(emptyList())
    }

    var lockedApps by remember {
        mutableStateOf(lockedRepository.getLockedApps())
    }

    var protectionActive by remember {
        mutableStateOf(security.isMasterProtectionActive())
    }

    var biometricEnabled by remember {
        mutableStateOf(security.isBiometricEnabled())
    }

    var selfieEnabled by remember {
        mutableStateOf(security.isIntruderSelfieEnabled())
    }

    var dualCameraEnabled by remember {
        mutableStateOf(security.isDualCameraCaptureEnabled())
    }

    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val biometricAvailable = BiometricManager.from(context)
        .canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            cameraPermissionGranted = granted
            message = if (granted) {
                "Camera permission granted."
            } else {
                "Camera permission denied."
            }
        }

    LaunchedEffect(Unit) {
        apps = packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != context.packageName }
            .map {
                AppInfo(
                    appName = packageManager
                        .getApplicationLabel(it).toString(),
                    packageName = it.packageName,
                    isSystemApp = (it.flags and
                        android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedBy { it.appName.lowercase() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(
            text = "PRIVORA",
            color = Color(0xFFB69CFF),
            fontSize = 30.sp
        )

        Text(
            text = "Your Privacy. Our Priority.",
            color = Color.LightGray
        )

        Spacer(Modifier.height(14.dp))

        if (!configured) {
            Text(
                "Create your security PIN",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= 6 && it.all(Char::isDigit)) {
                        pin = it
                        message = ""
                    }
                },
                label = { Text("Enter PIN (4–6 digits)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (pin.length in 4..6) {
                        security.saveCredential(
                            type = "pin",
                            credential = pin,
                            pinLength = pin.length
                        )
                        configured = true
                        pin = ""
                        message = "PIN saved successfully."
                    } else {
                        message = "PIN must contain 4–6 digits."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SAVE PIN")
            }
        } else {
            Text(
                "Security PIN is configured",
                color = Color(0xFF8BE0A0)
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            "Recovery Key",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = recoveryKey,
            onValueChange = {
                recoveryKey = it
                message = ""
            },
            label = { Text("Create a recovery key (8+ characters)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                try {
                    security.savePersonalRecoveryKey(recoveryKey)
                    recoveryKey = ""
                    message = "Recovery Key saved. Keep it somewhere safe."
                } catch (e: IllegalArgumentException) {
                    message = "Recovery Key must have at least 8 characters."
                }
            },
            enabled = recoveryKey.trim().length >= 8,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (security.hasRecoveryKey()) {
                    "UPDATE RECOVERY KEY"
                } else {
                    "SAVE RECOVERY KEY"
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        HorizontalDivider(color = Color.DarkGray)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Biometric Unlock", color = Color.White)
                Text(
                    if (biometricAvailable) {
                        "Use fingerprint or supported biometrics"
                    } else {
                        "No enrolled biometric is available"
                    },
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Switch(
                checked = biometricEnabled,
                onCheckedChange = {
                    biometricEnabled = it
                    security.setBiometricEnabled(it)
                },
                enabled = biometricAvailable
            )
        }

        HorizontalDivider(color = Color.DarkGray)

        Text(
            "Intruder Capture",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            "After four incorrect PIN attempts, PRIVORA can " +
                "capture photos while its visible capture screen is open.",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodySmall
        )

        if (!cameraPermissionGranted) {
            Button(
                onClick = {
                    cameraPermissionLauncher.launch(
                        Manifest.permission.CAMERA
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("GRANT CAMERA PERMISSION")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Front camera selfie", color = Color.White)

            Switch(
                checked = selfieEnabled,
                onCheckedChange = {
                    selfieEnabled = it
                    security.setIntruderSelfieEnabled(it)
                },
                enabled = cameraPermissionGranted
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Rear camera photo", color = Color.White)

            Switch(
                checked = dualCameraEnabled,
                onCheckedChange = {
                    dualCameraEnabled = it
                    security.setDualCameraCaptureEnabled(it)
                },
                enabled = cameraPermissionGranted
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onUsageAccess,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("OPEN ACCESSIBILITY SETTINGS")
        }

        Button(
            onClick = {
                val newState = !protectionActive
                val accepted = onProtectionToggle(newState)

                if (accepted) {
                    protectionActive = newState
                    message = if (newState) {
                        "PRIVORA Protection is ON."
                    } else {
                        "PRIVORA Protection is OFF."
                    }
                } else {
                    message = "Enable PRIVORA Accessibility first."
                }
            },
            enabled = configured,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (protectionActive) {
                    "STOP PROTECTION"
                } else {
                    "START PROTECTION"
                }
            )
        }

        if (message.isNotEmpty()) {
            Text(
                message,
                color = Color(0xFFFFB86B),
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Choose apps to lock",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            "Select the apps that PRIVORA should protect.",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodySmall
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(apps, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.appName, color = Color.White)
                        Text(
                            app.packageName,
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Checkbox(
                        checked = lockedApps.contains(app.packageName),
                        onCheckedChange = { checked ->
                            val updated = if (checked) {
                                lockedApps + app.packageName
                            } else {
                                lockedApps - app.packageName
                            }

                            lockedApps = updated
                            lockedRepository.setLockedApps(updated)

                            if (!checked) {
                                lockedRepository.clearUnlocked(app.packageName)
                            }
                        }
                    )
                }

                HorizontalDivider(color = Color.DarkGray)
            }
        }
    }
}
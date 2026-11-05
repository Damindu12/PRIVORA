package com.privora.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.privora.app.security.SecurityRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IntruderCaptureActivity : ComponentActivity() {

    private lateinit var security: SecurityRepository

    private var statusText by mutableStateOf("Preparing camera...")
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        security = SecurityRepository.getInstance(this)

        setContent {
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
                            "PRIVORA SECURITY",
                            color = Color(0xFFB69CFF),
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Spacer(Modifier.height(18.dp))

                        Text(
                            "Security capture is active",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "PRIVORA is using the camera after repeated " +
                                "incorrect PIN attempts. Camera use is " +
                                "visible to the device user.",
                            color = Color.LightGray
                        )

                        Spacer(Modifier.height(18.dp))

                        Text(
                            statusText,
                            color = Color(0xFF8BE0A0)
                        )

                        Spacer(Modifier.height(18.dp))

                        OutlinedButton(onClick = { finish() }) {
                            Text("CLOSE")
                        }
                    }
                }
            }
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            statusText = "Camera permission is missing. No photo was taken."
        } else if (!started) {
            started = true
            startCapture()
        }
    }

    private fun startCapture() {
        val selfieEnabled = security.isIntruderSelfieEnabled()
        val rearEnabled = security.isDualCameraCaptureEnabled()

        if (!selfieEnabled && !rearEnabled) {
            completeCapture("Intruder capture is disabled.")
            return
        }

        if (selfieEnabled) {
            captureFront()
        } else {
            captureBack()
        }
    }

    private fun captureFront() {
        statusText = "Capturing front camera photo..."

        bindCamera(CameraSelector.LENS_FACING_FRONT) {
            takePhoto(
                cameraName = "front",
                onSaved = {
                    if (security.isDualCameraCaptureEnabled()) {
                        captureBack()
                    } else {
                        completeCapture("Front camera photo saved.")
                    }
                },
                onFailed = { message ->
                    if (security.isDualCameraCaptureEnabled()) {
                        statusText = "$message Trying rear camera..."
                        captureBack()
                    } else {
                        completeCapture(message)
                    }
                }
            )
        }
    }

    private fun captureBack() {
        statusText = "Capturing rear camera photo..."

        bindCamera(CameraSelector.LENS_FACING_BACK) {
            takePhoto(
                cameraName = "back",
                onSaved = {
                    completeCapture("Rear camera photo saved.")
                },
                onFailed = { message ->
                    completeCapture(message)
                }
            )
        }
    }

    private fun bindCamera(
        lensFacing: Int,
        onReady: () -> Unit
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                if (!provider.hasCamera(selector)) {
                    val message = if (
                        lensFacing == CameraSelector.LENS_FACING_FRONT
                    ) {
                        "Front camera is unavailable."
                    } else {
                        "Rear camera is unavailable."
                    }

                    if (
                        lensFacing == CameraSelector.LENS_FACING_FRONT &&
                        security.isDualCameraCaptureEnabled()
                    ) {
                        statusText = "$message Trying rear camera..."
                        captureBack()
                    } else {
                        completeCapture(message)
                    }

                    return@addListener
                }

                provider.unbindAll()

                val capture = ImageCapture.Builder()
                    .setCaptureMode(
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    )
                    .build()

                imageCapture = capture

                provider.bindToLifecycle(
                    this,
                    selector,
                    capture
                )

                onReady()

            } catch (e: Exception) {
                val message = "Camera error: ${e.message}"

                if (
                    lensFacing == CameraSelector.LENS_FACING_FRONT &&
                    security.isDualCameraCaptureEnabled()
                ) {
                    statusText = "$message Trying rear camera..."
                    captureBack()
                } else {
                    completeCapture(message)
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto(
        cameraName: String,
        onSaved: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        val capture = imageCapture

        if (capture == null) {
            onFailed("$cameraName camera is not ready.")
            return
        }

        val directory = File(filesDir, "intruder_photos")

        if (!directory.exists() && !directory.mkdirs()) {
            onFailed("Could not create private photo folder.")
            return
        }

        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss_SSS",
            Locale.US
        ).format(Date())

        val photoFile = File(
            directory,
            "${cameraName}_$timestamp.jpg"
        )

        val outputOptions = OutputFileOptions.Builder(
            photoFile
        ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    output: ImageCapture.OutputFileResults
                ) {
                    statusText = "$cameraName photo saved."
                    onSaved()
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    onFailed(
                        "$cameraName capture failed: ${exception.message}"
                    )
                }
            }
        )
    }

    private fun completeCapture(message: String) {
        cameraProvider?.unbindAll()
        imageCapture = null
        statusText = message
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        super.onDestroy()
    }
}
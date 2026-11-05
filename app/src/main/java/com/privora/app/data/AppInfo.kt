package com.privora.app.data

data class AppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean = false
)
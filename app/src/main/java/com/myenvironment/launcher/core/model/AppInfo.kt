package com.myenvironment.launcher.core.model

/**
 * 端末にインストールされている起動可能アプリの情報
 */
data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val userSerialNumber: Long = 0L,
    val firstInstallTime: Long = 0L
) {
    val componentKey: String
        get() = "$packageName/$activityName"
}

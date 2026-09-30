package com.myenvironment.discoverprotocol

object DiscoverContract {
    const val LAUNCHER_PACKAGE = "com.myenvironment.launcher"
    const val COMPANION_PACKAGE = "com.myenvironment.chimediscoverbridge"
    const val COMPANION_SERVICE = "$COMPANION_PACKAGE.DiscoverBridgeService"
    const val CONNECT_PERMISSION = "$COMPANION_PACKAGE.CONNECT"
    const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
    const val OVERLAY_ACTION = "com.android.launcher3.WINDOW_OVERLAY"
    const val OVERLAY_DESCRIPTOR = "com.google.android.libraries.launcherclient.ILauncherOverlay"
    const val BRIDGE_DESCRIPTOR = "com.myenvironment.discoverprotocol.IDiscoverBridge"
}

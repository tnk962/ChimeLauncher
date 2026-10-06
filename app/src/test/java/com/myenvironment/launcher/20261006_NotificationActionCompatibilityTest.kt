package com.myenvironment.launcher

import com.myenvironment.launcher.core.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class NotificationActionCompatibilityTest {
    @Test fun oldSavedNotificationActionKeepsIdentityAndOtherDeviceEndpoint() {
        val saved = """{"id":"old-notifications","pageId":"home","type":"ACTION","packageName":"com.myenvironment.notifications","targetUri":"launcher://action/my_notifications","label":"My Notifications (通知履歴)","compact":{"x":1,"y":2}}"""
        val item = Json.decodeFromString<LayoutItem>(saved)
        val action = LauncherAction.fromActionId(item.targetUri)!!
        assertEquals(LauncherAction.MY_NOTIFICATIONS, action)
        assertEquals(action, LauncherAction.fromActionId("MY_NOTIFICATIONS"))
        assertEquals("com.example.goodpixel",action.companionPackageName)
        assertEquals("com.example.goodpixel.ui.notilog.NotiLogActivity",action.companionActivityName)
        assertEquals("old-notifications",item.id)
        assertEquals(GridPosition(1,2),item.compact)
        assertEquals("com.myenvironment.notifications",item.packageName)
    }
}

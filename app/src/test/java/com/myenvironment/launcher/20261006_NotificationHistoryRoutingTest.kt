package com.myenvironment.launcher

import com.myenvironment.launcher.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class NotificationHistoryRoutingTest {
    @Test fun galaxyPrioritizesNotistarAndFallsBackOnlyToSystem() {
        assertEquals(listOf(NotificationHistoryDestination.NOTISTAR,NotificationHistoryDestination.SYSTEM), notificationHistoryDestinations("Samsung",GalaxyNotificationHistoryTarget.NOTISTAR))
    }
    @Test fun galaxySystemSelectionNeverLaunchesNotistarOrGoodPixel() {
        assertEquals(listOf(NotificationHistoryDestination.SYSTEM), notificationHistoryDestinations("samsung",GalaxyNotificationHistoryTarget.SYSTEM))
    }
    @Test fun pixelUsesGoodPixelThenSystem() {
        assertEquals(listOf(NotificationHistoryDestination.GOODPIXEL,NotificationHistoryDestination.SYSTEM), notificationHistoryDestinations("Google",GalaxyNotificationHistoryTarget.NOTISTAR))
    }
    @Test fun transferredGalaxyPreferenceDoesNotChangeOtherDeviceRouting() {
        assertEquals(notificationHistoryDestinations("Google",GalaxyNotificationHistoryTarget.NOTISTAR), notificationHistoryDestinations("Google",GalaxyNotificationHistoryTarget.SYSTEM))
    }
    @Test fun oldSettingsDefaultToNotistarPreference() {
        assertEquals(GalaxyNotificationHistoryTarget.NOTISTAR,Json.decodeFromString<LauncherSettings>("{}").galaxyNotificationHistoryTarget)
    }
    @Test fun newPreferenceSurvivesSettingsJsonBackupRoundTrip() {
        val settings=LauncherSettings(galaxyNotificationHistoryTarget=GalaxyNotificationHistoryTarget.SYSTEM)
        assertEquals(settings,Json.decodeFromString<LauncherSettings>(Json.encodeToString(settings)))
    }
}

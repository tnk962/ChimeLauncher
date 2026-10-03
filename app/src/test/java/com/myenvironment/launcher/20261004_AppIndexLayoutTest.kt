package com.myenvironment.launcher

import com.myenvironment.launcher.core.model.LauncherSettings
import com.myenvironment.launcher.core.search.AppIndexLayout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AppIndexLayoutTest {
    @Test fun initialKeyboardLayoutStartsNearTheTop() {
        assertEquals(AppIndexLayout.Geometry(8f, 384f), AppIndexLayout.initialGeometry(400f))
    }
    @Test fun noKeyboardLayoutRetainsTheEstablishedCenter() {
        assertEquals(AppIndexLayout.Geometry(120f, 480f), AppIndexLayout.initialGeometry(714f))
    }
    @Test fun shortWindowsKeepTheEntireInitialRailInsideAvailableHeight() {
        for (available in listOf(400f, 180f, 40f)) {
            val geometry = AppIndexLayout.initialGeometry(available)
            assertTrue(geometry.topDp >= 0f)
            assertTrue(geometry.topDp + geometry.heightDp <= available)
        }
    }
    @Test fun hitTestingUsesActualRoundedSectionStarts() {
        val starts = floatArrayOf(0f, 15f, 30f, 46f)
        assertEquals(0, AppIndexLayout.labelIndex(-5f, starts))
        assertEquals(1, AppIndexLayout.labelIndex(29f, starts))
        assertEquals(2, AppIndexLayout.labelIndex(30f, starts))
        assertEquals(3, AppIndexLayout.labelIndex(100f, starts))
    }
    @Test fun oldSettingsGetDefaultAndNewBackupPreservesDistance() {
        assertEquals(32, Json.decodeFromString<LauncherSettings>("{}").searchIndexEdgeDistanceDp)
        val settings = LauncherSettings(searchIndexEdgeDistanceDp = 80)
        assertEquals(settings, Json.decodeFromString<LauncherSettings>(Json.encodeToString(settings)))
    }
}

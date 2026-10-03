package com.myenvironment.launcher

import com.myenvironment.launcher.core.model.LauncherSettings
import com.myenvironment.launcher.core.search.AppIndexLayout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AppIndexLayoutTest {
    @Test fun followsSystemGestureWidthAndHonorsLargerManualDistance() {
        assertEquals(32f, AppIndexLayout.edgeDistanceDp(32, 0f, 448f), 0f)
        assertEquals(64f, AppIndexLayout.edgeDistanceDp(32, 56f, 448f), 0f)
        assertEquals(80f, AppIndexLayout.edgeDistanceDp(80, 56f, 448f), 0f)
    }
    @Test fun boundsImportedValuesAndPreservesListSpaceInNarrowWindows() {
        assertEquals(32f, AppIndexLayout.edgeDistanceDp(-200, 0f, 448f), 0f)
        assertEquals(128f, AppIndexLayout.edgeDistanceDp(999, 0f, 448f), 0f)
        assertEquals(48f, AppIndexLayout.edgeDistanceDp(128, 100f, 220f), 0f)
    }
    @Test fun railIsOneAndAHalfTimesTallAtTheSameCenter() {
        val height = AppIndexLayout.heightDp(714f)
        assertEquals(480f, height, 0f)
        assertEquals(120f, AppIndexLayout.topDp(714f, height), 0f)
    }
    @Test fun keyboardAndShortWindowsKeepEntireRailInsideAvailableHeight() {
        for (available in listOf(400f, 180f, 40f)) {
            val height = AppIndexLayout.heightDp(available)
            val top = AppIndexLayout.topDp(available, height)
            assertTrue(top >= 0f)
            assertTrue(top + height <= available)
        }
    }
    @Test fun oldSettingsGetDefaultAndNewBackupPreservesDistance() {
        assertEquals(32, Json.decodeFromString<LauncherSettings>("{}").searchIndexEdgeDistanceDp)
        val settings = LauncherSettings(searchIndexEdgeDistanceDp = 80)
        assertEquals(settings, Json.decodeFromString<LauncherSettings>(Json.encodeToString(settings)))
    }
}

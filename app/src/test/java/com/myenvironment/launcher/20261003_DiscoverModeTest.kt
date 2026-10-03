package com.myenvironment.launcher

import com.myenvironment.launcher.core.feed.overlay.OverlayDragSession
import com.myenvironment.launcher.core.model.DiscoverMode
import com.myenvironment.launcher.core.model.LauncherSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DiscoverModeTest {
    @Test fun modesHaveDistinctBehaviors() {
        assertEquals(5, DiscoverMode.entries.size)
        assertEquals(setOf(DiscoverMode.NATIVE_BRIDGE, DiscoverMode.GOOGLE_ONLY),
            DiscoverMode.entries.filter { it.usesGoogleOverlay }.toSet())
        assertFalse(DiscoverMode.GOOGLE_ONLY.showsCustomFeed)
        assertTrue(DiscoverMode.FEED_ONLY.showsCustomFeed)
        assertEquals(DiscoverMode.FEED_ONLY, DiscoverMode.GOOGLE_APP.normalized)
        assertFalse(DiscoverMode.DISABLED.showsCustomFeed)
    }
    @Test fun savedModesAndBackupsRemainCompatible() {
        listOf("NATIVE_BRIDGE", "GOOGLE_APP", "DISABLED").forEach { saved ->
            val settings = Json.decodeFromString<LauncherSettings>("{\"discoverMode\":\"$saved\"}")
            assertEquals(DiscoverMode.valueOf(saved), settings.discoverMode)
        }
        DiscoverMode.entries.forEach { mode ->
            val settings = LauncherSettings(discoverMode = mode)
            assertEquals(settings, Json.decodeFromString<LauncherSettings>(Json.encodeToString(settings)))
        }
        assertEquals(DiscoverMode.NATIVE_BRIDGE, LauncherSettings().discoverMode)
    }
    @Test fun googleOnlyHasNoIntermediateFeedPage() {
        assertEquals(setOf(DiscoverMode.NATIVE_BRIDGE, DiscoverMode.FEED_ONLY, DiscoverMode.GOOGLE_APP),
            DiscoverMode.entries.filter { it.showsCustomFeed }.toSet())
    }
    @Test fun allAppsVerticalScrollAndHomeDirectionDoNotRevealGoogle() {
        val vertical = OverlayDragSession(8f, 400f)
        assertNull(vertical.move(0f, 30f) { error("Vertical scroll must stay in All Apps") })
        assertNull(vertical.move(200f, 0f) { error("Direction must remain locked") })
        val home = OverlayDragSession(8f, 400f)
        assertNull(home.move(-30f, 0f) { error("Home swipe must stay with pager") })
        val google = OverlayDragSession(8f, 400f)
        assertEquals(0.5f, google.move(200f, 0f) { true }!!, 0.001f)
    }

    @Test fun independentTogglesCoverEveryCombinationAndPreserveOtherToggle() {
        for (google in listOf(false, true)) for (feed in listOf(false, true)) {
            val mode = DiscoverMode.fromVisibility(google, feed)
            assertEquals(google, mode.usesGoogleOverlay)
            assertEquals(feed, mode.showsCustomFeed)
            assertEquals(feed, DiscoverMode.fromVisibility(!google, feed).showsCustomFeed)
            assertEquals(google, DiscoverMode.fromVisibility(google, !feed).usesGoogleOverlay)
        }
    }
    @Test fun legacyGoogleAppBackupMigratesWithoutAutoLaunching() {
        val settings = Json.decodeFromString<LauncherSettings>("{\"discoverMode\":\"GOOGLE_APP\"}")
        assertEquals(DiscoverMode.FEED_ONLY, settings.discoverMode.normalized)
        assertFalse(settings.discoverMode.normalized.usesGoogleOverlay)
        assertTrue(settings.discoverMode.normalized.showsCustomFeed)
    }

}

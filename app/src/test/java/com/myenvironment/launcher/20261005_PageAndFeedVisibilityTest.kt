package com.myenvironment.launcher

import com.myenvironment.launcher.core.feed.FeedCategory
import com.myenvironment.launcher.core.model.*
import com.myenvironment.launcher.ui.ExpandedPagerSlot
import com.myenvironment.launcher.ui.buildExpandedDualSlots
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PageAndFeedVisibilityTest {
    @Test fun oldBackupsKeepAllAppsAndEveryFeedEnabled() {
        for (json in listOf("{}", "{\"discoverMode\":\"GOOGLE_ONLY\"}")) {
            val settings = Json.decodeFromString<LauncherSettings>(json)
            assertTrue(settings.allAppsPageEnabled)
            assertEquals(FeedCategory.entries.toList(), settings.enabledFeedCategories)
        }
    }

    @Test fun pageTogglesCoverAllEightCombinationsAndKeepUserPages() {
        val userPages = listOf(LauncherPage("second", "2", 2), LauncherPage("first", "1", 1))
        for (apps in listOf(false, true)) for (google in listOf(false, true)) for (feed in listOf(false, true)) {
            val settings = LauncherSettings(allAppsPageEnabled = apps,
                discoverMode = DiscoverMode.fromVisibility(google, feed))
            val pages = settings.visibleLauncherPages(userPages)
            val expected = buildList {
                if (feed) add(LauncherPage.PAGE_ID_DISCOVER)
                if (apps) add(LauncherPage.PAGE_ID_ALL_APPS)
                addAll(listOf("home", "first", "second", "settings"))
            }
            assertEquals(expected, pages.map { it.id })
            assertEquals(if (feed) "discover" else if (apps) "all_apps" else "home", pages.first().id)
            assertEquals(userPages.sortedBy { it.sortOrder }, pages.filter { !it.isFixed })
        }
    }

    @Test fun hiddenAllAppsNeverOccupiesAnExpandedPagerSlot() {
        val settings = LauncherSettings(allAppsPageEnabled = false, discoverMode = DiscoverMode.GOOGLE_ONLY)
        val userPage = LauncherPage("extra", "extra", 1)
        val slots = buildExpandedDualSlots(settings.visibleLauncherPages(listOf(userPage)))
        val spread = slots.first() as ExpandedPagerSlot.DualSpread
        assertEquals(setOf("home", "extra"), spread.visiblePageIds)
        assertEquals("settings", slots.last().primaryPage.id)
        assertFalse(slots.any { "all_apps" in it.visiblePageIds })
    }

    @Test fun disablingSelectedFeedFallsBackToFirstEnabledFeed() {
        val settings = LauncherSettings(disabledFeedCategoryIds = setOf(FeedCategory.DISCOVER_CURATED.id))
        assertEquals(FeedCategory.AI_OPENAI, settings.resolveFeedCategory(FeedCategory.DISCOVER_CURATED))
        assertEquals(FeedCategory.HATENA_TECH, settings.resolveFeedCategory(FeedCategory.HATENA_TECH))
        assertFalse(settings.enabledFeedCategories.contains(FeedCategory.DISCOVER_CURATED))
    }

    @Test fun allFeedsOffHasNoSelectionAndReenabledFeedBecomesSelectable() {
        val allOff = LauncherSettings(disabledFeedCategoryIds = FeedCategory.entries.map { it.id }.toSet())
        assertTrue(allOff.enabledFeedCategories.isEmpty())
        assertNull(allOff.resolveFeedCategory(FeedCategory.DISCOVER_CURATED))
        val enabled = allOff.copy(disabledFeedCategoryIds = allOff.disabledFeedCategoryIds - FeedCategory.BUSINESS_POLITICS.id)
        assertEquals(listOf(FeedCategory.BUSINESS_POLITICS), enabled.enabledFeedCategories)
        assertEquals(FeedCategory.BUSINESS_POLITICS, enabled.resolveFeedCategory(FeedCategory.DISCOVER_CURATED))
    }

    @Test fun visibilityPreferencesRoundTripThroughBackupWithoutLosingUnknownIds() {
        val settings = LauncherSettings(allAppsPageEnabled = false, discoverMode = DiscoverMode.FEED_ONLY,
            disabledFeedCategoryIds = setOf(FeedCategory.AI_OPENAI.id, "future_feed"))
        val restored = Json.decodeFromString<LauncherSettings>(Json.encodeToString(settings))
        assertEquals(settings, restored)
        assertEquals(5, restored.enabledFeedCategories.size)
        assertFalse(restored.allAppsPageEnabled)
        assertEquals(DiscoverMode.FEED_ONLY, restored.discoverMode)
    }
}

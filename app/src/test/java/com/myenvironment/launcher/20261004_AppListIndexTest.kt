package com.myenvironment.launcher

import com.myenvironment.launcher.core.model.AppInfo
import com.myenvironment.launcher.core.search.AppListIndex
import org.junit.Assert.*
import org.junit.Test

class AppListIndexTest {
    private fun app(label: String, activity: String = label) =
        AppInfo("test.package", activity, label)

    @Test fun normalizesFullWidthLatinAccentsAndKatakana() {
        assertEquals("A", AppListIndex.section("Ａpp"))
        assertEquals("E", AppListIndex.section("Éclair"))
        assertEquals("か", AppListIndex.section("ｶﾞﾒﾗ"))
        assertEquals("は", AppListIndex.section("ぱずる"))
        assertEquals("あ", AppListIndex.section("ヴォイス"))
        assertEquals("や", AppListIndex.section("ャプリ"))
    }

    @Test fun neverInventsReadingsForKanjiOrNumbers() {
        assertEquals("#", AppListIndex.section("時計"))
        assertEquals("#", AppListIndex.section("123"))
        assertEquals("#", AppListIndex.section(""))
    }

    @Test fun listGroupsMatchRailOrderWithOtherAtEnd() {
        val sorted = AppListIndex.sorted(listOf(app("時計"), app("カレンダー"),
            app("Zebra"), app("Apple"), app("あぷり"), app("Beta")))
        assertEquals(listOf("A", "B", "Z", "あ", "か", "#"),
            sorted.map { AppListIndex.section(it.label) })
    }

    @Test fun jumpOffsetsAccountForSuggestionsAndSectionHeadings() {
        val apps = AppListIndex.sorted(listOf(app("Beta"), app("Apple"), app("Alpha"), app("かな")))
        assertEquals(mapOf("A" to 4, "B" to 7, "か" to 9), AppListIndex.positions(apps, 4))
        assertEquals(mapOf("A" to 1, "B" to 4, "か" to 6), AppListIndex.positions(apps, 1))
        assertFalse(AppListIndex.positions(apps, 1).containsKey("C"))
    }

    @Test fun keepsDistinctActivitiesAndProfilesButDropsRepeatedEntries() {
        val a = app("Alpha", "first")
        val b = app("Alpha", "second")
        val work = a.copy(userSerialNumber = 10)
        assertEquals(3, AppListIndex.sorted(listOf(a, a, b, work)).size)
        assertTrue(AppListIndex.positions(emptyList(), 1).isEmpty())
    }
}

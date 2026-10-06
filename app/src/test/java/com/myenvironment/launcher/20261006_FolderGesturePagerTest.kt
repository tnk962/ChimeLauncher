package com.myenvironment.launcher

import androidx.compose.ui.geometry.Offset
import com.myenvironment.launcher.core.model.*
import com.myenvironment.launcher.ui.*
import com.myenvironment.launcher.ui.home.isTouchInsideScrollableWidget
import org.junit.Assert.*
import org.junit.Test

class FolderGesturePagerTest {
    private fun ignored(folder: LayoutItem, point: Offset, expanded: Boolean = false) =
        isTouchInsideScrollableWidget(point, listOf(folder), expanded, 5, 6, 100f, 100f) { false }
    private val folder = LayoutItem("f", "home", ItemType.FOLDER, "", label = "Folder",
        compact = GridPosition(1,1), expanded = GridPosition(3,2), spanX = 1, spanY = 2)
    @Test fun expandedFolderOwnsBothSwipeDirectionsAndEmptySpace() {
        assertTrue(ignored(folder, Offset(150f, 150f)))
        assertTrue(ignored(folder, Offset(150f, 270f)))
        assertFalse(ignored(folder, Offset(250f, 150f)))
    }
    @Test fun expandedCoordinatesAndSingleCellBehaviorStayCorrect() {
        assertTrue(ignored(folder, Offset(350f, 270f), true))
        assertFalse(ignored(folder, Offset(150f, 150f), true))
        assertFalse(ignored(folder.copy(spanY=1), Offset(150f,150f)))
    }
    @Test fun singlePagerSurvivesListShrinkAndKeepsStableKeys() {
        val pages=listOf(LauncherPage.FIXED_HOME,LauncherPage.FIXED_SETTINGS)
        assertEquals("home",singlePagerKey(pages,0))
        assertEquals("pending-single:3",singlePagerKey(pages,3))
        assertEquals("pending-single:0",singlePagerKey(emptyList(),0))
    }
    @Test fun dualPagerSurvivesStartupSettingsUpdate() {
        val slots=listOf(ExpandedPagerSlot.SingleFull(LauncherPage.FIXED_HOME))
        assertEquals("home",dualPagerKey(slots,0))
        assertEquals("pending-dual:3",dualPagerKey(slots,3))
        assertNotEquals(dualPagerKey(slots,2),dualPagerKey(slots,3))
        assertEquals("pending-dual:0",dualPagerKey(emptyList(),0))
    }
}

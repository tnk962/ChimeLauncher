package com.myenvironment.launcher

import com.myenvironment.launcher.core.model.*
import com.myenvironment.launcher.ui.canPlaceSearchApp
import com.myenvironment.launcher.ui.insertSearchDockItem
import org.junit.Assert.*
import org.junit.Test

class SearchAppDropTest {
    private fun icon(id: String, x: Int = 1, y: Int = 1) = LayoutItem(
        id, "home", ItemType.APP, "package", label = id, compact = GridPosition(x, y))
    private fun dock(id: String, position: Int) = DockItem(id, position, ItemType.APP, "package", label = id)

    @Test fun `empty cell accepts app but occupied icon and widget cells reject it`() {
        val items = listOf(icon("app"), icon("widget", 2, 2).copy(type = ItemType.WIDGET, spanX = 2, spanY = 2))
        assertTrue(canPlaceSearchApp(items, "home", GridPosition(0, 0), false, 4, 4))
        for (cell in listOf(GridPosition(1, 1), GridPosition(2, 2), GridPosition(3, 3))) {
            assertFalse(canPlaceSearchApp(items, "home", cell, false, 4, 4))
        }
    }
    @Test fun `other page items do not block and outside cells reject`() {
        assertTrue(canPlaceSearchApp(listOf(icon("app")), "other", GridPosition(1, 1), false, 4, 4))
        for (cell in listOf(GridPosition(-1, 0), GridPosition(4, 0), GridPosition(0, 4))) {
            assertFalse(canPlaceSearchApp(emptyList(), "home", cell, false, 4, 4))
        }
    }
    @Test fun `expanded placement uses expanded positions and clamped widget spans`() {
        val items = listOf(icon("widget").copy(type = ItemType.WIDGET, expanded = GridPosition(3, 3), spanX = 2, spanY = 2))
        assertTrue(canPlaceSearchApp(items, "home", GridPosition(1, 1), true, 4, 4))
        assertFalse(canPlaceSearchApp(items, "home", GridPosition(2, 2), true, 4, 4))
    }
    @Test fun `dock inserts at beginning middle and end with contiguous order`() {
        val items = listOf(dock("b", 1), dock("a", 0))
        for ((index, expected) in listOf(0 to listOf("new", "a", "b"), 1 to listOf("a", "new", "b"), 2 to listOf("a", "b", "new"))) {
            val result = insertSearchDockItem(items, dock("new", 0), index, 3)!!
            assertEquals(expected, result.map { it.id })
            assertEquals(listOf(0, 1, 2), result.map { it.positionIndex })
        }
        assertEquals(listOf("new"), insertSearchDockItem(emptyList(), dock("new", 0), 5, 1)!!.map { it.id })
    }
    @Test fun `full dock rejects without modifying existing entries`() {
        val items = listOf(dock("a", 0))
        assertNull(insertSearchDockItem(items, dock("new", 0), 0, 1))
        assertEquals(listOf("a"), items.map { it.id })
    }
}

package com.myenvironment.launcher

import androidx.compose.ui.geometry.Offset
import com.myenvironment.launcher.core.model.DockItem
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.ui.components.SecondaryPageSwipe
import com.myenvironment.launcher.ui.reorderDockByInsertion
import org.junit.Assert.*
import org.junit.Test

class MultiPointerDragTest {
    private fun item(id: String, position: Int) = DockItem(id, position, ItemType.APP, "package", label = id)
    private val items = listOf(item("a", 0), item("b", 1), item("c", 2))

    @Test fun `dock drop moves to either end and preserves ids and count`() {
        val last = reorderDockByInsertion(items, "a", 3)
        assertEquals(listOf("b", "c", "a"), last.map { it.id })
        assertEquals(listOf("c", "a", "b"), reorderDockByInsertion(items, "c", 0).map { it.id })
        assertEquals(items.map { it.id }.toSet(), last.map { it.id }.toSet())
        assertEquals(listOf(0, 1, 2), last.map { it.positionIndex })
    }
    @Test fun `drop before and after original slot are no ops and unknown id changes nothing`() {
        assertEquals(items, reorderDockByInsertion(items, "b", 1))
        assertEquals(items, reorderDockByInsertion(items, "b", 2))
        assertEquals(items, reorderDockByInsertion(items, "missing", 0))
    }
    @Test fun `middle insertion adjusts index after removing original entry`() {
        assertEquals(listOf("b", "a", "c"), reorderDockByInsertion(items, "a", 2).map { it.id })
        assertEquals(listOf("a", "c", "b"), reorderDockByInsertion(items, "c", 1).map { it.id })
    }
    @Test fun `primary pointer cannot become page swipe pointer`() {
        val swipe = SecondaryPageSwipe(10, 48f)
        swipe.down(10, Offset(200f, 0f), true)
        assertFalse(swipe.owns(10))
        assertEquals(0, swipe.up(10, Offset.Zero))
    }
    @Test fun `secondary swipes page in both directions and permits another swipe`() {
        val swipe = SecondaryPageSwipe(10, 48f)
        swipe.down(20, Offset(200f, 100f), true)
        assertTrue(swipe.owns(20))
        assertEquals(1, swipe.up(20, Offset(100f, 110f)))
        assertFalse(swipe.owns(20))
        swipe.down(30, Offset(100f, 100f), true)
        assertEquals(-1, swipe.up(30, Offset(200f, 110f)))
    }
    @Test fun `short vertical and outside pager gestures do not navigate`() {
        val swipe = SecondaryPageSwipe(10, 48f)
        swipe.down(20, Offset.Zero, false)
        assertFalse(swipe.owns(20))
        swipe.down(20, Offset.Zero, true)
        assertEquals(0, swipe.up(20, Offset(20f, 0f)))
        swipe.down(20, Offset.Zero, true)
        assertEquals(0, swipe.up(20, Offset(100f, 100f)))
    }
    @Test fun `third finger cannot replace the secondary pointer`() {
        val swipe = SecondaryPageSwipe(10, 48f)
        swipe.down(20, Offset(200f, 100f), true)
        swipe.down(30, Offset.Zero, true)
        assertFalse(swipe.owns(30))
        assertEquals(0, swipe.up(30, Offset(100f, 0f)))
        assertTrue(swipe.owns(20))
        assertEquals(1, swipe.up(20, Offset(100f, 100f)))
    }
}

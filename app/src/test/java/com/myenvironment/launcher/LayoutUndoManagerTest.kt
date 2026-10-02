package com.myenvironment.launcher

import com.myenvironment.launcher.core.model.DockItem
import com.myenvironment.launcher.core.model.GridPosition
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LauncherPage
import com.myenvironment.launcher.core.model.LayoutItem
import com.myenvironment.launcher.core.model.LayoutSnapshot
import com.myenvironment.launcher.core.storage.LayoutUndoManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class LayoutUndoManagerTest {
    private fun app(id: String = "app", pageId: String = "home") = LayoutItem(
        id = id, pageId = pageId, type = ItemType.APP, packageName = id,
        label = id, compact = GridPosition(0, 0), expanded = GridPosition(4, 2)
    )

    private fun widget(id: Int, pageId: String = "home") = app("widget_$id", pageId).copy(
        type = ItemType.WIDGET, appWidgetId = id, spanX = 2, spanY = 3
    )

    private fun dock(id: String, index: Int) = DockItem(
        id = id, positionIndex = index, type = ItemType.APP, packageName = id, label = id
    )

    private class Fixture(initial: LayoutSnapshot = LayoutSnapshot(), limit: Int = 20) {
        var layout = initial
        var failRestore = false
        val released = mutableListOf<Int>()
        val protected = mutableSetOf<Int>()
        val manager = LayoutUndoManager(
            readSnapshot = { layout },
            restoreSnapshot = {
                if (failRestore) error("restore failed")
                layout = it
            },
            releaseWidgetId = { released.add(it) },
            protectedWidgetIds = { protected.toSet() },
            historyLimit = limit
        )
    }

    @Test
    fun `add move resize and delete restore both fold layouts in reverse order`() = runBlocking {
        val f = Fixture()
        f.manager.beginSession()
        val initial = f.layout
        f.manager.edit { f.layout = it.copy(items = listOf(widget(42))) }
        val added = f.layout
        f.manager.edit { f.layout = it.copy(items = it.items.map { item -> item.copy(compact = GridPosition(1, 2), expanded = GridPosition(5, 3)) }) }
        val moved = f.layout
        f.manager.edit { f.layout = it.copy(items = it.items.map { item -> item.copy(spanX = 3, spanY = 2) }) }
        val resized = f.layout
        f.manager.edit { f.layout = it.copy(items = emptyList()) }
        assertTrue(f.released.isEmpty())
        for (expected in listOf(resized, moved, added, initial)) {
            assertEquals(expected.normalized(), f.manager.undo())
            assertEquals(expected.normalized(), f.layout)
        }
        assertEquals(listOf(42), f.released)
        assertEquals(0, f.manager.state.value.count)
        assertNull(f.manager.undo())
    }

    @Test
    fun `swap is one operation and restores both icons`() = runBlocking {
        val first = app("a")
        val second = app("b").copy(compact = GridPosition(1, 0))
        val f = Fixture(LayoutSnapshot(items = listOf(first, second)))
        f.manager.beginSession()
        f.manager.edit {
            f.layout = it.copy(items = listOf(first.copy(compact = second.compact), second.copy(compact = first.compact)))
        }
        assertEquals(1, f.manager.state.value.count)
        assertEquals(listOf(first, second), f.manager.undo()!!.items)
    }

    @Test
    fun `page removal restores its name order items and original widget ID`() = runBlocking {
        val page = LauncherPage("page_1", "仕事", 1)
        val item = widget(73, page.id)
        val initial = LayoutSnapshot(userPages = listOf(page), items = listOf(item, app()))
        val f = Fixture(initial)
        f.manager.beginSession()
        f.manager.edit { f.layout = it.copy(userPages = emptyList(), items = it.items.filter { item -> item.pageId != page.id }) }
        assertTrue(f.released.isEmpty())
        assertEquals(initial.normalized(), f.manager.undo())
        f.manager.endSession()
        assertTrue(f.released.isEmpty())
    }

    @Test
    fun `dock reorder deletion and page rename undo independently`() = runBlocking {
        val page = LauncherPage("page_1", "元の名前", 1)
        val initial = LayoutSnapshot(userPages = listOf(page), dockItems = listOf(dock("a", 0), dock("b", 1)))
        val f = Fixture(initial)
        f.manager.beginSession()
        f.manager.edit { f.layout = it.copy(dockItems = listOf(dock("b", 0), dock("a", 1))) }
        val reordered = f.layout
        f.manager.edit { f.layout = it.copy(dockItems = listOf(dock("a", 0))) }
        val removed = f.layout
        f.manager.edit { f.layout = it.copy(userPages = listOf(page.copy(name = "変更後"))) }
        assertEquals(removed, f.manager.undo())
        assertEquals(reordered, f.manager.undo())
        assertEquals(initial, f.manager.undo())
    }

    @Test
    fun `page creation plus item move undo as one operation`() = runBlocking {
        val initial = LayoutSnapshot(items = listOf(app()))
        val f = Fixture(initial)
        f.manager.beginSession()
        f.manager.edit {
            val page = LauncherPage("page_new", "新ページ", 1)
            f.layout = it.copy(userPages = listOf(page), items = it.items.map { item -> item.copy(pageId = page.id) })
        }
        assertEquals(1, f.manager.state.value.count)
        assertEquals(initial, f.manager.undo())
    }

    @Test
    fun `history has exactly twenty entries and older operations stay committed`() = runBlocking {
        val f = Fixture(LayoutSnapshot(items = listOf(app())))
        f.manager.beginSession()
        for (x in 1..25) {
            f.manager.edit { f.layout = it.copy(items = it.items.map { item -> item.copy(compact = GridPosition(x, 0)) }) }
        }
        assertEquals(20, f.manager.state.value.count)
        repeat(20) { assertNotNull(f.manager.undo()) }
        assertEquals(GridPosition(5, 0), f.layout.items.single().compact)
        assertNull(f.manager.undo())
    }

    @Test
    fun `expired history releases deleted widget but keeps placed widget`() = runBlocking {
        val f = Fixture(LayoutSnapshot(items = listOf(widget(1), widget(2))), limit = 2)
        f.manager.beginSession()
        f.manager.edit { f.layout = it.copy(items = it.items.filter { item -> item.appWidgetId != 1 }) }
        repeat(2) { index ->
            f.manager.edit { f.layout = it.copy(items = it.items.map { item -> item.copy(label = "name_$index") }) }
        }
        assertEquals(listOf(1), f.released)
        f.manager.endSession()
        assertEquals(listOf(1), f.released)
        assertEquals(setOf(2), f.layout.widgetIds)
    }

    @Test
    fun `ending session discards history and frees only removed widgets`() = runBlocking {
        val f = Fixture(LayoutSnapshot(items = listOf(widget(1), widget(2))))
        f.manager.beginSession()
        f.manager.edit { f.layout = it.copy(items = it.items.filter { item -> item.appWidgetId != 1 }) }
        assertTrue(f.released.isEmpty())
        f.manager.endSession()
        assertEquals(listOf(1), f.released)
        assertFalse(f.manager.state.value.isEditing)
        assertEquals(0, f.manager.state.value.count)
        f.manager.beginSession()
        assertNull(f.manager.undo())
        assertEquals(setOf(2), f.layout.widgetIds)
    }

    @Test
    fun `editing outside session has no history and immediately frees removed IDs`() = runBlocking {
        val f = Fixture(LayoutSnapshot(items = listOf(widget(1))))
        f.manager.edit { f.layout = it.copy(items = emptyList()) }
        assertEquals(listOf(1), f.released)
        assertEquals(0, f.manager.state.value.count)
        assertNull(f.manager.undo())
    }

    @Test
    fun `no op repeated begin and reordered query results do not consume history`() = runBlocking {
        val f = Fixture(LayoutSnapshot(items = listOf(app("a"), app("b"))))
        f.manager.beginSession()
        f.manager.edit { f.layout = it.copy(items = it.items.reversed()) }
        assertEquals(0, f.manager.state.value.count)
        f.manager.edit { f.layout = it.copy(items = it.items.dropLast(1)) }
        f.manager.beginSession()
        assertEquals(1, f.manager.state.value.count)
    }

    @Test
    fun `locked or pending predicate prevents undo without losing history`() = runBlocking {
        val f = Fixture()
        f.manager.beginSession()
        f.manager.edit { f.layout = it.copy(items = listOf(app())) }
        assertNull(f.manager.undo { false })
        assertEquals(1, f.manager.state.value.count)
        assertEquals(1, f.layout.items.size)
        assertNotNull(f.manager.undo { true })
    }

    @Test
    fun `failed restore preserves history and widget for retry`() = runBlocking {
        val f = Fixture(LayoutSnapshot(items = listOf(widget(7))))
        f.manager.beginSession()
        f.manager.edit { f.layout = it.copy(items = emptyList()) }
        f.failRestore = true
        val failure = runCatching { f.manager.undo() }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(1, f.manager.state.value.count)
        assertFalse(f.manager.state.value.isBusy)
        assertTrue(f.released.isEmpty())
        f.failRestore = false
        assertEquals(setOf(7), f.manager.undo()!!.widgetIds)
    }

    @Test
    fun `partial edit failure rolls back all tables and releases newly added widget`() = runBlocking {
        val initial = LayoutSnapshot(items = listOf(app()))
        val f = Fixture(initial)
        f.manager.beginSession()
        val failure = runCatching {
            f.manager.edit {
                f.layout = it.copy(userPages = listOf(LauncherPage("p", "temp", 1)), items = it.items + widget(9))
                error("second write failed")
            }
        }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(initial, f.layout)
        assertEquals(listOf(9), f.released)
        assertEquals(0, f.manager.state.value.count)
        assertFalse(f.manager.state.value.isBusy)
    }

    @Test
    fun `cancelled edit rolls back before clearing busy flag`() = runBlocking {
        val initial = LayoutSnapshot(items = listOf(app()))
        val f = Fixture(initial)
        f.manager.beginSession()
        val written = CompletableDeferred<Unit>()
        val edit = launch {
            f.manager.edit {
                f.layout = it.copy(items = emptyList())
                written.complete(Unit)
                awaitCancellation()
            }
        }
        written.await()
        edit.cancelAndJoin()
        assertEquals(initial, f.layout)
        assertEquals(0, f.manager.state.value.count)
        assertFalse(f.manager.state.value.isBusy)
    }

    @Test
    fun `rapid edits and queued undo see fresh committed snapshots`() = runBlocking {
        val initial = LayoutSnapshot(items = listOf(app()))
        val f = Fixture(initial)
        f.manager.beginSession()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val first = async {
            f.manager.edit {
                started.complete(Unit)
                finish.await()
                f.layout = it.copy(items = it.items.map { item -> item.copy(label = "first") })
            }
        }
        started.await()
        assertTrue(f.manager.state.value.isBusy)
        val second = async {
            f.manager.edit {
                assertEquals("first", it.items.single().label)
                f.layout = it.copy(items = it.items.map { item -> item.copy(label = "second") })
            }
        }
        yield()
        val undo = async { f.manager.undo() }
        finish.complete(Unit)
        first.await()
        second.await()
        assertEquals("first", undo.await()!!.items.single().label)
        assertEquals(initial, f.manager.undo())
        assertFalse(f.manager.state.value.isBusy)
    }

    @Test
    fun `external mutation is preserved instead of overwritten by stale history`() = runBlocking {
        val f = Fixture()
        f.manager.beginSession()
        f.manager.edit { f.layout = it.copy(items = listOf(app())) }
        val external = LayoutSnapshot(items = listOf(app("external")))
        f.layout = external
        assertNull(f.manager.undo())
        assertEquals(external, f.layout)
        assertEquals(0, f.manager.state.value.count)
    }

    @Test
    fun `backup or rebind update invalidates earlier undo without reverting new layout`() = runBlocking {
        val f = Fixture(LayoutSnapshot(items = listOf(widget(1))))
        f.manager.beginSession()
        f.manager.edit { f.layout = it.copy(items = emptyList()) }
        f.manager.withoutHistory { f.layout = LayoutSnapshot(items = listOf(widget(2))) }
        assertEquals(listOf(1), f.released)
        assertNull(f.manager.undo())
        assertEquals(setOf(2), f.layout.widgetIds)
    }

    @Test
    fun `startup releases orphaned IDs but protects live and pending widgets`() = runBlocking {
        val f = Fixture(LayoutSnapshot(items = listOf(widget(1))))
        f.protected.add(2)
        f.manager.initialize(setOf(1, 2, 3))
        assertEquals(listOf(3), f.released)
        f.protected.clear()
        f.manager.endSession()
        assertEquals(listOf(3, 2), f.released)
    }

    @Test
    fun `new edit after undo retains the earlier undo chain`() = runBlocking {
        val f = Fixture()
        f.manager.beginSession()
        f.manager.edit { f.layout = it.copy(items = listOf(app("a"))) }
        val first = f.layout
        f.manager.edit { f.layout = it.copy(items = it.items + app("b")) }
        f.manager.undo()
        f.manager.edit { f.layout = it.copy(items = it.items + app("c")) }
        assertEquals(2, f.manager.state.value.count)
        assertEquals(first, f.manager.undo())
        assertEquals(LayoutSnapshot(), f.manager.undo())
    }

    @Test
    fun `ending session waits for active edit and then frees deleted widget`() = runBlocking {
        val f = Fixture(LayoutSnapshot(items = listOf(widget(4))))
        f.manager.beginSession()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val edit = async {
            f.manager.edit {
                started.complete(Unit)
                finish.await()
                f.layout = it.copy(items = emptyList())
            }
        }
        started.await()
        val end = async { f.manager.endSession() }
        finish.complete(Unit)
        edit.await()
        end.await()
        assertEquals(listOf(4), f.released)
        assertEquals(0, f.manager.state.value.count)
        assertFalse(f.manager.state.value.isEditing)
    }
}

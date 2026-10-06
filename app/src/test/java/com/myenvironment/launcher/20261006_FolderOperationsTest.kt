package com.myenvironment.launcher

import com.myenvironment.launcher.core.model.*
import com.myenvironment.launcher.core.storage.db.DockItemEntity
import com.myenvironment.launcher.core.storage.db.LayoutItemEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test
import com.myenvironment.launcher.core.storage.LayoutUndoManager
import kotlinx.coroutines.runBlocking

class FolderOperationsTest {
    private fun app(id: String, x: Int = 0, page: String = LauncherPage.PAGE_ID_HOME) =
        LayoutItem(id, page, ItemType.APP, "package.$id", label = id, compact = GridPosition(x, 0), expanded = GridPosition(x, 1))
    private val a = app("a")
    private val b = app("b", 1)
    private fun grouped() = LayoutSnapshot(items = listOf(a, b)).groupApp(a, false, false, b.id, "folder")!!

    @Test fun createFolderRetainsBothAppsAndBothPositions() {
        val result = grouped()
        assertEquals(1, result.items.size)
        assertEquals(listOf("b", "a"), result.folder("folder")!!.folderApps.map { it.id })
        assertEquals(b.compact, result.items.single().compact)
        assertEquals(b.expanded, result.items.single().expanded)
        assertEquals("", result.items.single().packageName)
    }
    @Test fun createDockFolderRemovesHomeSourceAtomically() {
        val dock = DockItem("dock", 0, ItemType.APP, "pkg.dock", label = "Dock")
        val result = LayoutSnapshot(items = listOf(a), dockItems = listOf(dock))
            .groupApp(a, false, false, "dock", "folder")!!
        assertTrue(result.items.isEmpty())
        assertEquals(2, result.dockItems.single().folderApps.size)
        assertEquals(0, result.dockItems.single().positionIndex)
    }
    @Test fun dockSourceCanJoinHomeFolderAndDockIndicesAreContiguous() {
        val source = DockItem("source", 0, ItemType.APP, "pkg.source", label = "source")
        val other = source.copy(id = "other", positionIndex = 1)
        val result = grouped().copy(dockItems = listOf(source, other))
            .groupApp(source.asLayoutItem(), true, false, "folder", "unused")!!
        assertEquals(3, result.folder("folder")!!.folderApps.size)
        assertEquals(listOf(0), result.dockItems.map { it.positionIndex })
    }
    @Test fun drawerCopyNeverRemovesExistingPlacement() {
        val result = LayoutSnapshot(items = listOf(a, b)).groupApp(a.copy(id = "copy"), false, true, b.id, "folder")!!
        assertTrue(result.items.any { it.id == a.id })
        assertEquals(2, result.folder("folder")!!.folderApps.size)
    }
    @Test fun selfDropDuplicateAppsAndFolderNestingAreRejected() {
        val source = LayoutSnapshot(items = listOf(a, b))
        assertNull(source.groupApp(a, false, false, a.id, "folder"))
        assertNull(grouped().groupApp(a, false, true, "folder", "new"))
        assertNull(grouped().groupApp(grouped().items.single(), false, false, b.id, "nested"))
    }
    @Test fun movingFolderToDockPreservesMembersAndCapacityFailurePreservesSource() {
        val source = grouped()
        val result = source.moveHomeToDock("folder", 0, 1)!!
        assertTrue(result.items.isEmpty())
        assertEquals(source.items.single().folderApps, result.dockItems.single().folderApps)
        assertNull(source.moveHomeToDock("folder", 0, 0))
        assertEquals(1, source.items.size)
        assertEquals(source.items.single().folderApps, result.dockItems.single().asLayoutItem().folderApps)
    }
    @Test fun renameAndLastMemberRemovalPreserveOtherLayoutData() {
        val source = grouped()
        assertEquals("Tools", source.renameFolder("folder", " Tools ")!!.folder("folder")!!.label)
        assertNull(source.renameFolder("folder", " "))
        assertEquals(1, source.withFolderApps("folder", listOf(a.asFolderApp())).folder("folder")!!.folderApps.size)
        assertTrue(source.withFolderApps("folder", emptyList()).items.isEmpty())
    }
    @Test fun placementRespectsWidgetsFullGridAndOtherPages() {
        val widget = a.copy(type = ItemType.WIDGET, spanX = 2, spanY = 2)
        val source = LayoutSnapshot(items = listOf(widget, b.copy(pageId = "other")))
        assertNull(source.freeFolderCell(a.pageId, false, 2, 2))
        assertEquals(GridPosition(2, 0), source.freeFolderCell(a.pageId, false, 3, 2))
        assertEquals(GridPosition(0, 0), source.freeFolderCell("empty", false, 2, 2))
    }
    @Test fun roomAndBackupRoundTripBothFolderPlacements() {
        val home = grouped().items.single()
        assertEquals(home, LayoutItemEntity.fromDomain(home).toDomain())
        val dock = grouped().moveHomeToDock("folder", 0, 5)!!.dockItems.single()
        assertEquals(dock, DockItemEntity.fromDomain(dock).toDomain())
        val backup = BackupPayload(createdAt = "2026-10-06", pages = listOf(BackupPage(a.pageId, "HOME", items = listOf(
            BackupLayoutItem(id = home.id, type = home.type, packageName = home.packageName, label = home.label,
                compact = home.compact, expanded = home.expanded, folderApps = home.folderApps)))), dock = listOf(dock), settings = LauncherSettings())
        assertEquals(backup, Json.decodeFromString<BackupPayload>(Json.encodeToString(backup)))
    }
    @Test fun undoRestoresFolderCreationRenameAndCrossDockMoveAsWholeSnapshots() = runBlocking {
        var state = LayoutSnapshot(items = listOf(a, b))
        val initial = state
        val undo = LayoutUndoManager(readSnapshot = { state }, restoreSnapshot = { state = it }, releaseWidgetId = {})
        undo.beginSession()
        undo.edit { state = it.groupApp(a, false, false, b.id, "folder")!! }
        val created = state
        undo.edit { state = it.renameFolder("folder", "Tools")!! }
        val renamed = state
        undo.edit { state = it.moveHomeToDock("folder", 0, 5)!! }
        for (expected in listOf(renamed, created, initial)) {
            assertEquals(expected.normalized(), undo.undo())
            assertEquals(expected.normalized(), state)
        }
    }
    @Test fun rebindMissingMemberPreservesFolderLocationOtherMembersAndMemberIdentity() {
        val source = grouped()
        val replacement = FolderApp("different-id", "new.package", "new.Activity", "New app")
        val updated = source.replaceFolderApp("folder", "b", replacement)!!
        val parent = updated.folder("folder")!!
        assertEquals(source.items.single().compact, parent.compact)
        assertEquals(source.items.single().expanded, parent.expanded)
        assertEquals("b", parent.folderApps.first().id)
        assertEquals("new.package", parent.folderApps.first().packageName)
        assertEquals(source.items.single().folderApps.last(), parent.folderApps.last())
        assertEquals(1, updated.items.size)
        assertNull(source.replaceFolderApp("folder", "missing", replacement))
    }
    @Test fun schemaOneBackupStillDecodesWithEmptyFolderContents() {
        val old = """{"schemaVersion":1,"createdAt":"old","pages":[{"id":"home","name":"HOME","items":[{"type":"APP","packageName":"old","label":"Old","compact":{"x":0,"y":0}}]}],"dock":[],"settings":{}}"""
        val decoded = Json.decodeFromString<BackupPayload>(old)
        assertEquals(1, decoded.schemaVersion)
        assertTrue(decoded.pages.single().items.single().folderApps.isEmpty())
    }
    @Test fun oldJsonAndRoomRowsDefaultToNoFolderContents() {
        val old = """{"id":"old","pageId":"home","type":"APP","packageName":"old","label":"Old","compact":{"x":0,"y":0}}"""
        assertTrue(Json.decodeFromString<LayoutItem>(old).folderApps.isEmpty())
        assertTrue(LayoutItemEntity.fromDomain(a).copy(folderAppsJson = "[]").toDomain().folderApps.isEmpty())
    }
}

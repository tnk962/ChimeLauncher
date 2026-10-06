package com.myenvironment.launcher.core.model

/** Folder edits return one snapshot so storage and Undo commit the whole operation atomically. */
internal fun LayoutSnapshot.folder(id: String): LayoutItem? =
    items.find { it.id == id && it.type == ItemType.FOLDER }
        ?: dockItems.find { it.id == id && it.type == ItemType.FOLDER }?.asLayoutItem()

internal fun LayoutSnapshot.withFolderApps(id: String, apps: List<FolderApp>): LayoutSnapshot = copy(
    items = items.mapNotNull { if (it.id != id) it else if (apps.isEmpty()) null else it.copy(folderApps = apps) },
    dockItems = dockItems.mapNotNull { if (it.id != id) it else if (apps.isEmpty()) null else it.copy(folderApps = apps) }
        .mapIndexed { index, item -> item.copy(positionIndex = index) }
)

internal fun LayoutSnapshot.addFolderApp(id: String, app: FolderApp): LayoutSnapshot? {
    val folder = folder(id) ?: return null
    if (folder.folderApps.any { it.packageName == app.packageName && it.activityName == app.activityName }) return null
    return withFolderApps(id, folder.folderApps + app)
}

internal fun LayoutSnapshot.groupApp(source: LayoutItem, fromDock: Boolean, copySource: Boolean, targetId: String, newFolderId: String): LayoutSnapshot? {
    if (source.type != ItemType.APP || (!copySource && source.id == targetId)) return null
    val actual = if (copySource) source else if (fromDock) dockItems.find { it.id == source.id }?.asLayoutItem()
        else items.find { it.id == source.id }
    if (actual?.type != ItemType.APP) return null
    val target = items.find { it.id == targetId } ?: dockItems.find { it.id == targetId }?.asLayoutItem() ?: return null
    if (target.type !in setOf(ItemType.APP, ItemType.FOLDER)) return null
    val members = if (target.type == ItemType.APP) listOf(target.asFolderApp()) else target.folderApps
    if (members.any { it.packageName == actual.packageName && it.activityName == actual.activityName }) return null
    val apps = members + actual.asFolderApp()
    return copy(
        items = items.filterNot { !copySource && !fromDock && it.id == actual.id }.map {
            if (it.id == targetId) it.copy(id = if (it.type == ItemType.FOLDER) it.id else newFolderId, type = ItemType.FOLDER, packageName = "", activityName = "", targetUri = "",
                label = if (it.type == ItemType.FOLDER) it.label else "フォルダ", folderApps = apps) else it
        },
        dockItems = dockItems.filterNot { !copySource && fromDock && it.id == actual.id }.map {
            if (it.id == targetId) it.copy(id = if (it.type == ItemType.FOLDER) it.id else newFolderId, type = ItemType.FOLDER, packageName = "", activityName = "", targetUri = "",
                label = if (it.type == ItemType.FOLDER) it.label else "フォルダ", folderApps = apps) else it
        }.mapIndexed { index, item -> item.copy(positionIndex = index) }
    )
}

internal fun LayoutSnapshot.renameFolder(id: String, name: String): LayoutSnapshot? {
    if (folder(id) == null || name.isBlank()) return null
    return copy(items = items.map { if (it.id == id) it.copy(label = name.trim()) else it },
        dockItems = dockItems.map { if (it.id == id) it.copy(label = name.trim()) else it })
}

internal fun LayoutSnapshot.moveHomeToDock(id: String, index: Int, capacity: Int): LayoutSnapshot? {
    val item = items.find { it.id == id && it.type in setOf(ItemType.APP, ItemType.FOLDER) } ?: return null
    if (dockItems.size >= capacity) return null
    val dock = dockItems.sortedBy { it.positionIndex }.toMutableList()
    dock.add(index.coerceIn(0, dock.size), DockItem(item.id, 0, item.type, item.packageName, item.activityName,
        item.targetUri, item.label, item.folderApps))
    return copy(items = items.filterNot { it.id == id }, dockItems = dock.mapIndexed { i, entry -> entry.copy(positionIndex = i) })
}

internal fun LayoutSnapshot.freeFolderCell(pageId: String, expanded: Boolean, columns: Int, rows: Int, preferred: GridPosition? = null): GridPosition? {
    val occupied = items.filter { it.pageId == pageId }.flatMap { it.occupiedCells(expanded, columns, rows) }.toSet()
    return (listOfNotNull(preferred) + (0 until rows).flatMap { y -> (0 until columns).map { x -> GridPosition(x, y) } })
        .firstOrNull { it.x in 0 until columns && it.y in 0 until rows && it !in occupied }
}

internal fun LayoutSnapshot.replaceFolderApp(folderId: String, memberId: String, replacement: FolderApp): LayoutSnapshot? {
    val parent = folder(folderId) ?: return null
    if (parent.folderApps.none { it.id == memberId }) return null
    return withFolderApps(folderId, parent.folderApps.map { if (it.id == memberId) replacement.copy(id = memberId) else it })
}

package com.myenvironment.launcher.core.model

/** Find a whole free rectangle, excluding the folder being resized or moved. */
internal fun LayoutSnapshot.folderSpace(id: String, pageId: String, expanded: Boolean, columns: Int, rows: Int,
    spanX: Int, spanY: Int, preferred: GridPosition): GridPosition? {
    if (spanX !in 1..columns || spanY !in 1..rows) return null
    val occupied = items.filter { it.id != id && it.pageId == pageId }
        .flatMap { it.occupiedCells(expanded, columns, rows) }.toSet()
    val preferredClamped = GridPosition(preferred.x.coerceIn(0, columns - spanX), preferred.y.coerceIn(0, rows - spanY))
    return (listOf(preferredClamped) + (0..rows-spanY).flatMap { y -> (0..columns-spanX).map { x -> GridPosition(x, y) } })
        .firstOrNull { position -> (0 until spanY).all { dy -> (0 until spanX).all { dx ->
            GridPosition(position.x + dx, position.y + dy) !in occupied
        } } }
}

internal fun LayoutSnapshot.sizeFolder(id: String, spanX: Int, spanY: Int,
    compactColumns: Int, compactRows: Int, expandedColumns: Int, expandedRows: Int): LayoutSnapshot? {
    val folder = items.find { it.id == id && it.type == ItemType.FOLDER } ?: return null
    val compact = folderSpace(id, folder.pageId, false, compactColumns, compactRows, spanX, spanY, folder.compact) ?: return null
    val expanded = folderSpace(id, folder.pageId, true, expandedColumns, expandedRows, spanX, spanY, folder.expanded ?: folder.compact) ?: return null
    return copy(items = items.map { if (it.id == id) it.copy(spanX = spanX, spanY = spanY, compact = compact, expanded = expanded) else it })
}

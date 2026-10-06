package com.myenvironment.launcher.core.storage.db

import androidx.room.ColumnInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import com.myenvironment.launcher.core.model.FolderApp
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.myenvironment.launcher.core.model.BackupSnapshotSummary
import com.myenvironment.launcher.core.model.DockItem
import com.myenvironment.launcher.core.model.GridPosition
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LauncherPage
import com.myenvironment.launcher.core.model.LayoutItem

@Entity(tableName = "user_pages")
data class UserPageEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int
) {
    fun toDomain(): LauncherPage = LauncherPage(
        id = id,
        name = name,
        sortOrder = sortOrder,
        isFixed = false
    )

    companion object {
        fun fromDomain(page: LauncherPage): UserPageEntity = UserPageEntity(
            id = page.id,
            name = page.name,
            sortOrder = page.sortOrder
        )
    }
}

@Entity(tableName = "layout_items")
data class LayoutItemEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val type: String,
    val packageName: String,
    val activityName: String,
    val targetUri: String,
    val label: String,
    val compactX: Int,
    val compactY: Int,
    val expandedX: Int?,
    val expandedY: Int?,
    val spanX: Int,
    val spanY: Int,
    val appWidgetId: Int = LayoutItem.NO_WIDGET_ID,
    @ColumnInfo(defaultValue = "'[]'") val folderAppsJson: String = "[]"
) {
    fun toDomain(): LayoutItem = LayoutItem(
        id = id,
        pageId = pageId,
        type = runCatching { ItemType.valueOf(type) }.getOrDefault(ItemType.APP),
        packageName = packageName,
        activityName = activityName,
        targetUri = targetUri,
        label = label,
        compact = GridPosition(compactX, compactY),
        expanded = if (expandedX != null && expandedY != null) {
            GridPosition(expandedX, expandedY)
        } else {
            null
        },
        spanX = spanX,
        spanY = spanY,
        appWidgetId = appWidgetId,
        folderApps = Json.decodeFromString<List<FolderApp>>(folderAppsJson)
    )

    companion object {
        fun fromDomain(item: LayoutItem): LayoutItemEntity = LayoutItemEntity(
            id = item.id,
            pageId = item.pageId,
            type = item.type.name,
            packageName = item.packageName,
            activityName = item.activityName,
            targetUri = item.targetUri,
            label = item.label,
            compactX = item.compact.x,
            compactY = item.compact.y,
            expandedX = item.expanded?.x,
            expandedY = item.expanded?.y,
            spanX = item.spanX,
            spanY = item.spanY,
            appWidgetId = item.appWidgetId,
            folderAppsJson = Json.encodeToString(item.folderApps)
        )
    }
}

@Entity(tableName = "dock_items")
data class DockItemEntity(
    @PrimaryKey val id: String,
    val positionIndex: Int,
    val type: String,
    val packageName: String,
    val activityName: String,
    val targetUri: String,
    val label: String,
    @ColumnInfo(defaultValue = "'[]'") val folderAppsJson: String = "[]"
) {
    fun toDomain(): DockItem = DockItem(
        id = id,
        positionIndex = positionIndex,
        type = runCatching { ItemType.valueOf(type) }.getOrDefault(ItemType.APP),
        packageName = packageName,
        activityName = activityName,
        targetUri = targetUri,
        label = label,
        folderApps = Json.decodeFromString<List<FolderApp>>(folderAppsJson)
    )

    companion object {
        fun fromDomain(item: DockItem): DockItemEntity = DockItemEntity(
            id = item.id,
            positionIndex = item.positionIndex,
            type = item.type.name,
            packageName = item.packageName,
            activityName = item.activityName,
            targetUri = item.targetUri,
            label = item.label,
            folderAppsJson = Json.encodeToString(item.folderApps)
        )
    }
}

@Entity(tableName = "backup_snapshots")
data class BackupSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: String,
    val pageCount: Int,
    val itemCount: Int,
    val jsonPayload: String
) {
    fun toSummary(): BackupSnapshotSummary = BackupSnapshotSummary(
        id = id,
        name = name,
        createdAt = createdAt,
        pageCount = pageCount,
        itemCount = itemCount
    )
}

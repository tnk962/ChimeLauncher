package com.myenvironment.launcher.core.model

import kotlinx.serialization.Serializable

/**
 * バックアップJSON全体のスキーマモデル (仕様 22)
 */
@Serializable
data class BackupPayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val createdAt: String,
    val pages: List<BackupPage>,
    val dock: List<DockItem>,
    val settings: LauncherSettings
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupPage(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val items: List<BackupLayoutItem>
)

@Serializable
data class BackupLayoutItem(
    val id: String = "",
    val type: ItemType,
    val packageName: String,
    val activityName: String = "",
    val targetUri: String = "",
    val label: String,
    val compact: GridPosition,
    val expanded: GridPosition? = null,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val appWidgetId: Int = LayoutItem.NO_WIDGET_ID
)

/**
 * アプリ内に保存されたバックアップスナップショットのメタデータ (仕様 23)
 */
data class BackupSnapshotSummary(
    val id: Long,
    val name: String,
    val createdAt: String,
    val pageCount: Int,
    val itemCount: Int
)

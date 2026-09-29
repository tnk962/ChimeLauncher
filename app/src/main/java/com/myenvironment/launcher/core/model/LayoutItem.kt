package com.myenvironment.launcher.core.model

import kotlinx.serialization.Serializable

/**
 * ホーム画面およびDockに配置可能なアイテム種別 (仕様 12)
 */
@Serializable
enum class ItemType {
    /** 通常のAndroidアプリ */
    APP,
    /** Android Shortcut または Deep Link URI */
    SHORTCUT,
    /** Launcher独自Action (Hatena Feed, 通知履歴, Search, Settings等) */
    ACTION
}

/**
 * Grid上のセル座標
 */
@Serializable
data class GridPosition(
    val x: Int,
    val y: Int
)

/**
 * HOME / ユーザー追加ページのGridに配置されるアイテム (仕様 11, 12, 20, 24)
 */
@Serializable
data class LayoutItem(
    val id: String,
    val pageId: String,
    val type: ItemType,
    val packageName: String,
    val activityName: String = "",
    val targetUri: String = "",
    val label: String,
    val compact: GridPosition,
    val expanded: GridPosition? = null,
    val spanX: Int = 1,
    val spanY: Int = 1
) {
    /**
     * 現在のWindow状態 (Compact / Expanded) に応じた表示座標を返す (仕様 20)。
     * Expanded位置が未設定(null)の場合は、compactPositionからExpandedグリッド内へ自動変換する。
     */
    fun resolvePosition(
        isExpanded: Boolean,
        expandedColumns: Int,
        expandedRows: Int
    ): GridPosition {
        if (!isExpanded) {
            return compact
        }
        val target = expanded ?: compact
        return GridPosition(
            x = target.x.coerceIn(0, (expandedColumns - spanX).coerceAtLeast(0)),
            y = target.y.coerceIn(0, (expandedRows - spanY).coerceAtLeast(0))
        )
    }
}

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
    ACTION,
    /** Android AppWidget (ホーム画面ウィジェット) */
    WIDGET
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
 * HOME / ユーザー追加ページのGridに配置されるアイテム (仕様 11, 12, 20, 24, 30)
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
    val spanY: Int = 1,
    val appWidgetId: Int = NO_WIDGET_ID
) {
    /**
     * 現在のグリッド列数・行数内に収まる有効なセル幅・高さを返す。
     */
    fun resolveSpanX(maxColumns: Int): Int =
        spanX.coerceIn(1, maxColumns.coerceAtLeast(1))

    fun resolveSpanY(maxRows: Int): Int =
        spanY.coerceIn(1, maxRows.coerceAtLeast(1))

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
        val effectiveSpanX = resolveSpanX(expandedColumns)
        val effectiveSpanY = resolveSpanY(expandedRows)
        return GridPosition(
            x = target.x.coerceIn(0, (expandedColumns - effectiveSpanX).coerceAtLeast(0)),
            y = target.y.coerceIn(0, (expandedRows - effectiveSpanY).coerceAtLeast(0))
        )
    }

    /**
     * Compact / Expanded 問わず、現在のグリッドサイズ (columns × rows) からはみ出さないようクランプした左上座標を返す。
     */
    fun resolveClampedPosition(
        isExpanded: Boolean,
        columns: Int,
        rows: Int
    ): GridPosition {
        val raw = if (isExpanded) (expanded ?: compact) else compact
        val effectiveSpanX = resolveSpanX(columns)
        val effectiveSpanY = resolveSpanY(rows)
        return GridPosition(
            x = raw.x.coerceIn(0, (columns - effectiveSpanX).coerceAtLeast(0)),
            y = raw.y.coerceIn(0, (rows - effectiveSpanY).coerceAtLeast(0))
        )
    }

    /**
     * このアイテムが現在のグリッド上で占有するすべてのセル座標セットを返す（マルチセル Widget 対応）。
     */
    fun occupiedCells(
        isExpanded: Boolean,
        columns: Int,
        rows: Int
    ): Set<GridPosition> {
        val pos = resolveClampedPosition(isExpanded, columns, rows)
        val w = resolveSpanX(columns)
        val h = resolveSpanY(rows)
        val result = LinkedHashSet<GridPosition>(w * h)
        for (dy in 0 until h) {
            for (dx in 0 until w) {
                val cx = pos.x + dx
                val cy = pos.y + dy
                if (cx in 0 until columns && cy in 0 until rows) {
                    result.add(GridPosition(cx, cy))
                }
            }
        }
        return result
    }

    companion object {
        const val NO_WIDGET_ID = -1
    }
}

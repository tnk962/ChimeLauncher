package com.myenvironment.launcher.ui.adaptive

import com.myenvironment.launcher.core.model.ExpandedDockPosition
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowWidthSizeClass
import com.myenvironment.launcher.core.model.LauncherSettings

/**
 * Dock の表示位置 (仕様 17, 18)
 */
enum class DockPlacement {
    /** Compact (Fold Closed / 通常スマホ) -> 下部Dock */
    BOTTOM,
    /** Expanded の左右側面Dock */
    RIGHT,
    LEFT
}

/**
 * 現在のWindowサイズに応じたAdaptiveレイアウト仕様
 */
data class AdaptiveLayoutSpec(
    val isExpanded: Boolean,
    val dockPlacement: DockPlacement,
    val homeGridColumns: Int,
    val homeGridRows: Int,
    val tinyIconsColumns: Int
)

/**
 * Jetpack WindowManager / Material 3 Adaptive を用いて現在のWindow幅からFold開閉状態を判定する (仕様 18)
 */
@Composable
fun rememberAdaptiveLayoutSpec(settings: LauncherSettings): AdaptiveLayoutSpec {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isExpanded = windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED ||
        windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM

    return AdaptiveLayoutSpec(
        isExpanded = isExpanded,
        dockPlacement = resolveDockPlacement(settings, isExpanded),
        homeGridColumns = if (isExpanded) settings.expandedGridColumns else settings.compactGridColumns,
        homeGridRows = if (isExpanded) settings.expandedGridRows else settings.compactGridRows,
        tinyIconsColumns = if (isExpanded) settings.tinyIconsColumnsExpanded else settings.tinyIconsColumnsCompact
    )
}

fun resolveDockPlacement(settings: LauncherSettings, isExpanded: Boolean): DockPlacement =
    if (!isExpanded) DockPlacement.BOTTOM else when (settings.expandedDockPosition) {
        ExpandedDockPosition.BOTTOM -> DockPlacement.BOTTOM
        ExpandedDockPosition.LEFT -> DockPlacement.LEFT
        ExpandedDockPosition.RIGHT -> DockPlacement.RIGHT
    }

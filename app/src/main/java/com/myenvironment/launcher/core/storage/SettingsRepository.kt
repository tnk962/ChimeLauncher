package com.myenvironment.launcher.core.storage

import com.myenvironment.launcher.core.model.DiscoverMode
import com.myenvironment.launcher.core.model.ExpandedPageLayoutMode
import com.myenvironment.launcher.core.model.LauncherSettings
import kotlinx.coroutines.flow.Flow

/**
 * Launcher設定値を永続化する境界インターフェース (仕様 21, 40)
 */
interface SettingsRepository {
    val settings: Flow<LauncherSettings>

    suspend fun setLayoutLocked(locked: Boolean)

    suspend fun setCompactGridSize(columns: Int, rows: Int)

    suspend fun setExpandedGridSize(columns: Int, rows: Int)

    suspend fun setTinyIconsConfig(
        columnsCompact: Int,
        columnsExpanded: Int,
        sizeDp: Int,
        showLabels: Boolean
    )

    suspend fun setSwipeDownNotificationEnabled(enabled: Boolean)

    suspend fun setDiscoverMode(mode: DiscoverMode)

    suspend fun setExpandedPageLayoutMode(mode: ExpandedPageLayoutMode)

    suspend fun setAllAppsLeftOnlyInExpandedSingle(leftOnly: Boolean)

    suspend fun replaceSettings(newSettings: LauncherSettings)
}

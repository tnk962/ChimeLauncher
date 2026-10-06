package com.myenvironment.launcher.core.storage

import com.myenvironment.launcher.core.feed.FeedCategory
import com.myenvironment.launcher.core.model.GalaxyNotificationHistoryTarget
import com.myenvironment.launcher.core.model.ExpandedDockPosition
import com.myenvironment.launcher.core.model.DiscoverMode
import com.myenvironment.launcher.core.model.ExpandedPageLayoutMode
import com.myenvironment.launcher.core.model.IndicatorStyle
import com.myenvironment.launcher.core.model.LauncherSettings
import com.myenvironment.launcher.core.model.ReturnChimeInterval
import kotlinx.coroutines.flow.Flow

/**
 * Chime Launcher 設定値および Chime 状態を永続化する境界インターフェース (仕様 21, 28, 40)
 */
interface SettingsRepository {
    val settings: Flow<LauncherSettings>

    val lastFirstChimeDate: Flow<String>
    val lastLauncherVisibleTimestamp: Flow<Long>

    suspend fun setDockIconCount(count: Int)

    suspend fun setExpandedDockPosition(position: ExpandedDockPosition)

    suspend fun setLayoutLocked(locked: Boolean)

    suspend fun setCompactGridSize(columns: Int, rows: Int)

    suspend fun setExpandedGridSize(columns: Int, rows: Int)

    suspend fun setTinyIconsConfig(
        columnsCompact: Int,
        columnsExpanded: Int,
        sizeDp: Int,
        showLabels: Boolean
    )

    suspend fun setSearchIndexEdgeDistanceDp(distanceDp: Int)

    suspend fun setSwipeDownNotificationEnabled(enabled: Boolean)

    suspend fun setGalaxyNotificationHistoryTarget(target: GalaxyNotificationHistoryTarget)

    suspend fun setAllAppsPageEnabled(enabled: Boolean)

    suspend fun setFeedCategoryEnabled(category: FeedCategory, enabled: Boolean)

    suspend fun setDiscoverMode(mode: DiscoverMode)

    suspend fun setExpandedPageLayoutMode(mode: ExpandedPageLayoutMode)

    suspend fun setAllAppsLeftOnlyInExpandedSingle(leftOnly: Boolean)

    suspend fun setIndicatorStyle(style: IndicatorStyle)

    suspend fun setFirstChimeEnabled(enabled: Boolean)

    suspend fun setReturnChimeEnabled(enabled: Boolean)

    suspend fun setReturnChimeInterval(interval: ReturnChimeInterval)

    suspend fun setTimeChimeEnabled(enabled: Boolean)

    suspend fun setChimeSoundEnabled(enabled: Boolean)

    suspend fun setLastFirstChimeDate(date: String)

    suspend fun setLastLauncherVisibleTimestamp(timestampMillis: Long)

    suspend fun replaceSettings(newSettings: LauncherSettings)
}

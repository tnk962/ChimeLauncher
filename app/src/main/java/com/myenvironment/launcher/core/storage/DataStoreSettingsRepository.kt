package com.myenvironment.launcher.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myenvironment.launcher.core.model.DiscoverMode
import com.myenvironment.launcher.core.model.ExpandedPageLayoutMode
import com.myenvironment.launcher.core.model.IndicatorStyle
import com.myenvironment.launcher.core.model.LauncherSettings
import com.myenvironment.launcher.core.model.ReturnChimeInterval
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "launcher_settings"
)

/**
 * Preferences DataStore を用いた SettingsRepository の実装 (仕様 21, 28)
 */
class DataStoreSettingsRepository(
    private val appContext: Context
) : SettingsRepository {

    private object Keys {
        val INITIALIZED = booleanPreferencesKey("initialized_default_layout")
        val LAYOUT_LOCKED = booleanPreferencesKey("layout_locked")
        val COMPACT_COLS = intPreferencesKey("compact_grid_columns")
        val COMPACT_ROWS = intPreferencesKey("compact_grid_rows")
        val EXPANDED_COLS = intPreferencesKey("expanded_grid_columns")
        val EXPANDED_ROWS = intPreferencesKey("expanded_grid_rows")
        val TINY_COLS_COMPACT = intPreferencesKey("tiny_icons_columns_compact")
        val TINY_COLS_EXPANDED = intPreferencesKey("tiny_icons_columns_expanded")
        val TINY_SIZE_DP = intPreferencesKey("tiny_icons_size_dp")
        val TINY_SHOW_LABELS = booleanPreferencesKey("tiny_icons_show_labels")
        val SWIPE_DOWN_NOTIFICATION = booleanPreferencesKey("swipe_down_notification_enabled")
        val DISCOVER_MODE = stringPreferencesKey("discover_mode_v2")
        val EXPANDED_LAYOUT_MODE = stringPreferencesKey("expanded_page_layout_mode")
        val ALL_APPS_LEFT_ONLY_EXPANDED = booleanPreferencesKey("all_apps_left_only_expanded")

        // Chime Launcher 設定 & 状態管理 (仕様 11, 13, 28, 32)
        val INDICATOR_STYLE = stringPreferencesKey("indicator_style")
        val FIRST_CHIME_ENABLED = booleanPreferencesKey("first_chime_enabled")
        val RETURN_CHIME_ENABLED = booleanPreferencesKey("return_chime_enabled")
        val RETURN_CHIME_INTERVAL = stringPreferencesKey("return_chime_interval")
        val TIME_CHIME_ENABLED = booleanPreferencesKey("time_chime_enabled")
        val CHIME_SOUND_ENABLED = booleanPreferencesKey("chime_sound_enabled")
        val LAST_FIRST_CHIME_DATE = stringPreferencesKey("last_first_chime_date")
        val LAST_LAUNCHER_VISIBLE_TIMESTAMP = longPreferencesKey("last_launcher_visible_timestamp")
    }

    override val settings: Flow<LauncherSettings> = appContext.launcherDataStore.data.map { prefs ->
        val default = LauncherSettings()
        val modeStr = prefs[Keys.DISCOVER_MODE]
        val discoverMode = modeStr?.let {
            runCatching { DiscoverMode.valueOf(it) }.getOrNull()
        } ?: default.discoverMode

        val expandedModeStr = prefs[Keys.EXPANDED_LAYOUT_MODE]
        val expandedLayoutMode = expandedModeStr?.let {
            runCatching { ExpandedPageLayoutMode.valueOf(it) }.getOrNull()
        } ?: default.expandedPageLayoutMode

        val indicatorStyle = prefs[Keys.INDICATOR_STYLE]?.let {
            runCatching { IndicatorStyle.valueOf(it) }.getOrNull()
        } ?: default.indicatorStyle

        val returnInterval = prefs[Keys.RETURN_CHIME_INTERVAL]?.let {
            runCatching { ReturnChimeInterval.valueOf(it) }.getOrNull()
        } ?: default.returnChimeInterval

        LauncherSettings(
            layoutLocked = prefs[Keys.LAYOUT_LOCKED] ?: default.layoutLocked,
            compactGridColumns = prefs[Keys.COMPACT_COLS] ?: default.compactGridColumns,
            compactGridRows = prefs[Keys.COMPACT_ROWS] ?: default.compactGridRows,
            expandedGridColumns = prefs[Keys.EXPANDED_COLS] ?: default.expandedGridColumns,
            expandedGridRows = prefs[Keys.EXPANDED_ROWS] ?: default.expandedGridRows,
            tinyIconsColumnsCompact = prefs[Keys.TINY_COLS_COMPACT] ?: default.tinyIconsColumnsCompact,
            tinyIconsColumnsExpanded = prefs[Keys.TINY_COLS_EXPANDED] ?: default.tinyIconsColumnsExpanded,
            tinyIconsSizeDp = prefs[Keys.TINY_SIZE_DP] ?: default.tinyIconsSizeDp,
            tinyIconsShowLabels = prefs[Keys.TINY_SHOW_LABELS] ?: default.tinyIconsShowLabels,
            swipeDownNotificationEnabled = prefs[Keys.SWIPE_DOWN_NOTIFICATION] ?: default.swipeDownNotificationEnabled,
            discoverMode = discoverMode,
            expandedPageLayoutMode = expandedLayoutMode,
            allAppsLeftOnlyInExpandedSingle = prefs[Keys.ALL_APPS_LEFT_ONLY_EXPANDED]
                ?: default.allAppsLeftOnlyInExpandedSingle,
            indicatorStyle = indicatorStyle,
            firstChimeEnabled = prefs[Keys.FIRST_CHIME_ENABLED] ?: default.firstChimeEnabled,
            returnChimeEnabled = prefs[Keys.RETURN_CHIME_ENABLED] ?: default.returnChimeEnabled,
            returnChimeInterval = returnInterval,
            timeChimeEnabled = prefs[Keys.TIME_CHIME_ENABLED] ?: default.timeChimeEnabled,
            chimeSoundEnabled = prefs[Keys.CHIME_SOUND_ENABLED] ?: default.chimeSoundEnabled
        )
    }

    override val lastFirstChimeDate: Flow<String> = appContext.launcherDataStore.data.map { prefs ->
        prefs[Keys.LAST_FIRST_CHIME_DATE] ?: ""
    }

    override val lastLauncherVisibleTimestamp: Flow<Long> = appContext.launcherDataStore.data.map { prefs ->
        prefs[Keys.LAST_LAUNCHER_VISIBLE_TIMESTAMP] ?: 0L
    }

    val isDefaultLayoutSeeded: Flow<Boolean> = appContext.launcherDataStore.data.map { prefs ->
        prefs[Keys.INITIALIZED] ?: false
    }

    suspend fun markDefaultLayoutSeeded() {
        appContext.launcherDataStore.edit { prefs ->
            prefs[Keys.INITIALIZED] = true
        }
    }

    override suspend fun setLayoutLocked(locked: Boolean) {
        appContext.launcherDataStore.edit { prefs ->
            prefs[Keys.LAYOUT_LOCKED] = locked
        }
    }

    override suspend fun setCompactGridSize(columns: Int, rows: Int) {
        appContext.launcherDataStore.edit { prefs ->
            prefs[Keys.COMPACT_COLS] = columns.coerceIn(3, 10)
            prefs[Keys.COMPACT_ROWS] = rows.coerceIn(3, 12)
        }
    }

    override suspend fun setExpandedGridSize(columns: Int, rows: Int) {
        appContext.launcherDataStore.edit { prefs ->
            prefs[Keys.EXPANDED_COLS] = columns.coerceIn(4, 14)
            prefs[Keys.EXPANDED_ROWS] = rows.coerceIn(4, 12)
        }
    }

    override suspend fun setTinyIconsConfig(
        columnsCompact: Int,
        columnsExpanded: Int,
        sizeDp: Int,
        showLabels: Boolean
    ) {
        appContext.launcherDataStore.edit { prefs ->
            prefs[Keys.TINY_COLS_COMPACT] = columnsCompact.coerceIn(4, 12)
            prefs[Keys.TINY_COLS_EXPANDED] = columnsExpanded.coerceIn(6, 16)
            prefs[Keys.TINY_SIZE_DP] = sizeDp.coerceIn(24, 64)
            prefs[Keys.TINY_SHOW_LABELS] = showLabels
        }
    }

    override suspend fun setSwipeDownNotificationEnabled(enabled: Boolean) {
        appContext.launcherDataStore.edit { prefs ->
            prefs[Keys.SWIPE_DOWN_NOTIFICATION] = enabled
        }
    }

    override suspend fun setDiscoverMode(mode: DiscoverMode) {
        appContext.launcherDataStore.edit { prefs ->
            prefs[Keys.DISCOVER_MODE] = mode.name
        }
    }

    override suspend fun setExpandedPageLayoutMode(mode: ExpandedPageLayoutMode) {
        appContext.launcherDataStore.edit { prefs ->
            prefs[Keys.EXPANDED_LAYOUT_MODE] = mode.name
        }
    }

    override suspend fun setAllAppsLeftOnlyInExpandedSingle(leftOnly: Boolean) {
        appContext.launcherDataStore.edit { prefs ->
            prefs[Keys.ALL_APPS_LEFT_ONLY_EXPANDED] = leftOnly
        }
    }

    override suspend fun setIndicatorStyle(style: IndicatorStyle) {
        appContext.launcherDataStore.edit { prefs ->
            prefs[Keys.INDICATOR_STYLE] = style.name
        }
    }

    override suspend fun setFirstChimeEnabled(enabled: Boolean) {
        appContext.launcherDataStore.edit { prefs ->
            prefs[Keys.FIRST_CHIME_ENABLED] = enabled
        }
    }

    override suspend fun setReturnChimeEnabled(enabled: Boolean) {
        appContext.launcherDataStore.edit { prefs ->
            prefs[Keys.RETURN_CHIME_ENABLED] = enabled
        }
    }

    override suspend fun setReturnChimeInterval(interval: ReturnChimeInterval) {
        appContext.launcherDataStore.edit { prefs ->
            prefs[Keys.RETURN_CHIME_INTERVAL] = interval.name
        }
    }

    override suspend fun setTimeChimeEnabled(enabled: Boolean) {
        appContext.launcherDataStore.edit { prefs ->
            prefs[Keys.TIME_CHIME_ENABLED] = enabled
        }
    }

    override suspend fun setChimeSoundEnabled(enabled: Boolean) {
        appContext.launcherDataStore.edit { prefs ->
            prefs[Keys.CHIME_SOUND_ENABLED] = enabled
        }
    }

    override suspend fun setLastFirstChimeDate(date: String) {
        appContext.launcherDataStore.edit { prefs ->
            prefs[Keys.LAST_FIRST_CHIME_DATE] = date
        }
    }

    override suspend fun setLastLauncherVisibleTimestamp(timestampMillis: Long) {
        appContext.launcherDataStore.edit { prefs ->
            prefs[Keys.LAST_LAUNCHER_VISIBLE_TIMESTAMP] = timestampMillis
        }
    }

    override suspend fun replaceSettings(newSettings: LauncherSettings) {
        appContext.launcherDataStore.edit { prefs ->
            prefs[Keys.LAYOUT_LOCKED] = newSettings.layoutLocked
            prefs[Keys.COMPACT_COLS] = newSettings.compactGridColumns
            prefs[Keys.COMPACT_ROWS] = newSettings.compactGridRows
            prefs[Keys.EXPANDED_COLS] = newSettings.expandedGridColumns
            prefs[Keys.EXPANDED_ROWS] = newSettings.expandedGridRows
            prefs[Keys.TINY_COLS_COMPACT] = newSettings.tinyIconsColumnsCompact
            prefs[Keys.TINY_COLS_EXPANDED] = newSettings.tinyIconsColumnsExpanded
            prefs[Keys.TINY_SIZE_DP] = newSettings.tinyIconsSizeDp
            prefs[Keys.TINY_SHOW_LABELS] = newSettings.tinyIconsShowLabels
            prefs[Keys.SWIPE_DOWN_NOTIFICATION] = newSettings.swipeDownNotificationEnabled
            prefs[Keys.DISCOVER_MODE] = newSettings.discoverMode.name
            prefs[Keys.EXPANDED_LAYOUT_MODE] = newSettings.expandedPageLayoutMode.name
            prefs[Keys.ALL_APPS_LEFT_ONLY_EXPANDED] = newSettings.allAppsLeftOnlyInExpandedSingle
            prefs[Keys.INDICATOR_STYLE] = newSettings.indicatorStyle.name
            prefs[Keys.FIRST_CHIME_ENABLED] = newSettings.firstChimeEnabled
            prefs[Keys.RETURN_CHIME_ENABLED] = newSettings.returnChimeEnabled
            prefs[Keys.RETURN_CHIME_INTERVAL] = newSettings.returnChimeInterval.name
            prefs[Keys.TIME_CHIME_ENABLED] = newSettings.timeChimeEnabled
            prefs[Keys.CHIME_SOUND_ENABLED] = newSettings.chimeSoundEnabled
        }
    }
}

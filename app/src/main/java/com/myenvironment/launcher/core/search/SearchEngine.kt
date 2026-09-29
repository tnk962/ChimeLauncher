package com.myenvironment.launcher.core.search

import com.myenvironment.launcher.core.model.AppInfo
import com.myenvironment.launcher.core.model.LauncherAction
import com.myenvironment.launcher.core.model.LayoutItem

/**
 * 検索結果の構造化モデル (仕様 8.2)
 *
 * 優先順位:
 * 1. インストール済みアプリ (installedApps)
 * 2. Launcherショートカット (shortcuts)
 * 3. Launcher独自Action (actions)
 * 4. Web検索クエリ (webSearchQuery)
 */
data class SearchResultGroup(
    val query: String,
    val matchingApps: List<AppInfo>,
    val matchingShortcuts: List<LayoutItem>,
    val matchingActions: List<LauncherAction>,
    val webSearchQuery: String
)

/**
 * Swipe Up Search 用の検索エンジン境界インターフェース (仕様 8, 9, 40)
 */
interface SearchEngine {
    fun search(
        query: String,
        installedApps: List<AppInfo>,
        configuredShortcuts: List<LayoutItem>
    ): SearchResultGroup
}

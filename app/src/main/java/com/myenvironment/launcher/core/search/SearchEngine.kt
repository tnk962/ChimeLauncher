package com.myenvironment.launcher.core.search

import com.myenvironment.launcher.core.model.AppInfo
import com.myenvironment.launcher.core.model.LauncherAction
import com.myenvironment.launcher.core.model.LayoutItem

/**
 * 検索結果の構造化モデル (仕様 8.2, Chime Launcher 仕様 21〜27)
 *
 * - Zero Query State (`query.isEmpty()`):
 *   `zeroQuerySections` (Recently Used / Frequently Used / Recently Installed) を表示する。
 * - 検索文字入力後 (`query.isNotEmpty()`):
 *   1. インストール済みアプリ (`matchingApps` - 名前一致度優先＋利用頻度微調整)
 *   2. Launcherショートカット (`matchingShortcuts`)
 *   3. Launcher独自Action (`matchingActions`)
 *   4. Web検索クエリ (`webSearchQuery`)
 */
data class SearchResultGroup(
    val query: String,
    val matchingApps: List<AppInfo>,
    val matchingShortcuts: List<LayoutItem>,
    val matchingActions: List<LauncherAction>,
    val webSearchQuery: String,
    val zeroQuerySections: ZeroQueryAppSections = ZeroQueryAppSections()
)

/**
 * Swipe Up Search 用の検索エンジン境界インターフェース (仕様 8, 9, 21〜27, 40)
 */
interface SearchEngine {
    fun search(
        query: String,
        installedApps: List<AppInfo>,
        configuredShortcuts: List<LayoutItem>,
        usageMap: Map<String, AppUsageMetric> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): SearchResultGroup
}

package com.myenvironment.launcher.core.search

import com.myenvironment.launcher.core.model.AppInfo
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LauncherAction
import com.myenvironment.launcher.core.model.LayoutItem
import java.util.Locale

/**
 * Swipe Up Search 用の検索エンジン実装 (仕様 8.2, 9, Chime Launcher 仕様 21〜27)
 *
 * - Zero Query State (`query.isBlank()`):
 *   - `Recently Used` / `Frequently Used` / `Recently Installed` を構築して返す。
 * - 文字入力後 (`query.isNotBlank()`):
 *   - ゼロクエリ候補と検索結果を混在させず、通常のアプリ検索を最優先する (仕様 26)。
 *   - 基本順位 (仕様 27):
 *     1. Exact match (1000)
 *     2. Prefix match (800)
 *     3. Token / Partial match (単語先頭一致=650, 部分一致=600, パッケージ名一致=400)
 *     4. Usage frequency (同一一致度ティア内で 0..90 点の軽いボーナスを加算し、名前一致度を逆転させない)
 */
class DefaultSearchEngine : SearchEngine {

    override fun search(
        query: String,
        installedApps: List<AppInfo>,
        configuredShortcuts: List<LayoutItem>,
        usageMap: Map<String, AppUsageMetric>,
        nowMillis: Long
    ): SearchResultGroup {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            val zeroQuery = AppUsageAnalyzer.computeZeroQuerySections(
                installedApps = installedApps,
                usageMap = usageMap,
                nowMillis = nowMillis
            )
            return SearchResultGroup(
                query = "",
                matchingApps = installedApps,
                matchingShortcuts = configuredShortcuts.filter { it.type == ItemType.SHORTCUT },
                matchingActions = LauncherAction.entries,
                webSearchQuery = "",
                zeroQuerySections = zeroQuery
            )
        }

        val normalizedQuery = trimmed.lowercase(Locale.ROOT)
        val wordBoundaryRegex = Regex("""[\s._\-/]+""")

        // 1. インストール済みアプリ検索（1. Exact > 2. Prefix > 3. Token/Partial > 4. Usage frequency）
        val scoredApps = installedApps.mapNotNull { app ->
            val labelLower = app.label.lowercase(Locale.ROOT)
            val pkgLower = app.packageName.lowercase(Locale.ROOT)
            val tokens = labelLower.split(wordBoundaryRegex).filter { it.isNotBlank() }

            val baseMatchScore = when {
                labelLower == normalizedQuery -> 1000
                labelLower.startsWith(normalizedQuery) -> 800
                tokens.any { it.startsWith(normalizedQuery) } -> 650
                labelLower.contains(normalizedQuery) -> 600
                pkgLower.contains(normalizedQuery) -> 400
                else -> 0
            }

            if (baseMatchScore > 0) {
                val usageBonus = usageMap[app.packageName]?.searchRankingBonus ?: 0
                app to (baseMatchScore + usageBonus)
            } else {
                null
            }
        }.sortedWith(
            compareByDescending<Pair<AppInfo, Int>> { it.second }
                .thenBy { it.first.label }
        ).map { it.first }

        // 2. Launcherショートカット検索
        val shortcuts = configuredShortcuts
            .filter { it.type == ItemType.SHORTCUT }
            .distinctBy { "${it.label}|${it.targetUri}" }
            .filter { item ->
                item.label.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    item.targetUri.lowercase(Locale.ROOT).contains(normalizedQuery)
            }

        // 3. Launcher独自Action検索
        val actions = LauncherAction.entries.filter { action ->
            action.title.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                action.subtitle.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                action.name.lowercase(Locale.ROOT).contains(normalizedQuery)
        }

        return SearchResultGroup(
            query = trimmed,
            matchingApps = scoredApps,
            matchingShortcuts = shortcuts,
            matchingActions = actions,
            webSearchQuery = trimmed,
            zeroQuerySections = ZeroQueryAppSections()
        )
    }
}

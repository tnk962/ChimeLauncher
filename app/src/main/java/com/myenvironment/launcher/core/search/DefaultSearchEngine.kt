package com.myenvironment.launcher.core.search

import com.myenvironment.launcher.core.model.AppInfo
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LauncherAction
import com.myenvironment.launcher.core.model.LayoutItem
import java.util.Locale

/**
 * Swipe Up Search 用の検索エンジン実装 (仕様 8.2, 9)
 *
 * 検索優先順位:
 * 1. インストール済みアプリ (前方一致 -> 部分一致 -> パッケージ名一致)
 * 2. Launcherショートカット
 * 3. Launcher独自Action
 * 4. Web検索 (Googleで「検索文字列」を検索)
 */
class DefaultSearchEngine : SearchEngine {

    override fun search(
        query: String,
        installedApps: List<AppInfo>,
        configuredShortcuts: List<LayoutItem>
    ): SearchResultGroup {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            // 未入力時は全アプリ一覧とActionを返し、そのままスクロールして選べるようにする
            return SearchResultGroup(
                query = "",
                matchingApps = installedApps,
                matchingShortcuts = configuredShortcuts.filter { it.type == ItemType.SHORTCUT },
                matchingActions = LauncherAction.entries,
                webSearchQuery = ""
            )
        }

        val normalizedQuery = trimmed.lowercase(Locale.ROOT)

        // 1. インストール済みアプリ検索（スコア順：完全一致 > 前方一致 > ラベル部分一致 > パッケージ名一致）
        val scoredApps = installedApps.mapNotNull { app ->
            val labelLower = app.label.lowercase(Locale.ROOT)
            val pkgLower = app.packageName.lowercase(Locale.ROOT)
            val score = when {
                labelLower == normalizedQuery -> 100
                labelLower.startsWith(normalizedQuery) -> 80
                labelLower.contains(normalizedQuery) -> 60
                pkgLower.contains(normalizedQuery) -> 40
                else -> 0
            }
            if (score > 0) app to score else null
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
            webSearchQuery = trimmed
        )
    }
}

package com.myenvironment.launcher.core.model

import kotlinx.serialization.Serializable

/**
 * Launcher内のページを表すモデル
 *
 * 固定ページ:
 * - Page -2: Google Discover (id = "discover")
 * - Page -1: All Apps / Tiny Icons (id = "all_apps")
 * - Page 0 : HOME (id = "home")
 *
 * ユーザー追加ページ:
 * - Page 1..N: HOMEの右側に自由追加 (sortOrder >= 1)
 *
 * 最右固定ページ:
 * - Settings: マイランチャー設定 (id = "settings", 一番右にスワイプした時に表示)
 */
@Serializable
data class LauncherPage(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val isFixed: Boolean = false
) {
    companion object {
        const val PAGE_ID_DISCOVER = "discover"
        const val PAGE_ID_ALL_APPS = "all_apps"
        const val PAGE_ID_HOME = "home"
        const val PAGE_ID_SETTINGS = "settings"

        val FIXED_DISCOVER = LauncherPage(
            id = PAGE_ID_DISCOVER,
            name = "Discover",
            sortOrder = -2,
            isFixed = true
        )

        val FIXED_ALL_APPS = LauncherPage(
            id = PAGE_ID_ALL_APPS,
            name = "All Apps",
            sortOrder = -1,
            isFixed = true
        )

        val FIXED_HOME = LauncherPage(
            id = PAGE_ID_HOME,
            name = "HOME",
            sortOrder = 0,
            isFixed = true
        )

        val FIXED_SETTINGS = LauncherPage(
            id = PAGE_ID_SETTINGS,
            name = "設定",
            sortOrder = 9999,
            isFixed = true
        )
    }
}

package com.myenvironment.launcher.core.model

import kotlinx.serialization.Serializable

/**
 * Google Discover ページの動作モード (仕様 29)
 */
@Serializable
enum class DiscoverMode(val displayName: String, val description: String) {
    NATIVE_BRIDGE(
        displayName = "Discover フィード表示 (推奨)",
        description = "左端ページに記事一覧を表示し、さらに端（行き止まり）へスワイプするとGoogleアプリを起動します"
    ),
    GOOGLE_APP(
        displayName = "Google Appを自動で開く",
        description = "左端ページへスワイプしたタイミングでGoogleアプリを起動します"
    ),
    DISABLED(
        displayName = "無効",
        description = "Google Discoverページを非表示にし、All Appsを最左ページにします"
    )
}

/**
 * Fold を開いた状態 (Expanded) でのページ表示モード
 */
@Serializable
enum class ExpandedPageLayoutMode(val displayName: String, val description: String) {
    DUAL_PAGE(
        displayName = "左右2ページ見開き表示 (推奨)",
        description = "HOMEやAll Appsを左右2ページ見開きで表示します（Discover・設定ページは1ページ全画面固定）"
    ),
    SINGLE_FULL(
        displayName = "1ページ全画面表示",
        description = "開いた時もすべてのページを1ページ全画面で広く表示します"
    )
}

/**
 * Launcher全体の設定モデル (仕様 10.2, 15, 21, 22, 29)
 */
@Serializable
data class LauncherSettings(
    val layoutLocked: Boolean = false,
    val compactGridColumns: Int = 5,
    val compactGridRows: Int = 6,
    val expandedGridColumns: Int = 8,
    val expandedGridRows: Int = 6,
    val tinyIconsColumnsCompact: Int = 7,
    val tinyIconsColumnsExpanded: Int = 10,
    val tinyIconsSizeDp: Int = 36,
    val tinyIconsShowLabels: Boolean = false,
    val swipeDownNotificationEnabled: Boolean = true,
    val discoverMode: DiscoverMode = DiscoverMode.NATIVE_BRIDGE,
    val expandedPageLayoutMode: ExpandedPageLayoutMode = ExpandedPageLayoutMode.DUAL_PAGE,
    val allAppsLeftOnlyInExpandedSingle: Boolean = false
)

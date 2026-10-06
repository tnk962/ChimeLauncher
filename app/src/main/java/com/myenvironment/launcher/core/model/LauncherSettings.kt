package com.myenvironment.launcher.core.model

import kotlinx.serialization.Serializable
import com.myenvironment.launcher.core.feed.FeedCategory

/**
 * Google Discover ページの動作モード (仕様 29)
 */
@Serializable
enum class DiscoverMode(val displayName: String, val description: String) {
    NATIVE_BRIDGE(
        displayName = "独自フィード + Google Discover (推奨)",
        description = "独自フィードのさらに左にGoogle Discoverを表示します。Chime Discover Companionが必要です"
    ),
    FEED_ONLY(
        displayName = "独自フィードのみ",
        description = "独自フィードだけを表示します。Google Discoverへの接続やGoogleアプリの自動起動は行いません"
    ),
    GOOGLE_ONLY(
        displayName = "Google Discoverのみ",
        description = "HOMEからAll Apps、その先へのスワイプでGoogle Discoverを表示します。Chime Discover Companionが必要です"
    ),
    GOOGLE_APP(
        displayName = "Google Appを自動で開く",
        description = "左端ページへスワイプしたタイミングでGoogleアプリを起動します"
    ),
    DISABLED(
        displayName = "無効",
        description = "Google Discoverページを非表示にし、All Appsを最左ページにします"
    );

    val usesGoogleOverlay: Boolean get() = this == NATIVE_BRIDGE || this == GOOGLE_ONLY
    val showsCustomFeed: Boolean get() = this == NATIVE_BRIDGE || this == FEED_ONLY || this == GOOGLE_APP
    // Retain the legacy enum name so old JSON backups can still be decoded.
    val normalized: DiscoverMode get() = if (this == GOOGLE_APP) FEED_ONLY else this

    companion object {
        fun fromVisibility(google: Boolean, feed: Boolean): DiscoverMode = when {
            google && feed -> NATIVE_BRIDGE
            google -> GOOGLE_ONLY
            feed -> FEED_ONLY
            else -> DISABLED
        }
    }
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
 * ページインジケーターの表示スタイル (Chime Launcher 仕様 12〜16, 32)
 */
@Serializable
enum class IndicatorStyle(
    val displayName: String,
    val englishLabel: String,
    val description: String
) {
    DOTS(
        displayName = "ドット (推奨)",
        englishLabel = "Dots",
        description = "最もミニマルなドット表示。Chime Momentsが最も美しく響きます"
    ),
    ICONS(
        displayName = "アイコン",
        englishLabel = "Icons",
        description = "Discover・Apps・Home・Settingsを小さなアイコンで表現します"
    ),
    TEXT(
        displayName = "テキスト",
        englishLabel = "Text",
        description = "Discover・Apps・1・2・Settingsなどの短いテキストで表示します"
    )
}

/**
 * Return Chime の判定間隔 (Chime Launcher 仕様 9.2, 11, 32, 33)
 */
@Serializable
enum class ReturnChimeInterval(
    val minutes: Int,
    val durationMillis: Long,
    val displayName: String,
    val englishLabel: String
) {
    MINUTES_30(
        minutes = 30,
        durationMillis = 30L * 60_000L,
        displayName = "30分",
        englishLabel = "30 minutes"
    ),
    HOURS_1(
        minutes = 60,
        durationMillis = 60L * 60_000L,
        displayName = "1時間",
        englishLabel = "1 hour"
    ),
    HOURS_3(
        minutes = 180,
        durationMillis = 180L * 60_000L,
        displayName = "3時間",
        englishLabel = "3 hours"
    ),
    HOURS_6(
        minutes = 360,
        durationMillis = 360L * 60_000L,
        displayName = "6時間",
        englishLabel = "6 hours"
    )
}

@Serializable
enum class ExpandedDockPosition(val displayName: String) {
    BOTTOM("下"), LEFT("左"), RIGHT("右")
}

/**
 * Chime Launcher 全体の設定モデル (仕様 10.2, 15, 21, 22, 29, Chime Moments 11〜16, 28, 32)
 */
@Serializable
data class LauncherSettings(
    val dockIconCount: Int = 7,
    val expandedDockPosition: ExpandedDockPosition = ExpandedDockPosition.RIGHT,
    val layoutLocked: Boolean = false,
    val compactGridColumns: Int = 5,
    val compactGridRows: Int = 6,
    val expandedGridColumns: Int = 8,
    val expandedGridRows: Int = 6,
    val tinyIconsColumnsCompact: Int = 7,
    val tinyIconsColumnsExpanded: Int = 10,
    val tinyIconsSizeDp: Int = 36,
    val tinyIconsShowLabels: Boolean = false,
    val searchIndexEdgeDistanceDp: Int = 32,
    val swipeDownNotificationEnabled: Boolean = true,
    val galaxyNotificationHistoryTarget: GalaxyNotificationHistoryTarget = GalaxyNotificationHistoryTarget.NOTISTAR,
    val discoverMode: DiscoverMode = DiscoverMode.NATIVE_BRIDGE,
    val allAppsPageEnabled: Boolean = true,
    val disabledFeedCategoryIds: Set<String> = emptySet(),
    val expandedPageLayoutMode: ExpandedPageLayoutMode = ExpandedPageLayoutMode.DUAL_PAGE,
    val allAppsLeftOnlyInExpandedSingle: Boolean = false,
    val indicatorStyle: IndicatorStyle = IndicatorStyle.DOTS,
    val firstChimeEnabled: Boolean = true,
    val returnChimeEnabled: Boolean = true,
    val returnChimeInterval: ReturnChimeInterval = ReturnChimeInterval.HOURS_1,
    val timeChimeEnabled: Boolean = true,
    val chimeSoundEnabled: Boolean = false
) {
    val enabledFeedCategories: List<FeedCategory>
        get() = FeedCategory.entries.filter { it.id !in disabledFeedCategoryIds }

    fun resolveFeedCategory(preferred: FeedCategory?): FeedCategory? =
        preferred?.takeIf { it in enabledFeedCategories } ?: enabledFeedCategories.firstOrNull()

    val effectiveDockIconCount: Int get() = dockIconCount.coerceIn(1, 12)
}

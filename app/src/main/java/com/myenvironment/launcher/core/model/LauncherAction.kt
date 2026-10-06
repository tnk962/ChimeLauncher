package com.myenvironment.launcher.core.model

/**
 * Launcher独自Action定義 (仕様 12, 19, 31, 36)
 */
enum class LauncherAction(
    val actionId: String,
    val title: String,
    val subtitle: String,
    val emojiIcon: String,
    val companionPackageName: String? = null,
    val companionActivityName: String? = null
) {
    SEARCH(
        actionId = "launcher://action/search",
        title = "Launcher 検索",
        subtitle = "アプリ・ショートカット・Webを高速検索",
        emojiIcon = "🔍"
    ),
    NOTIFICATION_SHADE(
        actionId = "launcher://action/notifications_shade",
        title = "通知シェードを開く",
        subtitle = "Android標準の通知パネルを展開",
        emojiIcon = "🔽"
    ),
    HATENA_FEED(
        actionId = "launcher://action/hatena_feed",
        title = "Hatena Discover",
        subtitle = "はてなブックマーク Discover風フィードを起動",
        emojiIcon = "📰",
        companionPackageName = "com.myenvironment.hatena"
    ),
    MY_NOTIFICATIONS(
        actionId = "launcher://action/my_notifications",
        title = "My Notifications (通知履歴)",
        subtitle = "GoodPixelの通知一覧を開く",
        emojiIcon = "🔔",
        companionPackageName = "com.example.goodpixel",
        companionActivityName = "com.example.goodpixel.ui.notilog.NotiLogActivity"
    ),
    SETTINGS(
        actionId = "launcher://action/settings",
        title = "Launcher 設定",
        subtitle = "グリッド・ロック・バックアップ設定を開く",
        emojiIcon = "⚙️"
    ),
    TOGGLE_LOCK(
        actionId = "launcher://action/toggle_lock",
        title = "レイアウトロック切替",
        subtitle = "ホーム画面の誤操作防止ロックをON/OFF",
        emojiIcon = "🔒"
    );

    companion object {
        fun fromActionId(actionId: String): LauncherAction? =
            entries.find { it.actionId == actionId || it.name == actionId }
    }
}

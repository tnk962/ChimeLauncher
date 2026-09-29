package com.myenvironment.launcher.core.feed

import androidx.compose.ui.graphics.ImageBitmap
import com.myenvironment.launcher.core.model.DiscoverMode

/**
 * Discover フィードのカテゴリ
 */
enum class FeedCategory(
    val id: String,
    val label: String,
    val googleNewsTopicUrl: String,
    val rssUrls: List<String>
) {
    TOP(
        id = "top",
        label = "おすすめ",
        googleNewsTopicUrl = "https://news.google.com/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNRFZxYUdjU0FtcGhHZ0pLVUNnQVAB?hl=ja&gl=JP&ceid=JP:ja",
        rssUrls = listOf(
            "https://news.yahoo.co.jp/rss/topics/top-picks.xml",
            "https://news.google.com/rss?hl=ja&gl=JP&ceid=JP:ja"
        )
    ),
    TECHNOLOGY(
        id = "tech",
        label = "テクノロジー",
        googleNewsTopicUrl = "https://news.google.com/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNRGRqTVhZU0FtcGhHZ0pLVUNnQVAB?hl=ja&gl=JP&ceid=JP:ja",
        rssUrls = listOf(
            "https://news.yahoo.co.jp/rss/topics/it.xml",
            "https://news.google.com/rss/headlines/section/topic/TECHNOLOGY?hl=ja&gl=JP&ceid=JP:ja"
        )
    ),
    BUSINESS(
        id = "business",
        label = "ビジネス",
        googleNewsTopicUrl = "https://news.google.com/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNRGx6TVdZU0FtcGhHZ0pLVUNnQVAB?hl=ja&gl=JP&ceid=JP:ja",
        rssUrls = listOf(
            "https://news.yahoo.co.jp/rss/topics/business.xml",
            "https://news.google.com/rss/headlines/section/topic/BUSINESS?hl=ja&gl=JP&ceid=JP:ja"
        )
    ),
    SCIENCE(
        id = "science",
        label = "サイエンス",
        googleNewsTopicUrl = "https://news.google.com/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNRFp0Y1RjU0FtcGhHZ0pLVUNnQVAB?hl=ja&gl=JP&ceid=JP:ja",
        rssUrls = listOf(
            "https://news.yahoo.co.jp/rss/topics/science.xml",
            "https://news.google.com/rss/headlines/section/topic/SCIENCE?hl=ja&gl=JP&ceid=JP:ja"
        )
    )
}

/**
 * Discover ページに表示する記事カードアイテム（画像URL＆記事概要テキスト付き）
 */
data class DiscoverArticle(
    val id: String,
    val title: String,
    val summary: String,
    val sourceName: String,
    val publishedAt: String,
    val url: String,
    val imageUrl: String? = null
)

/**
 * Google Discover 連携の Bridge 境界インターフェース (仕様 27, 28, 29, 40)
 */
interface FeedBridge {
    /** Native Bridge（Lawnfeed互換サービス等）が現在端末上で接続可能か */
    fun isNativeBridgeAvailable(): Boolean

    /** Googleアプリ本体がインストールされているか */
    fun isGoogleAppInstalled(): Boolean

    /** GoogleアプリのDiscover画面（メインActivity）を直接起動する */
    fun openGoogleDiscoverApp(): Boolean

    /** 記事URLを開く */
    fun openArticleUrl(url: String): Boolean

    /** 指定カテゴリのDiscoverフィード記事一覧（画像・要約付き）を非同期取得する */
    suspend fun fetchArticles(category: FeedCategory): Result<List<DiscoverArticle>>

    /** 記事サムネイル画像をメモリキャッシュから同期取得する */
    fun getCachedArticleImage(imageUrl: String): ImageBitmap?

    /** 記事サムネイル画像を非同期取得（またはOGP解決）してキャッシュに格納する */
    suspend fun loadArticleImage(imageUrl: String?, articleUrl: String): ImageBitmap?

    /** 記事のOGP要約テキストをメモリキャッシュから同期取得する */
    fun getCachedArticleSummary(articleUrl: String): String?

    /** 記事のOGP要約テキスト（og:description）を非同期取得してキャッシュに格納する */
    suspend fun loadArticleSummary(articleUrl: String): String?

    /** 左端ページへ到達した際のアクション（設定されたDiscoverModeに応じた処理） */
    fun onLeftmostPageReached(mode: DiscoverMode): Boolean
}

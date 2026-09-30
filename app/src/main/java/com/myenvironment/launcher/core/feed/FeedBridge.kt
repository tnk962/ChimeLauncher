package com.myenvironment.launcher.core.feed

import androidx.compose.ui.graphics.ImageBitmap
import com.myenvironment.launcher.core.model.DiscoverMode

/**
 * Discover フィードのカテゴリ（v0.2.0 刷新: 旧4ジャンルを廃止し、新4ジャンル構成へ変更）
 *
 * 1. Google Discover : Googleのおすすめ・トップニュース記事
 * 2. はてブ 総合     : はてなブックマーク 総合ホットエントリー・新着RSS
 * 3. はてブ テクノロジー: はてなブックマーク テクノロジー(IT) ホットエントリー・新着RSS
 * 4. ビジネス・政治  : はてなブックマーク 政治と経済・社会 ＋ ビジネス・国内政治ニュースRSS
 */
enum class FeedCategory(
    val id: String,
    val label: String,
    val googleNewsTopicUrl: String,
    val rssUrls: List<String>
) {
    GOOGLE_DISCOVER(
        id = "google_discover_v2",
        label = "Google Discover",
        googleNewsTopicUrl = "https://news.google.com/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNRFZxYUdjU0FtcGhHZ0pLVUNnQVAB?hl=ja&gl=JP&ceid=JP:ja",
        rssUrls = listOf(
            "https://news.yahoo.co.jp/rss/topics/top-picks.xml",
            "https://news.google.com/rss?hl=ja&gl=JP&ceid=JP:ja"
        )
    ),
    HATENA_ALL(
        id = "hatena_all_v2",
        label = "はてブ 総合",
        googleNewsTopicUrl = "",
        rssUrls = listOf(
            "https://b.hatena.ne.jp/hotentry.rss",
            "https://b.hatena.ne.jp/entrylist.rss"
        )
    ),
    HATENA_TECH(
        id = "hatena_tech_v2",
        label = "はてブ テクノロジー",
        googleNewsTopicUrl = "",
        rssUrls = listOf(
            "https://b.hatena.ne.jp/hotentry/it.rss",
            "https://b.hatena.ne.jp/entrylist/it.rss"
        )
    ),
    BUSINESS_POLITICS(
        id = "biz_politics_v2",
        label = "ビジネス・政治",
        googleNewsTopicUrl = "",
        rssUrls = listOf(
            "https://b.hatena.ne.jp/hotentry/economics.rss",
            "https://b.hatena.ne.jp/hotentry/social.rss",
            "https://news.yahoo.co.jp/rss/topics/business.xml",
            "https://news.yahoo.co.jp/rss/topics/domestic.xml"
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

    /** メモリ内の全フィード画像・OGPメタデータキャッシュを完全にクリアする */
    fun clearCache()

    /** 左端ページへ到達した際のアクション（設定されたDiscoverModeに応じた処理） */
    fun onLeftmostPageReached(mode: DiscoverMode): Boolean
}

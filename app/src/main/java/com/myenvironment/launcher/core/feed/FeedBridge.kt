package com.myenvironment.launcher.core.feed

import androidx.compose.ui.graphics.ImageBitmap
import com.myenvironment.launcher.core.model.DiscoverMode

/**
 * Discover フィードのカテゴリ（v0.3.0: Googleニュースを排除し、AI・OpenAI / リゼロ・アニメ / はてブ中心の6ジャンル構成）
 *
 * 1. おすすめ (AI・リゼロ): XenoSpectrum・OpenAI/生成AI・リゼロ/アニメ・ITmedia AI+・GIGAZINE等の厳選フィード
 * 2. AI・OpenAI         : XenoSpectrum・ITmedia AI+・Zenn(OpenAI/AI)・はてブ(OpenAI/生成AI)・GIGAZINE
 * 3. リゼロ・アニメ      : はてブ(リゼロ/Re:ゼロ/ラノベ)・アニメ！アニメ！・コミックナタリー・はてブ(アニメとゲーム)・ねとらぼ・4Gamer
 * 4. はてブ 総合         : はてなブックマーク 総合ホットエントリー・新着RSS
 * 5. はてブ テクノロジー : はてなブックマーク テクノロジー(IT) ホットエントリー・新着RSS
 * 6. ビジネス・政治      : はてなブックマーク 政治と経済・社会 ホットエントリー・新着RSS
 */
enum class FeedCategory(
    val id: String,
    val label: String,
    val googleNewsTopicUrl: String,
    val rssUrls: List<String>
) {
    DISCOVER_CURATED(
        id = "discover_curated_v3",
        label = "おすすめ (AI・リゼロ)",
        googleNewsTopicUrl = "",
        rssUrls = listOf(
            "https://xenospectrum.com/feed/",
            "https://b.hatena.ne.jp/q/%E3%83%AA%E3%82%BC%E3%83%AD%20OR%20Re%3A%E3%82%BC%E3%83%AD%20OR%20%E7%95%B0%E4%B8%96%E7%95%8C%20OR%20%E3%82%A2%E3%83%8B%E3%83%A1?mode=rss&sort=recent&users=1",
            "https://b.hatena.ne.jp/q/OpenAI%20OR%20ChatGPT%20OR%20Claude%20OR%20Gemini%20OR%20%E7%94%9F%E6%88%90AI?mode=rss&sort=recent&users=1",
            "https://rss.itmedia.co.jp/rss/2.0/aiplus.xml",
            "https://animeanime.jp/rss/index.rdf",
            "https://natalie.mu/comic/feed/news",
            "https://gigazine.net/news/rss_2.0/"
        )
    ),
    AI_OPENAI(
        id = "ai_openai_v3",
        label = "AI・OpenAI",
        googleNewsTopicUrl = "",
        rssUrls = listOf(
            "https://xenospectrum.com/feed/",
            "https://b.hatena.ne.jp/q/OpenAI%20OR%20ChatGPT%20OR%20GPT%20OR%20Claude%20OR%20Gemini%20OR%20LLM%20OR%20%E7%94%9F%E6%88%90AI?mode=rss&sort=recent&users=1",
            "https://rss.itmedia.co.jp/rss/2.0/aiplus.xml",
            "https://zenn.dev/topics/openai/feed",
            "https://zenn.dev/topics/ai/feed",
            "https://gigazine.net/news/rss_2.0/"
        )
    ),
    ANIME_REZERO(
        id = "anime_rezero_v3",
        label = "リゼロ・アニメ",
        googleNewsTopicUrl = "",
        rssUrls = listOf(
            "https://b.hatena.ne.jp/q/%E3%83%AA%E3%82%BC%E3%83%AD%20OR%20Re%3A%E3%82%BC%E3%83%AD%20OR%20%E9%95%B7%E6%9C%88%E9%81%94%E5%B9%B3?mode=rss&sort=popular",
            "https://b.hatena.ne.jp/q/%E3%83%AA%E3%82%BC%E3%83%AD%20OR%20Re%3A%E3%82%BC%E3%83%AD%20OR%20%E7%95%B0%E4%B8%96%E7%95%8C%20OR%20%E3%83%A9%E3%83%8E%E3%83%99?mode=rss&sort=recent&users=1",
            "https://animeanime.jp/rss/index.rdf",
            "https://natalie.mu/comic/feed/news",
            "https://b.hatena.ne.jp/hotentry/game.rss",
            "https://rss.itmedia.co.jp/rss/2.0/netlab.xml",
            "https://www.4gamer.net/rss/index.xml"
        )
    ),
    HATENA_ALL(
        id = "hatena_all_v3",
        label = "はてブ 総合",
        googleNewsTopicUrl = "",
        rssUrls = listOf(
            "https://b.hatena.ne.jp/hotentry.rss",
            "https://b.hatena.ne.jp/entrylist.rss"
        )
    ),
    HATENA_TECH(
        id = "hatena_tech_v3",
        label = "はてブ IT",
        googleNewsTopicUrl = "",
        rssUrls = listOf(
            "https://b.hatena.ne.jp/hotentry/it.rss",
            "https://b.hatena.ne.jp/entrylist/it.rss"
        )
    ),
    BUSINESS_POLITICS(
        id = "biz_politics_v3",
        label = "ビジネス・政治",
        googleNewsTopicUrl = "",
        rssUrls = listOf(
            "https://b.hatena.ne.jp/hotentry/economics.rss",
            "https://b.hatena.ne.jp/hotentry/social.rss",
            "https://b.hatena.ne.jp/entrylist/economics.rss"
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

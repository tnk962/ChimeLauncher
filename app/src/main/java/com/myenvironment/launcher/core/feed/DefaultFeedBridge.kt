package com.myenvironment.launcher.core.feed

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Process
import android.text.Html
import android.util.Base64
import android.util.LruCache
import android.util.Xml
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.myenvironment.launcher.core.model.DiscoverMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

/**
 * Google Discover Bridge の具象実装 (仕様 27, 28, 29)
 *
 * - Google News トピックフィード（おすすめ・テクノロジー・ビジネス・サイエンス）および主要ニュースRSSから記事を取得。
 * - 記事タイトルだけでなく「どんな記事かパッと分かる要約テキスト (summary)」と「サムネイル画像 (imageUrl)」を取得。
 * - 画像とOGP要約 (og:image / og:description) は LruCache にキャッシュし、高速にカードへ反映する。
 */
class DefaultFeedBridge(
    private val appContext: Context
) : FeedBridge {

    private data class OgpMetadata(
        val imageUrl: String?,
        val description: String?
    )

    private val packageManager = appContext.packageManager
    private val launcherApps = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    // 記事サムネイル画像のメモリキャッシュ
    private val imageCache = object : LruCache<String, ImageBitmap>(120) {}
    // 記事URLごとの OGP メタデータ (og:image / og:description) キャッシュ
    private val ogpMetadataCache = object : LruCache<String, OgpMetadata>(250) {}

    companion object {
        const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
        const val GOOGLE_SEARCH_ACTIVITY = "com.google.android.googlequicksearchbox.SearchActivity"
        const val COMPANION_PACKAGE = "com.myenvironment.chimediscoverbridge"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        private val REGEX_JSLOG_85008 = Regex("""jslog="85008;\s*3:([A-Za-z0-9+/=_\-]+);""")
        private val REGEX_JSLOG_95014 = Regex("""jslog="95014;\s*5:([A-Za-z0-9+/=_\-]+);""")
        private val REGEX_JSON_HTTP_URL = Regex(""""(https?://[^"\\\s]+(?:\\u00[0-9a-fA-F]{2}[^"\\\s]*)*)"""")
        private val REGEX_GNEWS_TITLE = Regex(
            """<a[^>]+(?:data-n-tid="29"|class="[^"]*(?:gPFEn|JtKRv|DY5T1d)[^"]*")[^>]*>(.*?)</a>""",
            RegexOption.DOT_MATCHES_ALL
        )
        private val REGEX_GNEWS_SOURCE = Regex(
            """<(?:div|a|span)[^>]+(?:data-n-tid="9"|class="[^"]*(?:vr1PYe|wEwyrc)[^"]*")[^>]*>(.*?)</(?:div|a|span)>""",
            RegexOption.DOT_MATCHES_ALL
        )
        private val REGEX_GNEWS_TIME = Regex("""<time[^>]*>(.*?)</time>""", RegexOption.DOT_MATCHES_ALL)
        private val REGEX_GNEWS_ATTACHMENT_IMG = Regex(
            """<img[^>]+src="((?:https://news\.google\.com)?/api/attachments/[^"]+)""""
        )
    }

    override fun isNativeBridgeAvailable(): Boolean {
        return try {
            packageManager.getPackageInfo(COMPANION_PACKAGE, 0)
            packageManager.checkSignatures(appContext.packageName, COMPANION_PACKAGE) == android.content.pm.PackageManager.SIGNATURE_MATCH
        } catch (_: Exception) {
            false
        }
    }

    override fun isGoogleAppInstalled(): Boolean {
        return try {
            val user = Process.myUserHandle()
            if (launcherApps.getActivityList(GOOGLE_APP_PACKAGE, user).isNotEmpty()) {
                return true
            }
            packageManager.getPackageInfo(GOOGLE_APP_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun openGoogleDiscoverApp(): Boolean {
        val user = Process.myUserHandle()

        // 1. LauncherApps API 経由で Google アプリのメイン Activity を起動
        try {
            val activities = launcherApps.getActivityList(GOOGLE_APP_PACKAGE, user)
            val target = activities.find {
                it.componentName.className.contains("SearchActivity", ignoreCase = true)
            } ?: activities.firstOrNull()

            if (target != null) {
                launcherApps.startMainActivity(target.componentName, user, null, null)
                return true
            }
        } catch (_: Exception) {
        }

        // 2. PackageManager.getLaunchIntentForPackage で起動
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(GOOGLE_APP_PACKAGE)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            if (launchIntent != null) {
                appContext.startActivity(launchIntent)
                return true
            }
        } catch (_: Exception) {
        }

        // 3. 明示的 ComponentName (SearchActivity) を指定して起動
        try {
            val explicitIntent = Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(GOOGLE_APP_PACKAGE, GOOGLE_SEARCH_ACTIVITY)
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            appContext.startActivity(explicitIntent)
            return true
        } catch (_: Exception) {
        }

        // 4. Google アプリの GOOGLE_SEARCH アクションで起動
        try {
            val searchIntent = Intent("com.google.android.googlequicksearchbox.GOOGLE_SEARCH").apply {
                setPackage(GOOGLE_APP_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(searchIntent)
            return true
        } catch (_: Exception) {
        }

        // 5. Google トップページへフォールバック（Googleニュースには飛ばさない）
        return openArticleUrl("https://www.google.com/")
    }

    override fun openArticleUrl(url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun fetchArticles(category: FeedCategory): Result<List<DiscoverArticle>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                coroutineScope {
                    // 1. Google News トピックページから実記事URL・高解像度サムネイル付き記事リストを取得
                    val googleTopicDeferred = async {
                        runCatching { fetchGoogleNewsTopicHtml(category.googleNewsTopicUrl) }
                            .getOrDefault(emptyList())
                    }

                    // 2. 補助 RSS フィードを並列取得
                    val rssDeferreds = category.rssUrls.map { feedUrl ->
                        async {
                            feedUrl to runCatching { fetchRssFeed(feedUrl) }.getOrDefault(emptyList())
                        }
                    }

                    val googleTopicArticles = googleTopicDeferred.await()
                    val rssResults = rssDeferreds.map { it.await() }

                    val allLists = buildList {
                        if (googleTopicArticles.isNotEmpty()) {
                            add(googleTopicArticles)
                            // Google News トピックHTMLが取得できた場合は、画像なし重複となる news.google.com/rss を除外し、Yahoo!ニュース等の直接RSSのみを統合
                            rssResults.forEach { (feedUrl, list) ->
                                if (!feedUrl.contains("news.google.com/rss") && list.isNotEmpty()) {
                                    add(list)
                                }
                            }
                        } else {
                            // フォールバック時のみ全RSSを使用
                            rssResults.forEach { (_, list) ->
                                if (list.isNotEmpty()) add(list)
                            }
                        }
                    }

                    // Google News トピックがあるカテゴリはそれを主軸にし、RSSのみのカテゴリ（はてブ総合/IT/ビジネス・政治）は均等インターリーブ統合
                    val merged = mutableListOf<DiscoverArticle>()
                    if (allLists.size == 1) {
                        merged.addAll(allLists[0])
                    } else if (googleTopicArticles.isNotEmpty() && allLists.isNotEmpty()) {
                        val primary = allLists[0]
                        val secondaryLists = allLists.drop(1)
                        var secIdx = 0
                        for (i in primary.indices) {
                            merged.add(primary[i])
                            if (i % 3 == 2 && secondaryLists.isNotEmpty()) {
                                for (sList in secondaryLists) {
                                    sList.getOrNull(secIdx)?.let { merged.add(it) }
                                }
                                secIdx++
                            }
                        }
                    } else if (allLists.isNotEmpty()) {
                        val maxLen = allLists.maxOfOrNull { it.size } ?: 0
                        for (i in 0 until maxLen) {
                            for (list in allLists) {
                                list.getOrNull(i)?.let { merged.add(it) }
                            }
                        }
                    }

                    merged
                        .distinctBy { it.url.ifBlank { it.title } }
                        .distinctBy { normalizeTitleKey(it.title) }
                        .take(50)
                }
            }
        }
    }

    private fun normalizeTitleKey(title: String): String {
        return title
            .replace(Regex("【[^】]*】"), "")
            .replace(Regex("（[^）]*）"), "")
            .replace(Regex("\\s+"), "")
            .take(28)
    }

    /**
     * Google News トピックページのHTMLから、配信元記事の直接URL・高解像度サムネイル画像・見出し・配信元・時刻を抽出する
     */
    private fun fetchGoogleNewsTopicHtml(topicUrl: String): List<DiscoverArticle> {
        if (topicUrl.isBlank()) return emptyList()
        val connection = (URL(topicUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Language", "ja-JP,ja;q=0.9")
        }

        val html = try {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }

        return parseGoogleNewsTopicHtml(html)
    }

    private fun parseGoogleNewsTopicHtml(html: String): List<DiscoverArticle> {
        val matches = REGEX_JSLOG_85008.findAll(html).toList()
        if (matches.isEmpty()) return emptyList()

        data class MutableArticleItem(
            val title: String,
            val sourceName: String,
            val publishedAt: String,
            val url: String,
            var imageUrl: String?,
            val relatedLines: MutableList<String> = mutableListOf()
        )

        val mainItems = mutableListOf<MutableArticleItem>()

        for (i in matches.indices) {
            val match = matches[i]
            val startIdx = match.range.first
            val nextStartIdx = if (i + 1 < matches.size) matches[i + 1].range.first else html.length
            val chunkEnd = minOf(nextStartIdx, startIdx + 8500)
            val chunk = html.substring(startIdx, chunkEnd)

            // 直前350文字に UwIKyb (Desktop) または OGnjD / EjqUne (Mobile) がある場合は同一トピック内の関連サブ記事
            val prefixStart = maxOf(0, startIdx - 350)
            val prefix = html.substring(prefixStart, startIdx)
            val isSubItem = prefix.contains("UwIKyb") || prefix.contains("OGnjD") || prefix.contains("EjqUne")

            val rawTitleHtml = REGEX_GNEWS_TITLE.find(chunk)?.groupValues?.getOrNull(1).orEmpty()
            val title = decodeHtmlText(rawTitleHtml)
            if (title.isBlank()) continue

            val rawSourceHtml = REGEX_GNEWS_SOURCE.find(chunk)?.groupValues?.getOrNull(1).orEmpty()
            val sourceName = decodeHtmlText(rawSourceHtml).ifBlank { "Google News" }
            val publishedAt = decodeHtmlText(
                REGEX_GNEWS_TIME.find(chunk)?.groupValues?.getOrNull(1).orEmpty()
            )

            // jslog="95014; 5:<base64>" から配信元の実記事URLをデコード
            val b64Url = REGEX_JSLOG_95014.find(chunk)?.groupValues?.getOrNull(1).orEmpty()
            val articleUrl = decodeUrlsFromBase64(b64Url).firstOrNull()
                ?: continue

            // jslog="85008; 3:<base64>" から配信元の高解像度サムネイル画像URLをデコード
            val b64Img = match.groupValues.getOrNull(1).orEmpty()
            val decodedImgUrl = decodeUrlsFromBase64(b64Img).firstOrNull { isValidArticleImageUrl(it) }
            val fallbackImgUrl = REGEX_GNEWS_ATTACHMENT_IMG.find(chunk)?.groupValues?.getOrNull(1)?.let { src ->
                val cleanSrc = src.replace("&amp;", "&")
                when {
                    cleanSrc.startsWith("http") -> cleanSrc
                    cleanSrc.startsWith("/") -> "https://news.google.com$cleanSrc"
                    else -> null
                }
            }
            val finalImageUrl = decodedImgUrl ?: fallbackImgUrl

            if (isSubItem && mainItems.isNotEmpty()) {
                val parent = mainItems.last()
                if (parent.imageUrl == null && finalImageUrl != null) {
                    parent.imageUrl = finalImageUrl
                }
                if (parent.relatedLines.size < 2 && title != parent.title) {
                    val (cleanSubTitle, cleanSubSource) = splitTitleAndSource(title, sourceName, articleUrl)
                    parent.relatedLines.add("・$cleanSubTitle ($cleanSubSource)")
                }
            } else {
                val (cleanTitle, cleanSource) = splitTitleAndSource(title, sourceName, articleUrl)
                mainItems.add(
                    MutableArticleItem(
                        title = cleanTitle,
                        sourceName = cleanSource,
                        publishedAt = publishedAt,
                        url = articleUrl,
                        imageUrl = finalImageUrl
                    )
                )
            }
        }

        return mainItems.map { item ->
            DiscoverArticle(
                id = UUID.nameUUIDFromBytes(item.url.toByteArray()).toString(),
                title = item.title,
                summary = item.relatedLines.joinToString("\n"),
                sourceName = item.sourceName,
                publishedAt = item.publishedAt,
                url = item.url,
                imageUrl = item.imageUrl
            )
        }
    }

    private fun decodeUrlsFromBase64(b64: String): List<String> {
        if (b64.isBlank()) return emptyList()
        return runCatching {
            val normalized = b64.replace('-', '+').replace('_', '/')
            val padded = when (normalized.length % 4) {
                2 -> "$normalized=="
                3 -> "$normalized="
                else -> normalized
            }
            val decoded = String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
            REGEX_JSON_HTTP_URL.findAll(decoded)
                .mapNotNull { it.groupValues.getOrNull(1) }
                .map { raw ->
                    raw.replace("\\u003d", "=")
                        .replace("\\u0026", "&")
                        .replace("&amp;", "&")
                }
                .toList()
        }.getOrDefault(emptyList())
    }

    private fun decodeHtmlText(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT).toString().trim()
        } catch (_: Exception) {
            raw.replace(Regex("<[^>]*>"), "").trim()
        }
    }

    private fun fetchRssFeed(feedUrl: String): List<DiscoverArticle> {
        val url = URL(feedUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }

        return try {
            connection.inputStream.use { stream ->
                parseFeedXml(stream)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseFeedXml(inputStream: InputStream): List<DiscoverArticle> {
        val parser = Xml.newPullParser()
        runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false) }
        parser.setInput(inputStream, null)

        val articles = mutableListOf<DiscoverArticle>()
        var insideItem = false
        var channelTitle = ""
        var currentTitle = ""
        var currentLink = ""
        var currentSource = ""
        var currentPubDate = ""
        var currentDescription = ""
        var currentEncodedHtml = ""
        var currentImageUrl: String? = null
        var currentBookmarkCount = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val rawName = parser.name.orEmpty().lowercase()
                    val prefix = parser.prefix?.lowercase().orEmpty()
                    val fullTag = if (prefix.isNotBlank() && !rawName.contains(":")) {
                        "$prefix:$rawName"
                    } else {
                        rawName
                    }
                    val localTag = fullTag.substringAfter(':')

                    if (localTag == "item" || localTag == "entry") {
                        insideItem = true
                        currentTitle = ""
                        currentLink = ""
                        currentSource = ""
                        currentPubDate = ""
                        currentDescription = ""
                        currentEncodedHtml = ""
                        currentImageUrl = null
                        currentBookmarkCount = ""
                    } else if (!insideItem && localTag == "title" && channelTitle.isBlank()) {
                        channelTitle = parser.nextText().trim()
                    } else if (insideItem) {
                        when {
                            localTag == "title" -> currentTitle = parser.nextText().trim()
                            localTag == "link" -> {
                                val rel = parser.getAttributeValue(null, "rel")?.lowercase()
                                val type = parser.getAttributeValue(null, "type")?.lowercase()
                                val href = parser.getAttributeValue(null, "href")
                                val text = parser.nextText().trim()
                                if (rel == "enclosure" || type?.startsWith("image/") == true) {
                                    val candidateImg = href ?: text
                                    if (currentImageUrl == null && isValidArticleImageUrl(candidateImg)) {
                                        currentImageUrl = candidateImg
                                    }
                                } else if (currentLink.isBlank()) {
                                    currentLink = text.ifBlank { href.orEmpty() }
                                }
                            }
                            localTag == "source" -> currentSource = parser.nextText().trim()
                            localTag == "pubdate" || fullTag == "dc:date" || localTag == "date" || localTag == "published" || localTag == "updated" -> {
                                val d = parser.nextText().trim()
                                if (currentPubDate.isBlank()) {
                                    currentPubDate = d
                                }
                            }
                            localTag == "description" || localTag == "summary" || localTag == "content" -> {
                                val raw = parser.nextText().trim()
                                if (currentImageUrl == null) {
                                    extractFirstImgUrl(raw)?.let { currentImageUrl = it }
                                }
                                if (raw.isNotBlank() && currentDescription.isBlank()) {
                                    currentDescription = raw
                                }
                            }
                            fullTag == "content:encoded" || localTag == "encoded" -> {
                                val raw = parser.nextText().trim()
                                if (currentImageUrl == null) {
                                    extractFirstImgUrl(raw)?.let { currentImageUrl = it }
                                }
                                currentEncodedHtml = raw
                            }
                            fullTag == "hatena:imageurl" || localTag == "imageurl" -> {
                                val img = parser.nextText().trim()
                                if (isValidArticleImageUrl(img)) {
                                    currentImageUrl = img
                                }
                            }
                            fullTag == "hatena:bookmarkcount" || localTag == "bookmarkcount" -> {
                                currentBookmarkCount = parser.nextText().trim()
                            }
                            fullTag == "media:content" || fullTag == "media:thumbnail" || localTag == "enclosure" || localTag == "thumbnail" -> {
                                val attrUrl = parser.getAttributeValue(null, "url")
                                if (!attrUrl.isNullOrBlank() && isValidArticleImageUrl(attrUrl)) {
                                    currentImageUrl = attrUrl
                                }
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val localTag = parser.name.orEmpty().lowercase().substringAfter(':')
                    if ((localTag == "item" || localTag == "entry") && insideItem) {
                        insideItem = false
                        if (currentTitle.isNotBlank() && currentLink.isNotBlank()) {
                            val effectiveRssSource = when {
                                currentSource.isNotBlank() -> currentSource
                                channelTitle.isNotBlank() && !channelTitle.startsWith("はてなブックマーク") -> channelTitle
                                else -> ""
                            }
                            val (cleanTitle, extractedSource) = splitTitleAndSource(
                                rawTitle = currentTitle,
                                rssSource = effectiveRssSource,
                                articleUrl = currentLink
                            )
                            val rawSummarySource = currentDescription.ifBlank { currentEncodedHtml }
                            val cleanSummary = cleanHtmlSummary(rawSummarySource, cleanTitle, extractedSource)
                            val formattedSource = if (currentBookmarkCount.isNotBlank() && currentBookmarkCount != "0") {
                                "$extractedSource • ${currentBookmarkCount} users"
                            } else {
                                extractedSource
                            }

                            articles.add(
                                DiscoverArticle(
                                    id = UUID.nameUUIDFromBytes(currentLink.toByteArray()).toString(),
                                    title = cleanTitle,
                                    summary = cleanSummary,
                                    sourceName = formattedSource,
                                    publishedAt = formatPubDate(currentPubDate),
                                    url = currentLink,
                                    imageUrl = currentImageUrl
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return articles
    }

    private fun extractFirstImgUrl(html: String): String? {
        val regex = Regex("""<img[^>]+src=["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
        return regex.findAll(html)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .firstOrNull { isValidArticleImageUrl(it) }
    }

    private fun isValidArticleImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http") &&
            !lower.contains("favicon") &&
            !lower.contains("j6_cofbogxhri9im864nl_ligxvsqp2aupskei7z0cnnf") && // Google News 汎用ロゴを除外
            !lower.contains("s.yimg.jp/images/jpnews/cre/common/all/images/fbico_ogp") &&
            !lower.contains("st-hatena.com/?url=") &&
            !lower.contains("b.hatena.ne.jp/entry/image") &&
            !lower.contains("b.hatena.ne.jp/images") &&
            !lower.contains("entry-button") &&
            !lower.contains("pixel") &&
            !lower.endsWith(".gif")
    }

    private fun cleanHtmlSummary(rawHtml: String, title: String, sourceName: String): String {
        if (rawHtml.isBlank()) return ""
        val withoutCite = rawHtml
            .replace(Regex("<cite[^>]*>.*?</cite>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), " ・")
        val plain = try {
            Html.fromHtml(withoutCite, Html.FROM_HTML_MODE_COMPACT).toString()
        } catch (_: Exception) {
            withoutCite.replace(Regex("<[^>]*>"), " ")
        }
        val cleaned = plain
            .replace("\uFFFC", "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removePrefix("・")
            .trim()
            .removePrefix(title)
            .trim()
            .removePrefix(sourceName)
            .trim()
            .removePrefix("-")
            .trim()

        return if (cleaned.length > 140) {
            cleaned.take(140) + "…"
        } else {
            cleaned
        }
    }

    private fun splitTitleAndSource(rawTitle: String, rssSource: String, articleUrl: String): Pair<String, String> {
        if (articleUrl.contains("news.google.com")) {
            val lastDash = rawTitle.lastIndexOf(" - ")
            if (lastDash > 0 && lastDash < rawTitle.length - 2) {
                val mainTitle = rawTitle.substring(0, lastDash).trim()
                val tailSource = rawTitle.substring(lastDash + 3).trim()
                return mainTitle to (rssSource.ifBlank { tailSource })
            }
        }
        val host = runCatching {
            URI(articleUrl).host?.removePrefix("www.")
        }.getOrNull().orEmpty()
        val friendlyHost = when {
            host.contains("xenospectrum.com") -> "xenospectrum.com"
            host.contains("itmedia.co.jp") -> "ITmedia"
            host.contains("zenn.dev") -> "Zenn"
            host.contains("gigazine.net") -> "GIGAZINE"
            host.contains("animeanime.jp") -> "アニメ！アニメ！"
            host.contains("natalie.mu") -> "コミックナタリー"
            host.contains("4gamer.net") -> "4Gamer.net"
            host.contains("dengekionline.com") -> "電撃オンライン"
            host.contains("famitsu.com") -> "ファミ通.com"
            host.contains("automaton-media.com") -> "AUTOMATON"
            host.contains("qiita.com") -> "Qiita"
            host.contains("note.com") -> "note"
            host.contains("re-zero-anime.jp") -> "Re:ゼロ公式"
            host.contains("news.yahoo.co.jp") -> "Yahoo!ニュース"
            host.isNotBlank() -> host
            else -> "Discover"
        }
        val finalSource = when {
            host.contains("xenospectrum.com") -> "xenospectrum.com"
            rssSource.isNotBlank() && rssSource.length <= 28 -> rssSource
            else -> friendlyHost
        }
        return rawTitle to finalSource
    }

    private fun formatPubDate(rawDate: String): String {
        if (rawDate.isBlank()) return ""
        if (rawDate.contains("T") && rawDate.length >= 16) {
            return rawDate.substring(5, 16).replace("-", "/").replace("T", " ")
        }
        return rawDate
            .removeSuffix("GMT")
            .removeSuffix("+0000")
            .trim()
            .take(16)
    }

    override fun getCachedArticleImage(imageUrl: String): ImageBitmap? {
        if (imageUrl.isBlank()) return null
        synchronized(imageCache) {
            return imageCache.get(imageUrl)
        }
    }

    override suspend fun loadArticleImage(imageUrl: String?, articleUrl: String): ImageBitmap? {
        return withContext(Dispatchers.IO) {
            val targetImgUrl = if (!imageUrl.isNullOrBlank()) {
                imageUrl
            } else {
                resolveOgpMetadata(articleUrl)?.imageUrl
            } ?: return@withContext null

            getCachedArticleImage(targetImgUrl)?.let { return@withContext it }

            runCatching {
                val connection = (URL(targetImgUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                try {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                    }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    bmp?.asImageBitmap()?.also { imageBitmap ->
                        synchronized(imageCache) {
                            imageCache.put(targetImgUrl, imageBitmap)
                            if (imageUrl == null && articleUrl.isNotBlank()) {
                                imageCache.put(articleUrl, imageBitmap)
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }

    override fun getCachedArticleSummary(articleUrl: String): String? {
        if (articleUrl.isBlank()) return null
        synchronized(ogpMetadataCache) {
            return ogpMetadataCache.get(articleUrl)?.description?.takeIf { it.isNotBlank() }
        }
    }

    override suspend fun loadArticleSummary(articleUrl: String): String? {
        return withContext(Dispatchers.IO) {
            resolveOgpMetadata(articleUrl)?.description?.takeIf { it.isNotBlank() }
        }
    }

    override fun clearCache() {
        synchronized(imageCache) {
            imageCache.evictAll()
        }
        synchronized(ogpMetadataCache) {
            ogpMetadataCache.evictAll()
        }
    }

    /**
     * 記事URLのHTML先頭から OGP (og:image / og:description) を1回の軽量リクエストで同時に解決・キャッシュする
     */
    private fun resolveOgpMetadata(articleUrl: String): OgpMetadata? {
        if (articleUrl.isBlank() || articleUrl.contains("news.google.com/rss/articles")) {
            return null
        }
        synchronized(ogpMetadataCache) {
            ogpMetadataCache.get(articleUrl)?.let { return it }
        }

        val fetched = runCatching {
            val conn = (URL(articleUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                val headChunk = ByteArray(28 * 1024)
                val readLen = conn.inputStream.use { stream ->
                    var total = 0
                    while (total < headChunk.size) {
                        val n = stream.read(headChunk, total, headChunk.size - total)
                        if (n <= 0) break
                        total += n
                    }
                    total
                }
                if (readLen <= 0) return@runCatching OgpMetadata(null, null)
                val html = String(headChunk, 0, readLen, Charsets.UTF_8)

                val imgUrl = extractMetaContent(html, "og:image|twitter:image")
                    ?.replace("&amp;", "&")
                    ?.takeIf { isValidArticleImageUrl(it) }

                val rawDesc = extractMetaContent(html, "og:description|twitter:description|description")
                val cleanDesc = rawDesc?.let { desc ->
                    decodeHtmlText(desc)
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .let { s -> if (s.length > 140) s.take(140) + "…" else s }
                }?.takeIf { it.length >= 12 }

                OgpMetadata(imageUrl = imgUrl, description = cleanDesc)
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(OgpMetadata(null, null))

        synchronized(ogpMetadataCache) {
            ogpMetadataCache.put(articleUrl, fetched)
        }
        return fetched
    }

    private fun extractMetaContent(html: String, keyPattern: String): String? {
        val regex1 = Regex(
            """<meta[^>]+(?:property|name)=["'](?:$keyPattern)["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        val regex2 = Regex(
            """<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["'](?:$keyPattern)["']""",
            RegexOption.IGNORE_CASE
        )
        return (regex1.find(html) ?: regex2.find(html))?.groupValues?.getOrNull(1)
    }

    override fun onLeftmostPageReached(mode: DiscoverMode): Boolean {
        return if (mode == DiscoverMode.GOOGLE_APP) {
            openGoogleDiscoverApp()
        } else {
            false
        }
    }
}

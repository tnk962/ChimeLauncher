package com.myenvironment.launcher.ui.discover

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myenvironment.launcher.core.feed.DiscoverArticle
import com.myenvironment.launcher.core.feed.FeedBridge
import com.myenvironment.launcher.core.feed.FeedCategory
import com.myenvironment.launcher.core.feed.overlay.OverlayDragSession
import com.myenvironment.launcher.core.model.DiscoverMode
import com.myenvironment.launcher.core.model.LauncherSettings
import com.myenvironment.launcher.core.model.LauncherAction
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Page -2: Discover & はてなブックマーク 統合フィードページ (仕様 27, 28, 29 / v0.5.0)
 *
 * - Googleニュース（新聞系見出し）を排除し、AI・OpenAI（XenoSpectrum等）やリゼロ・アニメ・はてブを中心とした6ジャンルを下部チップで切り替え可能。
 * - 各記事カードにサムネイル画像と要約スニペットを表示し、どんな記事か一目で分かるUI。
 * - 独自フィード端のドラッグをCompanion経由のGoogle Discover overlayへ渡す。
 */
@Composable
fun DiscoverPage(
    settings: LauncherSettings,
    onOpenSettings: () -> Unit,
    feedBridge: FeedBridge,
    onOpenArticle: (String) -> Unit,
    isSettledOnDiscover: Boolean = true,
    onSelectDiscoverMode: (DiscoverMode) -> Unit,
    onOpenGoogleApp: () -> Unit,
    onTriggerAction: (LauncherAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val discoverMode = settings.discoverMode
    val enabledCategories = settings.enabledFeedCategories
    val googleOverlay = LocalGoogleOverlayClient.current
    val overlayState = googleOverlay?.state?.collectAsStateWithLifecycle()?.value
    val overlayEnabled = discoverMode.usesGoogleOverlay
    val currentOverlayEnabled by rememberUpdatedState(overlayEnabled)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    var preferredCategory by remember { mutableStateOf(FeedCategory.DISCOVER_CURATED) }
    val selectedCategory = settings.resolveFeedCategory(preferredCategory)
    val articlesCache = remember { mutableStateMapOf<FeedCategory, List<DiscoverArticle>>() }
    val loading = remember { mutableStateMapOf<FeedCategory, Boolean>() }
    val errors = remember { mutableStateMapOf<FeedCategory, String>() }
    var refreshGeneration by remember { mutableIntStateOf(0) }
    var showModeDialog by remember { mutableStateOf(false) }
    var hasClearedInitialCache by remember { mutableStateOf(false) }
    val currentSettled by rememberUpdatedState(isSettledOnDiscover)

    fun refreshCategory() {
        selectedCategory?.let { category ->
            feedBridge.clearCache()
            articlesCache.remove(category)
            refreshGeneration++
        }
    }

    // The effect cancels an in-flight request when the page/category is disabled or left.
    LaunchedEffect(selectedCategory, discoverMode, isSettledOnDiscover, refreshGeneration) {
        val category = selectedCategory ?: return@LaunchedEffect
        if (!discoverMode.showsCustomFeed || !isSettledOnDiscover) return@LaunchedEffect
        if (!hasClearedInitialCache) {
            feedBridge.clearCache()
            articlesCache.clear()
            hasClearedInitialCache = true
        }
        if (articlesCache.containsKey(category)) return@LaunchedEffect
        loading[category] = true
        errors.remove(category)
        try {
            val result = feedBridge.fetchArticles(category)
            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = { articlesCache[category] = it },
                onFailure = { errors[category] = "フィードを取得できませんでした (${it.localizedMessage ?: "通信エラー"})" }
            )
        } finally {
            loading[category] = false
        }
    }

    val currentArticles = articlesCache[selectedCategory].orEmpty()
    val isLoading = loading[selectedCategory] == true
    val errorMessage = errors[selectedCategory]

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(googleOverlay, touchSlop) {
                val bottomChipExclusionPx = 76.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // Discoverページに到達完了している状態で、かつ下部ジャンルチップバー以外の領域から始まったスワイプのみ対象
                    if (!currentSettled || down.position.y > size.height - bottomChipExclusionPx) {
                        return@awaitEachGesture
                    }
                    var overlayDragging = false
                    val drag = OverlayDragSession(touchSlop, googleOverlay?.revealWidth ?: size.width.toFloat())
                    try {
                      while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = change.position.x - change.previousPosition.x
                        val dy = change.position.y - change.previousPosition.y

                        val progress = if (currentOverlayEnabled) drag.move(dx, dy) {
                            googleOverlay?.beginScroll() == true
                        } else null
                        if (progress != null) {
                            overlayDragging = true
                            change.consume()
                            googleOverlay?.scroll(progress)
                        }
                      }
                    } finally {
                        if (overlayDragging) googleOverlay?.endScroll()
                    }
                }
            }
            .padding(horizontal = 10.dp)
    ) {
        if (overlayEnabled) {
            Text(
                text = overlayState?.message ?: "Chime Discover Companionをインストールしてください",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
        }
        // 1. コンパクトヘッダーバー（タイトル ＋ Google App起動ボタン ＋ はてブ起動ボタン ＋ 更新 ＋ 設定）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Discover",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenGoogleApp,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Google App", fontSize = 11.sp)
                }

                FilledTonalButton(
                    onClick = { onTriggerAction(LauncherAction.HATENA_FEED) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("はてブ", fontSize = 11.sp)
                }

                IconButton(
                    onClick = { refreshCategory() },
                    enabled = selectedCategory != null,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "キャッシュをクリアして更新",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { showModeDialog = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Discover設定",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 2. 記事フィード一覧 (縦スクロール)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                selectedCategory == null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("表示するフィードがありません。", color = Color.White)
                        Text("設定画面の「フィード設定」で表示したいジャンルをONにしてください。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onOpenSettings) { Text("設定を開く") }
                    }
                }
                isLoading && currentArticles.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Discover フィードを読み込み中...",
                            color = Color(0xFFBDC1C6),
                            fontSize = 13.sp
                        )
                    }
                }

                errorMessage != null && currentArticles.isEmpty() -> {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xDD1A1D24)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = errorMessage ?: "読み込みエラー",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { refreshCategory() }
                                ) {
                                    Text("再試行")
                                }
                                FilledTonalButton(onClick = onOpenGoogleApp) {
                                    Text("Googleアプリを開く")
                                }
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = currentArticles,
                            key = { it.id },
                            contentType = { "discover_article_card" }
                        ) { article ->
                            DiscoverArticleCard(
                                article = article,
                                feedBridge = feedBridge,
                                onClick = { onOpenArticle(article.url) }
                            )
                        }
                    }
                }
            }
        }

        // 3. 画面下部：ジャンル選択チップバー（片手で操作しやすいよう下部に配置）
        Surface(
            color = Color(0xCC16181E),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 4.dp)
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(enabledCategories, key = { it.id }) { category ->
                    val selected = category == selectedCategory
                    FilterChip(
                        selected = selected,
                        onClick = { preferredCategory = category },
                        label = {
                            Text(
                                text = category.label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0x88242832),
                            labelColor = Color(0xFFE8EAED),
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
    }

    // Discover Mode 切り替えダイアログ (仕様 29)
    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            title = {
                Text("フィード表示", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Google Discover" to true, "独自フィード" to false).forEach { (label, isGoogle) ->
                        val checked = if (isGoogle) discoverMode.usesGoogleOverlay else discoverMode.showsCustomFeed
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        ) {
                            Text(label, modifier = Modifier.weight(1f))
                            Switch(checked = checked, onCheckedChange = { enabled ->
                                onSelectDiscoverMode(DiscoverMode.fromVisibility(
                                    google = if (isGoogle) enabled else discoverMode.usesGoogleOverlay,
                                    feed = if (isGoogle) discoverMode.showsCustomFeed else enabled
                                ))
                            })
                        }
                    }
                    Text("Google DiscoverにはChime Discover Companionが必要です。")
                }
            },
            confirmButton = {
                TextButton(onClick = { showModeDialog = false }) {
                    Text("閉じる")
                }
            }
        )
    }
}

@Composable
private fun DiscoverArticleCard(
    article: DiscoverArticle,
    feedBridge: FeedBridge,
    onClick: () -> Unit
) {
    val initialCachedImg = remember(article.imageUrl, article.url) {
        article.imageUrl?.let { feedBridge.getCachedArticleImage(it) }
            ?: feedBridge.getCachedArticleImage(article.url)
    }
    var thumbnailBitmap by remember(article.id) { mutableStateOf<ImageBitmap?>(initialCachedImg) }

    val cachedOgpSummary = remember(article.id, article.url) {
        feedBridge.getCachedArticleSummary(article.url)
    }
    var summaryText by remember(article.id) {
        mutableStateOf(cachedOgpSummary ?: article.summary)
    }

    LaunchedEffect(article.id, article.imageUrl, article.url) {
        if (thumbnailBitmap == null) {
            thumbnailBitmap = feedBridge.loadArticleImage(article.imageUrl, article.url)
        }
        if (cachedOgpSummary == null) {
            val resolved = feedBridge.loadArticleSummary(article.url)
            if (!resolved.isNullOrBlank()) {
                summaryText = resolved
            }
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xE01A1D24)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 上段: 配信元メディア & 日時
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = article.sourceName,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (article.publishedAt.isNotBlank()) {
                    Text(
                        text = article.publishedAt,
                        color = Color(0xFF9AA0A6),
                        fontSize = 11.sp
                    )
                }
            }

            // 中段: 記事タイトル + 要約スニペット + サムネイル画像 (右側)
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = article.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (summaryText.isNotBlank()) {
                        Text(
                            text = summaryText,
                            color = Color(0xFFBDC1C6),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val bmp = thumbnailBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = article.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 96.dp, height = 76.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF282C34))
                    )
                }
            }
        }
    }
}

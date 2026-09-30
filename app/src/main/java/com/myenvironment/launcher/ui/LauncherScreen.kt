package com.myenvironment.launcher.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pages
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myenvironment.launcher.MainActivity
import com.myenvironment.launcher.core.model.DiscoverMode
import com.myenvironment.launcher.core.model.ExpandedPageLayoutMode
import com.myenvironment.launcher.core.model.GridPosition
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LauncherPage
import com.myenvironment.launcher.ui.adaptive.AdaptiveLayoutSpec
import com.myenvironment.launcher.ui.adaptive.DockPlacement
import com.myenvironment.launcher.ui.adaptive.rememberAdaptiveLayoutSpec
import com.myenvironment.launcher.ui.allapps.AllAppsTinyPage
import com.myenvironment.launcher.ui.components.LauncherItemGraphic
import com.myenvironment.launcher.ui.discover.DiscoverPage
import com.myenvironment.launcher.ui.discover.LocalGoogleOverlayClient
import com.myenvironment.launcher.ui.dock.AdaptiveDock
import com.myenvironment.launcher.ui.editor.HomeEditSheet
import com.myenvironment.launcher.ui.editor.ItemPickerDialog
import com.myenvironment.launcher.ui.editor.LockedAlertDialog
import com.myenvironment.launcher.ui.editor.PageManagerDialog
import com.myenvironment.launcher.ui.editor.WidgetResizeDialog
import com.myenvironment.launcher.ui.home.CrossPageDragState
import com.myenvironment.launcher.ui.home.HomeGridPage
import com.myenvironment.launcher.ui.home.HomePageGridMetrics
import com.myenvironment.launcher.ui.home.MissingAppDialog
import com.myenvironment.launcher.ui.indicator.PageIndicatorBar
import com.myenvironment.launcher.ui.search.SearchOverlay
import com.myenvironment.launcher.ui.settings.JsonBackupPreviewDialog
import com.myenvironment.launcher.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 固定特殊ページ（Discover / All Apps / 設定）以外の、アイコン・ウィジェットを自由配置できるホームページかどうか
 */
internal fun LauncherPage.isEditableHomePage(): Boolean {
    return id != LauncherPage.PAGE_ID_DISCOVER &&
        id != LauncherPage.PAGE_ID_ALL_APPS &&
        id != LauncherPage.PAGE_ID_SETTINGS
}

/**
 * Fold展開時（左右2ページ見開きモード）のページャースロットモデル
 *
 * - Discover ページおよび 設定 ページは常に 1ページ全画面表示固定 (SingleFull)
 * - All Apps / HOME / ユーザー追加ページは 左右2ページ見開き表示 (DualSpread)
 */
internal sealed interface ExpandedPagerSlot {
    val primaryPage: LauncherPage
    val visiblePageIds: Set<String>

    data class SingleFull(
        val page: LauncherPage
    ) : ExpandedPagerSlot {
        override val primaryPage: LauncherPage get() = page
        override val visiblePageIds: Set<String> get() = setOf(page.id)
    }

    data class DualSpread(
        val leftPage: LauncherPage,
        val rightPage: LauncherPage
    ) : ExpandedPagerSlot {
        override val primaryPage: LauncherPage get() = rightPage
        override val visiblePageIds: Set<String> get() = setOf(leftPage.id, rightPage.id)
    }
}

internal fun buildExpandedDualSlots(pages: List<LauncherPage>): List<ExpandedPagerSlot> {
    if (pages.isEmpty()) {
        return listOf(ExpandedPagerSlot.SingleFull(LauncherPage.FIXED_HOME))
    }

    val slots = mutableListOf<ExpandedPagerSlot>()

    // 1. Discover ページは常に 1ページ全画面表示固定
    pages.filter { it.id == LauncherPage.PAGE_ID_DISCOVER }.forEach { discoverPage ->
        slots.add(ExpandedPagerSlot.SingleFull(discoverPage))
    }

    // 2. All Apps / HOME / ユーザー追加ページは左右2ページ見開きペアにする
    val middlePages = pages.filter {
        it.id != LauncherPage.PAGE_ID_DISCOVER && it.id != LauncherPage.PAGE_ID_SETTINGS
    }
    if (middlePages.size == 1) {
        slots.add(ExpandedPagerSlot.SingleFull(middlePages[0]))
    } else if (middlePages.size >= 2) {
        for (i in 0 until middlePages.size - 1) {
            slots.add(
                ExpandedPagerSlot.DualSpread(
                    leftPage = middlePages[i],
                    rightPage = middlePages[i + 1]
                )
            )
        }
    }

    // 3. 設定ページは常に 1ページ全画面表示固定
    pages.filter { it.id == LauncherPage.PAGE_ID_SETTINGS }.forEach { settingsPage ->
        slots.add(ExpandedPagerSlot.SingleFull(settingsPage))
    }

    return slots
}

internal fun resolveExpandedDualSlotIndex(
    slots: List<ExpandedPagerSlot>,
    targetPageId: String
): Int {
    if (slots.isEmpty()) return 0
    val primaryMatch = slots.indexOfFirst { it.primaryPage.id == targetPageId }
    if (primaryMatch >= 0) return primaryMatch
    val visibleMatch = slots.indexOfFirst { it.visiblePageIds.contains(targetPageId) }
    if (visibleMatch >= 0) return visibleMatch
    val homeMatch = slots.indexOfFirst { it.visiblePageIds.contains(LauncherPage.PAGE_ID_HOME) }
    return if (homeMatch >= 0) homeMatch else 0
}

/**
 * Chime Launcher ルート画面
 *
 * - ページ構成: [Discover] [All Apps] [HOME] [Page 2...] [設定 (一番右)]
 * - Fold閉 (Compact): 1ページ表示 + 下部 Bottom Dock
 * - Fold開 (Expanded):
 *   - DUAL_PAGE (デフォルト):
 *     - Discover と 設定 は 1ページ全画面表示固定
 *     - All Apps / HOME / 追加ページ は 左右2ページ見開き同時表示 + 右端 Right Dock
 *   - SINGLE_FULL: 開いた時も全ページを1ページ全画面表示 + 右端 Right Dock
 */
@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val googleOverlay = LocalGoogleOverlayClient.current
    val overlayState = googleOverlay?.state?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(uiState.settings.discoverMode, googleOverlay) {
        googleOverlay?.setEnabled(uiState.settings.discoverMode == DiscoverMode.NATIVE_BRIDGE)
    }
    val context = LocalContext.current
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // AppWidget バインド許可ダイアログ用 ActivityResultLauncher
    val widgetBindLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onWidgetBindActivityResult(result.resultCode == Activity.RESULT_OK)
    }

    // AppWidget バインド・設定 Activity 起動イベント購読
    LaunchedEffect(viewModel, context) {
        viewModel.widgetSystemEvents.collect { event ->
            when (event) {
                is WidgetSystemEvent.RequestBindAppWidget -> {
                    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, event.appWidgetId)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, event.provider)
                    }
                    runCatching {
                        widgetBindLauncher.launch(intent)
                    }.onFailure {
                        viewModel.onWidgetBindActivityResult(false)
                    }
                }

                is WidgetSystemEvent.RequestConfigureAppWidget -> {
                    val activity = context as? Activity
                    if (activity != null) {
                        runCatching {
                            viewModel.widgetHostManager.appWidgetHost.startAppWidgetConfigureActivityForResult(
                                activity,
                                event.appWidgetId,
                                0,
                                MainActivity.REQUEST_CODE_CONFIGURE_APPWIDGET,
                                null
                            )
                        }.onFailure {
                            // 設定 Activity の起動に失敗した場合もウィジェット配置自体は継続できるようにする
                            viewModel.onWidgetConfigureActivityResult(true)
                        }
                    } else {
                        viewModel.onWidgetConfigureActivityResult(true)
                    }
                }
            }
        }
    }

    val adaptiveSpec = rememberAdaptiveLayoutSpec(uiState.settings)
    val pages = uiState.pages
    val editableHomePages = remember(pages) { pages.filter { it.isEditableHomePage() } }
    val homePageIndex = uiState.homePageIndex

    // Fold展開時に2ページ見開き表示を行うかどうか
    val isDualPageMode = adaptiveSpec.isExpanded &&
        uiState.settings.expandedPageLayoutMode == ExpandedPageLayoutMode.DUAL_PAGE &&
        pages.size >= 2

    // 現在フォーカスしているメインページのID（閉じた時の表示ページ ＝ 開いた時のメインページ）
    var focusedPageId by rememberSaveable { mutableStateOf(LauncherPage.PAGE_ID_HOME) }

    val focusedPageIndex = remember(pages, focusedPageId, homePageIndex) {
        pages.indexOfFirst { it.id == focusedPageId }.takeIf { it >= 0 } ?: homePageIndex
    }

    // 1ページ表示用 PagerState
    val singlePagerState = rememberPagerState(
        initialPage = focusedPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
        pageCount = { pages.size }
    )

    // 2ページ見開き表示用スロット一覧（Discover・設定は1ページ全画面、中間ページは見開き2ページ）
    val dualSlots = remember(pages) { buildExpandedDualSlots(pages) }
    val initialDualSlotIndex = remember(dualSlots, focusedPageId) {
        resolveExpandedDualSlotIndex(dualSlots, focusedPageId)
    }
    val dualPagerState = rememberPagerState(
        initialPage = initialDualSlotIndex.coerceIn(0, (dualSlots.size - 1).coerceAtLeast(0)),
        pageCount = { dualSlots.size }
    )

    // --- ページ跨ぎドラッグ＆ドロップ状態と各ページの画面座標メトリクス ---
    var activeDragState by remember { mutableStateOf<CrossPageDragState?>(null) }
    val pageMetricsMap = remember { mutableStateMapOf<String, HomePageGridMetrics>() }
    var rootBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var pagerBoundsInRoot by remember { mutableStateOf(Rect.Zero) }

    // 編集モード終了時はドラッグ状態をクリア
    LaunchedEffect(uiState.overlay.isEditMode) {
        if (!uiState.overlay.isEditMode) {
            activeDragState = null
        }
    }

    /**
     * 現在ドラッグ中のアイテムがドロップされる対象ページID・メトリクス・セル座標を算出する。
     */
    fun resolveDropTarget(drag: CrossPageDragState): Triple<String, HomePageGridMetrics, GridPosition>? {
        val sourceMetrics = pageMetricsMap[drag.sourcePageId]
        if (isDualPageMode) {
            val slot = dualSlots.getOrNull(dualPagerState.currentPage.coerceIn(0, dualSlots.lastIndex))
            val candidatePageIds = slot?.visiblePageIds.orEmpty().filter { pageId ->
                pages.find { it.id == pageId }?.isEditableHomePage() == true
            }
            val centerX = drag.topLeftInRoot.x + drag.itemWidthPx / 2f
            val targetId = candidatePageIds.minByOrNull { pageId ->
                val b = pageMetricsMap[pageId]?.boundsInRoot
                if (b == null) {
                    Float.MAX_VALUE
                } else if (centerX in b.left..b.right) {
                    0f
                } else {
                    min(abs(centerX - b.left), abs(centerX - b.right))
                }
            } ?: candidatePageIds.firstOrNull() ?: drag.sourcePageId

            val metrics = pageMetricsMap[targetId] ?: sourceMetrics ?: return null
            val cell = metrics.resolveDropCell(drag.topLeftInRoot, drag.spanX, drag.spanY)
            return Triple(targetId, metrics, cell)
        } else {
            val currentP = pages.getOrNull(singlePagerState.currentPage.coerceIn(0, pages.lastIndex))
            val targetId = if (currentP != null && currentP.isEditableHomePage()) {
                currentP.id
            } else {
                drag.sourcePageId
            }
            val rawMetrics = pageMetricsMap[targetId] ?: sourceMetrics ?: return null
            // ページ遷移直後で boundsInRoot がスクロール前の座標だった場合は pagerBoundsInRoot を基準に補正
            val effectiveMetrics = if (pagerBoundsInRoot.width > 0f) {
                rawMetrics.copy(boundsInRoot = pagerBoundsInRoot)
            } else {
                rawMetrics
            }
            val cell = effectiveMetrics.resolveDropCell(drag.topLeftInRoot, drag.spanX, drag.spanY)
            return Triple(targetId, effectiveMetrics, cell)
        }
    }

    val currentDropTarget = remember(
        activeDragState,
        isDualPageMode,
        singlePagerState.currentPage,
        dualPagerState.currentPage,
        pagerBoundsInRoot
    ) {
        activeDragState?.let { resolveDropTarget(it) }
    }

    // --- ドラッグ中の画面左右端ホバーによるページ自動遷移 (約0.65秒キープで隣接ページへ遷移) ---
    val edgeHoverZonePx = with(density) { 54.dp.toPx() }
    val currentDrag = activeDragState
    val edgeHoverDirection: Int = when {
        currentDrag == null || pagerBoundsInRoot.width <= 0f -> 0
        currentDrag.fingerInRoot.x <= pagerBoundsInRoot.left + edgeHoverZonePx -> -1
        currentDrag.fingerInRoot.x >= pagerBoundsInRoot.right - edgeHoverZonePx -> 1
        else -> 0
    }

    // 現在のホバー方向で遷移可能なページがあるか（または右端で新規ページ作成可能か）を判定
    val edgeTransitionHint: String? = remember(
        edgeHoverDirection,
        isDualPageMode,
        singlePagerState.currentPage,
        dualPagerState.currentPage,
        pages,
        dualSlots
    ) {
        if (edgeHoverDirection == 0) {
            null
        } else if (isDualPageMode) {
            val curSlotIdx = dualPagerState.currentPage
            if (edgeHoverDirection < 0) {
                val prevSlot = dualSlots.getOrNull(curSlotIdx - 1)
                val hasHome = prevSlot?.visiblePageIds?.any { id ->
                    pages.find { it.id == id }?.isEditableHomePage() == true
                } == true
                if (hasHome) "◀ そのままキープで左のページへ" else null
            } else {
                val nextSlot = dualSlots.getOrNull(curSlotIdx + 1)
                val hasHome = nextSlot?.visiblePageIds?.any { id ->
                    pages.find { it.id == id }?.isEditableHomePage() == true
                } == true
                if (hasHome) "そのままキープで右のページへ ▶" else "＋ そのままキープで新規ページ作成 ▶"
            }
        } else {
            val curIdx = singlePagerState.currentPage
            if (edgeHoverDirection < 0) {
                val prevPage = pages.getOrNull(curIdx - 1)?.takeIf { it.isEditableHomePage() }
                if (prevPage != null) "◀ そのままキープで「${prevPage.name}」へ" else null
            } else {
                val nextPage = pages.getOrNull(curIdx + 1)
                if (nextPage != null && nextPage.isEditableHomePage()) {
                    "そのままキープで「${nextPage.name}」へ ▶"
                } else {
                    "＋ そのままキープで新規ページ作成 ▶"
                }
            }
        }
    }

    LaunchedEffect(
        activeDragState != null,
        edgeHoverDirection,
        isDualPageMode,
        singlePagerState.currentPage,
        dualPagerState.currentPage
    ) {
        if (activeDragState == null || edgeHoverDirection == 0 || edgeTransitionHint == null) {
            return@LaunchedEffect
        }
        // 画面端で約0.65秒ホバーし続けたらページを遷移する
        delay(650L)
        if (activeDragState == null) return@LaunchedEffect

        if (isDualPageMode) {
            val curSlotIdx = dualPagerState.currentPage
            if (edgeHoverDirection < 0) {
                val targetSlotIdx = curSlotIdx - 1
                val prevSlot = dualSlots.getOrNull(targetSlotIdx)
                val hasHome = prevSlot?.visiblePageIds?.any { id ->
                    pages.find { it.id == id }?.isEditableHomePage() == true
                } == true
                if (hasHome) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    dualPagerState.animateScrollToPage(targetSlotIdx)
                }
            } else {
                val targetSlotIdx = curSlotIdx + 1
                val nextSlot = dualSlots.getOrNull(targetSlotIdx)
                val hasHome = nextSlot?.visiblePageIds?.any { id ->
                    pages.find { it.id == id }?.isEditableHomePage() == true
                } == true
                if (hasHome) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    dualPagerState.animateScrollToPage(targetSlotIdx)
                } else {
                    // 右端の最終ページでさらに右端ホバーした場合は新規ページを作成して遷移
                    val created = viewModel.createUserPageForDrag()
                    if (created != null) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        focusedPageId = created.id
                        delay(80L)
                        val updatedSlots = buildExpandedDualSlots(viewModel.uiState.value.pages)
                        val newSlotIdx = resolveExpandedDualSlotIndex(updatedSlots, created.id)
                        dualPagerState.animateScrollToPage(newSlotIdx)
                    }
                }
            }
        } else {
            val curIdx = singlePagerState.currentPage
            if (edgeHoverDirection < 0) {
                val targetIdx = curIdx - 1
                val prevPage = pages.getOrNull(targetIdx)?.takeIf { it.isEditableHomePage() }
                if (prevPage != null) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    singlePagerState.animateScrollToPage(targetIdx)
                }
            } else {
                val targetIdx = curIdx + 1
                val nextPage = pages.getOrNull(targetIdx)
                if (nextPage != null && nextPage.isEditableHomePage()) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    singlePagerState.animateScrollToPage(targetIdx)
                } else {
                    // 右端の最終ホームページでさらに右端ホバーした場合は新規ページを作成して遷移
                    val created = viewModel.createUserPageForDrag()
                    if (created != null) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        focusedPageId = created.id
                        delay(80L)
                        val updatedPages = viewModel.uiState.value.pages
                        val newIdx = updatedPages.indexOfFirst { it.id == created.id }
                        if (newIdx >= 0) {
                            singlePagerState.animateScrollToPage(newIdx)
                        }
                    }
                }
            }
        }
    }

    // Compact (1ページ) <-> Expanded (2ページ見開き) 切替時に、見ていたページへ即座に同期
    LaunchedEffect(isDualPageMode, pages.size, dualSlots.size) {
        val currentFocusedIdx = pages.indexOfFirst { it.id == focusedPageId }
            .takeIf { it >= 0 } ?: homePageIndex

        if (isDualPageMode) {
            val targetSlot = resolveExpandedDualSlotIndex(dualSlots, focusedPageId)
                .coerceIn(0, (dualSlots.size - 1).coerceAtLeast(0))
            if (dualPagerState.currentPage != targetSlot) {
                dualPagerState.scrollToPage(targetSlot)
            }
        } else {
            val targetSingle = currentFocusedIdx.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            if (singlePagerState.currentPage != targetSingle) {
                singlePagerState.scrollToPage(targetSingle)
            }
        }
    }

    // ユーザーが1ページPagerをスワイプした際に focusedPageId を更新
    LaunchedEffect(singlePagerState.settledPage, isDualPageMode, pages) {
        if (!isDualPageMode) {
            pages.getOrNull(singlePagerState.settledPage)?.let { page ->
                focusedPageId = page.id
            }
        }
    }

    // ユーザーが2ページ見開きPagerをスワイプした際に、スロットのメインページを focusedPageId として記録
    LaunchedEffect(dualPagerState.settledPage, isDualPageMode, dualSlots) {
        if (isDualPageMode) {
            dualSlots.getOrNull(dualPagerState.settledPage)?.let { slot ->
                focusedPageId = slot.primaryPage.id
            }
        }
    }

    // Homeジェスチャーまたはページジャンプ要求時に指定ページへスクロール (仕様 4)
    LaunchedEffect(viewModel, pages, dualSlots, isDualPageMode) {
        viewModel.pageNavigationEvents.collect { targetPageId ->
            val targetIdx = pages.indexOfFirst { it.id == targetPageId }
            if (targetIdx >= 0) {
                focusedPageId = targetPageId
                if (isDualPageMode) {
                    val targetSlot = resolveExpandedDualSlotIndex(dualSlots, targetPageId)
                        .coerceIn(0, (dualSlots.size - 1).coerceAtLeast(0))
                    dualPagerState.animateScrollToPage(targetSlot)
                } else if (targetIdx < singlePagerState.pageCount) {
                    singlePagerState.animateScrollToPage(targetIdx)
                }
            }
        }
    }

    // DiscoverMode が GOOGLE_APP の場合、左端のDiscoverページへ到達したタイミングでGoogleアプリを起動する (仕様 29)
    val leftmostActivePageId = if (isDualPageMode) {
        val slot = dualSlots.getOrNull(dualPagerState.settledPage)
        when (slot) {
            is ExpandedPagerSlot.SingleFull -> slot.page.id
            is ExpandedPagerSlot.DualSpread -> slot.leftPage.id
            null -> null
        }
    } else {
        pages.getOrNull(singlePagerState.settledPage)?.id
    }
    var lastLeftmostPageId by remember { mutableStateOf(LauncherPage.PAGE_ID_HOME) }
    LaunchedEffect(leftmostActivePageId, uiState.settings.discoverMode) {
        val currentId = leftmostActivePageId ?: return@LaunchedEffect
        if (currentId == LauncherPage.PAGE_ID_DISCOVER &&
            lastLeftmostPageId != LauncherPage.PAGE_ID_DISCOVER &&
            uiState.settings.discoverMode == DiscoverMode.GOOGLE_APP
        ) {
            viewModel.feedBridge.openGoogleDiscoverApp()
        }
        lastLeftmostPageId = currentId
    }

    // 現在編集や追加のターゲットとなるメインページ
    val currentPage = if (isDualPageMode) {
        val currentSlot = dualSlots.getOrNull(dualPagerState.currentPage.coerceIn(0, dualSlots.lastIndex))
        currentSlot?.primaryPage ?: LauncherPage.FIXED_HOME
    } else {
        pages.getOrNull(singlePagerState.currentPage.coerceIn(0, pages.lastIndex)) ?: LauncherPage.FIXED_HOME
    }
    val visiblePageIndices: Set<Int> = if (isDualPageMode) {
        val currentSlot = dualSlots.getOrNull(dualPagerState.currentPage.coerceIn(0, dualSlots.lastIndex))
        val visibleIds = currentSlot?.visiblePageIds.orEmpty()
        pages.mapIndexedNotNull { idx, page -> if (page.id in visibleIds) idx else null }.toSet()
    } else {
        setOf(singlePagerState.currentPage.coerceIn(0, pages.lastIndex))
    }

    // Discoverページに到達して静止しているか（左端行き止まりからの追加スワイプでGoogleアプリを起動するため）
    val isSettledOnDiscover = if (isDualPageMode) {
        !dualPagerState.isScrollInProgress &&
            dualSlots.getOrNull(dualPagerState.settledPage)?.primaryPage?.id == LauncherPage.PAGE_ID_DISCOVER
    } else {
        !singlePagerState.isScrollInProgress &&
            pages.getOrNull(singlePagerState.settledPage)?.id == LauncherPage.PAGE_ID_DISCOVER
    }

    // Backボタン押下時：オーバーレイや編集モードを閉じ、HOME以外のページにいる場合はHOMEへ戻す (仕様 4)
    val shouldInterceptBack = (overlayState?.progress ?: 0f) > 0f || uiState.overlay.isSearchOverlayOpen ||
        uiState.overlay.isSettingsOpen ||
        uiState.overlay.resizingWidgetTarget != null ||
        uiState.overlay.isEditMode ||
        currentPage.id != LauncherPage.PAGE_ID_HOME

    BackHandler(enabled = shouldInterceptBack) {
        when {
            (overlayState?.progress ?: 0f) > 0f -> googleOverlay?.close()
            uiState.overlay.resizingWidgetTarget != null -> viewModel.dismissResizeWidgetDialog()
            uiState.overlay.isSearchOverlayOpen -> viewModel.closeSearchOverlay()
            uiState.overlay.isSettingsOpen -> viewModel.closeSettings()
            uiState.overlay.isEditMode -> viewModel.exitEditMode()
            currentPage.id != LauncherPage.PAGE_ID_HOME -> {
                viewModel.jumpToPage(LauncherPage.PAGE_ID_HOME)
            }
        }
    }

    val handleDragStart: (CrossPageDragState) -> Unit = { state ->
        activeDragState = state
    }
    val handleDragUpdate: (CrossPageDragState) -> Unit = { state ->
        activeDragState = state
    }
    val handleDragCancel: () -> Unit = {
        activeDragState = null
    }
    val handleDragEnd: (CrossPageDragState) -> Unit = { finalState ->
        val resolved = resolveDropTarget(finalState)
        activeDragState = null
        if (resolved != null) {
            val (targetPageId, targetMetrics, targetCell) = resolved
            viewModel.moveLayoutItem(
                item = finalState.item,
                newPosition = targetCell,
                isExpandedMode = targetMetrics.isExpanded,
                targetPageId = targetPageId
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = (overlayState?.progress ?: 0f) * size.width
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .onGloballyPositioned { coords ->
                rootBoundsInRoot = coords.boundsInRoot()
            }
    ) {
        if (adaptiveSpec.dockPlacement == DockPlacement.BOTTOM) {
            // Compact (Fold Closed) -> [Pager + Bottom Dock]
            Column(modifier = Modifier.fillMaxSize()) {
                EditModeBanner(
                    visible = uiState.overlay.isEditMode,
                    currentPage = currentPage,
                    onAddApp = { viewModel.requestAddItemToPage(currentPage, initialTab = 0) },
                    onAddWidget = { viewModel.requestAddItemToPage(currentPage, initialTab = 1) },
                    onManagePages = { viewModel.openPageManager() },
                    onFinishEdit = { viewModel.exitEditMode() }
                )

                HorizontalPager(
                    state = singlePagerState,
                    beyondViewportPageCount = pages.size.coerceAtLeast(1),
                    userScrollEnabled = activeDragState == null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            pagerBoundsInRoot = coords.boundsInRoot()
                        }
                ) { pageIndex ->
                    val page = pages.getOrNull(pageIndex) ?: LauncherPage.FIXED_HOME
                    LauncherPageContent(
                        page = page,
                        uiState = uiState,
                        adaptiveSpec = adaptiveSpec,
                        isHalfPaneInDualMode = false,
                        isSettledOnDiscover = isSettledOnDiscover,
                        activeDragState = activeDragState,
                        highlightedDropCell = if (currentDropTarget?.first == page.id) currentDropTarget.third else null,
                        editableHomePages = editableHomePages,
                        onGridMetricsChanged = { pageMetricsMap[it.pageId] = it },
                        onDragStartItem = handleDragStart,
                        onDragUpdateItem = handleDragUpdate,
                        onDragEndItem = handleDragEnd,
                        onDragCancelItem = handleDragCancel,
                        viewModel = viewModel
                    )
                }

                PageIndicatorBar(
                    pages = pages,
                    visiblePageIndices = visiblePageIndices,
                    indicatorStyle = uiState.settings.indicatorStyle,
                    isLayoutLocked = uiState.settings.layoutLocked,
                    timeSegment = uiState.overlay.currentTimeSegment,
                    timeChimeEnabled = uiState.settings.timeChimeEnabled,
                    activeChimeEvent = uiState.overlay.activeChimeEvent,
                    onSelectPage = { idx ->
                        pages.getOrNull(idx)?.let { viewModel.jumpToPage(it.id) }
                    },
                    onLongPressIndicator = { viewModel.openHomeEditSheet() },
                    onChimeAnimationFinished = { viewModel.onChimeAnimationFinished() }
                )

                AdaptiveDock(
                    dockItems = uiState.dockItems,
                    installedPackages = uiState.installedPackages,
                    placement = DockPlacement.BOTTOM,
                    isEditMode = uiState.overlay.isEditMode,
                    appDiscoveryRepository = viewModel.container.appDiscoveryRepository,
                    onDockItemClick = { item, isInstalled ->
                        viewModel.onDockItemClicked(item, isInstalled)
                    },
                    onRemoveDockItem = { viewModel.removeDockItem(it) },
                    onMoveDockItem = { item, delta -> viewModel.moveDockItem(item, delta) },
                    onRequestAddDockItem = { viewModel.requestAddItemToDock() }
                )
            }
        } else {
            // Expanded (Fold Open) -> [Main Area (Dual Page or Single Full) + Right Dock]
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    EditModeBanner(
                        visible = uiState.overlay.isEditMode,
                        currentPage = currentPage,
                        onAddApp = { viewModel.requestAddItemToPage(currentPage, initialTab = 0) },
                        onAddWidget = { viewModel.requestAddItemToPage(currentPage, initialTab = 1) },
                        onManagePages = { viewModel.openPageManager() },
                        onFinishEdit = { viewModel.exitEditMode() }
                    )

                    if (isDualPageMode) {
                        // 左右2ページ見開きモード（Discover と 設定 は 1ページ全画面固定、他は左右2ページ見開き）
                        HorizontalPager(
                            state = dualPagerState,
                            beyondViewportPageCount = dualSlots.size.coerceAtLeast(1),
                            userScrollEnabled = activeDragState == null,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    pagerBoundsInRoot = coords.boundsInRoot()
                                }
                        ) { slotIndex ->
                            val slot = dualSlots.getOrNull(slotIndex)
                                ?: ExpandedPagerSlot.SingleFull(LauncherPage.FIXED_HOME)

                            when (slot) {
                                is ExpandedPagerSlot.SingleFull -> {
                                    LauncherPageContent(
                                        page = slot.page,
                                        uiState = uiState,
                                        adaptiveSpec = adaptiveSpec,
                                        isHalfPaneInDualMode = false,
                                        isSettledOnDiscover = isSettledOnDiscover,
                                        activeDragState = activeDragState,
                                        highlightedDropCell = if (currentDropTarget?.first == slot.page.id) currentDropTarget.third else null,
                                        editableHomePages = editableHomePages,
                                        onGridMetricsChanged = { pageMetricsMap[it.pageId] = it },
                                        onDragStartItem = handleDragStart,
                                        onDragUpdateItem = handleDragUpdate,
                                        onDragEndItem = handleDragEnd,
                                        onDragCancelItem = handleDragCancel,
                                        viewModel = viewModel
                                    )
                                }

                                is ExpandedPagerSlot.DualSpread -> {
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        ) {
                                            LauncherPageContent(
                                                page = slot.leftPage,
                                                uiState = uiState,
                                                adaptiveSpec = adaptiveSpec,
                                                isHalfPaneInDualMode = true,
                                                isSettledOnDiscover = isSettledOnDiscover,
                                                activeDragState = activeDragState,
                                                highlightedDropCell = if (currentDropTarget?.first == slot.leftPage.id) currentDropTarget.third else null,
                                                editableHomePages = editableHomePages,
                                                onGridMetricsChanged = { pageMetricsMap[it.pageId] = it },
                                                onDragStartItem = handleDragStart,
                                                onDragUpdateItem = handleDragUpdate,
                                                onDragEndItem = handleDragEnd,
                                                onDragCancelItem = handleDragCancel,
                                                viewModel = viewModel
                                            )
                                        }

                                        VerticalDivider(
                                            color = Color.White.copy(alpha = 0.12f),
                                            thickness = 1.dp,
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .padding(vertical = 12.dp)
                                        )

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        ) {
                                            LauncherPageContent(
                                                page = slot.rightPage,
                                                uiState = uiState,
                                                adaptiveSpec = adaptiveSpec,
                                                isHalfPaneInDualMode = true,
                                                isSettledOnDiscover = isSettledOnDiscover,
                                                activeDragState = activeDragState,
                                                highlightedDropCell = if (currentDropTarget?.first == slot.rightPage.id) currentDropTarget.third else null,
                                                editableHomePages = editableHomePages,
                                                onGridMetricsChanged = { pageMetricsMap[it.pageId] = it },
                                                onDragStartItem = handleDragStart,
                                                onDragUpdateItem = handleDragUpdate,
                                                onDragEndItem = handleDragEnd,
                                                onDragCancelItem = handleDragCancel,
                                                viewModel = viewModel
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Expanded 1ページ全画面表示モード
                        HorizontalPager(
                            state = singlePagerState,
                            beyondViewportPageCount = pages.size.coerceAtLeast(1),
                            userScrollEnabled = activeDragState == null,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    pagerBoundsInRoot = coords.boundsInRoot()
                                }
                        ) { pageIndex ->
                            val page = pages.getOrNull(pageIndex) ?: LauncherPage.FIXED_HOME
                            LauncherPageContent(
                                page = page,
                                uiState = uiState,
                                adaptiveSpec = adaptiveSpec,
                                isHalfPaneInDualMode = false,
                                isSettledOnDiscover = isSettledOnDiscover,
                                activeDragState = activeDragState,
                                highlightedDropCell = if (currentDropTarget?.first == page.id) currentDropTarget.third else null,
                                editableHomePages = editableHomePages,
                                onGridMetricsChanged = { pageMetricsMap[it.pageId] = it },
                                onDragStartItem = handleDragStart,
                                onDragUpdateItem = handleDragUpdate,
                                onDragEndItem = handleDragEnd,
                                onDragCancelItem = handleDragCancel,
                                viewModel = viewModel
                            )
                        }
                    }

                    PageIndicatorBar(
                        pages = pages,
                        visiblePageIndices = visiblePageIndices,
                        indicatorStyle = uiState.settings.indicatorStyle,
                        isLayoutLocked = uiState.settings.layoutLocked,
                        timeSegment = uiState.overlay.currentTimeSegment,
                        timeChimeEnabled = uiState.settings.timeChimeEnabled,
                        activeChimeEvent = uiState.overlay.activeChimeEvent,
                        onSelectPage = { idx ->
                            val targetPage = pages.getOrNull(idx) ?: return@PageIndicatorBar
                            if (isDualPageMode) {
                                val targetSlot = resolveExpandedDualSlotIndex(dualSlots, targetPage.id)
                                    .coerceIn(0, (dualSlots.size - 1).coerceAtLeast(0))
                                coroutineScope.launch {
                                    dualPagerState.animateScrollToPage(targetSlot)
                                }
                            } else {
                                coroutineScope.launch {
                                    singlePagerState.animateScrollToPage(idx)
                                }
                            }
                        },
                        onLongPressIndicator = { viewModel.openHomeEditSheet() },
                        onChimeAnimationFinished = { viewModel.onChimeAnimationFinished() }
                    )
                }

                // Right Dock (Expanded / Fold Open)
                AdaptiveDock(
                    dockItems = uiState.dockItems,
                    installedPackages = uiState.installedPackages,
                    placement = DockPlacement.RIGHT,
                    isEditMode = uiState.overlay.isEditMode,
                    appDiscoveryRepository = viewModel.container.appDiscoveryRepository,
                    onDockItemClick = { item, isInstalled ->
                        viewModel.onDockItemClicked(item, isInstalled)
                    },
                    onRemoveDockItem = { viewModel.removeDockItem(it) },
                    onMoveDockItem = { item, delta -> viewModel.moveDockItem(item, delta) },
                    onRequestAddDockItem = { viewModel.requestAddItemToDock() }
                )
            }
        }

        // --- ページ跨ぎドラッグ中のフローティングプレビュー & 左右端ホバーインジケーター ---
        val drag = activeDragState
        if (drag != null) {
            // 1. 画面左右端のページ遷移ゾーン視覚ガイド
            if (edgeHoverDirection < 0 && edgeTransitionHint != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight(0.75f)
                        .width(44.dp)
                        .clip(RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else if (edgeHoverDirection > 0 && edgeTransitionHint != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(0.75f)
                        .width(44.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // 上部中央に現在のドロップ予定ページとホバー案内を表示
            val targetPageName = pages.find { it.id == currentDropTarget?.first }?.name ?: currentPage.name
            Surface(
                color = Color(0xEE101622),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
            ) {
                Text(
                    text = edgeTransitionHint
                        ?: "移動先: $targetPageName ${
                            currentDropTarget?.third?.let { "(列${it.x + 1}, 行${it.y + 1})" } ?: ""
                        }",
                    color = if (edgeTransitionHint != null) MaterialTheme.colorScheme.primary else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // 2. 指に追従するドラッグ中アイテム／ウィジェットのフローティングプレビュー
            val relX = (drag.topLeftInRoot.x - rootBoundsInRoot.left).roundToInt()
            val relY = (drag.topLeftInRoot.y - rootBoundsInRoot.top).roundToInt()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset { IntOffset(relX, relY) }
                    .size(width = drag.itemWidthDp, height = drag.itemHeightDp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC1A2232))
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp)
                    )
            ) {
                if (drag.item.type == ItemType.WIDGET) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = drag.item.label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "${drag.spanX}×${drag.spanY} ウィジェットを移動中",
                            color = Color(0xFFB0BEC5),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                } else {
                    LauncherItemGraphic(
                        type = drag.item.type,
                        packageName = drag.item.packageName,
                        activityName = drag.item.activityName,
                        targetUri = drag.item.targetUri,
                        label = drag.item.label,
                        isInstalled = true,
                        appDiscoveryRepository = viewModel.container.appDiscoveryRepository,
                        iconSize = 48.dp,
                        showLabel = true,
                        isEditMode = false
                    )
                }
            }
        }

        // --- Overlays & Dialogs ---

        // 1. Swipe Up Search Overlay (仕様 8, 9, 21〜27, 37)
        AnimatedVisibility(
            visible = uiState.overlay.isSearchOverlayOpen,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 }
        ) {
            SearchOverlay(
                installedApps = uiState.installedApps,
                configuredShortcuts = uiState.layoutItems,
                usageMap = uiState.usageMetrics,
                hasUsageAccessPermission = uiState.hasUsageAccessPermission,
                searchEngine = viewModel.searchEngine,
                appDiscoveryRepository = viewModel.container.appDiscoveryRepository,
                onLaunchApp = { viewModel.launchApp(it) },
                onLaunchShortcut = { viewModel.onLayoutItemClicked(it, true) },
                onTriggerAction = { viewModel.triggerLauncherAction(it) },
                onGoogleSearch = { viewModel.launchGoogleSearch(it) },
                onRequestUsageAccess = { viewModel.openUsageAccessSettings() },
                onDismiss = { viewModel.closeSearchOverlay() }
            )
        }

        // 2. Settings Overlay (万が一オーバーレイ指定された場合のフォールバック)
        AnimatedVisibility(
            visible = uiState.overlay.isSettingsOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SettingsScreen(
                settings = uiState.settings,
                snapshots = uiState.snapshots,
                statusMessage = uiState.overlay.statusMessage,
                isEmbeddedPage = false,
                hasUsageAccessPermission = uiState.hasUsageAccessPermission,
                updateState = uiState.updateState,
                onClearStatusMessage = { viewModel.clearStatusMessage() },
                onToggleLayoutLock = { viewModel.setLayoutLocked(it) },
                onUpdateCompactGrid = { c, r -> viewModel.updateCompactGrid(c, r) },
                onUpdateExpandedGrid = { c, r -> viewModel.updateExpandedGrid(c, r) },
                onUpdateTinyIcons = { cc, ce, size, labels ->
                    viewModel.updateTinyIconsConfig(cc, ce, size, labels)
                },
                onSelectExpandedLayoutMode = { viewModel.setExpandedPageLayoutMode(it) },
                onToggleAllAppsLeftOnlyInExpanded = { viewModel.setAllAppsLeftOnlyInExpandedSingle(it) },
                onSelectIndicatorStyle = { viewModel.setIndicatorStyle(it) },
                onToggleFirstChime = { viewModel.setFirstChimeEnabled(it) },
                onToggleReturnChime = { viewModel.setReturnChimeEnabled(it) },
                onSelectReturnChimeInterval = { viewModel.setReturnChimeInterval(it) },
                onToggleTimeChime = { viewModel.setTimeChimeEnabled(it) },
                onToggleSwipeDownNotification = { viewModel.setSwipeDownNotificationEnabled(it) },
                onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                onOpenUsageAccessSettings = { viewModel.openUsageAccessSettings() },
                onOpenDefaultHomeSettings = { viewModel.openDefaultHomeSettings() },
                onSelectDiscoverMode = { viewModel.setDiscoverMode(it) },
                onSaveSnapshot = { viewModel.saveSnapshot(it) },
                onRestoreSnapshot = { viewModel.restoreSnapshot(it) },
                onDeleteSnapshot = { viewModel.deleteSnapshot(it) },
                onExportBackupToUri = { uri -> viewModel.exportBackupToUri(context, uri) },
                onImportBackupFromUri = { uri -> viewModel.importBackupFromUri(context, uri) },
                onShowJsonPreview = { viewModel.openJsonBackupPreview() },
                onAutoBindMissingApps = { viewModel.autoBindMissingAppsToInstalledApps() },
                onAddDemoMissingAppPlaceholder = {
                    viewModel.addDemoMissingAppPlaceholder(adaptiveSpec.isExpanded && !isDualPageMode)
                },
                onCheckForUpdate = { viewModel.checkForAppUpdate() },
                onDownloadAndInstallUpdate = { viewModel.downloadAndInstallAppUpdate(it) },
                onInstallDownloadedApk = { viewModel.installDownloadedApk(it) },
                onOpenUnknownSourcesSettings = { viewModel.openUnknownAppSourcesSettings() },
                onOpenGitHubReleases = { viewModel.openGitHubReleasesPage(it) },
                onClose = { viewModel.closeSettings() }
            )
        }

        // 3. 空白長押し「ホーム画面を編集」シート (仕様 13)
        if (uiState.overlay.isHomeEditSheetOpen) {
            HomeEditSheet(
                isLayoutLocked = uiState.settings.layoutLocked,
                isEditMode = uiState.overlay.isEditMode,
                onAddAppClick = {
                    viewModel.requestAddItemToPage(currentPage, initialTab = 0)
                },
                onAddWidgetClick = {
                    viewModel.requestAddItemToPage(currentPage, initialTab = 1)
                },
                onAddShortcutOrActionClick = {
                    viewModel.requestAddItemToPage(currentPage, initialTab = 2)
                },
                onManagePagesClick = { viewModel.openPageManager() },
                onToggleEditModeClick = { viewModel.toggleEditMode() },
                onToggleLayoutLockClick = {
                    viewModel.setLayoutLocked(!uiState.settings.layoutLocked)
                },
                onOpenSettingsClick = { viewModel.openSettings() },
                onDismiss = { viewModel.closeHomeEditSheet() }
            )
        }

        // 4. ロック中の変更操作警告ダイアログ (仕様 15)
        if (uiState.overlay.showLockedAlert) {
            LockedAlertDialog(
                onUnlock = { viewModel.unlockLayoutFromDialog() },
                onDismiss = { viewModel.dismissLockedAlert() }
            )
        }

        // 5. アイテム追加ピッカーダイアログ (仕様 12, 13)
        val pickerTarget = uiState.overlay.itemPickerTarget
        if (pickerTarget != null) {
            val desc = when (pickerTarget) {
                is ItemPickerTarget.HomePageCell ->
                    "配置先: ${pickerTarget.pageName}" +
                        (pickerTarget.preferredCell?.let { " (列${it.x + 1}, 行${it.y + 1})" } ?: "")
                is ItemPickerTarget.Dock -> "配置先: Adaptive Dock"
            }
            val initialTab = (pickerTarget as? ItemPickerTarget.HomePageCell)?.initialTab ?: 0
            val useExpandedCoord = adaptiveSpec.isExpanded && !isDualPageMode
            ItemPickerDialog(
                initialTab = initialTab,
                targetDescription = desc,
                installedApps = uiState.installedApps,
                availableWidgets = uiState.availableWidgets,
                allowWidgets = pickerTarget is ItemPickerTarget.HomePageCell,
                appDiscoveryRepository = viewModel.container.appDiscoveryRepository,
                onSelectApp = { app ->
                    viewModel.addAppFromPicker(app, useExpandedCoord)
                },
                onSelectWidget = { widget ->
                    viewModel.addWidgetFromPicker(widget, useExpandedCoord)
                },
                onSelectAction = { action ->
                    viewModel.addActionFromPicker(action, useExpandedCoord)
                },
                onCreateShortcut = { label, uri ->
                    viewModel.addShortcutFromPicker(label, uri, useExpandedCoord)
                },
                onDismiss = { viewModel.dismissItemPicker() }
            )
        }

        // 6. ウィジェットサイズ変更ダイアログ
        val resizingWidget = uiState.overlay.resizingWidgetTarget
        if (resizingWidget != null) {
            val useExpandedFullGrid = adaptiveSpec.isExpanded && !isDualPageMode
            val maxCols = if (useExpandedFullGrid) {
                uiState.settings.expandedGridColumns
            } else {
                uiState.settings.compactGridColumns
            }
            val maxRows = if (useExpandedFullGrid) {
                uiState.settings.expandedGridRows
            } else {
                uiState.settings.compactGridRows
            }
            WidgetResizeDialog(
                item = resizingWidget,
                maxColumns = maxCols,
                maxRows = maxRows,
                onConfirmResize = { newSpanX, newSpanY ->
                    viewModel.resizeWidgetItem(
                        item = resizingWidget,
                        newSpanX = newSpanX,
                        newSpanY = newSpanY
                    )
                },
                onDismiss = { viewModel.dismissResizeWidgetDialog() }
            )
        }

        // 7. ページ管理ダイアログ (仕様 16)
        if (uiState.overlay.isPageManagerOpen) {
            PageManagerDialog(
                userPages = uiState.userPages,
                onAddPage = { viewModel.addUserPage(it) },
                onRenamePage = { id, name -> viewModel.renameUserPage(id, name) },
                onDeletePage = { viewModel.deleteUserPage(it) },
                onMovePage = { id, delta -> viewModel.moveUserPage(id, delta) },
                onJumpToPage = { viewModel.jumpToPage(it) },
                onDismiss = { viewModel.closePageManager() }
            )
        }

        // 8. 未インストールアプリ・ウィジェット Placeholder ダイアログ (仕様 25)
        val missingTarget = uiState.overlay.missingAppDialogTarget
        if (missingTarget != null) {
            MissingAppDialog(
                item = missingTarget,
                installedApps = uiState.installedApps,
                onOpenPlayStore = { pkg -> viewModel.openPlayStoreForPackage(pkg) },
                onSearchPlayStore = { query -> viewModel.searchPlayStoreForQuery(query) },
                onSearchPlayStoreWeb = { query -> viewModel.searchPlayStoreOnWebForQuery(query) },
                onReplaceWithInstalledApp = { item, targetApp ->
                    viewModel.replaceMissingItemWithInstalledApp(item, targetApp)
                },
                onRemoveFromHome = { item -> viewModel.deleteLayoutItem(item) },
                onDismiss = { viewModel.dismissMissingAppDialog() }
            )
        }

        // 9. 下スワイプ通知 Accessibility オンボーディングダイアログ (仕様 7.2)
        if (uiState.overlay.showAccessibilityOnboardingDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissAccessibilityOnboarding() },
                title = {
                    Text("下スワイプで通知を開く", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "ホーム画面の中央から下スワイプでAndroid標準の通知シェードを開くには、" +
                            "アクセシビリティ設定で「Chime Launcher 通知シェード操作」を有効にしてください。\n\n" +
                            "※画面内容の読み取りは一切行わず、通知パネル展開のみに使用します。"
                    )
                },
                confirmButton = {
                    Button(onClick = { viewModel.openAccessibilitySettings() }) {
                        Text("Accessibility権限設定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissAccessibilityOnboarding() }) {
                        Text("キャンセル")
                    }
                }
            )
        }

        // 10. JSONプレビュー・直接復元ダイアログ
        val jsonPreview = uiState.overlay.jsonPreviewContent
        if (jsonPreview != null) {
            JsonBackupPreviewDialog(
                initialJson = jsonPreview,
                onRestoreFromJsonText = { viewModel.restoreFromRawJson(it) },
                onDismiss = { viewModel.closeJsonBackupPreview() }
            )
        }
    }
}

@Composable
private fun LauncherPageContent(
    page: LauncherPage,
    uiState: LauncherUiState,
    adaptiveSpec: AdaptiveLayoutSpec,
    isHalfPaneInDualMode: Boolean,
    isSettledOnDiscover: Boolean,
    activeDragState: CrossPageDragState?,
    highlightedDropCell: GridPosition?,
    editableHomePages: List<LauncherPage>,
    onGridMetricsChanged: (HomePageGridMetrics) -> Unit,
    onDragStartItem: (CrossPageDragState) -> Unit,
    onDragUpdateItem: (CrossPageDragState) -> Unit,
    onDragEndItem: (CrossPageDragState) -> Unit,
    onDragCancelItem: () -> Unit,
    viewModel: LauncherViewModel
) {
    val context = LocalContext.current
    // 見開き2ページ表示の片側ペインでは、Compact用の列数・座標を使って閉じた時のレイアウトをそのまま綺麗に収める
    val useExpandedFullGrid = adaptiveSpec.isExpanded && !isHalfPaneInDualMode

    when (page.id) {
        LauncherPage.PAGE_ID_DISCOVER -> {
            DiscoverPage(
                discoverMode = uiState.settings.discoverMode,
                feedBridge = viewModel.feedBridge,
                isSettledOnDiscover = isSettledOnDiscover,
                onSelectDiscoverMode = { viewModel.setDiscoverMode(it) },
                onOpenGoogleApp = {
                    val opened = viewModel.feedBridge.openGoogleDiscoverApp()
                    if (!opened) {
                        viewModel.launchGoogleSearch("Google Discover")
                    }
                },
                onTriggerAction = { viewModel.triggerLauncherAction(it) }
            )
        }

        LauncherPage.PAGE_ID_ALL_APPS -> {
            val tinyCols = if (useExpandedFullGrid) {
                uiState.settings.tinyIconsColumnsExpanded
            } else {
                uiState.settings.tinyIconsColumnsCompact
            }

            AllAppsTinyPage(
                apps = uiState.installedApps,
                columns = tinyCols,
                iconSizeDp = uiState.settings.tinyIconsSizeDp,
                showLabels = uiState.settings.tinyIconsShowLabels,
                isExpandedSinglePage = useExpandedFullGrid,
                restrictToHalfWidthInExpanded = uiState.settings.allAppsLeftOnlyInExpandedSingle,
                appDiscoveryRepository = viewModel.container.appDiscoveryRepository,
                onLaunchApp = { viewModel.launchApp(it) },
                onAddAppToHome = { app ->
                    viewModel.quickAddAppToHome(app, useExpandedFullGrid, context)
                },
                onAddAppToDock = { app ->
                    viewModel.quickAddAppToDock(app, context)
                },
                onOpenAppInfo = { app -> viewModel.openAppInfo(app.packageName) },
                onOpenSearch = { viewModel.onSwipeUpSearch() },
                onToggleLabels = {
                    viewModel.updateTinyIconsConfig(
                        columnsCompact = uiState.settings.tinyIconsColumnsCompact,
                        columnsExpanded = uiState.settings.tinyIconsColumnsExpanded,
                        sizeDp = uiState.settings.tinyIconsSizeDp,
                        showLabels = !uiState.settings.tinyIconsShowLabels
                    )
                },
                onChangeColumns = { newCols ->
                    if (useExpandedFullGrid) {
                        viewModel.updateTinyIconsConfig(
                            columnsCompact = uiState.settings.tinyIconsColumnsCompact,
                            columnsExpanded = newCols,
                            sizeDp = uiState.settings.tinyIconsSizeDp,
                            showLabels = uiState.settings.tinyIconsShowLabels
                        )
                    } else {
                        viewModel.updateTinyIconsConfig(
                            columnsCompact = newCols,
                            columnsExpanded = uiState.settings.tinyIconsColumnsExpanded,
                            sizeDp = uiState.settings.tinyIconsSizeDp,
                            showLabels = uiState.settings.tinyIconsShowLabels
                        )
                    }
                },
                onChangeIconSize = { newSize ->
                    viewModel.updateTinyIconsConfig(
                        columnsCompact = uiState.settings.tinyIconsColumnsCompact,
                        columnsExpanded = uiState.settings.tinyIconsColumnsExpanded,
                        sizeDp = newSize,
                        showLabels = uiState.settings.tinyIconsShowLabels
                    )
                },
                onToggleHalfWidthInExpanded = {
                    viewModel.setAllAppsLeftOnlyInExpandedSingle(
                        !uiState.settings.allAppsLeftOnlyInExpandedSingle
                    )
                }
            )
        }

        LauncherPage.PAGE_ID_SETTINGS -> {
            // 一番右端のChime Launcher設定ページ
            SettingsScreen(
                settings = uiState.settings,
                snapshots = uiState.snapshots,
                statusMessage = uiState.overlay.statusMessage,
                isEmbeddedPage = true,
                hasUsageAccessPermission = uiState.hasUsageAccessPermission,
                updateState = uiState.updateState,
                onClearStatusMessage = { viewModel.clearStatusMessage() },
                onToggleLayoutLock = { viewModel.setLayoutLocked(it) },
                onUpdateCompactGrid = { c, r -> viewModel.updateCompactGrid(c, r) },
                onUpdateExpandedGrid = { c, r -> viewModel.updateExpandedGrid(c, r) },
                onUpdateTinyIcons = { cc, ce, size, labels ->
                    viewModel.updateTinyIconsConfig(cc, ce, size, labels)
                },
                onSelectExpandedLayoutMode = { viewModel.setExpandedPageLayoutMode(it) },
                onToggleAllAppsLeftOnlyInExpanded = { viewModel.setAllAppsLeftOnlyInExpandedSingle(it) },
                onSelectIndicatorStyle = { viewModel.setIndicatorStyle(it) },
                onToggleFirstChime = { viewModel.setFirstChimeEnabled(it) },
                onToggleReturnChime = { viewModel.setReturnChimeEnabled(it) },
                onSelectReturnChimeInterval = { viewModel.setReturnChimeInterval(it) },
                onToggleTimeChime = { viewModel.setTimeChimeEnabled(it) },
                onToggleSwipeDownNotification = { viewModel.setSwipeDownNotificationEnabled(it) },
                onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                onOpenUsageAccessSettings = { viewModel.openUsageAccessSettings() },
                onOpenDefaultHomeSettings = { viewModel.openDefaultHomeSettings() },
                onSelectDiscoverMode = { viewModel.setDiscoverMode(it) },
                onSaveSnapshot = { viewModel.saveSnapshot(it) },
                onRestoreSnapshot = { viewModel.restoreSnapshot(it) },
                onDeleteSnapshot = { viewModel.deleteSnapshot(it) },
                onExportBackupToUri = { uri -> viewModel.exportBackupToUri(context, uri) },
                onImportBackupFromUri = { uri -> viewModel.importBackupFromUri(context, uri) },
                onShowJsonPreview = { viewModel.openJsonBackupPreview() },
                onAutoBindMissingApps = { viewModel.autoBindMissingAppsToInstalledApps() },
                onAddDemoMissingAppPlaceholder = {
                    viewModel.addDemoMissingAppPlaceholder(useExpandedFullGrid)
                },
                onCheckForUpdate = { viewModel.checkForAppUpdate() },
                onDownloadAndInstallUpdate = { viewModel.downloadAndInstallAppUpdate(it) },
                onInstallDownloadedApk = { viewModel.installDownloadedApk(it) },
                onOpenUnknownSourcesSettings = { viewModel.openUnknownAppSourcesSettings() },
                onOpenGitHubReleases = { viewModel.openGitHubReleasesPage(it) },
                onClose = { viewModel.jumpToPage(LauncherPage.PAGE_ID_HOME) }
            )
        }

        else -> {
            // Page 0 (HOME) および HOME右側のユーザー追加ページ
            val pageItems = uiState.layoutItems.filter { it.pageId == page.id }
            val cols = if (useExpandedFullGrid) uiState.settings.expandedGridColumns else uiState.settings.compactGridColumns
            val rows = if (useExpandedFullGrid) uiState.settings.expandedGridRows else uiState.settings.compactGridRows

            HomeGridPage(
                page = page,
                items = pageItems,
                installedPackages = uiState.installedPackages,
                columns = cols,
                rows = rows,
                isExpanded = useExpandedFullGrid,
                isEditMode = uiState.overlay.isEditMode,
                appDiscoveryRepository = viewModel.container.appDiscoveryRepository,
                widgetHostManager = viewModel.widgetHostManager,
                onItemClick = { item, isInstalled ->
                    viewModel.onLayoutItemClicked(item, isInstalled)
                },
                onBlankLongPress = { viewModel.openHomeEditSheet() },
                onRequestAddAtCell = { cellPos ->
                    viewModel.requestAddItemToPage(page, preferredCell = cellPos)
                },
                onMoveItem = { item, targetPos ->
                    viewModel.moveLayoutItem(item, targetPos, useExpandedFullGrid)
                },
                onResizeWidgetRequest = { item ->
                    viewModel.openResizeWidgetDialog(item)
                },
                onRebindWidgetRequest = { item ->
                    viewModel.requestRebindExistingWidget(item, useExpandedFullGrid)
                },
                onConfigureWidgetRequest = { item ->
                    viewModel.requestConfigureExistingWidget(item)
                },
                onSilentAutoRebindWidget = { item ->
                    viewModel.trySilentAutoRebindWidget(item)
                },
                onDeleteItem = { item -> viewModel.deleteLayoutItem(item) },
                onEnterEditMode = { viewModel.enterEditMode() },
                onOpenAppInfo = { pkg -> viewModel.openAppInfo(pkg) },
                onSwipeUp = { viewModel.onSwipeUpSearch() },
                onSwipeDown = { viewModel.onSwipeDownNotification(context) },
                activeDragState = activeDragState,
                highlightedDropCell = highlightedDropCell,
                availableHomePages = editableHomePages,
                onGridMetricsChanged = onGridMetricsChanged,
                onDragStartItem = onDragStartItem,
                onDragUpdateItem = onDragUpdateItem,
                onDragEndItem = onDragEndItem,
                onDragCancelItem = onDragCancelItem,
                onMoveItemToAnotherPage = { item, destPageId ->
                    viewModel.moveItemToAnotherPage(item, destPageId, useExpandedFullGrid)
                }
            )
        }
    }
}

@Composable
private fun EditModeBanner(
    visible: Boolean,
    currentPage: LauncherPage,
    onAddApp: () -> Unit,
    onAddWidget: () -> Unit,
    onManagePages: () -> Unit,
    onFinishEdit: () -> Unit
) {
    AnimatedVisibility(visible = visible) {
        Surface(
            color = Color(0xDD1A1D24),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "編集: ${currentPage.name}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(onClick = onAddApp) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("追加", fontSize = 12.sp)
                    }
                    FilledTonalButton(onClick = onAddWidget) {
                        Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Widget", fontSize = 12.sp)
                    }
                    FilledTonalButton(onClick = onManagePages) {
                        Icon(Icons.Default.Pages, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ページ", fontSize = 12.sp)
                    }
                    Button(onClick = onFinishEdit) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("完了", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

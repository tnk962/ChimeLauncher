package com.myenvironment.launcher.ui

import com.myenvironment.launcher.core.model.folderSpace
import com.myenvironment.launcher.core.model.sizeFolder
import com.myenvironment.launcher.core.model.FolderApp
import com.myenvironment.launcher.core.model.asLayoutItem
import com.myenvironment.launcher.core.model.folder
import com.myenvironment.launcher.core.model.withFolderApps
import com.myenvironment.launcher.core.model.addFolderApp
import com.myenvironment.launcher.core.model.groupApp
import com.myenvironment.launcher.core.model.renameFolder
import com.myenvironment.launcher.core.model.moveHomeToDock
import com.myenvironment.launcher.core.model.freeFolderCell
import com.myenvironment.launcher.core.model.replaceFolderApp
import com.myenvironment.launcher.core.model.ExpandedDockPosition
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myenvironment.launcher.AppContainer
import com.myenvironment.launcher.accessibility.NotificationShadeService
import com.myenvironment.launcher.core.chime.ChimeEvent
import com.myenvironment.launcher.core.chime.TimeSegment
import com.myenvironment.launcher.core.launcher.MissingAppResolver
import com.myenvironment.launcher.core.model.AppInfo
import com.myenvironment.launcher.core.model.BackupSnapshotSummary
import com.myenvironment.launcher.core.model.DiscoverMode
import com.myenvironment.launcher.core.model.DockItem
import com.myenvironment.launcher.core.model.GridPosition
import com.myenvironment.launcher.core.model.IndicatorStyle
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LauncherAction
import com.myenvironment.launcher.core.feed.FeedCategory
import com.myenvironment.launcher.core.model.visibleLauncherPages
import com.myenvironment.launcher.core.model.LauncherPage
import com.myenvironment.launcher.core.model.LauncherSettings
import com.myenvironment.launcher.core.model.LayoutItem
import com.myenvironment.launcher.core.model.ReturnChimeInterval
import com.myenvironment.launcher.core.search.AppUsageMetric
import com.myenvironment.launcher.core.update.AppUpdateState
import com.myenvironment.launcher.core.update.ReleaseUpdateInfo
import com.myenvironment.launcher.core.widget.WidgetProviderCatalogItem
import com.myenvironment.launcher.core.model.LayoutSnapshot
import com.myenvironment.launcher.core.storage.LayoutUndoManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * アイテム追加ピッカーの追加先指定
 */
sealed interface ItemPickerTarget {
    data class HomePageCell(
        val pageId: String,
        val pageName: String,
        val preferredCell: GridPosition? = null,
        val initialTab: Int = 0
    ) : ItemPickerTarget

    data object Dock : ItemPickerTarget
}

/**
 * システムの AppWidget バインド許可ダイアログや Configuration Activity を起動するためのイベント
 */
sealed interface WidgetSystemEvent {
    data class RequestBindAppWidget(
        val appWidgetId: Int,
        val provider: ComponentName
    ) : WidgetSystemEvent

    data class RequestConfigureAppWidget(
        val appWidgetId: Int
    ) : WidgetSystemEvent
}

/**
 * バインド・初期設定完了待ちのウィジェット配置リクエスト
 */
internal data class PendingWidgetPlacement(
    val appWidgetId: Int,
    val provider: ComponentName,
    val label: String,
    val pageId: String,
    val preferredCell: GridPosition?,
    val spanX: Int,
    val spanY: Int,
    val isExpandedMode: Boolean,
    val existingItemId: String? = null
)

/**
 * ViewModel内のローカルUI制御状態
 */
data class OverlayControlState(
    val activeFolderId: String? = null,
    val isEditMode: Boolean = false,
    val undoCount: Int = 0,
    val isLayoutOperationInProgress: Boolean = false,
    val isWidgetPlacementPending: Boolean = false,
    val isSearchOverlayOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isHomeEditSheetOpen: Boolean = false,
    val isPageManagerOpen: Boolean = false,
    val showLockedAlert: Boolean = false,
    val showAccessibilityOnboardingDialog: Boolean = false,
    val missingAppDialogTarget: LayoutItem? = null,
    val resizingWidgetTarget: LayoutItem? = null,
    val itemPickerTarget: ItemPickerTarget? = null,
    val jsonPreviewContent: String? = null,
    val statusMessage: String? = null,
    val activeChimeEvent: ChimeEvent? = null,
    val currentTimeSegment: TimeSegment = TimeSegment.DAY
)

/**
 * Chime Launcher 全体の統合UIステート
 */
data class LauncherUiState(
    val installedApps: List<AppInfo> = emptyList(),
    val installedPackages: Set<String> = emptySet(),
    val availableWidgets: List<WidgetProviderCatalogItem> = emptyList(),
    val usageMetrics: Map<String, AppUsageMetric> = emptyMap(),
    val hasUsageAccessPermission: Boolean = true,
    val pages: List<LauncherPage> = listOf(
        LauncherPage.FIXED_DISCOVER,
        LauncherPage.FIXED_ALL_APPS,
        LauncherPage.FIXED_HOME
    ),
    val userPages: List<LauncherPage> = emptyList(),
    val homePageIndex: Int = 2,
    val layoutItems: List<LayoutItem> = emptyList(),
    val dockItems: List<DockItem> = emptyList(),
    val settings: LauncherSettings = LauncherSettings(),
    val snapshots: List<BackupSnapshotSummary> = emptyList(),
    val updateState: AppUpdateState = AppUpdateState.Idle,
    val overlay: OverlayControlState = OverlayControlState()
)

class LauncherViewModel(
    val container: AppContainer
) : ViewModel() {

    private val appDiscovery = container.appDiscoveryRepository
    private val appLauncher = container.appLauncher
    private val layoutRepository = container.layoutRepository
    private val settingsRepository = container.settingsRepository
    private val backupManager = container.backupManager
    val appUsageRepository = container.appUsageRepository
    val chimeController = container.chimeController
    val widgetHostManager = container.widgetHostManager
    val searchEngine = container.searchEngine
    val feedBridge = container.feedBridge
    val appUpdateManager = container.appUpdateManager

    private val overlayState = MutableStateFlow(OverlayControlState())
    private val availableWidgetsState = MutableStateFlow<List<WidgetProviderCatalogItem>>(emptyList())

    private val committingWidgetIds = mutableSetOf<Int>()
    private var pendingWidgetPlacement: PendingWidgetPlacement? = null
        set(value) {
            field = value
            overlayState.update { it.copy(isWidgetPlacementPending = value != null || committingWidgetIds.isNotEmpty()) }
        }
    private val undoManager = LayoutUndoManager(
        readSnapshot = { layoutRepository.getLayoutSnapshot() },
        restoreSnapshot = { snapshot ->
            layoutRepository.restoreLayoutSnapshot(snapshot)
        },
        releaseWidgetId = widgetHostManager::deleteAppWidgetId,
        protectedWidgetIds = {
            committingWidgetIds + listOfNotNull(pendingWidgetPlacement?.appWidgetId)
        }
    )
    private val silentRebindAttemptedItemIds = HashSet<String>()

    // Pagerを指定ページIDへ移動させるイベントストリーム (Home Gesture復帰等で利用: 仕様 4)
    private val _pageNavigationEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val pageNavigationEvents: SharedFlow<String> = _pageNavigationEvents.asSharedFlow()

    // システムの AppWidget バインド許可 / 設定 Activity 起動イベントストリーム
    private val _widgetSystemEvents = MutableSharedFlow<WidgetSystemEvent>(extraBufferCapacity = 4)
    val widgetSystemEvents: SharedFlow<WidgetSystemEvent> = _widgetSystemEvents.asSharedFlow()

    val uiState: StateFlow<LauncherUiState> = combine(
        combine(
            appDiscovery.installedApps,
            appDiscovery.installedPackages,
            layoutRepository.userPages
        ) { apps, pkgs, uPages -> Triple(apps, pkgs, uPages) },
        combine(
            layoutRepository.layoutItems,
            layoutRepository.dockItems,
            settingsRepository.settings
        ) { items, dock, settings -> Triple(items, dock, settings) },
        combine(
            backupManager.savedSnapshots,
            overlayState,
            availableWidgetsState
        ) { snaps, overlay, widgets -> Triple(snaps, overlay, widgets) },
        combine(
            appUsageRepository.usageMetrics,
            appUsageRepository.hasUsageAccessPermission,
            appUpdateManager.updateState
        ) { usage, hasUsagePerm, updateSt -> Triple(usage, hasUsagePerm, updateSt) }
    ) { (apps, pkgs, uPages), (items, dock, settings), (snaps, overlay, widgets), (usage, hasUsagePerm, updateSt) ->
        val allPages = settings.visibleLauncherPages(uPages)
        val homeIdx = allPages.indexOfFirst { it.id == LauncherPage.PAGE_ID_HOME }.coerceAtLeast(0)

        LauncherUiState(
            installedApps = apps,
            installedPackages = pkgs,
            availableWidgets = widgets,
            usageMetrics = usage,
            hasUsageAccessPermission = hasUsagePerm,
            pages = allPages,
            userPages = uPages.sortedBy { it.sortOrder },
            homePageIndex = homeIdx,
            layoutItems = items,
            dockItems = dock,
            settings = settings,
            snapshots = snaps,
            updateState = updateSt,
            overlay = overlay
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LauncherUiState()
    )

    init {
        viewModelScope.launch {
            undoManager.state.collect { state ->
                overlayState.update { it.copy(undoCount = state.count, isLayoutOperationInProgress = state.isBusy) }
            }
        }
        viewModelScope.launch {
            layoutRepository.ensureInitialized()
            undoManager.initialize(runCatching { widgetHostManager.appWidgetHost.appWidgetIds.toSet() }.getOrDefault(emptySet()))
        }
        viewModelScope.launch {
            combine(
                appDiscovery.installedPackages,
                settingsRepository.settings
            ) { _, settings ->
                settings.compactGridColumns to settings.compactGridRows
            }.collect { (cols, rows) ->
                val catalog = withContext(Dispatchers.IO) {
                    widgetHostManager.loadInstalledWidgetCatalog(
                        maxColumns = cols,
                        maxRows = rows
                    )
                }
                availableWidgetsState.value = catalog
            }
        }
    }

    /**
     * Chime Launcher がフォアグラウンドに表示された際、Chime Moments (First / Return / Time) を判定し、
     * 検索用の利用統計キャッシュおよびGitHub Releasesの新バージョン確認もバックグラウンド更新する (仕様 6〜10, 29, 30, 37)
     */
    fun onLauncherResumed(nowMillis: Long = System.currentTimeMillis()) {
        appUsageRepository.refreshUsageStatsAsync(force = false)
        appUpdateManager.checkForUpdatesAutoIfNeeded(nowMillis)
        viewModelScope.launch {
            val currentSettings = settingsRepository.settings.first()
            val lastFirstDate = settingsRepository.lastFirstChimeDate.first()
            val lastVisibleTs = settingsRepository.lastLauncherVisibleTimestamp.first()

            val evaluation = chimeController.evaluateOnHomeVisible(
                nowMillis = nowMillis,
                lastFirstChimeDate = lastFirstDate,
                lastLauncherVisibleTimestamp = lastVisibleTs,
                settings = currentSettings
            )

            overlayState.update { state ->
                state.copy(
                    currentTimeSegment = evaluation.timeSegment,
                    activeChimeEvent = evaluation.event ?: state.activeChimeEvent
                )
            }

            if (evaluation.updatedLastFirstChimeDate != lastFirstDate) {
                settingsRepository.setLastFirstChimeDate(evaluation.updatedLastFirstChimeDate)
            }
            settingsRepository.setLastLauncherVisibleTimestamp(evaluation.updatedLastVisibleTimestamp)
        }
    }

    /**
     * Chime Launcher から別アプリやスリープへ離れた際のタイムスタンプを記録する (Return Chime 計測用: 仕様 9, 28)
     */
    fun onLauncherPaused(nowMillis: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            settingsRepository.setLastLauncherVisibleTimestamp(nowMillis)
        }
    }

    /**
     * ページインジケーターでの Chime Moments アニメーション完了通知
     */
    fun onChimeAnimationFinished() {
        overlayState.update { it.copy(activeChimeEvent = null) }
    }

    private val discoverReturn = DiscoverReturn()
    private val appReturn = AppReturn()

    internal fun clearAppReturn() = appReturn.clear()

    fun openDiscoverArticle(url: String) {
        if (feedBridge.openArticleUrl(url)) discoverReturn.remember(DiscoverReturnTarget.CUSTOM)
    }

    fun rememberGoogleDiscoverDeparture() {
        discoverReturn.remember(DiscoverReturnTarget.GOOGLE)
    }

    internal fun consumeDiscoverReturn(): DiscoverReturnTarget? = discoverReturn.consume(
        uiState.value.settings.discoverMode.showsCustomFeed,
        uiState.value.settings.discoverMode.usesGoogleOverlay
    )

    /**
     * AndroidのHome操作が実行された際、すべてのオーバーレイを閉じる。アプリからの復帰と検索の終了は現在ページを維持し、それ以外はHOMEへ戻す (仕様 4, 30)
     * ※通常のホーム遷移では毎回ハプティックや音を追加せず、Chime条件成立時のみ静かに反応する
     */
    fun onHomeGestureInvoked() {
        val keepCurrentPage = keepPageOnHomeReturn(appReturn.consume(), overlayState.value.isSearchOverlayOpen)
        exitEditMode()
        overlayState.update {
            it.copy(
                activeFolderId = null,
                isEditMode = false,
                isSearchOverlayOpen = false,
                isSettingsOpen = false,
                isHomeEditSheetOpen = false,
                isPageManagerOpen = false,
                showLockedAlert = false,
                showAccessibilityOnboardingDialog = false,
                missingAppDialogTarget = null,
                resizingWidgetTarget = null,
                itemPickerTarget = null,
                jsonPreviewContent = null
            )
        }
        if (!keepCurrentPage) _pageNavigationEvents.tryEmit(LauncherPage.PAGE_ID_HOME)
        onLauncherResumed()
    }

    fun jumpToPage(pageId: String) {
        _pageNavigationEvents.tryEmit(pageId)
    }

    private fun layoutState(snapshot: LayoutSnapshot) = uiState.value.copy(
        userPages = snapshot.userPages,
        layoutItems = snapshot.items,
        dockItems = snapshot.dockItems
    )

    private suspend fun reportLayoutFailure(action: suspend () -> Unit) {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val message = "レイアウト操作に失敗しました: ${failure.message}"
            overlayState.update { it.copy(statusMessage = message) }
            Toast.makeText(container.appContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun launchLayoutEdit(action: suspend (LayoutSnapshot) -> Unit) {
        viewModelScope.launch {
            reportLayoutFailure {
                undoManager.edit { snapshot ->
                    if (settingsRepository.settings.first().layoutLocked) {
                        overlayState.update { it.copy(showLockedAlert = true) }
                    } else {
                        action(snapshot)
                    }
                }
            }
        }
    }

    private fun launchWithoutUndo(action: suspend (LayoutSnapshot) -> Unit) {
        viewModelScope.launch {
            reportLayoutFailure { undoManager.withoutHistory(action) }
        }
    }

    fun undoLastLayoutEdit() = runIfUnlocked {
        if (!overlayState.value.isEditMode || overlayState.value.isWidgetPlacementPending) return@runIfUnlocked
        viewModelScope.launch {
            reportLayoutFailure {
                val restored = undoManager.undo {
                    overlayState.value.isEditMode && !overlayState.value.isWidgetPlacementPending &&
                        !settingsRepository.settings.first().layoutLocked
                } ?: return@reportLayoutFailure
                val restoredPage = restored.userPages.firstOrNull { page ->
                    uiState.value.userPages.none { it.id == page.id }
                }
                restoredPage?.let { _pageNavigationEvents.tryEmit(it.id) }
            }
        }
    }

    override fun onCleared() {
        val pending = pendingWidgetPlacement
        pendingWidgetPlacement = null
        // viewModelScopeは既にキャンセルされるため、有限の後処理を別スコープで完了させる。
        CoroutineScope(Dispatchers.Main.immediate).launch {
            reportLayoutFailure {
                undoManager.endSession()
                pending?.let {
                    if (it.appWidgetId !in layoutRepository.getLayoutSnapshot().widgetIds) {
                        widgetHostManager.deleteAppWidgetId(it.appWidgetId)
                    }
                }
            }
        }
        super.onCleared()
    }

    // --- レイアウトロック判定ヘルパー (仕様 15) ---
    private fun runIfUnlocked(action: () -> Unit) {
        if (uiState.value.settings.layoutLocked) {
            overlayState.update { it.copy(showLockedAlert = true) }
        } else {
            action()
        }
    }

    fun dismissLockedAlert() {
        overlayState.update { it.copy(showLockedAlert = false) }
    }

    fun unlockLayoutFromDialog() {
        viewModelScope.launch {
            settingsRepository.setLayoutLocked(false)
            overlayState.update { it.copy(showLockedAlert = false) }
        }
    }

    // --- ジェスチャー操作 (仕様 6, 7, 8) ---
    private fun canTriggerHomeSwipeGesture(): Boolean {
        val ov = overlayState.value
        return !ov.isSettingsOpen &&
            !ov.isSearchOverlayOpen &&
            !ov.isEditMode &&
            !ov.isHomeEditSheetOpen &&
            !ov.isPageManagerOpen &&
            !ov.showLockedAlert &&
            !ov.showAccessibilityOnboardingDialog &&
            ov.missingAppDialogTarget == null &&
            ov.resizingWidgetTarget == null &&
            ov.itemPickerTarget == null &&
            ov.jsonPreviewContent == null
    }

    fun onSwipeUpSearch() {
        if (!canTriggerHomeSwipeGesture()) return
        overlayState.update { it.copy(isSearchOverlayOpen = true) }
    }

    fun openSearchOverlay() {
        if (overlayState.value.isSettingsOpen) return
        overlayState.update { it.copy(isSearchOverlayOpen = true) }
    }

    fun closeSearchOverlay() {
        overlayState.update { it.copy(isSearchOverlayOpen = false) }
    }

    fun onSwipeDownNotification(context: Context) {
        if (!canTriggerHomeSwipeGesture()) return
        val currentSettings = uiState.value.settings
        if (!currentSettings.swipeDownNotificationEnabled) return

        val expanded = NotificationShadeService.expandNotifications(context)
        if (!expanded) {
            // AccessibilityService も StatusBarManager も使えない場合のみオンボーディングを表示 (仕様 7.2)
            overlayState.update { it.copy(showAccessibilityOnboardingDialog = true) }
        }
    }

    fun dismissAccessibilityOnboarding() {
        overlayState.update { it.copy(showAccessibilityOnboardingDialog = false) }
    }

    fun openAccessibilitySettings() {
        overlayState.update { it.copy(showAccessibilityOnboardingDialog = false) }
        appLauncher.openAccessibilitySettings()
    }

    fun openUsageAccessSettings() {
        appLauncher.openUsageAccessSettings()
    }

    fun openDefaultHomeSettings() {
        appLauncher.openDefaultHomeSettings()
    }

    fun openAppInfo(packageName: String) {
        appLauncher.openAppDetailsSettings(packageName)
    }

    // --- アプリ / Shortcut / Action / Widget / Placeholder 起動 (仕様 12, 24, 25, 30) ---
    fun onLayoutItemClicked(item: LayoutItem, isInstalled: Boolean) {
        when (item.type) {
            ItemType.FOLDER -> overlayState.update { it.copy(activeFolderId = item.id) }
            ItemType.APP -> {
                if (!isInstalled) {
                    // 未インストールPlaceholderの場合はPlayストア連携ダイアログを表示 (仕様 25)
                    overlayState.update { it.copy(missingAppDialogTarget = item) }
                } else {
                    val launched = appLauncher.launchApp(item.packageName, item.activityName)
                    if (!launched) {
                        overlayState.update { it.copy(missingAppDialogTarget = item) }
                    } else {
                        appReturn.remember()
                        appUsageRepository.recordAppLaunch(item.packageName)
                    }
                }
            }
            ItemType.WIDGET -> {
                if (!isInstalled) {
                    overlayState.update { it.copy(missingAppDialogTarget = item) }
                }
            }
            ItemType.SHORTCUT -> {
                appLauncher.launchShortcutUri(item.targetUri)
            }
            ItemType.ACTION -> {
                val action = LauncherAction.fromActionId(item.targetUri)
                if (action != null) {
                    triggerLauncherAction(action)
                }
            }
        }
    }

    fun onDockItemClicked(item: DockItem, isInstalled: Boolean) {
        when (item.type) {
            ItemType.FOLDER -> overlayState.update { it.copy(activeFolderId = item.id) }
            ItemType.APP, ItemType.WIDGET -> {
                if (!isInstalled) {
                    overlayState.update {
                        it.copy(
                            missingAppDialogTarget = LayoutItem(
                                id = item.id,
                                pageId = "dock",
                                type = item.type,
                                packageName = item.packageName,
                                activityName = item.activityName,
                                targetUri = item.targetUri,
                                label = item.label,
                                compact = GridPosition(0, 0)
                            )
                        )
                    }
                } else {
                    val launched = appLauncher.launchApp(item.packageName, item.activityName)
                    if (launched) {
                        appReturn.remember()
                        appUsageRepository.recordAppLaunch(item.packageName)
                    }
                }
            }
            ItemType.SHORTCUT -> {
                appLauncher.launchShortcutUri(item.targetUri)
            }
            ItemType.ACTION -> {
                val action = LauncherAction.fromActionId(item.targetUri)
                if (action != null) {
                    triggerLauncherAction(action)
                }
            }
        }
    }

    fun launchApp(app: AppInfo) {
        val launched = appLauncher.launchApp(app)
        if (launched) {
            appReturn.remember()
            appUsageRepository.recordAppLaunch(app.packageName)
        }
    }

    fun launchGoogleSearch(query: String) {
        appLauncher.launchGoogleSearch(query)
    }

    fun openPlayStoreForPackage(packageName: String) {
        appLauncher.openPlayStore(packageName)
    }

    fun searchPlayStoreForQuery(query: String) {
        appLauncher.searchPlayStore(query)
    }

    fun searchPlayStoreOnWebForQuery(query: String) {
        appLauncher.searchPlayStoreOnWeb(query)
    }

    /**
     * 未インストールPlaceholderアイコンを、端末内（Pixel等）にインストール済みの該当・代替アプリにその位置のまま置き換える。
     */
    fun replaceMissingItemWithInstalledApp(missingItem: LayoutItem, targetApp: AppInfo) {
        launchWithoutUndo { snapshot ->
            if (missingItem.pageId.startsWith("folder:")) {
                val updated = snapshot.replaceFolderApp(missingItem.pageId.removePrefix("folder:"), missingItem.id,
                    FolderApp(missingItem.id, targetApp.packageName, targetApp.activityName, targetApp.label))
                    ?: return@launchWithoutUndo
                layoutRepository.restoreLayoutSnapshot(updated)
            } else if (missingItem.pageId == "dock") {
                val existingDock = snapshot.dockItems.find { it.id == missingItem.id }
                if (existingDock != null) {
                    layoutRepository.upsertDockItem(
                        existingDock.copy(
                            packageName = targetApp.packageName,
                            activityName = targetApp.activityName,
                            label = targetApp.label
                        )
                    )
                }
            } else {
                val existingLayout = snapshot.items.find { it.id == missingItem.id } ?: missingItem
                layoutRepository.upsertLayoutItem(
                    existingLayout.copy(
                        packageName = targetApp.packageName,
                        activityName = targetApp.activityName,
                        label = targetApp.label
                    )
                )
            }
            overlayState.update {
                it.copy(
                    missingAppDialogTarget = null,
                    statusMessage = "「${missingItem.label}」を端末内の「${targetApp.label}」に置き換えました"
                )
            }
            Toast.makeText(
                container.appContext,
                "「${targetApp.label}」に置き換えました",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * ホーム画面およびDock上のすべての未インストールPlaceholderをスキャンし、
     * Galaxy版Kindle (`com.amazon.kindleForSamsung` -> `com.amazon.kindle`) や
     * Samsung標準アプリ (`com.sec.android.app.camera` -> Pixelカメラ等)、同名インストール済みアプリが
     * 端末内に存在する場合は一括で自動紐付けする。
     */
    fun autoBindMissingAppsToInstalledApps() {
        launchWithoutUndo { snapshot ->
            val state = layoutState(snapshot)
            val installedPkgs = state.installedPackages
            val installedApps = state.installedApps

            var replacedCount = 0

            // 1. ホーム画面の未インストール APP アイテムを照合
            state.layoutItems
                .filter { it.type == ItemType.APP && !installedPkgs.contains(it.packageName) }
                .forEach { missingItem ->
                    val resolution = MissingAppResolver.resolve(missingItem, installedApps)
                    val bestCandidate = resolution.installedCandidates.firstOrNull { it.score >= 88 }
                    if (bestCandidate != null) {
                        layoutRepository.upsertLayoutItem(
                            missingItem.copy(
                                packageName = bestCandidate.appInfo.packageName,
                                activityName = bestCandidate.appInfo.activityName,
                                label = bestCandidate.appInfo.label
                            )
                        )
                        replacedCount++
                    }
                }

            // 2. Dockの未インストール APP アイテムを照合
            state.dockItems
                .filter { it.type == ItemType.APP && !installedPkgs.contains(it.packageName) }
                .forEach { missingDock ->
                    val syntheticItem = LayoutItem(
                        id = missingDock.id,
                        pageId = "dock",
                        type = missingDock.type,
                        packageName = missingDock.packageName,
                        activityName = missingDock.activityName,
                        label = missingDock.label,
                        compact = GridPosition(0, 0)
                    )
                    val resolution = MissingAppResolver.resolve(syntheticItem, installedApps)
                    val bestCandidate = resolution.installedCandidates.firstOrNull { it.score >= 88 }
                    if (bestCandidate != null) {
                        layoutRepository.upsertDockItem(
                            missingDock.copy(
                                packageName = bestCandidate.appInfo.packageName,
                                activityName = bestCandidate.appInfo.activityName,
                                label = bestCandidate.appInfo.label
                            )
                        )
                        replacedCount++
                    }
                }

            val msg = if (replacedCount > 0) {
                "未インストール枠のうち ${replacedCount}個のアイコンを端末内のアプリ（Kindle・標準アプリ等）に自動紐付けしました"
            } else {
                "端末内に直接一致する代替アプリは見つかりませんでした（各アイコンをタップしてキーワード検索・ストア移動ができます）"
            }
            overlayState.update { it.copy(statusMessage = msg) }
            Toast.makeText(container.appContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun dismissMissingAppDialog() {
        overlayState.update { it.copy(missingAppDialogTarget = null) }
    }

    fun triggerLauncherAction(action: LauncherAction) {
        when (action) {
            LauncherAction.SEARCH -> openSearchOverlay()
            LauncherAction.NOTIFICATION_SHADE -> {
                val ok = NotificationShadeService.expandNotifications(container.appContext)
                if (!ok) {
                    overlayState.update { it.copy(showAccessibilityOnboardingDialog = true) }
                }
            }
            LauncherAction.SETTINGS -> openSettings()
            LauncherAction.TOGGLE_LOCK -> {
                val nextLocked = !uiState.value.settings.layoutLocked
                setLayoutLocked(nextLocked)
            }
            LauncherAction.HATENA_FEED -> appLauncher.launchCompanionAppOrFallback(action)
            LauncherAction.MY_NOTIFICATIONS -> {
                if (appLauncher.launchCompanionAppOrFallback(action)) appReturn.remember()
            }
        }
    }

    // --- 編集シート & 編集モード制御 (仕様 13, 14, 15) ---
    fun openHomeEditSheet() {
        overlayState.update { it.copy(isHomeEditSheetOpen = true) }
    }

    fun closeHomeEditSheet() {
        overlayState.update { it.copy(isHomeEditSheetOpen = false) }
    }

    fun enterEditMode() = runIfUnlocked {
        if (overlayState.value.isEditMode) return@runIfUnlocked
        overlayState.update { it.copy(isEditMode = true) }
        viewModelScope.launch { reportLayoutFailure { undoManager.beginSession() } }
    }

    fun toggleEditMode() {
        if (overlayState.value.isEditMode) exitEditMode() else enterEditMode()
    }

    fun exitEditMode() {
        overlayState.update { it.copy(isEditMode = false, undoCount = 0) }
        viewModelScope.launch { reportLayoutFailure { undoManager.endSession() } }
    }

    fun openSettings() {
        _pageNavigationEvents.tryEmit(LauncherPage.PAGE_ID_SETTINGS)
    }

    fun closeSettings() {
        overlayState.update { it.copy(isSettingsOpen = false, statusMessage = null) }
        _pageNavigationEvents.tryEmit(LauncherPage.PAGE_ID_HOME)
    }

    // --- アイテム追加・移動・削除 (仕様 11, 13, 14, 20, 30) ---
    fun requestAddItemToPage(
        page: LauncherPage,
        preferredCell: GridPosition? = null,
        initialTab: Int = 0
    ) = runIfUnlocked {
        val targetPage = if (
            page.id == LauncherPage.PAGE_ID_DISCOVER ||
            page.id == LauncherPage.PAGE_ID_ALL_APPS ||
            page.id == LauncherPage.PAGE_ID_SETTINGS
        ) {
            LauncherPage.FIXED_HOME
        } else {
            page
        }
        overlayState.update {
            it.copy(
                itemPickerTarget = ItemPickerTarget.HomePageCell(
                    pageId = targetPage.id,
                    pageName = targetPage.name,
                    preferredCell = preferredCell,
                    initialTab = initialTab
                )
            )
        }
    }

    fun requestAddItemToDock() = runIfUnlocked {
        overlayState.update {
            it.copy(itemPickerTarget = ItemPickerTarget.Dock)
        }
    }

    fun dismissItemPicker() {
        overlayState.update { it.copy(itemPickerTarget = null) }
    }

    fun addAppFromPicker(app: AppInfo, isExpandedMode: Boolean) = runIfUnlocked {
        val target = overlayState.value.itemPickerTarget ?: ItemPickerTarget.HomePageCell(
            pageId = LauncherPage.PAGE_ID_HOME,
            pageName = "HOME"
        )
        launchLayoutEdit { snapshot ->
            when (target) {
                is ItemPickerTarget.HomePageCell -> {
                    if (target.pageId != LauncherPage.PAGE_ID_HOME && snapshot.userPages.none { it.id == target.pageId }) return@launchLayoutEdit
                    val pos = target.preferredCell ?: findFirstAvailableCell(target.pageId, isExpandedMode, snapshot)
                    val newItem = LayoutItem(
                        id = UUID.randomUUID().toString(),
                        pageId = target.pageId,
                        type = ItemType.APP,
                        packageName = app.packageName,
                        activityName = app.activityName,
                        label = app.label,
                        compact = pos,
                        expanded = if (isExpandedMode) pos else null
                    )
                    layoutRepository.upsertLayoutItem(newItem)
                }
                is ItemPickerTarget.Dock -> {
                    val currentDock = snapshot.dockItems
                    if (currentDock.size >= uiState.value.settings.effectiveDockIconCount) {
                        overlayState.update { it.copy(statusMessage = "Dockが満杯です。設定でアイコン数を増やしてください") }
                        return@launchLayoutEdit
                    }
                    val newDock = DockItem(
                        id = UUID.randomUUID().toString(),
                        positionIndex = currentDock.size,
                        type = ItemType.APP,
                        packageName = app.packageName,
                        activityName = app.activityName,
                        label = app.label
                    )
                    layoutRepository.upsertDockItem(newDock)
                }
            }
        }
    }

    fun addActionFromPicker(action: LauncherAction, isExpandedMode: Boolean) = runIfUnlocked {
        val target = overlayState.value.itemPickerTarget ?: return@runIfUnlocked
        launchLayoutEdit { snapshot ->
            when (target) {
                is ItemPickerTarget.HomePageCell -> {
                    if (target.pageId != LauncherPage.PAGE_ID_HOME && snapshot.userPages.none { it.id == target.pageId }) return@launchLayoutEdit
                    val pos = target.preferredCell ?: findFirstAvailableCell(target.pageId, isExpandedMode, snapshot)
                    val newItem = LayoutItem(
                        id = UUID.randomUUID().toString(),
                        pageId = target.pageId,
                        type = ItemType.ACTION,
                        packageName = action.companionPackageName.orEmpty(),
                        targetUri = action.actionId,
                        label = action.title,
                        compact = pos,
                        expanded = if (isExpandedMode) pos else null
                    )
                    layoutRepository.upsertLayoutItem(newItem)
                }
                is ItemPickerTarget.Dock -> {
                    val currentDock = snapshot.dockItems
                    if (currentDock.size >= uiState.value.settings.effectiveDockIconCount) {
                        overlayState.update { it.copy(statusMessage = "Dockが満杯です。設定でアイコン数を増やしてください") }
                        return@launchLayoutEdit
                    }
                    val newDock = DockItem(
                        id = UUID.randomUUID().toString(),
                        positionIndex = currentDock.size,
                        type = ItemType.ACTION,
                        packageName = action.companionPackageName.orEmpty(),
                        targetUri = action.actionId,
                        label = action.title
                    )
                    layoutRepository.upsertDockItem(newDock)
                }
            }
        }
    }

    fun addShortcutFromPicker(label: String, uri: String, isExpandedMode: Boolean) = runIfUnlocked {
        val target = overlayState.value.itemPickerTarget ?: return@runIfUnlocked
        launchLayoutEdit { snapshot ->
            when (target) {
                is ItemPickerTarget.HomePageCell -> {
                    if (target.pageId != LauncherPage.PAGE_ID_HOME && snapshot.userPages.none { it.id == target.pageId }) return@launchLayoutEdit
                    val pos = target.preferredCell ?: findFirstAvailableCell(target.pageId, isExpandedMode, snapshot)
                    val newItem = LayoutItem(
                        id = UUID.randomUUID().toString(),
                        pageId = target.pageId,
                        type = ItemType.SHORTCUT,
                        packageName = "",
                        targetUri = uri,
                        label = label,
                        compact = pos,
                        expanded = if (isExpandedMode) pos else null
                    )
                    layoutRepository.upsertLayoutItem(newItem)
                }
                is ItemPickerTarget.Dock -> {
                    val currentDock = snapshot.dockItems
                    if (currentDock.size >= uiState.value.settings.effectiveDockIconCount) {
                        overlayState.update { it.copy(statusMessage = "Dockが満杯です。設定でアイコン数を増やしてください") }
                        return@launchLayoutEdit
                    }
                    val newDock = DockItem(
                        id = UUID.randomUUID().toString(),
                        positionIndex = currentDock.size,
                        type = ItemType.SHORTCUT,
                        packageName = "",
                        targetUri = uri,
                        label = label
                    )
                    layoutRepository.upsertDockItem(newDock)
                }
            }
        }
    }

    // --- AppWidget 追加・バインド・設定・リサイズ制御 (仕様 30) ---
    fun addWidgetFromPicker(
        widget: WidgetProviderCatalogItem,
        isExpandedMode: Boolean
    ) = runIfUnlocked {
        val target = overlayState.value.itemPickerTarget as? ItemPickerTarget.HomePageCell
            ?: ItemPickerTarget.HomePageCell(
                pageId = LauncherPage.PAGE_ID_HOME,
                pageName = "HOME"
            )

        // 先に前回の未完了バインドがあれば解放
        pendingWidgetPlacement?.let { stale ->
            if (stale.appWidgetId > 0 && stale.appWidgetId !in committingWidgetIds) {
                widgetHostManager.deleteAppWidgetId(stale.appWidgetId)
            }
        }

        val appWidgetId = widgetHostManager.allocateAppWidgetId()
        if (appWidgetId <= 0) {
            overlayState.update {
                it.copy(statusMessage = "ウィジェットIDの割り当てに失敗しました")
            }
            return@runIfUnlocked
        }

        val pending = PendingWidgetPlacement(
            appWidgetId = appWidgetId,
            provider = widget.provider,
            label = widget.widgetLabel,
            pageId = target.pageId,
            preferredCell = target.preferredCell,
            spanX = widget.defaultSpanX,
            spanY = widget.defaultSpanY,
            isExpandedMode = isExpandedMode,
            existingItemId = null
        )
        pendingWidgetPlacement = pending

        val boundImmediately = widgetHostManager.tryBindAppWidget(appWidgetId, widget.provider)
        if (boundImmediately) {
            proceedAfterWidgetBound(pending)
        } else {
            _widgetSystemEvents.tryEmit(
                WidgetSystemEvent.RequestBindAppWidget(
                    appWidgetId = appWidgetId,
                    provider = widget.provider
                )
            )
        }
    }

    /**
     * バックアップ復元後などで未バインド状態のウィジェットをユーザーがタップした際、再バインドを実行する。
     */
    fun requestRebindExistingWidget(
        item: LayoutItem,
        isExpandedMode: Boolean
    ) {
        val providerInfo = widgetHostManager.findProviderInfo(item.packageName, item.activityName)
        if (providerInfo == null) {
            overlayState.update { it.copy(missingAppDialogTarget = item) }
            return
        }

        pendingWidgetPlacement?.takeIf { it.appWidgetId !in committingWidgetIds }?.let {
            widgetHostManager.deleteAppWidgetId(it.appWidgetId)
        }
        pendingWidgetPlacement = null
        val newWidgetId = widgetHostManager.allocateAppWidgetId()
        if (newWidgetId <= 0) return

        val pending = PendingWidgetPlacement(
            appWidgetId = newWidgetId,
            provider = providerInfo.provider,
            label = item.label,
            pageId = item.pageId,
            preferredCell = item.compact,
            spanX = item.spanX,
            spanY = item.spanY,
            isExpandedMode = isExpandedMode,
            existingItemId = item.id
        )
        pendingWidgetPlacement = pending

        val boundImmediately = widgetHostManager.tryBindAppWidget(newWidgetId, providerInfo.provider)
        if (boundImmediately) {
            proceedAfterWidgetBound(pending)
        } else {
            _widgetSystemEvents.tryEmit(
                WidgetSystemEvent.RequestBindAppWidget(
                    appWidgetId = newWidgetId,
                    provider = providerInfo.provider
                )
            )
        }
    }

    /**
     * バックアップ復元直後などに、システム許可なしでサイレント再バインド可能か1度だけ試行する。
     */
    fun trySilentAutoRebindWidget(item: LayoutItem) {
        if (!silentRebindAttemptedItemIds.add(item.id)) return
        viewModelScope.launch {
            val providerInfo = withContext(Dispatchers.IO) {
                widgetHostManager.findProviderInfo(item.packageName, item.activityName)
            } ?: return@launch
            val candidateId = widgetHostManager.allocateAppWidgetId()
            if (candidateId <= 0) return@launch
            committingWidgetIds.add(candidateId)
            var committed = false
            try {
                if (widgetHostManager.tryBindAppWidget(candidateId, providerInfo.provider)) {
                    reportLayoutFailure {
                        committed = undoManager.withoutHistory { snapshot ->
                            if (snapshot.items.none { it.id == item.id }) return@withoutHistory false
                            layoutRepository.updateWidgetId(item.id, candidateId)
                            true
                        }
                    }
                }
            } finally {
                committingWidgetIds.remove(candidateId)
                if (!committed) {
                    withContext(NonCancellable) {
                        if (candidateId !in layoutRepository.getLayoutSnapshot().widgetIds) widgetHostManager.deleteAppWidgetId(candidateId)
                    }
                }
            }
        }
    }

    /**
     * 配置済みウィジェットのコンテキストメニューから「⚙️ ウィジェットの設定」を開く。
     */
    fun requestConfigureExistingWidget(item: LayoutItem) {
        if (item.appWidgetId <= 0) return
        val info = widgetHostManager.getAppWidgetInfo(item.appWidgetId) ?: return
        if (info.configure != null) {
            pendingWidgetPlacement = null
            _widgetSystemEvents.tryEmit(
                WidgetSystemEvent.RequestConfigureAppWidget(item.appWidgetId)
            )
        }
    }

    /**
     * システムの `ACTION_APPWIDGET_BIND` ダイアログ結果を受け取る。
     */
    fun onWidgetBindActivityResult(granted: Boolean) {
        val pending = pendingWidgetPlacement ?: return
        if (pending.appWidgetId in committingWidgetIds) return
        if (granted) {
            proceedAfterWidgetBound(pending)
        } else {
            widgetHostManager.deleteAppWidgetId(pending.appWidgetId)
            pendingWidgetPlacement = null
        }
    }

    private fun proceedAfterWidgetBound(pending: PendingWidgetPlacement) {
        val info = widgetHostManager.getAppWidgetInfo(pending.appWidgetId)
        val needsConfigure = pending.existingItemId == null &&
            info?.configure != null &&
            !isConfigurationOptional(info)

        if (needsConfigure) {
            _widgetSystemEvents.tryEmit(
                WidgetSystemEvent.RequestConfigureAppWidget(pending.appWidgetId)
            )
        } else {
            commitPendingWidget(pending)
        }
    }

    /**
     * ウィジェットの Configuration Activity 完了結果を受け取る。
     */
    fun onWidgetConfigureActivityResult(success: Boolean) {
        val pending = pendingWidgetPlacement ?: return
        if (pending.appWidgetId in committingWidgetIds) return
        if (success) {
            commitPendingWidget(pending)
        } else {
            widgetHostManager.deleteAppWidgetId(pending.appWidgetId)
            pendingWidgetPlacement = null
        }
    }

    private fun commitPendingWidget(pending: PendingWidgetPlacement) {
        if (!committingWidgetIds.add(pending.appWidgetId)) return
        overlayState.update { it.copy(isWidgetPlacementPending = true) }
        viewModelScope.launch {
            var committed = false
            try {
                reportLayoutFailure {
                    if (pending.existingItemId != null) {
                        committed = undoManager.withoutHistory { snapshot ->
                            if (snapshot.items.none { it.id == pending.existingItemId }) return@withoutHistory false
                            layoutRepository.updateWidgetId(pending.existingItemId, pending.appWidgetId)
                            true
                        }
                    } else {
                        committed = undoManager.edit { snapshot ->
                            val settings = settingsRepository.settings.first()
                            if (settings.layoutLocked) return@edit false
                            if (pending.pageId != LauncherPage.PAGE_ID_HOME && snapshot.userPages.none { it.id == pending.pageId }) return@edit false
                            val cols = (if (pending.isExpandedMode) settings.expandedGridColumns else settings.compactGridColumns).coerceAtLeast(3)
                            val rows = (if (pending.isExpandedMode) settings.expandedGridRows else settings.compactGridRows).coerceAtLeast(3)
                            val (position, span) = findBestGridPlacementForSpan(
                                existingItems = snapshot.items.filter { it.pageId == pending.pageId },
                                requestedSpanX = pending.spanX,
                                requestedSpanY = pending.spanY,
                                preferredCell = pending.preferredCell,
                                isExpandedMode = pending.isExpandedMode,
                                columns = cols,
                                rows = rows
                            )
                            layoutRepository.upsertLayoutItem(LayoutItem(
                                id = UUID.randomUUID().toString(),
                                pageId = pending.pageId,
                                type = ItemType.WIDGET,
                                packageName = pending.provider.packageName,
                                activityName = pending.provider.className,
                                label = pending.label,
                                compact = position,
                                expanded = if (pending.isExpandedMode) position else null,
                                spanX = span.first,
                                spanY = span.second,
                                appWidgetId = pending.appWidgetId
                            ))
                            true
                        }
                        if (committed) _pageNavigationEvents.tryEmit(pending.pageId)
                    }
                }
            } finally {
                committingWidgetIds.remove(pending.appWidgetId)
                if (pendingWidgetPlacement == pending) pendingWidgetPlacement = null
                else overlayState.update { it.copy(isWidgetPlacementPending = pendingWidgetPlacement != null || committingWidgetIds.isNotEmpty()) }
                if (!committed) {
                    // DB書き込み直後のキャンセル時も、保存済みIDは削除しない。
                    withContext(NonCancellable) {
                        if (pending.appWidgetId !in layoutRepository.getLayoutSnapshot().widgetIds) {
                            widgetHostManager.deleteAppWidgetId(pending.appWidgetId)
                        }
                    }
                }
            }
        }
    }

    private fun isConfigurationOptional(info: AppWidgetProviderInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val features = info.widgetFeatures
        return (features and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0 &&
            (features and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0
    }

    fun openResizeWidgetDialog(item: LayoutItem) = runIfUnlocked {
        if (item.type == ItemType.FOLDER) enterEditMode()
        overlayState.update { it.copy(activeFolderId = null, resizingWidgetTarget = item) }
    }

    fun dismissResizeWidgetDialog() {
        overlayState.update { it.copy(resizingWidgetTarget = null) }
    }

    fun resizeWidgetItem(item: LayoutItem, newSpanX: Int, newSpanY: Int) = runIfUnlocked {
        launchLayoutEdit { snapshot ->
            val item = snapshot.items.find { it.id == item.id } ?: return@launchLayoutEdit
            if (item.type == ItemType.FOLDER) {
                val settings = settingsRepository.settings.first()
                val updated = snapshot.sizeFolder(item.id, newSpanX, newSpanY,
                    settings.compactGridColumns, settings.compactGridRows,
                    settings.expandedGridColumns, settings.expandedGridRows)
                if (updated == null) Toast.makeText(container.appContext, "そのサイズを置ける空きがありません", Toast.LENGTH_SHORT).show()
                else layoutRepository.restoreLayoutSnapshot(updated)
                return@launchLayoutEdit
            }
            val settings = uiState.value.settings
            val compactCols = settings.compactGridColumns.coerceAtLeast(3)
            val compactRows = settings.compactGridRows.coerceAtLeast(3)
            val expandedCols = settings.expandedGridColumns.coerceAtLeast(3)
            val expandedRows = settings.expandedGridRows.coerceAtLeast(3)

            val safeSpanX = newSpanX.coerceAtLeast(1)
            val safeSpanY = newSpanY.coerceAtLeast(1)

            val clampedCompact = GridPosition(
                x = item.compact.x.coerceIn(0, (compactCols - safeSpanX.coerceAtMost(compactCols)).coerceAtLeast(0)),
                y = item.compact.y.coerceIn(0, (compactRows - safeSpanY.coerceAtMost(compactRows)).coerceAtLeast(0))
            )
            val clampedExpanded = item.expanded?.let { exp ->
                GridPosition(
                    x = exp.x.coerceIn(0, (expandedCols - safeSpanX.coerceAtMost(expandedCols)).coerceAtLeast(0)),
                    y = exp.y.coerceIn(0, (expandedRows - safeSpanY.coerceAtMost(expandedRows)).coerceAtLeast(0))
                )
            }

            layoutRepository.updateItemSpan(
                itemId = item.id,
                spanX = safeSpanX,
                spanY = safeSpanY,
                adjustedCompactPosition = clampedCompact,
                adjustedExpandedPosition = clampedExpanded
            )
        }
    }

    fun quickAddAppToHome(app: AppInfo, isExpandedMode: Boolean, context: Context) = runIfUnlocked {
        launchLayoutEdit { snapshot ->
            val pos = findFirstAvailableCell(LauncherPage.PAGE_ID_HOME, isExpandedMode, snapshot)
            val newItem = LayoutItem(
                id = UUID.randomUUID().toString(),
                pageId = LauncherPage.PAGE_ID_HOME,
                type = ItemType.APP,
                packageName = app.packageName,
                activityName = app.activityName,
                label = app.label,
                compact = pos,
                expanded = if (isExpandedMode) pos else null
            )
            layoutRepository.upsertLayoutItem(newItem)
            Toast.makeText(context, "${app.label} をHOMEに追加しました", Toast.LENGTH_SHORT).show()
        }
    }

    fun quickAddAppToDock(app: AppInfo, context: Context) = runIfUnlocked {
        launchLayoutEdit { snapshot ->
            val currentDock = snapshot.dockItems
            if (currentDock.size >= uiState.value.settings.effectiveDockIconCount) {
                Toast.makeText(context, "Dockが満杯です。設定でアイコン数を増やしてください", Toast.LENGTH_SHORT).show()
                return@launchLayoutEdit
            }
            val newDock = DockItem(
                id = UUID.randomUUID().toString(),
                positionIndex = currentDock.size,
                type = ItemType.APP,
                packageName = app.packageName,
                activityName = app.activityName,
                label = app.label
            )
            layoutRepository.upsertDockItem(newDock)
            Toast.makeText(context, "${app.label} をDockに追加しました", Toast.LENGTH_SHORT).show()
        }
    }

    fun addSearchAppToHome(app: AppInfo, pageId: String, cell: GridPosition, isExpanded: Boolean) = runIfUnlocked {
        launchLayoutEdit { snapshot ->
            if (pageId != LauncherPage.PAGE_ID_HOME && snapshot.userPages.none { it.id == pageId }) return@launchLayoutEdit
            val settings = settingsRepository.settings.first()
            val columns = if (isExpanded) settings.expandedGridColumns else settings.compactGridColumns
            val rows = if (isExpanded) settings.expandedGridRows else settings.compactGridRows
            if (!canPlaceSearchApp(snapshot.items, pageId, cell, isExpanded, columns, rows)) {
                Toast.makeText(container.appContext, "空いているセルにドロップしてください", Toast.LENGTH_SHORT).show()
                return@launchLayoutEdit
            }
            layoutRepository.upsertLayoutItem(LayoutItem(
                id = UUID.randomUUID().toString(), pageId = pageId, type = ItemType.APP,
                packageName = app.packageName, activityName = app.activityName, label = app.label,
                compact = GridPosition(cell.x.coerceIn(0, settings.compactGridColumns - 1),
                    cell.y.coerceIn(0, settings.compactGridRows - 1)),
                expanded = if (isExpanded) cell else null
            ))
        }
    }

    fun addSearchAppToDock(app: AppInfo, index: Int) = runIfUnlocked {
        launchLayoutEdit { snapshot ->
            val limit = settingsRepository.settings.first().effectiveDockIconCount
            val item = DockItem(UUID.randomUUID().toString(), index, ItemType.APP,
                app.packageName, app.activityName, label = app.label)
            val result = insertSearchDockItem(snapshot.dockItems, item, index, limit)
            if (result == null) {
                Toast.makeText(container.appContext, "Dockが満杯です", Toast.LENGTH_SHORT).show()
            } else layoutRepository.replaceDockItems(result)
        }
    }

    fun closeFolder() { overlayState.update { it.copy(activeFolderId = null) } }

    fun renameFolder(id: String, name: String) = runIfUnlocked {
        enterEditMode()
        launchLayoutEdit { snapshot -> snapshot.renameFolder(id, name)?.let { layoutRepository.restoreLayoutSnapshot(it) } }
    }

    fun addAppToFolder(id: String, app: AppInfo) = runIfUnlocked {
        enterEditMode()
        launchLayoutEdit { snapshot ->
            snapshot.addFolderApp(id, FolderApp(UUID.randomUUID().toString(), app.packageName, app.activityName, app.label))
                ?.let { layoutRepository.restoreLayoutSnapshot(it) }
        }
    }

    fun launchFolderApp(folderId: String, app: FolderApp) {
        closeFolder()
        onLayoutItemClicked(LayoutItem(app.id, "folder:$folderId", ItemType.APP, app.packageName,
            app.activityName, label = app.label, compact = GridPosition(0, 0)), container.appDiscoveryRepository.isPackageInstalled(app.packageName))
    }

    fun groupAppIntoFolder(source: LayoutItem, fromDock: Boolean, copySource: Boolean, targetId: String) = runIfUnlocked {
        launchLayoutEdit { snapshot ->
            snapshot.groupApp(if (copySource) source.copy(id = UUID.randomUUID().toString()) else source,
                fromDock, copySource, targetId, UUID.randomUUID().toString())?.let { layoutRepository.restoreLayoutSnapshot(it) }
        }
    }

    fun moveHomeItemToDock(id: String, index: Int) = runIfUnlocked {
        launchLayoutEdit { snapshot ->
            val updated = snapshot.moveHomeToDock(id, index, settingsRepository.settings.first().effectiveDockIconCount)
            if (updated == null) Toast.makeText(container.appContext, "Dockが満杯です", Toast.LENGTH_SHORT).show()
            else layoutRepository.restoreLayoutSnapshot(updated)
        }
    }

    fun moveDockItemToHome(id: String, pageId: String, preferred: GridPosition, expanded: Boolean) = runIfUnlocked {
        launchLayoutEdit { snapshot ->
            val item = snapshot.dockItems.find { it.id == id } ?: return@launchLayoutEdit
            if (item.type !in setOf(ItemType.APP, ItemType.FOLDER)) return@launchLayoutEdit
            if (pageId != LauncherPage.PAGE_ID_HOME && snapshot.userPages.none { it.id == pageId }) return@launchLayoutEdit
            val settings = settingsRepository.settings.first()
            val compactCell = snapshot.freeFolderCell(pageId, false, settings.compactGridColumns, settings.compactGridRows,
                preferred.takeUnless { expanded })
            val expandedCell = snapshot.freeFolderCell(pageId, true, settings.expandedGridColumns, settings.expandedGridRows,
                preferred.takeIf { expanded })
            if (compactCell == null || expandedCell == null) {
                Toast.makeText(container.appContext, "ホームに空きがありません", Toast.LENGTH_SHORT).show()
                return@launchLayoutEdit
            }
            val home = item.asLayoutItem(pageId).copy(compact = compactCell, expanded = expandedCell)
            layoutRepository.restoreLayoutSnapshot(snapshot.copy(items = snapshot.items + home,
                dockItems = snapshot.dockItems.filterNot { it.id == id }.mapIndexed { i, entry -> entry.copy(positionIndex = i) }))
        }
    }

    fun extractFolderApp(id: String, memberId: String, toDock: Boolean) = runIfUnlocked {
        enterEditMode()
        launchLayoutEdit { snapshot ->
            val folder = snapshot.folder(id) ?: return@launchLayoutEdit
            val app = folder.folderApps.find { it.id == memberId } ?: return@launchLayoutEdit
            val remaining = snapshot.withFolderApps(id, folder.folderApps.filterNot { it.id == memberId })
            val settings = settingsRepository.settings.first()
            val updated = if (toDock) {
                if (remaining.dockItems.size >= settings.effectiveDockIconCount) null
                else remaining.copy(dockItems = remaining.dockItems + DockItem(UUID.randomUUID().toString(), remaining.dockItems.size,
                    ItemType.APP, app.packageName, app.activityName, label = app.label))
            } else {
                val pageId = snapshot.items.find { it.id == id }?.pageId ?: LauncherPage.PAGE_ID_HOME
                val compact = remaining.freeFolderCell(pageId, false, settings.compactGridColumns, settings.compactGridRows)
                val expanded = remaining.freeFolderCell(pageId, true, settings.expandedGridColumns, settings.expandedGridRows)
                if (compact == null || expanded == null) null else remaining.copy(items = remaining.items +
                    LayoutItem(UUID.randomUUID().toString(), pageId, ItemType.APP, app.packageName, app.activityName, label = app.label,
                        compact = compact, expanded = expanded))
            }
            if (updated == null) Toast.makeText(container.appContext, "取り出し先に空きがありません", Toast.LENGTH_SHORT).show()
            else layoutRepository.restoreLayoutSnapshot(updated)
        }
    }

    fun moveLayoutItem(
        item: LayoutItem,
        newPosition: GridPosition,
        isExpandedMode: Boolean,
        targetPageId: String = item.pageId
    ) = runIfUnlocked {
        launchLayoutEdit { snapshot ->
            val item = snapshot.items.find { it.id == item.id } ?: return@launchLayoutEdit
            if (targetPageId != LauncherPage.PAGE_ID_HOME && snapshot.userPages.none { it.id == targetPageId }) return@launchLayoutEdit
            if (item.type == ItemType.FOLDER && (targetPageId != item.pageId || item.spanX > 1 || item.spanY > 1)) {
                val settings = settingsRepository.settings.first()
                val compact = snapshot.folderSpace(item.id, targetPageId, false, settings.compactGridColumns, settings.compactGridRows,
                    item.spanX, item.spanY, if (isExpandedMode) item.compact else newPosition)
                val expanded = snapshot.folderSpace(item.id, targetPageId, true, settings.expandedGridColumns, settings.expandedGridRows,
                    item.spanX, item.spanY, if (isExpandedMode) newPosition else item.expanded ?: item.compact)
                if (compact == null || expanded == null) {
                    Toast.makeText(container.appContext, "移動先に空きがありません", Toast.LENGTH_SHORT).show()
                } else layoutRepository.upsertLayoutItem(item.copy(pageId = targetPageId, compact = compact, expanded = expanded))
                return@launchLayoutEdit
            }
            val state = layoutState(snapshot)
            val cols = if (isExpandedMode) state.settings.expandedGridColumns else state.settings.compactGridColumns
            val rows = if (isExpandedMode) state.settings.expandedGridRows else state.settings.compactGridRows
            val currentPos = item.resolveClampedPosition(isExpandedMode, cols, rows)

            val spanX = item.resolveSpanX(cols)
            val spanY = item.resolveSpanY(rows)
            val clampedTarget = GridPosition(
                x = newPosition.x.coerceIn(0, (cols - spanX).coerceAtLeast(0)),
                y = newPosition.y.coerceIn(0, (rows - spanY).coerceAtLeast(0))
            )

            if (item.type == ItemType.FOLDER && snapshot.items.any {
                it.id != item.id && it.pageId == targetPageId && (it.spanX > 1 || it.spanY > 1) &&
                    clampedTarget in it.occupiedCells(isExpandedMode, cols, rows)
            }) {
                Toast.makeText(container.appContext, "ウィジェット上には移動できません", Toast.LENGTH_SHORT).show()
                return@launchLayoutEdit
            }
            if (targetPageId == item.pageId) {
                // 同一ページ内の移動：1×1 アイテム同士で移動先セルに別の 1×1 アイテムがある場合は位置をスワップ
                if (spanX == 1 && spanY == 1) {
                    val occupyingItem = state.layoutItems
                        .filter { it.pageId == item.pageId && it.id != item.id && it.spanX == 1 && it.spanY == 1 }
                        .find { it.resolveClampedPosition(isExpandedMode, cols, rows) == clampedTarget }

                    if (occupyingItem != null) {
                        layoutRepository.updateItemPosition(occupyingItem.id, currentPos, isExpandedMode)
                    }
                }
                layoutRepository.updateItemPosition(item.id, clampedTarget, isExpandedMode)
            } else {
                // ページを跨いだ移動：移動先ページでドロップ位置または周辺の最適なセルに配置
                val targetPageItems = state.layoutItems.filter { it.pageId == targetPageId && it.id != item.id }
                val (assignedPos, _) = findBestGridPlacementForSpan(
                    existingItems = targetPageItems,
                    requestedSpanX = spanX,
                    requestedSpanY = spanY,
                    preferredCell = clampedTarget,
                    isExpandedMode = isExpandedMode,
                    columns = cols,
                    rows = rows
                )
                layoutRepository.updateItemPageAndPosition(
                    itemId = item.id,
                    targetPageId = targetPageId,
                    newPosition = assignedPos,
                    isExpandedMode = isExpandedMode
                )
            }
        }
    }

    /**
     * コンテキストメニュー等から指定ページ（または新規ページ）へアイテム・ウィジェットを移動し、そのページへジャンプする。
     */
    fun moveItemToAnotherPage(
        item: LayoutItem,
        targetPageId: String?,
        isExpandedMode: Boolean
    ) = runIfUnlocked {
        launchLayoutEdit { snapshot ->
            val item = snapshot.items.find { it.id == item.id } ?: return@launchLayoutEdit
            val destinationPageId = if (targetPageId.isNullOrBlank()) {
                layoutRepository.addUserPage("").id
            } else {
                if (targetPageId != LauncherPage.PAGE_ID_HOME && snapshot.userPages.none { it.id == targetPageId }) return@launchLayoutEdit
                targetPageId
            }
            if (item.type == ItemType.FOLDER) {
                val settings = settingsRepository.settings.first()
                val compact = snapshot.folderSpace(item.id, destinationPageId, false, settings.compactGridColumns, settings.compactGridRows, item.spanX, item.spanY, item.compact)
                val expanded = snapshot.folderSpace(item.id, destinationPageId, true, settings.expandedGridColumns, settings.expandedGridRows, item.spanX, item.spanY, item.expanded ?: item.compact)
                if (compact == null || expanded == null) {
                    Toast.makeText(container.appContext, "移動先に空きがありません", Toast.LENGTH_SHORT).show()
                } else {
                    layoutRepository.upsertLayoutItem(item.copy(pageId = destinationPageId, compact = compact, expanded = expanded))
                    _pageNavigationEvents.tryEmit(destinationPageId)
                }
                return@launchLayoutEdit
            }
            val state = layoutState(snapshot)
            val cols = if (isExpandedMode) state.settings.expandedGridColumns else state.settings.compactGridColumns
            val rows = if (isExpandedMode) state.settings.expandedGridRows else state.settings.compactGridRows
            val spanX = item.resolveSpanX(cols)
            val spanY = item.resolveSpanY(rows)
            val currentPos = item.resolveClampedPosition(isExpandedMode, cols, rows)
            val targetPageItems = state.layoutItems.filter { it.pageId == destinationPageId && it.id != item.id }
            val (assignedPos, _) = findBestGridPlacementForSpan(
                existingItems = targetPageItems,
                requestedSpanX = spanX,
                requestedSpanY = spanY,
                preferredCell = currentPos,
                isExpandedMode = isExpandedMode,
                columns = cols,
                rows = rows
            )
            layoutRepository.updateItemPageAndPosition(
                itemId = item.id,
                targetPageId = destinationPageId,
                newPosition = assignedPos,
                isExpandedMode = isExpandedMode
            )
            _pageNavigationEvents.tryEmit(destinationPageId)
        }
    }

    /**
     * ドラッグ中に右端の最終ホームページからさらに右へ移動しようとした際、新しいユーザーページを即座に作成する。
     */
    suspend fun createUserPageForDrag(): LauncherPage? {
        var created: LauncherPage? = null
        reportLayoutFailure {
            undoManager.edit {
                if (!settingsRepository.settings.first().layoutLocked && overlayState.value.isEditMode) {
                    created = layoutRepository.addUserPage("")
                }
            }
        }
        return created
    }

    fun deleteLayoutItem(item: LayoutItem) = runIfUnlocked {
        launchLayoutEdit { _ ->
            if (item.pageId == "dock") {
                layoutRepository.deleteDockItem(item.id)
            } else {
                layoutRepository.deleteLayoutItem(item.id)
            }
        }
    }

    fun removeDockItem(item: DockItem) = runIfUnlocked {
        launchLayoutEdit { _ ->
            layoutRepository.deleteDockItem(item.id)
        }
    }

    fun reorderDockItemByDrop(itemId: String, insertionIndex: Int) = runIfUnlocked {
        launchLayoutEdit { snapshot ->
            val reordered = reorderDockByInsertion(snapshot.dockItems, itemId, insertionIndex)
            if (reordered != snapshot.dockItems) layoutRepository.replaceDockItems(reordered)
        }
    }

    fun moveDockItem(item: DockItem, delta: Int) = runIfUnlocked {
        launchLayoutEdit { snapshot ->
            val current = snapshot.dockItems.toMutableList()
            val index = current.indexOfFirst { it.id == item.id }
            if (index == -1) return@launchLayoutEdit
            val targetIndex = (index + delta).coerceIn(0, current.lastIndex)
            if (targetIndex == index) return@launchLayoutEdit
            current.add(targetIndex, current.removeAt(index))
            layoutRepository.replaceDockItems(current)
        }
    }

    // --- ページ管理 (仕様 16) ---
    fun openPageManager() = runIfUnlocked {
        overlayState.update { it.copy(isPageManagerOpen = true) }
    }

    fun closePageManager() {
        overlayState.update { it.copy(isPageManagerOpen = false) }
    }

    fun addUserPage(name: String) = runIfUnlocked {
        launchLayoutEdit { _ ->
            val newPage = layoutRepository.addUserPage(name)
            _pageNavigationEvents.tryEmit(newPage.id)
        }
    }

    fun renameUserPage(pageId: String, newName: String) = runIfUnlocked {
        launchLayoutEdit { _ ->
            layoutRepository.renameUserPage(pageId, newName)
        }
    }

    fun deleteUserPage(pageId: String) = runIfUnlocked {
        launchLayoutEdit { _ ->
            layoutRepository.deleteUserPage(pageId)
        }
    }

    fun moveUserPage(pageId: String, delta: Int) = runIfUnlocked {
        launchLayoutEdit { snapshot ->
            val pages = snapshot.userPages.toMutableList()
            val index = pages.indexOfFirst { it.id == pageId }
            if (index == -1) return@launchLayoutEdit
            val targetIndex = (index + delta).coerceIn(0, pages.lastIndex)
            if (targetIndex == index) return@launchLayoutEdit
            pages.add(targetIndex, pages.removeAt(index))
            layoutRepository.reorderUserPages(pages.map { it.id })
        }
    }

    // --- 設定更新 ---
    fun setLayoutLocked(locked: Boolean) {
        launchWithoutUndo { _ ->
            settingsRepository.setLayoutLocked(locked)
            if (locked) {
                exitEditMode()
            }
        }
    }

    fun updateCompactGrid(columns: Int, rows: Int) {
        viewModelScope.launch {
            settingsRepository.setCompactGridSize(columns, rows)
        }
    }

    fun updateExpandedGrid(columns: Int, rows: Int) {
        viewModelScope.launch {
            settingsRepository.setExpandedGridSize(columns, rows)
        }
    }

    fun updateTinyIconsConfig(
        columnsCompact: Int,
        columnsExpanded: Int,
        sizeDp: Int,
        showLabels: Boolean
    ) {
        viewModelScope.launch {
            settingsRepository.setTinyIconsConfig(
                columnsCompact = columnsCompact,
                columnsExpanded = columnsExpanded,
                sizeDp = sizeDp,
                showLabels = showLabels
            )
        }
    }

    fun setSearchIndexEdgeDistanceDp(distanceDp: Int) {
        viewModelScope.launch { settingsRepository.setSearchIndexEdgeDistanceDp(distanceDp) }
    }

    fun setSwipeDownNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSwipeDownNotificationEnabled(enabled)
        }
    }

    fun setAllAppsPageEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAllAppsPageEnabled(enabled) }
    }

    fun setFeedCategoryEnabled(category: FeedCategory, enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFeedCategoryEnabled(category, enabled) }
    }

    fun setDiscoverMode(mode: DiscoverMode) {
        viewModelScope.launch {
            settingsRepository.setDiscoverMode(mode)
        }
    }

    fun setDockIconCount(count: Int) {
        viewModelScope.launch { settingsRepository.setDockIconCount(count) }
    }

    fun setExpandedDockPosition(position: ExpandedDockPosition) {
        viewModelScope.launch { settingsRepository.setExpandedDockPosition(position) }
    }

    fun setExpandedPageLayoutMode(mode: com.myenvironment.launcher.core.model.ExpandedPageLayoutMode) {
        viewModelScope.launch {
            settingsRepository.setExpandedPageLayoutMode(mode)
        }
    }

    fun setAllAppsLeftOnlyInExpandedSingle(leftOnly: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAllAppsLeftOnlyInExpandedSingle(leftOnly)
        }
    }

    fun setIndicatorStyle(style: IndicatorStyle) {
        viewModelScope.launch {
            settingsRepository.setIndicatorStyle(style)
        }
    }

    fun setFirstChimeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setFirstChimeEnabled(enabled)
        }
    }

    fun setReturnChimeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReturnChimeEnabled(enabled)
        }
    }

    fun setReturnChimeInterval(interval: ReturnChimeInterval) {
        viewModelScope.launch {
            settingsRepository.setReturnChimeInterval(interval)
        }
    }

    fun setTimeChimeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTimeChimeEnabled(enabled)
        }
    }

    // --- Backup & Restore (仕様 22, 23, 24) ---
    fun saveSnapshot(customName: String?) {
        viewModelScope.launch {
            val summary = backupManager.saveInternalSnapshot(customName)
            overlayState.update {
                it.copy(statusMessage = "バックアップ「${summary.name}」を保存しました")
            }
        }
    }

    fun restoreSnapshot(snapshotId: Long) {
        launchWithoutUndo { _ ->
            silentRebindAttemptedItemIds.clear()
            val result = backupManager.restoreInternalSnapshot(snapshotId)
            overlayState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { payload -> "バックアップ (${payload.createdAt}) を復元しました" },
                        onFailure = { err -> "復元に失敗しました: ${err.message}" }
                    )
                )
            }
        }
    }

    fun deleteSnapshot(snapshotId: Long) {
        viewModelScope.launch {
            backupManager.deleteInternalSnapshot(snapshotId)
        }
    }

    fun exportBackupToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val jsonString = backupManager.exportToJsonString()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(jsonString.toByteArray(Charsets.UTF_8))
                    } ?: error("出力ストリームを開けませんでした")
                }
            }
            overlayState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { "JSONファイルへエクスポートしました" },
                        onFailure = { e -> "エクスポート失敗: ${e.message}" }
                    )
                )
            }
        }
    }

    fun importBackupFromUri(context: Context, uri: Uri) {
        launchWithoutUndo { _ ->
            silentRebindAttemptedItemIds.clear()
            val result = runCatching {
                val (bytes, displayName) = withContext(Dispatchers.IO) {
                    val fileBytes = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes()
                    } ?: error("ファイルを読み込めませんでした")
                    val name = resolveDisplayName(context, uri)
                    fileBytes to name
                }
                val isNova = com.myenvironment.launcher.core.backup.NovaBackupConverter
                    .isNovaBackupOrSqlite(bytes, displayName)
                val payload = backupManager.importFromBackupBytes(bytes, displayName).getOrThrow()
                payload to isNova
            }
            overlayState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { (payload, isNova) ->
                            val totalIcons = payload.pages.sumOf { p -> p.items.size }
                            val gridCols = payload.settings.compactGridColumns
                            val gridRows = payload.settings.compactGridRows
                            if (isNova) {
                                "Novaバックアップを変換・インポートしました (${payload.pages.size}ページ / ${totalIcons}アイコン / グリッド ${gridCols}列×${gridRows}行)"
                            } else {
                                "ファイルから復元しました (${payload.pages.size}ページ / ${totalIcons}アイテム / グリッド ${gridCols}列×${gridRows}行)"
                            }
                        },
                        onFailure = { e -> "インポート失敗: ${e.message}" }
                    )
                )
            }
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            } ?: uri.lastPathSegment
        }.getOrNull() ?: uri.lastPathSegment
    }

    fun openJsonBackupPreview() {
        viewModelScope.launch {
            val json = backupManager.exportToJsonString()
            overlayState.update { it.copy(jsonPreviewContent = json) }
        }
    }

    fun closeJsonBackupPreview() {
        overlayState.update { it.copy(jsonPreviewContent = null) }
    }

    fun restoreFromRawJson(jsonText: String) {
        launchWithoutUndo { _ ->
            silentRebindAttemptedItemIds.clear()
            val result = backupManager.restoreFromJsonString(jsonText)
            overlayState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { "JSONテキストからレイアウトを復元しました" },
                        onFailure = { e -> "JSON復元エラー: ${e.message}" }
                    )
                )
            }
        }
    }

    fun addDemoMissingAppPlaceholder(isExpandedMode: Boolean) {
        launchWithoutUndo { snapshot ->
            val pos = findFirstAvailableCell(LauncherPage.PAGE_ID_HOME, isExpandedMode, snapshot)
            val missingItem = LayoutItem(
                id = UUID.randomUUID().toString(),
                pageId = LauncherPage.PAGE_ID_HOME,
                type = ItemType.APP,
                packageName = "com.spotify.music",
                activityName = "com.spotify.music.MainActivity",
                label = "Spotify",
                compact = pos,
                expanded = if (isExpandedMode) pos else null
            )
            layoutRepository.upsertLayoutItem(missingItem)
            overlayState.update {
                it.copy(statusMessage = "HOME画面に Spotify (Placeholder検証用) を追加しました")
            }
        }
    }

    fun clearStatusMessage() {
        overlayState.update { it.copy(statusMessage = null) }
    }

    // --- アプリ内自己アップデート (GitHub Releases 連携) ---

    fun checkForAppUpdate() {
        appUpdateManager.checkForUpdates(manual = true)
    }

    fun downloadAndInstallAppUpdate(release: ReleaseUpdateInfo) {
        appUpdateManager.downloadAndInstallRelease(release)
    }

    fun installDownloadedApk(apkFilePath: String) {
        appUpdateManager.triggerPackageInstaller(File(apkFilePath))
    }

    fun openGitHubReleasesPage(url: String = AppUpdateState.GITHUB_RELEASES_PAGE_URL) {
        appUpdateManager.openUrlInBrowser(url)
    }

    fun openUnknownAppSourcesSettings() {
        appUpdateManager.openUnknownSourcesSettings()
    }

    private fun findFirstAvailableCell(pageId: String, isExpandedMode: Boolean, snapshot: LayoutSnapshot? = null): GridPosition {
        val state = snapshot?.let(::layoutState) ?: uiState.value
        val cols = (if (isExpandedMode) state.settings.expandedGridColumns else state.settings.compactGridColumns).coerceAtLeast(3)
        val rows = (if (isExpandedMode) state.settings.expandedGridRows else state.settings.compactGridRows).coerceAtLeast(3)
        val pageItems = state.layoutItems.filter { it.pageId == pageId }
        return findBestGridPlacementForSpan(
            existingItems = pageItems,
            requestedSpanX = 1,
            requestedSpanY = 1,
            preferredCell = null,
            isExpandedMode = isExpandedMode,
            columns = cols,
            rows = rows
        ).first
    }

    companion object {
        /**
         * 指定されたセル幅・高さ (`requestedSpanX` × `requestedSpanY`) が既存アイテムと重ならず配置できる
         * 最適な左上座標と有効スパン `(GridPosition, Pair<spanX, spanY>)` を返す。
         */
        internal fun findBestGridPlacementForSpan(
            existingItems: List<LayoutItem>,
            requestedSpanX: Int,
            requestedSpanY: Int,
            preferredCell: GridPosition?,
            isExpandedMode: Boolean,
            columns: Int,
            rows: Int
        ): Pair<GridPosition, Pair<Int, Int>> {
            val safeCols = columns.coerceAtLeast(1)
            val safeRows = rows.coerceAtLeast(1)
            val targetW = requestedSpanX.coerceIn(1, safeCols)
            val targetH = requestedSpanY.coerceIn(1, safeRows)

            val occupied = HashSet<GridPosition>()
            existingItems.forEach { item ->
                occupied.addAll(item.occupiedCells(isExpandedMode, safeCols, safeRows))
            }

            fun canFitAt(startX: Int, startY: Int, w: Int, h: Int): Boolean {
                if (startX < 0 || startY < 0 || startX + w > safeCols || startY + h > safeRows) {
                    return false
                }
                for (dy in 0 until h) {
                    for (dx in 0 until w) {
                        if (occupied.contains(GridPosition(startX + dx, startY + dy))) {
                            return false
                        }
                    }
                }
                return true
            }

            // 1. ユーザーが空白セルを指定していた場合、そのセルを左上にして収まるか確認
            if (preferredCell != null) {
                val clampedX = preferredCell.x.coerceIn(0, (safeCols - targetW).coerceAtLeast(0))
                val clampedY = preferredCell.y.coerceIn(0, (safeRows - targetH).coerceAtLeast(0))
                if (canFitAt(clampedX, clampedY, targetW, targetH)) {
                    return GridPosition(clampedX, clampedY) to (targetW to targetH)
                }
            }

            // 2. 要求された (targetW × targetH) がそのまま収まる空き領域を探索
            for (y in 0..(safeRows - targetH)) {
                for (x in 0..(safeCols - targetW)) {
                    if (canFitAt(x, y, targetW, targetH)) {
                        return GridPosition(x, y) to (targetW to targetH)
                    }
                }
            }

            // 3. 要求サイズで空きがない場合、段階的にサイズを縮小して既存アイテムと重ならない最大領域を探す
            for (h in targetH downTo 1) {
                for (w in targetW downTo 1) {
                    if (preferredCell != null && canFitAt(preferredCell.x, preferredCell.y, w, h)) {
                        return preferredCell to (w to h)
                    }
                    for (y in 0..(safeRows - h)) {
                        for (x in 0..(safeCols - w)) {
                            if (canFitAt(x, y, w, h)) {
                                return GridPosition(x, y) to (w to h)
                            }
                        }
                    }
                }
            }

            // 4. 全セルが埋まっている場合のフォールバック
            val fallbackPos = preferredCell?.let {
                GridPosition(
                    x = it.x.coerceIn(0, (safeCols - targetW).coerceAtLeast(0)),
                    y = it.y.coerceIn(0, (safeRows - targetH).coerceAtLeast(0))
                )
            } ?: GridPosition(0, 0)
            return fallbackPos to (targetW to targetH)
        }

        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LauncherViewModel(container) as T
                }
            }
    }
}

internal fun canPlaceSearchApp(items: List<LayoutItem>, pageId: String, cell: GridPosition,
    isExpanded: Boolean, columns: Int, rows: Int): Boolean =
    cell.x in 0 until columns && cell.y in 0 until rows &&
        items.none { it.pageId == pageId && cell in it.occupiedCells(isExpanded, columns, rows) }

internal fun insertSearchDockItem(items: List<DockItem>, item: DockItem, index: Int, limit: Int): List<DockItem>? {
    if (items.size >= limit) return null
    return items.sortedBy { it.positionIndex }.toMutableList().apply {
        add(index.coerceIn(0, size), item)
    }.mapIndexed { position, entry -> entry.copy(positionIndex = position) }
}

internal fun reorderDockByInsertion(items: List<DockItem>, itemId: String, insertionIndex: Int): List<DockItem> {
    val ordered = items.sortedBy { it.positionIndex }.toMutableList()
    val from = ordered.indexOfFirst { it.id == itemId }
    if (from < 0) return items
    val insertion = insertionIndex.coerceIn(0, ordered.size)
    val item = ordered.removeAt(from)
    ordered.add((if (insertion > from) insertion - 1 else insertion).coerceIn(0, ordered.size), item)
    return ordered.mapIndexed { index, entry -> entry.copy(positionIndex = index) }
}

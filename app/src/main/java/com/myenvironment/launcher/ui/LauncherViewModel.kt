package com.myenvironment.launcher.ui

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
import com.myenvironment.launcher.core.launcher.MissingAppResolver
import com.myenvironment.launcher.core.model.AppInfo
import com.myenvironment.launcher.core.model.BackupSnapshotSummary
import com.myenvironment.launcher.core.model.DiscoverMode
import com.myenvironment.launcher.core.model.DockItem
import com.myenvironment.launcher.core.model.GridPosition
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LauncherAction
import com.myenvironment.launcher.core.model.LauncherPage
import com.myenvironment.launcher.core.model.LauncherSettings
import com.myenvironment.launcher.core.model.LayoutItem
import com.myenvironment.launcher.core.widget.WidgetProviderCatalogItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val isEditMode: Boolean = false,
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
    val statusMessage: String? = null
)

/**
 * Launcher全体の統合UIステート
 */
data class LauncherUiState(
    val installedApps: List<AppInfo> = emptyList(),
    val installedPackages: Set<String> = emptySet(),
    val availableWidgets: List<WidgetProviderCatalogItem> = emptyList(),
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
    val widgetHostManager = container.widgetHostManager
    val searchEngine = container.searchEngine
    val feedBridge = container.feedBridge

    private val overlayState = MutableStateFlow(OverlayControlState())
    private val availableWidgetsState = MutableStateFlow<List<WidgetProviderCatalogItem>>(emptyList())

    private var pendingWidgetPlacement: PendingWidgetPlacement? = null
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
        ) { snaps, overlay, widgets -> Triple(snaps, overlay, widgets) }
    ) { (apps, pkgs, uPages), (items, dock, settings), (snaps, overlay, widgets) ->
        val fixedLeftAndHome = buildList {
            if (settings.discoverMode != DiscoverMode.DISABLED) {
                add(LauncherPage.FIXED_DISCOVER)
            }
            add(LauncherPage.FIXED_ALL_APPS)
            add(LauncherPage.FIXED_HOME)
        }
        // 一番右にスワイプした時にマイランチャー設定ページが出るように末尾へ配置
        val allPages = fixedLeftAndHome + uPages.sortedBy { it.sortOrder } + LauncherPage.FIXED_SETTINGS
        val homeIdx = allPages.indexOfFirst { it.id == LauncherPage.PAGE_ID_HOME }.coerceAtLeast(0)

        LauncherUiState(
            installedApps = apps,
            installedPackages = pkgs,
            availableWidgets = widgets,
            pages = allPages,
            userPages = uPages.sortedBy { it.sortOrder },
            homePageIndex = homeIdx,
            layoutItems = items,
            dockItems = dock,
            settings = settings,
            snapshots = snaps,
            overlay = overlay
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LauncherUiState()
    )

    init {
        viewModelScope.launch {
            layoutRepository.ensureInitialized()
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
     * AndroidのHome操作が実行された際、すべてのオーバーレイを閉じてHOMEページへ戻す (仕様 4)
     */
    fun onHomeGestureInvoked() {
        overlayState.update {
            it.copy(
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
        _pageNavigationEvents.tryEmit(LauncherPage.PAGE_ID_HOME)
    }

    fun jumpToPage(pageId: String) {
        _pageNavigationEvents.tryEmit(pageId)
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

    fun openDefaultHomeSettings() {
        appLauncher.openDefaultHomeSettings()
    }

    fun openAppInfo(packageName: String) {
        appLauncher.openAppDetailsSettings(packageName)
    }

    // --- アプリ / Shortcut / Action / Widget / Placeholder 起動 (仕様 12, 24, 25, 30) ---
    fun onLayoutItemClicked(item: LayoutItem, isInstalled: Boolean) {
        when (item.type) {
            ItemType.APP -> {
                if (!isInstalled) {
                    // 未インストールPlaceholderの場合はPlayストア連携ダイアログを表示 (仕様 25)
                    overlayState.update { it.copy(missingAppDialogTarget = item) }
                } else {
                    val launched = appLauncher.launchApp(item.packageName, item.activityName)
                    if (!launched) {
                        overlayState.update { it.copy(missingAppDialogTarget = item) }
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
                    appLauncher.launchApp(item.packageName, item.activityName)
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
        appLauncher.launchApp(app)
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
        viewModelScope.launch {
            if (missingItem.pageId == "dock") {
                val existingDock = uiState.value.dockItems.find { it.id == missingItem.id }
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
                val existingLayout = uiState.value.layoutItems.find { it.id == missingItem.id } ?: missingItem
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
        viewModelScope.launch {
            val state = uiState.value
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
            LauncherAction.HATENA_FEED, LauncherAction.MY_NOTIFICATIONS -> {
                appLauncher.launchCompanionAppOrFallback(action)
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
        overlayState.update { it.copy(isEditMode = true) }
    }

    fun toggleEditMode() {
        if (overlayState.value.isEditMode) {
            overlayState.update { it.copy(isEditMode = false) }
        } else {
            runIfUnlocked {
                overlayState.update { it.copy(isEditMode = true) }
            }
        }
    }

    fun exitEditMode() {
        overlayState.update { it.copy(isEditMode = false) }
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
        viewModelScope.launch {
            when (target) {
                is ItemPickerTarget.HomePageCell -> {
                    val pos = target.preferredCell ?: findFirstAvailableCell(target.pageId, isExpandedMode)
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
                    val currentDock = uiState.value.dockItems
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
        viewModelScope.launch {
            when (target) {
                is ItemPickerTarget.HomePageCell -> {
                    val pos = target.preferredCell ?: findFirstAvailableCell(target.pageId, isExpandedMode)
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
                    val currentDock = uiState.value.dockItems
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
        viewModelScope.launch {
            when (target) {
                is ItemPickerTarget.HomePageCell -> {
                    val pos = target.preferredCell ?: findFirstAvailableCell(target.pageId, isExpandedMode)
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
                    val currentDock = uiState.value.dockItems
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
            if (stale.existingItemId == null && stale.appWidgetId > 0) {
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

            val bound = widgetHostManager.tryBindAppWidget(candidateId, providerInfo.provider)
            if (bound) {
                if (item.appWidgetId > 0 && item.appWidgetId != candidateId) {
                    widgetHostManager.deleteAppWidgetId(item.appWidgetId)
                }
                layoutRepository.updateWidgetId(item.id, candidateId)
            } else {
                widgetHostManager.deleteAppWidgetId(candidateId)
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
        if (success) {
            commitPendingWidget(pending)
        } else {
            widgetHostManager.deleteAppWidgetId(pending.appWidgetId)
            pendingWidgetPlacement = null
        }
    }

    private fun commitPendingWidget(pending: PendingWidgetPlacement) {
        pendingWidgetPlacement = null
        viewModelScope.launch {
            if (pending.existingItemId != null) {
                layoutRepository.updateWidgetId(pending.existingItemId, pending.appWidgetId)
                return@launch
            }

            val state = uiState.value
            val cols = if (pending.isExpandedMode) {
                state.settings.expandedGridColumns
            } else {
                state.settings.compactGridColumns
            }.coerceAtLeast(3)
            val rows = if (pending.isExpandedMode) {
                state.settings.expandedGridRows
            } else {
                state.settings.compactGridRows
            }.coerceAtLeast(3)

            val pageItems = state.layoutItems.filter { it.pageId == pending.pageId }
            val (assignedPos, assignedSpan) = findBestGridPlacementForSpan(
                existingItems = pageItems,
                requestedSpanX = pending.spanX,
                requestedSpanY = pending.spanY,
                preferredCell = pending.preferredCell,
                isExpandedMode = pending.isExpandedMode,
                columns = cols,
                rows = rows
            )

            val newItem = LayoutItem(
                id = UUID.randomUUID().toString(),
                pageId = pending.pageId,
                type = ItemType.WIDGET,
                packageName = pending.provider.packageName,
                activityName = pending.provider.className,
                label = pending.label,
                compact = assignedPos,
                expanded = if (pending.isExpandedMode) assignedPos else null,
                spanX = assignedSpan.first,
                spanY = assignedSpan.second,
                appWidgetId = pending.appWidgetId
            )
            layoutRepository.upsertLayoutItem(newItem)
            _pageNavigationEvents.tryEmit(pending.pageId)
        }
    }

    private fun isConfigurationOptional(info: AppWidgetProviderInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val features = info.widgetFeatures
        return (features and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0 &&
            (features and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0
    }

    fun openResizeWidgetDialog(item: LayoutItem) = runIfUnlocked {
        overlayState.update { it.copy(resizingWidgetTarget = item) }
    }

    fun dismissResizeWidgetDialog() {
        overlayState.update { it.copy(resizingWidgetTarget = null) }
    }

    fun resizeWidgetItem(item: LayoutItem, newSpanX: Int, newSpanY: Int) = runIfUnlocked {
        viewModelScope.launch {
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
        viewModelScope.launch {
            val pos = findFirstAvailableCell(LauncherPage.PAGE_ID_HOME, isExpandedMode)
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
        viewModelScope.launch {
            val currentDock = uiState.value.dockItems
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

    fun moveLayoutItem(
        item: LayoutItem,
        newPosition: GridPosition,
        isExpandedMode: Boolean,
        targetPageId: String = item.pageId
    ) = runIfUnlocked {
        viewModelScope.launch {
            val state = uiState.value
            val cols = if (isExpandedMode) state.settings.expandedGridColumns else state.settings.compactGridColumns
            val rows = if (isExpandedMode) state.settings.expandedGridRows else state.settings.compactGridRows
            val currentPos = item.resolveClampedPosition(isExpandedMode, cols, rows)

            val spanX = item.resolveSpanX(cols)
            val spanY = item.resolveSpanY(rows)
            val clampedTarget = GridPosition(
                x = newPosition.x.coerceIn(0, (cols - spanX).coerceAtLeast(0)),
                y = newPosition.y.coerceIn(0, (rows - spanY).coerceAtLeast(0))
            )

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
        viewModelScope.launch {
            val destinationPageId = if (targetPageId.isNullOrBlank()) {
                layoutRepository.addUserPage("").id
            } else {
                targetPageId
            }
            val state = uiState.value
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
        if (uiState.value.settings.layoutLocked) return null
        return layoutRepository.addUserPage("")
    }

    fun deleteLayoutItem(item: LayoutItem) = runIfUnlocked {
        viewModelScope.launch {
            if (item.pageId == "dock") {
                layoutRepository.deleteDockItem(item.id)
            } else {
                if (item.type == ItemType.WIDGET && item.appWidgetId > 0) {
                    widgetHostManager.deleteAppWidgetId(item.appWidgetId)
                }
                layoutRepository.deleteLayoutItem(item.id)
            }
        }
    }

    fun removeDockItem(item: DockItem) = runIfUnlocked {
        viewModelScope.launch {
            layoutRepository.deleteDockItem(item.id)
        }
    }

    fun moveDockItem(item: DockItem, delta: Int) = runIfUnlocked {
        val current = uiState.value.dockItems.toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index == -1) return@runIfUnlocked
        val targetIndex = (index + delta).coerceIn(0, current.lastIndex)
        if (targetIndex == index) return@runIfUnlocked

        val removed = current.removeAt(index)
        current.add(targetIndex, removed)
        viewModelScope.launch {
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
        viewModelScope.launch {
            val newPage = layoutRepository.addUserPage(name)
            _pageNavigationEvents.tryEmit(newPage.id)
        }
    }

    fun renameUserPage(pageId: String, newName: String) = runIfUnlocked {
        viewModelScope.launch {
            layoutRepository.renameUserPage(pageId, newName)
        }
    }

    fun deleteUserPage(pageId: String) = runIfUnlocked {
        viewModelScope.launch {
            // 削除対象ページ内の AppWidget ID も解放する
            uiState.value.layoutItems
                .filter { it.pageId == pageId && it.type == ItemType.WIDGET && it.appWidgetId > 0 }
                .forEach { widgetHostManager.deleteAppWidgetId(it.appWidgetId) }
            layoutRepository.deleteUserPage(pageId)
        }
    }

    fun moveUserPage(pageId: String, delta: Int) = runIfUnlocked {
        val pages = uiState.value.userPages.toMutableList()
        val idx = pages.indexOfFirst { it.id == pageId }
        if (idx == -1) return@runIfUnlocked
        val targetIdx = (idx + delta).coerceIn(0, pages.lastIndex)
        if (targetIdx == idx) return@runIfUnlocked

        val page = pages.removeAt(idx)
        pages.add(targetIdx, page)
        viewModelScope.launch {
            layoutRepository.reorderUserPages(pages.map { it.id })
        }
    }

    // --- 設定更新 ---
    fun setLayoutLocked(locked: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLayoutLocked(locked)
            if (locked) {
                overlayState.update { it.copy(isEditMode = false) }
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

    fun setSwipeDownNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSwipeDownNotificationEnabled(enabled)
        }
    }

    fun setDiscoverMode(mode: DiscoverMode) {
        viewModelScope.launch {
            settingsRepository.setDiscoverMode(mode)
        }
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
            val pos = findFirstAvailableCell(LauncherPage.PAGE_ID_HOME, isExpandedMode)
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

    private fun findFirstAvailableCell(pageId: String, isExpandedMode: Boolean): GridPosition {
        val state = uiState.value
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

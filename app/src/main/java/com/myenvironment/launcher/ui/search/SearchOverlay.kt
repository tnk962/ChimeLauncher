package com.myenvironment.launcher.ui.search

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import com.myenvironment.launcher.core.launcher.AppDiscoveryRepository
import com.myenvironment.launcher.core.model.AppInfo
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LauncherAction
import com.myenvironment.launcher.core.model.LayoutItem
import com.myenvironment.launcher.core.search.AppUsageMetric
import com.myenvironment.launcher.core.search.SearchEngine
import com.myenvironment.launcher.core.search.AppIndexLayout
import com.myenvironment.launcher.core.search.AppListIndex
import com.myenvironment.launcher.ui.components.appDragState
import com.myenvironment.launcher.ui.components.launcherDragSource
import com.myenvironment.launcher.ui.home.DragOrigin
import com.myenvironment.launcher.ui.components.LauncherItemGraphic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/**
 * Swipe Up Search オーバーレイ画面 (仕様 8, 9, Chime Launcher 仕様 21〜27)
 *
 * - Zero Query State (`query.isBlank()`):
 *   - `Recently Used`
 *   - `Frequently Used`
 *   - `Recently Installed`
 *   をコンパクトな候補として表示し、その下に索引付きの全アプリ縦一覧を表示する。
 * - 検索文字入力後 (`query.isNotBlank()`):
 *   - ゼロクエリ候補と検索結果を混在させず、通常のアプリ検索結果へ即座に切り替える。
 */
@Composable
fun SearchOverlay(
    installedApps: List<AppInfo>,
    configuredShortcuts: List<LayoutItem>,
    usageMap: Map<String, AppUsageMetric> = emptyMap(),
    hasUsageAccessPermission: Boolean = true,
    searchEngine: SearchEngine,
    appDiscoveryRepository: AppDiscoveryRepository,
    onLaunchApp: (AppInfo) -> Unit,
    onLaunchShortcut: (LayoutItem) -> Unit,
    onTriggerAction: (LauncherAction) -> Unit,
    onGoogleSearch: (String) -> Unit,
    onRequestUsageAccess: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var fieldRightPx by remember { mutableStateOf<Float?>(null) }
    var contentRightPx by remember { mutableStateOf<Float?>(null) }
    var contentTopPx by remember { mutableStateOf(0f) }
    var initialHeadingCenterPx by remember { mutableStateOf<Float?>(null) }
    var currentHeadingCenterPx by remember { mutableStateOf<Float?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val searchResults = remember(query, installedApps, configuredShortcuts, usageMap) {
        searchEngine.search(
            query = query,
            installedApps = installedApps,
            configuredShortcuts = configuredShortcuts,
            usageMap = usageMap
        )
    }

    val isZeroQuery = query.trim().isEmpty()
    val listState = rememberLazyListState()
    var indexJumpJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val indexedApps = remember(installedApps) { AppListIndex.sorted(installedApps) }
    LaunchedEffect(listState, keyboardController) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) keyboardController?.hide()
        }
    }


    LaunchedEffect(query) { listState.scrollToItem(0) }

    // 検索開始直後に TextField へ Focus & ソフトキーボード表示 (仕様 8.1)
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        delay(60)
        keyboardController?.show()
    }

    Surface(
        color = Color(0xEE101217),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 検索入力バー
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "クリア")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (!isZeroQuery) {
                                val topApp = searchResults.matchingApps.firstOrNull()
                                if (topApp != null) {
                                    onLaunchApp(topApp)
                                    onDismiss()
                                } else {
                                    onGoogleSearch(query)
                                    onDismiss()
                                }
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onGloballyPositioned { fieldRightPx = it.boundsInRoot().right }
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "検索を閉じる",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isZeroQuery) {
                val zeroSections = searchResults.zeroQuerySections
                val suggestions = listOf(
                    "最近使ったアプリ" to zeroSections.recentlyUsed,
                    "よく使うアプリ" to zeroSections.frequentlyUsed,
                    "最近インストールしたアプリ" to zeroSections.recentlyInstalled
                ).filter { it.second.isNotEmpty() }
                val positions = remember(indexedApps, suggestions.size) {
                    AppListIndex.positions(indexedApps, suggestions.size + 1)
                }
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()
                    .onGloballyPositioned {
                        contentRightPx = it.boundsInRoot().right
                        contentTopPx = it.boundsInRoot().top
                    }) {
                    val density = LocalDensity.current
                    val trailingGap = with(density) {
                        ((contentRightPx ?: 0f) - (fieldRightPx ?: 0f)).coerceAtLeast(0f).toDp()
                    }
                    val imeBottom = WindowInsets.ime.getBottom(density)
                    var initialGeometry by remember(maxWidth) { mutableStateOf<AppIndexLayout.Geometry?>(null) }
                    LaunchedEffect(maxHeight, imeBottom, currentHeadingCenterPx) {
                        if (initialGeometry == null || initialHeadingCenterPx == null) {
                            // Wait for the initial IME animation to settle, then retain this
                            // geometry for the search session even when the keyboard closes.
                            delay(if (imeBottom > 0) 180 else 650)
                            if (initialGeometry == null) initialGeometry = AppIndexLayout.initialGeometry(maxHeight.value)
                            initialHeadingCenterPx = currentHeadingCenterPx
                        }
                    }
                    val sizing = initialGeometry ?: AppIndexLayout.initialGeometry(maxHeight.value)
                    val headingOffsetDp = (initialHeadingCenterPx ?: currentHeadingCenterPx)?.let { center ->
                        with(density) { (center - contentTopPx).toDp().value }
                    }
                    val geometry = sizing.copy(topDp = headingOffsetDp?.let {
                        AppIndexLayout.anchorTopDp(it, sizing.heightDp, AppListIndex.labels.size)
                    } ?: sizing.topDp)
                    Row(Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            suggestions.forEach { (title, apps) ->
                                item(key = "suggestion_$title") {
                                    ZeroQueryAppRowSection(title, apps, appDiscoveryRepository,
                                        { onLaunchApp(it); onDismiss() }, { searchAppDragModifier(it) })
                                }
                            }
                            item(key = "all_apps_title") {
                                SearchSectionHeader("すべてのアプリ (${indexedApps.size})",
                                    textModifier = Modifier.onGloballyPositioned {
                                        if (initialHeadingCenterPx == null) {
                                            currentHeadingCenterPx = it.boundsInRoot().center.y
                                        }
                                    })
                            }
                            indexedApps.groupBy { AppListIndex.section(it.label) }.forEach { (section, apps) ->
                                item(key = "index_$section") {
                                    SearchSectionHeader(if (section == "#") "その他" else section)
                                }
                                items(apps, key = { "all_${it.componentKey}:${it.userSerialNumber}" }) { app ->
                                    SearchAppRow(app, appDiscoveryRepository) {
                                        onLaunchApp(app); onDismiss()
                                    }
                                }
                            }
                            if (!hasUsageAccessPermission) {
                                item(key = "zero_usage_permission_hint") {
                                    TextButton(onClick = onRequestUsageAccess,
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
                                        Text("端末全体の利用履歴も反映する (使用状況へのアクセス設定)",
                                            color = Color(0xFF9AA0A6), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        CompactAppIndex(
                            positions = positions,
                            trailingGap = trailingGap,
                            geometry = geometry,
                            onSelect = { target ->
                                indexJumpJob?.cancel()
                                indexJumpJob = scope.launch { listState.scrollToItem(target) }
                            },
                            onTouchStart = {
                                // Retain the current rail before IME dismissal changes constraints.
                                if (initialGeometry == null) initialGeometry = sizing
                                if (initialHeadingCenterPx == null) initialHeadingCenterPx = currentHeadingCenterPx
                                keyboardController?.hide()
                            }
                        )
                    }
                }
            } else {
                // --- 検索文字入力後: 通常のアプリ検索結果を最優先表示 (ゼロクエリ候補と混在させない: 仕様 26, 27) ---
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. インストール済みアプリ
                    if (searchResults.matchingApps.isNotEmpty()) {
                        item {
                            SearchSectionHeader("Apps (${searchResults.matchingApps.size})")
                        }
                        items(
                            items = searchResults.matchingApps,
                            key = { "app_${it.packageName}_${it.activityName}" }
                        ) { app ->
                            SearchAppRow(app, appDiscoveryRepository) {
                                onLaunchApp(app)
                                onDismiss()
                            }
                        }
                    }

                    // 2. Launcher ショートカット
                    if (searchResults.matchingShortcuts.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            SearchSectionHeader("Shortcuts")
                        }
                        items(
                            items = searchResults.matchingShortcuts,
                            key = { "shortcut_${it.id}" }
                        ) { shortcut ->
                            Surface(
                                color = Color(0xFF1C2028),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onLaunchShortcut(shortcut)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(text = "🔗", fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            text = shortcut.label,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = shortcut.targetUri,
                                            color = Color(0xFF9AA0A6),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Launcher 独自 Action
                    if (searchResults.matchingActions.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            SearchSectionHeader("Actions")
                        }
                        items(
                            items = searchResults.matchingActions,
                            key = { "action_${it.name}" }
                        ) { action ->
                            Surface(
                                color = Color(0xFF1C2028),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        onTriggerAction(action)
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(text = action.emojiIcon, fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            text = action.title,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = action.subtitle,
                                            color = Color(0xFF9AA0A6),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Web検索（検索結果最下部に「Googleで『検索文字列』を検索」を表示: 仕様 9）
                    if (searchResults.webSearchQuery.isNotBlank()) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            SearchSectionHeader("Web")
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onGoogleSearch(searchResults.webSearchQuery)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = "Googleで「${searchResults.webSearchQuery}」を検索",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchAppRow(app: AppInfo, repository: AppDiscoveryRepository, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF1C2028), shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().then(searchAppDragModifier(app)).clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            LauncherItemGraphic(type = ItemType.APP, packageName = app.packageName,
                activityName = app.activityName, targetUri = "", label = app.label,
                isInstalled = true, appDiscoveryRepository = repository,
                iconSize = 32.dp, showLabel = false)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, color = Color(0xFF9AA0A6), fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ZeroQueryAppRowSection(
    title: String,
    apps: List<AppInfo>,
    appDiscoveryRepository: AppDiscoveryRepository,
    onAppClick: (AppInfo) -> Unit,
    dragModifier: @Composable (AppInfo) -> Modifier
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            apps.take(4).forEach { app ->
                Row(
                    modifier = Modifier.weight(1f).then(dragModifier(app))
                        .clickable { onAppClick(app) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LauncherItemGraphic(type = ItemType.APP, packageName = app.packageName,
                        activityName = app.activityName, targetUri = "", label = app.label,
                        isInstalled = true, appDiscoveryRepository = appDiscoveryRepository,
                        iconSize = 26.dp, showLabel = false)
                    Spacer(Modifier.width(4.dp))
                    Text(app.label, color = Color.White, fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String, textModifier: Modifier = Modifier) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 4.dp).then(textModifier)
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
    }
}

@Composable
private fun searchAppDragModifier(app: AppInfo): Modifier {
    val sizePx = with(LocalDensity.current) { 56.dp.toPx() }
    return Modifier.launcherDragSource(
        key = "search:${app.packageName}:${app.activityName}", origin = DragOrigin.SEARCH,
        createState = { finger -> appDragState(app, DragOrigin.SEARCH, finger, sizePx) }
    )
}

/** Keep the index clear of edge gestures and let a single finger scrub tiny sections. */
@Composable
private fun CompactAppIndex(
    positions: Map<String, Int>,
    trailingGap: androidx.compose.ui.unit.Dp,
    geometry: AppIndexLayout.Geometry,
    onSelect: (Int) -> Unit,
    onTouchStart: () -> Unit
) {
    var selected by remember { mutableStateOf<String?>(null) }
    val labels = AppListIndex.labels
    val labelStarts = remember { FloatArray(labels.size) }
    val density = LocalDensity.current
    Box(Modifier.width(28.dp + trailingGap).fillMaxHeight()) {
        val railHeight = geometry.heightDp.dp
        val top = geometry.topDp.dp
        val labelSize = (railHeight.value / labels.size * 0.82f / density.fontScale).sp
        Column(
            Modifier.offset(y = top).width(28.dp).height(railHeight)
                .pointerInput(positions, railHeight) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        onTouchStart()
                        fun select(y: Float) {
                            val index = AppIndexLayout.labelIndex(y, labelStarts)
                            val label = labels[index]
                            if (selected != label) {
                                selected = label
                                positions[label]?.let(onSelect)
                            }
                        }
                        select(down.position.y)
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) break
                                select(change.position.y)
                            }
                        } finally {
                            selected = null
                        }
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            labels.forEachIndexed { index, label ->
                Box(Modifier.fillMaxWidth().weight(1f)
                    .onGloballyPositioned { labelStarts[index] = it.positionInParent().y }
                    .semantics {
                    positions[label]?.let { target ->
                        onClick(label = "$label へ移動") {
                            onTouchStart()
                            onSelect(target)
                            true
                        }
                    }
                }, contentAlignment = Alignment.Center) {
                    Text(label, fontSize = labelSize, lineHeight = labelSize,
                        color = if (selected == label || positions[label] != null)
                            MaterialTheme.colorScheme.primary else Color(0xFF626773),
                        textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
        selected?.let { label ->
            val sectionOffset = railHeight * ((labels.indexOf(label) + 0.5f) / labels.size)
            Box(
                Modifier.offset(x = (-62).dp, y = top + sectionOffset - 28.dp)
                    .requiredSize(56.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = MaterialTheme.colorScheme.onPrimary, fontSize = 28.sp,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

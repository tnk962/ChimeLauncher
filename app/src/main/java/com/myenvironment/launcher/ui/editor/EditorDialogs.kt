package com.myenvironment.launcher.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pages
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myenvironment.launcher.core.launcher.AppDiscoveryRepository
import com.myenvironment.launcher.core.model.AppInfo
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LauncherAction
import com.myenvironment.launcher.core.model.LauncherPage
import com.myenvironment.launcher.core.model.LayoutItem
import com.myenvironment.launcher.core.widget.WidgetProviderCatalogItem
import com.myenvironment.launcher.ui.components.LauncherItemGraphic
import java.util.Locale

/**
 * 空白長押し時に表示する「ホーム画面を編集」ボトムシート (仕様 13, 30)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeEditSheet(
    isLayoutLocked: Boolean,
    isEditMode: Boolean,
    onAddAppClick: () -> Unit,
    onAddWidgetClick: () -> Unit,
    onAddShortcutOrActionClick: () -> Unit,
    onManagePagesClick: () -> Unit,
    onToggleEditModeClick: () -> Unit,
    onToggleLayoutLockClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "ホーム画面を編集",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            ListItem(
                headlineContent = { Text("＋ アプリを追加") },
                supportingContent = { Text("検索可能なアプリ一覧から現在のページへ配置") },
                leadingContent = { Icon(Icons.Default.Apps, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onAddAppClick()
                }
            )

            ListItem(
                headlineContent = { Text("＋ ウィジェットを追加") },
                supportingContent = { Text("カレンダー・時計・天気などのAppWidgetを配置・サイズ調整") },
                leadingContent = { Icon(Icons.Default.Widgets, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onAddWidgetClick()
                }
            )

            ListItem(
                headlineContent = { Text("＋ ショートカット / Actionを追加") },
                supportingContent = { Text("Deep Link・URL・Hatena Feed・通知履歴等を配置") },
                leadingContent = { Icon(Icons.Default.Link, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onAddShortcutOrActionClick()
                }
            )

            ListItem(
                headlineContent = { Text("＋ ページ管理") },
                supportingContent = { Text("HOME右側ページの追加・名前変更・並び替え・削除") },
                leadingContent = { Icon(Icons.Default.Pages, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onManagePagesClick()
                }
            )

            ListItem(
                headlineContent = {
                    Text(if (isEditMode) "✓ 編集モードを終了" else "Dock・アイコン・Widget配置を編集 (編集モード)")
                },
                supportingContent = { Text("ドラッグ移動・Widgetサイズ変更・削除・Dock編集") },
                leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onToggleEditModeClick()
                }
            )

            ListItem(
                headlineContent = {
                    Text(
                        if (isLayoutLocked) "🔓 レイアウトのロックを解除" else "🔒 レイアウトをロック"
                    )
                },
                supportingContent = {
                    Text(
                        if (isLayoutLocked) "現在ロック中（誤操作防止が有効です）"
                        else "ポケット内での誤削除・移動を防止します"
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = if (isLayoutLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (isLayoutLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                },
                modifier = Modifier.clickable {
                    onDismiss()
                    onToggleLayoutLockClick()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            ListItem(
                headlineContent = { Text("Launcher設定 & バックアップ") },
                supportingContent = { Text("Grid列数・Tiny Icons・Accessibility・JSONバックアップ/復元") },
                leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onOpenSettingsClick()
                }
            )
        }
    }
}

/**
 * レイアウトロック中に変更操作を試みた際の警告ダイアログ (仕様 15)
 *
 * 🔒 ホーム画面はロックされています
 * [キャンセル] [ロック解除]
 */
@Composable
fun LockedAlertDialog(
    onUnlock: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🔒 ホーム画面はロックされています",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text("誤操作によるアイコンの移動・削除やページ変更を防ぐため、現在ホーム画面はロックされています。ロックを解除しますか？")
        },
        confirmButton = {
            Button(
                onClick = {
                    onUnlock()
                    onDismiss()
                }
            ) {
                Text("ロック解除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

/**
 * ホーム画面またはDockへアイテム（アプリ / Widget / Action / Shortcut）を追加するための検索付きピッカーダイアログ (仕様 12, 13, 30)
 *
 * Tab index:
 * - 0: アプリ
 * - 1: Widget (allowWidgets == true の場合)
 * - 2: Action
 * - 3: Shortcut
 */
@Composable
fun ItemPickerDialog(
    initialTab: Int = 0,
    allowWidgets: Boolean = true,
    targetDescription: String,
    installedApps: List<AppInfo>,
    availableWidgets: List<WidgetProviderCatalogItem>,
    appDiscoveryRepository: AppDiscoveryRepository,
    onSelectApp: (AppInfo) -> Unit,
    onSelectWidget: (WidgetProviderCatalogItem) -> Unit,
    onSelectAction: (LauncherAction) -> Unit,
    onCreateShortcut: (label: String, uri: String) -> Unit,
    onDismiss: () -> Unit
) {
    val safeInitialTab = if (!allowWidgets && initialTab == 1) 0 else initialTab.coerceIn(0, 3)
    var selectedTab by remember { mutableIntStateOf(safeInitialTab) }
    var searchQuery by remember { mutableStateOf("") }
    var widgetSearchQuery by remember { mutableStateOf("") }
    var shortcutLabel by remember { mutableStateOf("") }
    var shortcutUri by remember { mutableStateOf("https://") }

    val filteredApps = remember(searchQuery, installedApps) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) {
            installedApps
        } else {
            installedApps.filter {
                it.label.lowercase(Locale.ROOT).contains(q) ||
                    it.packageName.lowercase(Locale.ROOT).contains(q)
            }
        }
    }

    val filteredWidgets = remember(widgetSearchQuery, availableWidgets) {
        val q = widgetSearchQuery.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) {
            availableWidgets
        } else {
            availableWidgets.filter {
                it.widgetLabel.lowercase(Locale.ROOT).contains(q) ||
                    it.appLabel.lowercase(Locale.ROOT).contains(q) ||
                    it.packageName.lowercase(Locale.ROOT).contains(q) ||
                    it.description.lowercase(Locale.ROOT).contains(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "アイテムを追加",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = targetDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                val tabs = remember(allowWidgets) {
                    if (allowWidgets) {
                        listOf(
                            0 to "アプリ",
                            1 to "Widget",
                            2 to "Action",
                            3 to "Shortcut"
                        )
                    } else {
                        listOf(
                            0 to "アプリ",
                            2 to "Action",
                            3 to "Shortcut"
                        )
                    }
                }
                val currentTabRowIdx = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0)

                TabRow(selectedTabIndex = currentTabRowIdx) {
                    tabs.forEach { (tabId, tabTitle) ->
                        Tab(
                            selected = selectedTab == tabId,
                            onClick = { selectedTab = tabId },
                            text = { Text(tabTitle, fontSize = 12.sp, maxLines = 1) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (selectedTab) {
                    // Tab 0: 検索可能なアプリ一覧
                    0 -> {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("アプリ名・パッケージ名で検索") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            items(
                                items = filteredApps,
                                key = { "${it.packageName}/${it.activityName}" }
                            ) { app ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectApp(app)
                                            onDismiss()
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        LauncherItemGraphic(
                                            type = ItemType.APP,
                                            packageName = app.packageName,
                                            activityName = app.activityName,
                                            targetUri = "",
                                            label = app.label,
                                            isInstalled = true,
                                            appDiscoveryRepository = appDiscoveryRepository,
                                            iconSize = 34.dp,
                                            showLabel = false
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = app.label,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = app.packageName,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Tab 1: Android AppWidget 一覧
                    1 -> {
                        OutlinedTextField(
                            value = widgetSearchQuery,
                            onValueChange = { widgetSearchQuery = it },
                            placeholder = { Text("ウィジェット名・アプリ名で検索 (例: カレンダー)") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (filteredWidgets.isEmpty()) {
                            Text(
                                text = "該当するウィジェットが見つかりません。",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                items(
                                    items = filteredWidgets,
                                    key = { "${it.packageName}/${it.providerClassName}" }
                                ) { widget ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSelectWidget(widget)
                                                onDismiss()
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                        ) {
                                            LauncherItemGraphic(
                                                type = ItemType.APP,
                                                packageName = widget.packageName,
                                                activityName = "",
                                                targetUri = "",
                                                label = widget.appLabel,
                                                isInstalled = true,
                                                appDiscoveryRepository = appDiscoveryRepository,
                                                iconSize = 36.dp,
                                                showLabel = false
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = widget.widgetLabel,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = widget.appLabel,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                if (widget.description.isNotBlank()) {
                                                    Text(
                                                        text = widget.description,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "${widget.defaultSpanX}×${widget.defaultSpanY}",
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Tab 2: Launcher独自Action
                    2 -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            items(LauncherAction.entries) { action ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectAction(action)
                                            onDismiss()
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Text(text = action.emojiIcon, fontSize = 22.sp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = action.title,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = action.subtitle,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Tab 3: Deep Link / URL Shortcut
                    3 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Web URL や Deep Link (例: Keepの特定ノート、Wallabag、ChatGPT等) をショートカットとして配置します。",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = shortcutLabel,
                                onValueChange = { shortcutLabel = it },
                                label = { Text("表示ラベル (例: 📚 読みたい)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = shortcutUri,
                                onValueChange = { shortcutUri = it },
                                label = { Text("URL または Deep Link URI") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (shortcutLabel.isNotBlank() && shortcutUri.isNotBlank()) {
                                        onCreateShortcut(shortcutLabel.trim(), shortcutUri.trim())
                                        onDismiss()
                                    }
                                },
                                enabled = shortcutLabel.isNotBlank() && shortcutUri.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("ショートカットを追加")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}

/**
 * 配置済みウィジェットのセルサイズ (spanX × spanY) を変更するダイアログ
 */
@Composable
fun WidgetResizeDialog(
    item: LayoutItem,
    maxColumns: Int,
    maxRows: Int,
    onConfirmResize: (newSpanX: Int, newSpanY: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val safeMaxCols = maxColumns.coerceAtLeast(1)
    val safeMaxRows = maxRows.coerceAtLeast(1)
    var spanX by remember(item) {
        mutableIntStateOf(item.spanX.coerceIn(1, safeMaxCols))
    }
    var spanY by remember(item) {
        mutableIntStateOf(item.spanY.coerceIn(1, safeMaxRows))
    }

    val presets = remember(safeMaxCols, safeMaxRows) {
        listOf(
            2 to 1,
            2 to 2,
            3 to 2,
            4 to 1,
            4 to 2,
            4 to 3,
            safeMaxCols to 2,
            safeMaxCols to 3
        )
            .map { (w, h) -> w.coerceAtMost(safeMaxCols) to h.coerceAtMost(safeMaxRows) }
            .distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "ウィジェットのサイズ変更",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = item.label,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 横幅 (spanX) 調整
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Text("横幅 (列数)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("最大 $safeMaxCols 列", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = { if (spanX > 1) spanX-- },
                                enabled = spanX > 1
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "減らす")
                            }
                            Text(
                                text = "$spanX",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            FilledTonalButton(
                                onClick = { if (spanX < safeMaxCols) spanX++ },
                                enabled = spanX < safeMaxCols
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "増やす")
                            }
                        }
                    }
                }

                // 縦幅 (spanY) 調整
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Text("高さ (行数)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("最大 $safeMaxRows 行", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = { if (spanY > 1) spanY-- },
                                enabled = spanY > 1
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "減らす")
                            }
                            Text(
                                text = "$spanY",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            FilledTonalButton(
                                onClick = { if (spanY < safeMaxRows) spanY++ },
                                enabled = spanY < safeMaxRows
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "増やす")
                            }
                        }
                    }
                }

                // よく使うサイズプリセット
                Text(
                    text = "クイックサイズ選択:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.chunked(4).forEach { rowPresets ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowPresets.forEach { (pw, ph) ->
                                FilterChip(
                                    selected = spanX == pw && spanY == ph,
                                    onClick = {
                                        spanX = pw
                                        spanY = ph
                                    },
                                    label = { Text("${pw}×${ph}", fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmResize(spanX, spanY)
                    onDismiss()
                }
            ) {
                Text("適用 (${spanX}×${spanY})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

/**
 * ページ管理ダイアログ (仕様 16)
 *
 * Discover / All Apps / HOME は削除不可。
 * HOME右側のユーザー追加ページのみ、追加・削除・並び替え・名前変更が可能。
 */
@Composable
fun PageManagerDialog(
    userPages: List<LauncherPage>,
    onAddPage: (String) -> Unit,
    onRenamePage: (String, String) -> Unit,
    onDeletePage: (String) -> Unit,
    onMovePage: (String, Int) -> Unit,
    onJumpToPage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newPageName by remember { mutableStateOf("") }
    var editingPage by remember { mutableStateOf<LauncherPage?>(null) }
    var renameText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("ページ管理", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "固定ページ (削除不可): Discover | All Apps | HOME",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // 新規ページ追加フォーム
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newPageName,
                        onValueChange = { newPageName = it },
                        placeholder = { Text("新規ページ名 (例: 仕事, ゲーム)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onAddPage(newPageName.ifBlank { "Page ${userPages.size + 2}" })
                            newPageName = ""
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("追加")
                    }
                }

                HorizontalDivider()

                if (userPages.isEmpty()) {
                    Text(
                        text = "HOME右側の追加ページはまだありません。上の入力欄から「仕事」「ゲーム」などのページを追加できます。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        items(
                            items = userPages,
                            key = { it.id }
                        ) { page ->
                            val idx = userPages.indexOf(page)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    onJumpToPage(page.id)
                                                    onDismiss()
                                                }
                                        ) {
                                            Text(
                                                text = page.name,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "Page ${idx + 2} (タップで移動)",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Row {
                                            IconButton(
                                                onClick = { onMovePage(page.id, -1) },
                                                enabled = idx > 0
                                            ) {
                                                Icon(Icons.Default.ArrowUpward, contentDescription = "左(前)へ移動")
                                            }
                                            IconButton(
                                                onClick = { onMovePage(page.id, 1) },
                                                enabled = idx < userPages.lastIndex
                                            ) {
                                                Icon(Icons.Default.ArrowDownward, contentDescription = "右(次)へ移動")
                                            }
                                            IconButton(
                                                onClick = {
                                                    editingPage = page
                                                    renameText = page.name
                                                }
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "名前変更")
                                            }
                                            IconButton(
                                                onClick = { onDeletePage(page.id) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "削除",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }

                                    if (editingPage?.id == page.id) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = renameText,
                                                onValueChange = { renameText = it },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    onRenamePage(page.id, renameText)
                                                    editingPage = null
                                                }
                                            ) {
                                                Text("保存")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完了")
            }
        }
    )
}

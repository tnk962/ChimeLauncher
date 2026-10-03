package com.myenvironment.launcher.ui.allapps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myenvironment.launcher.core.launcher.AppDiscoveryRepository
import com.myenvironment.launcher.core.model.AppInfo
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.ui.components.launcherDragSource
import com.myenvironment.launcher.ui.components.appDragState
import com.myenvironment.launcher.ui.home.DragOrigin
import com.myenvironment.launcher.ui.components.LauncherItemGraphic

/**
 * Page -1: All Apps / Tiny Icons ページ (仕様 10)
 *
 * - 各セル内にPopup(DropdownMenu)を持たせない軽量構造と事前キャッシュImageBitmapにより、
 *   Fold展開時の大量アイコン表示でも滑らかに高速スクロール可能。
 * - Fold展開時も、Dockを除くページ領域全体を使用する。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllAppsTinyPage(
    apps: List<AppInfo>,
    columns: Int,
    iconSizeDp: Int,
    showLabels: Boolean,
    appDiscoveryRepository: AppDiscoveryRepository,
    onLaunchApp: (AppInfo) -> Unit,
    onAddAppToHome: (AppInfo) -> Unit,
    onAddAppToDock: (AppInfo) -> Unit,
    onOpenAppInfo: (AppInfo) -> Unit,
    onOpenSearch: () -> Unit,
    onToggleLabels: () -> Unit,
    onChangeColumns: (Int) -> Unit,
    onChangeIconSize: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showQuickConfigMenu by remember { mutableStateOf(false) }
    var selectedAppForDialog by remember { mutableStateOf<AppInfo?>(null) }

    val effectiveColumns = columns.coerceAtLeast(4)
    val dragSizePx = with(LocalDensity.current) { 56.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
        ) {
            // コンパクトなヘッダー
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "All Apps",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color(0x8820242C),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${apps.size}",
                            color = Color(0xFFBDC1C6),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {

                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "アプリを検索",
                            tint = Color.White
                        )
                    }

                    Box {
                        IconButton(onClick = { showQuickConfigMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Tiny Icons 表示設定",
                                tint = Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = showQuickConfigMenu,
                            onDismissRequest = { showQuickConfigMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (showLabels) "☑ アプリ名を表示" else "☐ アプリ名を表示")
                                },
                                onClick = {
                                    onToggleLabels()
                                    showQuickConfigMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("列数を増やす (${columns}列 → ${(columns + 1).coerceAtMost(14)}列)") },
                                onClick = { onChangeColumns(columns + 1) }
                            )
                            DropdownMenuItem(
                                text = { Text("列数を減らす (${columns}列 → ${(columns - 1).coerceAtLeast(4)}列)") },
                                onClick = { onChangeColumns(columns - 1) }
                            )
                            DropdownMenuItem(
                                text = { Text("アイコン拡大 (${iconSizeDp}dp → ${(iconSizeDp + 4).coerceAtMost(56)}dp)") },
                                onClick = { onChangeIconSize(iconSizeDp + 4) }
                            )
                            DropdownMenuItem(
                                text = { Text("アイコン縮小 (${iconSizeDp}dp → ${(iconSizeDp - 4).coerceAtLeast(24)}dp)") },
                                onClick = { onChangeIconSize(iconSizeDp - 4) }
                            )
                        }
                    }
                }
            }

            // Tiny Icons Grid 本体（各セルにPopupを持たせず超軽量化）
            LazyVerticalGrid(
                columns = GridCells.Fixed(effectiveColumns),
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = Color(0x55101216),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(if (showLabels) 10.dp else 8.dp)
            ) {
                items(
                    items = apps,
                    key = { "${it.packageName}/${it.activityName}/${it.userSerialNumber}" },
                    contentType = { "tiny_app_icon" }
                ) { app ->
                    LauncherItemGraphic(
                        type = ItemType.APP,
                        packageName = app.packageName,
                        activityName = app.activityName,
                        targetUri = "",
                        label = app.label,
                        isInstalled = true,
                        appDiscoveryRepository = appDiscoveryRepository,
                        iconSize = iconSizeDp.dp,
                        showLabel = showLabels,
                        modifier = Modifier
                            .launcherDragSource(
                                key = "allapps:${app.packageName}:${app.activityName}:${app.userSerialNumber}",
                                origin = DragOrigin.ALL_APPS,
                                onLongPressRelease = { selectedAppForDialog = app },
                                createState = { finger -> appDragState(app, DragOrigin.ALL_APPS, finger, dragSizePx) }
                            )
                            .semantics { onLongClick("アプリのメニュー") { selectedAppForDialog = app; true } }
                            .clickable { onLaunchApp(app) }
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }
    }

    // 長押しされたアプリのアクションダイアログ（ページ全体で1つだけ生成）
    val targetApp = selectedAppForDialog
    if (targetApp != null) {
        AlertDialog(
            onDismissRequest = { selectedAppForDialog = null },
            title = { Text(targetApp.label, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = targetApp.packageName,
                        fontSize = 11.sp,
                        color = Color(0xFF9AA0A6)
                    )
                    Button(
                        onClick = {
                            selectedAppForDialog = null
                            onLaunchApp(targetApp)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("起動する")
                    }
                    OutlinedButton(
                        onClick = {
                            selectedAppForDialog = null
                            onAddAppToHome(targetApp)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("＋ HOME画面に追加")
                    }
                    OutlinedButton(
                        onClick = {
                            selectedAppForDialog = null
                            onAddAppToDock(targetApp)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("＋ Dockに追加")
                    }
                    OutlinedButton(
                        onClick = {
                            selectedAppForDialog = null
                            onOpenAppInfo(targetApp)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ℹ️ アプリ情報")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedAppForDialog = null }) {
                    Text("閉じる")
                }
            }
        )
    }
}

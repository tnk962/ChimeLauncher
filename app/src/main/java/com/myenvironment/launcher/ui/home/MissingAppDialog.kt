package com.myenvironment.launcher.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myenvironment.launcher.core.launcher.MissingAppResolver
import com.myenvironment.launcher.core.launcher.MissingPackageOrigin
import com.myenvironment.launcher.core.model.AppInfo
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LayoutItem

/**
 * 未インストールアプリ／ウィジェットのPlaceholderタップ時に表示するダイアログ (仕様 24, 25)
 *
 * Galaxy等の別端末のNovaバックアップからPixelへ復元した際に発生する
 * 「Galaxy Store版パッケージID（Kindle等）」「Samsung固有アプリ」「旧パッケージID」を診断して理由を表示し、
 * 1. Pixel端末内のインストール済み同名・代替アプリへの1タップ置き換え
 * 2. Google Play正規パッケージIDへの変換オープン
 * 3. アプリ名・パッケージ単語によるPlayストア内キーワード検索 & Web検索（しつこく検索）
 * を提供する。
 */
@Composable
fun MissingAppDialog(
    item: LayoutItem,
    installedApps: List<AppInfo> = emptyList(),
    onOpenPlayStore: (String) -> Unit,
    onSearchPlayStore: (String) -> Unit = {},
    onSearchPlayStoreWeb: (String) -> Unit = {},
    onReplaceWithInstalledApp: (LayoutItem, AppInfo) -> Unit = { _, _ -> },
    onRemoveFromHome: (LayoutItem) -> Unit,
    onDismiss: () -> Unit
) {
    val isWidget = item.type == ItemType.WIDGET
    val resolution = remember(item, installedApps) {
        MissingAppResolver.resolve(item, installedApps)
    }
    var searchQuery by remember(resolution) {
        mutableStateOf(
            resolution.suggestedSearchQueries.firstOrNull()
                ?: item.label.ifBlank { item.packageName }
        )
    }

    val badgeColor = when (resolution.origin) {
        MissingPackageOrigin.GALAXY_STORE_EDITION -> Color(0xFFFFB74D)
        MissingPackageOrigin.SAMSUNG_SYSTEM_OR_EXCLUSIVE -> Color(0xFF64B5F6)
        MissingPackageOrigin.CARRIER_CUSTOM -> Color(0xFFBA68C8)
        MissingPackageOrigin.GENERAL_APP -> Color(0xFF81C995)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isWidget) "未インストールウィジェットの解決" else "未インストールアプリの検索・解決",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = if (isWidget) "${item.label} (${item.spanX}×${item.spanY})" else item.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. パッケージ診断・なぜヒットしないかの違い表示カード
                Surface(
                    color = badgeColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = badgeColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = resolution.origin.badgeTitle,
                                color = badgeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            text = resolution.origin.description,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        if (item.packageName.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "元パッケージID: ${item.packageName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        if (resolution.mappedPlayStorePackage != null) {
                            Text(
                                text = "Google Play正規ID候補: ${resolution.mappedPlayStorePackage}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF81C995),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // 2. この端末（Pixel）に既にインストールされている同名・代替アプリ候補がある場合
                if (!isWidget && resolution.installedCandidates.isNotEmpty()) {
                    Surface(
                        color = Color(0xFF1E3A2F),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF81C995)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "端末内に該当・代替アプリが見つかりました",
                                    color = Color(0xFF81C995),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                text = "パッケージIDの違いで未インストール扱いになっています。1タップでこの位置のアイコンを端末内のアプリに置き換えられます。",
                                fontSize = 11.sp,
                                color = Color(0xFFDADCE0),
                                lineHeight = 15.sp
                            )

                            resolution.installedCandidates.forEach { candidate ->
                                Button(
                                    onClick = {
                                        onReplaceWithInstalledApp(item, candidate.appInfo)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2E7D32),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "「${candidate.appInfo.label}」に置き換える",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "${candidate.matchReason} (${candidate.appInfo.packageName})",
                                            fontSize = 10.sp,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // 3. Google Play 正規パッケージID変換がある場合は最優先ボタンとして表示
                val mappedPkg = resolution.mappedPlayStorePackage
                if (mappedPkg != null) {
                    Button(
                        onClick = {
                            onOpenPlayStore(mappedPkg)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Shop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Google Play版IDでストアを開く",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = mappedPkg,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // 4. しつこくキーワード検索セクション（パッケージIDがヒットしない時の強力検索）
                Text(
                    text = "キーワードでしつこくPlayストア・Web検索",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                if (resolution.suggestedSearchQueries.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        resolution.suggestedSearchQueries.forEach { suggestion ->
                            FilterChip(
                                selected = searchQuery == suggestion,
                                onClick = { searchQuery = suggestion },
                                label = { Text(suggestion, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("検索キーワード (編集可)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                FilledTonalButton(
                    onClick = {
                        val q = searchQuery.trim().ifEmpty { item.label }
                        onSearchPlayStore(q)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Playストアを「${searchQuery.trim().ifEmpty { item.label }}」で検索")
                }

                FilledTonalButton(
                    onClick = {
                        val q = searchQuery.trim().ifEmpty { item.label }
                        onSearchPlayStoreWeb(q)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Web (Google) でPlayストアページを検索")
                }

                // 5. 元のパッケージIDで直接Playストア詳細ページを開くボタン
                if (item.packageName.isNotBlank()) {
                    if (mappedPkg == null) {
                        Button(
                            onClick = {
                                onOpenPlayStore(item.packageName)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Shop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("元のパッケージIDでPlayストアを開く")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                onOpenPlayStore(item.packageName)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("元のID (${item.packageName}) でストアを試す", fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider()

                // 6. ホームから削除ボタン
                OutlinedButton(
                    onClick = {
                        onRemoveFromHome(item)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ホームから削除", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

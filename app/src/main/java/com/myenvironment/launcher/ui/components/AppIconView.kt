package com.myenvironment.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myenvironment.launcher.core.launcher.AppDiscoveryRepository
import com.myenvironment.launcher.core.model.FolderApp
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LauncherAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * アプリアイコン・Shortcut・Launcher Action・未インストールPlaceholderを描画する共通コンポーネント (仕様 10, 12, 24)
 *
 * メモリキャッシュ済みの ImageBitmap を同期的に初期値として取得するため、
 * All Apps ページの高速スクロール時にもコルーチン待ちや再生成が発生せず滑らかに描画される。
 */
@Composable
fun LauncherItemGraphic(
    type: ItemType,
    packageName: String,
    activityName: String,
    targetUri: String,
    label: String,
    isInstalled: Boolean,
    appDiscoveryRepository: AppDiscoveryRepository,
    iconSize: Dp = 48.dp,
    showLabel: Boolean = true,
    isEditMode: Boolean = false,
    modifier: Modifier = Modifier,
    folderApps: List<FolderApp> = emptyList()
) {
    // 1. まずメモリキャッシュから同期的に即座に取得（0ms）
    val cachedInitial = remember(packageName, activityName, isInstalled) {
        if (isInstalled && packageName.isNotBlank()) {
            appDiscoveryRepository.getCachedIconBitmap(packageName, activityName)
        } else {
            null
        }
    }

    var iconBitmap by remember(packageName, activityName, isInstalled) {
        mutableStateOf<ImageBitmap?>(cachedInitial)
    }

    // 2. キャッシュ未ヒット時のみバックグラウンドで生成してキャッシュに格納
    LaunchedEffect(packageName, activityName, isInstalled) {
        if (iconBitmap == null && isInstalled && packageName.isNotBlank()) {
            iconBitmap = withContext(Dispatchers.IO) {
                appDiscoveryRepository.loadOrCreateIconBitmap(packageName, activityName)
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(iconSize)
        ) {
            when {
                type == ItemType.FOLDER -> {
                    Column(Modifier.size(iconSize).clip(RoundedCornerShape(12.dp)).background(Color(0xCC29354A)).padding(3.dp)) {
                        repeat(2) { row ->
                            Row {
                                repeat(2) { column ->
                                    val app = folderApps.getOrNull(row * 2 + column)
                                    if (app == null) Box(Modifier.size((iconSize - 6.dp) / 2))
                                    else LauncherItemGraphic(ItemType.APP, app.packageName, app.activityName, "", app.label,
                                        appDiscoveryRepository.isPackageInstalled(app.packageName), appDiscoveryRepository,
                                        iconSize = (iconSize - 6.dp) / 2, showLabel = false)
                                }
                            }
                        }
                    }
                }
                // 1. 未インストールアプリの Placeholder 表示 (仕様 24)
                !isInstalled && type == ItemType.APP -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(iconSize)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xAA2A2D35))
                            .border(
                                width = 1.5.dp,
                                color = Color(0xFFFFB74D),
                                shape = RoundedCornerShape(14.dp)
                            )
                    ) {
                        Text(
                            text = "?",
                            color = Color(0xFFFFB74D),
                            fontSize = (iconSize.value * 0.45f).sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 2. Launcher独自Action
                type == ItemType.ACTION -> {
                    val action = LauncherAction.fromActionId(targetUri)
                    val bitmap = iconBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = label,
                            modifier = Modifier.size(iconSize)
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(iconSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Text(
                                text = action?.emojiIcon ?: "⚡",
                                fontSize = (iconSize.value * 0.45f).sp
                            )
                        }
                    }
                }

                // 3. Shortcut
                type == ItemType.SHORTCUT -> {
                    val bitmap = iconBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = label,
                            modifier = Modifier.size(iconSize)
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(iconSize)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xDD263238))
                                .border(1.dp, Color(0xFF80CBC4), RoundedCornerShape(14.dp))
                        ) {
                            Text(
                                text = "🔗",
                                fontSize = (iconSize.value * 0.42f).sp
                            )
                        }
                    }
                }

                // 4. 通常アプリ
                else -> {
                    val bitmap = iconBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = label,
                            modifier = Modifier.size(iconSize)
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(iconSize)
                                .clip(CircleShape)
                                .background(Color(0x8837474F))
                        ) {
                            Text(
                                text = label.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = (iconSize.value * 0.4f).sp
                            )
                        }
                    }
                }
            }

            // 編集モードバッジ
            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        if (showLabel) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.85f),
                        offset = Offset(0f, 2f),
                        blurRadius = 4f
                    )
                ),
                modifier = Modifier.padding(top = 3.dp)
            )

            // 未インストール時のサブラベル表示 (仕様 24)
            if (!isInstalled && type == ItemType.APP) {
                Text(
                    text = "未インストール",
                    color = Color(0xFFFFB74D),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .background(
                            color = Color(0xCC1E1E24),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

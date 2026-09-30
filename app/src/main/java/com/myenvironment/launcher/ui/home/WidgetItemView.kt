package com.myenvironment.launcher.ui.home

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.myenvironment.launcher.core.model.LayoutItem
import com.myenvironment.launcher.core.widget.LauncherAppWidgetHostView
import com.myenvironment.launcher.core.widget.WidgetHostManager

/**
 * ホーム画面グリッド上に配置された AppWidget ([ItemType.WIDGET]) を描画するコンポーネント
 *
 * - バインド済み Widget: [AndroidView] 内で [LauncherAppWidgetHostView] をホストして実ウィジェットを表示
 * - 未バインド Widget (バックアップ復元後など): 自動サイレントバインドを試み、システム許可が必要な場合はワンタップ有効化カードを表示
 * - 未インストール Widget: 位置とセルサイズを保持したまま未インストール Placeholder を表示 (仕様 24, 25)
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WidgetItemView(
    item: LayoutItem,
    spanX: Int,
    spanY: Int,
    widthDp: Dp,
    heightDp: Dp,
    isPackageInstalled: Boolean,
    isEditMode: Boolean,
    widgetHostManager: WidgetHostManager,
    onLongPressWidget: () -> Unit,
    onRequestResize: () -> Unit,
    onRequestRebindWidget: (LayoutItem) -> Unit,
    onMissingWidgetClick: (LayoutItem) -> Unit,
    onSilentAutoRebindAttempt: (LayoutItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val boundInfo = remember(item.appWidgetId, isPackageInstalled) {
        if (item.appWidgetId > 0) {
            widgetHostManager.getAppWidgetInfo(item.appWidgetId)
        } else {
            null
        }
    }

    val availableProvider = remember(item.packageName, item.activityName, isPackageInstalled) {
        if (isPackageInstalled) {
            widgetHostManager.findProviderInfo(item.packageName, item.activityName)
        } else {
            null
        }
    }

    // バックアップ復元直後などで appWidgetId が未バインドだが、対象アプリとProviderが存在する場合は
    // 1度だけサイレントバインドを試行する
    LaunchedEffect(item.id, item.appWidgetId, isPackageInstalled, boundInfo == null, availableProvider != null) {
        if (boundInfo == null && isPackageInstalled && availableProvider != null) {
            onSilentAutoRebindAttempt(item)
        }
    }

    val widthIntDp = widthDp.value.toInt().coerceAtLeast(48)
    val heightIntDp = heightDp.value.toInt().coerceAtLeast(48)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
    ) {
        when {
            // 1. バインド済みで有効な AppWidget
            boundInfo != null && item.appWidgetId > 0 -> {
                AndroidView(
                    factory = { ctx ->
                        val view = widgetHostManager.createHostView(
                            context = ctx,
                            appWidgetId = item.appWidgetId,
                            providerInfo = boundInfo,
                            widthDp = widthIntDp,
                            heightDp = heightIntDp
                        )
                        view.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        (view as? LauncherAppWidgetHostView)?.apply {
                            isInEditModeOverlay = isEditMode
                            onWidgetLongPress = onLongPressWidget
                        }
                        view
                    },
                    update = { view ->
                        (view as? LauncherAppWidgetHostView)?.apply {
                            isInEditModeOverlay = isEditMode
                            onWidgetLongPress = onLongPressWidget
                        }
                        widgetHostManager.updateHostViewSize(
                            hostView = view,
                            widthDp = widthIntDp,
                            heightDp = heightIntDp
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 2. アプリはインストールされているが未バインド状態（バックアップ復元後など）
            isPackageInstalled && availableProvider != null -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xDD1E2430),
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .combinedClickable(
                            onClick = {
                                if (!isEditMode) {
                                    onRequestRebindWidget(item)
                                }
                            },
                            onLongClick = onLongPressWidget
                        )
                ) {
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
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.label,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "タップしてウィジェットを有効化 (${spanX}×${spanY})",
                            color = Color(0xFFB0BEC5),
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        if (heightDp >= 100.dp && !isEditMode) {
                            Spacer(modifier = Modifier.height(6.dp))
                            FilledTonalButton(
                                onClick = { onRequestRebindWidget(item) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("有効化", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // 3. アプリ自体が未インストールの Widget Placeholder (仕様 24, 25)
            else -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xCC232026),
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 1.5.dp,
                            color = Color(0xFFFFB74D),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .combinedClickable(
                            onClick = {
                                if (!isEditMode) {
                                    onMissingWidgetClick(item)
                                }
                            },
                            onLongClick = onLongPressWidget
                        )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFB74D))
                        ) {
                            Text(
                                text = "?",
                                color = Color(0xFFFFB74D),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.label,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "未インストール Widget (${spanX}×${spanY})",
                            color = Color(0xFFFFB74D),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // 編集モード時のリサイズ＆ドラッグオーバーレイフレーム
        if (isEditMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x44101622))
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                // 左上: 現在のセルサイズバッジ (例: 4×2)
                Surface(
                    color = Color(0xDD1A1D24),
                    shape = RoundedCornerShape(bottomEnd = 12.dp, topStart = 14.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "${spanX}×${spanY} • ${item.label}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // 下部中央: サイズ変更ボタン
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .clickable { onRequestResize() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "サイズ変更",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "サイズ変更 (${spanX}×${spanY})",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

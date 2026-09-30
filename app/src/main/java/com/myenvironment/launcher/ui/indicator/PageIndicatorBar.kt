package com.myenvironment.launcher.ui.indicator

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myenvironment.launcher.core.chime.ChimeEvent
import com.myenvironment.launcher.core.chime.TimeSegment
import com.myenvironment.launcher.core.model.IndicatorStyle
import com.myenvironment.launcher.core.model.LauncherPage
import kotlin.math.PI
import kotlin.math.sin

/**
 * Time Chime の時間帯に応じたインジケーターカラーパレット (仕様 10.3)
 */
internal data class TimeChimePalette(
    val activeColor: Color,
    val inactiveColor: Color,
    val chimeAccentColor: Color,
    val containerBackground: Color
)

@Composable
internal fun rememberTimeChimePalette(
    timeSegment: TimeSegment,
    timeChimeEnabled: Boolean
): TimeChimePalette {
    val defaultPrimary = MaterialTheme.colorScheme.primary
    val effectiveSegment = if (timeChimeEnabled) timeSegment else TimeSegment.DAY

    val targetActive = when (effectiveSegment) {
        TimeSegment.MORNING -> Color(0xFFFFE4A0) // やや明るく柔らかい朝焼けトーン
        TimeSegment.DAY -> defaultPrimary        // 標準色
        TimeSegment.EVENING -> Color(0xFFFFB884) // 少し暖色寄りの夕暮れトーン
        TimeSegment.NIGHT -> Color(0xFF8AB4F8)   // 落ち着いた青系
        TimeSegment.LATE_NIGHT -> Color(0xFF6B84AC).copy(alpha = 0.80f) // 明度を少し下げた深夜トーン
    }

    val targetAccent = when (effectiveSegment) {
        TimeSegment.MORNING -> Color(0xFFFFF1C2)
        TimeSegment.DAY -> Color(0xFFB2EBF2)
        TimeSegment.EVENING -> Color(0xFFFFCC99)
        TimeSegment.NIGHT -> Color(0xFFA7C8FF)
        TimeSegment.LATE_NIGHT -> Color(0xFF849EC4)
    }

    val targetInactive = when (effectiveSegment) {
        TimeSegment.LATE_NIGHT -> Color.White.copy(alpha = 0.22f)
        TimeSegment.MORNING -> Color(0xFFFFF8E7).copy(alpha = 0.34f)
        TimeSegment.EVENING -> Color(0xFFFFF0E5).copy(alpha = 0.32f)
        else -> Color.White.copy(alpha = 0.30f)
    }

    val animatedActive by animateColorAsState(
        targetValue = targetActive,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "TimeChimeActiveColor"
    )
    val animatedAccent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "TimeChimeAccentColor"
    )
    val animatedInactive by animateColorAsState(
        targetValue = targetInactive,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "TimeChimeInactiveColor"
    )

    return TimeChimePalette(
        activeColor = animatedActive,
        inactiveColor = animatedInactive,
        chimeAccentColor = animatedAccent,
        containerBackground = Color(0x55101218)
    )
}

/**
 * Text モード用の簡潔なページ表示文字列を解決する (仕様 14)
 *
 * 例: Discover / Apps / 1 / 2 / 3 / Settings
 */
internal fun resolveShortPageLabel(
    page: LauncherPage,
    homePageOrdinal: Int
): String {
    return when (page.id) {
        LauncherPage.PAGE_ID_DISCOVER -> "Discover"
        LauncherPage.PAGE_ID_ALL_APPS -> "Apps"
        LauncherPage.PAGE_ID_HOME -> "1"
        LauncherPage.PAGE_ID_SETTINGS -> "Settings"
        else -> {
            val defaultPagePattern = Regex("""^Page\s+(\d+)$""", RegexOption.IGNORE_CASE)
            val match = defaultPagePattern.matchEntire(page.name.trim())
            if (match != null) {
                match.groupValues[1]
            } else if (page.name.isBlank()) {
                homePageOrdinal.toString()
            } else {
                page.name.take(8)
            }
        }
    }
}

/**
 * Icons モード用のページアイコンを解決する (仕様 15)
 */
internal fun resolvePageIcon(page: LauncherPage): ImageVector {
    return when (page.id) {
        LauncherPage.PAGE_ID_DISCOVER -> Icons.Default.Newspaper
        LauncherPage.PAGE_ID_ALL_APPS -> Icons.Default.Apps
        LauncherPage.PAGE_ID_SETTINGS -> Icons.Default.Settings
        else -> Icons.Default.Home
    }
}

/**
 * Chime Launcher ページインジケーター & Chime Moments 描画コンポーネント (仕様 7〜19, 34, 36)
 *
 * 構造:
 * PageIndicatorBar
 *  ├─ DotIndicator
 *  ├─ IconIndicator
 *  ├─ TextIndicator
 *  └─ IndicatorChimeEffect (描画レイヤー完結の波紋・ライン演出)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageIndicatorBar(
    pages: List<LauncherPage>,
    visiblePageIndices: Set<Int>,
    indicatorStyle: IndicatorStyle,
    timeSegment: TimeSegment,
    timeChimeEnabled: Boolean,
    activeChimeEvent: ChimeEvent?,
    onChimeAnimationFinished: () -> Unit,
    isLayoutLocked: Boolean,
    onSelectPage: (Int) -> Unit,
    onLongPressIndicator: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isReduceMotionEnabled = remember(activeChimeEvent) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }

    val palette = rememberTimeChimePalette(
        timeSegment = timeSegment,
        timeChimeEnabled = timeChimeEnabled
    )

    // Chime アニメーション進行度 (0f..1f)
    val chimeProgress = remember { Animatable(0f) }

    LaunchedEffect(activeChimeEvent) {
        val event = activeChimeEvent ?: return@LaunchedEffect
        chimeProgress.snapTo(0f)
        val durationMs = when {
            isReduceMotionEnabled -> 650
            event is ChimeEvent.First -> 1650
            event is ChimeEvent.Return -> 1750
            else -> 1500
        }
        chimeProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = LinearOutSlowInEasing
            )
        )
        chimeProgress.snapTo(0f)
        onChimeAnimationFinished()
    }

    // 各インジケーターアイテムの親Row内の中心座標とサイズを記録（Chime animation origin として使用: 仕様 17）
    val itemCenters = remember { mutableStateMapOf<Int, Offset>() }
    val itemSizes = remember { mutableStateMapOf<Int, Size>() }

    // Return Chime 時は穏やかにインジケーター間隔を伸縮させる（高さや縦位置は一切変えない: 仕様 9.3, 18）
    val progressVal = chimeProgress.value
    val returnSpreadBoostDp = if (
        activeChimeEvent is ChimeEvent.Return &&
        !isReduceMotionEnabled &&
        progressVal > 0f &&
        progressVal < 1f
    ) {
        val envelope = sin(progressVal * PI).toFloat().coerceIn(0f, 1f)
        (4.5f * envelope).dp
    } else {
        0.dp
    }

    val baseSpacingDp = when (indicatorStyle) {
        IndicatorStyle.DOTS -> 7.dp
        IndicatorStyle.ICONS -> 8.dp
        IndicatorStyle.TEXT -> 6.dp
    }

    // 固定高さ (30.dp) を維持し、Chime中も他のUIが上下に動かないようにする (仕様 18)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(baseSpacingDp + returnSpreadBoostDp),
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(palette.containerBackground)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPressIndicator
                )
                .IndicatorChimeEffect(
                    activeChimeEvent = activeChimeEvent,
                    progress = progressVal,
                    isReduceMotion = isReduceMotionEnabled,
                    indicatorStyle = indicatorStyle,
                    visiblePageIndices = visiblePageIndices,
                    itemCenters = itemCenters,
                    itemSizes = itemSizes,
                    palette = palette
                )
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            var homeOrdinalCounter = 0
            pages.forEachIndexed { index, page ->
                if (page.id != LauncherPage.PAGE_ID_DISCOVER &&
                    page.id != LauncherPage.PAGE_ID_ALL_APPS &&
                    page.id != LauncherPage.PAGE_ID_SETTINGS
                ) {
                    homeOrdinalCounter++
                }
                val isSelected = visiblePageIndices.contains(index)

                // First Chime 中の現在ドット/アイコンの穏やかな明度・スケール強調
                val firstPulseEnvelope = if (
                    activeChimeEvent is ChimeEvent.First &&
                    isSelected &&
                    progressVal > 0f &&
                    progressVal < 1f
                ) {
                    sin(progressVal * PI).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .onPlaced { coords ->
                            val pos = coords.positionInParent()
                            val w = coords.size.width.toFloat()
                            val h = coords.size.height.toFloat()
                            itemCenters[index] = Offset(pos.x + w / 2f, pos.y + h / 2f)
                            itemSizes[index] = Size(w, h)
                        }
                        .clickable { onSelectPage(index) }
                ) {
                    when (indicatorStyle) {
                        IndicatorStyle.DOTS -> {
                            DotIndicator(
                                isSelected = isSelected,
                                highlightFraction = firstPulseEnvelope,
                                palette = palette
                            )
                        }

                        IndicatorStyle.ICONS -> {
                            IconIndicator(
                                page = page,
                                isSelected = isSelected,
                                highlightFraction = firstPulseEnvelope,
                                palette = palette
                            )
                        }

                        IndicatorStyle.TEXT -> {
                            val label = resolveShortPageLabel(page, homeOrdinalCounter.coerceAtLeast(1))
                            TextIndicator(
                                label = label,
                                isSelected = isSelected,
                                highlightFraction = firstPulseEnvelope,
                                palette = palette
                            )
                        }
                    }
                }
            }

            if (isLayoutLocked) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "レイアウトロック中",
                    tint = Color.White.copy(alpha = 0.60f),
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

/**
 * Dots モードの単一アイテム描画 (仕様 16)
 *
 * タップ領域サイズ (16.dp × 16.dp) は固定し、内部のドット径のみ現在位置で強調する。
 */
@Composable
private fun DotIndicator(
    isSelected: Boolean,
    highlightFraction: Float,
    palette: TimeChimePalette
) {
    val baseDiameter = if (isSelected) 7.5.dp else 4.5.dp
    val pulseBonus = (1.5f * highlightFraction).dp
    val dotColor = if (isSelected) {
        if (highlightFraction > 0.05f) palette.chimeAccentColor else palette.activeColor
    } else {
        palette.inactiveColor
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(baseDiameter + pulseBonus)
                .clip(CircleShape)
                .background(dotColor)
        )
    }
}

/**
 * Icons モードの単一アイテム描画 (仕様 15)
 */
@Composable
private fun IconIndicator(
    page: LauncherPage,
    isSelected: Boolean,
    highlightFraction: Float,
    palette: TimeChimePalette
) {
    val icon = resolvePageIcon(page)
    val tint = if (isSelected) {
        if (highlightFraction > 0.05f) palette.chimeAccentColor else palette.activeColor
    } else {
        palette.inactiveColor.copy(alpha = 0.45f)
    }
    val iconSize = if (isSelected) 14.dp else 11.5.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(width = 20.dp, height = 18.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = page.name,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Text モードの単一アイテム描画 (仕様 14)
 */
@Composable
private fun TextIndicator(
    label: String,
    isSelected: Boolean,
    highlightFraction: Float,
    palette: TimeChimePalette
) {
    val bgColor = if (isSelected) {
        val base = if (highlightFraction > 0.05f) palette.chimeAccentColor else palette.activeColor
        base.copy(alpha = 0.24f + 0.16f * highlightFraction)
    } else {
        Color.Transparent
    }
    val textColor = if (isSelected) {
        if (highlightFraction > 0.05f) palette.chimeAccentColor else palette.activeColor
    } else {
        Color.White.copy(alpha = 0.48f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .widthIn(min = 18.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * First Chime / Return Chime を描画レイヤー内だけで完結させるエフェクト Modifier (仕様 8.2, 9.3, 17, 18, 19, 34, 36)
 */
private fun Modifier.IndicatorChimeEffect(
    activeChimeEvent: ChimeEvent?,
    progress: Float,
    isReduceMotion: Boolean,
    indicatorStyle: IndicatorStyle,
    visiblePageIndices: Set<Int>,
    itemCenters: Map<Int, Offset>,
    itemSizes: Map<Int, Size>,
    palette: TimeChimePalette
): Modifier = this.drawWithContent {
    val event = activeChimeEvent
    val hasActiveChime = event != null && progress > 0.001f && progress < 0.999f

    if (!hasActiveChime) {
        drawContent()
        return@drawWithContent
    }

    // 現在のアクティブインジケーターの中心座標を計算
    val activeCenters = visiblePageIndices.mapNotNull { itemCenters[it] }
    val origin = if (activeCenters.isNotEmpty()) {
        Offset(
            x = activeCenters.map { it.x }.average().toFloat(),
            y = activeCenters.map { it.y }.average().toFloat()
        )
    } else {
        Offset(size.width / 2f, size.height / 2f)
    }
    val horizontalPaddingPx = 12.dp.toPx()
    val verticalPaddingPx = 5.dp.toPx()
    val originInCanvas = Offset(
        x = origin.x + horizontalPaddingPx,
        y = origin.y + verticalPaddingPx
    )

    when (event) {
        is ChimeEvent.First -> {
            // コンテンツを描画した上で、現在位置から静かな波紋を1〜2回広げる (• ((●)) •)
            drawContent()

            if (isReduceMotion) {
                // Accessibility Reduce Motion: 一瞬だけ静かな明度ハイライトを表示 (仕様 19)
                val alpha = (sin(progress * PI).toFloat() * 0.45f).coerceIn(0f, 0.45f)
                drawCircle(
                    color = palette.chimeAccentColor.copy(alpha = alpha),
                    radius = 12.dp.toPx(),
                    center = originInCanvas
                )
            } else {
                // 波紋1 (0.0 -> 0.85)
                val r1Progress = (progress / 0.85f).coerceIn(0f, 1f)
                val r1Alpha = ((1f - r1Progress) * sin(r1Progress * PI).toFloat() * 0.72f).coerceIn(0f, 0.72f)

                // 波紋2 (0.20 -> 1.0)
                val r2Progress = ((progress - 0.20f) / 0.80f).coerceIn(0f, 1f)
                val r2Alpha = if (progress >= 0.20f) {
                    ((1f - r2Progress) * sin(r2Progress * PI).toFloat() * 0.55f).coerceIn(0f, 0.55f)
                } else {
                    0f
                }

                // 中央の柔らかな発光コア
                val coreAlpha = (sin(progress * PI).toFloat() * 0.32f).coerceIn(0f, 0.32f)
                drawCircle(
                    color = palette.chimeAccentColor.copy(alpha = coreAlpha),
                    radius = 10.dp.toPx(),
                    center = originInCanvas
                )

                if (indicatorStyle == IndicatorStyle.TEXT) {
                    // Textモードでは現在ページ名の周囲から楕円カプセル状の波紋を広げる (仕様 17)
                    val activeIdx = visiblePageIndices.firstOrNull()
                    val baseSize = activeIdx?.let { itemSizes[it] } ?: Size(28.dp.toPx(), 16.dp.toPx())
                    if (r1Alpha > 0.01f) {
                        val expandW1 = baseSize.width + 22.dp.toPx() * r1Progress
                        val expandH1 = baseSize.height + 12.dp.toPx() * r1Progress
                        drawRoundRect(
                            color = palette.chimeAccentColor.copy(alpha = r1Alpha),
                            topLeft = Offset(originInCanvas.x - expandW1 / 2f, originInCanvas.y - expandH1 / 2f),
                            size = Size(expandW1, expandH1),
                            cornerRadius = CornerRadius(expandH1 / 2f, expandH1 / 2f),
                            style = Stroke(width = 1.4.dp.toPx())
                        )
                    }
                    if (r2Alpha > 0.01f) {
                        val expandW2 = baseSize.width + 26.dp.toPx() * r2Progress
                        val expandH2 = baseSize.height + 14.dp.toPx() * r2Progress
                        drawRoundRect(
                            color = palette.activeColor.copy(alpha = r2Alpha),
                            topLeft = Offset(originInCanvas.x - expandW2 / 2f, originInCanvas.y - expandH2 / 2f),
                            size = Size(expandW2, expandH2),
                            cornerRadius = CornerRadius(expandH2 / 2f, expandH2 / 2f),
                            style = Stroke(width = 1.1.dp.toPx())
                        )
                    }
                } else {
                    // Dots / Icons モード: 現在位置から2重の同心円リングが静かに広がる (• ((●)) •)
                    val minRadius = 4.dp.toPx()
                    val maxRadius = 18.dp.toPx()
                    if (r1Alpha > 0.01f) {
                        drawCircle(
                            color = palette.chimeAccentColor.copy(alpha = r1Alpha),
                            radius = minRadius + (maxRadius - minRadius) * r1Progress,
                            center = originInCanvas,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                    if (r2Alpha > 0.01f) {
                        drawCircle(
                            color = palette.activeColor.copy(alpha = r2Alpha),
                            radius = minRadius + (maxRadius * 1.12f - minRadius) * r2Progress,
                            center = originInCanvas,
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }
                }
            }
        }

        is ChimeEvent.Return -> {
            // Return Chime: ドット同士を結ぶ細いラインが伸びて、ゆっくり通常状態へ戻る (• ───── ● ───── •, 仕様 9.3)
            val allX = itemCenters.values.map { it.x + horizontalPaddingPx }
            val minX = (allX.minOrNull() ?: (originInCanvas.x - 32.dp.toPx()))
            val maxX = (allX.maxOrNull() ?: (originInCanvas.x + 32.dp.toPx()))

            val envelope = if (isReduceMotion) {
                1f
            } else {
                sin(progress * PI).toFloat().coerceIn(0f, 1f)
            }
            val lineAlpha = (sin(progress * PI).toFloat() * 0.58f).coerceIn(0f, 0.58f)

            val currentLeftX = originInCanvas.x - (originInCanvas.x - minX) * envelope
            val currentRightX = originInCanvas.x + (maxX - originInCanvas.x) * envelope

            if (lineAlpha > 0.01f && currentRightX > currentLeftX + 2f) {
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            palette.activeColor.copy(alpha = lineAlpha * 0.25f),
                            palette.chimeAccentColor.copy(alpha = lineAlpha),
                            palette.activeColor.copy(alpha = lineAlpha * 0.25f)
                        ),
                        startX = currentLeftX,
                        endX = currentRightX
                    ),
                    start = Offset(currentLeftX, originInCanvas.y),
                    end = Offset(currentRightX, originInCanvas.y),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            drawContent()
        }

        null -> {
            drawContent()
        }
    }
}

package com.myenvironment.launcher.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal enum class LauncherSwipeAction {
    NONE, SEARCH, CONSUME, OPEN_NOTIFICATION, DELEGATE
}

/** Pointer-independent swipe decisions, including notification direction lock and release gating. */
internal class LauncherSwipeSession(
    private val searchThresholdPx: Float,
    private val notificationThresholdPx: Float,
    private val touchSlopPx: Float
) {
    private var notificationRejected = false
    private var trackingNotification = false
    private var finished = false

    fun move(dx: Float, dy: Float, pressed: Boolean): LauncherSwipeAction {
        if (finished) return LauncherSwipeAction.NONE
        val horizontal = abs(dx)
        val vertical = abs(dy)

        // Once horizontal intent is visible, this gesture cannot open notifications later.
        if (horizontal > touchSlopPx && horizontal > vertical * 1.15f) {
            notificationRejected = true
        }

        // Preserve the existing search threshold and immediate upward activation.
        if (dy < -searchThresholdPx && vertical > horizontal * 1.15f) {
            finished = true
            return LauncherSwipeAction.SEARCH
        }
        if (horizontal > searchThresholdPx && horizontal > vertical * 1.15f) {
            finished = true
            return LauncherSwipeAction.DELEGATE
        }

        val qualifies = !notificationRejected &&
            dy >= notificationThresholdPx && dy >= horizontal * 2f
        if (!pressed) {
            finished = true
            return when {
                qualifies -> LauncherSwipeAction.OPEN_NOTIFICATION
                trackingNotification -> LauncherSwipeAction.CONSUME
                else -> LauncherSwipeAction.NONE
            }
        }
        if (qualifies) trackingNotification = true
        return if (trackingNotification) LauncherSwipeAction.CONSUME else LauncherSwipeAction.NONE
    }
}

/**
 * HOME / ユーザー追加ページの空白・グリッド領域で垂直方向の上スワイプ(検索)と下スワイプ(通知シェード)を検出するModifier (仕様 6, 7, 8)
 *
 * - PointerEventPass.Initial で垂直スワイプを先行判定することで、グリッド内セルやアイコンの combinedClickable に
 *   イベントを奪われることなく上スワイプ検索を発火させる。
 * - 下スワイプは64dp以上・縦が横の2倍以上の動きだけを対象にし、指を離してから通知シェードを開く。
 *   横方向に動き始めた操作から通知へ切り替えず、同じ指で開いた通知に触れる誤操作を避ける。
 * - 縦スクロール可能なウィジェット（Google Keepのメモ一覧、カレンダー予定リスト等）の上でタッチが開始された場合は
 *   [shouldIgnoreTouchAt] により即座にスルーし、通知シェードや検索オーバーレイを誤発火させずウィジェットのスクロールのみを反応させる。
 * - All Apps ページや SearchOverlay、Discover ページなどの縦スクロール画面には適用しないことで、
 *   スクロール操作との競合を完全に防ぐ。
 */
fun Modifier.launcherVerticalSwipeGestures(
    enabled: Boolean = true,
    thresholdPx: Float = 75f,
    shouldIgnoreTouchAt: (Offset) -> Boolean = { false },
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit
): Modifier {
    if (!enabled) return this
    return this.pointerInput(enabled, thresholdPx, shouldIgnoreTouchAt, onSwipeUp, onSwipeDown) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val startPos = down.position
            if (shouldIgnoreTouchAt(startPos)) {
                return@awaitEachGesture
            }

            val startX = startPos.x
            val startY = startPos.y
            val downTime = down.uptimeMillis
            val session = LauncherSwipeSession(thresholdPx, 64.dp.toPx(), viewConfiguration.touchSlop)

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                if (shouldIgnoreTouchAt(startPos) || event.changes.any { it.id != down.id && it.pressed }) {
                    break
                }

                val dx = change.position.x - startX
                val dy = change.position.y - startY

                when (session.move(dx, dy, change.pressed)) {
                    LauncherSwipeAction.SEARCH -> {
                        change.consume()
                        onSwipeUp()
                        break
                    }
                    LauncherSwipeAction.OPEN_NOTIFICATION -> {
                        change.consume()
                        onSwipeDown()
                        break
                    }
                    LauncherSwipeAction.CONSUME -> change.consume()
                    LauncherSwipeAction.DELEGATE -> break
                    LauncherSwipeAction.NONE -> Unit
                }

                // その場で長押しされた場合はアイコン/空白の長押しメニューに委譲
                if (change.uptimeMillis - downTime > 450L && abs(dx) < 30f && abs(dy) < 30f) {
                    break
                }

                if (!change.pressed) break
            }
        }
    }
}

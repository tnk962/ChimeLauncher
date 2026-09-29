package com.myenvironment.launcher.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * HOME / ユーザー追加ページの空白・グリッド領域で垂直方向の上スワイプ(検索)と下スワイプ(通知シェード)を検出するModifier (仕様 6, 7, 8)
 *
 * - PointerEventPass.Initial で垂直スワイプを先行判定することで、グリッド内セルやアイコンの combinedClickable に
 *   イベントを奪われることなく、素早いフリックでも確実に検索・通知シェードを発火させる。
 * - All Apps ページや SearchOverlay、Discover ページなどの縦スクロール画面には適用しないことで、
 *   スクロール操作との競合を完全に防ぐ。
 */
fun Modifier.launcherVerticalSwipeGestures(
    enabled: Boolean = true,
    thresholdPx: Float = 75f,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit
): Modifier {
    if (!enabled) return this
    return this.pointerInput(enabled, thresholdPx, onSwipeUp, onSwipeDown) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val startX = down.position.x
            val startY = down.position.y
            val downTime = down.uptimeMillis
            var triggered = false

            while (!triggered) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                val dx = change.position.x - startX
                val dy = change.position.y - startY

                // 垂直方向が水平方向の1.15倍以上大きく、かつしきい値を超えた場合に即座に垂直スワイプとして発火
                // (素早いフリックで指を離したフレーム (!change.pressed) でも先に判定できるようここでチェック)
                if (abs(dy) > thresholdPx && abs(dy) > abs(dx) * 1.15f) {
                    triggered = true
                    event.changes.forEach { it.consume() }
                    if (dy < 0) {
                        onSwipeUp()
                    } else {
                        onSwipeDown()
                    }
                    break
                }

                // 水平方向のスワイプ（ページ切り替え）と判定された場合はループを抜けて HorizontalPager に委譲
                if (abs(dx) > thresholdPx && abs(dx) > abs(dy) * 1.15f) {
                    break
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


package com.myenvironment.launcher.core.search

object AppIndexLayout {
    fun edgeDistanceDp(configured: Int, systemGestureDp: Float, availableWidthDp: Float): Float =
        maxOf(configured.coerceIn(32, 128).toFloat(), systemGestureDp + 8f)
            .coerceAtMost((availableWidthDp - 172f).coerceAtLeast(16f))

    fun heightDp(availableHeightDp: Float): Float = minOf(480f, (availableHeightDp - 16f).coerceAtLeast(1f))

    fun topDp(availableHeightDp: Float, heightDp: Float): Float =
        (360f - heightDp / 2f).coerceIn(0f, (availableHeightDp - heightDp - 8f).coerceAtLeast(0f))
}

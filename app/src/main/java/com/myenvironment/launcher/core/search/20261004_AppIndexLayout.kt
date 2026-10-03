package com.myenvironment.launcher.core.search

object AppIndexLayout {
    data class Geometry(val topDp: Float, val heightDp: Float)

    fun anchorTopDp(headingCenterDp: Float, railHeightDp: Float, sectionCount: Int): Float =
        (headingCenterDp - railHeightDp / sectionCount / 2f).coerceAtLeast(0f)

    fun labelIndex(y: Float, starts: FloatArray): Int =
        starts.indexOfLast { y >= it }.coerceAtLeast(0)

    /** Capture once after the initial keyboard layout; do not recalculate on IME dismissal. */
    fun initialGeometry(availableHeightDp: Float): Geometry {
        val height = minOf(480f, (availableHeightDp - 16f).coerceAtLeast(1f))
        val top = (360f - height / 2f)
            .coerceIn(0f, (availableHeightDp - height - 8f).coerceAtLeast(0f))
        return Geometry(top, height)
    }
}

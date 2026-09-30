package com.myenvironment.launcher.core.feed.overlay

import kotlin.math.abs

/** Locks one pointer stream to vertical scrolling, the regular pager, or Google overlay. */
class OverlayDragSession(private val touchSlop: Float, private val width: Float) {
    enum class Owner { UNDECIDED, CONTENT, OVERLAY }
    var owner = Owner.UNDECIDED
        private set
    private var dx = 0f
    private var dy = 0f

    fun move(deltaX: Float, deltaY: Float, canBegin: () -> Boolean): Float? {
        dx += deltaX
        dy += deltaY
        if (owner == Owner.UNDECIDED) {
            if (abs(dy) > touchSlop && abs(dy) >= abs(dx)) owner = Owner.CONTENT
            else if (dx < -touchSlop) owner = Owner.CONTENT
            else if (dx > touchSlop && dx > abs(dy) * 1.3f) {
                owner = if (width > 0 && canBegin()) Owner.OVERLAY else Owner.CONTENT
            }
        }
        return if (owner == Owner.OVERLAY) (dx / width).coerceIn(0f, 1f) else null
    }
}

package com.myenvironment.launcher.ui

internal enum class DiscoverReturnTarget { CUSTOM, GOOGLE }

/** One return to the feed; the next HOME request retains normal launcher behavior. */
internal class DiscoverReturn {
    private var pending: DiscoverReturnTarget? = null
    fun remember(target: DiscoverReturnTarget) { pending = target }
    fun consume(customEnabled: Boolean, googleEnabled: Boolean): DiscoverReturnTarget? {
        val target = pending
        pending = null
        return target?.takeIf {
            when (it) {
                DiscoverReturnTarget.CUSTOM -> customEnabled
                DiscoverReturnTarget.GOOGLE -> googleEnabled
            }
        }
    }
}

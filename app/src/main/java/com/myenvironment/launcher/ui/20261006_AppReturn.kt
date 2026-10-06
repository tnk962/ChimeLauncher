package com.myenvironment.launcher.ui

/** Preserve the current page for one successful app launch, including HOME return. */
internal class AppReturn {
    private var pending = false
    fun remember() { pending = true }
    fun consume(): Boolean = pending.also { pending = false }
    fun clear() { pending = false }
}

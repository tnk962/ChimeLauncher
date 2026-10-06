package com.myenvironment.launcher.ui

import com.myenvironment.launcher.core.model.LauncherPage

// During startup a measured page index can outlive a settings/page-list update.
internal fun singlePagerKey(pages: List<LauncherPage>, index: Int): String =
    pages.getOrNull(index)?.id ?: "pending-single:$index"

internal fun dualPagerKey(slots: List<ExpandedPagerSlot>, index: Int): String =
    slots.getOrNull(index)?.visiblePageIds?.sorted()?.joinToString("|") ?: "pending-dual:$index"

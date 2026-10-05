package com.myenvironment.launcher.core.model

/** Build the actual pager contents without removing saved user pages or app data. */
internal fun LauncherSettings.visibleLauncherPages(userPages: List<LauncherPage>): List<LauncherPage> = buildList {
    if (discoverMode.showsCustomFeed) add(LauncherPage.FIXED_DISCOVER)
    if (allAppsPageEnabled) add(LauncherPage.FIXED_ALL_APPS)
    add(LauncherPage.FIXED_HOME)
    addAll(userPages.sortedBy { it.sortOrder })
    add(LauncherPage.FIXED_SETTINGS)
}

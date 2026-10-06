package com.myenvironment.launcher.ui

/** Closing search and returning from an app should preserve the underlying pager. */
internal fun keepPageOnHomeReturn(appReturn: Boolean, searchOpen: Boolean): Boolean = appReturn || searchOpen

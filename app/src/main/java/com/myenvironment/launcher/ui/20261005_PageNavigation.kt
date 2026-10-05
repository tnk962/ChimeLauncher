package com.myenvironment.launcher.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** A cancelled pager animation must not cancel the receiver of future HOME/Back requests. */
internal suspend fun Flow<String>.collectPageNavigationRequests(navigate: suspend (String) -> Unit) {
    collectLatest(navigate)
}

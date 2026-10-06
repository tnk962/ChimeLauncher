package com.myenvironment.launcher

import com.myenvironment.launcher.ui.AppReturn
import org.junit.Assert.*
import org.junit.Test

class AppReturnTest {
    @Test fun successfulLaunchPreservesPageOnlyOnce() {
        val state = AppReturn()
        assertFalse(state.consume())
        state.remember()
        assertTrue(state.consume())
        assertFalse(state.consume())
    }
    @Test fun backReturnClearsPendingHomeException() {
        val state = AppReturn()
        state.remember()
        state.clear()
        assertFalse(state.consume())
    }
}

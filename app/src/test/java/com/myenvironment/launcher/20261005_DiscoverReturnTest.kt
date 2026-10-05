package com.myenvironment.launcher

import com.myenvironment.launcher.ui.DiscoverReturn
import com.myenvironment.launcher.ui.DiscoverReturnTarget
import org.junit.Assert.*
import org.junit.Test

class DiscoverReturnTest {
    @Test fun ordinaryHomeHasNoReturnTarget() {
        assertNull(DiscoverReturn().consume(true, true))
    }
    @Test fun articleReturnIsConsumedOnceForBothFeeds() {
        DiscoverReturnTarget.entries.forEach { target ->
            val state = DiscoverReturn()
            state.remember(target)
            assertEquals(target, state.consume(true, true))
            assertNull(state.consume(true, true))
        }
    }
    @Test fun disabledFeedCannotBeRestoredLater() {
        DiscoverReturnTarget.entries.forEach { target ->
            val state = DiscoverReturn()
            state.remember(target)
            assertNull(state.consume(false, false))
            assertNull(state.consume(true, true))
        }
    }
    @Test fun latestDepartureReplacesOldTarget() {
        val state = DiscoverReturn()
        state.remember(DiscoverReturnTarget.CUSTOM)
        state.remember(DiscoverReturnTarget.GOOGLE)
        assertEquals(DiscoverReturnTarget.GOOGLE, state.consume(true, true))
    }
}

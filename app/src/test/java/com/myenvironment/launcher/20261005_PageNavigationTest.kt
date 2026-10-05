package com.myenvironment.launcher

import com.myenvironment.launcher.ui.collectPageNavigationRequests
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageNavigationTest {
    @Test fun interruptedAnimationStillReceivesHomeAndBackRequests() = runTest {
        val requests = MutableSharedFlow<String>()
        val visited = mutableListOf<String>()
        val receiver = backgroundScope.launch {
            requests.collectPageNavigationRequests { target ->
                visited += target
                if (target == "interrupted") throw CancellationException("Pager interrupted by touch")
            }
        }
        runCurrent()
        requests.emit("interrupted")
        runCurrent()
        assertTrue(receiver.isActive)
        assertEquals(1, requests.subscriptionCount.value)
        requests.emit("home")
        runCurrent()
        requests.emit("home")
        runCurrent()
        assertEquals(listOf("interrupted", "home", "home"), visited)
    }

    @Test fun homeRequestSupersedesPendingAnimationWithoutLosingReceiver() = runTest {
        val requests = MutableSharedFlow<String>()
        val visited = mutableListOf<String>()
        var oldAnimationCompleted = false
        backgroundScope.launch {
            requests.collectPageNavigationRequests { target ->
                visited += target
                if (target == "settings") {
                    delay(1_000)
                    oldAnimationCompleted = true
                }
            }
        }
        runCurrent()
        requests.emit("settings")
        runCurrent()
        requests.emit("home")
        runCurrent()
        assertEquals(listOf("settings", "home"), visited)
        assertFalse(oldAnimationCompleted)
        assertEquals(1, requests.subscriptionCount.value)
    }

    @Test fun leavingCompositionCancelsReceiverAndAnimation() = runTest {
        val requests = MutableSharedFlow<String>()
        var animationCancelled = false
        val receiver = backgroundScope.launch {
            requests.collectPageNavigationRequests {
                try { delay(1_000) } finally { animationCancelled = true }
            }
        }
        runCurrent()
        requests.emit("home")
        runCurrent()
        receiver.cancel()
        runCurrent()
        assertTrue(animationCancelled)
        assertEquals(0, requests.subscriptionCount.value)
    }
}

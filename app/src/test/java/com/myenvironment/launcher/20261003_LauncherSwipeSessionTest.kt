package com.myenvironment.launcher

import com.myenvironment.launcher.ui.components.LauncherSwipeAction
import com.myenvironment.launcher.ui.components.LauncherSwipeSession
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSwipeSessionTest {
    private fun session(density: Float = 1f) = LauncherSwipeSession(75f, 64f * density, 8f * density)

    @Test fun `notification waits for release and fires only once`() {
        val swipe = session()
        assertEquals(LauncherSwipeAction.CONSUME, swipe.move(0f, 80f, true))
        assertEquals(LauncherSwipeAction.CONSUME, swipe.move(0f, 120f, true))
        assertEquals(LauncherSwipeAction.OPEN_NOTIFICATION, swipe.move(0f, 120f, false))
        assertEquals(LauncherSwipeAction.NONE, swipe.move(0f, 120f, false))
    }

    @Test fun `small and diagonal downward gestures do not open notifications`() {
        assertEquals(LauncherSwipeAction.NONE, session().move(0f, 63f, false))
        assertEquals(LauncherSwipeAction.NONE, session().move(50f, 80f, false))
        assertEquals(LauncherSwipeAction.OPEN_NOTIFICATION, session().move(40f, 80f, false))
        assertEquals(LauncherSwipeAction.OPEN_NOTIFICATION, session().move(30f, 80f, false))
    }

    @Test fun `initial sideways motion permanently rejects notifications in either direction`() {
        for (direction in listOf(-1f, 1f)) {
            val swipe = session()
            assertEquals(LauncherSwipeAction.NONE, swipe.move(20f * direction, 5f, true))
            assertEquals(LauncherSwipeAction.NONE, swipe.move(20f * direction, 140f, false))
        }
    }

    @Test fun `page swipe keeps ownership even when ending vertically`() {
        for (direction in listOf(-1f, 1f)) {
            val swipe = session()
            assertEquals(LauncherSwipeAction.DELEGATE, swipe.move(100f * direction, 20f, true))
            assertEquals(LauncherSwipeAction.NONE, swipe.move(100f * direction, 300f, false))
        }
    }

    @Test fun `notification distance is density independent`() {
        val swipe = session(3f)
        assertEquals(LauncherSwipeAction.NONE, swipe.move(0f, 100f, true))
        assertEquals(LauncherSwipeAction.NONE, swipe.move(0f, 191f, true))
        assertEquals(LauncherSwipeAction.OPEN_NOTIFICATION, swipe.move(0f, 192f, false))
    }

    @Test fun `returning below notification threshold cancels activation`() {
        val swipe = session()
        assertEquals(LauncherSwipeAction.CONSUME, swipe.move(0f, 100f, true))
        assertEquals(LauncherSwipeAction.CONSUME, swipe.move(0f, 30f, false))
    }

    @Test fun `upward search still activates before release at its original threshold`() {
        val swipe = session(3f)
        assertEquals(LauncherSwipeAction.NONE, swipe.move(0f, -75f, true))
        assertEquals(LauncherSwipeAction.SEARCH, swipe.move(0f, -76f, true))
        assertEquals(LauncherSwipeAction.NONE, swipe.move(0f, -100f, false))
    }
}

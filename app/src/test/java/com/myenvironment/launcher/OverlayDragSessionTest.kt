package com.myenvironment.launcher

import com.myenvironment.launcher.core.feed.overlay.OverlayDragSession
import com.myenvironment.launcher.core.update.AppUpdateParser
import org.junit.Assert.*
import org.junit.Test

class OverlayDragSessionTest {
    @Test fun `preview can upgrade to equal numbered stable without offering a downgrade`() {
        assertTrue(AppUpdateParser.isNewerVersion("1.2.0-discover-preview.1", "v1.2.0"))
        assertFalse(AppUpdateParser.isNewerVersion("1.2.0-discover-preview.1", "v1.1.0"))
        assertFalse(AppUpdateParser.isNewerVersion("1.2.0", "v1.2.0-discover-preview.1"))
        assertFalse(AppUpdateParser.isNewerVersion("1.2.0+build-1", "v1.2.0"))
    }

    @Test fun `vertical reading cannot turn into Google reveal mid gesture`() {
        val drag = OverlayDragSession(8f, 400f)
        var binds = 0
        assertNull(drag.move(2f, 25f) { binds++; true })
        assertNull(drag.move(300f, 0f) { binds++; true })
        assertEquals(0, binds)
        assertEquals(OverlayDragSession.Owner.CONTENT, drag.owner)
    }

    @Test fun `opening can be reversed and sends bounded progress only`() {
        val drag = OverlayDragSession(8f, 400f)
        var begins = 0
        assertNull(drag.move(4f, 0f) { begins++; true })
        assertEquals(0.25f, drag.move(96f, 0f) { begins++; true }!!, 0.0001f)
        assertEquals(1f, drag.move(500f, 0f) { begins++; true }!!, 0.0001f)
        assertEquals(0f, drag.move(-700f, 0f) { begins++; true }!!, 0.0001f)
        assertEquals(1, begins)
    }

    @Test fun `absent companion and normal pager direction never capture drag`() {
        val unavailable = OverlayDragSession(8f, 400f)
        assertNull(unavailable.move(20f, 0f) { false })
        assertNull(unavailable.move(200f, 0f) { error("Must not retry within same gesture") })
        val pager = OverlayDragSession(8f, 400f)
        assertNull(pager.move(-20f, 0f) { error("Normal pager direction") })
    }
}

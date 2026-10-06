package com.myenvironment.launcher

import com.myenvironment.launcher.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class FolderSizingTest {
    private val folder = LayoutItem("folder", "home", ItemType.FOLDER, "", label = "Tools",
        compact = GridPosition(0,0), expanded = GridPosition(0,0),
        folderApps = listOf(FolderApp("app", "pkg", label = "App")))
    @Test fun resizeRetainsContentsNameAndIdentity() {
        val result = LayoutSnapshot(items = listOf(folder)).sizeFolder("folder",2,2,5,6,8,6)!!.items.single()
        assertEquals(2,result.spanX);assertEquals(2,result.spanY)
        assertEquals(folder.folderApps,result.folderApps);assertEquals(folder.label,result.label);assertEquals(folder.id,result.id)
        assertEquals(result,Json.decodeFromString<LayoutItem>(Json.encodeToString(result)))
    }
    @Test fun wholeRectangleAvoidsOtherIconsInBothLayouts() {
        val blocker = folder.copy(id="other",type=ItemType.WIDGET,spanX=2,spanY=2,compact=GridPosition(1,0),expanded=GridPosition(0,1))
        val result=LayoutSnapshot(items=listOf(folder,blocker)).sizeFolder("folder",2,2,5,6,5,6)!!.items.first()
        for (expanded in listOf(false,true)) assertTrue(result.occupiedCells(expanded,5,6).intersect(blocker.occupiedCells(expanded,5,6)).isEmpty())
    }
    @Test fun unavailableLayoutRejectsResizeWithoutLosingContents() {
        val blocker=folder.copy(id="block",type=ItemType.WIDGET,compact=GridPosition(0,0),expanded=GridPosition(1,0),spanX=2,spanY=2)
        val snapshot=LayoutSnapshot(items=listOf(folder,blocker))
        assertNull(snapshot.sizeFolder("folder",2,2,2,2,3,2))
        assertEquals(1,snapshot.items.first().spanX)
        assertNull(snapshot.sizeFolder("folder",0,2,5,6,5,6))
    }
    @Test fun shrinkingBackToOneCellRetainsApps() {
        val snapshot=LayoutSnapshot(items=listOf(folder.copy(spanX=2,spanY=2)))
        val result=snapshot.sizeFolder("folder",1,1,5,6,5,6)!!.items.single()
        assertEquals(folder.folderApps,result.folderApps);assertEquals(1,result.spanX);assertEquals(1,result.spanY)
    }
}

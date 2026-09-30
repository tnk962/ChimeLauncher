package com.myenvironment.launcher

import com.myenvironment.launcher.core.backup.NovaBackupConverter
import com.myenvironment.launcher.core.backup.NovaFavoriteRecord
import com.myenvironment.launcher.core.backup.NovaGridPreference
import com.myenvironment.launcher.core.model.AppInfo
import com.myenvironment.launcher.core.model.BackupLayoutItem
import com.myenvironment.launcher.core.model.BackupPage
import com.myenvironment.launcher.core.model.BackupPayload
import com.myenvironment.launcher.core.model.GridPosition
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LauncherPage
import com.myenvironment.launcher.core.model.LauncherSettings
import com.myenvironment.launcher.core.model.LayoutItem
import com.myenvironment.launcher.core.search.DefaultSearchEngine
import androidx.compose.ui.geometry.Offset
import com.myenvironment.launcher.core.widget.WidgetHostManager
import com.myenvironment.launcher.ui.LauncherViewModel
import com.myenvironment.launcher.ui.home.HomePageGridMetrics
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherCoreLogicTest {

    @Test
    fun `resolvePosition converts compact position to expanded when expanded is null`() {
        val item = LayoutItem(
            id = "item_1",
            pageId = LauncherPage.PAGE_ID_HOME,
            type = ItemType.APP,
            packageName = "com.openai.chatgpt",
            label = "ChatGPT",
            compact = GridPosition(x = 3, y = 4),
            expanded = null
        )

        // Compact mode -> compact position
        assertEquals(GridPosition(3, 4), item.resolvePosition(isExpanded = false, expandedColumns = 8, expandedRows = 6))

        // Expanded mode with null expanded -> falls back to compact position
        assertEquals(GridPosition(3, 4), item.resolvePosition(isExpanded = true, expandedColumns = 8, expandedRows = 6))
    }

    @Test
    fun `resolvePosition uses explicit expanded position when provided`() {
        val item = LayoutItem(
            id = "item_2",
            pageId = LauncherPage.PAGE_ID_HOME,
            type = ItemType.APP,
            packageName = "com.openai.chatgpt",
            label = "ChatGPT",
            compact = GridPosition(x = 1, y = 1),
            expanded = GridPosition(x = 6, y = 2)
        )

        assertEquals(GridPosition(1, 1), item.resolvePosition(isExpanded = false, expandedColumns = 8, expandedRows = 6))
        assertEquals(GridPosition(6, 2), item.resolvePosition(isExpanded = true, expandedColumns = 8, expandedRows = 6))
    }

    @Test
    fun `DefaultSearchEngine prioritizes prefix matches and includes web search query`() {
        val engine = DefaultSearchEngine()
        val apps = listOf(
            AppInfo(packageName = "com.android.chrome", activityName = "Main", label = "Chrome"),
            AppInfo(packageName = "com.openai.chatgpt", activityName = "Main", label = "ChatGPT"),
            AppInfo(packageName = "jp.chatwork", activityName = "Main", label = "Chatwork"),
            AppInfo(packageName = "com.spotify.music", activityName = "Main", label = "Spotify")
        )
        val shortcuts = listOf(
            LayoutItem(
                id = "sc_1",
                pageId = LauncherPage.PAGE_ID_HOME,
                type = ItemType.SHORTCUT,
                packageName = "",
                targetUri = "https://chatgpt.com/?Temporary=true",
                label = "ChatGPT 新規チャット",
                compact = GridPosition(0, 0)
            )
        )

        val result = engine.search("cha", apps, shortcuts)

        assertEquals(2, result.matchingApps.size)
        assertEquals("ChatGPT", result.matchingApps[0].label)
        assertEquals("Chatwork", result.matchingApps[1].label)
        assertEquals(1, result.matchingShortcuts.size)
        assertEquals("ChatGPT 新規チャット", result.matchingShortcuts[0].label)
        assertEquals("cha", result.webSearchQuery)
    }

    @Test
    fun `BackupPayload serializes and deserializes preserving missing app metadata`() {
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        val payload = BackupPayload(
            schemaVersion = 1,
            createdAt = "2026-09-30T00:00:00+09:00",
            pages = listOf(
                BackupPage(
                    id = "home",
                    name = "HOME",
                    items = listOf(
                        BackupLayoutItem(
                            id = "1",
                            type = ItemType.APP,
                            packageName = "com.openai.chatgpt",
                            label = "ChatGPT",
                            compact = GridPosition(0, 0),
                            expanded = GridPosition(0, 0)
                        )
                    )
                )
            ),
            dock = emptyList(),
            settings = LauncherSettings(layoutLocked = true)
        )

        val encoded = json.encodeToString(payload)
        assertTrue(encoded.contains("\"packageName\": \"com.openai.chatgpt\""))
        assertTrue(encoded.contains("\"layoutLocked\": true"))

        val decoded = json.decodeFromString<BackupPayload>(encoded)
        assertEquals(1, decoded.schemaVersion)
        assertEquals("com.openai.chatgpt", decoded.pages.first().items.first().packageName)
        assertTrue(decoded.settings.layoutLocked)
    }

    @Test
    fun `NovaBackupConverter ignores widgets, counts grid dimensions, and builds multiple pages in order`() {
        val records = listOf(
            // Screen 0: 5x2 Clock Widget at top (should be ignored)
            NovaFavoriteRecord(
                id = 1L,
                title = null,
                intent = null,
                container = NovaFavoriteRecord.CONTAINER_DESKTOP,
                screen = 0,
                cellX = 0,
                cellY = 0,
                spanX = 5,
                spanY = 2,
                itemType = NovaFavoriteRecord.ITEM_TYPE_APPWIDGET,
                appWidgetId = 12
            ),
            // Screen 0: App icons placed on 6x7 grid (up to cellX=5, cellY=6)
            NovaFavoriteRecord(
                id = 2L,
                title = "Chrome",
                intent = "#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;component=com.android.chrome/com.google.android.apps.chrome.Main;end",
                container = NovaFavoriteRecord.CONTAINER_DESKTOP,
                screen = 0,
                cellX = 0,
                cellY = 2,
                itemType = NovaFavoriteRecord.ITEM_TYPE_APPLICATION
            ),
            NovaFavoriteRecord(
                id = 3L,
                title = "ChatGPT",
                intent = "#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;component=com.openai.chatgpt/.MainActivity;end",
                container = NovaFavoriteRecord.CONTAINER_DESKTOP,
                screen = 0,
                cellX = 5,
                cellY = 6,
                itemType = NovaFavoriteRecord.ITEM_TYPE_APPLICATION
            ),
            // Screen 1 (Page 2): App icons
            NovaFavoriteRecord(
                id = 4L,
                title = "Slack",
                intent = "#Intent;action=android.intent.action.MAIN;component=com.Slack/.ui.HomeActivity;end",
                container = NovaFavoriteRecord.CONTAINER_DESKTOP,
                screen = 1,
                cellX = 1,
                cellY = 0,
                itemType = NovaFavoriteRecord.ITEM_TYPE_APPLICATION
            ),
            // Screen 2 (Page 3): App icons
            NovaFavoriteRecord(
                id = 5L,
                title = "Spotify",
                intent = "#Intent;action=android.intent.action.MAIN;component=com.spotify.music/.MainActivity;end",
                container = NovaFavoriteRecord.CONTAINER_DESKTOP,
                screen = 2,
                cellX = 2,
                cellY = 3,
                itemType = NovaFavoriteRecord.ITEM_TYPE_APPLICATION
            ),
            // Dock item (Hotseat)
            NovaFavoriteRecord(
                id = 6L,
                title = "Gmail",
                intent = "#Intent;action=android.intent.action.MAIN;component=com.google.android.gm/.ConversationListActivityGmail;end",
                container = NovaFavoriteRecord.CONTAINER_HOTSEAT,
                screen = 0,
                cellX = 0,
                cellY = 0,
                itemType = NovaFavoriteRecord.ITEM_TYPE_APPLICATION
            )
        )

        val payload = NovaBackupConverter.convertRecordsToBackupPayload(
            records = records,
            orderedScreenIds = listOf(0, 1, 2),
            gridPref = NovaGridPreference(),
            currentSettings = LauncherSettings(compactGridColumns = 5, compactGridRows = 6)
        )

        // 1. グリッド数が最大座標 (cellX=5 -> 6列, cellY=6 -> 7行) に更新されていること
        assertEquals(6, payload.settings.compactGridColumns)
        assertEquals(7, payload.settings.compactGridRows)
        assertEquals(6, payload.settings.expandedGridColumns)
        assertEquals(7, payload.settings.expandedGridRows)

        // 2. ページ数が 3ページ (HOME, Page 2, Page 3) 作成されていること
        assertEquals(3, payload.pages.size)
        assertEquals(LauncherPage.PAGE_ID_HOME, payload.pages[0].id)
        assertEquals("Page 2", payload.pages[1].name)
        assertEquals("Page 3", payload.pages[2].name)

        // 3. ウィジェットが無視され、HOMEには2つのアプリが順番通りに配置されていること
        assertEquals(2, payload.pages[0].items.size)
        assertEquals("com.android.chrome", payload.pages[0].items[0].packageName)
        assertEquals(GridPosition(0, 2), payload.pages[0].items[0].compact)
        assertEquals("com.openai.chatgpt", payload.pages[0].items[1].packageName)
        assertEquals("com.openai.chatgpt.MainActivity", payload.pages[0].items[1].activityName)
        assertEquals(GridPosition(5, 6), payload.pages[0].items[1].compact)

        // 4. Page 2, Page 3 のアイコンも正しく配置されていること
        assertEquals(1, payload.pages[1].items.size)
        assertEquals("com.Slack", payload.pages[1].items[0].packageName)
        assertEquals(GridPosition(1, 0), payload.pages[1].items[0].compact)

        assertEquals(1, payload.pages[2].items.size)
        assertEquals("com.spotify.music", payload.pages[2].items[0].packageName)
        assertEquals(GridPosition(2, 3), payload.pages[2].items[0].compact)

        // 5. Dock にもコピーされていること
        assertEquals(1, payload.dock.size)
        assertEquals("com.google.android.gm", payload.dock[0].packageName)
    }

    @Test
    fun `NovaBackupConverter parses grid size from Nova preferences XML`() {
        val xml = """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <map>
                <int name="desktop_grid_rows" value="8" />
                <int name="desktop_grid_cols" value="6" />
            </map>
        """.trimIndent()

        val pref = NovaBackupConverter.parsePreferencesXml(xml)
        assertEquals(6, pref.columns)
        assertEquals(8, pref.rows)
    }

    @Test
    fun `buildExpandedDualSlots keeps Discover and Settings as 1-page full screen and middle pages as dual spreads`() {
        val page2 = LauncherPage(id = "page_2", name = "Page 2", sortOrder = 1, isFixed = false)
        val pages = listOf(
            LauncherPage.FIXED_DISCOVER,
            LauncherPage.FIXED_ALL_APPS,
            LauncherPage.FIXED_HOME,
            page2,
            LauncherPage.FIXED_SETTINGS
        )

        val slots = com.myenvironment.launcher.ui.buildExpandedDualSlots(pages)

        // Slot 0: Discover (1ページ全画面固定)
        // Slot 1: All Apps + HOME (左右2ページ見開き)
        // Slot 2: HOME + Page 2 (左右2ページ見開き)
        // Slot 3: 設定 (1ページ全画面固定)
        assertEquals(4, slots.size)
        assertTrue(slots[0] is com.myenvironment.launcher.ui.ExpandedPagerSlot.SingleFull)
        assertEquals(LauncherPage.PAGE_ID_DISCOVER, slots[0].primaryPage.id)

        assertTrue(slots[1] is com.myenvironment.launcher.ui.ExpandedPagerSlot.DualSpread)
        assertEquals(
            setOf(LauncherPage.PAGE_ID_ALL_APPS, LauncherPage.PAGE_ID_HOME),
            slots[1].visiblePageIds
        )

        assertTrue(slots[2] is com.myenvironment.launcher.ui.ExpandedPagerSlot.DualSpread)
        assertEquals(
            setOf(LauncherPage.PAGE_ID_HOME, "page_2"),
            slots[2].visiblePageIds
        )

        assertTrue(slots[3] is com.myenvironment.launcher.ui.ExpandedPagerSlot.SingleFull)
        assertEquals(LauncherPage.PAGE_ID_SETTINGS, slots[3].primaryPage.id)

        assertEquals(0, com.myenvironment.launcher.ui.resolveExpandedDualSlotIndex(slots, LauncherPage.PAGE_ID_DISCOVER))
        assertEquals(1, com.myenvironment.launcher.ui.resolveExpandedDualSlotIndex(slots, LauncherPage.PAGE_ID_ALL_APPS))
        assertEquals(1, com.myenvironment.launcher.ui.resolveExpandedDualSlotIndex(slots, LauncherPage.PAGE_ID_HOME))
        assertEquals(2, com.myenvironment.launcher.ui.resolveExpandedDualSlotIndex(slots, "page_2"))
        assertEquals(3, com.myenvironment.launcher.ui.resolveExpandedDualSlotIndex(slots, LauncherPage.PAGE_ID_SETTINGS))
    }

    @Test
    fun `LayoutItem occupiedCells and resolveClampedPosition handle multi-cell widgets`() {
        val widgetItem = LayoutItem(
            id = "widget_1",
            pageId = LauncherPage.PAGE_ID_HOME,
            type = ItemType.WIDGET,
            packageName = "com.google.android.calendar",
            activityName = "com.google.android.calendar.widget.MonthWidgetProvider",
            label = "Google Calendar",
            appWidgetId = 101,
            compact = GridPosition(x = 3, y = 5),
            spanX = 4,
            spanY = 2
        )

        // 5x6 グリッドでは (3, 5) に 4x2 ウィジェットははみ出すため、(1, 4) にクランプされること
        val clamped = widgetItem.resolveClampedPosition(isExpanded = false, columns = 5, rows = 6)
        assertEquals(GridPosition(1, 4), clamped)

        val cells = widgetItem.occupiedCells(isExpanded = false, columns = 5, rows = 6)
        assertEquals(8, cells.size)
        assertTrue(cells.contains(GridPosition(1, 4)))
        assertTrue(cells.contains(GridPosition(4, 5)))
    }

    @Test
    fun `WidgetHostManager calculateDefaultSpan converts dp and targetCells to grid span`() {
        // targetCellWidth / targetCellHeight が指定されている場合はそれを優先（最大グリッド数でクランプ）
        assertEquals(
            4 to 2,
            WidgetHostManager.calculateDefaultSpan(
                minWidthDp = 250,
                minHeightDp = 110,
                targetCellWidth = 4,
                targetCellHeight = 2,
                maxColumns = 5,
                maxRows = 6
            )
        )

        // targetCell が 0 の場合は (dp + 30) / 70 で換算
        assertEquals(
            4 to 2,
            WidgetHostManager.calculateDefaultSpan(
                minWidthDp = 250,
                minHeightDp = 110,
                targetCellWidth = 0,
                targetCellHeight = 0,
                maxColumns = 5,
                maxRows = 6
            )
        )
    }

    @Test
    fun `findBestGridPlacementForSpan avoids collisions for multi-cell widgets`() {
        val existingItems = listOf(
            // (0, 0) に 1x1 アプリアイコン
            LayoutItem(
                id = "app_1",
                pageId = LauncherPage.PAGE_ID_HOME,
                type = ItemType.APP,
                packageName = "com.example.app1",
                label = "App 1",
                compact = GridPosition(0, 0)
            ),
            // (1, 0) に 4x1 ウィジェット -> 0行目はすべて埋まる
            LayoutItem(
                id = "widget_top",
                pageId = LauncherPage.PAGE_ID_HOME,
                type = ItemType.WIDGET,
                packageName = "com.example.clock",
                activityName = "com.example.clock.ClockWidget",
                label = "Clock",
                compact = GridPosition(1, 0),
                spanX = 4,
                spanY = 1
            )
        )

        // 5x6 グリッドに 3x2 ウィジェットを配置すると、0行目は埋まっているため (0, 1) が選ばれること
        val (pos, span) = LauncherViewModel.findBestGridPlacementForSpan(
            existingItems = existingItems,
            requestedSpanX = 3,
            requestedSpanY = 2,
            preferredCell = null,
            isExpandedMode = false,
            columns = 5,
            rows = 6
        )
        assertEquals(GridPosition(0, 1), pos)
        assertEquals(3 to 2, span)
    }

    @Test
    fun `BackupPayload serializes and deserializes WIDGET items with span and appWidgetId`() {
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        val payload = BackupPayload(
            schemaVersion = 1,
            createdAt = "2026-09-30T12:00:00+09:00",
            pages = listOf(
                BackupPage(
                    id = LauncherPage.PAGE_ID_HOME,
                    name = "HOME",
                    items = listOf(
                        BackupLayoutItem(
                            id = "widget_cal",
                            type = ItemType.WIDGET,
                            packageName = "com.google.android.calendar",
                            activityName = "com.google.android.calendar.widget.MonthWidgetProvider",
                            label = "カレンダー",
                            appWidgetId = 42,
                            compact = GridPosition(0, 0),
                            expanded = GridPosition(2, 1),
                            spanX = 4,
                            spanY = 3
                        )
                    )
                )
            ),
            dock = emptyList(),
            settings = LauncherSettings()
        )

        val encoded = json.encodeToString(payload)
        val decoded = json.decodeFromString<BackupPayload>(encoded)
        val restoredWidget = decoded.pages.first().items.first()

        assertEquals(ItemType.WIDGET, restoredWidget.type)
        assertEquals(42, restoredWidget.appWidgetId)
        assertEquals(4, restoredWidget.spanX)
        assertEquals(3, restoredWidget.spanY)
        assertEquals("com.google.android.calendar.widget.MonthWidgetProvider", restoredWidget.activityName)
    }

    @Test
    fun `HomePageGridMetrics resolveDropCell maps root coordinates and clamps multi-cell span`() {
        val metrics = HomePageGridMetrics(
            pageId = "page_2",
            boundsInRoot = androidx.compose.ui.geometry.Rect(
                left = 100f,
                top = 200f,
                right = 600f,
                bottom = 800f
            ),
            columns = 5,
            rows = 6,
            cellWidthPx = 100f,
            cellHeightPx = 100f,
            isExpanded = false
        )

        // (100 + 210, 200 + 310) -> cellWidth=100, cellHeight=100 -> ((210 + 50)/100)=2, ((310 + 50)/100)=3
        val cellForSingle = metrics.resolveDropCell(
            topLeftInRoot = Offset(x = 310f, y = 510f),
            spanX = 1,
            spanY = 1
        )
        assertEquals(GridPosition(2, 3), cellForSingle)

        // 4x3 ウィジェットを右端付近にドロップした場合は (columns - spanX, rows - spanY) = (1, 3) にクランプされること
        val cellForLargeWidget = metrics.resolveDropCell(
            topLeftInRoot = Offset(x = 550f, y = 750f),
            spanX = 4,
            spanY = 3
        )
        assertEquals(GridPosition(1, 3), cellForLargeWidget)

        // 高密度表示用の仮想スケール定数が 0.85f (1未満の拡大キャンバス比率) であること
        assertTrue(WidgetHostManager.WIDGET_CONTENT_SCALE in 0.7f..0.95f)
    }

    @Test
    fun `isTouchInsideScrollableWidget ignores vertical swipe only on scrollable widgets like Keep`() {
        val keepWidget = LayoutItem(
            id = "widget_keep",
            pageId = LauncherPage.PAGE_ID_HOME,
            type = ItemType.WIDGET,
            packageName = "com.google.android.keep",
            activityName = "com.google.android.keep.widget.MemoryAppWidgetProvider",
            label = "Keep メモ",
            appWidgetId = 201,
            compact = GridPosition(x = 0, y = 0),
            spanX = 3,
            spanY = 3
        )
        val clockWidget = LayoutItem(
            id = "widget_clock",
            pageId = LauncherPage.PAGE_ID_HOME,
            type = ItemType.WIDGET,
            packageName = "com.google.android.deskclock",
            activityName = "com.android.alarmclock.DigitalAppWidgetProvider",
            label = "時計",
            appWidgetId = 202,
            compact = GridPosition(x = 3, y = 0),
            spanX = 2,
            spanY = 1
        )
        val appIcon = LayoutItem(
            id = "app_chrome",
            pageId = LauncherPage.PAGE_ID_HOME,
            type = ItemType.APP,
            packageName = "com.android.chrome",
            label = "Chrome",
            compact = GridPosition(x = 0, y = 4)
        )
        val items = listOf(keepWidget, clockWidget, appIcon)
        val scrollableCheck: (LayoutItem) -> Boolean = { it.appWidgetId == 201 }

        // 1. スクロール可能な Keep ウィジェット (0..300, 0..300) 上のタッチ -> true（ランチャーの検索・通知スワイプを無効化し、Keepのスクロールのみ反応）
        assertTrue(
            com.myenvironment.launcher.ui.home.isTouchInsideScrollableWidget(
                touchOffset = Offset(150f, 150f),
                items = items,
                isExpanded = false,
                columns = 5,
                rows = 6,
                cellWidthPx = 100f,
                cellHeightPx = 100f,
                isWidgetScrollable = scrollableCheck
            )
        )

        // 2. スクロールしない時計ウィジェット (300..500, 0..100) 上のタッチ -> false（ランチャーの検索・通知スワイプが反応）
        org.junit.Assert.assertFalse(
            com.myenvironment.launcher.ui.home.isTouchInsideScrollableWidget(
                touchOffset = Offset(400f, 50f),
                items = items,
                isExpanded = false,
                columns = 5,
                rows = 6,
                cellWidthPx = 100f,
                cellHeightPx = 100f,
                isWidgetScrollable = scrollableCheck
            )
        )

        // 3. 通常アプリアイコンや空白セル上のタッチ -> false（ランチャーの検索・通知スワイプが反応）
        org.junit.Assert.assertFalse(
            com.myenvironment.launcher.ui.home.isTouchInsideScrollableWidget(
                touchOffset = Offset(50f, 450f),
                items = items,
                isExpanded = false,
                columns = 5,
                rows = 6,
                cellWidthPx = 100f,
                cellHeightPx = 100f,
                isWidgetScrollable = scrollableCheck
            )
        )
    }
}

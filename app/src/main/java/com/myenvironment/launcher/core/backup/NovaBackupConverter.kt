package com.myenvironment.launcher.core.backup

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.myenvironment.launcher.core.model.AppInfo
import com.myenvironment.launcher.core.model.BackupLayoutItem
import com.myenvironment.launcher.core.model.BackupPage
import com.myenvironment.launcher.core.model.BackupPayload
import com.myenvironment.launcher.core.model.DockItem
import com.myenvironment.launcher.core.model.GridPosition
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LauncherPage
import com.myenvironment.launcher.core.model.LauncherSettings
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLDecoder
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Nova Launcher の SQLite `favorites` テーブルの1行を表す中間データモデル
 */
data class NovaFavoriteRecord(
    val id: Long,
    val title: String? = null,
    val intent: String? = null,
    val container: Int = CONTAINER_DESKTOP,
    val screen: Int = 0,
    val cellX: Int = 0,
    val cellY: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val itemType: Int = ITEM_TYPE_APPLICATION,
    val appWidgetId: Int = -1,
    val appWidgetProvider: String? = null
) {
    companion object {
        const val CONTAINER_DESKTOP = -100
        const val CONTAINER_HOTSEAT = -101

        const val ITEM_TYPE_APPLICATION = 0
        const val ITEM_TYPE_SHORTCUT = 1
        const val ITEM_TYPE_FOLDER = 2
        const val ITEM_TYPE_APPWIDGET = 4
        const val ITEM_TYPE_CUSTOM_APPWIDGET = 5
        const val ITEM_TYPE_DEEP_SHORTCUT = 6
    }
}

/**
 * Nova Launcher の設定 XML (`com.teslacoilsw.launcher_preferences.xml` 等) から抽出したグリッド設定
 */
data class NovaGridPreference(
    val columns: Int? = null,
    val rows: Int? = null
)

/**
 * Nova Launcher のバックアップファイル (`.novabackup` ZIPアーカイブ または `launcher.db` / `nova.db` SQLiteファイル) を解析し、
 * My Launcher 向けの [BackupPayload] に変換するコンバーター。
 *
 * 変換仕様:
 * 1. ウィジェット (`itemType == 4, 5` や `appWidgetId >= 0`) は無視し、アプリアイコンの並びのみを抽出する。
 * 2. 配置されているアイコンの座標（および設定XML）から縦・横のグリッド数を数え、My Launcherの設定 (`LauncherSettings`) のグリッド数を変更する。
 * 3. ページ数（HOME + 右スワイプで増える追加ページ `Page 2`, `Page 3`...）を構築し、各ページに順番にアプリアイコンを配置する。
 */
object NovaBackupConverter {

    private val COMPONENT_REGEX = Regex("""component=([^/;]+)/(?:([^;]+))""")
    private val PACKAGE_REGEX = Regex("""package=([^;]+)""")

    private val XML_INT_TAG_REGEX = Regex("""<int\s+name="([^"]+)"\s+value="(-?\d+)"\s*/>""")
    private val XML_STRING_TAG_REGEX = Regex("""<string\s+name="([^"]+)">([^<]+)</string>""")
    private val GRID_PAIR_REGEX = Regex("""(\d+)\s*[xX×,]\s*(\d+)""")

    private val NOVA_INTERNAL_PACKAGES = setOf(
        "com.teslacoilsw.launcher",
        "com.teslacoilsw.launcher.prime"
    )

    /**
     * バイト配列またはファイル名から、Nova Launcher 等のバックアップ（ZIP / GZIP / SQLite DB / XML / CSV）かどうかを判定する。
     */
    fun isNovaBackupOrSqlite(bytes: ByteArray, fileName: String? = null): Boolean {
        val lowerName = fileName?.lowercase().orEmpty()
        if (lowerName.endsWith(".novabackup") ||
            lowerName.endsWith(".backup") ||
            lowerName.endsWith(".bak") ||
            lowerName.endsWith(".db") ||
            lowerName.endsWith(".sqlite") ||
            lowerName.endsWith(".sqlite3") ||
            lowerName.endsWith(".zip") ||
            lowerName.endsWith(".gz")
        ) {
            return true
        }
        return isZipHeader(bytes) ||
            isGzipHeader(bytes) ||
            isSqliteHeader(bytes) ||
            findZipOffset(bytes) >= 0 ||
            findSqliteOffset(bytes) >= 0
    }

    fun isZipHeader(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && // 'P'
            bytes[1] == 0x4B.toByte() && // 'K'
            (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte())
    }

    fun isGzipHeader(bytes: ByteArray): Boolean {
        return bytes.size >= 2 &&
            bytes[0] == 0x1F.toByte() &&
            bytes[1] == 0x8B.toByte()
    }

    fun isSqliteHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 16) return false
        val header = String(bytes, 0, 15, Charsets.US_ASCII)
        return header == "SQLite format 3"
    }

    private fun findZipOffset(bytes: ByteArray, maxSearchBytes: Int = 4096): Int {
        val limit = minOf(bytes.size - 4, maxSearchBytes)
        for (i in 0..limit) {
            if (bytes[i] == 0x50.toByte() &&
                bytes[i + 1] == 0x4B.toByte() &&
                bytes[i + 2] == 0x03.toByte() &&
                bytes[i + 3] == 0x04.toByte()
            ) {
                return i
            }
        }
        return -1
    }

    private fun findSqliteOffset(bytes: ByteArray, maxSearchBytes: Int = 4096): Int {
        val sig = "SQLite format 3".toByteArray(Charsets.US_ASCII)
        val limit = minOf(bytes.size - sig.size, maxSearchBytes)
        for (i in 0..limit) {
            var match = true
            for (j in sig.indices) {
                if (bytes[i + j] != sig[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    /**
     * `.novabackup` (ZIP / GZIP)、SQLite `.db`、XML、または CSV/テキスト形式のバイト列を読み取り、[BackupPayload] に変換する。
     */
    fun convertFromBytes(
        context: Context,
        bytes: ByteArray,
        installedApps: List<AppInfo>,
        currentSettings: LauncherSettings,
        fallbackDock: List<DockItem> = emptyList()
    ): BackupPayload {
        // GZIP 圧縮されている場合はまず展開
        val rawBytes = if (isGzipHeader(bytes)) {
            runCatching {
                java.util.zip.GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            }.getOrDefault(bytes)
        } else {
            bytes
        }

        val dbCandidates = mutableListOf<Pair<String, ByteArray>>()
        val xmlTexts = mutableListOf<String>()
        var gridPref = NovaGridPreference()

        val zipOffset = if (isZipHeader(rawBytes)) 0 else findZipOffset(rawBytes)
        val sqliteOffset = if (isSqliteHeader(rawBytes)) 0 else findSqliteOffset(rawBytes)

        if (zipOffset >= 0) {
            val zipSlice = if (zipOffset == 0) rawBytes else rawBytes.copyOfRange(zipOffset, rawBytes.size)
            collectEntriesFromZip(zipSlice, dbCandidates, xmlTexts)
            for (xmlText in xmlTexts) {
                val parsedPref = parsePreferencesXml(xmlText)
                if (parsedPref.columns != null || parsedPref.rows != null) {
                    gridPref = NovaGridPreference(
                        columns = parsedPref.columns ?: gridPref.columns,
                        rows = parsedPref.rows ?: gridPref.rows
                    )
                }
            }
        } else if (sqliteOffset >= 0) {
            val dbSlice = if (sqliteOffset == 0) rawBytes else rawBytes.copyOfRange(sqliteOffset, rawBytes.size)
            dbCandidates.add("launcher.db" to dbSlice)
        }

        var extractedRecords: List<NovaFavoriteRecord> = emptyList()
        var extractedScreens: List<Int> = emptyList()

        if (dbCandidates.isNotEmpty()) {
            // launcher.db / nova.db を優先しつつ、favorites テーブルにレコードがある DB を採用する
            val sortedCandidates = dbCandidates.sortedBy { (name, _) ->
                val lower = name.lowercase()
                when {
                    lower.endsWith("launcher.db") -> 0
                    lower.endsWith("nova.db") -> 1
                    else -> 2
                }
            }

            for ((_, dbBytes) in sortedCandidates) {
                val result = runCatching { readSqliteDatabase(context, dbBytes) }.getOrNull()
                if (result != null && result.first.isNotEmpty()) {
                    extractedRecords = result.first
                    extractedScreens = result.second
                    break
                }
            }
        }

        // DBから抽出できなかった場合は、ZIP内のXMLまたは単体ファイル (XML / CSV / TSV / テキスト) からアプリアイコン配置を解析
        if (extractedRecords.isEmpty()) {
            val textContent = if (xmlTexts.isNotEmpty()) {
                xmlTexts.joinToString("\n")
            } else {
                runCatching { rawBytes.toString(Charsets.UTF_8).trimStart('\uFEFF').trim() }.getOrDefault("")
            }

            if (textContent.isNotBlank()) {
                val parsedPref = parsePreferencesXml(textContent)
                if (parsedPref.columns != null || parsedPref.rows != null) {
                    gridPref = parsedPref
                }
                extractedRecords = parseTextOrCsvOrXmlRecords(textContent, installedApps)
            }
        }

        if (extractedRecords.isEmpty()) {
            throw IllegalArgumentException("バックアップファイル内にホーム画面のアイコンデータが見つかりませんでした")
        }

        val nowIso = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        return convertRecordsToBackupPayload(
            records = extractedRecords,
            orderedScreenIds = extractedScreens,
            gridPref = gridPref,
            installedApps = installedApps,
            currentSettings = currentSettings,
            fallbackDock = fallbackDock,
            createdAt = nowIso
        )
    }

    private fun collectEntriesFromZip(
        zipBytes: ByteArray,
        dbCandidates: MutableList<Pair<String, ByteArray>>,
        xmlTexts: MutableList<String>,
        depth: Int = 0
    ) {
        if (depth > 2) return
        runCatching {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val entryName = entry.name
                        val rawEntryBytes = zis.readBytes()
                        val entryBytes = if (isGzipHeader(rawEntryBytes)) {
                            runCatching {
                                java.util.zip.GZIPInputStream(ByteArrayInputStream(rawEntryBytes)).use { it.readBytes() }
                            }.getOrDefault(rawEntryBytes)
                        } else {
                            rawEntryBytes
                        }

                        when {
                            isSqliteHeader(entryBytes) ||
                                entryName.endsWith(".db", ignoreCase = true) ||
                                entryName.endsWith(".sqlite", ignoreCase = true) ||
                                entryName.endsWith(".sqlite3", ignoreCase = true) -> {
                                dbCandidates.add(entryName to entryBytes)
                            }
                            isZipHeader(entryBytes) -> {
                                collectEntriesFromZip(entryBytes, dbCandidates, xmlTexts, depth + 1)
                            }
                            entryName.endsWith(".xml", ignoreCase = true) ||
                                entryName.endsWith(".json", ignoreCase = true) ||
                                entryName.endsWith(".csv", ignoreCase = true) ||
                                entryName.endsWith(".txt", ignoreCase = true) -> {
                                val text = runCatching { entryBytes.toString(Charsets.UTF_8) }.getOrDefault("")
                                if (text.isNotBlank()) {
                                    xmlTexts.add(text)
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
    }

    /**
     * XML (`<favorite .../>`)、CSV (`page,x,y,packageName,...`)、またはテキスト内の Intent / パッケージ名一覧から
     * [NovaFavoriteRecord] リストを生成する。
     */
    fun parseTextOrCsvOrXmlRecords(
        text: String,
        installedApps: List<AppInfo> = emptyList()
    ): List<NovaFavoriteRecord> {
        val records = mutableListOf<NovaFavoriteRecord>()
        var nextId = 1L

        // 1. Launcher XML (<favorite ... packageName="..." className="..." screen="..." x="..." y="..." />)
        val favTagRegex = Regex("""<(?:favorite|appicon|shortcut)\b([^>]+)/?>""", RegexOption.IGNORE_CASE)
        val attrRegex = Regex("""([a-zA-Z0-9_:]+)\s*=\s*["']([^"']*)["']""")
        for (match in favTagRegex.findAll(text)) {
            val attrBlock = match.groupValues[1]
            val attrs = mutableMapOf<String, String>()
            for (attr in attrRegex.findAll(attrBlock)) {
                val key = attr.groupValues[1].substringAfterLast(':').lowercase()
                attrs[key] = attr.groupValues[2]
            }
            val pkg = attrs["packagename"] ?: attrs["package"] ?: continue
            val cls = attrs["classname"] ?: attrs["class"] ?: attrs["activity"] ?: ""
            val title = attrs["title"] ?: attrs["label"]
            val screen = attrs["screen"]?.toIntOrNull() ?: attrs["page"]?.toIntOrNull() ?: 0
            val x = attrs["x"]?.toIntOrNull() ?: attrs["cellx"]?.toIntOrNull() ?: 0
            val y = attrs["y"]?.toIntOrNull() ?: attrs["celly"]?.toIntOrNull() ?: 0
            val containerStr = attrs["container"]?.lowercase().orEmpty()
            val container = if (containerStr.contains("hotseat") || containerStr.contains("dock") || containerStr == "-101") {
                NovaFavoriteRecord.CONTAINER_HOTSEAT
            } else {
                NovaFavoriteRecord.CONTAINER_DESKTOP
            }
            val comp = if (cls.isNotBlank()) "$pkg/$cls" else "$pkg/"
            records.add(
                NovaFavoriteRecord(
                    id = nextId++,
                    title = title,
                    intent = "#Intent;component=$comp;end",
                    container = container,
                    screen = screen,
                    cellX = x,
                    cellY = y
                )
            )
        }
        if (records.isNotEmpty()) return records

        // 2. CSV / TSV / 行ベース解析
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("//") }
        if (lines.isEmpty()) return emptyList()

        val delimiter = if (lines.first().contains('\t')) '\t' else ','
        val firstCells = lines.first().split(delimiter).map { it.trim().trim('"', '\'').lowercase() }
        val hasHeader = firstCells.any {
            it in setOf("packagename", "package", "pkg", "app", "component", "intent", "label", "title")
        }

        if (hasHeader && lines.size > 1) {
            val pkgIdx = firstCells.indexOfFirst { it in setOf("packagename", "package", "pkg", "component", "intent") }
            val actIdx = firstCells.indexOfFirst { it in setOf("activityname", "activity", "classname", "class") }
            val labelIdx = firstCells.indexOfFirst { it in setOf("label", "title", "name", "app") }
            val pageIdx = firstCells.indexOfFirst { it in setOf("screen", "page", "pageid", "page_index") }
            val xIdx = firstCells.indexOfFirst { it in setOf("x", "cellx", "cell_x", "col", "column") }
            val yIdx = firstCells.indexOfFirst { it in setOf("y", "celly", "cell_y", "row") }
            val containerIdx = firstCells.indexOfFirst { it in setOf("container", "location", "type") }

            var autoIndex = 0
            for (line in lines.drop(1)) {
                val cols = line.split(delimiter).map { it.trim().trim('"', '\'') }
                val rawPkgOrComp = if (pkgIdx in cols.indices) cols[pkgIdx] else cols.firstOrNull().orEmpty()
                if (rawPkgOrComp.isBlank()) continue

                val intentStr = when {
                    rawPkgOrComp.contains("component=") || rawPkgOrComp.contains("package=") -> rawPkgOrComp
                    rawPkgOrComp.contains("/") -> "#Intent;component=$rawPkgOrComp;end"
                    else -> {
                        val act = if (actIdx in cols.indices) cols[actIdx] else ""
                        if (act.isNotBlank()) "#Intent;component=$rawPkgOrComp/$act;end" else "#Intent;package=$rawPkgOrComp;end"
                    }
                }
                val label = if (labelIdx in cols.indices) cols[labelIdx].takeIf { it.isNotBlank() } else null
                val screen = if (pageIdx in cols.indices) {
                    cols[pageIdx].filter { it.isDigit() }.toIntOrNull()?.let { if (it > 0 && cols[pageIdx].startsWith("page", ignoreCase = true)) it - 1 else it } ?: 0
                } else {
                    autoIndex / 20
                }
                val x = if (xIdx in cols.indices) cols[xIdx].toIntOrNull() ?: (autoIndex % 4) else (autoIndex % 4)
                val y = if (yIdx in cols.indices) cols[yIdx].toIntOrNull() ?: ((autoIndex % 20) / 4) else ((autoIndex % 20) / 4)
                val isDock = containerIdx in cols.indices && (
                    cols[containerIdx].equals("dock", ignoreCase = true) ||
                        cols[containerIdx].equals("hotseat", ignoreCase = true) ||
                        cols[containerIdx] == "-101"
                    )

                records.add(
                    NovaFavoriteRecord(
                        id = nextId++,
                        title = label,
                        intent = intentStr,
                        container = if (isDock) NovaFavoriteRecord.CONTAINER_HOTSEAT else NovaFavoriteRecord.CONTAINER_DESKTOP,
                        screen = screen,
                        cellX = x,
                        cellY = y
                    )
                )
                autoIndex++
            }
            if (records.isNotEmpty()) return records
        }

        // 3. 任意のテキスト・JSON断片・CSVから "#Intent;..." またはパッケージ名 ("com.xxx.yyy") を抽出
        val pkgTokenRegex = Regex("""(?:component=([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)/([^\s;"',>]*))|(?:["'\s,;]|^)([a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+){2,})(?:["'\s,;]|$)""")
        var idx = 0
        for (line in lines) {
            for (m in pkgTokenRegex.findAll(line)) {
                val compPkg = m.groupValues[1]
                val compAct = m.groupValues[2]
                val barePkg = m.groupValues[3]
                val pkg = compPkg.ifBlank { barePkg }
                if (pkg.isBlank() || pkg in NOVA_INTERNAL_PACKAGES) continue

                val intentStr = if (compPkg.isNotBlank()) {
                    "#Intent;component=$compPkg/$compAct;end"
                } else {
                    "#Intent;package=$pkg;end"
                }
                val page = idx / 20
                val cellInPage = idx % 20
                records.add(
                    NovaFavoriteRecord(
                        id = nextId++,
                        title = installedApps.firstOrNull { it.packageName == pkg }?.label,
                        intent = intentStr,
                        container = NovaFavoriteRecord.CONTAINER_DESKTOP,
                        screen = page,
                        cellX = cellInPage % 4,
                        cellY = cellInPage / 4
                    )
                )
                idx++
            }
        }
        return records
    }

    /**
     * Nova Launcher の SharedPreferences XML からデスクトップグリッドの列数・行数を抽出する。
     */
    fun parsePreferencesXml(xmlText: String): NovaGridPreference {
        if (xmlText.isBlank()) return NovaGridPreference()

        val intMap = mutableMapOf<String, Int>()
        XML_INT_TAG_REGEX.findAll(xmlText).forEach { match ->
            val key = match.groupValues[1].lowercase()
            val value = match.groupValues[2].toIntOrNull()
            if (value != null && value in 2..20) {
                intMap[key] = value
            }
        }

        var cols: Int? = intMap["desktop_grid_cols"]
            ?: intMap["desktop_columns"]
            ?: intMap["desktop_cols"]
            ?: intMap["workspace_columns"]

        var rows: Int? = intMap["desktop_grid_rows"]
            ?: intMap["desktop_rows"]
            ?: intMap["workspace_rows"]

        if (cols == null || rows == null) {
            XML_STRING_TAG_REGEX.findAll(xmlText).forEach { match ->
                val key = match.groupValues[1].lowercase()
                val rawVal = match.groupValues[2].trim()
                if (key == "desktop_grid" || key == "desktop_grid_size") {
                    val pairMatch = GRID_PAIR_REGEX.find(rawVal)
                    if (pairMatch != null) {
                        // Nova の文字列形式は通常 "rows x cols" (例: "6x5" = 6行×5列)
                        val first = pairMatch.groupValues[1].toIntOrNull()
                        val second = pairMatch.groupValues[2].toIntOrNull()
                        if (first != null && second != null && first in 2..20 && second in 2..20) {
                            if (rows == null) rows = first
                            if (cols == null) cols = second
                        }
                    }
                }
            }
        }

        return NovaGridPreference(columns = cols, rows = rows)
    }

    /**
     * 抽出した `favorites` レコード群から My Launcher 向けの [BackupPayload] を構築する。
     */
    fun convertRecordsToBackupPayload(
        records: List<NovaFavoriteRecord>,
        orderedScreenIds: List<Int> = emptyList(),
        gridPref: NovaGridPreference = NovaGridPreference(),
        installedApps: List<AppInfo> = emptyList(),
        currentSettings: LauncherSettings = LauncherSettings(),
        fallbackDock: List<DockItem> = emptyList(),
        createdAt: String = "2026-09-30T00:00:00+09:00"
    ): BackupPayload {
        val installedByPkg = installedApps.groupBy { it.packageName }

        // 1. デスクトップ上のフォルダ (_id -> folder record) を把握
        val folderMap = records
            .filter {
                it.container == NovaFavoriteRecord.CONTAINER_DESKTOP &&
                    it.itemType == NovaFavoriteRecord.ITEM_TYPE_FOLDER
            }
            .associateBy { it.id.toInt() }

        // 2. デスクトップ直接配置のアプリアイコン & フォルダ内アプリアイコンを抽出（ウィジェットは無視）
        data class PlacedCandidate(
            val record: NovaFavoriteRecord,
            val screen: Int,
            val preferredX: Int,
            val preferredY: Int,
            val isDirectDesktop: Boolean,
            val app: ExtractedApp
        )

        val desktopCandidates = mutableListOf<PlacedCandidate>()
        val dockCandidates = mutableListOf<Pair<NovaFavoriteRecord, ExtractedApp>>()

        for (rec in records) {
            if (isWidgetRecord(rec)) continue
            if (rec.itemType == NovaFavoriteRecord.ITEM_TYPE_FOLDER) continue

            val extracted = extractAppFromRecord(rec, installedByPkg) ?: continue

            when {
                rec.container == NovaFavoriteRecord.CONTAINER_DESKTOP -> {
                    desktopCandidates.add(
                        PlacedCandidate(
                            record = rec,
                            screen = rec.screen,
                            preferredX = rec.cellX.coerceAtLeast(0),
                            preferredY = rec.cellY.coerceAtLeast(0),
                            isDirectDesktop = true,
                            app = extracted
                        )
                    )
                }

                rec.container == NovaFavoriteRecord.CONTAINER_HOTSEAT -> {
                    dockCandidates.add(rec to extracted)
                }

                rec.container > 0 -> {
                    // デスクトップ上のフォルダに入っているアプリアイコンも、そのページのフォルダ位置基準で順番に配置可能にする
                    val parentFolder = folderMap[rec.container]
                    if (parentFolder != null) {
                        desktopCandidates.add(
                            PlacedCandidate(
                                record = rec,
                                screen = parentFolder.screen,
                                preferredX = parentFolder.cellX.coerceAtLeast(0),
                                preferredY = parentFolder.cellY.coerceAtLeast(0),
                                isDirectDesktop = false,
                                app = extracted
                            )
                        )
                    }
                }
            }
        }

        // 3. 縦横のグリッド数を数える
        // アプリアイコンの最大座標 + 1 と、デスクトップ全アイテム（ウィジェットの配置範囲等も含む）の座標、およびXML設定値から算出
        val allDesktopRecords = records.filter { it.container == NovaFavoriteRecord.CONTAINER_DESKTOP }
        val maxAppCol = desktopCandidates.maxOfOrNull { it.preferredX + 1 } ?: 0
        val maxAppRow = desktopCandidates.maxOfOrNull { it.preferredY + 1 } ?: 0
        val maxDesktopCol = allDesktopRecords.maxOfOrNull {
            it.cellX.coerceAtLeast(0) + it.spanX.coerceIn(1, 10)
        } ?: 0
        val maxDesktopRow = allDesktopRecords.maxOfOrNull {
            it.cellY.coerceAtLeast(0) + it.spanY.coerceIn(1, 10)
        } ?: 0

        val countedColumns = maxOf(
            gridPref.columns ?: 0,
            maxAppCol,
            maxDesktopCol
        ).let { if (it <= 0) currentSettings.compactGridColumns else it }
            .coerceIn(3, 16)

        // 1ページあたりの最大アイテム数がグリッド容量を超える場合（フォルダ展開時など）は行数も十分に確保する
        val maxItemsInSinglePage = desktopCandidates
            .groupBy { it.screen }
            .maxOfOrNull { it.value.size } ?: 0
        val minRowsForCapacity = if (maxItemsInSinglePage > 0) {
            ceil(maxItemsInSinglePage.toDouble() / countedColumns.toDouble()).toInt()
        } else {
            0
        }

        val countedRows = maxOf(
            gridPref.rows ?: 0,
            maxAppRow,
            maxDesktopRow,
            minRowsForCapacity
        ).let { if (it <= 0) currentSettings.compactGridRows else it }
            .coerceIn(3, 16)

        // 4. ページ順序（HOME + 右スワイプで増えていくページ）を決定する
        val screensWithApps = desktopCandidates.map { it.screen }.toSet()
        val orderedTargetScreens: List<Int> = if (screensWithApps.isEmpty()) {
            listOf(0)
        } else if (orderedScreenIds.isNotEmpty()) {
            val fromWorkspace = orderedScreenIds.filter { it in screensWithApps }
            val remaining = (screensWithApps - fromWorkspace.toSet()).sorted()
            (fromWorkspace + remaining).distinct()
        } else {
            screensWithApps.sorted()
        }

        // 5. 各ページに順番にアプリアイコンを配置していく
        val backupPages = orderedTargetScreens.mapIndexed { pageIndex, screenId ->
            val isHomePage = pageIndex == 0
            val pageId = if (isHomePage) LauncherPage.PAGE_ID_HOME else "page_${pageIndex + 1}"
            val pageName = if (isHomePage) "HOME" else "Page ${pageIndex + 1}"

            // 直接デスクトップに置かれたアイコンを最優先し、上から下 (cellY)、左から右 (cellX) の順番にソート
            val candidatesForPage = desktopCandidates
                .filter { it.screen == screenId }
                .sortedWith(
                    compareBy<PlacedCandidate>(
                        { !it.isDirectDesktop },
                        { it.preferredY },
                        { it.preferredX },
                        { it.record.screen },
                        { it.record.cellY },
                        { it.record.cellX },
                        { it.record.id }
                    )
                )

            val occupiedCells = mutableSetOf<GridPosition>()
            val pageItems = mutableListOf<BackupLayoutItem>()

            for (candidate in candidatesForPage) {
                val targetX = candidate.preferredX.coerceIn(0, countedColumns - 1)
                val targetY = candidate.preferredY.coerceIn(0, countedRows - 1)
                val preferredPos = GridPosition(x = targetX, y = targetY)

                val assignedPos = if (preferredPos !in occupiedCells) {
                    preferredPos
                } else {
                    findNextFreeCell(occupiedCells, countedColumns, countedRows)
                }

                occupiedCells.add(assignedPos)
                pageItems.add(
                    BackupLayoutItem(
                        id = UUID.randomUUID().toString(),
                        type = ItemType.APP,
                        packageName = candidate.app.packageName,
                        activityName = candidate.app.activityName,
                        targetUri = "",
                        label = candidate.app.label,
                        compact = assignedPos,
                        expanded = assignedPos,
                        spanX = 1,
                        spanY = 1
                    )
                )
            }

            BackupPage(
                id = pageId,
                name = pageName,
                sortOrder = pageIndex,
                items = pageItems
            )
        }

        // 6. Dock (Hotseat) のアプリアイコンを順番に並べる
        val restoredDock: List<DockItem> = if (dockCandidates.isNotEmpty()) {
            dockCandidates
                .sortedWith(
                    compareBy<Pair<NovaFavoriteRecord, ExtractedApp>>(
                        { it.first.cellX },
                        { it.first.screen },
                        { it.first.cellY },
                        { it.first.id }
                    )
                )
                .mapIndexed { idx, (_, app) ->
                    DockItem(
                        id = UUID.randomUUID().toString(),
                        positionIndex = idx,
                        type = ItemType.APP,
                        packageName = app.packageName,
                        activityName = app.activityName,
                        targetUri = "",
                        label = app.label
                    )
                }
        } else {
            fallbackDock
        }

        // 7. 数えた縦横グリッド数で My Launcher の設定を更新する
        val updatedSettings = currentSettings.copy(
            compactGridColumns = countedColumns,
            compactGridRows = countedRows,
            expandedGridColumns = countedColumns,
            expandedGridRows = countedRows
        )

        return BackupPayload(
            schemaVersion = BackupPayload.CURRENT_SCHEMA_VERSION,
            createdAt = createdAt,
            pages = backupPages,
            dock = restoredDock,
            settings = updatedSettings
        )
    }

    data class ExtractedApp(
        val packageName: String,
        val activityName: String,
        val label: String
    )

    fun isWidgetRecord(record: NovaFavoriteRecord): Boolean {
        if (record.itemType == NovaFavoriteRecord.ITEM_TYPE_APPWIDGET ||
            record.itemType == NovaFavoriteRecord.ITEM_TYPE_CUSTOM_APPWIDGET
        ) {
            return true
        }
        if (record.appWidgetId >= 0 && record.intent.isNullOrBlank()) {
            return true
        }
        if (!record.appWidgetProvider.isNullOrBlank() && record.intent.isNullOrBlank()) {
            return true
        }
        return false
    }

    fun extractAppFromRecord(
        record: NovaFavoriteRecord,
        installedByPkg: Map<String, List<AppInfo>> = emptyMap()
    ): ExtractedApp? {
        if (isWidgetRecord(record)) return null
        val rawIntent = record.intent?.trim() ?: return null
        if (rawIntent.isEmpty()) return null

        val (pkg, act) = parsePackageAndActivityFromIntent(rawIntent) ?: return null
        if (pkg.isBlank() || pkg in NOVA_INTERNAL_PACKAGES) return null

        val matchedApps = installedByPkg[pkg].orEmpty()
        val matchedApp = if (act.isNotBlank()) {
            matchedApps.find { it.activityName == act } ?: matchedApps.firstOrNull()
        } else {
            matchedApps.firstOrNull()
        }

        val resolvedActivity = act.ifBlank { matchedApp?.activityName.orEmpty() }
        val resolvedLabel = record.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: matchedApp?.label
            ?: deriveLabelFromPackage(pkg)

        return ExtractedApp(
            packageName = pkg,
            activityName = resolvedActivity,
            label = resolvedLabel
        )
    }

    /**
     * Nova / Android Intent URI 文字列 (`#Intent;...;component=pkg/cls;...;end` 等) から
     * `packageName` と `activityName` を抽出する。
     */
    fun parsePackageAndActivityFromIntent(intentUri: String): Pair<String, String>? {
        val decoded = runCatching {
            if (intentUri.contains("%2F", ignoreCase = true) || intentUri.contains("%3B", ignoreCase = true)) {
                URLDecoder.decode(intentUri, "UTF-8")
            } else {
                intentUri
            }
        }.getOrDefault(intentUri)

        val compMatch = COMPONENT_REGEX.find(decoded) ?: COMPONENT_REGEX.find(intentUri)
        if (compMatch != null) {
            val pkg = compMatch.groupValues[1].trim()
            val rawCls = compMatch.groupValues[2].trim()
            val fullCls = when {
                rawCls.isEmpty() -> ""
                rawCls.startsWith(".") -> "$pkg$rawCls"
                !rawCls.contains(".") -> "$pkg.$rawCls"
                else -> rawCls
            }
            if (pkg.isNotEmpty()) {
                return pkg to fullCls
            }
        }

        val pkgMatch = PACKAGE_REGEX.find(decoded) ?: PACKAGE_REGEX.find(intentUri)
        if (pkgMatch != null) {
            val pkg = pkgMatch.groupValues[1].trim()
            if (pkg.isNotEmpty()) {
                return pkg to ""
            }
        }

        return null
    }

    private fun deriveLabelFromPackage(packageName: String): String {
        val lastSegment = packageName.substringAfterLast('.').ifBlank { packageName }
        return lastSegment.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun findNextFreeCell(
        occupied: Set<GridPosition>,
        columns: Int,
        rows: Int
    ): GridPosition {
        for (y in 0 until rows) {
            for (x in 0 until columns) {
                val pos = GridPosition(x = x, y = y)
                if (pos !in occupied) {
                    return pos
                }
            }
        }
        // 万一すべてのセルが埋まっている場合は最終セルにフォールバック
        return GridPosition(
            x = (columns - 1).coerceAtLeast(0),
            y = (rows - 1).coerceAtLeast(0)
        )
    }

    private fun readSqliteDatabase(
        context: Context,
        dbBytes: ByteArray
    ): Pair<List<NovaFavoriteRecord>, List<Int>> {
        val tempFile = File.createTempFile("nova_backup_", ".db", context.cacheDir)
        try {
            tempFile.writeBytes(dbBytes)
            val db = SQLiteDatabase.openDatabase(
                tempFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            try {
                val tables = getTableNames(db)
                val favoritesTable = tables.firstOrNull { it.equals("favorites", ignoreCase = true) }
                    ?: findTableWithFavoriteColumns(db, tables)
                    ?: return emptyList<NovaFavoriteRecord>() to emptyList()

                val orderedScreens = readWorkspaceScreens(db, tables)
                val records = readFavoriteRecords(db, favoritesTable)
                return records to orderedScreens
            } finally {
                db.close()
            }
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private fun getTableNames(db: SQLiteDatabase): List<String> {
        val result = mutableListOf<String>()
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (!name.isNullOrBlank()) {
                    result.add(name)
                }
            }
        }
        return result
    }

    private fun findTableWithFavoriteColumns(db: SQLiteDatabase, tables: List<String>): String? {
        for (table in tables) {
            val hasCols = runCatching {
                db.rawQuery("SELECT * FROM \"$table\" LIMIT 0", null).use { cursor ->
                    val names = cursor.columnNames.map { it.lowercase() }.toSet()
                    "intent" in names && "cellx" in names && "celly" in names
                }
            }.getOrDefault(false)
            if (hasCols) return table
        }
        return null
    }

    private fun readWorkspaceScreens(db: SQLiteDatabase, tables: List<String>): List<Int> {
        val screenTable = tables.firstOrNull { it.equals("workspaceScreens", ignoreCase = true) }
            ?: return emptyList()

        return runCatching {
            val pairs = mutableListOf<Pair<Int, Int>>() // (screenId, rank)
            db.rawQuery("SELECT * FROM \"$screenTable\"", null).use { cursor ->
                val colMap = cursor.columnIndexMap()
                while (cursor.moveToNext()) {
                    val id = cursor.getIntByNames(colMap, "_id", "id") ?: continue
                    val rank = cursor.getIntByNames(colMap, "screenRank", "rank") ?: id
                    pairs.add(id to rank)
                }
            }
            pairs.sortedWith(compareBy({ it.second }, { it.first })).map { it.first }
        }.getOrDefault(emptyList())
    }

    private fun readFavoriteRecords(db: SQLiteDatabase, tableName: String): List<NovaFavoriteRecord> {
        val records = mutableListOf<NovaFavoriteRecord>()
        db.rawQuery("SELECT * FROM \"$tableName\"", null).use { cursor ->
            val colMap = cursor.columnIndexMap()
            var fallbackId = 1L
            while (cursor.moveToNext()) {
                val id = cursor.getLongByNames(colMap, "_id", "id") ?: fallbackId++
                val title = cursor.getStringByNames(colMap, "title", "label")
                val intent = cursor.getStringByNames(colMap, "intent")
                val container = cursor.getDoubleByNames(colMap, "container")?.roundToInt()
                    ?: NovaFavoriteRecord.CONTAINER_DESKTOP
                val screen = cursor.getDoubleByNames(colMap, "screen", "rank")?.roundToInt() ?: 0
                val cellX = cursor.getDoubleByNames(colMap, "cellX", "cell_x")?.roundToInt() ?: 0
                val cellY = cursor.getDoubleByNames(colMap, "cellY", "cell_y")?.roundToInt() ?: 0
                val spanX = cursor.getDoubleByNames(colMap, "spanX", "span_x")?.roundToInt()?.coerceAtLeast(1) ?: 1
                val spanY = cursor.getDoubleByNames(colMap, "spanY", "span_y")?.roundToInt()?.coerceAtLeast(1) ?: 1
                val itemType = cursor.getIntByNames(colMap, "itemType", "item_type")
                    ?: NovaFavoriteRecord.ITEM_TYPE_APPLICATION
                val appWidgetId = cursor.getIntByNames(colMap, "appWidgetId", "app_widget_id") ?: -1
                val appWidgetProvider = cursor.getStringByNames(colMap, "appWidgetProvider", "app_widget_provider")

                records.add(
                    NovaFavoriteRecord(
                        id = id,
                        title = title,
                        intent = intent,
                        container = container,
                        screen = screen,
                        cellX = cellX,
                        cellY = cellY,
                        spanX = spanX,
                        spanY = spanY,
                        itemType = itemType,
                        appWidgetId = appWidgetId,
                        appWidgetProvider = appWidgetProvider
                    )
                )
            }
        }
        return records
    }

    private fun Cursor.columnIndexMap(): Map<String, Int> {
        return columnNames.mapIndexed { index, name -> name.lowercase() to index }.toMap()
    }

    private fun Cursor.getStringByNames(colMap: Map<String, Int>, vararg names: String): String? {
        for (name in names) {
            val idx = colMap[name.lowercase()] ?: continue
            if (!isNull(idx)) {
                return runCatching { getString(idx) }.getOrNull()
            }
        }
        return null
    }

    private fun Cursor.getIntByNames(colMap: Map<String, Int>, vararg names: String): Int? {
        for (name in names) {
            val idx = colMap[name.lowercase()] ?: continue
            if (!isNull(idx)) {
                return runCatching { getInt(idx) }.getOrNull()
            }
        }
        return null
    }

    private fun Cursor.getLongByNames(colMap: Map<String, Int>, vararg names: String): Long? {
        for (name in names) {
            val idx = colMap[name.lowercase()] ?: continue
            if (!isNull(idx)) {
                return runCatching { getLong(idx) }.getOrNull()
            }
        }
        return null
    }

    private fun Cursor.getDoubleByNames(colMap: Map<String, Int>, vararg names: String): Double? {
        for (name in names) {
            val idx = colMap[name.lowercase()] ?: continue
            if (!isNull(idx)) {
                return runCatching { getDouble(idx) }.getOrNull()
            }
        }
        return null
    }
}

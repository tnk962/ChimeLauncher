package com.myenvironment.launcher.core.launcher

import com.myenvironment.launcher.core.model.AppInfo
import com.myenvironment.launcher.core.model.LayoutItem

/**
 * 未インストールアプリ（Galaxy等の別端末バックアップから復元されたアイコン）のパッケージ種別と差異理由
 */
enum class MissingPackageOrigin(
    val badgeTitle: String,
    val description: String
) {
    GALAXY_STORE_EDITION(
        badgeTitle = "Galaxy版・別ストア版パッケージ",
        description = "Galaxy Store 等で配信されている専用パッケージIDのため、Pixel の Playストアでは ID が異なります。Playストア版IDまたはアプリ名検索で入手できます。"
    ),
    SAMSUNG_SYSTEM_OR_EXCLUSIVE(
        badgeTitle = "Galaxy (Samsung) 標準・固有アプリ",
        description = "Galaxy 標準または Samsung 固有アプリです。Playストアで入手できない場合は、Pixel 標準の同等アプリ（カメラ・時計・電卓・フォト等）にそのまま置き換えられます。"
    ),
    CARRIER_CUSTOM(
        badgeTitle = "キャリア向け・プリインストールパッケージ",
        description = "通信キャリア固有のパッケージIDです。Playストアでは通常版のアプリ名でキーワード検索すると見つかる場合があります。"
    ),
    GENERAL_APP(
        badgeTitle = "一般アプリ（パッケージID未一致）",
        description = "パッケージID直指定でヒットしない場合は、アプリ名やパッケージ内キーワードでPlayストアを直接検索できます。"
    )
}

/**
 * 端末内（Pixel等）に既にインストールされている代替・同一アプリ候補
 */
data class InstalledAppMatchCandidate(
    val appInfo: AppInfo,
    val matchReason: String,
    val score: Int
)

/**
 * 未インストールアプリの診断・代替パッケージ変換・検索キーワード抽出結果
 */
data class MissingAppResolution(
    val originalPackage: String,
    val originalLabel: String,
    val origin: MissingPackageOrigin,
    /** Google Playストアで配信されている正規パッケージID候補（元IDと異なる場合のみ先頭に配置） */
    val mappedPlayStorePackage: String?,
    /** Playストア検索 (`market://search?q=...`) に使う推奨キーワード（優先順） */
    val suggestedSearchQueries: List<String>,
    /** この端末（Pixel等）に既にインストールされている同一・代替アプリ候補 */
    val installedCandidates: List<InstalledAppMatchCandidate>
)

/**
 * Galaxy 等の別機種バックアップ（Nova Launcher 等）を Pixel 等で復元した際に、
 * パッケージIDの違い（Kindle for Samsung 等）やメーカー標準アプリの違いを診断し、
 * 「端末内の該当アプリへの紐付け」や「Playストアでのしつこい検索」を行うリゾルバー。
 */
object MissingAppResolver {

    /**
     * Galaxy Store版 / Amazon版 / 旧パッケージID -> Google Playストア正規パッケージID の既知マッピング
     */
    private val KNOWN_PLAY_STORE_PACKAGE_MAP: Map<String, Pair<String, String>> = mapOf(
        // Kindle (Galaxy Store版 "com.amazon.kindleForSamsung" 等 -> Google Play版 "com.amazon.kindle")
        "com.amazon.kindleforsamsung" to ("com.amazon.kindle" to "Amazon Kindle"),
        "com.amazon.kindlefc" to ("com.amazon.kindle" to "Amazon Kindle"),
        "jp.co.amazon.kindle" to ("com.amazon.kindle" to "Amazon Kindle"),
        "com.amazon.venezia" to ("com.amazon.mShop.android.shopping" to "Amazon ショッピング"),
        "com.amazon.mshop.android" to ("com.amazon.mShop.android.shopping" to "Amazon ショッピング"),
        "com.ebay.kr.gmarket.samsung" to ("com.ebay.kr.gmarket" to "Gmarket"),
        "com.netflix.mediaclient.samsung" to ("com.netflix.mediaclient" to "Netflix"),
        "com.spotify.music.samsung" to ("com.spotify.music" to "Spotify"),
        "com.microsoft.office.outlook.samsung" to ("com.microsoft.office.outlook" to "Microsoft Outlook"),
        "com.twitter.android" to ("com.twitter.android" to "X (Twitter)")
    )

    /**
     * Galaxy (Samsung) 標準システムアプリ -> Pixel (Google) 標準の同等アプリパッケージID ＆ 検索名
     */
    private data class SystemEquivalentSpec(
        val canonicalLabel: String,
        val pixelPackages: List<String>,
        val playStorePackage: String? = null
    )

    private val SAMSUNG_TO_PIXEL_EQUIVALENTS: Map<String, SystemEquivalentSpec> = mapOf(
        "com.sec.android.app.camera" to SystemEquivalentSpec(
            canonicalLabel = "カメラ",
            pixelPackages = listOf("com.google.android.GoogleCamera", "com.android.camera2")
        ),
        "com.sec.android.gallery3d" to SystemEquivalentSpec(
            canonicalLabel = "Google フォト / ギャラリー",
            pixelPackages = listOf("com.google.android.apps.photos", "com.google.android.apps.photosgo"),
            playStorePackage = "com.google.android.apps.photos"
        ),
        "com.sec.android.app.popupcalculator" to SystemEquivalentSpec(
            canonicalLabel = "電卓",
            pixelPackages = listOf("com.google.android.calculator", "com.android.calculator2"),
            playStorePackage = "com.google.android.calculator"
        ),
        "com.sec.android.app.clockpackage" to SystemEquivalentSpec(
            canonicalLabel = "時計",
            pixelPackages = listOf("com.google.android.deskclock", "com.android.deskclock"),
            playStorePackage = "com.google.android.deskclock"
        ),
        "com.sec.android.app.myfiles" to SystemEquivalentSpec(
            canonicalLabel = "Files by Google (ファイル)",
            pixelPackages = listOf("com.google.android.apps.nbu.files", "com.google.android.documentsui"),
            playStorePackage = "com.google.android.apps.nbu.files"
        ),
        "com.samsung.android.dialer" to SystemEquivalentSpec(
            canonicalLabel = "電話",
            pixelPackages = listOf("com.google.android.dialer", "com.android.dialer"),
            playStorePackage = "com.google.android.dialer"
        ),
        "com.samsung.android.incallui" to SystemEquivalentSpec(
            canonicalLabel = "電話",
            pixelPackages = listOf("com.google.android.dialer", "com.android.dialer"),
            playStorePackage = "com.google.android.dialer"
        ),
        "com.samsung.android.app.contacts" to SystemEquivalentSpec(
            canonicalLabel = "連絡先 (コンタクト)",
            pixelPackages = listOf("com.google.android.contacts", "com.android.contacts"),
            playStorePackage = "com.google.android.contacts"
        ),
        "com.samsung.android.contacts" to SystemEquivalentSpec(
            canonicalLabel = "連絡先 (コンタクト)",
            pixelPackages = listOf("com.google.android.contacts", "com.android.contacts"),
            playStorePackage = "com.google.android.contacts"
        ),
        "com.samsung.android.messaging" to SystemEquivalentSpec(
            canonicalLabel = "メッセージ",
            pixelPackages = listOf("com.google.android.apps.messaging", "com.android.messaging"),
            playStorePackage = "com.google.android.apps.messaging"
        ),
        "com.samsung.android.calendar" to SystemEquivalentSpec(
            canonicalLabel = "Google カレンダー",
            pixelPackages = listOf("com.google.android.calendar", "com.android.calendar"),
            playStorePackage = "com.google.android.calendar"
        ),
        "com.sec.android.app.voicenote" to SystemEquivalentSpec(
            canonicalLabel = "レコーダー",
            pixelPackages = listOf("com.google.android.apps.recorder"),
            playStorePackage = "com.google.android.apps.recorder"
        ),
        "com.samsung.android.app.notes" to SystemEquivalentSpec(
            canonicalLabel = "Samsung Notes / Google Keep",
            pixelPackages = listOf("com.samsung.android.app.notes", "com.google.android.keep"),
            playStorePackage = "com.google.android.keep"
        ),
        "com.samsung.android.app.reminder" to SystemEquivalentSpec(
            canonicalLabel = "Google ToDo (リマインダー)",
            pixelPackages = listOf("com.google.android.apps.tasks", "com.google.android.keep"),
            playStorePackage = "com.google.android.apps.tasks"
        ),
        "com.samsung.android.spay" to SystemEquivalentSpec(
            canonicalLabel = "Google ウォレット",
            pixelPackages = listOf("com.google.android.apps.walletnfcrel"),
            playStorePackage = "com.google.android.apps.walletnfcrel"
        ),
        "com.sec.android.app.sbrowser" to SystemEquivalentSpec(
            canonicalLabel = "Samsung Internet / Chrome",
            pixelPackages = listOf("com.sec.android.app.sbrowser", "com.android.chrome"),
            playStorePackage = "com.sec.android.app.sbrowser"
        ),
        "com.sec.android.app.shealth" to SystemEquivalentSpec(
            canonicalLabel = "Samsung Health / Fitbit",
            pixelPackages = listOf("com.sec.android.app.shealth", "com.fitbit.FitbitMobile"),
            playStorePackage = "com.sec.android.app.shealth"
        ),
        "com.samsung.android.oneconnect" to SystemEquivalentSpec(
            canonicalLabel = "SmartThings",
            pixelPackages = listOf("com.samsung.android.oneconnect"),
            playStorePackage = "com.samsung.android.oneconnect"
        ),
        "com.samsung.android.app.watchmanager" to SystemEquivalentSpec(
            canonicalLabel = "Galaxy Wearable",
            pixelPackages = listOf("com.samsung.android.app.watchmanager"),
            playStorePackage = "com.samsung.android.app.watchmanager"
        )
    )

    private val GENERIC_PACKAGE_SEGMENTS = setOf(
        "com", "jp", "co", "ne", "or", "net", "org", "io", "app", "apps", "android",
        "mobile", "client", "main", "activity", "launcher", "ui", "view", "sec",
        "samsung", "galaxy", "forsamsung", "google", "nttdocomo", "docomo", "kddi",
        "au", "softbank", " official", "release", "prod", "free", "paid"
    )

    /**
     * 未インストールアイテムの総合診断・代替候補・検索クエリを生成する
     */
    fun resolve(
        item: LayoutItem,
        installedApps: List<AppInfo>
    ): MissingAppResolution {
        val rawPkg = item.packageName.trim()
        val lowerPkg = rawPkg.lowercase()
        val rawLabel = item.label.trim()

        // 1. パッケージ由来の分類
        val origin = classifyOrigin(lowerPkg)

        // 2. Google Play 正規パッケージIDの推定
        val mappedPlayPkg = resolveMappedPlayStorePackage(rawPkg, lowerPkg, rawLabel)

        // 3. しつこく検索するためのキーワード候補リスト生成
        val searchQueries = buildSearchQueries(rawPkg, lowerPkg, rawLabel)

        // 4. 端末内（Pixel）のインストール済みアプリから同一・代替候補をしつこく探索
        val installedCandidates = findInstalledCandidates(
            rawPkg = rawPkg,
            lowerPkg = lowerPkg,
            rawLabel = rawLabel,
            mappedPlayPkg = mappedPlayPkg,
            installedApps = installedApps
        )

        return MissingAppResolution(
            originalPackage = rawPkg,
            originalLabel = rawLabel,
            origin = origin,
            mappedPlayStorePackage = mappedPlayPkg?.takeIf { !it.equals(rawPkg, ignoreCase = true) },
            suggestedSearchQueries = searchQueries,
            installedCandidates = installedCandidates
        )
    }

    private fun classifyOrigin(lowerPkg: String): MissingPackageOrigin {
        return when {
            lowerPkg in KNOWN_PLAY_STORE_PACKAGE_MAP ||
                lowerPkg.contains("forsamsung") ||
                lowerPkg.endsWith(".samsung") ||
                lowerPkg.endsWith("_samsung") ||
                lowerPkg.contains(".galaxy.") -> {
                MissingPackageOrigin.GALAXY_STORE_EDITION
            }

            lowerPkg.startsWith("com.sec.android.") ||
                lowerPkg.startsWith("com.samsung.android.") ||
                lowerPkg.startsWith("com.samsung.") ||
                lowerPkg.startsWith("com.sec.") -> {
                MissingPackageOrigin.SAMSUNG_SYSTEM_OR_EXCLUSIVE
            }

            lowerPkg.startsWith("jp.co.nttdocomo.") ||
                lowerPkg.startsWith("com.nttdocomo.") ||
                lowerPkg.startsWith("com.kddi.") ||
                lowerPkg.startsWith("jp.softbank.") -> {
                MissingPackageOrigin.CARRIER_CUSTOM
            }

            else -> MissingPackageOrigin.GENERAL_APP
        }
    }

    private fun resolveMappedPlayStorePackage(
        rawPkg: String,
        lowerPkg: String,
        rawLabel: String
    ): String? {
        // 1. 既知の Galaxy Store / 別エディション -> Play Store マップ
        KNOWN_PLAY_STORE_PACKAGE_MAP[lowerPkg]?.first?.let { return it }

        // 2. Kindle を含むパッケージ名・ラベルは "com.amazon.kindle" を最優先候補にする
        if (lowerPkg.contains("kindle") || rawLabel.contains("kindle", ignoreCase = true)) {
            return "com.amazon.kindle"
        }

        // 3. Samsung 標準アプリの Play Store 対応パッケージ
        SAMSUNG_TO_PIXEL_EQUIVALENTS[lowerPkg]?.playStorePackage?.let { return it }

        // 4. 末尾の "ForSamsung" / ".samsung" / "_samsung" を除去した正規パッケージID
        val stripped = rawPkg
            .replace(Regex("(?i)[._-]?for[_-]?samsung$"), "")
            .replace(Regex("(?i)[._-]samsung$"), "")
            .replace(Regex("(?i)[._-]galaxy$"), "")
            .trim('.')
        if (stripped.isNotBlank() && !stripped.equals(rawPkg, ignoreCase = true) && stripped.contains('.')) {
            return stripped
        }

        return null
    }

    private fun buildSearchQueries(
        rawPkg: String,
        lowerPkg: String,
        rawLabel: String
    ): List<String> {
        val queries = linkedSetOf<String>()

        // 1. 既知マップの正規アプリ名
        KNOWN_PLAY_STORE_PACKAGE_MAP[lowerPkg]?.second?.let { queries.add(it) }
        SAMSUNG_TO_PIXEL_EQUIVALENTS[lowerPkg]?.canonicalLabel?.let { queries.add(it) }

        if (lowerPkg.contains("kindle") || rawLabel.contains("kindle", ignoreCase = true)) {
            queries.add("Amazon Kindle")
            queries.add("Kindle")
        }

        // 2. アイコンのラベル名（ノイズ除去後）
        val cleanedLabel = rawLabel
            .replace(Regex("(?i)\\bfor\\s+samsung\\b"), "")
            .replace(Regex("(?i)\\bfor\\s+galaxy\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleanedLabel.isNotBlank()) {
            queries.add(cleanedLabel)
        }

        // 3. パッケージ名から抽出した意味のある単語トークン (例: com.amazon.kindleForSamsung -> "amazon kindle", "kindle")
        val pkgTokens = extractMeaningfulPackageTokens(rawPkg)
        if (pkgTokens.isNotEmpty()) {
            val joined = pkgTokens.joinToString(" ")
            if (joined.isNotBlank()) {
                queries.add(joined)
            }
            pkgTokens.lastOrNull()?.takeIf { it.length >= 3 }?.let { lastToken ->
                queries.add(lastToken)
            }
        }

        // 4. 元のパッケージ名そのものも検索クエリ候補の末尾に残す
        if (rawPkg.isNotBlank()) {
            queries.add(rawPkg)
        }

        return queries.toList()
    }

    internal fun extractMeaningfulPackageTokens(packageName: String): List<String> {
        if (packageName.isBlank()) return emptyList()
        val rawSegments = packageName.split('.', '_', '-')
        val tokens = mutableListOf<String>()
        for (seg in rawSegments) {
            // "kindleForSamsung" のような CamelCase を分割 ("kindle", "For", "Samsung")
            val camelSplit = seg
                .replace(Regex("(?i)forsamsung"), "")
                .split(Regex("(?<=[a-z])(?=[A-Z])"))
                .map { it.trim() }
                .filter { it.isNotBlank() }

            for (part in camelSplit) {
                val lower = part.lowercase()
                if (lower.length >= 2 && lower !in GENERIC_PACKAGE_SEGMENTS) {
                    tokens.add(part)
                }
            }
        }
        return tokens.distinctBy { it.lowercase() }
    }

    private fun normalizeForComparison(text: String): String {
        return text
            .lowercase()
            .replace(Regex("(?i)forsamsung|for\\s*samsung|for\\s*galaxy"), "")
            .replace(Regex("[\\s_\\-・/()（）【】\\[\\].:]"), "")
    }

    private fun findInstalledCandidates(
        rawPkg: String,
        lowerPkg: String,
        rawLabel: String,
        mappedPlayPkg: String?,
        installedApps: List<AppInfo>
    ): List<InstalledAppMatchCandidate> {
        if (installedApps.isEmpty()) return emptyList()

        val results = mutableListOf<InstalledAppMatchCandidate>()
        val normalizedTargetLabel = normalizeForComparison(rawLabel)
        val pkgTokens = extractMeaningfulPackageTokens(rawPkg).map { it.lowercase() }
        val equivalentSpec = SAMSUNG_TO_PIXEL_EQUIVALENTS[lowerPkg]

        for (app in installedApps) {
            val appLowerPkg = app.packageName.lowercase()
            val normalizedAppLabel = normalizeForComparison(app.label)

            var bestScore = 0
            var bestReason = ""

            // 1. 正規化された Play ストアパッケージIDと完全一致 (例: com.amazon.kindleForSamsung -> com.amazon.kindle)
            if (mappedPlayPkg != null && appLowerPkg == mappedPlayPkg.lowercase()) {
                bestScore = 100
                bestReason = "Google Play版パッケージ (${app.packageName}) が端末内にインストール済み"
            }

            // 2. Galaxy 標準アプリに対応する Pixel 標準アプリ (例: Galaxyカメラ -> Pixelカメラ)
            if (bestScore < 95 && equivalentSpec != null && app.packageName in equivalentSpec.pixelPackages) {
                bestScore = 95
                bestReason = "Galaxy標準アプリに対応するPixel標準アプリ (${app.label})"
            }

            // 3. アプリ名（ラベル）の完全一致（空白・記号除去後）
            if (bestScore < 92 &&
                normalizedTargetLabel.length >= 2 &&
                normalizedTargetLabel == normalizedAppLabel
            ) {
                bestScore = 92
                bestReason = "同じアプリ名「${app.label}」が別パッケージIDでインストール済み"
            }

            // 4. アプリ名（ラベル）の部分包含一致 (例: "Kindle" と "Amazon Kindle")
            if (bestScore < 84 &&
                normalizedTargetLabel.length >= 3 &&
                normalizedAppLabel.length >= 3 &&
                (normalizedAppLabel.contains(normalizedTargetLabel) || normalizedTargetLabel.contains(normalizedAppLabel))
            ) {
                bestScore = 84
                bestReason = "類似するアプリ名「${app.label}」が端末内にインストール済み"
            }

            // 5. パッケージ内の主要トークン一致 (例: "kindle" トークンがパッケージ名またはラベルに含まれる)
            if (bestScore < 76 && pkgTokens.isNotEmpty()) {
                val matchedToken = pkgTokens.firstOrNull { token ->
                    token.length >= 4 && (appLowerPkg.contains(token) || normalizedAppLabel.contains(token))
                }
                if (matchedToken != null) {
                    bestScore = 76
                    bestReason = "キーワード「$matchedToken」に一致する端末内アプリ"
                }
            }

            if (bestScore > 0) {
                results.add(
                    InstalledAppMatchCandidate(
                        appInfo = app,
                        matchReason = bestReason,
                        score = bestScore
                    )
                )
            }
        }

        return results
            .sortedWith(compareByDescending<InstalledAppMatchCandidate> { it.score }.thenBy { it.appInfo.label })
            .distinctBy { it.appInfo.packageName }
            .take(4)
    }
}

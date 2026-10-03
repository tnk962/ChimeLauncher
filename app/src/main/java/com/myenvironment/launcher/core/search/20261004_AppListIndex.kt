package com.myenvironment.launcher.core.search

import com.myenvironment.launcher.core.model.AppInfo
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** The list and jump rail share these sections; no guessed readings for kanji. */
object AppListIndex {
    val labels = ('A'..'Z').map { it.toString() } +
        listOf("あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ", "#")

    fun section(label: String): String {
        val normalized = Normalizer.normalize(
            Normalizer.normalize(label.trim(), Normalizer.Form.NFKC), Normalizer.Form.NFD
        )
        val first = normalized.firstOrNull()?.uppercaseChar() ?: return "#"
        if (first in 'A'..'Z') return first.toString()
        val kana = if (first in 'ァ'..'ヶ') (first.code - 0x60).toChar() else first
        return when (kana) {
            in "ぁあぃいぅうぇえぉお" -> "あ"
            in "かきくけこゕゖ" -> "か"
            in "さしすせそ" -> "さ"
            in "たちっつてと" -> "た"
            in "なにぬねの" -> "な"
            in "はひふへほ" -> "は"
            in "まみむめも" -> "ま"
            in "ゃやゅゆょよ" -> "や"
            in "らりるれろ" -> "ら"
            in "ゎわゐゑをん" -> "わ"
            else -> "#"
        }
    }

    fun sorted(apps: List<AppInfo>): List<AppInfo> {
        val collator = Collator.getInstance(Locale.JAPANESE)
        return apps.distinctBy { "${it.componentKey}:${it.userSerialNumber}" }.sortedWith { a, b ->
            val group = labels.indexOf(section(a.label)).compareTo(labels.indexOf(section(b.label)))
            if (group != 0) group else {
                val name = collator.compare(a.label, b.label)
                if (name != 0) name else a.componentKey.compareTo(b.componentKey)
            }
        }
    }

    /** Absolute LazyColumn offsets include the compact suggestion rows before All Apps. */
    fun positions(apps: List<AppInfo>, prefixItems: Int): Map<String, Int> {
        val result = linkedMapOf<String, Int>()
        var offset = prefixItems
        for (app in apps) {
            val group = section(app.label)
            if (group !in result) result[group] = offset++ // section heading
            offset++ // app row
        }
        return result
    }
}

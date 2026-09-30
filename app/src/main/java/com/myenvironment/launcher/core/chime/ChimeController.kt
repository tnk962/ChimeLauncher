package com.myenvironment.launcher.core.chime

import com.myenvironment.launcher.core.model.LauncherSettings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Chime Moments の単発発火イベント (仕様 6, 8, 9, 29, 35)
 *
 * 優先順位:
 * First Chime > Return Chime > Time Chime (Ambient State)
 */
sealed interface ChimeEvent {
    data object First : ChimeEvent
    data class Return(val awayDurationMillis: Long) : ChimeEvent
}

/**
 * Time Chime の時間帯アンビエント状態 (仕様 10.2, 35)
 */
enum class TimeSegment(
    val displayName: String,
    val startHour: Int,
    val endHour: Int
) {
    MORNING(
        displayName = "Morning",
        startHour = TimeChimeProvider.MORNING_START_HOUR,
        endHour = TimeChimeProvider.MORNING_END_HOUR
    ),
    DAY(
        displayName = "Day",
        startHour = TimeChimeProvider.DAY_START_HOUR,
        endHour = TimeChimeProvider.DAY_END_HOUR
    ),
    EVENING(
        displayName = "Evening",
        startHour = TimeChimeProvider.EVENING_START_HOUR,
        endHour = TimeChimeProvider.EVENING_END_HOUR
    ),
    NIGHT(
        displayName = "Night",
        startHour = TimeChimeProvider.NIGHT_START_HOUR,
        endHour = TimeChimeProvider.NIGHT_END_HOUR
    ),
    LATE_NIGHT(
        displayName = "Late Night",
        startHour = TimeChimeProvider.LATE_NIGHT_START_HOUR,
        endHour = TimeChimeProvider.LATE_NIGHT_END_HOUR
    )
}

/**
 * First Chime 判定ロジック (仕様 8.1, 34, 38)
 *
 * - その日初めてホーム画面が表示された際に一度だけ発生する（日付単位 `yyyy-MM-dd` で管理）。
 * - 同日の2回目以降は発生せず、日付変更後に再び発生可能になる。
 */
object FirstChimeDetector {
    fun shouldTrigger(
        currentDate: String,
        lastFirstChimeDate: String,
        enabled: Boolean
    ): Boolean {
        if (!enabled) return false
        if (currentDate.isBlank()) return false
        return currentDate != lastFirstChimeDate
    }
}

/**
 * Return Chime 判定ロジック (仕様 9.1, 9.2, 30, 34, 38)
 *
 * - 一定時間以上（初期値60分、設定で30分/1時間/3時間/6時間に変更可）離れていた状態から
 *   ホーム画面へ戻ってきた際に発生する。
 * - 通常の「アプリ → Home」の数秒〜数分程度の往復では発生させない。
 */
object ReturnChimeDetector {
    fun shouldTrigger(
        nowMillis: Long,
        lastLauncherVisibleTimestamp: Long,
        thresholdMillis: Long,
        enabled: Boolean
    ): Boolean {
        if (!enabled) return false
        if (lastLauncherVisibleTimestamp <= 0L || thresholdMillis <= 0L) return false
        val elapsed = nowMillis - lastLauncherVisibleTimestamp
        return elapsed >= thresholdMillis
    }
}

/**
 * Time Chime 時間帯判定プロバイダー (仕様 10.2, 34, 38)
 *
 * - Morning    : 05:00 - 10:59
 * - Day        : 11:00 - 16:59
 * - Evening    : 17:00 - 19:59
 * - Night      : 20:00 - 23:59
 * - Late Night : 00:00 - 04:59
 * - Time Chime が OFF の場合は常に標準色 (DAY) を返す。
 */
object TimeChimeProvider {
    const val LATE_NIGHT_START_HOUR = 0
    const val LATE_NIGHT_END_HOUR = 4
    const val MORNING_START_HOUR = 5
    const val MORNING_END_HOUR = 10
    const val DAY_START_HOUR = 11
    const val DAY_END_HOUR = 16
    const val EVENING_START_HOUR = 17
    const val EVENING_END_HOUR = 19
    const val NIGHT_START_HOUR = 20
    const val NIGHT_END_HOUR = 23

    fun resolveTimeSegment(
        hourOfDay: Int,
        enabled: Boolean = true
    ): TimeSegment {
        if (!enabled) return TimeSegment.DAY
        val normalizedHour = ((hourOfDay % 24) + 24) % 24
        return when (normalizedHour) {
            in MORNING_START_HOUR..MORNING_END_HOUR -> TimeSegment.MORNING
            in DAY_START_HOUR..DAY_END_HOUR -> TimeSegment.DAY
            in EVENING_START_HOUR..EVENING_END_HOUR -> TimeSegment.EVENING
            in NIGHT_START_HOUR..NIGHT_END_HOUR -> TimeSegment.NIGHT
            else -> TimeSegment.LATE_NIGHT
        }
    }
}

/**
 * ChimeController の1回のホーム表示評価結果
 */
data class ChimeEvaluationResult(
    val event: ChimeEvent?,
    val timeSegment: TimeSegment,
    val updatedLastFirstChimeDate: String,
    val updatedLastVisibleTimestamp: Long
)

/**
 * First Chime / Return Chime / Time Chime を統合制御するコントローラー (仕様 29, 34)
 *
 * 優先度:
 * First Chime > Return Chime > Time Chime
 * First と Return が同時成立した場合（例：朝8時に8時間ぶりにその日初めてホームを開いた場合）は
 * First Chime のみを再生し、複数アニメーションを連続再生しない。
 */
class ChimeController(
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
) {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun formatDate(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zoneIdProvider())
            .toLocalDate()
            .format(dateFormatter)
    }

    fun extractHourOfDay(epochMillis: Long): Int {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zoneIdProvider())
            .hour
    }

    fun evaluateOnHomeVisible(
        nowMillis: Long,
        currentDate: String = formatDate(nowMillis),
        hourOfDay: Int = extractHourOfDay(nowMillis),
        lastFirstChimeDate: String,
        lastLauncherVisibleTimestamp: Long,
        settings: LauncherSettings
    ): ChimeEvaluationResult {
        val timeSegment = TimeChimeProvider.resolveTimeSegment(
            hourOfDay = hourOfDay,
            enabled = settings.timeChimeEnabled
        )

        val isFirstChime = FirstChimeDetector.shouldTrigger(
            currentDate = currentDate,
            lastFirstChimeDate = lastFirstChimeDate,
            enabled = settings.firstChimeEnabled
        )

        if (isFirstChime) {
            return ChimeEvaluationResult(
                event = ChimeEvent.First,
                timeSegment = timeSegment,
                updatedLastFirstChimeDate = currentDate,
                updatedLastVisibleTimestamp = nowMillis
            )
        }

        val isReturnChime = ReturnChimeDetector.shouldTrigger(
            nowMillis = nowMillis,
            lastLauncherVisibleTimestamp = lastLauncherVisibleTimestamp,
            thresholdMillis = settings.returnChimeInterval.durationMillis,
            enabled = settings.returnChimeEnabled
        )

        if (isReturnChime) {
            val awayDuration = (nowMillis - lastLauncherVisibleTimestamp).coerceAtLeast(0L)
            return ChimeEvaluationResult(
                event = ChimeEvent.Return(awayDurationMillis = awayDuration),
                timeSegment = timeSegment,
                updatedLastFirstChimeDate = lastFirstChimeDate,
                updatedLastVisibleTimestamp = nowMillis
            )
        }

        return ChimeEvaluationResult(
            event = null,
            timeSegment = timeSegment,
            updatedLastFirstChimeDate = lastFirstChimeDate,
            updatedLastVisibleTimestamp = nowMillis
        )
    }
}

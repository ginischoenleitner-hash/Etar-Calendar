/*
 * Menstrual cycle phases shown as pastel day backgrounds.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.android.calendar.cycle

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.annotation.StringRes
import com.android.calendar.settings.GeneralPreferences
import com.android.calendar.theme.isSystemInDarkTheme
import com.android.calendar.widget.CalendarAppWidgetProvider
import ws.xsoh.etar.R
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A boundary of a cycle phase, expressed in days (never weeks).
 * Cycle days are 1-based: day 1 is the first day of the period, the last cycle day is the day
 * before the next period starts.
 */
sealed class CycleDay {
    /** The n-th day of the cycle, counted from the first day of the period (1 = first day). */
    data class FromStart(val day: Int) : CycleDay()

    /**
     * Counted back from the end of the cycle, i.e. from the first day of the next period:
     * 1 = the last cycle day, 7 = seven days before the next period starts.
     */
    data class BeforeCycleEnd(val days: Int) : CycleDay()

    fun resolve(cycleLength: Int): Int = when (this) {
        is FromStart -> day
        is BeforeCycleEnd -> cycleLength + 1 - days
    }.coerceIn(1, cycleLength)
}

data class CyclePhase(
    val id: String,
    @StringRes val nameRes: Int,
    val start: CycleDay,
    val end: CycleDay,
    val defaultColor: Int
)

data class CycleDayInfo(
    /** 1-based day within the current cycle */
    val cycleDay: Int,
    /** length of the cycle this day belongs to (measured for completed cycles, configured for predicted ones) */
    val cycleLength: Int,
    /** null if no phase covers this cycle day */
    val phase: CyclePhase?,
    /** true if this day is a recorded first day of a period */
    val isRecordedStart: Boolean
)

object CycleHelper {
    const val KEY_ENABLED = "cycle_tracking_enabled"
    const val KEY_LENGTH = "cycle_length"
    const val KEY_PERIOD_STARTS = "cycle_period_starts"
    const val KEY_COLOR_PREFIX = "cycle_phase_color_"
    const val KEY_PREFIX = "cycle_"

    const val DEFAULT_LENGTH = 28
    const val MIN_LENGTH = 15
    const val MAX_LENGTH = 60

    /** Julian day number of 1970-01-01, the epoch day 0 of [LocalDate]. */
    private const val EPOCH_JULIAN_DAY = 2440588

    /**
     * The cycle phases, in order. The first phase whose range contains a cycle day wins,
     * so for very short cycles earlier phases take precedence.
     *
     * Example for a 28 day cycle:
     *  - Menstruation:   days 1–6
     *  - Follikelphase:  days 7–9   (from day 7 until the ovulation window starts)
     *  - Eisprung:       days 10–16 (19 to 13 days before the cycle end)
     *  - Lutealphase:    days 17–21 (12 to 8 days before the cycle end)
     *  - PMS-Phase:      days 22–28 (the last 7 days)
     */
    @JvmField
    val PHASES: List<CyclePhase> = listOf(
        CyclePhase(
            id = "menstruation",
            nameRes = R.string.cycle_phase_menstruation,
            start = CycleDay.FromStart(1),
            end = CycleDay.FromStart(6),
            defaultColor = 0xFFF7B7B7.toInt()   // light red
        ),
        CyclePhase(
            id = "follicular",
            nameRes = R.string.cycle_phase_follicular,
            start = CycleDay.FromStart(7),
            end = CycleDay.BeforeCycleEnd(20),
            defaultColor = 0xFFC6E8C0.toInt()   // light green
        ),
        CyclePhase(
            id = "ovulation",
            nameRes = R.string.cycle_phase_ovulation,
            start = CycleDay.BeforeCycleEnd(19),
            end = CycleDay.BeforeCycleEnd(13),
            defaultColor = 0xFFDCCCF2.toInt()   // light purple
        ),
        CyclePhase(
            id = "luteal",
            nameRes = R.string.cycle_phase_luteal,
            start = CycleDay.BeforeCycleEnd(12),
            end = CycleDay.BeforeCycleEnd(8),
            defaultColor = 0xFFBFDDF5.toInt()   // light blue
        ),
        CyclePhase(
            id = "pms",
            nameRes = R.string.cycle_phase_pms,
            start = CycleDay.BeforeCycleEnd(7),
            end = CycleDay.BeforeCycleEnd(1),
            defaultColor = 0xFFFFD4A8.toInt()   // light orange
        )
    )

    /** Pastel colors offered in the phase color picker. */
    @JvmField
    val PASTEL_PALETTE: IntArray = intArrayOf(
        0xFFF7B7B7.toInt(), 0xFFF9C6D3.toInt(), 0xFFFFD4A8.toInt(), 0xFFFFE9A8.toInt(),
        0xFFC6E8C0.toInt(), 0xFFB8E6D8.toInt(), 0xFFBFDDF5.toInt(), 0xFFC9D3F5.toInt(),
        0xFFDCCCF2.toInt(), 0xFFEBC8EE.toInt(), 0xFFE3D5C8.toInt(), 0xFFD9D9D9.toInt()
    )

    // ---------------------------------------------------------------------------------------
    // Settings

    @JvmStatic
    fun prefs(context: Context): SharedPreferences = GeneralPreferences.getSharedPreferences(context)

    @JvmStatic
    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)

    @JvmStatic
    fun getCycleLength(context: Context): Int =
        (prefs(context).getString(KEY_LENGTH, null)?.toIntOrNull() ?: DEFAULT_LENGTH)
            .coerceIn(MIN_LENGTH, MAX_LENGTH)

    /** Every recorded first day of a period, sorted ascending. */
    @JvmStatic
    fun getPeriodStarts(context: Context): List<LocalDate> =
        parseStarts(prefs(context).getString(KEY_PERIOD_STARTS, "") ?: "")

    private fun parseStarts(raw: String): List<LocalDate> = raw.split(",")
        .mapNotNull { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
        .distinct()
        .sorted()

    private fun setPeriodStarts(context: Context, starts: Collection<LocalDate>) {
        prefs(context).edit()
            .putString(KEY_PERIOD_STARTS, starts.distinct().sorted().joinToString(","))
            .apply()
        refreshWidgets(context)
    }

    @JvmStatic
    fun isPeriodStart(context: Context, date: LocalDate) = getPeriodStarts(context).contains(date)

    /** Adds the date as a period start, or removes it if it already is one. Returns true if it is now a start. */
    @JvmStatic
    fun togglePeriodStart(context: Context, date: LocalDate): Boolean {
        val starts = getPeriodStarts(context).toMutableList()
        val isNowStart = if (starts.remove(date)) false else starts.add(date)
        setPeriodStarts(context, starts)
        return isNowStart
    }

    @JvmStatic
    fun addPeriodStart(context: Context, date: LocalDate) {
        setPeriodStarts(context, getPeriodStarts(context) + date)
    }

    /** Redraws the agenda widget after the cycle data changed. */
    @JvmStatic
    fun refreshWidgets(context: Context) {
        runCatching {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, CalendarAppWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                manager.notifyAppWidgetViewDataChanged(ids, R.id.events_list)
            }
        }
    }

    @JvmStatic
    fun getPhaseColor(context: Context, phase: CyclePhase): Int =
        prefs(context).getInt(KEY_COLOR_PREFIX + phase.id, phase.defaultColor)

    @JvmStatic
    fun setPhaseColor(context: Context, phase: CyclePhase, color: Int) {
        prefs(context).edit().putInt(KEY_COLOR_PREFIX + phase.id, color).apply()
        refreshWidgets(context)
    }

    // ---------------------------------------------------------------------------------------
    // Calculation

    @JvmStatic
    fun julianDayToDate(julianDay: Int): LocalDate = LocalDate.ofEpochDay((julianDay - EPOCH_JULIAN_DAY).toLong())

    @JvmStatic
    fun dateToJulianDay(date: LocalDate): Int = (date.toEpochDay() + EPOCH_JULIAN_DAY).toInt()

    @JvmStatic
    fun getPhaseForCycleDay(cycleDay: Int, cycleLength: Int): CyclePhase? =
        PHASES.firstOrNull { cycleDay in it.start.resolve(cycleLength)..it.end.resolve(cycleLength) }

    private var cachedSignature: String? = null
    private var cachedCalculator: CycleCalculator? = null

    /**
     * The calculator for the current settings, or null when cycle tracking is off or no period
     * start is recorded. Cached, because the views call this while drawing.
     */
    @JvmStatic
    @Synchronized
    fun getCalculator(context: Context): CycleCalculator? {
        val signature = getSignature(context)
        if (signature != cachedSignature) {
            cachedSignature = signature
            cachedCalculator = createCalculator(context)
        }
        return cachedCalculator
    }

    private fun createCalculator(context: Context): CycleCalculator? {
        if (!isEnabled(context)) {
            return null
        }

        val starts = getPeriodStarts(context)
        if (starts.isEmpty()) {
            return null
        }

        val colors = PHASES.associate { it.id to getPhaseColor(context, it) }
        return CycleCalculator(starts, getCycleLength(context), colors)
    }

    /** Changes whenever anything that affects the cycle coloring changes. */
    @JvmStatic
    fun getSignature(context: Context): String {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_ENABLED, false)) {
            return ""
        }

        val colors = PHASES.joinToString(",") { prefs.getInt(KEY_COLOR_PREFIX + it.id, it.defaultColor).toString() }
        return "${prefs.getString(KEY_LENGTH, "")}|${prefs.getString(KEY_PERIOD_STARTS, "")}|$colors"
    }

    /** Convenience for the Java views: the background color for a julian day, or 0 if it has none. */
    @JvmStatic
    fun getDayBackgroundColor(context: Context, julianDay: Int): Int {
        val phaseColor = getCalculator(context)?.getPhaseColor(julianDayToDate(julianDay)) ?: return 0
        return getDisplayColor(context, phaseColor)
    }

    /**
     * Pastel colors are used almost as-is in the light theme; in the dark theme they are
     * toned down so that light text stays readable.
     */
    @JvmStatic
    fun getDisplayColor(context: Context, phaseColor: Int): Int {
        val alpha = if (isSystemInDarkTheme(context)) 0.30f else 0.85f
        return withAlpha(phaseColor, alpha)
    }

    @JvmStatic
    fun withAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}

class CycleCalculator(
    private val starts: List<LocalDate>,
    private val configuredLength: Int,
    private val phaseColors: Map<String, Int>
) {
    fun getInfo(date: LocalDate): CycleDayInfo? {
        // nothing is shown before the first recorded period start
        val index = starts.indexOfLast { !it.isAfter(date) }
        if (index == -1) {
            return null
        }

        val start = starts[index]
        val next = starts.getOrNull(index + 1)
        val daysSinceStart = ChronoUnit.DAYS.between(start, date).toInt()

        if (next != null) {
            val measuredLength = ChronoUnit.DAYS.between(start, next).toInt()
            // a very long gap most likely means some period starts were not entered,
            // so fall back to repeating the configured cycle length
            if (measuredLength <= configuredLength * 2) {
                val cycleDay = daysSinceStart + 1
                return CycleDayInfo(
                    cycleDay = cycleDay,
                    cycleLength = measuredLength,
                    phase = CycleHelper.getPhaseForCycleDay(cycleDay, measuredLength),
                    isRecordedStart = daysSinceStart == 0
                )
            }
        }

        val cycleDay = daysSinceStart % configuredLength + 1
        return CycleDayInfo(
            cycleDay = cycleDay,
            cycleLength = configuredLength,
            phase = CycleHelper.getPhaseForCycleDay(cycleDay, configuredLength),
            isRecordedStart = daysSinceStart == 0
        )
    }

    /** The phase color for the given day, or null if the day has no phase. */
    fun getPhaseColor(date: LocalDate): Int? {
        val phase = getInfo(date)?.phase ?: return null
        return phaseColors[phase.id] ?: phase.defaultColor
    }
}

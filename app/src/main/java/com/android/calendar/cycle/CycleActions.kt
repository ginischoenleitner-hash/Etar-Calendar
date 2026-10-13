/*
 * Menstrual cycle phases shown as pastel day backgrounds.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.android.calendar.cycle

import android.content.Context
import android.text.format.DateUtils
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ws.xsoh.etar.R
import java.time.ZoneId

object CycleActions {

    /**
     * Called when a day is long pressed in the month, week or day view.
     * Without cycle tracking the original action runs directly (creating a new event).
     * With cycle tracking enabled a small menu additionally offers to mark / unmark the day
     * as the first day of a period.
     */
    @JvmStatic
    fun showLongPressActions(context: Context, julianDay: Int, title: CharSequence?, newEventAction: Runnable) {
        if (!CycleHelper.isEnabled(context)) {
            newEventAction.run()
            return
        }

        val date = CycleHelper.julianDayToDate(julianDay)
        val isStart = CycleHelper.isPeriodStart(context, date)
        val items = arrayOf<CharSequence>(
            context.getString(R.string.new_event_dialog_option),
            context.getString(if (isStart) R.string.cycle_remove_period_start else R.string.cycle_mark_period_start)
        )

        val dialogTitle = title ?: formatDate(context, julianDay)
        MaterialAlertDialogBuilder(context)
            .setTitle(dialogTitle)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> newEventAction.run()
                    1 -> {
                        val isNowStart = CycleHelper.togglePeriodStart(context, date)
                        Toast.makeText(
                            context,
                            if (isNowStart) R.string.cycle_period_start_added else R.string.cycle_period_start_removed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .show()
            .setCanceledOnTouchOutside(true)
    }

    @JvmStatic
    fun formatDate(context: Context, julianDay: Int): String {
        val millis = CycleHelper.julianDayToDate(julianDay)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_YEAR
        return DateUtils.formatDateTime(context, millis, flags)
    }

    /** Text such as "Zyklustag 9 · Follikelphase" for a day, or null if it is not inside a cycle. */
    @JvmStatic
    fun getCycleDayLabel(context: Context, julianDay: Int): String? {
        val info = CycleHelper.getCalculator(context)?.getInfo(CycleHelper.julianDayToDate(julianDay)) ?: return null
        val cycleDay = context.getString(R.string.cycle_day_label, info.cycleDay)
        val phase = info.phase?.let { " · " + context.getString(it.nameRes) } ?: ""
        return cycleDay + phase
    }
}

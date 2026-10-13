/*
 * Menstrual cycle phases shown as pastel day backgrounds.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.android.calendar.cycle

import android.app.DatePickerDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.android.calendar.Utils
import com.android.calendar.colorpicker.ColorPickerDialog
import com.android.calendar.settings.GeneralPreferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ws.xsoh.etar.R
import java.time.LocalDate

/** Settings screen for the cycle feature: on/off, cycle length, period starts and phase colors. */
class CyclePreferences : PreferenceFragmentCompat() {

    private lateinit var enabledPref: SwitchPreference
    private lateinit var lengthPref: ListPreference
    private lateinit var startsPref: Preference
    private lateinit var colorsCategory: PreferenceCategory

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = GeneralPreferences.SHARED_PREFS_NAME
        setPreferencesFromResource(R.xml.cycle_preferences, rootKey)

        enabledPref = findPreference(CycleHelper.KEY_ENABLED)!!
        lengthPref = findPreference(CycleHelper.KEY_LENGTH)!!
        startsPref = findPreference(KEY_STARTS_SCREEN)!!
        colorsCategory = findPreference(KEY_COLORS_CATEGORY)!!

        val lengths = (CycleHelper.MIN_LENGTH..CycleHelper.MAX_LENGTH).toList()
        lengthPref.entries = lengths.map { resources.getQuantityString(R.plurals.cycle_days, it, it) }.toTypedArray()
        lengthPref.entryValues = lengths.map { it.toString() }.toTypedArray()
        if (lengthPref.value == null) {
            lengthPref.value = CycleHelper.DEFAULT_LENGTH.toString()
        }
        lengthPref.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()

        enabledPref.setOnPreferenceChangeListener { _, newValue ->
            updateVisibility(newValue as Boolean)
            true
        }

        startsPref.setOnPreferenceClickListener {
            showPeriodStartsDialog()
            true
        }

        CycleHelper.PHASES.forEach { phase ->
            val pref = Preference(requireContext()).apply {
                key = "cycle_phase_color_pref_" + phase.id
                setTitle(phase.nameRes)
                isPersistent = false
                isIconSpaceReserved = true
                setOnPreferenceClickListener {
                    showColorPicker(phase, this)
                    true
                }
            }
            updateColorIcon(pref, CycleHelper.getPhaseColor(requireContext(), phase))
            colorsCategory.addPreference(pref)
        }

        updateStartsSummary()
        updateVisibility(enabledPref.isChecked)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activity?.title = getString(R.string.cycle)
    }

    private fun updateVisibility(enabled: Boolean) {
        lengthPref.isVisible = enabled
        startsPref.isVisible = enabled
        colorsCategory.isVisible = enabled
    }

    private fun updateStartsSummary() {
        val starts = CycleHelper.getPeriodStarts(requireContext())
        startsPref.summary = if (starts.isEmpty()) {
            getString(R.string.cycle_no_period_starts)
        } else {
            // the most recent entry and how many there are
            "${CycleActions.formatDate(requireContext(), CycleHelper.dateToJulianDay(starts.last()))} (${starts.size})"
        }
    }

    private fun updateColorIcon(pref: Preference, color: Int) {
        val size = resources.getDimensionPixelSize(R.dimen.cycle_color_swatch_size)
        pref.icon = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setSize(size, size)
        }
    }

    private fun showColorPicker(phase: CyclePhase, pref: Preference) {
        val dialog = ColorPickerDialog.newInstance(
            phase.nameRes,
            CycleHelper.PASTEL_PALETTE,
            CycleHelper.getPhaseColor(requireContext(), phase),
            4,
            ColorPickerDialog.SIZE_SMALL
        )
        dialog.setOnColorSelectedListener { color ->
            CycleHelper.setPhaseColor(requireContext(), phase, color)
            updateColorIcon(pref, color)
        }
        dialog.show(parentFragmentManager, "cycle_color_picker")
    }

    /** Lists the recorded period starts (newest first); tapping one removes it, the neutral button adds one. */
    private fun showPeriodStartsDialog() {
        val context = requireContext()
        val starts = CycleHelper.getPeriodStarts(context).sortedDescending()
        val labels = starts.map { CycleActions.formatDate(context, CycleHelper.dateToJulianDay(it)) }.toTypedArray<CharSequence>()

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.cycle_period_starts)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.cycle_add_period_start) { _, _ -> pickNewStart() }

        if (starts.isEmpty()) {
            builder.setMessage(R.string.cycle_no_period_starts)
        } else {
            builder.setItems(labels) { _, which -> confirmRemoval(starts[which], labels[which]) }
        }
        builder.show()
    }

    private fun confirmRemoval(date: LocalDate, label: CharSequence) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cycle_remove_period_start)
            .setMessage(label)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                CycleHelper.togglePeriodStart(requireContext(), date)
                updateStartsSummary()
                showPeriodStartsDialog()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> showPeriodStartsDialog() }
            .show()
    }

    private fun pickNewStart() {
        val today = LocalDate.now()
        val picker = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                CycleHelper.addPeriodStart(requireContext(), LocalDate.of(year, month + 1, dayOfMonth))
                updateStartsSummary()
                showPeriodStartsDialog()
            },
            today.year,
            today.monthValue - 1,
            today.dayOfMonth
        )
        picker.datePicker.firstDayOfWeek = Utils.getFirstDayOfWeekAsCalendar(requireContext())
        picker.show()
    }

    companion object {
        private const val KEY_STARTS_SCREEN = "cycle_period_starts_screen"
        private const val KEY_COLORS_CATEGORY = "cycle_phase_colors_category"
    }
}

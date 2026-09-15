/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.preference.DropDownPreference
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.SettingsSpinnerAdapter
import com.android.settingslib.widget.theme.R

/** A [DropDownPreference] whose menu is drawn like the Settings spinners. */
class HubDropDownPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : DropDownPreference(context, attrs) {
    override fun createAdapter(): ArrayAdapter<*> = SettingsSpinnerAdapter<CharSequence>(context)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val spinner = holder.findViewById(R.id.spinner) as? Spinner
        (spinner?.adapter as? SettingsSpinnerAdapter<*>)?.setSelectedPosition(
            findIndexOfValue(value)
        )
    }
}

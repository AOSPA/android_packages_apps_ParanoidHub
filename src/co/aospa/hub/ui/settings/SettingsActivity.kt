/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.ui.settings

import android.os.Bundle
import co.aospa.hub.R
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.collapsingtoolbar.R as CollapsingToolbarR

/** Hosts [SettingsFragment] under the Settings collapsing toolbar. */
class SettingsActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.settings_title)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(CollapsingToolbarR.id.content_frame, SettingsFragment())
                .commit()
        }
    }

    override fun onNavigateUp(): Boolean {
        finish()
        return true
    }
}

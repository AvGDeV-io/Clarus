/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings

import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import org.mozilla.fenix.R

/**
 * Preserves the stock Android / Material switch style strictly for debug/developer settings,
 * ensuring secret settings retain the original stock toggle design while user-facing settings
 * use custom Clarus specular glass toggles.
 */
fun PreferenceGroup.preserveStockSwitchStyle() {
    for (i in 0 until preferenceCount) {
        val pref = getPreference(i)
        if (pref is SwitchPreferenceCompat) {
            pref.widgetLayoutResource = R.layout.preference_material_switch_stock
        }
        if (pref is PreferenceGroup) {
            pref.preserveStockSwitchStyle()
        }
    }
}

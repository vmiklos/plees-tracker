package hu.vmiklos.plees_tracker

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class Preferences : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}

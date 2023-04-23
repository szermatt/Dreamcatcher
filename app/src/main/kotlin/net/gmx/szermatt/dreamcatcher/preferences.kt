package net.gmx.szermatt.dreamcatcher

import android.os.Bundle
import android.util.Log
import androidx.leanback.preference.LeanbackPreferenceFragment
import net.gmx.szermatt.dreamcatcher.DreamCatcherApplication.Companion.TAG

class DreamCatcherPreferenceFragment : LeanbackPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(TAG, "Created prefs with root key $rootKey")
        setPreferencesFromResource(R.xml.preference, rootKey)
    }
}


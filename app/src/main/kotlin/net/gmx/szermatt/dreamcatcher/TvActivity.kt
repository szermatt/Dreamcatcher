package net.gmx.szermatt.dreamcatcher

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import net.gmx.szermatt.dreamcatcher.DreamCatcherApplication.Companion.TAG

/**
 * The main activity, containing the settings.
 */
class TvActivity : Activity() {
    private var mPreferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = DreamCatcherPreferenceFragment()
        fragmentManager.beginTransaction().replace(R.id.preferences, prefs).commit()
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "started")
        val prefs = getDefaultSharedPreferences(this)
        val intent = serviceIntent(this)
        if (DreamCatcherService.isEnabled(prefs)) {
            startForegroundService(intent)
        } else {
            mPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                if (key == "enabled" && DreamCatcherService.isEnabled(prefs)) {
                    startForegroundService(intent)
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(mPreferenceListener)
        }
    }

    override fun onDestroy() {
        if (mPreferenceListener != null) {
            val prefs = getDefaultSharedPreferences(this)
            prefs.unregisterOnSharedPreferenceChangeListener(mPreferenceListener)
            mPreferenceListener = null
        }
        super.onDestroy()
    }
}
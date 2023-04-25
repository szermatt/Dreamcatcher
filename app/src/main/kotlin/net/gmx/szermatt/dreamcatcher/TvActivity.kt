package net.gmx.szermatt.dreamcatcher

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
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
        val prefs = DreamCatcherPreferenceManager(this)
        val intent = serviceIntent(this)
        mPreferenceListener = prefs.onEnabled {
            startForegroundService(intent)
        }
    }

    override fun onDestroy() {
        DreamCatcherPreferenceManager(this).unregister(mPreferenceListener)
        mPreferenceListener = null

        super.onDestroy()
    }
}
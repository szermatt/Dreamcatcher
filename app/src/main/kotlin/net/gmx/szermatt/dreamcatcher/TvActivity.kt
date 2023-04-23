package net.gmx.szermatt.dreamcatcher

import android.app.Activity
import android.os.Bundle
import android.util.Log
import net.gmx.szermatt.dreamcatcher.DreamCatcherApplication.Companion.TAG

/**
 * The main activity, containing the settings.
 */
class TvActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = DreamCatcherPreferenceFragment()
        fragmentManager.beginTransaction().replace(R.id.preferences, prefs).commit()
    }

    override fun onStart() {
        super.onStart()
        startForegroundService(serviceIntent(this))
        Log.i(TAG, "started")
    }
}
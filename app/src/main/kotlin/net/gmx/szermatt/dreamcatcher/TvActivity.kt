package net.gmx.szermatt.dreamcatcher

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import net.gmx.szermatt.dreamcatcher.DreamCatcherApplication.Companion.TAG

/**
 * The main activity, containing the settings.
 */
class TvActivity : FragmentActivity() {
    private var mPreferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportFragmentManager.beginTransaction().replace(
            R.id.preferences,
            DreamCatcherPreferenceFragment()
        ).commit()
    }

    override fun onStart() {
        super.onStart()

        Log.i(TAG, "started")
        val prefs = DreamCatcherPreferenceManager(this)
        mPreferenceListener = prefs.onEnabled {
            startDreamCatcherService(this)
        }
    }

    override fun onDestroy() {
        val prefs = DreamCatcherPreferenceManager(this)
        if (prefs.enabled) {
            // This should restart the service in case it's killed with the
            // rest of the app.
            startDreamCatcherService(this)
        }

        prefs.unregister(mPreferenceListener)
        mPreferenceListener = null

        super.onDestroy()
    }
}
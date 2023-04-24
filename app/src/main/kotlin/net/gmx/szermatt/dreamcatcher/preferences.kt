package net.gmx.szermatt.dreamcatcher

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.leanback.preference.LeanbackPreferenceFragment
import androidx.preference.Preference
import androidx.work.WorkManager


class DreamCatcherPreferenceFragment : LeanbackPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference, rootKey)

        val prefs = preferenceManager.sharedPreferences!!

        // TODO: make enabled actually do something
        val enabled: Preference = preferenceManager.findPreference("enabled")!!
        enabled.setSummaryProvider {
            getString(
                R.string.preference_enabled_summary,
                getHostportOrDefault(prefs, context), getDelayOrDefault(prefs)
            )
        }
        val delay: Preference = preferenceManager.findPreference("delay")!!
        delay.setSummaryProvider {
            getString(R.string.preference_delay_summary, getDelayOrDefault(prefs))
        }
        val hostport: Preference = preferenceManager.findPreference("hostport")!!
        hostport.setSummaryProvider {
            getString(R.string.preference_hostport_summary, getHostportOrDefault(prefs, context))
        }
        val test: Preference = preferenceManager.findPreference("test")!!
        test.setSummaryProvider {
            getString(R.string.preference_test_summary, getHostportOrDefault(prefs, context))
        }
        test.setOnPreferenceClickListener {
            Toast.makeText(context, "Testing connection...", Toast.LENGTH_LONG).show()
            val op = WorkManager.getInstance(context).enqueue(
                PowerOffWorker.workRequest(dryRun = true)
            )
            val result = op.result
            result.addListener({
                // TODO: have the preference show the result (success, failure, unknown)
                try {
                    result.get()
                    Toast.makeText(context, "Connection OK", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Connection failed!", Toast.LENGTH_LONG).show()
                }
            }, context.mainExecutor)
            true
        }
        val powerOff: Preference = preferenceManager.findPreference("powerOff")!!
        powerOff.setSummaryProvider {
            getString(R.string.preference_poweroff_summary, getHostportOrDefault(prefs, context))
        }
        powerOff.setOnPreferenceClickListener {
            Toast.makeText(context, "Powering off...", Toast.LENGTH_LONG).show()
            val op = WorkManager.getInstance(context).enqueue(PowerOffWorker.workRequest())
            val result = op.result
            result.addListener({
                try {
                    result.get()
                } catch (e: Exception) {
                    Toast.makeText(context, "Power off failed!", Toast.LENGTH_LONG).show()
                }
            }, context.mainExecutor)
            true
        }
    }

    /** Returns the value of `delay` or a default value, if still unset. */
    private fun getDelayOrDefault(prefs: SharedPreferences) = prefs.getInt("delay", 10)

    /** Returns the value of `hostport` or a default value, suitable for display, if none is set. */
    private fun getHostportOrDefault(prefs: SharedPreferences, ctx: Context): String {
        return prefs.getString("hostport", ctx.getString(R.string.harmony_hub))!!
    }
}


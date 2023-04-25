package net.gmx.szermatt.dreamcatcher

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.IntDef
import androidx.leanback.app.ProgressBarManager
import androidx.leanback.preference.LeanbackPreferenceFragment
import androidx.preference.Preference
import androidx.work.WorkManager


class DreamCatcherPreferenceFragment : LeanbackPreferenceFragment() {
    companion object {

        @IntDef(TEST_RESULT_UNKNOWN, TEST_RESULT_OK, TEST_RESULT_FAIL)
        @Retention(AnnotationRetention.SOURCE)
        annotation class TestResult

        const val TEST_RESULT_UNKNOWN = 0
        const val TEST_RESULT_OK = 1
        const val TEST_RESULT_FAIL = 2
    }

    val mProgressManager = ProgressBarManager()

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
        hostport.setOnPreferenceChangeListener { _, newValue ->
            if (newValue != prefs.getString("hostport", "")) {
                // Changing the address invalidates the test result.
                with(prefs.edit()) {
                    putInt("test", TEST_RESULT_UNKNOWN)
                    commit()
                }
            }
            true
        }
        val test: Preference = preferenceManager.findPreference("test")!!
        test.setSummaryProvider {
            val id = when (prefs.getInt("test", TEST_RESULT_UNKNOWN)) {
                TEST_RESULT_OK -> R.string.preference_test_ok_summary
                TEST_RESULT_FAIL -> R.string.preference_test_fail_summary
                else -> R.string.preference_test_summary
            }
            getString(id, getHostportOrDefault(prefs, context))
        }
        test.setOnPreferenceClickListener {
            Toast.makeText(context, "Testing connection...", Toast.LENGTH_LONG).show()
            mProgressManager.enableProgressBar()
            val op = WorkManager.getInstance(context).enqueue(
                PowerOffWorker.workRequest(dryRun = true)
            )
            val result = op.result
            result.addListener({
                mProgressManager.disableProgressBar()
                with(prefs.edit()) {
                    putInt("test", TEST_RESULT_UNKNOWN)
                    commit()
                }
                try {
                    result.get()
                    with(prefs.edit()) {
                        putInt("test", TEST_RESULT_OK)
                        commit()
                    }
                    Toast.makeText(context, "Connection OK", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    with(prefs.edit()) {
                        putInt("test", TEST_RESULT_FAIL)
                        commit()
                    }
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
            mProgressManager.enableProgressBar()
            val op = WorkManager.getInstance(context).enqueue(PowerOffWorker.workRequest())
            val result = op.result
            result.addListener({
                mProgressManager.disableProgressBar()
                try {
                    result.get()
                } catch (e: Exception) {
                    Toast.makeText(context, "Power off failed!", Toast.LENGTH_LONG).show()
                }
            }, context.mainExecutor)
            true
        }
    }

    override fun onStart() {
        super.onStart()

        mProgressManager.setRootView(activity.findViewById(R.id.main))
    }

    /** Returns the value of `delay` or a default value, if still unset. */
    private fun getDelayOrDefault(prefs: SharedPreferences) = prefs.getInt("delay", 10)

    /** Returns the value of `hostport` or a default value, suitable for display, if none is set. */
    private fun getHostportOrDefault(prefs: SharedPreferences, ctx: Context): String {
        return prefs.getString("hostport", ctx.getString(R.string.harmony_hub))!!
    }
}


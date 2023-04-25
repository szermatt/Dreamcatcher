package net.gmx.szermatt.dreamcatcher

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.IntDef
import androidx.leanback.app.ProgressBarManager
import androidx.leanback.preference.LeanbackPreferenceFragment
import androidx.preference.Preference
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import androidx.work.WorkManager
import net.gmx.szermatt.dreamcatcher.DreamCatcherApplication.Companion.TAG
import net.gmx.szermatt.dreamcatcher.TestResult.Companion.TEST_RESULT_FAIL
import net.gmx.szermatt.dreamcatcher.TestResult.Companion.TEST_RESULT_OK
import net.gmx.szermatt.dreamcatcher.TestResult.Companion.TEST_RESULT_UNKNOWN
import java.lang.Boolean.parseBoolean
import java.lang.Integer.parseInt

/**
 * Helper for accessing and changing preferences used by this app.
 */
class DreamCatcherPreferenceFragment : LeanbackPreferenceFragment() {
    val mProgressManager = ProgressBarManager()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference, rootKey)

        val prefs = DreamCatcherPreferenceManager(context)

        val enabled: Preference =
            preferenceManager.findPreference(DreamCatcherPreferenceManager.ENABLED_KEY)!!
        enabled.setSummaryProvider {
            getString(
                R.string.preference_enabled_summary, prefs.getHostportForDisplay(), prefs.delay
            )
        }
        val delay: Preference =
            preferenceManager.findPreference(DreamCatcherPreferenceManager.DELAY_KEY)!!
        delay.setSummaryProvider {
            getString(R.string.preference_delay_summary, prefs.delay)
        }
        val hostport: Preference =
            preferenceManager.findPreference(DreamCatcherPreferenceManager.HOSTPORT_KEY)!!
        hostport.setSummaryProvider {
            getString(R.string.preference_hostport_summary, prefs.getHostportForDisplay())
        }
        hostport.setOnPreferenceChangeListener { _, newValue ->
            if (newValue != prefs.hostport) {
                // Changing the address invalidates the test result.
                prefs.test = TEST_RESULT_UNKNOWN
            }
            true
        }
        val test: Preference = preferenceManager.findPreference("test")!!
        test.setSummaryProvider {
            val id = when (prefs.test) {
                TEST_RESULT_OK -> R.string.preference_test_ok_summary
                TEST_RESULT_FAIL -> R.string.preference_test_fail_summary
                else -> R.string.preference_test_summary
            }
            getString(id, prefs.getHostportForDisplay())
        }
        test.setOnPreferenceClickListener {
            Toast.makeText(context, "Testing connection...", Toast.LENGTH_LONG).show()
            mProgressManager.enableProgressBar()
            val op = WorkManager.getInstance(context).enqueue(
                PowerOffWorker.workRequest(prefs.hostport, dryRun = true)
            )
            val result = op.result
            result.addListener({
                mProgressManager.disableProgressBar()
                prefs.test = TEST_RESULT_UNKNOWN
                try {
                    result.get()
                    prefs.test = TEST_RESULT_OK
                    Toast.makeText(context, "Connection OK", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    prefs.test = TEST_RESULT_FAIL
                    Toast.makeText(context, "Connection failed!", Toast.LENGTH_LONG).show()
                }
            }, context.mainExecutor)
            true
        }
        val powerOff: Preference = preferenceManager.findPreference("powerOff")!!
        powerOff.setSummaryProvider {
            getString(R.string.preference_poweroff_summary, prefs.getHostportForDisplay())
        }
        powerOff.setOnPreferenceClickListener {
            Toast.makeText(context, "Powering off...", Toast.LENGTH_LONG).show()
            mProgressManager.enableProgressBar()
            val op =
                WorkManager.getInstance(context).enqueue(PowerOffWorker.workRequest(prefs.hostport))
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
    private fun getDelayOrDefault(prefs: SharedPreferences): Int {
        try {
            return prefs.getInt("delay", 10)
        } catch (e: ClassCastException) {
            val v = prefs.getString("delay", "10")
            Log.e(TAG, "content of delay preference ($v) is not an int", e)
            return parseInt(v)
        }
    }

    /** Returns the value of `hostport` or a default value, suitable for display, if none is set. */
    private fun getHostportOrDefault(prefs: SharedPreferences, ctx: Context): String {
        return prefs.getString("hostport", ctx.getString(R.string.harmony_hub))!!
    }
}

@IntDef(TEST_RESULT_UNKNOWN, TEST_RESULT_OK, TEST_RESULT_FAIL)
@Retention(AnnotationRetention.SOURCE)
annotation class TestResult {
    companion object {
        const val TEST_RESULT_UNKNOWN = 0
        const val TEST_RESULT_OK = 1
        const val TEST_RESULT_FAIL = 2
    }
}

internal class DreamCatcherPreferenceManager(private val context: Context) {
    companion object {
        const val DELAY_KEY = "delay"
        const val ENABLED_KEY = "enabled"
        const val TEST_KEY = "test"
        const val HOSTPORT_KEY = "hostport"
    }

    private val prefs: SharedPreferences = getDefaultSharedPreferences(context)

    val enabled: Boolean
        get() = safeGetBoolean(ENABLED_KEY, context.resources.getBoolean(R.bool.enabled_default))

    val delay: Int
        get() = safeGetInt(DELAY_KEY, context.resources.getInteger(R.integer.delay_default))

    val hostport: String
        get() = prefs.getString(HOSTPORT_KEY, "")!!

    @TestResult
    var test: Int
        get() = safeGetInt(TEST_KEY, TEST_RESULT_UNKNOWN)
        set(v: Int) = with(prefs.edit()) {
            putInt(TEST_KEY, v)
            commit()
        }

    /** Returns the value of `hostport` or a default value, suitable for display, if none is set. */
    fun getHostportForDisplay(): String {
        return prefs.getString(HOSTPORT_KEY, context.getString(R.string.harmony_hub))!!
    }

    /**
     * Executes [lambda] once `enabled` becomes true.
     *
     * If it already true, executes [lambda] immediately and returns null.
     *
     * @return a listener to pass to [unregister]
     */
    fun onEnabled(lambda: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener? {
        if (enabled) {
            lambda()
            return null
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == DreamCatcherPreferenceManager.ENABLED_KEY && enabled) {
                lambda()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    /**
     * Executes [lambda] once `enabled` becomes false.
     *
     * If it already false, executes [lambda] immediately and returns null.
     *
     * @return a listener to pass to [unregister]
     */
    fun onDisabled(lambda: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener? {
        if (!enabled) {
            lambda()
            return null
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ENABLED_KEY && !enabled) {
                lambda()
            }
        }
        return listener
    }

    /** Unregister a listener. */
    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener == null) return

        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun safeGetBoolean(key: String, defaultValue: Boolean): Boolean {
        return try {
            prefs.getBoolean(key, defaultValue)
        } catch (e: ClassCastException) {
            parseBoolean(prefs.getString(key, defaultValue.toString()))
        }
    }

    private fun safeGetInt(key: String, defaultValue: Int): Int {
        return try {
            prefs.getInt(key, defaultValue)
        } catch (e: ClassCastException) {
            parseInt(prefs.getString(key, defaultValue.toString()))
        }
    }
}
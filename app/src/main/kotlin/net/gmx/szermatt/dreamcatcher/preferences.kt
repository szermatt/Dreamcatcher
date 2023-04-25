package net.gmx.szermatt.dreamcatcher

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.IntDef
import androidx.leanback.app.ProgressBarManager
import androidx.leanback.preference.LeanbackPreferenceFragment
import androidx.preference.Preference
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import androidx.work.WorkManager
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
            prefs.test = TEST_RESULT_UNKNOWN
            val op = WorkManager.getInstance(context).enqueue(
                PowerOffWorker.workRequest(prefs.hostport, dryRun = true)
            )
            op.result.addListener(object : OperationListener(op) {
                override fun onSuccess() {
                    prefs.test = TEST_RESULT_OK
                    Toast.makeText(context, "Connection OK", Toast.LENGTH_LONG).show()
                }

                override fun onError() {
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
            val op =
                WorkManager.getInstance(context).enqueue(PowerOffWorker.workRequest(prefs.hostport))
            op.result.addListener(object : OperationListener(op) {
                override fun onSuccess() {
                    prefs.test = TEST_RESULT_OK
                }

                override fun onError() {
                    Toast.makeText(context, "Power off failed!", Toast.LENGTH_LONG).show()
                }
            }, context.mainExecutor)
            true
        }
    }
}

/** Helper for accessing and changing the preferences of this app. */
internal class DreamCatcherPreferenceManager(private val context: Context) {
    companion object {
        const val DELAY_KEY = "delay"
        const val ENABLED_KEY = "enabled"
        const val TEST_KEY = "test"
        const val HOSTPORT_KEY = "hostport"
    }

    private val prefs: SharedPreferences = getDefaultSharedPreferences(context)

    /** Checks whether the [DreamCatcherService] should run. */
    val enabled: Boolean
        get() = safeGetBoolean(ENABLED_KEY, context.resources.getBoolean(R.bool.enabled_default))

    /** Delay between the start of a daydream and sending the power off command. */
    val delay: Int
        get() = safeGetInt(DELAY_KEY, context.resources.getInteger(R.integer.delay_default))

    /** Address of the XMPP port of the Harmony Hub. */
    val hostport: String
        get() = prefs.getString(HOSTPORT_KEY, "")!!

    /** Result of the last attempt at connecting to the Harmony Hub. */
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
            if (key == ENABLED_KEY && enabled) {
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
        prefs.registerOnSharedPreferenceChangeListener(listener)
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

/** Value for `test` in [DreamCatcherPreferenceManager]. */
@IntDef(TEST_RESULT_UNKNOWN, TEST_RESULT_OK, TEST_RESULT_FAIL)
@Retention(AnnotationRetention.SOURCE)
annotation class TestResult {
    companion object {
        const val TEST_RESULT_UNKNOWN = 0
        const val TEST_RESULT_OK = 1
        const val TEST_RESULT_FAIL = 2
    }
}

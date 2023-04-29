package net.gmx.szermatt.dreamcatcher

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.annotation.IntDef
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.preference.Preference
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import net.gmx.szermatt.dreamcatcher.DreamCatcherApplication.Companion.TAG
import net.gmx.szermatt.dreamcatcher.TestResult.Companion.TEST_RESULT_FAIL
import net.gmx.szermatt.dreamcatcher.TestResult.Companion.TEST_RESULT_OK
import net.gmx.szermatt.dreamcatcher.TestResult.Companion.TEST_RESULT_UNKNOWN
import java.lang.Boolean.parseBoolean
import java.lang.Integer.parseInt

/**
 * Helper for accessing and changing preferences used by this app.
 */
class DreamCatcherPreferenceFragment : LeanbackPreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference, rootKey)

        val enabled: Preference =
            preferenceManager.findPreference(DreamCatcherPreferenceManager.ENABLED_KEY)!!
        enabled.setSummaryProvider {
            val context = it.context
            val prefs = DreamCatcherPreferenceManager(context)
            context.getString(
                R.string.preference_enabled_summary, prefs.getAddressForDisplay(), prefs.delay
            )
        }
        val delay: Preference =
            preferenceManager.findPreference(DreamCatcherPreferenceManager.DELAY_KEY)!!
        delay.setSummaryProvider {
            val context = it.context
            val prefs = DreamCatcherPreferenceManager(context)
            context.getString(R.string.preference_delay_summary, prefs.delay)
        }
        val address: Preference =
            preferenceManager.findPreference(DreamCatcherPreferenceManager.HOSTPORT_KEY)!!
        address.setSummaryProvider {
            val context = it.context
            val prefs = DreamCatcherPreferenceManager(context)
            context.getString(R.string.preference_address_summary, prefs.getAddressForDisplay())
        }
        address.setOnPreferenceChangeListener { p, newValue ->
            val prefs = DreamCatcherPreferenceManager(p.context)
            if (newValue != prefs.address) {
                // Changing the address invalidates the test result.
                prefs.test = TEST_RESULT_UNKNOWN
            }
            true
        }
        val test: Preference = preferenceManager.findPreference("test")!!
        test.setSummaryProvider {
            val context = it.context
            val prefs = DreamCatcherPreferenceManager(context)
            val id = when (prefs.test) {
                TEST_RESULT_OK -> R.string.preference_test_ok_summary
                TEST_RESULT_FAIL -> R.string.preference_test_fail_summary
                else -> R.string.preference_test_summary
            }
            context.getString(id, prefs.getAddressForDisplay())
        }
        test.setOnPreferenceClickListener {
            val context = it.context
            val prefs = DreamCatcherPreferenceManager(context)

            val request = PowerOffWorker.workRequest(prefs.address, dryRun = true)
            val workManager = WorkManager.getInstance(context)
            workManager.enqueue(request)
            showProgress(request, context.getString(R.string.testing_connection))
            cancelWhenBlocked(workManager, this, request.id)

            prefs.test = TEST_RESULT_UNKNOWN
            onWorkDone(workManager, this, request.id) { state ->
                when (state) {
                    WorkInfo.State.SUCCEEDED -> prefs.test = TEST_RESULT_OK
                    WorkInfo.State.FAILED -> prefs.test = TEST_RESULT_FAIL
                    else -> {}
                }
            }
            true
        }
        val powerOff: Preference = preferenceManager.findPreference("powerOff")!!
        powerOff.setSummaryProvider {
            val context = it.context
            val prefs = DreamCatcherPreferenceManager(context)
            context.getString(R.string.preference_poweroff_summary, prefs.getAddressForDisplay())
        }
        powerOff.setOnPreferenceClickListener {
            val context = it.context
            val prefs = DreamCatcherPreferenceManager(context)

            val request = PowerOffWorker.workRequest(prefs.address)
            val workManager = WorkManager.getInstance(context)
            workManager.enqueue(request)
            showProgress(request, context.getString(R.string.powering_off))
            cancelWhenBlocked(workManager, this, request.id)
            onWorkDone(workManager, this, request.id) { state ->
                if (state == WorkInfo.State.SUCCEEDED) {
                    prefs.test = TEST_RESULT_OK
                }
            }
            true
        }
    }

    /** Show a fragment that tracks the progress of [request]. */
    private fun showProgress(request: WorkRequest, message: String) {
        parentFragmentManager
            .beginTransaction()
            .add(R.id.main, WorkProgressFragment.create(request.id, message))
            .commit()
    }
}

/** Helper for accessing and changing the preferences of this app. */
internal class DreamCatcherPreferenceManager(private val context: Context) {
    companion object {
        const val DELAY_KEY = "delay"
        const val ENABLED_KEY = "enabled"
        const val TEST_KEY = "test"
        const val HOSTPORT_KEY = "address"
    }

    private val prefs: SharedPreferences = getDefaultSharedPreferences(context)

    /** Checks whether the [DreamCatcherService] should run. */
    val enabled: Boolean
        get() = safeGetBoolean(ENABLED_KEY, context.resources.getBoolean(R.bool.enabled_default))

    /** Delay between the start of a daydream and sending the power off command. */
    val delay: Int
        get() = safeGetInt(DELAY_KEY, context.resources.getInteger(R.integer.delay_default))

    /** Address of the XMPP port of the Harmony Hub. */
    val address: String
        get() = prefs.getString(HOSTPORT_KEY, "")!!

    /** Result of the last attempt at connecting to the Harmony Hub. */
    @TestResult
    var test: Int
        get() = safeGetInt(TEST_KEY, TEST_RESULT_UNKNOWN)
        set(v: Int) = with(prefs.edit()) {
            putInt(TEST_KEY, v)
            commit()
        }

    /** Returns the value of `address` or a default value, suitable for display, if none is set. */
    fun getAddressForDisplay(): String {
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
        } catch (_: ClassCastException) {
            val s = prefs.getString(key, defaultValue.toString())
            try {
                parseBoolean(s)
            } catch (_: IllegalArgumentException) {
                Log.w(TAG, "Invalid preference value for ${key}: '${s}'")
                defaultValue
            }
        }
    }

    private fun safeGetInt(key: String, defaultValue: Int): Int {
        return try {
            prefs.getInt(key, defaultValue)
        } catch (_: ClassCastException) {
            val s = prefs.getString(key, defaultValue.toString())!!
            try {
                parseInt(s)
            } catch (_: IllegalArgumentException) {
                Log.w(TAG, "Invalid preference value for ${key}: '${s}'")
                defaultValue
            }
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

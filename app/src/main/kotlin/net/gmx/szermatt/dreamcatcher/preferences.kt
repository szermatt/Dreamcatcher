package net.gmx.szermatt.dreamcatcher

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.annotation.IntDef
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import androidx.preference.PreferenceViewHolder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import kotlinx.coroutines.*
import net.gmx.szermatt.dreamcatcher.DreamcatcherApplication.Companion.TAG
import net.gmx.szermatt.dreamcatcher.TestResult.Companion.TEST_RESULT_FAIL
import net.gmx.szermatt.dreamcatcher.TestResult.Companion.TEST_RESULT_OK
import net.gmx.szermatt.dreamcatcher.TestResult.Companion.TEST_RESULT_UNKNOWN
import net.gmx.szermatt.dreamcatcher.harmony.DiscoveredHub
import net.gmx.szermatt.dreamcatcher.harmony.discoveryChannel
import java.lang.Boolean.parseBoolean
import java.lang.Integer.parseInt

/**
 * Helper for accessing and changing preferences used by this app.
 */
class DreamcatcherPreferenceFragment : LeanbackPreferenceFragmentCompat() {
    @ExperimentalCoroutinesApi
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference, rootKey)

        val prefs = DreamcatcherPreferenceManager(requireContext())
        val delay: Preference =
            preferenceManager.findPreference(DreamcatcherPreferenceManager.DELAY_KEY)!!
        val hubList = preferenceManager.findPreference<ListPreference>("hub")!!
        val test = preferenceManager.findPreference<TestResultPreference>(
            DreamcatcherPreferenceManager.TEST_KEY
        )!!
        val powerOff: Preference = preferenceManager.findPreference("powerOff")!!

        delay.setSummaryProvider {
            it.context.getString(R.string.preference_delay_summary, prefs.delay)
        }

        prefs.hub?.let { selected ->
            hubList.entries = arrayOf(selected.friendlyName)
            hubList.entryValues = arrayOf(selected.toString())
            hubList.setValueIndex(0)
        }
        hubList.setSummaryProvider {
            prefs.hub?.friendlyName
                ?: it.context.getString(R.string.preference_hub_summary)
        }
        hubList.setOnPreferenceChangeListener { _, _ ->
            test.invalidateResult()
            true
        }
        lifecycleScope.launch(Dispatchers.Main) {
            updateHubList(requireContext(), hubList)
        }

        test.setSummaryProvider {
            val id = when (prefs.test) {
                TEST_RESULT_OK -> R.string.preference_test_ok_summary
                TEST_RESULT_FAIL -> R.string.preference_test_fail_summary
                else -> R.string.preference_test_summary
            }
            it.context.getString(id)
        }
        test.setOnPreferenceClickListener {
            val request = PowerOffWorker.workRequest(uuid = prefs.hub?.uuid, dryRun = true)
            val workManager = WorkManager.getInstance(it.context)
            workManager.enqueue(request)
            showProgress(request, it.context.getString(R.string.testing_connection))
            cancelWhenBlocked(workManager, this, request.id)

            test.invalidateResult()
            onWorkDone(workManager, this, request.id) { state ->
                when (state) {
                    WorkInfo.State.SUCCEEDED -> test.persistSuccess()
                    WorkInfo.State.FAILED -> test.persistFailure()
                    else -> {}
                }
                Log.d(TAG, "${request.id} finished in state ${state}, result=${prefs.test}.")
            }
            true
        }

        powerOff.setOnPreferenceClickListener {
            val request = PowerOffWorker.workRequest(uuid = prefs.hub?.uuid)
            val workManager = WorkManager.getInstance(it.context)
            workManager.enqueue(request)
            showProgress(request, it.context.getString(R.string.powering_off))
            cancelWhenBlocked(workManager, this, request.id)
            true
        }
    }

    /**
     * Lets discovery run in the background and add any new hub to [hubList].
     */
    @ExperimentalCoroutinesApi
    private suspend fun updateHubList(context: Context, hubList: ListPreference) =
        withContext(Dispatchers.Main) {
            val hubs = mutableMapOf<String, DiscoveredHub>()
            hubList.value?.let { hubString ->
                DiscoveredHub.fromString(hubString)?.let { hub ->
                    hubs[hub.uuid] = hub
                }
            }
            val onlineHubs = mutableSetOf<String>()

            for (hub in discoveryChannel()) {
                if (onlineHubs.contains(hub.uuid)) continue
                onlineHubs.add(hub.uuid)

                hubs[hub.uuid] = hub
                val selected = hubList.value?.let { DiscoveredHub.fromString(it)?.uuid }
                hubList.entries = hubs.map { (_, hub) ->
                    context.getString(
                        R.string.discovered_hub,
                        hub.friendlyName ?: context.getString(R.string.unnamed_hub)
                    )
                }.toTypedArray()
                hubList.entryValues = hubs.map { (_, hub) ->
                    hub.toString()
                }.toTypedArray()
                val index = selected?.let { hubs.keys.indexOf(selected) } ?: -1
                if (index >= 0) {
                    hubList.setValueIndex(index)
                }
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
internal class DreamcatcherPreferenceManager(
    private val context: Context,
) {
    companion object {
        const val DELAY_KEY = "delay"
        const val ENABLED_KEY = "enabled"
        const val TEST_KEY = "test"
        const val HUB_KEY = "hub"
    }

    private val prefs: SharedPreferences = getDefaultSharedPreferences(context)

    /** Checks whether the [DreamcatcherService] should run. */
    val enabled: Boolean
        get() = safeGetBoolean(ENABLED_KEY, context.resources.getBoolean(R.bool.enabled_default))

    /** Delay between the start of a daydream and sending the power off command. */
    val delay: Int
        get() = safeGetInt(DELAY_KEY, parseInt(context.resources.getString(R.string.delay_default)))

    /** Result of the last attempt at connecting to the Harmony Hub. */
    @TestResult
    val test: Int
        get() = safeGetInt(TEST_KEY, TEST_RESULT_UNKNOWN)

    /** Currently selected Harmony Hub, if any. */
    val hub: DiscoveredHub?
        get() = DiscoveredHub.fromString(prefs.getString(HUB_KEY, "")!!)

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

/** Value for `test` in [DreamcatcherPreferenceManager]. */
@IntDef(TEST_RESULT_UNKNOWN, TEST_RESULT_OK, TEST_RESULT_FAIL)
@Retention(AnnotationRetention.SOURCE)
annotation class TestResult {
    companion object {
        const val TEST_RESULT_UNKNOWN = 0
        const val TEST_RESULT_OK = 1
        const val TEST_RESULT_FAIL = 2
    }
}

class TestResultPreference(
    context: Context, attrs: AttributeSet?
) : Preference(context, attrs) {
    private var mCross: View? = null
    private var mTicked: View? = null

    constructor(context: Context) : this(context, null)

    init {
        widgetLayoutResource = R.layout.result_pref_widget_layout
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        mTicked = holder.findViewById(R.id.prefs_ticked)
        mCross = holder.findViewById(R.id.prefs_cross)

        updateIcon()
    }

    private fun updateIcon() {
        when (getPersistedInt(TEST_RESULT_UNKNOWN)) {
            TEST_RESULT_OK -> {
                mTicked?.visibility = View.VISIBLE
                mCross?.visibility = View.INVISIBLE
            }
            TEST_RESULT_FAIL -> {
                mTicked?.visibility = View.INVISIBLE
                mCross?.visibility = View.VISIBLE
            }
            else -> {
                mTicked?.visibility = View.INVISIBLE
                mCross?.visibility = View.INVISIBLE
            }
        }
    }

    override fun notifyChanged() {
        super.notifyChanged()
        updateIcon()
    }

    fun invalidateResult() {
        set(TEST_RESULT_UNKNOWN)
    }

    fun persistSuccess() {
        set(TEST_RESULT_OK)
    }

    fun persistFailure() {
        set(TEST_RESULT_FAIL)
    }

    private fun set(@TestResult result: Int) {
        if (callChangeListener(result)) {
            persistInt(result)
        }
        notifyChanged()
    }
}

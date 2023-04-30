package net.gmx.szermatt.dreamcatcher

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import net.gmx.szermatt.dreamcatcher.DreamCatcherApplication.Companion.TAG
import java.util.*
import kotlin.math.roundToInt

/** Cancels the worker if it is blocked. */
fun cancelWhenBlocked(
    manager: WorkManager,
    owner: LifecycleOwner,
    requestId: UUID,
) {
    val liveWorkInfo = manager.getWorkInfoByIdLiveData(requestId)
    val initial = liveWorkInfo.value?.state
    if (isFinal(initial)) return

    if (initial == WorkInfo.State.BLOCKED) {
        Log.i(TAG, "${requestId} blocked, cancelling.")
        manager.cancelWorkById(requestId)
        return
    }
    val observer = object : Observer<WorkInfo> {
        override fun onChanged(workInfo: WorkInfo?) {
            val state = workInfo?.state
            when {
                state == WorkInfo.State.BLOCKED -> {
                    Log.i(TAG, "${requestId} blocked, cancelling.")
                    liveWorkInfo.removeObserver(this)
                    manager.cancelWorkById(requestId)
                }
                isFinal(state) -> {
                    Log.d(TAG, "${requestId} done.")
                    liveWorkInfo.removeObserver(this)
                }
            }
        }
    }
    liveWorkInfo.observe(owner, observer)
}

/** Calls [lambda] when the [request] is finished, successfully or not. */
fun onWorkDone(
    manager: WorkManager,
    owner: LifecycleOwner,
    requestId: UUID,
    lambda: (WorkInfo.State) -> Unit
) {
    val liveWorkInfo = manager.getWorkInfoByIdLiveData(requestId)
    val initial = liveWorkInfo.value?.state
    if (initial != null && isFinal(initial)) {
        Log.d(TAG, "${requestId} reached final state ${initial}.")
        lambda(initial)
        return
    }
    val observer = object : Observer<WorkInfo> {
        override fun onChanged(workInfo: WorkInfo?) {
            val state = workInfo?.state
            if (state != null && isFinal(state)) {
                Log.d(TAG, "${requestId} reached final state ${state}.")
                liveWorkInfo.removeObserver(this)
                lambda(state)
            }
        }
    }
    liveWorkInfo.observe(owner, observer)
}

/** Calls [lambda] whenever the [WorkInfo] of the work changes. */
fun onWorkInfoChange(
    manager: WorkManager,
    owner: LifecycleOwner,
    requestId: UUID,
    lambda: (WorkInfo) -> Unit
) {
    val liveWorkInfo = manager.getWorkInfoByIdLiveData(requestId)
    liveWorkInfo.value?.let { lambda(it) }
    liveWorkInfo.observe(owner) { lambda(it) }
}

private fun isFinal(state: WorkInfo.State?): Boolean {
    return when (state) {
        WorkInfo.State.CANCELLED -> true
        WorkInfo.State.SUCCEEDED -> true
        WorkInfo.State.FAILED -> true
        else -> false
    }
}

/** A fragment that displays shows the state of a work request. */
class WorkProgressFragment : Fragment(R.layout.progress_fragment) {
    companion object {
        /** How long a "failed" message is displayed. */
        private const val LONG_DELAY_MS = 1000L

        /** How long to keep a full progress bar on the screen. */
        private const val SHORT_DELAY_MS = 250L

        /** Key in the worker's progress data containing the current step, starting at 0. */
        private val CURRENT_STEP_KEY = "WorkProgressFragment.currentStep"

        /** Key in the worker's progress data containing the total step count. */
        private val STEP_COUNT_KEY = "WorkProgressFragment.stepCount"

        /**
         * Creates a new fragment for tracking a work request.
         *
         * The work request is identified by its [uuid]. Once attached,
         * the fragment stays up until the work request has entered a final state, showing
         * [message].
         */
        fun create(uuid: UUID, message: String): WorkProgressFragment {
            val bundle = Bundle()
            bundle.putString("uuid", uuid.toString())
            bundle.putString("message", message)
            val fragment = WorkProgressFragment()
            fragment.setArguments(bundle)
            return fragment
        }

        /**
         * Fill a worker's progress data in a way understood by this fragment.
         */
        fun fillProgressData(data: Data.Builder, currentStep: Int, stepCount: Int) {
            data.putInt(CURRENT_STEP_KEY, currentStep)
            data.putInt(STEP_COUNT_KEY, stepCount)
        }
    }

    private var mSpinner: ProgressBar? = null
    private var mProgressBar: ProgressBar? = null
    private var mLabel: TextView? = null
    private var mStateLabel: TextView? = null
    private var mHiding = false
    private var mHidden = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireActivity()
            .onBackPressedDispatcher
            .addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!mHiding) {
                        val c = context
                        val id = arguments?.getString("uuid", null)

                        if (c != null && id != null) {
                            WorkManager.getInstance(c).cancelWorkById(UUID.fromString(id))
                        }
                    }
                    hideNow()
                }
            })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mSpinner = view.findViewById<ProgressBar>(R.id.spinner)
        mSpinner?.visibility = View.VISIBLE

        mProgressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        mProgressBar?.visibility = View.GONE

        mLabel = view.findViewById<TextView>(R.id.progressLabel)!!
        mLabel?.text = arguments?.getString("message") ?: ""

        mStateLabel = view.findViewById<TextView>(R.id.progressStateLabel)!!
        mStateLabel?.visibility = View.GONE

        val uuid = UUID.fromString(arguments?.getString("uuid") ?: "")
        val workManager = WorkManager.getInstance(context!!)
        onWorkInfoChange(workManager, viewLifecycleOwner, uuid) { workInfo ->
            updateProgress(workInfo)
        }
        onWorkDone(workManager, viewLifecycleOwner, uuid) { state ->
            when (state) {
                WorkInfo.State.SUCCEEDED -> {
                    hideLater(SHORT_DELAY_MS)
                }
                WorkInfo.State.CANCELLED -> {
                    mStateLabel?.text = getString(R.string.progress_cancelled)
                    mStateLabel?.visibility = View.VISIBLE
                    hideLater(LONG_DELAY_MS)
                }
                else -> {
                    mStateLabel?.text = getString(R.string.progress_failed)
                    mStateLabel?.visibility = View.VISIBLE
                    hideLater(LONG_DELAY_MS)
                }
            }
        }
    }

    /**
     * Have the spinner/progress bar reflect the progress reported by the worker.
     */
    private fun updateProgress(workInfo: WorkInfo) {
        if (workInfo.state == WorkInfo.State.SUCCEEDED) {
            // Move progress bar to 100%, since we might have missed the last update. Note that
            // if the spinner is still showing, this does nothing.
            Log.d(TAG, "Progress: 100%")
            mProgressBar?.progress = 100
            return
        }

        val progress = workInfo.progress
        val currentStep = progress.getInt(CURRENT_STEP_KEY, -1)
        val stepCount = progress.getInt(STEP_COUNT_KEY, 0)

        if (currentStep < 0 || currentStep > stepCount) return

        val percent = (currentStep.toFloat() / stepCount.toFloat() * 100).roundToInt()
        if (mProgressBar?.visibility != View.VISIBLE) {
            // Since a percentage is now available, show the progress bar instead of the spinner.
            mSpinner?.visibility = View.GONE
            mProgressBar?.visibility = View.VISIBLE
        }
        mProgressBar?.progress = percent
    }

    private fun hideLater(delayMs: Long) {
        if (mHiding) return

        Handler(Looper.getMainLooper()).postDelayed({
            hideNow()
        }, delayMs)
        mHiding = true
    }

    private fun hideNow() {
        if (mHidden) return

        mHidden = true
        parentFragmentManager.beginTransaction().remove(this).commit()
    }
}

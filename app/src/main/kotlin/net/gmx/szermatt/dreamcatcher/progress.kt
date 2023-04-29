package net.gmx.szermatt.dreamcatcher

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import net.gmx.szermatt.dreamcatcher.DreamCatcherApplication.Companion.TAG
import java.util.*

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
        /** How long to show a final state before detaching the fragment. */
        private const val DETACH_DELAY_MS = 1000L

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
    }

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
        mLabel = view.findViewById<TextView>(R.id.progressLabel)!!
        mLabel?.text = arguments?.getString("message") ?: ""
        mStateLabel = view.findViewById<TextView>(R.id.progressStateLabel)!!
        mStateLabel?.visibility = View.GONE

        val uuid = UUID.fromString(arguments?.getString("uuid") ?: "")
        val workManager = WorkManager.getInstance(context!!)
        onWorkDone(workManager, viewLifecycleOwner, uuid) { state ->
            mStateLabel?.visibility = View.VISIBLE
            mStateLabel?.text = when (state) {
                WorkInfo.State.SUCCEEDED -> getString(R.string.progress_succeeded)
                WorkInfo.State.CANCELLED -> getString(R.string.progress_cancelled)
                else -> getString(R.string.progress_failed)
            }
            hideLater()
        }
    }

    private fun hideLater() {
        if (mHiding) return

        Handler(Looper.getMainLooper()).postDelayed({
            hideNow()
        }, DETACH_DELAY_MS)
        mHiding = true
    }

    private fun hideNow() {
        if (mHidden) return

        mHidden = true
        parentFragmentManager.beginTransaction().remove(this).commit()
    }
}

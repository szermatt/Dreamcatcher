package net.gmx.szermatt.dreamcatcher

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.*

/** Calls [lambda] when the [request] is finished, successfully or not. */
fun onWorkDone(
    manager: WorkManager,
    owner: LifecycleOwner,
    request: WorkRequest,
    lambda: (WorkInfo.State) -> Unit
) {
    val liveWorkInfo = manager.getWorkInfoByIdLiveData(request.id)
    val state = liveWorkInfo.value?.state
    if (state != null && isFinal(state)) {
        lambda(state)
        return
    }
    val observer = object : Observer<WorkInfo> {
        override fun onChanged(workInfo: WorkInfo?) {
            workInfo?.state?.let { state ->
                if (isFinal(state)) {
                    liveWorkInfo.removeObserver(this)
                    lambda(state)
                }
            }
        }
    }
    liveWorkInfo.observe(owner, observer)
}

private fun isFinal(state: WorkInfo.State): Boolean {
    return when(state) {
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
        private const val DETACH_DELAY_MS = 1000

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mLabel = view.findViewById<TextView>(R.id.progressLabel)!!
        mLabel?.text = arguments?.getString("message") ?: ""

        mStateLabel = view.findViewById<TextView>(R.id.progressStateLabel)!!

        val uuid = UUID.fromString(arguments?.getString("uuid") ?: "")
        val liveWorkInfo = WorkManager.getInstance(context!!).getWorkInfoByIdLiveData(uuid)
        updateProgress(liveWorkInfo.value)
        liveWorkInfo.observe(this) { updateProgress(it) }
    }

    /**
     * Have the UI reflect the state of [WorkInfo].
     *
     * Once the worker has entered a final state, the fragment detaches itself after a delay.
     */
    private fun updateProgress(workInfo: WorkInfo?) {
        when (workInfo?.state) {
            WorkInfo.State.CANCELLED -> {
                mStateLabel?.visibility = View.VISIBLE
                mStateLabel?.text = getString(R.string.progress_cancelled)
                hideLater()
            }
            WorkInfo.State.SUCCEEDED -> {
                mStateLabel?.visibility = View.VISIBLE
                mStateLabel?.text = getString(R.string.progress_succeeded)
                hideLater()
            }
            WorkInfo.State.FAILED -> {
                mStateLabel?.visibility = View.VISIBLE
                mStateLabel?.text = getString(R.string.progress_failed)
                hideLater()
            }
            else -> {
                mStateLabel?.visibility = View.GONE
                mStateLabel?.text = ""
            }
        }
    }

    private fun hideLater() {
        if (mHiding) return

        Handler(Looper.getMainLooper()).postDelayed({
            parentFragmentManager.beginTransaction().remove(this).commit()
        }, 1000)
        mHiding = true
    }
}

package net.gmx.szermatt.dreamcatcher

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
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
        Log.i(DreamCatcherApplication.TAG, "${requestId} blocked, cancelling.")
        manager.cancelWorkById(requestId)
        return
    }
    val observer = object : Observer<WorkInfo> {
        override fun onChanged(workInfo: WorkInfo?) {
            val state = workInfo?.state
            when {
                state == WorkInfo.State.BLOCKED -> {
                    Log.i(DreamCatcherApplication.TAG, "${requestId} blocked, cancelling.")
                    liveWorkInfo.removeObserver(this)
                    manager.cancelWorkById(requestId)
                }
                isFinal(state) -> {
                    Log.d(DreamCatcherApplication.TAG, "${requestId} done.")
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
        Log.d(DreamCatcherApplication.TAG, "${requestId} reached final state ${initial}.")
        lambda(initial)
        return
    }
    val observer = object : Observer<WorkInfo> {
        override fun onChanged(workInfo: WorkInfo?) {
            val state = workInfo?.state
            if (state != null && isFinal(state)) {
                Log.d(DreamCatcherApplication.TAG, "${requestId} reached final state ${state}.")
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

package net.gmx.szermatt.dreamcatcher

import android.content.Context
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.work.*
import net.gmx.szermatt.dreamcatcher.DreamCatcherApplication.Companion.TAG
import net.gmx.szermatt.dreamcatcher.harmony.PowerOffTask
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/** Wraps a [PowerOffTask] into a Worker.  */
class PowerOffWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        /** Creates a one-time [WorkRequest] for this worker. */
        fun workRequest(
            uuid: String? = null,
            delayInMinutes: Int = 0,
            dryRun: Boolean = false,
            tag: String? = null
        ): WorkRequest {
            val b = OneTimeWorkRequest.Builder(PowerOffWorker::class.java)
            if (delayInMinutes > 0) {
                b.setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                b.setInitialDelay(delayInMinutes.toLong(), TimeUnit.MINUTES)
            }
            tag?.let { b.addTag(it) }
            val data = Data.Builder()
            data.putBoolean("dryRun", dryRun)
            data.putString("uuid", uuid)
            b.setInputData(data.build())
            return b.build()
        }
    }

    // TODO: refactor task to avoid having to have a stopped field and synchronizing
    @GuardedBy("this")
    private var stopped = false

    @GuardedBy("this")
    private var task: PowerOffTask? = null

    override fun doWork(): Result {
        return try {
            val dryRun = inputData.getBoolean("dryRun", false)
            Log.d(TAG, "PowerOffWorker launched dryRun=$dryRun ")
            synchronized(this) {
                if (task == null) {
                    if (stopped) throw CancellationException("stopped")

                    task = PowerOffTask(
                        uuid = inputData.getString("uuid"),
                        listener = object : PowerOffTask.Listener {
                            override fun onPowerOffTaskProgress(step: Int, stepCount: Int) {
                                val data = Data.Builder()
                                WorkProgressFragment.fillProgressData(data, step, stepCount)
                                setProgressAsync(data.build())
                            }
                        }
                    )
                }
                task
            }?.run(dryRun = dryRun)
            Log.d(TAG, "PowerOffWorker succeeded")
            Result.success()
        } catch (e: CancellationException) {
            Log.d(TAG, "PowerOffWorker finished after being stopped", e)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "PowerOffWorker failed with exception ${e}", e)
            Result.failure()
        }
    }

    override fun onStopped() {
        Log.w(TAG, "PowerOffWorker stopped")
        synchronized(this) {
            stopped = true
            task
        }?.stop()
    }
}
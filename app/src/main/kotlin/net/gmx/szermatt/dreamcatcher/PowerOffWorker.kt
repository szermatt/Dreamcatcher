package net.gmx.szermatt.dreamcatcher

import android.content.Context
import android.util.Log
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
            address: String,
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
            data.putString("address", address)
            b.setInputData(data.build())
            return b.build()
        }
    }

    private val task = PowerOffTask(
        inputData.getString("address")!!,
        listener = object: PowerOffTask.Listener {
            override fun onPowerOffTaskProgress(step: Int, stepCount: Int) {
                val data = Data.Builder()
                WorkProgressFragment.fillProgressData(data, step, stepCount)
                setProgressAsync(data.build())
            }
        }
    )

    override fun doWork(): Result {
        return try {
            val dryRun = inputData.getBoolean("dryRun", false)
            Log.d(TAG, "PowerOffWorker launched dryRun=$dryRun ")
            task.run(dryRun = dryRun)
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
        task.stop()
    }
}
package net.gmx.szermatt.dreamcatcher

import android.content.Context
import android.util.Log
import androidx.work.*
import net.gmx.szermatt.dreamcatcher.DreamCatcherApplication.Companion.TAG
import net.gmx.szermatt.dreamcatcher.harmony.PowerOffStep
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
            dryRun: Boolean = false
        ): WorkRequest {
            val b = OneTimeWorkRequest.Builder(PowerOffWorker::class.java)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
            if (delayInMinutes > 0) {
                b.setInitialDelay(delayInMinutes.toLong(), TimeUnit.MINUTES)
            }
            val data = Data.Builder()
            data.putBoolean("dryRun", dryRun)
            data.putString("address", address)
            b.setInputData(data.build())
            return b.build()
        }

        /** Extracts the last step done from progress data reported by the worker. */
        @PowerOffStep
        fun getProgress(data: Data): Int {
            return data.getInt("step", PowerOffStep.STEP_SCHEDULED)
        }
    }

    private val task = PowerOffTask(
        inputData.getString("address")!!,
        listener = object: PowerOffTask.Listener {
            override fun onPowerOffTaskProgress(step: Int) {
                Log.d(TAG, "PowerOffWorker progress step=${step}")

                val data = Data.Builder()
                data.putInt("step", step)
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
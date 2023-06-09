package net.gmx.szermatt.dreamcatcher

import android.content.Context
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.work.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.gmx.szermatt.dreamcatcher.DreamcatcherApplication.Companion.TAG
import net.gmx.szermatt.dreamcatcher.harmony.PowerOffTask
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/** Wraps a [PowerOffTask] into a Worker.  */
class PowerOffWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

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

    @GuardedBy("this")
    private val task = PowerOffTask(
        listener = object : PowerOffTask.Listener {
            override fun onPowerOffTaskProgress(step: Int, stepCount: Int) {
                val data = Data.Builder()
                WorkProgressFragment.fillProgressData(data, step, stepCount)
                setProgressAsync(data.build())
            }
        }
    )

    @ExperimentalCoroutinesApi
    override suspend fun doWork(): Result {
        return try {
            val dryRun = inputData.getBoolean("dryRun", false)
            Log.d(TAG, "PowerOffWorker launched dryRun=$dryRun ")
            task.run(uuid = inputData.getString("uuid"), dryRun = dryRun)
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
}
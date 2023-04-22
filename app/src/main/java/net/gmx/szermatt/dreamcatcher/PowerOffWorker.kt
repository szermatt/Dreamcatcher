package net.gmx.szermatt.dreamcatcher

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import net.gmx.szermatt.dreamcatcher.DreamCatcherApplication.Companion.TAG
import net.gmx.szermatt.dreamcatcher.harmony.PowerOffTask
import java.util.concurrent.CancellationException

/** Wraps a [PowerOffTask] into a Worker.  */
class PowerOffWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    private val task = PowerOffTask()
    override fun doWork(): Result {
        return try {
            Log.d(TAG, "PowerOffWorker launched")
            task.run()
            Log.d(TAG, "PowerOffWorker succeeded")
            Result.success()
        } catch (e: CancellationException) {
            Log.d(TAG, "PowerOffWorker finished after being stopped", e)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "PowerOffWorker failed", e)
            Result.failure()
        }
    }

    override fun onStopped() {
        Log.w(TAG, "PowerOffWorker stopped")
        task.stop()
    }
}
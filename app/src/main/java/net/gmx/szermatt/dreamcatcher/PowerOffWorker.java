package net.gmx.szermatt.dreamcatcher;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import net.gmx.szermatt.dreamcatcher.harmony.PowerOffTask;

/** Wraps a {@link PowerOffTask} into a Worker. */
public class PowerOffWorker extends Worker {
    private static final String TAG = "DreamCatcher";

    private final PowerOffTask task = new PowerOffTask();

    public PowerOffWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        try {
            Log.i(TAG, "PowerOffWorker launched");
            task.run();
            Log.i(TAG, "PowerOffWorker succeeded");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "PowerOffWorker failed", e);
            return Result.failure();
        }
    }

    @Override
    public void onStopped() {
        Log.w(TAG, "PowerOffWorker stopped");
        task.stop();
    }
}

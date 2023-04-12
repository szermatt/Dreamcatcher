package net.gmx.szermatt.dreamcatcher;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class DreamCatcherService extends Service {
    private static final String TAG = "DreamCatcher";
    private static final String CHANNEL_ID = "DreamCatcher";

    private static final String WORKER_TAG = "powerOff";

    private final BroadcastReceiver mDreamingStarted = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Dreaming started");
            WorkManager.getInstance(context).enqueue(
                    new OneTimeWorkRequest.Builder(PowerOffWorker.class)
                            .setConstraints(new Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .build())
                            .setInitialDelay(10, TimeUnit.MINUTES)
                            .addTag(WORKER_TAG)
                            .build());
        }
    };

    private final BroadcastReceiver mDreamingStopped = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Dreaming stopped");
            WorkManager.getInstance(context).cancelAllWorkByTag(WORKER_TAG);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundWithNotification();

        registerReceiver(mDreamingStarted, new IntentFilter(Intent.ACTION_DREAMING_STARTED));
        registerReceiver(mDreamingStopped, new IntentFilter(Intent.ACTION_DREAMING_STOPPED));

        Log.d(TAG, "Service started with intent " + intent.getAction());
        return START_STICKY;
    }

    /**
     * Setup a notification channel and notification, to satisfy the requirement
     * that foreground services call startForeground() within 5s of being started.
     */
    private void startForegroundWithNotification() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "DreamCatcher Channel", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("DreamCatcher Notifications");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);

        startForeground(1, new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_computer)
                .setContentTitle("Dream Catcher")
                .setContentText("Watching out for long daydreams")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build());
    }

}

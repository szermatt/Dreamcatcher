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
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

public class DreamCatcherService extends Service {
    private static final String TAG = "DreamCatcher";
    private static final String CHANNEL_ID = "DreamCatcher";
    private BroadcastReceiver mReceiver;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    public void onDestroy() {
        Toast.makeText(this, "Dream catcher stopped", Toast.LENGTH_LONG).show();
        Log.d(TAG, "onDestroy");
    }

    @Override
    public void onStart(Intent intent, int startId)
    {
        Toast.makeText(this, "Dream catcher started", Toast.LENGTH_LONG).show();
        Log.d(TAG, "onStart");
        createNotificationChannel();
        startForeground(1, new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(androidx.leanback.R.drawable.lb_ic_pause)
                .setContentTitle("Dream Catcher")
                .setContentText("running...")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build());
        Log.d(TAG, "notified");

        this.mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Got Dreaming Intent: " + intent.getAction());
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_DREAMING_STARTED);
        filter.addAction(Intent.ACTION_DREAMING_STOPPED);
        this.registerReceiver(this.mReceiver, filter);

        Log.d(TAG, "registered");
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
            CharSequence name = "DreamCatcher Channel";
            String description = "DreamCatcher Notifications";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
    }
}

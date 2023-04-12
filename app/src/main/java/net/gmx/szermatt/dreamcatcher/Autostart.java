package net.gmx.szermatt.dreamcatcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * AutoStart listens to BOOT_COMPLETED intents and starts the service at boot time.
 */
public class Autostart extends BroadcastReceiver {
    private static final String TAG = "DreamCatcher";

    public void onReceive(Context context, Intent arg1) {
        Log.i(TAG, "autostart");
        Intent intent = new Intent(context, DreamCatcherService.class);
        context.startForegroundService(intent);
    }
}

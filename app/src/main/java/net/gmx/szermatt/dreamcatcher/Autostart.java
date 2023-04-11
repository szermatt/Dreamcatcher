package net.gmx.szermatt.dreamcatcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class Autostart extends BroadcastReceiver {
    private static final String TAG = "DreamCatcher";

    public void onReceive(Context context, Intent arg1)
    {
        Log.i(TAG, "autostart");
        Intent intent = new Intent(context,DreamCatcherService.class);
        context.startForegroundService(intent);
    }
}

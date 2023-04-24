package net.gmx.szermatt.dreamcatcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import androidx.work.WorkManager
import net.gmx.szermatt.dreamcatcher.DreamCatcherApplication.Companion.TAG


/** Intent used to start the DreamCatcherService. */
internal fun serviceIntent(context: Context) = Intent(context, DreamCatcherService::class.java)

/**
 * AutoStart listens to BOOT_COMPLETED intents and starts the service at boot time.
 */
class Autostart : BroadcastReceiver() {
    override fun onReceive(context: Context, arg1: Intent) {
        Log.i(TAG, "autostart")
        context.startForegroundService(serviceIntent(context))
    }
}

class DreamCatcherService : Service() {
    /** True once broadcast receivers have been registered.  */
    private var mRegistered = false

    private val mDreamingStarted: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val prefs = getDefaultSharedPreferences(context)
            val delayInMinutes = prefs.getInt("delay", 10)
            Log.d(TAG, "Dreaming started, power off in ${delayInMinutes}m")
            WorkManager.getInstance(context).enqueue(
                PowerOffWorker.workRequest(delayInMinutes = delayInMinutes)
            )
        }
    }
    private val mDreamingStopped: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Dreaming stopped")
            WorkManager.getInstance(context).cancelAllWorkByTag(WORKER_TAG)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        if (mRegistered) {
            unregisterReceiver(mDreamingStarted)
            unregisterReceiver(mDreamingStopped)
            mRegistered = false
        }
        Log.d(TAG, "Service destroyed")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (!mRegistered) {
            startForegroundWithNotification()

            // Cancel workers that might be left over from a previous
            // instance. If, for example, the device crashes after
            // starting the worker but before it's run, the system
            // might start the worker once it's up - but not in
            // daydream state anymore.
            WorkManager.getInstance(this).cancelAllWorkByTag(WORKER_TAG)
            registerReceiver(mDreamingStarted, IntentFilter(Intent.ACTION_DREAMING_STARTED))
            registerReceiver(mDreamingStopped, IntentFilter(Intent.ACTION_DREAMING_STOPPED))
            mRegistered = true
            Log.d(TAG, "Service started with intent " + intent.action)
        }
        return START_STICKY
    }

    /**
     * Setup a notification channel and notification, to satisfy the requirement
     * that foreground services call startForeground() within 5s of being started.
     */
    private fun startForegroundWithNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID, "DreamCatcher Channel", NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "DreamCatcher Notifications"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        startForeground(
            1, NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_computer)
                .setContentTitle("Dream Catcher")
                .setContentText("Watching out for long daydreams")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        )
    }

    companion object {
        private const val CHANNEL_ID = "DreamCatcher"
        private const val WORKER_TAG = "powerOff"
    }
}
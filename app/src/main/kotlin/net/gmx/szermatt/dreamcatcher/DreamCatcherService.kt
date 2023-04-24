package net.gmx.szermatt.dreamcatcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.*
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
    override fun onReceive(context: Context, intent: Intent) {
        if (DreamCatcherService.isEnabled(context)) {
            Log.i(TAG, "autostart")
            context.startForegroundService(serviceIntent(context))
        }
    }
}

class DreamCatcherService : Service() {
    companion object {
        fun isEnabled(context: Context) = isEnabled(getDefaultSharedPreferences(context))
        fun isEnabled(prefs: SharedPreferences) = prefs.getBoolean("enabled", false)

        private const val CHANNEL_ID = "DreamCatcher"
        private const val WORKER_TAG = "powerOff"
    }

    /** True once broadcast receivers have been registered.  */
    private var mRegistered = false

    /** Registered listener, non-null when [mRegistered] is true. */
    private var mEnabledListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** Broadcast receiver for dreaming started, non-null when [mRegistered] is true. */
    private var mDreamingStarted: BroadcastReceiver? = null

    /** Broadcast receiver for dreaming stopped, non-null when [mRegistered] is true. */
    private var mDreamingStopped: BroadcastReceiver? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        if (mRegistered) {
            val prefs = getDefaultSharedPreferences(this)
            prefs.unregisterOnSharedPreferenceChangeListener(mEnabledListener)
            unregisterReceiver(mDreamingStarted)
            unregisterReceiver(mDreamingStopped)
            mRegistered = false
            mEnabledListener = null
            mDreamingStarted = null
            mDreamingStopped = null
        }
        Log.d(TAG, "Service destroyed")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val prefs = getDefaultSharedPreferences(this)
        if (!isEnabled(prefs)) {
            stopService(intent)
            return START_STICKY
        }
        if (!mRegistered) {
            mEnabledListener =
                SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                    if (key == "enabled" && !isEnabled(prefs)) {
                        stopService(intent)
                    }
                }
            prefs.registerOnSharedPreferenceChangeListener(mEnabledListener)

            mDreamingStarted = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val prefs = getDefaultSharedPreferences(context)
                    val delayInMinutes = prefs.getInt("delay", 10)
                    Log.d(TAG, "Dreaming started, power off in ${delayInMinutes}m")
                    WorkManager.getInstance(context).enqueue(
                        PowerOffWorker.workRequest(delayInMinutes = delayInMinutes)
                    )
                }
            }
            registerReceiver(mDreamingStarted, IntentFilter(Intent.ACTION_DREAMING_STARTED))

            mDreamingStopped = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    Log.d(TAG, "Dreaming stopped")
                    WorkManager.getInstance(context).cancelAllWorkByTag(WORKER_TAG)
                }
            }
            registerReceiver(mDreamingStopped, IntentFilter(Intent.ACTION_DREAMING_STOPPED))

            startForegroundWithNotification()
            mRegistered = true

            // Cancel workers that might be left over from a previous
            // instance. If, for example, the device crashes after
            // starting the worker but before it's run, the system
            // might start the worker once it's up - but not in
            // daydream state anymore.
            WorkManager.getInstance(this).cancelAllWorkByTag(WORKER_TAG)

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

}
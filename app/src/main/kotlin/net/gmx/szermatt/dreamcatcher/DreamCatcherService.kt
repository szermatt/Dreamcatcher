package net.gmx.szermatt.dreamcatcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.*
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import net.gmx.szermatt.dreamcatcher.DreamCatcherApplication.Companion.TAG


/** Starts the service indirectly, using [DreamCatcherServiceStarter]. */
internal fun startDreamCatcherService(context: Context) {
    val intent = Intent()
    intent.action = "startService"
    intent.setClass(context, DreamCatcherServiceStarter::class.java)
    context.sendBroadcast(intent)
}

/**
 * Listens to BOOT_COMPLETED and startService intents and starts the service.
 */
class DreamCatcherServiceStarter : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (DreamCatcherPreferenceManager(context).enabled) {
            Log.i(TAG, "started by intent ${intent.action}")
            context.startForegroundService(Intent(context, DreamCatcherService::class.java))
        }
    }
}

/**
 * A service that tracks DREAMING_STARTED/STOPPED events and launches [PowerOffWorker].
 *
 * Catching DREAMING_STARTED/STOPPED requires an active app and service. To be able to track when
 * day dream starts and ends and power everything off through the Harmony Hub, this service needs to
 * stay up and running, as a foreground service, when it is enabled.
 *
 * This service is started by [DreamCatcherServiceStarter] at boot time, when the application
 * runs or whenever it is enabled in the the [SharedPreferences]. It stops on its own whenever it is
 * disabled in the preferences.
 */
class DreamCatcherService : Service() {
    companion object {
        private const val CHANNEL_ID = "DreamCatcher"
        private const val WORKER_TAG = "powerOff"
    }

    /** True once broadcast receivers have been registered.  */
    private var mRegistered = false

    /** Registered listener, non-null when [mRegistered] is true. */
    private var mDisabledListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** Broadcast receiver for dreaming started, non-null when [mRegistered] is true. */
    private var mDreamingStarted: BroadcastReceiver? = null

    /** Broadcast receiver for dreaming stopped, non-null when [mRegistered] is true. */
    private var mDreamingStopped: BroadcastReceiver? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        if (mRegistered) {
            DreamCatcherPreferenceManager(this).unregister(mDisabledListener)
            mDisabledListener = null

            unregisterReceiver(mDreamingStarted)
            mDreamingStarted = null

            unregisterReceiver(mDreamingStopped)
            mDreamingStopped = null

            mRegistered = false
        }
        Log.d(TAG, "Service destroyed")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (mRegistered) return START_STICKY

        val prefs = DreamCatcherPreferenceManager(this)
        mDisabledListener = prefs.onDisabled {
            WorkManager.getInstance(this).cancelAllWorkByTag(WORKER_TAG)
            stopService(intent)
        }
        if (mDisabledListener == null) return START_STICKY

        mDreamingStarted = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!prefs.enabled) return;

                val delayInMinutes = prefs.delay
                Log.d(
                    TAG,
                    "Dreaming started, send power off in ${delayInMinutes}m"
                )
                WorkManager.getInstance(context).enqueue(
                    PowerOffWorker.workRequest(
                        uuid = prefs.hub?.uuid,
                        delayInMinutes = delayInMinutes,
                        tag = WORKER_TAG,
                    )
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
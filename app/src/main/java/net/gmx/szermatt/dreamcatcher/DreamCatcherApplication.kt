package net.gmx.szermatt.dreamcatcher

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Holds global singleton instances.  */
class DreamCatcherApplication : Application() {
    private val executor = Executors.newCachedThreadPool()
    private val mainThreadHandler = Handler(Looper.getMainLooper())

    companion object {
        internal const val TAG = "DreamCatcher"

        /**
         * Returns the executor to use when running background tasks.
         */
        fun inBackground(context: Context): Executor {
            return fromContext(context).executor
        }

        /**
         * Returns a handler to use when running tasks on the main thread.
         */
        fun onMain(context: Context): Handler {
            return fromContext(context).mainThreadHandler
        }

        /**
         * Gets the appropriate application instance for the current context.
         */
        fun fromContext(context: Context): DreamCatcherApplication {
            return context.applicationContext as DreamCatcherApplication
        }
    }
}
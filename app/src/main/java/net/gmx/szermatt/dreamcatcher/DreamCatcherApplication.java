package net.gmx.szermatt.dreamcatcher;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Holds global singleton instances. */
public class DreamCatcherApplication extends Application {
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    /**
     * Returns the executor to use when running background tasks.
     */
    static Executor inBackground(Context context) {
        return fromContext(context).executor;
    }

    /**
     * Returns a handler to use when running tasks on the main thread.
     */
    static Handler onMain(Context context) {
        return fromContext(context).mainThreadHandler;
    }

    /**
     * Gets the appropriate application instance for the current context.
     */
    static DreamCatcherApplication fromContext(Context context) {
        return (DreamCatcherApplication) context.getApplicationContext();
    }
}

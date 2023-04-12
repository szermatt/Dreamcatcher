package net.gmx.szermatt.dreamcatcher;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DreamCatcherApplication extends Application {
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    /** Gets the appropriate application instance for the current context. */
    static DreamCatcherApplication fromContext(Context context) {
        return (DreamCatcherApplication) context.getApplicationContext();
    }

    /** Returns the executor to use when running background tasks. */
    Executor getBackgroundExecutor() {
        return executor;
    }

    /** Returns a handler to use when running tasks on the main thread. */
    Handler getMainThreadHandler() {
        return mainThreadHandler;
    }
}

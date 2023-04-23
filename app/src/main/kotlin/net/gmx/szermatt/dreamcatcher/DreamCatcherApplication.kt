package net.gmx.szermatt.dreamcatcher

import android.app.Application

/** Holds global singleton instances.  */
class DreamCatcherApplication : Application() {
    companion object {
        internal const val TAG = "DreamCatcher"
    }
}
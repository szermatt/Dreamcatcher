package net.gmx.szermatt.dreamcatcher

import android.app.Application

/** Holds global singleton instances.  */
class DreamcatcherApplication : Application() {
    companion object {
        internal const val TAG = "Dreamcatcher"
    }
}
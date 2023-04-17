package net.gmx.szermatt.dreamcatcher

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import net.gmx.szermatt.dreamcatcher.DreamCatcherApplication.Companion.TAG
import net.gmx.szermatt.dreamcatcher.harmony.PowerOffTask

/**
 * A placeholder activity for the application; its main functionality is in the service.
 */
class TvActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val inBackground = DreamCatcherApplication.inBackground(this)
        val onMain = DreamCatcherApplication.onMain(this)
        val button = findViewById<ImageButton>(R.id.power_off)
        button.setOnClickListener { _: View? ->
            Toast.makeText(this, "Power Off Requested", Toast.LENGTH_LONG).show()
            inBackground.execute {
                try {
                    Log.i(TAG, "Powering off...")
                    PowerOffTask().run()
                    Log.i(TAG, "Powered off")
                } catch (e: Exception) {
                    Log.e(TAG, "Power Off Failed", e)
                    onMain.post {
                        Toast.makeText(this, "Power Off Failed", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        startForegroundService(serviceIntent(this))
        Log.i(TAG, "started")
    }
}
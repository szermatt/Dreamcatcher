package net.gmx.szermatt.dreamcatcher

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import net.gmx.szermatt.dreamcatcher.DreamCatcherApplication.Companion.TAG

/**
 * A placeholder activity for the application; its main functionality is in the service.
 */
class TvActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val button = findViewById<ImageButton>(R.id.power_off)
        button.setOnClickListener { _: View? ->
            Toast.makeText(this, "Power Off Requested", Toast.LENGTH_LONG).show()
            val op = WorkManager.getInstance(this).enqueue(
                OneTimeWorkRequest.Builder(PowerOffWorker::class.java)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
            )
            val result = op.result
            result.addListener(
                {
                    try {
                        result.get()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Power Off Failed", Toast.LENGTH_LONG).show()
                    }
                },
                mainExecutor,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        startForegroundService(serviceIntent(this))
        Log.i(TAG, "started")
    }
}
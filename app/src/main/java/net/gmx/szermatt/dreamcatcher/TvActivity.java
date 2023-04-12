package net.gmx.szermatt.dreamcatcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import net.gmx.szermatt.dreamcatcher.harmony.PowerOffTask;

import java.util.concurrent.Executor;

/**
 * A placeholder activity for the application; its main functionality is in the service.
 */
public class TvActivity extends Activity {
    private static final String TAG = "DreamCatcher";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Executor inBackground = DreamCatcherApplication.inBackground(this);
        final Handler onMain = DreamCatcherApplication.onMain(this);

        ImageButton button = findViewById(R.id.power_off);
        button.setOnClickListener(v -> {
            Toast.makeText(this, "Power Off Requested", Toast.LENGTH_LONG).show();
            inBackground.execute(() -> {
                try {
                    Log.i(TAG, "Powering off...");
                    new PowerOffTask().run();
                    Log.i(TAG, "Powered off");
                } catch (Exception e) {
                    Log.e(TAG, "Power Off Failed", e);
                    onMain.post(() -> {
                        Toast.makeText(this, "Power Off Failed", Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        startForegroundService(new Intent(this, DreamCatcherService.class));
        Log.i(TAG, "started");
    }
}

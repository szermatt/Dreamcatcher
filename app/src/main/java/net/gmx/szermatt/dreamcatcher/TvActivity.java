package net.gmx.szermatt.dreamcatcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import net.gmx.szermatt.dreamcatcher.harmony.PowerOffTask;

import java.util.concurrent.Executor;

public class TvActivity extends Activity {
    private static final String TAG = "DreamCatcher";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button = (Button) findViewById(R.id.power_off);
        DreamCatcherApplication app = DreamCatcherApplication.fromContext(this);
        final Executor inBackground = app.getBackgroundExecutor();
        final Handler onMain = app.getMainThreadHandler();
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
        Intent intent = new Intent(this,DreamCatcherService.class);
        startForegroundService(intent);
        Log.i(TAG, "started");
    }
}

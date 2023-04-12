package net.gmx.szermatt.dreamcatcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

public class TvActivity extends Activity {
    private static final String TAG = "DreamCatcher";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button = (Button) findViewById(R.id.power_off);
        button.setOnClickListener(v -> {
            Toast.makeText(this, "Power Off Requested", Toast.LENGTH_LONG).show();
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

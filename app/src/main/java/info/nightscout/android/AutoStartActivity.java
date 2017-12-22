package info.nightscout.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import info.nightscout.android.medtronic.service.MasterService;

/**
 * Created by John on 22.12.17.
 */

public class AutoStartActivity extends AppCompatActivity {
    private static final String TAG = AutoStartActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        if (prefs.getBoolean("EnableCgmService", false)) {
            Log.d(TAG, "MasterService auto starter, starting!");

            startService(new Intent(getBaseContext(), MasterService.class));
        }

        finish();
    }
}

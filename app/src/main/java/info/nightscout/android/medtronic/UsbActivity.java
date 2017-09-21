package info.nightscout.android.medtronic;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import info.nightscout.android.UploaderApplication;

/**
 * Created by John on 8.9.17.
 */

public class UsbActivity extends AppCompatActivity {
    private static final String TAG = UsbActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(UploaderApplication.getAppContext());
        if (!prefs.getBoolean("EnableCgmService", false)) {
            Log.d(TAG, "starting main activity");
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        }

        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
    }

}
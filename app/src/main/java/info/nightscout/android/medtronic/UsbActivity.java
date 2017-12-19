package info.nightscout.android.medtronic;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import info.nightscout.android.medtronic.service.MasterService;

/**
 * Created by Pogman on 8.9.17.
 */

// UsbActivity is being used to intercept OS intents for Usb so that we can have permissions be excepted permanently for defaults
// also to stop the MainActivity being brought forward when user is in another app and a unplug/plug happens due to loose connection

public class UsbActivity extends AppCompatActivity {
    private static final String TAG = UsbActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");

        // if CGM service has not been started then start the UI
        // else let the service know we have usb permission and to start/resume polling

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean("EnableCgmService", false)) {
            Log.d(TAG, "starting main activity");
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        } else {
            Log.d(TAG, "starting master service");
            startService(new Intent(this, MasterService.class));
            sendBroadcast(new Intent(MasterService.Constants.ACTION_HAS_USB_PERMISSION));
        }

        finish();
    }
}
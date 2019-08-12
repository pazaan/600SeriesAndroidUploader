package info.nightscout.android.medtronic;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.store.DataStore;
import io.realm.Realm;

/**
 * Created by Pogman on 8.9.17.
 */

// UsbActivity is being used to intercept OS intents for Usb so that we can have permissions be excepted permanently for defaults
// also to stop the MainActivity being brought forward when user is in another app and a unplug/plug happens due to loose connection

public class UsbActivity extends Activity {
    private static final String TAG = UsbActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");

        // if CGM service has not been started then start the UI
        // else let the service know we have usb permission and to start/resume polling

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        boolean service = false;

        if (prefs.getBoolean("EnableCgmService", false)) {

            Realm storeRealm = null;
            try {
                storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
                if (storeRealm.where(DataStore.class).findFirst() != null) service = true;
            } catch (Exception ignored) {
            } finally {
                if (storeRealm != null && !storeRealm.isClosed()) storeRealm.close();
            }

        }

        if (service) {
            Log.d(TAG, "starting master service");
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(new Intent(this, MasterService.class));
                } else {
                    startService(new Intent(this, MasterService.class));
                }
            } catch (Exception ignored) {
            }
            // notify usb activity received
            // may only receive intent from os after permission has been accepted or set as default for app
            // older os versions will send intent on usb connect whatever permission state
            sendBroadcast(new Intent(MasterService.Constants.ACTION_USB_ACTIVITY));
        } else {
            Log.d(TAG, "starting main activity");
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        }

        finish();
    }
}
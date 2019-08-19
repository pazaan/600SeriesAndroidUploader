package info.nightscout.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.store.DataStore;
import io.realm.Realm;

/**
 * Created by Pogman on 22.12.17.
 */

public class AutoStartActivity extends AppCompatActivity {
    private static final String TAG = AutoStartActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        boolean service = false;

        if (prefs.getBoolean("EnableCgmService", false)) {

            Realm storeRealm = null;
            try {
                storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
                if (storeRealm.where(DataStore.class).findFirst() != null) {
                    service = true;
                    storeRealm.executeTransaction(new Realm.Transaction() {
                        @Override
                        public void execute(@NonNull Realm realm) {
                            realm.where(DataStore.class).findFirst()
                                    .setStartupTimestamp(System.currentTimeMillis());
                        }
                    });
                }
            } catch (Exception ignored) {
            } finally {
                if (storeRealm != null && !storeRealm.isClosed()) storeRealm.close();
            }

        }

        if (service) {
            Log.d(TAG, "MasterService auto starter, starting!");
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(new Intent(getBaseContext(), MasterService.class));
                } else {
                    startService(new Intent(getBaseContext(), MasterService.class));
                }
            } catch (Exception ignored) {
            }
        }

        finish();
    }
}

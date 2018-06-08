package info.nightscout.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.store.DataStore;
import io.realm.Realm;

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

            try {
                if (Realm.compactRealm(Realm.getDefaultConfiguration()))
                    Log.i(TAG, "compactRealm: default successful");
                if (Realm.compactRealm(UploaderApplication.getStoreConfiguration()))
                    Log.i(TAG, "compactRealm: store successful");
                if (Realm.compactRealm(UploaderApplication.getUserLogConfiguration()))
                    Log.i(TAG, "compactRealm: userlog successful");
                if (Realm.compactRealm(UploaderApplication.getHistoryConfiguration()))
                    Log.i(TAG, "compactRealm: history successful");
            } catch (Exception e) {
                Log.e(TAG, "Error trying to compact realm" + Log.getStackTraceString(e));
            }

            Log.d(TAG, "MasterService auto starter, starting!");

            startService(new Intent(getBaseContext(), MasterService.class));
        }

        finish();
    }
}

package info.nightscout.android.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Date;

import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.UserLogMessage;
import io.realm.Realm;

public class RealmKit {
    private static final String TAG = RealmKit.class.getSimpleName();

    public static synchronized void compact(Context context) {
        Log.d(TAG, "compactRealm called");

        try {

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
            long now = System.currentTimeMillis();
            long compact = now - 24 * 60 * 60000L;
            long lastrun;

            StringBuilder sb = new StringBuilder();

            lastrun = sharedPreferences.getLong("RealmCompactTimestampDefault", 0);
            Log.d(TAG, String.format("compactRealm: last run on default: %s", new Date(lastrun).toString()));
            if (lastrun < compact && Realm.compactRealm(Realm.getDefaultConfiguration())) {
                Log.i(TAG, "compactRealm: compacting default successful");
                sb.append(" default");
                sharedPreferences.edit().putLong("RealmCompactTimestampDefault", now).apply();
            }

            lastrun = sharedPreferences.getLong("RealmCompactTimestampStore", 0);
            Log.d(TAG, String.format("compactRealm: last run on store: %s", new Date(lastrun).toString()));
            if (lastrun < compact && Realm.compactRealm(UploaderApplication.getStoreConfiguration())) {
                Log.i(TAG, "compactRealm: compacting store successful");
                sb.append(" store");
                sharedPreferences.edit().putLong("RealmCompactTimestampStore", now).apply();
            }

            lastrun = sharedPreferences.getLong("RealmCompactTimestampUserlog", 0);
            Log.d(TAG, String.format("compactRealm: last run on userlog: %s", new Date(lastrun).toString()));
            if (lastrun < compact && Realm.compactRealm(UploaderApplication.getUserLogConfiguration())) {
                Log.i(TAG, "compactRealm: compacting userlog successful");
                sb.append(" userlog");
                sharedPreferences.edit().putLong("RealmCompactTimestampUserlog", now).apply();
            }

            lastrun = sharedPreferences.getLong("RealmCompactTimestampHistory", 0);
            Log.d(TAG, String.format("compactRealm: last run on history: %s", new Date(lastrun).toString()));
            if (lastrun < compact && Realm.compactRealm(UploaderApplication.getHistoryConfiguration())) {
                Log.i(TAG, "compactRealm: compacting history successful");
                sb.append(" history");
                sharedPreferences.edit().putLong("RealmCompactTimestampHistory", now).apply();
            }

            if (sb.length() > 0) {
                UserLogMessage.getInstance().addAsync(UserLogMessage.TYPE.NOTE, UserLogMessage.FLAG.EXTENDED,
                        "Realm: compacted" + sb.toString());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error trying to compact realm" + Log.getStackTraceString(e));
        }
    }

}

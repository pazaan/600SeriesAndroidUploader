package info.nightscout.android.upload.nightscout;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import info.nightscout.android.R;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.utils.DataStore;
import io.realm.Realm;
import io.realm.RealmResults;

public class NightscoutUploadIntentService extends IntentService {

    private static final String TAG = NightscoutUploadIntentService.class.getSimpleName();

    private Context mContext;
    private NightScoutUpload mNightScoutUpload;

    public NightscoutUploadIntentService() {
        super(NightscoutUploadIntentService.class.getName());
    }

    protected void sendStatus(String message) {
        Intent localIntent =
                new Intent(Constants.ACTION_STATUS_MESSAGE)
                        .putExtra(Constants.EXTENDED_DATA, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "onCreate called");
        mContext = this.getBaseContext();

        mNightScoutUpload = new NightScoutUpload();

    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent called");
        Realm mRealm = Realm.getDefaultInstance();

        RealmResults<PumpStatusEvent> records = mRealm
                .where(PumpStatusEvent.class)
                .equalTo("uploaded", false)
                .notEqualTo("sgv", 0)
                .findAll();

        if (records.size() > 0) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);

            Boolean enableRESTUpload = prefs.getBoolean("EnableRESTUpload", false);
            try {
                if (enableRESTUpload) {
                    long start = System.currentTimeMillis();
                    Log.i(TAG, String.format("Starting upload of %s record using a REST API", records.size()));
                    String urlSetting = prefs.getString(mContext.getString(R.string.preference_nightscout_url), "");
                    String secretSetting = prefs.getString(mContext.getString(R.string.preference_api_secret), "YOURAPISECRET");
                    Boolean uploadSuccess = mNightScoutUpload.doRESTUpload(urlSetting,
                            secretSetting, DataStore.getInstance().getUploaderBatteryLevel(), records);
                    if (uploadSuccess) {
                        mRealm.beginTransaction();
                        for (PumpStatusEvent updateRecord : records) {
                            updateRecord.setUploaded(true);
                        }
                        mRealm.commitTransaction();
                    }
                    Log.i(TAG, String.format("Finished upload of %s record using a REST API in %s ms", records.size(), System.currentTimeMillis() - start));
                }
            } catch (Exception e) {
                Log.e(TAG, "ERROR uploading data!!!!!", e);
            }
        } else {
            Log.i(TAG, "No records has to be uploaded");
        }
        mRealm.close();

        NightscoutUploadReceiver.completeWakefulIntent(intent);
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    public final class Constants {
        public static final String ACTION_STATUS_MESSAGE = "info.nightscout.android.upload.nightscout.STATUS_MESSAGE";

        public static final String EXTENDED_DATA = "info.nightscout.android.upload.nightscout.DATA";
    }


}

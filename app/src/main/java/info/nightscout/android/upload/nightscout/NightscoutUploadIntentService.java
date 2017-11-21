package info.nightscout.android.upload.nightscout;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.List;

import info.nightscout.android.R;
import info.nightscout.android.medtronic.PumpHistoryHandler;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.medtronicNg.PumpHistoryInterface;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.utils.StatusMessage;
import io.realm.Realm;
import io.realm.RealmResults;

public class NightscoutUploadIntentService extends IntentService {
    private static final String TAG = NightscoutUploadIntentService.class.getSimpleName();

    private Context mContext;
    private NightScoutUpload mNightScoutUpload;

    private StatusMessage statusMessage = StatusMessage.getInstance();

    public NightscoutUploadIntentService() {
        super(NightscoutUploadIntentService.class.getName());
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

        upload();

        NightscoutUploadReceiver.completeWakefulIntent(intent);
    }

    private void upload() {

        Realm mRealm = Realm.getDefaultInstance();

        final RealmResults<PumpStatusEvent> statusRecords = mRealm
                .where(PumpStatusEvent.class)
                .equalTo("uploaded", false)
                .findAll();

        PumpHistoryHandler pumpHistoryHandler = new PumpHistoryHandler();

        List<PumpHistoryInterface> records = pumpHistoryHandler.uploadREQ();

        if (statusRecords.size() > 0 || records.size() > 0) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            Boolean enableRESTUpload = prefs.getBoolean("EnableRESTUpload", false);

            try {
                if (enableRESTUpload) {
                    long start = System.currentTimeMillis();

                    Log.i(TAG, String.format("Starting upload of %s record using a REST API", statusRecords.size() + records.size()));
                    String urlSetting = prefs.getString(mContext.getString(R.string.preference_nightscout_url), "");
                    String secretSetting = prefs.getString(mContext.getString(R.string.preference_api_secret), "YOURAPISECRET");

                    int uploaderBatteryLevel = MasterService.getUploaderBatteryLevel();

                    Boolean uploadSuccess = mNightScoutUpload.doRESTUpload(
                            urlSetting,
                            secretSetting,
                            uploaderBatteryLevel,
                            statusRecords,
                            records);

                    if (uploadSuccess) {
                        pumpHistoryHandler.uploadACK();

                        mRealm.executeTransaction(new Realm.Transaction() {
                            @Override
                            public void execute(Realm realm) {
                                for (PumpStatusEvent updateRecord : statusRecords)
                                    updateRecord.setUploaded(true);
                            }
                        });

                    } else statusMessage.add(MasterService.ICON_WARN + "Uploading to nightscout was unsuccessful");

                    Log.i(TAG, String.format("Finished upload of %s record using a REST API in %s ms", records.size(), System.currentTimeMillis() - start));
                }
            } catch (Exception e) {
                statusMessage.add(MasterService.ICON_WARN + "Error uploading: " + e.getMessage());
                Log.e(TAG, "ERROR uploading data!!!!!", e);
            }

        } else {
            Log.i(TAG, "No records have to be uploaded");
        }

        pumpHistoryHandler.close();
        mRealm.close();
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

}

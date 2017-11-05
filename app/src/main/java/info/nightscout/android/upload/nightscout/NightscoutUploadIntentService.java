package info.nightscout.android.upload.nightscout;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.android.R;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.medtronicNg.PumpHistory;
import info.nightscout.android.model.medtronicNg.PumpHistoryBG;
import info.nightscout.android.model.medtronicNg.PumpHistoryBasal;
import info.nightscout.android.model.medtronicNg.PumpHistoryBolus;
import info.nightscout.android.model.medtronicNg.PumpHistoryCGM;
import info.nightscout.android.model.medtronicNg.PumpHistoryMisc;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.utils.StatusMessage;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;

public class NightscoutUploadIntentService extends IntentService {

    private static final String TAG = NightscoutUploadIntentService.class.getSimpleName();

    private Context mContext;
    private NightScoutUpload mNightScoutUpload;
    private NightScoutUploadV2 mNightScoutUploadV2;
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
        mNightScoutUploadV2 = new NightScoutUploadV2();

    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent called");

        uploadV1();
        uploadV2();

        NightscoutUploadReceiver.completeWakefulIntent(intent);
    }

    private void uploadV1() {
        Realm mRealm = Realm.getDefaultInstance();

        RealmResults<PumpStatusEvent> records = mRealm
                .where(PumpStatusEvent.class)
                .equalTo("uploaded", false)
                .findAll();

        if (records.size() > 0) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);

            Boolean enableRESTUpload = prefs.getBoolean("EnableRESTUpload", false);
            Boolean enableTreatmentsUpload = prefs.getBoolean("EnableTreatmentsUpload", false);

            try {
                if (enableRESTUpload) {
                    long start = System.currentTimeMillis();

                    Log.i(TAG, String.format("Starting upload of %s record using a REST API", records.size()));
                    String urlSetting = prefs.getString(mContext.getString(R.string.preference_nightscout_url), "");
                    String secretSetting = prefs.getString(mContext.getString(R.string.preference_api_secret), "YOURAPISECRET");

                    Boolean uploadSuccess = mNightScoutUpload.doRESTUpload(urlSetting,
                            secretSetting, enableTreatmentsUpload, MasterService.getUploaderBatteryLevel(), records);
                    if (uploadSuccess) {
                        mRealm.beginTransaction();
                        for (PumpStatusEvent updateRecord : records) {
                            updateRecord.setUploaded(true);
                        }
                        mRealm.commitTransaction();
                    } else {
                        statusMessage.add(MasterService.ICON_WARN + "Uploading to Nightscout returned unsuccessful");
                    }
                    Log.i(TAG, String.format("Finished upload of %s record using a REST API in %s ms", records.size(), System.currentTimeMillis() - start));
                } else {
                    mRealm.beginTransaction();
                    for (PumpStatusEvent updateRecord : records) {
                        updateRecord.setUploaded(true);
                    }
                    mRealm.commitTransaction();
                }
            } catch (Exception e) {
                statusMessage.add(MasterService.ICON_WARN + "Error uploading: " + e.getMessage());
                Log.e(TAG, "ERROR uploading data!!!!!", e);
            }
        } else {
            Log.i(TAG, "No records have to be uploaded");
        }

        mRealm.close();
    }

    private void uploadV2() {
        Realm historyRealm;
        historyRealm = Realm.getInstance(UploaderApplication.getHistoryConfiguration());

        List<PumpHistory> history = new ArrayList<>();

        RealmResults<PumpHistoryCGM> records = historyRealm
                .where(PumpHistoryCGM.class)
                .equalTo("uploadREQ", true)
                .findAll();
        for (PumpHistoryCGM record : records) history.add(record);

        RealmResults<PumpHistoryBG> records1 = historyRealm
                .where(PumpHistoryBG.class)
                .equalTo("uploadREQ", true)
                .findAll();
        for (PumpHistoryBG record : records1) history.add(record);

        RealmResults<PumpHistoryBolus> records2 = historyRealm
                .where(PumpHistoryBolus.class)
                .equalTo("uploadREQ", true)
                .findAll();
        for (PumpHistoryBolus record : records2) history.add(record);

        RealmResults<PumpHistoryBasal> records3 = historyRealm
                .where(PumpHistoryBasal.class)
                .equalTo("uploadREQ", true)
                .findAll();
        for (PumpHistoryBasal record : records3) history.add(record);

        RealmResults<PumpHistoryMisc> records4 = historyRealm
                .where(PumpHistoryMisc.class)
                .equalTo("uploadREQ", true)
                .findAll();
        for (PumpHistoryMisc record : records4) history.add(record);

        Log.d(TAG, "*history* records to upload " + history.size());
        Log.d(TAG, "*history* CGM records to upload " + records.size());
        Log.d(TAG, "*history* BG records to upload " + records1.size());
        Log.d(TAG, "*history* BOLUS records to upload " + records2.size());
        Log.d(TAG, "*history* BASAL records to upload " + records3.size());
        Log.d(TAG, "*history* MISC records to upload " + records4.size());


        if (history.size() > 0) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);

            Boolean enableRESTUpload = prefs.getBoolean("EnableRESTUpload", false);

            try {
                if (enableRESTUpload) {
                    long start = System.currentTimeMillis();

                    Log.i(TAG, String.format("Starting upload of %s record using a REST API", history.size()));
                    String urlSetting = prefs.getString(mContext.getString(R.string.preference_nightscout_url), "");
                    String secretSetting = prefs.getString(mContext.getString(R.string.preference_api_secret), "YOURAPISECRET");

                    Boolean uploadSuccess = mNightScoutUploadV2.doRESTUpload(urlSetting,
                            secretSetting, history);
                    if (uploadSuccess) {
                        historyRealm.beginTransaction();
                        for (PumpHistory updateRecord : history) {
                            updateRecord.setUploadACK(true);
                            updateRecord.setUploadREQ(false);
                        }
                        historyRealm.commitTransaction();
                    } else {
                        statusMessage.add(MasterService.ICON_WARN + "*HISTORY* Uploading to Nightscout returned unsuccessful");
                    }
                    Log.i(TAG, String.format("Finished upload of %s record using a REST API in %s ms", history.size(), System.currentTimeMillis() - start));
                }
            } catch (Exception e) {
                statusMessage.add(MasterService.ICON_WARN + "*HISTORY* Error uploading: " + e.getMessage());
                Log.e(TAG, "*HISTORY* ERROR uploading data!!!!!", e);
            }
        } else {
            Log.i(TAG, "*HISTORY* No records have to be uploaded");
        }

        historyRealm.close();
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

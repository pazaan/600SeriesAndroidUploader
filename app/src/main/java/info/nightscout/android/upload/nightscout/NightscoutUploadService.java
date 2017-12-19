package info.nightscout.android.upload.nightscout;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.List;

import info.nightscout.android.R;
import info.nightscout.android.medtronic.PumpHistoryHandler;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.medtronicNg.PumpHistoryInterface;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import io.realm.Realm;
import io.realm.RealmResults;

import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_WARN;
import static info.nightscout.android.utils.ToolKit.getWakeLock;
import static info.nightscout.android.utils.ToolKit.releaseWakeLock;

public class NightscoutUploadService extends Service {
    private static final String TAG = NightscoutUploadService.class.getSimpleName();

    private Context mContext;

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate called");

        mContext = this.getBaseContext();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + "  : " + intent);

        if (intent != null) new Upload().start();

        return START_NOT_STICKY;
    }

    private class Upload extends Thread {
        public void run() {

            PowerManager.WakeLock wl = getWakeLock(mContext, TAG, 60000);

            Realm mRealm = Realm.getDefaultInstance();

            final RealmResults<PumpStatusEvent> statusRecords = mRealm
                    .where(PumpStatusEvent.class)
                    .equalTo("uploaded", false)
                    .findAll();

            PumpHistoryHandler pumpHistoryHandler = new PumpHistoryHandler(mContext);

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

                        boolean uploadSuccess = new NightScoutUpload().doRESTUpload(
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

                        } else userLogMessage(ICON_WARN + "Uploading to nightscout was unsuccessful");

                        Log.i(TAG, String.format("Finished upload of %s record using a REST API in %s ms", records.size(), System.currentTimeMillis() - start));
                    }
                } catch (Exception e) {
                    userLogMessage(ICON_WARN + "Error uploading: " + e.getMessage());
                    Log.e(TAG, "ERROR uploading data!!!!!", e);
                }

            } else {
                Log.i(TAG, "No records have to be uploaded");
            }

            pumpHistoryHandler.close();
            mRealm.close();

            releaseWakeLock(wl);
            stopSelf();
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    protected void userLogMessage(String message) {
        try {
            Intent intent =
                    new Intent(MasterService.Constants.ACTION_USERLOG_MESSAGE)
                            .putExtra(MasterService.Constants.EXTENDED_DATA, message);
            sendBroadcast(intent);
        } catch (Exception ignored) {
        }
    }
}

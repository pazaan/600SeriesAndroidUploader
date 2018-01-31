package info.nightscout.android.upload.nightscout;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.util.List;

import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.PumpHistoryHandler;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.medtronicNg.PumpHistoryInterface;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.store.DataStore;
import io.realm.Realm;
import io.realm.RealmResults;

import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_WARN;
import static info.nightscout.android.utils.ToolKit.getWakeLock;
import static info.nightscout.android.utils.ToolKit.releaseWakeLock;

public class NightscoutUploadService extends Service {
    private static final String TAG = NightscoutUploadService.class.getSimpleName();

    private Context mContext;

    private Realm realm;
    private Realm storeRealm;
    private DataStore dataStore;

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

        if (intent != null) {
            if (startId == 1)
                new Upload().start();
            else {
                Log.d(TAG, "Service already in progress with previous task");
                userLogMessage(ICON_WARN + "Uploading service is busy completing previous task. New records will be uploaded after the next poll.");
            }
        }

        return START_NOT_STICKY;
    }

    private class Upload extends Thread {
        public void run() {

            PowerManager.WakeLock wl = getWakeLock(mContext, TAG, 60000);

            storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
            dataStore = storeRealm.where(DataStore.class).findFirst();

            if (dataStore.isNightscoutUpload()) {
                new NightscoutStatus(mContext).check();
                if (dataStore.isNightscoutAvailable()) uploadRecords();
            }

            storeRealm.close();

            releaseWakeLock(wl);
            stopSelf();
        }
    }

    private void uploadRecords() {

        realm = Realm.getDefaultInstance();
        final RealmResults<PumpStatusEvent> statusRecords = realm
                .where(PumpStatusEvent.class)
                .equalTo("uploaded", false)
                .findAll();
        Log.i(TAG, "Device status records to upload: " + statusRecords.size());

        PumpHistoryHandler pumpHistoryHandler = new PumpHistoryHandler(mContext);
        List<PumpHistoryInterface> records = pumpHistoryHandler.uploadREQ();

        int total = records.size() + statusRecords.size();

        if (total > 0) {

            try {

                long start = System.currentTimeMillis();

                Log.i(TAG, String.format("Starting upload of %s record using a REST API", total));
                String urlSetting = dataStore.getNightscoutURL();
                String secretSetting = dataStore.getNightscoutSECRET();

                int uploaderBatteryLevel = MasterService.getUploaderBatteryLevel();

                new NightScoutUpload().doRESTUpload(
                        storeRealm,
                        dataStore,
                        urlSetting,
                        secretSetting,
                        uploaderBatteryLevel,
                        statusRecords,
                        records);

                pumpHistoryHandler.uploadACK();

                realm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        for (PumpStatusEvent updateRecord : statusRecords)
                            updateRecord.setUploaded(true);
                    }
                });

                if (dataStore.isDbgEnableExtendedErrors())
                    userLogMessage("Uploaded " + total + " records [" + (System.currentTimeMillis() - start) + "ms]");

                Log.i(TAG, String.format("Finished upload of %s record using a REST API in %s ms", total, System.currentTimeMillis() - start));

            } catch (Exception e) {
                Log.e(TAG, "ERROR uploading to Nightscout", e);

                storeRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        dataStore.setNightscoutAvailable(false);                        }
                });

                if (dataStore.isDbgEnableUploadErrors())
                    userLogMessage(ICON_WARN + "Uploading to nightscout was unsuccessful: " + e.getMessage());
            }

        } else {
            Log.i(TAG, "No records have to be uploaded");
        }

        pumpHistoryHandler.close();
        realm.close();
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

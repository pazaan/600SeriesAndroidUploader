package info.nightscout.android.upload.nightscout;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.List;

import info.nightscout.android.UploaderApplication;
import info.nightscout.android.history.PumpHistoryHandler;
import info.nightscout.android.medtronic.Stats;
import info.nightscout.android.medtronic.UserLogMessage;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.medtronicNg.PumpHistoryInterface;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.store.DataStore;
import info.nightscout.android.model.store.StatNightscout;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static info.nightscout.android.utils.ToolKit.getWakeLock;
import static info.nightscout.android.utils.ToolKit.releaseWakeLock;

public class NightscoutUploadService extends Service {
    private static final String TAG = NightscoutUploadService.class.getSimpleName();

    private Context mContext;

    private Realm realm;
    private Realm storeRealm;
    private DataStore dataStore;
    private PumpHistoryHandler pumpHistoryHandler;
    private StatNightscout statNightscout;

    private int worker;

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate called");

        mContext = this.getBaseContext();
        Stats.open();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");

        Stats.close();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + "  : " + intent);

        if (intent != null) {
            if (startId == 1) {
                worker = 1;
                new Upload().start();
            } else {
                worker++;
                Log.i(TAG, "Uploading service already in progress with previous task. Worker count = " + worker);
            }
        }

        return START_NOT_STICKY;
    }

    private class Upload extends Thread {
        public void run() {

            PowerManager.WakeLock wl = getWakeLock(mContext, TAG, 60000);

            storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
            dataStore = storeRealm.where(DataStore.class).findFirst();

            statNightscout = (StatNightscout) Stats.getInstance().readRecord(StatNightscout.class);
            statNightscout.incRun();

            if (dataStore.isNightscoutUpload()) {
                new NightscoutStatus(mContext).check();

                if (dataStore.isNightscoutAvailable()) {
                    realm = Realm.getDefaultInstance();
                    pumpHistoryHandler = new PumpHistoryHandler(mContext);

                    do {
                        uploadRecords();
                    } while (--worker > 0);

                    pumpHistoryHandler.close();
                    realm.close();
                } else {
                    statNightscout.incSiteUnavailable();
                }
            }

            storeRealm.close();

            releaseWakeLock(wl);
            stopSelf();
        }
    }

    private void uploadRecords() {

        RealmResults<PumpStatusEvent> pumpRecords = realm
                .where(PumpStatusEvent.class)
                .sort("eventDate", Sort.ASCENDING)
                .findAll();

        String device = "";
        if (pumpRecords.size() > 0) device = pumpRecords.last().getDeviceName();

        final RealmResults<PumpStatusEvent> statusRecords = pumpRecords
                .where()
                .equalTo("uploaded", false)
                .findAll();
        Log.i(TAG, "Device status records to upload: " + statusRecords.size());

        // attach additional info to the nightscout pump status pill
        String info = pumpHistoryHandler.nightscoutInfo();

        pumpHistoryHandler.processSenderTTL("NS");
        List<PumpHistoryInterface> records = pumpHistoryHandler.getSenderRecordsREQ("NS");

        // rerun the uploader if we hit the limiter for this pass
        if (records.size() == pumpHistoryHandler.getPumpHistorySender().getSender("NS").getLimiter()) {
            Log.i(TAG, "Upload limit hit, another pass scheduled. Worker count = " + worker);
            worker++;
        }

        int total = records.size() + statusRecords.size();

        if (total > 0) {

            try {

                long start = System.currentTimeMillis();

                Log.i(TAG, String.format("Starting process of %s records for upload", total));
                String urlSetting = dataStore.getNightscoutURL();
                String secretSetting = dataStore.getNightscoutSECRET();

                int uploaderBatteryLevel = MasterService.getUploaderBatteryLevel();

                new NightScoutUpload().doRESTUpload(
                        pumpHistoryHandler.getPumpHistorySender(),
                        storeRealm,
                        dataStore,
                        urlSetting,
                        secretSetting,
                        uploaderBatteryLevel,
                        device,
                        info,
                        statusRecords,
                        records);

                pumpHistoryHandler.setSenderRecordsACK(records,"NS");

                realm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {
                        for (PumpStatusEvent updateRecord : statusRecords)
                            updateRecord.setUploaded(true);
                    }
                });

                long timer = System.currentTimeMillis() - start;
                statNightscout.timer(timer);
                statNightscout.settotalRecords(statNightscout.getTotalRecords() + total);

                //UserLogMessage.sendN(mContext, String.format("Uploaded %s records", total));
                UserLogMessage.sendE(mContext, String.format("Uploaded %s records [%sms]", total, timer));

                Log.i(TAG, String.format("Finished upload of %s record using a REST API in %s ms", total, timer));

            } catch (Exception e) {
                Log.e(TAG, "ERROR uploading to Nightscout", e);
                statNightscout.incError();

                // clear the worker count and try again after the next poll
                worker = 0;

                storeRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {
                        dataStore.setNightscoutAvailable(false);
                    }
                });

                if (dataStore.isDbgEnableUploadErrors())
                    UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN,
                            "Uploading to nightscout was unsuccessful: " + e.getMessage());
            }

        } else {
            Log.i(TAG, "No records have to be uploaded");
        }
    }

}

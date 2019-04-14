package info.nightscout.android.upload.nightscout;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.android.R;
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
import io.realm.Sort;

import static info.nightscout.android.history.PumpHistorySender.SENDER_ID_NIGHTSCOUT;
import static info.nightscout.android.medtronic.service.MedtronicCnlService.DEVICE_HEADER;
import static info.nightscout.android.utils.ToolKit.getWakeLock;
import static info.nightscout.android.utils.ToolKit.acquireWakelock;
import static info.nightscout.android.utils.ToolKit.releaseWakeLock;

public class NightscoutUploadService extends Service {
    private static final String TAG = NightscoutUploadService.class.getSimpleName();

    private Context mContext;

    private Realm realm;
    private Realm storeRealm;
    private DataStore dataStore;
    private PumpHistoryHandler pumpHistoryHandler;
    private StatNightscout statNightscout;

    private NightscoutUploadProcess nightscoutUploadProcess;

    private boolean rerun;

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
                new Upload().start();
            } else {
                rerun = true;
                // cancel upload in progress as settings may have changed or recent poll results need priority
                if (nightscoutUploadProcess != null) nightscoutUploadProcess.cancel();
                Log.i(TAG, "Uploading service already in progress with previous task, another pass scheduled.");
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

            pumpHistoryHandler = new PumpHistoryHandler(mContext);

            if (dataStore.isNightscoutUpload()) {
                new NightscoutStatus(mContext).check();

                if (dataStore.isNightscoutAvailable()) {
                    realm = Realm.getDefaultInstance();

                    do {
                        rerun = false;

                        updateDB();
                        uploadRecords();

                        if (rerun) {

                            acquireWakelock(wl, 60000);

                            if (nightscoutUploadProcess != null && !nightscoutUploadProcess.isCancel()) {
                                // cooldown period
                                try {
                                    Thread.sleep(5000);
                                } catch (InterruptedException ignored) {
                                }
                            }

                            // refresh database
                            realm.refresh();
                            storeRealm.refresh();
                            pumpHistoryHandler.refresh();
                        }

                    } while (rerun);

                } else {
                    statNightscout.incSiteUnavailable();
                    updateDB();
                }
            } else {
                updateDB();
            }

            if (pumpHistoryHandler != null) pumpHistoryHandler.close();
            if (realm != null) realm.close();
            if (storeRealm != null) storeRealm.close();

            releaseWakeLock(wl);
            stopSelf();
        }
    }

    // any settings that have changed that require resends need to be handled before/after uploading
    private void updateDB() {
        long now = System.currentTimeMillis();

        pumpHistoryHandler.processSenderTTL(SENDER_ID_NIGHTSCOUT);
        pumpHistoryHandler.checkResendRequests();
        pumpHistoryHandler.runEstimate();

        Log.d(TAG, "updateDB processing took " + (System.currentTimeMillis() - now) + "ms");
    }

    private void uploadRecords() {

        PumpStatusEvent current = realm
                .where(PumpStatusEvent.class)
                .sort("eventDate", Sort.DESCENDING)
                .findFirst();
        String device = current != null
                ? current.getDeviceName()
                : DEVICE_HEADER;

        List<PumpStatusEvent> statusRecords = new ArrayList<>(
                realm.where(PumpStatusEvent.class)
                        .sort("eventDate", Sort.ASCENDING)
                        .equalTo("uploaded", false)
                        .findAll());

        if (statusRecords.size() > 50) {
            statusRecords = statusRecords.subList(statusRecords.size() - 50, statusRecords.size());
            Log.i(TAG, "Process limit reached for status records, another pass scheduled.");
            rerun = true;
        }

        Log.i(TAG, "Device status records to process for upload: " + statusRecords.size());

        List<PumpHistoryInterface> records = pumpHistoryHandler.getSenderRecordsREQ(SENDER_ID_NIGHTSCOUT);

        // rerun the uploader if we hit the limiter for this pass
        if (records.size() >= pumpHistoryHandler.getPumpHistorySender().getSender(SENDER_ID_NIGHTSCOUT).getLimiter()) {
            Log.i(TAG, "Process limit reached for history records, another pass scheduled.");
            rerun = true;
        }
        Log.i(TAG, "History records to process for upload: " + records.size());

        // attach additional info to the nightscout pump status pill
        PumpHistoryHandler.ExtraInfo extraInfo = pumpHistoryHandler.getExtraInfo();

        int total = records.size() + statusRecords.size();

        if (total > 0) {

            try {

                long start = System.currentTimeMillis();

                Log.i(TAG, String.format("Starting process of %s records for upload", total));
                String urlSetting = dataStore.getNightscoutURL();
                String secretSetting = dataStore.getNightscoutSECRET();

                int uploaderBatteryLevel = MasterService.getUploaderBatteryLevel();

                if (nightscoutUploadProcess == null)
                    nightscoutUploadProcess = new NightscoutUploadProcess(urlSetting, secretSetting);

                nightscoutUploadProcess.doRESTUpload(
                        pumpHistoryHandler.getPumpHistorySender(),
                        storeRealm,
                        dataStore,
                        uploaderBatteryLevel,
                        device,
                        extraInfo,
                        statusRecords,
                        records);

                if (!nightscoutUploadProcess.isCancel()) {
                    pumpHistoryHandler.setSenderRecordsACK(records, SENDER_ID_NIGHTSCOUT);

                    final List<PumpStatusEvent> finalStatusRecords = statusRecords;
                    realm.executeTransaction(new Realm.Transaction() {
                        @Override
                        public void execute(@NonNull Realm realm) {
                            for (PumpStatusEvent updateRecord : finalStatusRecords)
                                updateRecord.setUploaded(true);
                        }
                    });

                    long timer = System.currentTimeMillis() - start;
                    statNightscout.timer(timer);
                    statNightscout.settotalRecords(statNightscout.getTotalRecords() + total);

                    UserLogMessage.sendE(mContext, String.format("{id;%s}: {id;%s} %s [%sms]",
                            R.string.ul_share__nightscout, R.string.ul_share__processed, total, timer));
                } else {
                    Log.i(TAG, "Uploading to Nightscout was canceled");
                }

            } catch (Exception e) {
                Log.e(TAG, "ERROR uploading to Nightscout", e);
                statNightscout.incError();

                // Do not rerun, try again after the next poll
                rerun = false;

                storeRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {
                        dataStore.setNightscoutAvailable(false);
                    }
                });

                if (dataStore.isDbgEnableUploadErrors()) {
                    if (e.getMessage().contains("no longer valid"))
                        // An integrity check database reset may have deleted the Realm object
                        // Only show this message as an 'extended error'
                        UserLogMessage.sendE(mContext, UserLogMessage.TYPE.WARN,
                                "Uploading to nightscout was unsuccessful: " + e.getMessage());
                    else
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN,
                                "Uploading to nightscout was unsuccessful: " + e.getMessage());
                }
            }

        } else {
            Log.i(TAG, "No records have to be uploaded");
        }
    }

}

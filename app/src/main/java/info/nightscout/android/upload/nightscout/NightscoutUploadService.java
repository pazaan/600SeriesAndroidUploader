package info.nightscout.android.upload.nightscout;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.annotation.NonNull;
import android.util.Log;

import java.util.List;

import info.nightscout.android.R;
import info.nightscout.android.history.PumpHistoryHandler;
import info.nightscout.android.medtronic.Stats;
import info.nightscout.android.medtronic.UserLogMessage;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.medtronicNg.PumpHistoryInterface;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.store.StatNightscout;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;
import io.realm.exceptions.RealmException;

import static info.nightscout.android.history.PumpHistorySender.SENDER_ID_NIGHTSCOUT;
import static info.nightscout.android.medtronic.service.MedtronicCnlService.DEVICE_HEADER;
import static info.nightscout.android.utils.ToolKit.getWakeLock;
import static info.nightscout.android.utils.ToolKit.acquireWakelock;
import static info.nightscout.android.utils.ToolKit.releaseWakeLock;

public class NightscoutUploadService extends Service {
    private static final String TAG = NightscoutUploadService.class.getSimpleName();

    private Context mContext;

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

            PowerManager.WakeLock wl = getWakeLock(mContext, TAG, 90000);

            pumpHistoryHandler = new PumpHistoryHandler(mContext);

            try {

                statNightscout = (StatNightscout) Stats.getInstance().readRecord(StatNightscout.class);
                statNightscout.incRun();

                if (pumpHistoryHandler.dataStore.isNightscoutUpload()) {
                    new NightscoutStatus(mContext).check();

                    if (pumpHistoryHandler.dataStore.isNightscoutAvailable()) {

                        do {
                            rerun = false;

                            updateDB();
                            uploadRecords();

                            if (rerun) {

                                acquireWakelock(wl, 90000);

                                if (nightscoutUploadProcess != null && !nightscoutUploadProcess.isCancel()) {
                                    // cooldown period
                                    try {
                                        Thread.sleep(
                                                pumpHistoryHandler.dataStore.getNightscoutURL() != null
                                                        && pumpHistoryHandler.dataStore.getNightscoutURL().toLowerCase().contains("azure")
                                                        ? 10000 : 5000);
                                    } catch (InterruptedException ignored) {
                                    }
                                }

                                // refresh database
                                pumpHistoryHandler.refresh();
                            }

                            Log.d(TAG, "rerun = " + rerun);

                        } while (rerun && pumpHistoryHandler.dataStore.isNightscoutUpload());

                    } else {
                        statNightscout.incSiteUnavailable();
                        updateDB();
                    }
                } else {
                    updateDB();
                }

            } catch (RealmException e) {
                Log.e(TAG, "Unexpected Realm Error! " + Log.getStackTraceString(e));
                UserLogMessage.sendE(mContext, UserLogMessage.TYPE.WARN, String.format("{id;%s} %s", R.string.ul_poll__unexpected_error, Log.getStackTraceString(e)));
            } catch (Exception e) {
                Log.e(TAG, "Unexpected Error! " + Log.getStackTraceString(e));
                UserLogMessage.sendE(mContext, UserLogMessage.TYPE.WARN, String.format("{id;%s} %s", R.string.ul_poll__unexpected_error, Log.getStackTraceString(e)));
            }

            pumpHistoryHandler.close();

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

        PumpStatusEvent current = pumpHistoryHandler.realm
                .where(PumpStatusEvent.class)
                .sort("eventDate", Sort.DESCENDING)
                .findFirst();
        final String device = current != null
                ? current.getDeviceName()
                : DEVICE_HEADER;

        RealmResults<PumpStatusEvent> r = pumpHistoryHandler.realm
                .where(PumpStatusEvent.class)
                .sort("eventDate", Sort.ASCENDING)
                .equalTo("uploaded", false)
                .findAll();
        // rerun the uploader if we hit the limiter for this pass
        if (r.size() > 20) {
            Log.i(TAG, "Process limit reached for status records, another pass scheduled.");
            rerun = true;
        }
        final List<PumpStatusEvent> statusRecords = pumpHistoryHandler.realm.copyFromRealm(r);

        Log.i(TAG, "Device status records to process for upload: " + statusRecords.size());

        pumpHistoryHandler.historyRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {

                List<PumpHistoryInterface> historyRecords = pumpHistoryHandler.getSenderRecordsREQ(SENDER_ID_NIGHTSCOUT);

                // rerun the uploader if we hit the limiter for this pass
                if (historyRecords.size() >= pumpHistoryHandler.getPumpHistorySender().getSender(SENDER_ID_NIGHTSCOUT).getLimiter()) {
                    Log.i(TAG, "Process limit reached for history records, another pass scheduled.");
                    rerun = true;
                }
                Log.i(TAG, "History records to process for upload: " + historyRecords.size());

                // attach additional info to the nightscout pump status pill
                PumpHistoryHandler.ExtraInfo extraInfo = pumpHistoryHandler.getExtraInfo();

                int total = historyRecords.size() + statusRecords.size();

                if (total > 0) {

                    try {

                        long start = System.currentTimeMillis();

                        Log.i(TAG, String.format("Starting process of %s records for upload", total));
                        String urlSetting = pumpHistoryHandler.dataStore.getNightscoutURL();
                        String secretSetting = pumpHistoryHandler.dataStore.getNightscoutSECRET();

                        int uploaderBatteryLevel = MasterService.getUploaderBatteryLevel();

                        if (nightscoutUploadProcess == null)
                            nightscoutUploadProcess = new NightscoutUploadProcess(urlSetting, secretSetting);

                        nightscoutUploadProcess.doRESTUpload(
                                pumpHistoryHandler,
                                uploaderBatteryLevel,
                                device,
                                extraInfo,
                                statusRecords,
                                historyRecords);

                        if (!nightscoutUploadProcess.isCancel()) {

                            for (PumpStatusEvent record : statusRecords)
                                record.setUploaded(true);
                            pumpHistoryHandler.realm.executeTransaction(new Realm.Transaction() {
                                @Override
                                public void execute(@NonNull Realm realm) {
                                    realm.copyToRealmOrUpdate(statusRecords);
                                }
                            });

                            pumpHistoryHandler.setSenderRecordACK(historyRecords, SENDER_ID_NIGHTSCOUT);

                            long timer = System.currentTimeMillis() - start;
                            statNightscout.timer(timer);
                            statNightscout.settotalRecords(statNightscout.getTotalRecords() + total);
                            statNightscout.setTotalHttp(statNightscout.getTotalHttp() + nightscoutUploadProcess.getHttpWorkload());

                            UserLogMessage.sendE(mContext, String.format("{id;%s}: {id;%s} %s http %s E:%s/%s/%s T:%s/%s/%s P:%s/%s/%s D:%s C:%s/%s [%sms]",
                                    R.string.ul_share__nightscout, R.string.ul_share__processed, total,
                                    nightscoutUploadProcess.getHttpWorkload(),
                                    nightscoutUploadProcess.getEntriesCheckCount(),
                                    nightscoutUploadProcess.getEntriesDeleteCount(),
                                    nightscoutUploadProcess.getEntriesBulkCount(),
                                    nightscoutUploadProcess.getTreatmentsCheckCount(),
                                    nightscoutUploadProcess.getTreatmentsDeleteCount(),
                                    nightscoutUploadProcess.getTreatmentsBulkCount(),
                                    nightscoutUploadProcess.getProfileCheckCount(),
                                    nightscoutUploadProcess.getProfileDeleteCount(),
                                    nightscoutUploadProcess.getProfileWriteCount(),
                                    nightscoutUploadProcess.getDeviceWriteCount(),
                                    nightscoutUploadProcess.getCheanupCheckCount(),
                                    nightscoutUploadProcess.getCheanupDeleteCount(),
                                    timer));

                        } else {
                            Log.i(TAG, "Uploading to Nightscout was canceled");
                        }

                    } catch (NightscoutException e) {
                        Log.e(TAG, "Nightscout Server Error:", e);
                        statNightscout.incError();

                        // Do not rerun, try again after the next poll
                        rerun = false;

                        pumpHistoryHandler.storeRealm.executeTransaction(new Realm.Transaction() {
                            @Override
                            public void execute(@NonNull Realm realm) {
                                pumpHistoryHandler.dataStore.setNightscoutAvailable(false);
                            }
                        });

                        if (pumpHistoryHandler.dataStore.isDbgEnableUploadErrors()) {
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN,
                                    String.format("{id;%s} %s",
                                            R.string.ul_ns__warn_upload_unsuccessful,
                                            e.getMessage()));
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Exception while processing upload:", e);
                        statNightscout.incError();

                        // Do not rerun, try again after the next poll
                        rerun = false;

                        pumpHistoryHandler.storeRealm.executeTransaction(new Realm.Transaction() {
                            @Override
                            public void execute(@NonNull Realm realm) {
                                pumpHistoryHandler.dataStore.setNightscoutAvailable(false);
                            }
                        });

                        String t[] = Log.getStackTraceString(e).split("at ");

                        UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN,
                                String.format("{id;%s} %s",
                                        R.string.ul_ns__warn_upload_unsuccessful,
                                        t.length < 2 ? e.getMessage() : e.getMessage() + " >>> " +
                                                t[1].replace("info.nightscout.android","").replace("\n", "")));
                        UserLogMessage.sendE(mContext, UserLogMessage.TYPE.WARN, Log.getStackTraceString(e));
                    }

                } else {
                    Log.i(TAG, "No records have to be uploaded");
                }

            }
        });

    }

}

package info.nightscout.android.xdrip_plus;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import info.nightscout.android.R;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.history.PumpHistoryHandler;
import info.nightscout.android.medtronic.UserLogMessage;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.medtronicNg.PumpHistoryCGM;
import info.nightscout.android.model.medtronicNg.PumpHistoryInterface;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.store.DataStore;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static info.nightscout.android.history.PumpHistorySender.SENDER_ID_XDRIP;
import static info.nightscout.android.utils.ToolKit.getWakeLock;
import static info.nightscout.android.utils.ToolKit.releaseWakeLock;

/**
 * Created by jamorham on 17/11/2016.
 */


public class XDripPlusUploadService extends Service {
    private static final String TAG = XDripPlusUploadService.class.getSimpleName();

    private Context mContext;

    private Realm mRealm;
    private Realm storeRealm;
    private DataStore dataStore;

    private SimpleDateFormat ISO8601_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());

    private String device;

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
            else
                Log.d(TAG, "Service already in progress with previous task");
        }

        return START_NOT_STICKY;
    }

    private class Upload extends Thread {
        public void run() {

            PowerManager.WakeLock wl = getWakeLock(mContext, TAG, 60000);

            storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
            dataStore = storeRealm.where(DataStore.class).findFirst();

            if (dataStore.isEnableXdripPlusUpload()) {

                device = "NA";

                mRealm = Realm.getDefaultInstance();
                PumpHistoryHandler pumpHistoryHandler = new PumpHistoryHandler(mContext);

                try {

                    RealmResults<PumpStatusEvent> pumpStatusEvents = mRealm
                            .where(PumpStatusEvent.class)
                            .sort("eventDate", Sort.DESCENDING)
                            .findAll();

                    if (pumpStatusEvents.size() > 0) {
                        device = pumpStatusEvents.first().getDeviceName();
                        doXDripUploadStatus(pumpStatusEvents.first());
                    }

                    List<PumpHistoryInterface> records = pumpHistoryHandler.getSenderRecordsREQ(SENDER_ID_XDRIP);

                    for (PumpHistoryInterface record : records) {
                        if (((PumpHistoryCGM) record).getSgv() > 0) doXDripUploadCGM((PumpHistoryCGM) record, device);
                    }

                    pumpHistoryHandler.setSenderRecordsACK(records, SENDER_ID_XDRIP);

                    if (!dataStore.isXdripPlusUploadAvailable()) {
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.SHARE, String.format("{id;%s} {id;%s}",
                                R.string.ul_share__xdrip, R.string.ul_share__is_available));
                        storeRealm.executeTransaction(new Realm.Transaction() {
                            @Override
                            public void execute(@NonNull Realm realm) {
                                dataStore.setXdripPlusUploadAvailable(true);
                            }
                        });
                    }

                } catch (Exception e) {
                    UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, String.format("{id;%s} {id;%s}",
                            R.string.ul_share__xdrip, R.string.ul_share__is_not_available));
                    storeRealm.executeTransaction(new Realm.Transaction() {
                        @Override
                        public void execute(@NonNull Realm realm) {
                            dataStore.setXdripPlusUploadAvailable(false);
                        }
                    });
                }

                pumpHistoryHandler.close();
                mRealm.close();
            }

            storeRealm.close();

            releaseWakeLock(wl);
            stopSelf();
        }
    }

    private void doXDripUploadCGM(PumpHistoryCGM record, String device) throws Exception {
        JSONArray entriesBody = new JSONArray();
        JSONObject json = new JSONObject();

        json.put("device", device);
        json.put("type", "sgv");
        json.put("date", record.getEventDate().getTime());
        json.put("dateString", record.getEventDate());
        json.put("sgv", record.getSgv());
        String trend = record.getCgmTrend();
        if (trend != null) json.put("direction", PumpHistoryCGM.NS_TREND.valueOf(trend).dexcom().string());

        entriesBody.put(json);
        sendBundle(mContext, "add", "entries", entriesBody);
    }

    private void doXDripUploadStatus(PumpStatusEvent record) throws Exception {
        final JSONArray devicestatusBody = new JSONArray();

        JSONObject json = new JSONObject();
        json.put("uploaderBattery", MasterService.getUploaderBatteryLevel());
        json.put("device", record.getDeviceName());
        json.put("created_at", ISO8601_DATE_FORMAT.format(record.getEventDate()));

        JSONObject pumpInfo = new JSONObject();
        pumpInfo.put("clock", ISO8601_DATE_FORMAT.format(record.getEventDate()));
        pumpInfo.put("reservoir", new BigDecimal(record.getReservoirAmount()).setScale(3, BigDecimal.ROUND_HALF_UP));

        JSONObject iob = new JSONObject();
        iob.put("timestamp", record.getEventDate());
        iob.put("bolusiob", record.getActiveInsulin());

        JSONObject battery = new JSONObject();
        battery.put("percent", record.getBatteryPercentage());

        pumpInfo.put("iob", iob);
        pumpInfo.put("battery", battery);
        json.put("pump", pumpInfo);

        devicestatusBody.put(json);
        sendBundle(mContext, "add", "devicestatus", devicestatusBody);
    }

    private void sendBundle(Context context, String action, String collection, JSONArray json) throws Exception {
        final Bundle bundle = new Bundle();
        bundle.putString("action", action);
        bundle.putString("collection", collection);
        bundle.putString("data", json.toString());

        final Intent intent = new Intent(Constants.XDRIP_PLUS_NS_EMULATOR);
        intent.putExtras(bundle).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);

        List<ResolveInfo> receivers = context.getPackageManager().queryBroadcastReceivers(intent, 0);
        if (receivers.size() < 1) {
            Log.w(TAG, "No xDrip receivers found.");
            throw new Exception("No xDrip receivers found.");
        } else {
            Log.d(TAG, receivers.size() + " xDrip receivers");
        }
    }

    public final class Constants {
        public static final String ACTION_STATUS_MESSAGE = "info.nightscout.android.xdrip_plus.STATUS_MESSAGE";
        public static final String EXTENDED_DATA = "info.nightscout.android.xdrip_plus.DATA";
        private static final String XDRIP_PLUS_NS_EMULATOR = "com.eveningoutpost.dexdrip.NS_EMULATOR";
    }
}

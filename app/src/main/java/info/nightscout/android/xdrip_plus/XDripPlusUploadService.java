package info.nightscout.android.xdrip_plus;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import info.nightscout.android.R;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.PumpHistoryParser;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.medtronicNg.PumpHistoryCGM;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.upload.nightscout.serializer.EntriesSerializer;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static info.nightscout.android.utils.ToolKit.getWakeLock;
import static info.nightscout.android.utils.ToolKit.releaseWakeLock;

/**
 * Created by jamorham on 17/11/2016.
 */


public class XDripPlusUploadService extends Service {
    private static final String TAG = XDripPlusUploadService.class.getSimpleName();

    private Context mContext;
    private static final SimpleDateFormat ISO8601_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());

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

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            Boolean enableXdripPlusUpload = prefs.getBoolean(getString(R.string.preference_enable_xdrip_plus), false);

            if (enableXdripPlusUpload) {

                device = "NA";

                Realm mRealm = Realm.getDefaultInstance();

                Realm historyRealm = Realm.getInstance(UploaderApplication.getHistoryConfiguration());

                final RealmResults<PumpHistoryCGM> history_records = historyRealm
                        .where(PumpHistoryCGM.class)
                        .equalTo("xdripACK", false)
                        .notEqualTo("sgv", 0)
                        .findAllSorted("eventDate", Sort.DESCENDING);

                RealmResults<PumpStatusEvent> records = mRealm
                        .where(PumpStatusEvent.class)
                        .findAllSorted("eventDate", Sort.DESCENDING);

                if (records.size() > 0) {
                    device = records.first().getDeviceName();
                    doXDripUploadStatus(records.first());
                }

                if (history_records.size() > 0) {
                    historyRealm.executeTransaction(new Realm.Transaction() {
                        @Override
                        public void execute(Realm realm) {

                            int limit = 500;
                            for (PumpHistoryCGM history_record : history_records) {
                                doXDripUploadCGM(history_record, device);
                                history_record.setXdripACK(true);
                                if (--limit == 0) break;
                            }
                        }
                    });
                }

                historyRealm.close();
                mRealm.close();
            }

            releaseWakeLock(wl);
            stopSelf();
        }
    }

    private void doXDripUploadCGM(PumpHistoryCGM record, String device) {
        try {
            JSONArray entriesBody = new JSONArray();
            JSONObject json = new JSONObject();

            json.put("device", device);
            json.put("type", "sgv");
            json.put("date", record.getEventDate().getTime());
            json.put("dateString", record.getEventDate());
            json.put("sgv", record.getSgv());
            String trend = record.getCgmTrend();
            if (trend != null) json.put("direction", PumpHistoryParser.TextEN.valueOf("NS_TREND_" + trend).getText());

            entriesBody.put(json);
            sendBundle(mContext, "add", "entries", entriesBody);
        } catch (Exception e) {
            Log.e(TAG, "Unable to send bundle: " + e);
        }
    }

    private void doXDripUploadStatus(PumpStatusEvent record) {
        try {
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
        } catch (Exception e) {
            Log.e(TAG, "Unable to send bundle: " + e);
        }
    }



    private void doXDripUpload(List<PumpStatusEvent> records) {
        try {

            final JSONArray devicestatusBody = new JSONArray();
            final JSONArray entriesBody = new JSONArray();

            for (PumpStatusEvent record : records) {
                addDeviceStatus(devicestatusBody, record);
                addSgvEntry(entriesBody, record);
                addMbgEntry(entriesBody, record);
            }

            if (entriesBody.length() > 0) {
                sendBundle(mContext, "add", "entries", entriesBody);
            }
            if (devicestatusBody.length() > 0) {
                sendBundle(mContext, "add", "devicestatus", devicestatusBody);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to send bundle: " + e);
        }
    }

    private void sendBundle(Context context, String action, String collection, JSONArray json) {
        final Bundle bundle = new Bundle();
        bundle.putString("action", action);
        bundle.putString("collection", collection);
        bundle.putString("data", json.toString());
        final Intent intent = new Intent(Constants.XDRIP_PLUS_NS_EMULATOR);
        intent.putExtras(bundle).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
        List<ResolveInfo> receivers = context.getPackageManager().queryBroadcastReceivers(intent, 0);
        if (receivers.size() < 1) {
            Log.w(TAG, "No xDrip receivers found. ");
        } else {
            Log.d(TAG, receivers.size() + " xDrip receivers");
        }
    }

    private void addDeviceStatus(JSONArray devicestatusArray, PumpStatusEvent record) throws Exception {
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
        //String jsonString = json.toString();

        devicestatusArray.put(json);
    }

    private void addSgvEntry(JSONArray entriesArray, PumpStatusEvent pumpRecord) throws Exception {
        if (pumpRecord.isValidSGV()) {
            JSONObject json = new JSONObject();
            // TODO replace with Retrofit/EntriesSerializer
            json.put("sgv", pumpRecord.getSgv());
            json.put("direction", EntriesSerializer.getDirectionString(pumpRecord.getCgmTrend()));
            json.put("device", pumpRecord.getDeviceName());
            json.put("type", "sgv");
            json.put("date", pumpRecord.getCgmDate().getTime());
            json.put("dateString", pumpRecord.getCgmDate());

            entriesArray.put(json);
        }
    }

    private void addMbgEntry(JSONArray entriesArray, PumpStatusEvent pumpRecord) throws Exception {
        /*
        if (pumpRecord.isValidBGL()) {
            JSONObject json = new JSONObject();

            // TODO replace with Retrofit/EntriesSerializer
            json.put("type", "mbg");
            json.put("mbg", pumpRecord.getRecentBGL());
            json.put("device", pumpRecord.getDeviceName());
            json.put("date", pumpRecord.getEventDate().getTime());
            json.put("dateString", pumpRecord.getEventDate());

            entriesArray.put(json);
        }
        */
    }

    public final class Constants {
        public static final String ACTION_STATUS_MESSAGE = "info.nightscout.android.xdrip_plus.STATUS_MESSAGE";
        public static final String EXTENDED_DATA = "info.nightscout.android.xdrip_plus.DATA";
        private static final String XDRIP_PLUS_NS_EMULATOR = "com.eveningoutpost.dexdrip.NS_EMULATOR";
    }
}

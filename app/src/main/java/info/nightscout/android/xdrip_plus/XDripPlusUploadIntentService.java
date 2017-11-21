package info.nightscout.android.xdrip_plus;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
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

/**
 * Created by jamorham on 17/11/2016.
 */


public class XDripPlusUploadIntentService extends IntentService {

    private static final String TAG = XDripPlusUploadIntentService.class.getSimpleName();
    private static final SimpleDateFormat ISO8601_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());
    Context mContext;

    public XDripPlusUploadIntentService() {
        super(XDripPlusUploadIntentService.class.getName());
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "onCreate called");
        mContext = this.getBaseContext();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent called");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        Boolean enableXdripPlusUpload = prefs.getBoolean(getString(R.string.preference_enable_xdrip_plus), false);

        if (enableXdripPlusUpload) {

            String device = "NA";

            Realm mRealm = Realm.getDefaultInstance();

            Realm historyRealm = Realm.getInstance(UploaderApplication.getHistoryConfiguration());

            RealmResults<PumpHistoryCGM> history_records = historyRealm
                    .where(PumpHistoryCGM.class)
                    .equalTo("xdrip", false)
                    .notEqualTo("sgv", 0)
                    .findAllSorted("eventDate", Sort.ASCENDING);

            RealmResults<PumpStatusEvent> records = mRealm
                    .where(PumpStatusEvent.class)
                    .findAllSorted("eventDate", Sort.DESCENDING);

            if (records.size() > 0) {
                device = records.first().getDeviceName();
                doXDripUploadStatus(records.first());
            }

            if (history_records.size() > 0) {
                historyRealm.beginTransaction();
                for (PumpHistoryCGM history_record : history_records) {
                    doXDripUploadCGM(history_record, device);
                    history_record.setXdrip(true);
                }
                historyRealm.commitTransaction();
            }

            historyRealm.close();
            mRealm.close();
        }

        XDripPlusUploadReceiver.completeWakefulIntent(intent);
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

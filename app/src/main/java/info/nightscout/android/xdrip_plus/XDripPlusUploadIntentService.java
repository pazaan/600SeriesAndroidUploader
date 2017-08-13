package info.nightscout.android.xdrip_plus;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.upload.nightscout.serializer.EntriesSerializer;
import info.nightscout.android.utils.DataStore;
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

    // status unused
    protected void sendStatus(String message) {
        Intent localIntent =
                new Intent(info.nightscout.android.xdrip_plus.XDripPlusUploadIntentService.Constants.ACTION_STATUS_MESSAGE)
                        .putExtra(info.nightscout.android.xdrip_plus.XDripPlusUploadIntentService.Constants.EXTENDED_DATA, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
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
        Realm mRealm = Realm.getDefaultInstance();

        RealmResults<PumpStatusEvent> all_records = mRealm
                .where(PumpStatusEvent.class)
                .notEqualTo("sgv", 0)
                .findAllSorted("eventDate", Sort.DESCENDING);

        // get the most recent record and send that
        if (all_records.size() > 0) {
            List<PumpStatusEvent> records = all_records.subList(0, 1);
            doXDripUpload(records);
        }
        mRealm.close();
        XDripPlusUploadReceiver.completeWakefulIntent(intent);
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
        json.put("uploaderBattery", DataStore.getInstance().getUploaderBatteryLevel());
        json.put("device", record.getDeviceName());
        json.put("created_at", ISO8601_DATE_FORMAT.format(record.getPumpDate()));

        JSONObject pumpInfo = new JSONObject();
        pumpInfo.put("clock", ISO8601_DATE_FORMAT.format(record.getPumpDate()));
        pumpInfo.put("reservoir", new BigDecimal(record.getReservoirAmount()).setScale(3, BigDecimal.ROUND_HALF_UP));

        JSONObject iob = new JSONObject();
        iob.put("timestamp", record.getPumpDate());
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
        JSONObject json = new JSONObject();
        // TODO replace with Retrofit/EntriesSerializer
        json.put("sgv", pumpRecord.getSgv());
        json.put("direction", EntriesSerializer.getDirectionString(pumpRecord.getCgmTrend()));
        json.put("device", pumpRecord.getDeviceName());
        json.put("type", "sgv");
        json.put("date", pumpRecord.getSgvDate().getTime());
        json.put("dateString", pumpRecord.getSgvDate());

        entriesArray.put(json);
    }

    private void addMbgEntry(JSONArray entriesArray, PumpStatusEvent pumpRecord) throws Exception {
        if (pumpRecord.hasRecentBolusWizard()) {
            JSONObject json = new JSONObject();

            // TODO replace with Retrofit/EntriesSerializer
            json.put("type", "mbg");
            json.put("mbg", pumpRecord.getBolusWizardBGL());
            json.put("device", pumpRecord.getDeviceName());
            json.put("date", pumpRecord.getEventDate().getTime());
            json.put("dateString", pumpRecord.getEventDate());

            entriesArray.put(json);
        }
    }


    public final class Constants {
        public static final String ACTION_STATUS_MESSAGE = "info.nightscout.android.xdrip_plus.STATUS_MESSAGE";
        public static final String EXTENDED_DATA = "info.nightscout.android.xdrip_plus.DATA";
        private static final String XDRIP_PLUS_NS_EMULATOR = "com.eveningoutpost.dexdrip.NS_EMULATOR";
    }
}

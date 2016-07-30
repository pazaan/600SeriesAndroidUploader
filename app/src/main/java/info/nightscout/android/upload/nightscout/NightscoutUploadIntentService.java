package info.nightscout.android.upload.nightscout;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.nightscout.android.R;
import info.nightscout.android.medtronic.MainActivity;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.upload.nightscout.serializer.EntriesSerializer;
import io.realm.Realm;
import io.realm.RealmResults;

public class NightscoutUploadIntentService extends IntentService {

    private static final String TAG = NightscoutUploadIntentService.class.getSimpleName();
    private static final SimpleDateFormat ISO8601_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());
    private static final int SOCKET_TIMEOUT = 60 * 1000;
    private static final int CONNECTION_TIMEOUT = 30 * 1000;
    Context mContext;
    private Realm mRealm;

    public NightscoutUploadIntentService() {
        super(NightscoutUploadIntentService.class.getName());
    }

    protected void sendStatus(String message) {
        Intent localIntent =
                new Intent(Constants.ACTION_STATUS_MESSAGE)
                        .putExtra(Constants.EXTENDED_DATA, message);
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
        mRealm = Realm.getDefaultInstance();

        RealmResults<PumpStatusEvent> records = mRealm
                .where(PumpStatusEvent.class)
                .equalTo("uploaded", false)
                .notEqualTo("sgv", 0)
                .findAll();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        Boolean enableRESTUpload = prefs.getBoolean("EnableRESTUpload", false);
        try {
            if (enableRESTUpload) {
                long start = System.currentTimeMillis();
                Log.i(TAG, String.format("Starting upload of %s record using a REST API", records.size()));
                doRESTUpload(prefs, records);
                Log.i(TAG, String.format("Finished upload of %s record using a REST API in %s ms", records.size(), System.currentTimeMillis() - start));
            }
        } catch (Exception e) {
            Log.e(TAG, "ERROR uploading data!!!!!", e);
        }
    }

    private void doRESTUpload(SharedPreferences prefs, RealmResults<PumpStatusEvent> records) {
        String apiScheme = "https://";
        String apiUrl = "";
        String apiSecret = prefs.getString(mContext.getString(R.string.preference_api_secret), "YOURAPISECRET");

        // TODO - this code needs to go to the Settings Activity.
        // Add the extra match for "KEY@" to support the previous single field
        Pattern p = Pattern.compile("(.*\\/\\/)?(.*@)?([^\\/]*)(.*)");
        Matcher m = p.matcher(prefs.getString(mContext.getString(R.string.preference_nightscout_url), ""));

        if (m.find()) {
            apiUrl = m.group(3);

            // Only override apiSecret from URL (the "old" way), if the API secret preference is empty
            if (apiSecret.equals("YOURAPISECRET") || apiSecret.equals("")) {
                apiSecret = (m.group(2) == null) ? "" : m.group(2).replace("@", "");
            }

            // Override the URI scheme if it's been provided in the preference)
            if (m.group(1) != null && !m.group(1).equals("")) {
                apiScheme = m.group(1);
            }
        }

        // Update the preferences to match what we expect. Only really used from converting from the
        // old format to the new format. Aren't we nice for managing backward compatibility?
        prefs.edit().putString(mContext.getString(R.string.preference_api_secret), apiSecret).apply();
        prefs.edit().putString(mContext.getString(R.string.preference_nightscout_url), String.format("%s%s", apiScheme, apiUrl)).apply();

        String uploadUrl = String.format("%s%s@%s/api/v1/", apiScheme, apiSecret, apiUrl);

        try {
            doRESTUploadTo(uploadUrl, records);
        } catch (Exception e) {
            Log.e(TAG, "Unable to do REST API Upload to: " + uploadUrl, e);
        }
    }

    private void doRESTUploadTo(String baseURI, RealmResults<PumpStatusEvent> records) {
        try {
            String baseURL;
            String secret = null;
            String[] uriParts = baseURI.split("@");

            if (uriParts.length == 1) {
                throw new Exception("Starting with API v1, a pass phase is required");
            } else if (uriParts.length == 2) {
                secret = uriParts[0];
                baseURL = uriParts[1];

                // new format URL!
                if (secret.contains("http")) {
                    if (secret.contains("https")) {
                        baseURL = "https://" + baseURL;
                    } else {
                        baseURL = "http://" + baseURL;
                    }
                    String[] uriParts2 = secret.split("//");
                    secret = uriParts2[1];
                }
            } else {
                throw new Exception(String.format("Unexpected baseURI: %s, uriParts.length: %s", baseURI, uriParts.length));
            }

            HttpParams params = new BasicHttpParams();
            HttpConnectionParams.setSoTimeout(params, SOCKET_TIMEOUT);
            HttpConnectionParams.setConnectionTimeout(params, CONNECTION_TIMEOUT);

            DefaultHttpClient httpclient = new DefaultHttpClient(params);

            for (PumpStatusEvent record : records) {
                postDeviceStatus(record, baseURL, httpclient);

                String postURL = baseURL + "entries";

                Log.i(TAG, "postURL: " + postURL);

                HttpPost post = new HttpPost(postURL);

                if (secret == null || secret.isEmpty()) {
                    throw new Exception("Starting with API v1, a pass phase is required");
                } else {
                    MessageDigest digest = MessageDigest.getInstance("SHA-1");
                    byte[] bytes = secret.getBytes("UTF-8");
                    digest.update(bytes, 0, bytes.length);
                    bytes = digest.digest();
                    StringBuilder sb = new StringBuilder(bytes.length * 2);
                    for (byte b : bytes) {
                        sb.append(String.format("%02x", b & 0xff));
                    }
                    String token = sb.toString();
                    post.setHeader("api-secret", token);
                }

                JSONObject json = new JSONObject();

                try {
                    // FIXME - Change this to bulk uploads
                    populateV1APIEntry(json, record);
                } catch (Exception e) {
                    Log.w(TAG, "Unable to populate entry", e);
                    continue;
                }

                String jsonString = json.toString();

                Log.i(TAG, "Upload JSON: " + jsonString);

                try {
                    StringEntity se = new StringEntity(jsonString);
                    post.setEntity(se);
                    post.setHeader("Accept", "application/json");
                    post.setHeader("Content-type", "application/json");

                    ResponseHandler responseHandler = new BasicResponseHandler();
                    httpclient.execute(post, responseHandler);
                } catch (Exception e) {
                    Log.w(TAG, "Unable to post data to: '" + post.getURI().toString() + "'", e);
                }

                // Yay! We uploaded. Tell Realm
                mRealm.beginTransaction();
                // TODO - does realm have auto incrementing keys?
                // Turns out not yet (https://github.com/realm/realm-java/issues/469),
                // but in the meantime, use this: https://gist.github.com/carloseduardosx/a7bd88d7337660cd10a2c5dcc580ebd0
                RealmResults<PumpStatusEvent> updateRecordResults = mRealm
                        .where(PumpStatusEvent.class)
                        .equalTo("eventDate", record.getEventDate())
                        .equalTo("deviceName", record.getDeviceName())
                        .equalTo("sgv", record.getSgv())
                        .findAll();
                // FIXME - We shouldn't need this after we remove insertion of duplicates
                for (PumpStatusEvent updateRecord : updateRecordResults) {
                    updateRecord.setUploaded(true);
                }
                mRealm.commitTransaction();
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to post data", e);
        }
    }

    private void postDeviceStatus(PumpStatusEvent record, String baseURL, DefaultHttpClient httpclient) throws Exception {
        String devicestatusURL = baseURL + "devicestatus";
        Log.i(TAG, "devicestatusURL: " + devicestatusURL);

        JSONObject json = new JSONObject();
        json.put("uploaderBattery", MainActivity.batLevel);
        json.put("device", record.getDeviceName());

        JSONObject pumpInfo = new JSONObject();
        pumpInfo.put("clock", ISO8601_DATE_FORMAT.format(record.getPumpDate()));
        pumpInfo.put("reservoir", record.getReservoirAmount());

        JSONObject iob = new JSONObject();
        iob.put("timestamp", record.getPumpDate());
        iob.put("bolusiob", record.getActiveInsulin());

        JSONObject battery = new JSONObject();
        battery.put("percent", record.getBatteryPercentage());

        pumpInfo.put("iob", iob);
        pumpInfo.put("battery", battery);
        json.put("pump", pumpInfo);
        String jsonString = json.toString();
        Log.i(TAG, "Device Status JSON: " + jsonString);

        HttpPost post = new HttpPost(devicestatusURL);
        StringEntity se = new StringEntity(jsonString);
        post.setEntity(se);
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-type", "application/json");

        ResponseHandler responseHandler = new BasicResponseHandler();
        httpclient.execute(post, responseHandler);
    }

    private void populateV1APIEntry(JSONObject json, PumpStatusEvent pumpRecord) throws Exception {
        // TODO replace with Retrofit/EntriesSerializer
        json.put("sgv", pumpRecord.getSgv());
        json.put("direction", EntriesSerializer.getDirectionString(pumpRecord.getCgmTrend()));
        json.put("device", pumpRecord.getDeviceName());
        json.put("type", "sgv");
        json.put("date", pumpRecord.getEventDate().getTime());
        json.put("dateString", pumpRecord.getEventDate());

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

package info.nightscout.android.upload.nightscout;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.store.DataStore;
import info.nightscout.api.StatusEndpoints;
import info.nightscout.api.UploadApi;
import io.realm.Realm;
import retrofit2.Response;

import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_HELP;
import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_INFO;
import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_WARN;

/**
 * Created by Pogman on 21.1.18.
 */

public class NightscoutStatus {
    private static final String TAG = NightscoutStatus.class.getSimpleName();

    // recommend updating nightscout with versions older then this
    private final int NS_MAJOR = 0;
    private final int NS_MINOR = 10;
    private final int NS_POINT = 2;

    // report schedule
    private final long NS_REPORT = 120 * 60000L;

    private Context mContext;

    private Realm storeRealm;
    private DataStore dataStore;

    private StatusEndpoints statusEndpoints;

    private String ns_name = "";
    private String ns_customTitle = "";
    private String ns_version = "0.0.0";
    private String ns_units = "";
    private String ns_authDefaultRoles = "";
    private String ns_pumpFields = "";
    private String ns_enable = "";
    private boolean ns_careportal = false;
    private boolean ns_devicestatus = false;

    private String ns_version_code = "0.0.0";
    private String ns_version_channel = "";
    private String ns_version_date = "";
    private int ns_version_major = 0;
    private int ns_version_minor = 0;
    private int ns_version_point = 0;

    private boolean available;
    private long reporttime;

    public NightscoutStatus(Context context) {
        mContext = context;
    }

    public void check() {
        long now = System.currentTimeMillis();

        storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
        dataStore = storeRealm.where(DataStore.class).findFirst();

        available = dataStore.isNightscoutAvailable();
        reporttime = dataStore.getNightscoutReportTime();

        if (!isOnline()) {
            available = false;
            if (dataStore.isDbgEnableUploadErrors())
                userLogMessage(ICON_WARN + "Offline / No internet service");

        } else {

            boolean report = true;
            if (reporttime > 0 && now - reporttime < NS_REPORT) report = false;

            if (!ns_authDefaultRoles.contains("readable")
                    || (dataStore.isNsEnableTreatments() && !dataStore.isNightscoutCareportal()))
                available = false;

            if (!available || report) {
                String url = dataStore.getNightscoutURL();
                String secret = dataStore.getNightscoutSECRET();
                available = new getStatus().run(url, secret);

                if (available && dataStore.isDbgEnableUploadErrors()) {

                    if (!dataStore.isNightscoutAvailable()) {
                        userLogMessage(ICON_INFO + "Nightscout site is available");
                    }

                    /* This doesn't seem to be a requirement for newer versions of NS
                    if (!ns_authDefaultRoles.contains("readable")) {
                        userLogMessage(ICON_WARN + "Nightscout 'authDefaultRoles' check has failed, data can not be uploaded.");
                        userLogMessage(ICON_HELP + "Add 'AUTH_DEFAULT_ROLES' and set to 'readable devicestatus-upload' in Azure or Heroku.");
                    }
                    */

                    if (dataStore.isNsEnableTreatments() && !ns_careportal) {
                        userLogMessage(ICON_WARN + "Careportal is not enabled in Nightscout, treatment data can not be uploaded.");
                        userLogMessage(ICON_HELP + "Add 'careportal' to your ENABLE string in Azure or Heroku. Treatments can be disabled in the 'Advanced Nightscout Settings' menu.");
                    }

                    if (report) {
                        reporttime = now;

                        if (dataStore.isDbgEnableExtendedErrors()) {
                            userLogMessage("NS version: " + ns_version);
                        }

                        if (ns_version_major <= NS_MAJOR &&
                                (ns_version_minor < NS_MINOR || (ns_version_minor == NS_MINOR && ns_version_point < NS_POINT))) {
                            userLogMessage(ICON_HELP + "Your version of Nightscout is out of date. It is recommended to use the latest release version of Nightscout with the 600 Series Uploader.");
                        }

                        if (!(ns_enable.contains("PUMP") || ns_enable.contains("pump"))) {
                            userLogMessage(ICON_HELP + "Pump status information can be shown in Nightscout. Add 'pump' to your ENABLE string in Azure or Heroku.");
                        } else {
                            if (!ns_devicestatus) {
                                userLogMessage(ICON_HELP + "Pump status details can be shown in Nightscout. Add 'DEVICESTATUS_ADVANCED' with value 'true' to your strings in Azure or Heroku.");
                            }
                            if (ns_pumpFields.equals("")) {
                                userLogMessage(ICON_HELP + "Pump battery, insulin, delivery modes, calibration and active error alert can be shown in Nightscout. Add 'PUMP_FIELDS' with value 'clock reservoir battery status' to your strings in Azure or Heroku.");
                            } else {
                                if (!(ns_pumpFields.contains("STATUS") || ns_pumpFields.contains("status")))
                                    userLogMessage(ICON_HELP + "Bolus delivery modes, error alerts and calibration time can be shown in Nightscout. Add 'status' to your 'PUMP_FIELDS' string in Azure or Heroku.");
                                if (!(ns_pumpFields.contains("RESERVOIR") || ns_pumpFields.contains("reservoir")))
                                    userLogMessage(ICON_HELP + "Units of insulin remaining in the pump can be shown in Nightscout. Add 'reservoir' to your 'PUMP_FIELDS' string in Azure or Heroku.");
                                if (!(ns_pumpFields.contains("BATTERY") || ns_pumpFields.contains("battery")))
                                    userLogMessage(ICON_HELP + "Pump battery percentage remaining can be shown in Nightscout. Add 'battery' to your 'PUMP_FIELDS' string in Azure or Heroku.");
                                if (!(ns_pumpFields.contains("CLOCK") || ns_pumpFields.contains("clock")))
                                    userLogMessage(ICON_HELP + "Time since last pump reading can shown on Nightscout. Add 'clock' to your 'PUMP_FIELDS' string in Azure or Heroku.");
                            }
                        }

                        if (dataStore.isNsEnableTreatments() && dataStore.isNsEnableSensorChange()
                                && !(ns_enable.contains("SAGE") || ns_enable.contains("sage"))) {
                            userLogMessage(ICON_HELP + "Sensor changes are detected and sent to Nightscout. Add 'sage' to your ENABLE string in Azure or Heroku to see time of last change. This can be disabled in the 'Advanced Nightscout Settings' menu.");
                        }

                        if (dataStore.isNsEnableTreatments() && dataStore.isNsEnableReservoirChange()
                                && !(ns_enable.contains("CAGE") || ns_enable.contains("cage"))) {
                            userLogMessage(ICON_HELP + "Reservoir changes are detected and sent to Nightscout. Add 'cage' to your ENABLE string in Azure or Heroku to see time of last change. This can be disabled in the 'Advanced Nightscout Settings' menu.");
                        }

                        if (dataStore.isNsEnableProfileUpload()
                                && !(ns_enable.contains("PROFILE") || ns_enable.contains("profile"))) {
                            userLogMessage(ICON_HELP + "Basal Profiles are sent to Nightscout. Add 'profile' to your ENABLE string in Azure or Heroku. This can be disabled in the 'Advanced Nightscout Settings' menu.");
                        }

                        if (!(ns_enable.contains("BASAL") || ns_enable.contains("basal"))) {
                            userLogMessage(ICON_HELP + "Basal rates profiles and pattern changes can be shown in Nightscout. Add 'basal' to your ENABLE string in Azure or Heroku.");
                        }

                        if (!(ns_enable.contains("IOB") || ns_enable.contains("iob"))) {
                            userLogMessage(ICON_HELP + "IOB active insulin can be shown in Nightscout. Add 'iob' to your ENABLE string in Azure or Heroku.");
                        }
                    }

                } else if (!available && dataStore.isDbgEnableUploadErrors())
                    userLogMessage(ICON_WARN + "Nightscout site is not available");
            }
        }

        storeRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                dataStore.setNightscoutAvailable(available);
                dataStore.setNightscoutReportTime(reporttime);
                dataStore.setNightscoutCareportal(ns_careportal);
            }
        });

        storeRealm.close();
    }

    private class getStatus extends Thread {
        public boolean run(String url, String secret) {
            boolean available = false;

            try{
                UploadApi uploadApi = new UploadApi(url, formToken(secret));

                statusEndpoints = uploadApi.getStatusEndpoints();

                Response<StatusEndpoints.Status> responseBody = statusEndpoints.getStatus().execute();
                if (!responseBody.isSuccessful())
                    throw new Exception("no response " + responseBody.message());

                if (responseBody.body() == null)
                    throw new Exception("empty status response");

                String s;

                s = responseBody.body().getVersion();
                if (s != null) {
                    ns_version = s;
                    String parts[] = ns_version.split("-");
                    if (parts.length == 3) {
                        ns_version_code = parts[0];
                        ns_version_channel = parts[1];
                        ns_version_date = parts[2];
                    }
                    String code[] = ns_version_code.split("\\.");
                    if (code.length == 3) {
                        try {
                            ns_version_major = Integer.parseInt(code[0]);
                            ns_version_minor = Integer.parseInt(code[1]);
                            ns_version_point = Integer.parseInt(code[2]);
                        } catch (Exception ignored) {
                        }
                    }
                }

                s = responseBody.body().getName();
                if (s != null) ns_name = s;

                if (responseBody.body().isCareportalEnabled() != null) ns_careportal = responseBody.body().isCareportalEnabled();

                if (responseBody.body().getSettings() != null) {

                    s = responseBody.body().getSettings().getCustomTitle();
                    if (s != null) ns_customTitle = s;

                    s = responseBody.body().getSettings().getUnits();
                    if (s != null) ns_units = s;

                    s = responseBody.body().getSettings().getAuthDefaultRoles();
                    if (s != null) ns_authDefaultRoles = s;

                    List<String> enable = responseBody.body().getSettings().getEnable();
                    if (enable != null) {
                        for (String e : enable) {
                            ns_enable += e + " ";
                        }
                    }
                }

                if (responseBody.body().getExtendedSettings() != null) {

                    StatusEndpoints.Pump pump = responseBody.body().getExtendedSettings().getPump();
                    if (pump != null) {
                        s = pump.getFields();
                        if (s != null) ns_pumpFields = s;
                    }

                    StatusEndpoints.Devicestatus devicestatus = responseBody.body().getExtendedSettings().getDevicestatus();
                    if (devicestatus != null) {
                        if (devicestatus.isAdvanced() != null) ns_devicestatus = devicestatus.isAdvanced();
                    }
                }

                available = true;

            } catch (Exception e) {
                Log.e(TAG, "Nightscout status check error", e);
                if (dataStore.isDbgEnableUploadErrors())
                    userLogMessage(ICON_WARN + "Nightscout status is not available: " + e.getMessage());
            }

            return available;
        }
    }

    @NonNull
    private String formToken(String secret) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] bytes = secret.getBytes("UTF-8");
        digest.update(bytes, 0, bytes.length);
        bytes = digest.digest();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
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
            mContext.sendBroadcast(intent);
        } catch (Exception ignored) {
        }
    }

}

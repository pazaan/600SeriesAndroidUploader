package info.nightscout.android.upload.nightscout;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.Date;
import java.util.List;

import info.nightscout.android.R;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.UserLogMessage;
import info.nightscout.android.model.store.DataStore;
import io.realm.Realm;
import retrofit2.Response;

/**
 * Created by Pogman on 21.1.18.
 */

public class NightscoutStatus {
    private static final String TAG = NightscoutStatus.class.getSimpleName();

    // recommend updating nightscout with versions older then this
    private final int NS_MAJOR = 0;
    private final int NS_MINOR = 10;
    private final int NS_POINT = 2;

    // NS v0.11.1 delete handling changed to work as query based
    // regression caused the id based method to be limited to 4 days max causing deletes older then this to not complete
    // Use query based delete handling for NS version >= this
    private final int NS_MAJOR_USE_QUERY = 0;
    private final int NS_MINOR_USE_QUERY = 11;
    private final int NS_POINT_USE_QUERY = 0;

    // Use profile handling for NS version >= this
    private final int NS_MAJOR_USE_PROFILE = 0;
    private final int NS_MINOR_USE_PROFILE = 10;
    private final int NS_POINT_USE_PROFILE = 2;

    // NS version blacklist for compatibility checks
    private final String[] NS_BLACKLIST = {"0.11.0"};

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
    private boolean ns_usequery = false;
    private boolean ns_useprofile = false;

    private String ns_version_code = "0.0.0";
    private String ns_version_channel = "";
    private String ns_version_date = "";
    private int ns_version_major = 0;
    private int ns_version_minor = 0;
    private int ns_version_point = 0;

    private long deviceTime = 0;
    private long serverTime = 0;

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
        ns_careportal = dataStore.isNightscoutCareportal();
        ns_usequery = dataStore.isNightscoutUseQuery();
        ns_useprofile = dataStore.isNightscoutUseProfile();

        if (!UploaderApplication.isOnline()) {
            available = false;
            if (dataStore.isDbgEnableUploadErrors())
                UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN,
                        R.string.ul_share__offline);

        } else {

            boolean report = true;
            if (reporttime > 0 && now - reporttime < NS_REPORT) report = false;

            if (dataStore.isNsEnableTreatments() && !dataStore.isNightscoutCareportal())
                available = false;

            if (!available || report) {
                String url = dataStore.getNightscoutURL();
                String secret = dataStore.getNightscoutSECRET();
                available = new getStatus().run(url, secret);

                if (available) {
                    for (String s : NS_BLACKLIST) {
                        if (s.equals(String.format("%s.%s.%s", ns_version_major, ns_version_minor, ns_version_point))) {
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN,
                                    String.format("{id;%s}: {id;%s} %s",
                                            R.string.ul_share__nightscout, R.string.ul_share__version, ns_version));
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP,
                                    R.string.ul_ns__help_version);
                            available = false;
                            break;
                        }
                    }
                }

                if (available && dataStore.isDbgEnableUploadErrors()) {

                    if (!dataStore.isNightscoutAvailable()) {
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.SHARE, String.format("{id;%s} {id;%s}",
                                R.string.ul_share__nightscout_site, R.string.ul_share__is_available));
                    }

                    if (dataStore.isNsEnableTreatments() && !ns_careportal) {
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN,
                                R.string.ul_ns__warn_careportal);
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP,
                                R.string.ul_ns__help_careportal);
                    }

                    Log.d(TAG, String.format("DEVICE: %s NIGHTSCOUT SERVER: %s DIFFERENCE: %s seconds",
                            new Date(deviceTime).toString(),
                            new Date(serverTime).toString(),
                            (deviceTime - serverTime) / 1000L
                    ));
                    if (Math.abs(serverTime - deviceTime) > 10 * 60000L) {
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN,
                                R.string.ul_ns__warn_servertime);
                        UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP,
                                R.string.ul_ns__help_servertime);
                    }

                    if (report) {
                        reporttime = now;

                        UserLogMessage.sendE(mContext, String.format("{id;%s}: {id;%s} %s",
                                R.string.ul_share__nightscout, R.string.ul_share__version, ns_version));

                        UserLogMessage.sendE(mContext, String.format("{id;%s}: {diff;%s} {date.time;%s}",
                                R.string.ul_share__nightscout,
                                (serverTime - deviceTime) / 1000L,
                                serverTime));

                        if (ns_version_major < NS_MAJOR ||
                                (ns_version_major <= NS_MAJOR && ns_version_minor < NS_MINOR) ||
                                (ns_version_major <= NS_MAJOR && ns_version_minor <= NS_MINOR && ns_version_point < NS_POINT)) {
                            UserLogMessage.sendN(mContext, String.format("{id;%s}: {id;%s} %s",
                                    R.string.ul_share__nightscout, R.string.ul_share__version, ns_version));
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP,
                                    R.string.ul_ns__help_version);
                        }

                        if (!(ns_enable.toLowerCase().contains("pump"))) {
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP,
                                    R.string.ul_ns__help_config_pump);
                        } else {
                            if (!ns_devicestatus) {
                                UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP,
                                        R.string.ul_ns__help_config_devicestatus);
                            }
                            if (ns_pumpFields.equals("")) {
                                UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP,
                                        R.string.ul_ns__help_config_pumpfields);
                            } else {
                                if (!(ns_pumpFields.toLowerCase().contains("status")))
                                    UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP,
                                            R.string.ul_ns__help_config_pumpfields_status);
                                if (!(ns_pumpFields.toLowerCase().contains("reservoir")))
                                    UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP,
                                            R.string.ul_ns__help_config_pumpfields_reservoir);
                                if (!(ns_pumpFields.toLowerCase().contains("battery")))
                                    UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP,
                                            R.string.ul_ns__help_config_pumpfields_battery);
                                if (!(ns_pumpFields.toLowerCase().contains("clock")))
                                    UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP,
                                            R.string.ul_ns__help_config_pumpfields_clock);
                            }
                        }

                        if (dataStore.isNsEnableTreatments() && dataStore.isNsEnableSensorChange()
                                && !(ns_enable.toLowerCase().contains("sage"))) {
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP,
                                    R.string.ul_ns__help_config_sage);
                        }

                        if (dataStore.isNsEnableTreatments() && dataStore.isNsEnableReservoirChange()
                                && !(ns_enable.toLowerCase().contains("cage"))) {
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP,
                                    R.string.ul_ns__help_config_cage);
                        }

                        if (dataStore.isNsEnableTreatments() && dataStore.isNsEnableInsulinChange()
                                && !(ns_enable.toLowerCase().contains("iage"))) {
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP,
                                    R.string.ul_ns__help_config_iage);
                        }

                        if (dataStore.isNsEnableTreatments() && dataStore.isNsEnableBatteryChange()
                                && !(ns_enable.toLowerCase().contains("bage"))) {
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP,
                                    R.string.ul_ns__help_config_bage);
                        }

                        if (dataStore.isNsEnableProfileUpload()
                                && !(ns_enable.toLowerCase().contains("profile"))) {
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP,
                                    R.string.ul_ns__help_config_profile);
                        }

                        if (!(ns_enable.toLowerCase().contains("basal"))) {
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP,
                                    R.string.ul_ns__help_config_basal);
                        }

                        if (!(ns_enable.toLowerCase().contains("iob"))) {
                            UserLogMessage.send(mContext, UserLogMessage.TYPE.HELP,
                                    R.string.ul_ns__help_config_iob);
                        }

                    }
                }

                if (!available && dataStore.isDbgEnableUploadErrors())
                    UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, String.format("{id;%s} {id;%s}",
                            R.string.ul_share__nightscout_site, R.string.ul_share__is_not_available));
            }
        }

        storeRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                dataStore.setNightscoutAvailable(available);
                dataStore.setNightscoutReportTime(reporttime);
                dataStore.setNightscoutCareportal(ns_careportal);
                dataStore.setNightscoutUseQuery(ns_usequery);
                dataStore.setNightscoutUseProfile(ns_useprofile);
            }
        });

        storeRealm.close();
    }

    private class getStatus extends Thread {
        public boolean run(String url, String secret) {
            boolean available = false;

            try{
                UploadApi uploadApi = new UploadApi(url, secret);

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
                    if (parts.length > 0) {
                        ns_version_code = parts[0];
                    }
                    if(parts.length > 1){
                        ns_version_channel = parts[1];
                    }
                    if(parts.length > 2) {
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

                if (ns_version_major < NS_MAJOR_USE_QUERY ||
                        (ns_version_major <= NS_MAJOR_USE_QUERY && ns_version_minor < NS_MINOR_USE_QUERY) ||
                        (ns_version_major <= NS_MAJOR_USE_QUERY && ns_version_minor <= NS_MINOR_USE_QUERY && ns_version_point < NS_POINT_USE_QUERY)) {
                    ns_usequery = false;
                } else {
                    ns_usequery = true;
                }

                if (ns_version_major < NS_MAJOR_USE_PROFILE ||
                        (ns_version_major <= NS_MAJOR_USE_PROFILE && ns_version_minor < NS_MINOR_USE_PROFILE) ||
                        (ns_version_major <= NS_MAJOR_USE_PROFILE && ns_version_minor <= NS_MINOR_USE_PROFILE && ns_version_point < NS_POINT_USE_PROFILE)) {
                    ns_useprofile = false;
                } else {
                    ns_useprofile = true;
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
                        StringBuilder sb = new StringBuilder();
                        for (String e : enable) {
                            sb.append(sb.length() == 0 ? "" : " ");
                            sb.append(e);
                        }
                        ns_enable = sb.toString();
                    }
                }

                if (!ns_enable.toLowerCase().contains("profile"))
                    ns_useprofile = false;

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

                try {
                    if (responseBody.body().getServerTimeEpoch() != null) {
                        serverTime = responseBody.body().getServerTimeEpoch().longValue();
                        deviceTime = System.currentTimeMillis();
                    }
                } catch (Exception ignored) {
                    serverTime = 0;
                    deviceTime = 0;
                }

                available = true;

            } catch (Exception e) {
                Log.e(TAG, "Nightscout status check error", e);
                if (dataStore.isDbgEnableUploadErrors())
                    UserLogMessage.send(mContext, UserLogMessage.TYPE.WARN, String.format("{id;%s}: %s",
                            R.string.ul_share__nightscout_status, e.getMessage()));
            }

            return available;
        }
    }

}

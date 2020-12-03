package info.nightscout.android;

import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.SystemClock;

import androidx.preference.PreferenceManager;
import android.util.Log;

import com.bugfender.sdk.Bugfender;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import info.nightscout.android.model.medtronicNg.PumpHistoryMarker;
import info.nightscout.android.model.medtronicNg.PumpHistorySystem;
import info.nightscout.android.model.medtronicNg.ContourNextLinkInfo;
import info.nightscout.android.model.medtronicNg.PumpHistoryAlarm;
import info.nightscout.android.model.medtronicNg.PumpHistoryBG;
import info.nightscout.android.model.medtronicNg.PumpHistoryBasal;
import info.nightscout.android.model.medtronicNg.PumpHistoryBolus;
import info.nightscout.android.model.medtronicNg.PumpHistoryCGM;
import info.nightscout.android.model.medtronicNg.PumpHistoryDaily;
import info.nightscout.android.model.medtronicNg.PumpHistoryLoop;
import info.nightscout.android.model.medtronicNg.PumpHistoryMisc;
import info.nightscout.android.model.medtronicNg.PumpHistoryPattern;
import info.nightscout.android.model.medtronicNg.PumpHistoryProfile;
import info.nightscout.android.model.medtronicNg.HistorySegment;
import info.nightscout.android.model.medtronicNg.PumpHistorySettings;
import info.nightscout.android.model.medtronicNg.PumpInfo;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.store.DataStore;
import info.nightscout.android.model.store.StatCnl;
import info.nightscout.android.model.store.StatPoll;
import info.nightscout.android.model.store.StatNightscout;
import info.nightscout.android.model.store.StatPushover;
import info.nightscout.android.model.store.UserLog;
import info.nightscout.android.utils.FormatKit;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.annotations.RealmModule;
import uk.co.chrisjenx.calligraphy.CalligraphyConfig;
import androidx.multidex.MultiDexApplication;

/**
 * Created by lgoedhart on 9/06/2016.
 */
public class UploaderApplication extends MultiDexApplication {
    private static final String TAG = UploaderApplication.class.getSimpleName();

    private static RealmConfiguration storeConfiguration;
    private static RealmConfiguration userLogConfiguration;
    private static RealmConfiguration historyConfiguration;

    private static ConnectivityManager connectivityManager;

    private static long startupRealtime;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "onCreate Called");

        startupRealtime = SystemClock.elapsedRealtime();

        CalligraphyConfig.initDefault(new CalligraphyConfig.Builder()
                .setDefaultFontPath("fonts/OpenSans-Regular.ttf")
                .setFontAttrId(R.attr.fontPath)
                .build()
        );

        if (!BuildConfig.DEBUG) {
            try {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

                if (prefs.getBoolean(getString(R.string.key_dbgCrashlytics), getResources().getBoolean(R.bool.default_dbgCrashlytics))) {
                    FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);
                }

                if (prefs.getBoolean(getString(R.string.key_dbgRemoteLogcat), getResources().getBoolean(R.bool.default_dbgRemoteLogcat))) {
                    Bugfender.init(this, BuildConfig.BUGFENDER_API_KEY, BuildConfig.DEBUG);
                    Bugfender.enableLogcatLogging();
                    Bugfender.setDeviceString("NightscoutURL", prefs.getString(getString(R.string.key_nightscoutURL), "Not set"));
                }
            } catch (Exception ignored) {
            }
        }

        Realm.init(this);

        RealmConfiguration realmConfiguration = new RealmConfiguration.Builder()
                .allowWritesOnUiThread(true)
                .modules(new MainModule())
                .deleteRealmIfMigrationNeeded()
                .build();
        Realm.setDefaultConfiguration(realmConfiguration);

        storeConfiguration = new RealmConfiguration.Builder()
                .allowWritesOnUiThread(true)
                .name("store.realm")
                .modules(new StoreModule())
                .deleteRealmIfMigrationNeeded()
                .build();

        userLogConfiguration = new RealmConfiguration.Builder()
                .allowWritesOnUiThread(true)
                .name("userlog.realm")
                .modules(new UserLogModule())
                .deleteRealmIfMigrationNeeded()
                .build();

        historyConfiguration = new RealmConfiguration.Builder()
                .allowWritesOnUiThread(true)
                .name("history.realm")
                .modules(new HistoryModule())
                .deleteRealmIfMigrationNeeded()
                .build();

        // Uploader specific string formatting and localisation formatting accessible from any module
        FormatKit.init(this);

        // Some Android versions will leak if ConnectivityManager not attached to app context here
        connectivityManager = (ConnectivityManager) getApplicationContext()
                .getSystemService(CONNECTIVITY_SERVICE);
    }

    public static boolean isOnline() {
        return getConnectionType() > 0;
    }

    // Returns connection type. 0: none; 1: mobile data; 2: wifi
    public static int getConnectionType() {
        int result = 0;
        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
                if (capabilities != null) {
                    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        result = 2;
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        result = 1;
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        result = 3;
                    }
                }
            }
        } else {
            //  only accesses NetworkInfo on SDK <23 devices
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            if (activeNetwork != null) {
                // connected to the internet
                if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                    result = 2;
                } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                    result = 1;
                } else if (activeNetwork.getType() == ConnectivityManager.TYPE_VPN) {
                    result = 3;
                }
            }
        }
        return result;
    }

    public static long getStartupRealtime() {
        return UploaderApplication.startupRealtime;
    }

    public static long getUptime() {
        return SystemClock.elapsedRealtime() - UploaderApplication.startupRealtime;
    }

    public static RealmConfiguration getStoreConfiguration() {
        return storeConfiguration;
    }

    public static RealmConfiguration getUserLogConfiguration() {
        return userLogConfiguration;
    }

    public static RealmConfiguration getHistoryConfiguration() {
        return historyConfiguration;
    }

    @RealmModule(classes = {
            ContourNextLinkInfo.class,
            PumpInfo.class,
            PumpStatusEvent.class
    })
    private class MainModule {}

    @RealmModule(classes = {
            DataStore.class,
            StatPoll.class,
            StatCnl.class,
            StatNightscout.class,
            StatPushover.class
    })
    private class StoreModule {}

    @RealmModule(classes = {
            UserLog.class
    })
    private class UserLogModule {}

    @RealmModule(classes = {
            HistorySegment.class,
            PumpHistoryCGM.class,
            PumpHistoryBolus.class,
            PumpHistoryBasal.class,
            PumpHistoryBG.class,
            PumpHistoryMisc.class,
            PumpHistoryMarker.class,
            PumpHistoryProfile.class,
            PumpHistoryPattern.class,
            PumpHistorySettings.class,
            PumpHistoryLoop.class,
            PumpHistoryDaily.class,
            PumpHistoryAlarm.class,
            PumpHistorySystem.class
    })
    private class HistoryModule {}

}

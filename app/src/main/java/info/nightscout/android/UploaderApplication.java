package info.nightscout.android;

import android.app.Application;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import com.bugfender.sdk.Bugfender;
import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;

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
import io.fabric.sdk.android.Fabric;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.annotations.RealmModule;
import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

/**
 * Created by lgoedhart on 9/06/2016.
 */
public class UploaderApplication extends Application {
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
                    Fabric.with(this, new Crashlytics());
                }
                if (prefs.getBoolean(getString(R.string.key_dbgAnswers), getResources().getBoolean(R.bool.default_dbgAnswers))) {
                    Fabric.with(this, new Answers(), new Crashlytics());
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
                .modules(new MainModule())
                .deleteRealmIfMigrationNeeded()
                .build();
        Realm.setDefaultConfiguration(realmConfiguration);

        storeConfiguration = new RealmConfiguration.Builder()
                .name("store.realm")
                .modules(new StoreModule())
                .deleteRealmIfMigrationNeeded()
                .build();

        userLogConfiguration = new RealmConfiguration.Builder()
                .name("userlog.realm")
                .modules(new UserLogModule())
                .deleteRealmIfMigrationNeeded()
                .build();

        historyConfiguration = new RealmConfiguration.Builder()
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
        NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
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

package info.nightscout.android;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.bugfender.sdk.Bugfender;
import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;

import info.nightscout.android.model.medtronicNg.BasalRate;
import info.nightscout.android.model.medtronicNg.BasalSchedule;
import info.nightscout.android.model.medtronicNg.ContourNextLinkInfo;
import info.nightscout.android.model.medtronicNg.PumpInfo;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.utils.ServiceStarter;
import info.nightscout.android.utils.StatusStore;
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
    private static Context context;
    private static RealmConfiguration storeConfiguration;

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate Called");
        UploaderApplication.context = getApplicationContext();

        super.onCreate();
        CalligraphyConfig.initDefault(new CalligraphyConfig.Builder()
                .setDefaultFontPath("fonts/OpenSans-Regular.ttf")
                .setFontAttrId(R.attr.fontPath)
                .build()
        );

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        if (prefs.getBoolean(getString(R.string.preferences_enable_crashlytics), true)) {
            Fabric.with(this, new Crashlytics());
        }
        if (prefs.getBoolean(getString(R.string.preferences_enable_answers), true)) {
            Fabric.with(this, new Answers(), new Crashlytics());
        }

        if (prefs.getBoolean(getString(R.string.preferences_enable_remote_logcat), false)) {
            Bugfender.init(this, BuildConfig.BUGFENDER_API_KEY, BuildConfig.DEBUG);
            Bugfender.enableLogcatLogging();
            Bugfender.setDeviceString("NightscoutURL", prefs.getString(getString(R.string.preference_nightscout_url), "Not set"));
        }

        Realm.init(this);

        RealmConfiguration realmConfiguration = new RealmConfiguration.Builder()
                .modules(new MainModule())
                .deleteRealmIfMigrationNeeded()
                .build();
        Realm.setDefaultConfiguration(realmConfiguration);

        storeConfiguration = new RealmConfiguration.Builder()
                .name("storerealm.realm")
                .modules(new StoreModule())
                .deleteRealmIfMigrationNeeded()
                .build();

        new ServiceStarter(getApplicationContext()).start(getApplicationContext());
//        new ServiceStarter(getApplicationContext());
    }

    public static Context getAppContext() {
        return UploaderApplication.context;
    }

    public static boolean checkAppContext(Context context) {
        if (getAppContext() == null) {
            UploaderApplication.context = context;
            return false;
        } else {
            return true;
        }
    }

    public static RealmConfiguration getStoreConfiguration() {
        return storeConfiguration;
    }

    @RealmModule(classes = {BasalRate.class, BasalSchedule.class, ContourNextLinkInfo.class, PumpInfo.class, PumpStatusEvent.class})
    public class MainModule {
    }
    @RealmModule(classes = {StatusStore.class})
    private class StoreModule {
    }

}

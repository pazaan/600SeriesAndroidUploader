package info.nightscout.android;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.bugfender.sdk.Bugfender;
import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;

import io.fabric.sdk.android.Fabric;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

/**
 * Created by lgoedhart on 9/06/2016.
 */
public class UploaderApplication extends Application {
    @Override
    public void onCreate() {
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
                .deleteRealmIfMigrationNeeded()
                .build();

        Realm.setDefaultConfiguration(realmConfiguration);
    }
}

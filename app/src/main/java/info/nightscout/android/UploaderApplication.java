package info.nightscout.android;

import android.app.Application;

import com.bugfender.sdk.Bugfender;

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

        //Bugfender.init(this, "TSwASSlwqOGXFb86RadI7EEJZSr2jGeT", BuildConfig.DEBUG);
        //Bugfender.enableLogcatLogging();

        RealmConfiguration realmConfiguration = new RealmConfiguration.Builder(this).build();
        Realm.setDefaultConfiguration(realmConfiguration);
    }
}

package info.nightscout.android.medtronic;

import android.util.Log;

import info.nightscout.android.UploaderApplication;
import info.nightscout.android.model.store.UserLog;
import io.realm.Realm;
import io.realm.RealmResults;

public class UserLogMessage {
    private static final String TAG = UserLogMessage.class.getSimpleName();
    private static UserLogMessage instance;

    private static final long STALE_MS = 72 * 60 * 60 * 1000L;

    private UserLogMessage() {
    }

    public static UserLogMessage getInstance() {
        if (UserLogMessage.instance == null) {
            instance = new UserLogMessage();
        }
        return instance;
    }

    private void addMessage(final String message) {
        Log.d(TAG, "addMessage: " + message);

        final Realm userLogRealm = Realm.getInstance(UploaderApplication.getUserLogConfiguration());

        userLogRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {

                userLogRealm.createObject(UserLog.class).UserLogMessage(message);

                // remove stale items
                RealmResults results = userLogRealm.where(UserLog.class)
                        .lessThan("timestamp", System.currentTimeMillis() - STALE_MS)
                        .findAll();
                if (results.size() > 0) results.deleteAllFromRealm();
            }
        });

        userLogRealm.close();
    }

    public void add(String message) {
        addMessage(message);
    }

    public void clear() {
        final Realm userLogRealm = Realm.getInstance(UploaderApplication.getUserLogConfiguration());

        userLogRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                userLogRealm.deleteAll();
            }
        });

        userLogRealm.close();
    }

    public final class Icons {
        // TODO - use a message type and insert icon as part of ui user log message handling
        public static final String ICON_WARN = "{ion_alert_circled} ";
        public static final String ICON_BGL = "{ion_waterdrop} ";
        public static final String ICON_USB = "{ion_usb} ";
        public static final String ICON_INFO = "{ion_information_circled} ";
        public static final String ICON_HELP = "{ion_ios_lightbulb} ";
        public static final String ICON_SETTING = "{ion_android_settings} ";
        public static final String ICON_HEART = "{ion_heart} ";
        public static final String ICON_LOW = "{ion_battery_low} ";
        public static final String ICON_FULL = "{ion_battery_full} ";
        public static final String ICON_CGM = "{ion_ios_pulse_strong} ";
        public static final String ICON_SUSPEND = "{ion_pause} ";
        public static final String ICON_RESUME = "{ion_play} ";
        public static final String ICON_BOLUS = "{ion_skip_forward} ";
        public static final String ICON_BASAL = "{ion_skip_forward} ";
        public static final String ICON_CHANGE = "{ion_android_hand} ";
        public static final String ICON_REFRESH = "{ion_loop} ";
        public static final String ICON_BELL = "{ion_android_notifications} ";
        public static final String ICON_NOTE = "{ion_clipboard} ";
    }
}
package info.nightscout.android.model.store;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;

import info.nightscout.android.UploaderApplication;
import io.realm.RealmObject;
import io.realm.annotations.Index;

import static info.nightscout.android.medtronic.MainActivity.MMOLXLFACTOR;
import static org.apache.commons.lang3.math.NumberUtils.toInt;

public class UserLog extends RealmObject {
    @Index
    private long timestamp;
    private String message;

    public void UserLogMessage(String message) {
        UserLogMessage(System.currentTimeMillis(), message);
    }

    public void UserLogMessage(long timestamp, String message) {
        this.timestamp = timestamp;
        this.message = message;
    }

    public String toString() {
        DateFormat df = new SimpleDateFormat("E HH:mm:ss");
        String split[] = message.split("¦");
        if (split.length == 2)
            return df.format(timestamp) + ": " + split[0] + strFormatSGV(toInt(split[1]));
        else if (split.length == 3)
            return df.format(timestamp) + ": " + split[0] + strFormatSGV(toInt(split[1])) + split[2];
        else
            return df.format(timestamp) + ": " + message;
    }

   private String strFormatSGV(double sgvValue) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(UploaderApplication.getAppContext());
        boolean isMmolxl = prefs.getBoolean("mmolxl", false);
        boolean isMmolxlDecimals = prefs.getBoolean("mmolDecimals", false);

        NumberFormat sgvFormatter;
        if (isMmolxl) {
            if (isMmolxlDecimals) {
                sgvFormatter = new DecimalFormat("0.00");
            } else {
                sgvFormatter = new DecimalFormat("0.0");
            }
            return sgvFormatter.format(sgvValue / MMOLXLFACTOR);
        } else {
            sgvFormatter = new DecimalFormat("0");
            return sgvFormatter.format(sgvValue);
        }
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

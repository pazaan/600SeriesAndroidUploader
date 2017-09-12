package info.nightscout.android.utils;

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

public class StatusStore extends RealmObject {
    @Index
    private long timestamp;
    private String message;

    public void StatusMessage(String message) {
        StatusMessage(System.currentTimeMillis(), message);
    }

    public void StatusMessage(long timestamp, String message) {
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

}

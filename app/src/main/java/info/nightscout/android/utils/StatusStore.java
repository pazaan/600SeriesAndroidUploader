package info.nightscout.android.utils;

import android.text.format.DateUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.realm.RealmObject;
import io.realm.annotations.Index;

/**
 * Created by Pogman on 22.7.17.
 */

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
        return df.format(timestamp) + ": " + message;

/*
        if (DateUtils.isToday(timestamp)) {
            DateFormat df = new SimpleDateFormat("h:mm:ss a");
            return df.format(timestamp) + ": " + message;
        } else {
            DateFormat df = new SimpleDateFormat("E h:mm:ss a");
            return df.format(timestamp) + ": " + message;
        }
*/
    }
}

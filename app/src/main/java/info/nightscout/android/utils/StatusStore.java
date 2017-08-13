package info.nightscout.android.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import io.realm.RealmObject;
import io.realm.annotations.Index;

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
    }
}

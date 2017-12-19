package info.nightscout.android.model.store;

import io.realm.RealmObject;
import io.realm.annotations.Index;

public class UserLog extends RealmObject {
    @Index
    public long timestamp;
    public String message;

    public void UserLogMessage(String message) {
        UserLogMessage(System.currentTimeMillis(), message);
    }

    public void UserLogMessage(long timestamp, String message) {
        this.timestamp = timestamp;
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }
}

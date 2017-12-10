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
                RealmResults results = userLogRealm.where(UserLog.class)
                        .findAll();
                if (results.size() > 0) results.deleteAllFromRealm();
            }
        });

        userLogRealm.close();
    }
}
package info.nightscout.android.utils;

import android.util.Log;

import info.nightscout.android.UploaderApplication;
import io.realm.Realm;
import io.realm.RealmResults;

public class StatusMessage {
    private static final String TAG = StatusMessage.class.getSimpleName();
    private static StatusMessage instance;

    private static final int STALE_MS = 72 * 60 * 60 * 1000;

    private StatusMessage() {
    }

    public static StatusMessage getInstance() {
        if (StatusMessage.instance == null) {
            instance = new StatusMessage();
        }
        return instance;
    }

    private void addMessage(final String message) {
        Log.d(TAG, "addMessage: " + message);

        Realm storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());

        storeRealm.beginTransaction();
        storeRealm.createObject(StatusStore.class).StatusMessage(message);

        // remove stale items
        RealmResults results = storeRealm.where(StatusStore.class)
                .lessThan("timestamp", System.currentTimeMillis() - STALE_MS)
                .findAll();
        if (results.size() > 0) results.deleteAllFromRealm();

        storeRealm.commitTransaction();
        storeRealm.close();
    }

    public void add(String message) {
        addMessage(message);
    }

    public void clear() {
        Realm storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());

        final RealmResults results = storeRealm.where(StatusStore.class)
                .findAll();
        if (results.size() > 0) {
            storeRealm.executeTransaction(
                    new Realm.Transaction() {
                        @Override
                        public void execute(Realm realm) {
                            results.deleteAllFromRealm();
                        }
                    });
        }

        storeRealm.close();
    }

}
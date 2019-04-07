package info.nightscout.android.medtronic;

import android.support.annotation.NonNull;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import info.nightscout.android.UploaderApplication;
import info.nightscout.android.model.store.StatCnl;
import info.nightscout.android.model.store.StatInterface;
import info.nightscout.android.model.store.StatPoll;
import info.nightscout.android.model.store.StatNightscout;
import info.nightscout.android.model.store.StatPushover;
import io.realm.Realm;
import io.realm.RealmResults;

// thread safe: no open Realm refs held, db objects are read and held locally and only write back to Realm when the open count is zero
// keep writable record classes associated to the process they live in, records will not be global until fully closed or a write requested

public class Stats {
    private static final String TAG = Stats.class.getSimpleName();

    public static final Class[] STAT_CLASSES = new Class[] {StatPoll.class, StatCnl.class, StatNightscout.class, StatPushover.class};
    private static final long STAT_STALE = 30 * 24 * 60 * 60000L;

    public static final SimpleDateFormat sdfDateToKey = new SimpleDateFormat("yyyyMMdd", Locale.US);

    private int open;
    private List<LoadedRecord> loadedRecords = new ArrayList<>();

    private class LoadedRecord {
        private StatInterface statRecord;
        private boolean isWrite;
        LoadedRecord(StatInterface statRecord, boolean isWrite) {
            this.statRecord = statRecord;
            this.isWrite = isWrite;
        }
    }

    private Stats() {
        Log.d(TAG, "init called [Pid=" + android.os.Process.myPid() + "]");
    }

    private static class LazyHolder {
        static final Stats instance = new Stats();

        static Stats open() {
            synchronized (LazyHolder.class) {
                Log.d(TAG, "open called [open=" + LazyHolder.instance.open + "]" + " rec: " + LazyHolder.instance.loadedRecords.size());
                instance.open++;
            }
            return instance;
        }

        static void close() {
            synchronized (LazyHolder.class) {
                instance.open--;
                Log.d(TAG, "close called [open=" + LazyHolder.instance.open + "]" + " rec: " + LazyHolder.instance.loadedRecords.size());
                if (instance.open < 1) {
                    instance.writeRecords();
                    instance.loadedRecords.clear();
                    instance.open = 0;
                }
            }
        }
    }

    public static Stats getInstance() {
        return LazyHolder.instance;
    }

    public static Stats open() {
        return LazyHolder.open();
    }

    public static void close() {
        LazyHolder.close();
    }

    public static int opened() {
        return LazyHolder.instance.open;
    }

    public StatInterface readRecord(Class clazz) {
        return readRecords(new Class[]{clazz}, sdfDateToKey.format(System.currentTimeMillis()), true)[0];
    }

    public StatInterface readRecord(Class clazz, boolean isWrite) {
        return readRecords(new Class[]{clazz}, sdfDateToKey.format(System.currentTimeMillis()), isWrite)[0];
    }

    public StatInterface readRecord(Class clazz, String key, boolean isWrite) {
        return readRecords(new Class[]{clazz}, key, isWrite)[0];
    }

    public StatInterface[] readRecords(Class[] classes) {
        return readRecords(classes, sdfDateToKey.format(System.currentTimeMillis()), true);
    }

    public StatInterface[] readRecords(Class[] classes, String key, boolean isWrite) {
        Realm storeRealm = null;
        StatInterface[] loaded = new StatInterface[classes.length];

        int i = 0;
        for (Class clazz : classes) {
            boolean load = true;

            for (LoadedRecord loadedRecord : loadedRecords) {
                if (loadedRecord.statRecord.getClass().equals(clazz) && loadedRecord.statRecord.getKey().equals(key)) {
                    loadedRecord.isWrite |= isWrite;
                    loaded[i++] = loadedRecord.statRecord;
                    load = false;
                    break;
                }
            }

            if (load) {
                if (storeRealm == null)
                    storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());

                StatInterface record = (StatInterface) storeRealm.where(clazz)
                        .equalTo("key", key)
                        .findFirst();

                if (record == null) {
                    Log.d(TAG, String.format("create: %s key: %s write: %s", clazz.getSimpleName(), key, isWrite));
                    try {
                        record = (StatInterface) clazz.getConstructor().newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException(TAG + " could not construct new class");
                    }
                    record.setKey(key);
                    record.setDate(new Date(System.currentTimeMillis()));
                } else {
                    Log.d(TAG, String.format("read: %s key: %s write: %s", clazz.getSimpleName(), key, isWrite));
                    record = storeRealm.copyFromRealm(record);
                }

                loadedRecords.add(new LoadedRecord(record, isWrite));
                loaded[i++] = record;
            }
        }

        if (storeRealm != null) storeRealm.close();
        return loaded;
    }

    private void writeRecords() {
        if (loadedRecords != null && loadedRecords.size() > 0) {
            final Realm storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());

            try {

                storeRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {
                        for (LoadedRecord loadedRecord : loadedRecords) {
                            if (loadedRecord.isWrite) {
                                if (open < 1) loadedRecord.isWrite = false;
                                storeRealm.copyToRealmOrUpdate(loadedRecord.statRecord);
                                Log.d(TAG, String.format("write: %s key: %s", loadedRecord.statRecord.getClass().getSimpleName(), loadedRecord.statRecord.getKey()));
                            }
                        }
                    }
                });

            } catch (Exception e) {
                Log.w(TAG, "Stats write records, Realm task could not complete");
            }

            storeRealm.close();
        }
    }

    public String toString(Class clazz) {
        return toString(new Class[]{clazz});
    }

    public String toString(Class[] classes) {
        StringBuilder sb = new StringBuilder();
        StatInterface[] loaded = readRecords(classes, sdfDateToKey.format(System.currentTimeMillis()), false);
        for (StatInterface record : loaded) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(record.toString());
        }
        return sb.toString();
    }

    public static void stale() {
        if (LazyHolder.instance.open > 0) Log.w(TAG, "stale called while stats are open [open=" + LazyHolder.instance.open + "]");

        long now = System.currentTimeMillis();

        final Date staledate = new Date(now - STAT_STALE);
        final Class[] classes = STAT_CLASSES;

        final Realm storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());

        try {

            storeRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {

                    int count = 0;
                    for (Class clazz : classes) {

                        RealmResults stale = storeRealm.where(clazz)
                                .lessThan("date", staledate)
                                .findAll();

                        count += stale.size();
                        stale.deleteAllFromRealm();

                    }

                    if (count > 0) Log.d(TAG, "deleted " + count + " stale records");
                }
            });

        } catch (Exception e) {
            Log.w(TAG, "Stats stale cleanup, Realm task could not complete");
        }

        storeRealm.close();
    }

    public static String report(Date date) {
        return report(sdfDateToKey.format(date));
    }

    public static String report(String key) {
        if (LazyHolder.instance.open > 0) Log.w(TAG, "report called while stats are open [open=" + LazyHolder.instance.open + "]");

        StringBuilder sb = new StringBuilder();

        Class[] classes = STAT_CLASSES;

        Realm storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());

        for (Class clazz : classes) {
            StatInterface record = (StatInterface) storeRealm.where(clazz)
                    .equalTo("key", key)
                    .findFirst();
            if (record != null) {
                sb.append(sb.length() > 0 ? " [" : "[");
                sb.append(clazz.getSimpleName());
                sb.append("] ");
                sb.append(record.toString());
            }
        }

        storeRealm.close();
        return sb.toString();
    }

}

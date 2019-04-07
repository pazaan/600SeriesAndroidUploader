package info.nightscout.android.medtronic;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.store.UserLog;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

public class UserLogMessage {
    private static final String TAG = UserLogMessage.class.getSimpleName();
    private static UserLogMessage instance;

    private static final int MESSAGES_MAX = 10000;

    private static long index;

    private UserLogMessage() {
        Log.d(TAG, "UserLogMessage: init called [Pid=" + android.os.Process.myPid() + "]");
    }

    private static class LazyHolder {
        static final UserLogMessage instance = new UserLogMessage();
    }

    public static UserLogMessage getInstance() {
        return LazyHolder.instance;
    }

    private void addMessage(final boolean async, final long timestamp, final TYPE type, final FLAG flag, final String message) {
        Log.d(TAG, "addMessage: " + message);

        Realm userLogRealm = Realm.getInstance(UploaderApplication.getUserLogConfiguration());

        if (index == 0) {
            Number last = userLogRealm.where(UserLog.class).max("index");
            index = last == null ? 1 : last.longValue() + 1;
        }
        final long i = index++;

        try {
            if (async) {
                userLogRealm.executeTransactionAsync(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {
                        realm.copyToRealmOrUpdate(new UserLog().message(i, timestamp, type.value(), flag.value(), message));
                    }
                });
            } else {
                userLogRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(@NonNull Realm realm) {
                        realm.copyToRealmOrUpdate(new UserLog().message(i, timestamp, type.value(), flag.value(), message));
                    }
                });
            }
        } catch (Exception e) {
            // rare, can throw when there are too many messages to be handled using concurrent async realm processes
            Log.e(TAG, "Could not add message: " + e.getMessage());
        }

        userLogRealm.close();
    }

    public void stale() {
        final Realm userLogRealm = Realm.getInstance(UploaderApplication.getUserLogConfiguration());

        try {
            userLogRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    // remove stale items
                    RealmResults results = realm.where(UserLog.class)
                            .findAll();
                    if (results.size() > MESSAGES_MAX) {
                        int count = results.size() - MESSAGES_MAX;
                        results.where()
                                .sort("index", Sort.ASCENDING)
                                .limit(count)
                                .findAll()
                                .deleteAllFromRealm();
                        Log.d(TAG, String.format("removed %s stale items", count));
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Could not remove stale messages: " + e.getMessage());
        }

        userLogRealm.close();
    }

    public void clear() {
        Realm userLogRealm = Realm.getInstance(UploaderApplication.getUserLogConfiguration());

        try {
            userLogRealm.executeTransactionAsync(new Realm.Transaction() {
                @Override
                public void execute(@NonNull Realm realm) {
                    realm.deleteAll();
                }
            });
            index = 0;
        } catch (Exception e) {
            Log.e(TAG, "Could not clear messages: " + e.getMessage());
        }

        userLogRealm.close();
    }

    public void add(String message) {
        add(TYPE.NA, FLAG.NA, message);
    }

    public void add(TYPE type, String message) {
        add(type, FLAG.NA, message);
    }

    public void add(int id) {
        add(TYPE.NA, FLAG.NA, String.format("{id;%s}", id));
    }

    public void add(TYPE type, int id) {
        add(type, String.format("{id;%s}", id));
    }

    public void add(TYPE type, FLAG flag, String message) {
        addMessage(false, System.currentTimeMillis(), type, flag, message);
    }

    public void addAsync(TYPE type, FLAG flag, String message) {
        addMessage(true, System.currentTimeMillis(), type, flag, message);
    }

    public static void send(Context context, String message) {
        send(context, TYPE.NA, FLAG.NA, message);
    }

    public static void send(Context context, TYPE type, String message) {
        send(context, type, FLAG.NA, message);
    }

    public static void send(Context context, int id) {
        send(context, TYPE.NA, FLAG.NA, String.format("{id;%s}", id));
    }

    public static void send(Context context, TYPE type, int id) {
        send(context, type, FLAG.NA, String.format("{id;%s}", id));
    }

    public static void sendN(Context context, String message) {
        send(context, TYPE.NA, FLAG.NORMAL, message);
    }

    public static void sendN(Context context, TYPE type, String message) {
        send(context, type, FLAG.NORMAL, message);
    }

    public static void sendN(Context context, TYPE type, int id) {
        send(context, type, FLAG.NORMAL, String.format("{id;%s}", id));
    }

    public static void sendE(Context context, String message) {
        send(context, TYPE.NA, FLAG.EXTENDED, message);
    }

    public static void sendE(Context context, TYPE type, String message) {
        send(context, type, FLAG.EXTENDED, message);
    }

    public static void sendE(Context context, TYPE type, int id) {
        send(context, type, FLAG.EXTENDED, String.format("{id;%s}", id));
    }

    private static void send(Context context, TYPE type, FLAG flag, String message) {
        try {
            Intent intent =
                    new Intent(MasterService.Constants.ACTION_USERLOG_MESSAGE)
                            .putExtra("type", type)
                            .putExtra("flag", flag)
                            .putExtra("message", message);
            context.sendBroadcast(intent);
        } catch (Exception ignored) {
        }
    }

    public enum TYPE {
        WARN(1),
        INFO(2),
        HELP(3),
        OPTION(4),
        CGM(5),
        SGV(6),
        HISTORY(7),
        HEART(8),
        ESTIMATE(9),
        NOTE(10),
        REQUESTED(11),
        RECEIVED(12),
        SHARE(13),
        STARTUP(14),
        SHUTDOWN(15),
        PUSHOVER(16),
        NIGHTSCOUT(17),
        ISIG(18),
        NA(-1);

        private int value;

        TYPE(int value) {
            this.value = value;
        }

        public int value() {
            return this.value;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static TYPE convert(int value) {
            for (TYPE type : TYPE.values())
                if (type.value == value) return type;
            return TYPE.NA;
        }
    }

    public enum FLAG {
        NORMAL(1),
        EXTENDED(2),
        DEBUG(3),
        NA(-1);

        private int value;

        FLAG(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) this.value;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static FLAG convert(byte value) {
            for (FLAG flag : FLAG.values())
                if (flag.value == value) return flag;
            return FLAG.NA;
        }
    }

}
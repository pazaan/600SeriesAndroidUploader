package info.nightscout.android.pushover;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import info.nightscout.android.UploaderApplication;
import info.nightscout.android.history.MessageItem;
import info.nightscout.android.history.PumpHistoryHandler;
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.medtronicNg.PumpHistoryInterface;
import info.nightscout.android.model.store.DataStore;
import io.realm.Realm;
import okhttp3.Headers;
import retrofit2.Response;

import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_INFO;
import static info.nightscout.android.medtronic.UserLogMessage.Icons.ICON_WARN;
import static info.nightscout.android.utils.ToolKit.getWakeLock;
import static info.nightscout.android.utils.ToolKit.releaseWakeLock;

public class PushoverUploadService extends Service {
    private static final String TAG = PushoverUploadService.class.getSimpleName();

    private final String url = "https://api.pushover.net/";

    private Context mContext;

    private Realm storeRealm;
    private DataStore dataStore;

    PumpHistoryHandler pumpHistoryHandler;

    PushoverApi pushoverApi;

    private boolean valid;
    private String apiToken;
    private String userToken;
    private int messagesSent;
    private int appLimit;
    private int appRemaining;
    private long appReset;

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate called");

        mContext = this.getBaseContext();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + "  : " + intent);

        if (intent != null) {
            if (startId == 1)
                new Upload().start();
            else {
                Log.d(TAG, "Service already in progress with previous task");
                //userLogMessage(ICON_WARN + "Uploading service is busy completing previous task. New records will be uploaded after the next poll.");
            }
        }

        return START_NOT_STICKY;
    }

    private class Upload extends Thread {
        public void run() {

            PowerManager.WakeLock wl = getWakeLock(mContext, TAG, 60000);

            storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
            dataStore = storeRealm.where(DataStore.class).findFirst();

            if (UploaderApplication.isOnline() && dataStore.isPushoverEnable()) {
                pushoverApi = new PushoverApi(url);
                if (isValid()) process();
            }

            storeRealm.close();

            releaseWakeLock(wl);
            stopSelf();
        }
    }

    protected void userLogMessage(String message) {
        try {
            Intent intent =
                    new Intent(MasterService.Constants.ACTION_USERLOG_MESSAGE)
                            .putExtra(MasterService.Constants.EXTENDED_DATA, message);
            mContext.sendBroadcast(intent);
        } catch (Exception ignored) {
        }
    }

    public boolean isValid() {

        valid = dataStore.isPushoverValidated();
        apiToken = dataStore.getPushoverAPItoken();
        userToken = dataStore.getPushoverUSERtoken();
        String apiCheck = dataStore.getPushoverAPItokenCheck();
        String userCheck = dataStore.getPushoverUSERtokenCheck();

        try {

            if (!valid && apiToken.equals(apiCheck) && userToken.equals(userCheck)) {
                userLogMessage(ICON_WARN + "Pushover: validation failed. Check that your Pushover account is active and your account settings are correct.");
                throw new Exception("account error");
            }

            else if (valid && !(apiToken.equals(apiCheck) && userToken.equals(userCheck)))
                valid = false;

            if (!valid) {
                if (apiToken.length() != 30 || userToken.length() != 30)
                    throw new Exception("api/user token is not valid");

                PushoverEndpoints pushoverEndpoints = pushoverApi.getPushoverEndpoints();

                PushoverEndpoints.Message pem = new PushoverEndpoints.Message();
                pem.setToken(apiToken);
                pem.setUser(userToken);

                Response<PushoverEndpoints.Message> response = pushoverEndpoints.validate(pem).execute();

                if (!response.isSuccessful())
                    throw new Exception("no response " + response.message());
                else if (response.body() == null)
                    throw new Exception("response body null");
                else if (response.code() != 200 && response.code() != 400)
                    throw new Exception("server error");

                String status = response.body().getStatus();
                if (response.code() == 400 || status == null || !status.equals("1")) {
                    userLogMessage(ICON_WARN + "Pushover: validation failed. Check that your Pushover account is active and your account settings are correct.");
                    throw new Exception("account error");
                } else {

                    valid = true;

                    userLogMessage(ICON_INFO + "Pushover: validation success");

                    String[] devices = response.body().getDevices();
                    if (devices != null) {
                        String result = "";
                        for (String s : devices) {
                            result += " '" + s + "'";
                        }
                        userLogMessage(ICON_INFO + "Pushover devices:" + result);
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Pushover validation failed: " + e.getMessage());
        }

        updateValidation();

        return valid;
    }

    private void resetValidation() {
        valid = false;
        apiToken = "";
        userToken = "";
        updateValidation();
    }

    private void updateValidation() {
        storeRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {
                dataStore.setPushoverValidated(valid);
                dataStore.setPushoverAPItokenCheck(apiToken);
                dataStore.setPushoverUSERtokenCheck(userToken);
            }
        });
    }

    private void process() {
        messagesSent = 0;

        pumpHistoryHandler = new PumpHistoryHandler(mContext);
        List<PumpHistoryInterface> records = pumpHistoryHandler.getSenderRecordsREQ("PO");

        for (PumpHistoryInterface record : records) {

            List<info.nightscout.android.history.MessageItem> messageItems = record.message(pumpHistoryHandler.pumpHistorySender,"PO");

            boolean success = true;
            for (MessageItem messageItem : messageItems) {
                success &= send(messageItem);
            }

            if (success) {
                pumpHistoryHandler.setSenderRecordACK(record, "PO");
            } else {
                break;
            }

        }

        pumpHistoryHandler.close();

        if (messagesSent > 0) {
            DateFormat df = new SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH);
            Log.i(TAG, String.format(Locale.ENGLISH, "Sent: %d Limit: %d Remaining: %d Reset: %s",
                    messagesSent, appLimit, appRemaining, df.format(appReset * 1000)));

        }
    }

    public boolean send(MessageItem messageItem) {
        boolean success;

        String title = messageItem.getTitle();
        String message = messageItem.getMessage();
        String extended = messageItem.getExtended();

        if (dataStore.isPushoverTitleTime())
            title += " " + messageItem.getClock();

        if (extended.length() > 0)
            message += " • " + extended;

        // Pushover will fail with a empty message string
        if (message.length() == 0) message = "...";

        PushoverEndpoints pushoverEndpoints = pushoverApi.getPushoverEndpoints();

        PushoverEndpoints.Message pem = new PushoverEndpoints.Message();
        pem.setToken(apiToken);
        pem.setUser(userToken);

        pem.setTitle(title);
        pem.setMessage(message);
        pem.setTimestamp(String.valueOf(messageItem.getDate().getTime() / 1000L));

        String priority;
        String sound;
        switch (messageItem.getType()) {
            case ALERT_ON_HIGH:
                priority = dataStore.getPushoverPriorityOnHigh();
                sound = dataStore.getPushoverSoundOnHigh();
                break;
            case ALERT_ON_LOW:
                priority = dataStore.getPushoverPriorityOnLow();
                sound = dataStore.getPushoverSoundOnLow();
                break;
            case ALERT_BEFORE_HIGH:
                priority = dataStore.getPushoverPriorityBeforeHigh();
                sound = dataStore.getPushoverSoundBeforeHigh();
                break;
            case ALERT_BEFORE_LOW:
                priority = dataStore.getPushoverPriorityBeforeLow();
                sound = dataStore.getPushoverSoundBeforeLow();
                break;
            case ALERT_AUTOMODE_EXIT:
                priority = dataStore.getPushoverPriorityAutoModeExit();
                sound = dataStore.getPushoverSoundAutoModeExit();
                break;
            case ALERT_EMERGENCY:
                priority = dataStore.getPushoverPriorityPumpEmergency();
                sound = dataStore.getPushoverSoundPumpEmergency();
                break;
            case ALERT_ACTIONABLE:
                priority = dataStore.getPushoverPriorityPumpActionable();
                sound = dataStore.getPushoverSoundPumpActionable();
                break;
            case ALERT_INFORMATIONAL:
                priority = dataStore.getPushoverPriorityPumpInformational();
                sound = dataStore.getPushoverSoundPumpInformational();
                break;
            case REMINDER:
                priority = dataStore.getPushoverPriorityPumpReminder();
                sound = dataStore.getPushoverSoundPumpReminder();
                break;
            case BOLUS:
                priority = dataStore.getPushoverPriorityBolus();
                sound = dataStore.getPushoverSoundBolus();
                break;
            case BASAL:
                priority = dataStore.getPushoverPriorityBasal();
                sound = dataStore.getPushoverSoundBasal();
                break;
            case SUSPEND:
            case RESUME:
                priority = dataStore.getPushoverPrioritySuspendResume();
                sound = dataStore.getPushoverSoundSuspendResume();
                break;
            case BG:
                priority = dataStore.getPushoverPriorityBG();
                sound = dataStore.getPushoverSoundBG();
                break;
            case CALIBRATION:
                priority = dataStore.getPushoverPriorityCalibration();
                sound = dataStore.getPushoverSoundCalibration();
                break;
            case CONSUMABLE:
                priority = dataStore.getPushoverPriorityConsumables();
                sound = dataStore.getPushoverSoundConsumables();
                break;
            case DAILY_TOTALS:
                priority = dataStore.getPushoverPriorityDailyTotals();
                sound = dataStore.getPushoverSoundDailyTotals();
                break;
            case ALERT_UPLOADER_ERROR:
                priority = dataStore.getPushoverPriorityUploaderPumpErrors();
                sound = dataStore.getPushoverSoundUploaderPumpErrors();
                break;
            case ALERT_UPLOADER_CONNECTION:
                priority = dataStore.getPushoverPriorityUploaderPumpConnection();
                sound = dataStore.getPushoverSoundUploaderPumpConnection();
                break;
            case ALERT_UPLOADER_BATTERY:
                priority = dataStore.getPushoverPriorityUploaderBattery();
                sound = dataStore.getPushoverSoundUploaderBattery();
                break;
            default:

                if (messageItem.getPriority() == MessageItem.PRIORITY.EMERGENCY) {
                    priority = dataStore.getPushoverPriorityPumpEmergency();
                    sound = dataStore.getPushoverSoundPumpEmergency();
                } else if (messageItem.getPriority() == MessageItem.PRIORITY.HIGH) {
                    priority = dataStore.getPushoverPriorityPumpActionable();
                    sound = dataStore.getPushoverSoundPumpActionable();
                } else if (messageItem.getPriority() == MessageItem.PRIORITY.NORMAL) {
                    priority = dataStore.getPushoverPriorityPumpInformational();
                    sound = dataStore.getPushoverSoundPumpInformational();
                } else {
                    priority = "0";
                    sound = "none";
                }
        }

        if (messageItem.isCleared()) {
            priority = dataStore.getPushoverPriorityCleared();
            sound = dataStore.getPushoverSoundCleared();
        } else if (messageItem.isSilenced()) {
            priority = dataStore.getPushoverPrioritySilenced();
            sound = dataStore.getPushoverSoundSilenced();
        }

        if (dataStore.isPushoverEnablePriorityOverride())
            priority = dataStore.getPushoverPriorityOverride();
        if (dataStore.isPushoverEnableSoundOverride())
            sound = dataStore.getPushoverSoundOverride();

        pem.setPriority(priority);
        pem.setSound(sound);

        if (priority.equals("2")) {
            pem.setRetry(dataStore.getPushoverEmergencyRetry());
            pem.setExpire(dataStore.getPushoverEmergencyExpire());
        }

        try {
            Response<PushoverEndpoints.Message> response = pushoverEndpoints.postMessage(pem).execute();

            if (!response.isSuccessful()) {
                throw new Exception("no response " + response.message());
            } else if (response.body() == null) {
                throw new Exception("response body null");
            } else if (response.code() == 400) {
                resetValidation();
                throw new Exception("account error");
            } else if (response.code() != 200) {
                throw new Exception("server error");
            }

            try {
                Headers headers = response.headers();
                for (int i = 0, count = headers.size(); i < count; i++) {
                    String name = headers.name(i);
                    if ("X-Limit-App-Limit".equalsIgnoreCase(name)) {
                        appLimit = Integer.parseInt(headers.value(i));
                    } else if ("X-Limit-App-Remaining".equalsIgnoreCase(name)) {
                        appRemaining = Integer.parseInt(headers.value(i));
                    } else if ("X-Limit-App-Reset".equalsIgnoreCase(name)) {
                        appReset = Long.parseLong(headers.value(i));
                    }
                }
            } catch (Exception ignored) {}

            messagesSent++;

            success = true;
            Log.i(TAG, "success");
        } catch (Exception e) {
            success = false;
            Log.e(TAG, "failed: " + e.getMessage());
        }

        return success;
    }

    public enum PRIORITY {
        LOWEST("-2"),
        LOW("-1"),
        NORMAL("0"),
        HIGH("1"),
        EMERGENCY("2");

        private String string;

        PRIORITY(String string) {
            this.string = string;
        }
    }

    public enum SOUND {
        PUSHOVER("pushover"),
        BIKE("bike"),
        BUGLE("bugle"),
        CASHREGISTER("cashregister"),
        CLASSICAL("classical"),
        COSMIC("cosmic"),
        FALLING("falling"),
        GAMELAN("gamelan"),
        INCOMING("incoming"),
        INTERMISSION("intermission"),
        MAGIC("magic"),
        MECHANICAL("mechanical"),
        PIANOBAR("pianobar"),
        SIREN("siren"),
        SPACEALARM("spacealarm"),
        TUGBOAT("tugboat"),
        ALIEN("alien"),
        CLIMB("climb"),
        PERSISTENT("persistent"),
        ECHO("echo"),
        UPDOWN("updown"),
        NONE("none");

        private String string;

        SOUND(String string) {
            this.string = string;
        }
    }

}

/*

LOWEST
no sounds
no notification pop-up
useful as a ledger of events without bothering user

LOW
no sounds
notification pop-up

NORMAL
sounds
notification pop-up

HIGH
sounds
notification pop-up coloured red

EMERGENCY
sounds
notification pop-up coloured red
can repeat at >=30 seconds for specified time
persistent alarm that needs to be acknowledged by user

*/
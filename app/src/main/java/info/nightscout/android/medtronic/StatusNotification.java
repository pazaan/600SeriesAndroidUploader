package info.nightscout.android.medtronic;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import info.nightscout.android.R;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.history.PumpHistoryParser;
import info.nightscout.android.model.medtronicNg.PumpHistoryBG;
import info.nightscout.android.model.medtronicNg.PumpHistoryBasal;
import info.nightscout.android.model.medtronicNg.PumpHistoryBolus;
import info.nightscout.android.model.medtronicNg.PumpHistoryCGM;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.store.DataStore;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static android.support.v4.app.NotificationCompat.VISIBILITY_PUBLIC;
import static info.nightscout.android.medtronic.MainActivity.MMOLXLFACTOR;
import static info.nightscout.android.medtronic.service.MasterService.SERVICE_NOTIFICATION_ID;

/**
 * Created by Pogman on 8.9.17.
 */

public class StatusNotification {
    private static final String TAG = StatusNotification.class.getSimpleName();

    private static final int COLOR_NO_DATA = 0x00444444;
    private static final int COLOR_SGV_STALE = 0x000060A0;
    private static final int COLOR_SGV_RED = 0x00C04040;
    private static final int COLOR_SGV_YELLOW = 0x00E0C040;
    private static final int COLOR_SGV_GREEN = 0x0040A040;

    private static final String TEXT_AT_TIME = " at ";

    private static StatusNotification instance;

    private NotificationCompat.Builder mNotificationBuilder;
    private NotificationManager mNotificationManager;

    private DateFormat dateFormatterFull = new SimpleDateFormat("h:mm:ss a", Locale.US);
    private DateFormat dateFormatterShort = new SimpleDateFormat("h:mm a", Locale.US);

    private Realm realm;
    private Realm storeRealm;
    private Realm historyRealm;
    private DataStore dataStore;

    private NOTIFICATION mode;
    private long nextpoll;

    private StatusNotification() {
    }

    public static StatusNotification getInstance() {
        Log.d(TAG, "getInstance called");
        if (StatusNotification.instance == null) {
            instance = new StatusNotification();
        }
        return instance;
    }

    public void endNotification() {
        Log.d(TAG, "endNotification called");

        if (historyRealm != null && !historyRealm.isClosed()) historyRealm.close();
        if (storeRealm != null && !storeRealm.isClosed()) storeRealm.close();
        if (realm != null && !realm.isClosed()) realm.close();
        dataStore = null;
        storeRealm = null;
        historyRealm = null;
        realm = null;

        if (mNotificationManager != null) {
            mNotificationManager.cancelAll();
            mNotificationManager = null;
        }

        if (instance != null) instance = null;
    }

    public void initNotification(Context context) {
        Log.d(TAG, "initNotification called");

        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationBuilder = new NotificationCompat.Builder(context)
                .setContentTitle("600 Series Uploader")
                .setSmallIcon(R.drawable.ic_notification)
                .setVisibility(VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent);
    }

    public void updateNotification(NOTIFICATION mode) {
        this.mode = mode;
        updateNotification();
    }

    public void updateNotification(NOTIFICATION mode, long nextpoll) {
        this.mode = mode;
        this.nextpoll = nextpoll;
        updateNotification();
    }

    private void updateNotification() {
        Log.d(TAG, "updateNotification called");

        try {

            realm = Realm.getDefaultInstance();
            storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
            historyRealm = Realm.getInstance(UploaderApplication.getHistoryConfiguration());
            dataStore = storeRealm.where(DataStore.class).findFirst();

            if (realm == null || storeRealm == null || historyRealm == null || dataStore == null) {
                Log.e(TAG, "unexpected null for Realm");
                return;
            }

            long now = System.currentTimeMillis();

            String poll = "";
            if (mode == NOTIFICATION.NORMAL && nextpoll > 0)
                poll = "Next " + dateFormatterFull.format(nextpoll);

            long sgvTime = now;
            long sgvAge = -1;
            int sgvValue = 0;

            String sgv = "";
            String delta = "";

            RealmResults<PumpHistoryCGM> results = historyRealm.where(PumpHistoryCGM.class)
                    .greaterThan("eventDate", new Date(now - (24 * 60 * 60000L)))
                    .greaterThan("sgv", 0)
                    .sort("eventDate", Sort.DESCENDING)
                    .findAll();
            if (results.size() > 0) {
                sgvValue = results.first().getSgv();
                sgv = "SGV " + strFormatSGV(sgvValue);
                sgvTime = results.first().getEventDate().getTime();
                sgvAge = (now - sgvTime) / 60000L;

                if (results.size() > 1 && mode != NOTIFICATION.ERROR) {
                    long deltaTime = (sgvTime - results.get(1).getEventDate().getTime()) / 60000L;
                    if (sgvAge < 60 && deltaTime < 30) {
                        int diff = results.first().getSgv() - results.get(1).getSgv();
                        if (dataStore.isMmolxl())
                            delta += "Δ " + (diff > 0 ? "+" : "") + new BigDecimal(diff / MMOLXLFACTOR).setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString();
                        else
                            delta += "Δ " + (diff > 0 ? "+" : "") + diff;
                        if (deltaTime > 6)
                            delta += " " + deltaTime + "m";
                    }
                }

            } else sgv = "No SGV available";

            String alert = "";
            String alertmessage = "";
            RealmResults<PumpStatusEvent> pumpStatusEvents = realm.where(PumpStatusEvent.class)
                    .sort("eventDate", Sort.DESCENDING)
                    .findAll();
            if (pumpStatusEvents.size() > 0) {
                if (pumpStatusEvents.first().getAlert() > 0) {
                    alert = "⚠";
                    alertmessage = "Active Alert on Pump";
                }
            }

            int color = COLOR_NO_DATA;
            if (sgvAge >= 0 && sgvAge <= 15) {
                if (sgvValue < 80)
                    color = COLOR_SGV_RED;
                else if (sgvValue <= 180)
                    color = COLOR_SGV_GREEN;
                else if (sgvValue <= 260)
                    color = COLOR_SGV_YELLOW;
                else
                    color = COLOR_SGV_RED;
            } else if (realm.where(PumpStatusEvent.class)
                    .greaterThan("eventDate", new Date(now - (15 * 60000L)))
                    .findAll().size() > 0) {
                color = COLOR_SGV_STALE;
            }

            String iob = iob();
            String basal = basal();
            String bolus = lastBolus();
            String bolusing = bolusing();
            String bg = lastBG();
            String calibration = calibration();

            String content = iob + "  " + alert + "  " + calibration;
            String summary = (poll.equals("") ? "" : poll + "  ") + calibration;
            if (mode == NOTIFICATION.ERROR) {
                content = "connection error";
                summary = content;
            }

            NotificationCompat.InboxStyle sub = new NotificationCompat.InboxStyle()
                    .addLine(alertmessage)
                    .addLine(iob)
                    .addLine(basal)
                    .addLine(bg)
                    .addLine(bolus)
                    .addLine(bolusing)
                    .setSummaryText(summary);

//        mNotificationBuilder.setProgress(0, 0, false);
            if (mode == NOTIFICATION.ERROR) {
                color = COLOR_NO_DATA;
                mNotificationBuilder.setSmallIcon(R.drawable.ic_error);
            } else if (mode == NOTIFICATION.BUSY) {
//            mNotificationBuilder.setProgress(0, 0, true);
                mNotificationBuilder.setSmallIcon(R.drawable.busy_anim);
            } else
                mNotificationBuilder.setSmallIcon(R.drawable.ic_notification);

            mNotificationBuilder.setStyle(sub);
            mNotificationBuilder.setWhen(sgvTime);
            mNotificationBuilder.setColor(color);

            mNotificationBuilder.setContentTitle(sgv + "   " + delta);
            mNotificationBuilder.setContentText(content);

            mNotificationManager.notify(
                    SERVICE_NOTIFICATION_ID,
                    mNotificationBuilder.build());

        } catch (Exception e) {
            Log.e(TAG, "Unexpected Error! " + Log.getStackTraceString(e));

        } finally {
            if (historyRealm != null && !historyRealm.isClosed()) historyRealm.close();
            if (storeRealm != null && !storeRealm.isClosed()) storeRealm.close();
            if (realm != null && !realm.isClosed()) realm.close();
            dataStore = null;
            storeRealm = null;
            historyRealm = null;
            realm = null;
        }
    }

    private String strFormatSGV(double sgvValue) {
        NumberFormat sgvFormatter;
        if (dataStore.isMmolxl()) {
            if (dataStore.isMmolxlDecimals())
                sgvFormatter = new DecimalFormat("0.00");
            else
                sgvFormatter = new DecimalFormat("0.0");
            return sgvFormatter.format(sgvValue / MMOLXLFACTOR);
        } else {
            sgvFormatter = new DecimalFormat("0");
            return sgvFormatter.format(sgvValue);
        }
    }
    /*
    private String calibration() {
        long now = System.currentTimeMillis();
        String text = "";

        RealmResults<PumpStatusEvent> results = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(now - (15 * 60000L)))
                .equalTo("cgmActive", true)
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        if (results.size() > 0) {
            if (results.first().isCgmCalibrating())
                text = "Calibrating...";
            else if (results.first().isCgmCalibrationComplete())
                text = "Calibration complete";
            else {
                if (results.first().isCgmWarmUp())
                    text = "Warmup ";
                else
                    text = "Calibration ";
                long timer = ((results.first().getCgmDate().getTime() - now) / 60000L) + results.first().getCalibrationDueMinutes();
                if (timer > 0)
                    text += (timer >= 60 ? results.first().getCalibrationDueMinutes() / 60 + "h" : "") + timer % 60 + "m";
                else
                    text = "Calibrate now!";
            }
        }

        return text;
    }
    */

    private String calibration() {
        long now = System.currentTimeMillis();
        String text = "";

        RealmResults<PumpStatusEvent> results = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(now - (15 * 60000L)))
                .equalTo("cgmActive", true)
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        if (results.size() > 0) {

            if (results.first().isCgmWarmUp())
                text = "Warmup";
            else if (results.first().isCgmCalibrating())
                text = "Calibrating...";
            else if (results.first().isCgmCalibrationComplete())
                text = "Calibration complete";
            else if (results.first().getCalibrationDueMinutes() == 0)
                text = "Calibrate now!";
            else if (results.first().getSgv() == 0 && results.first().isCgmException() &&
                    !PumpHistoryParser.CGM_EXCEPTION.SENSOR_CAL_NEEDED.equals(results.first().getCgmExceptionType()))

                switch (PumpHistoryParser.CGM_EXCEPTION.convert(results.first().getCgmExceptionType())) {
                    case SENSOR_INIT:
                        text = "[sensor init]";
                        break;
                    case SENSOR_CAL_NEEDED:
                        text = "[calibrate now]";
                        break;
                    case SENSOR_ERROR:
                        text = "[sg not available]";
                        break;
                    case SENSOR_CHANGE_SENSOR_ERROR:
                        text = "[change sensor]";
                        break;
                    case SENSOR_END_OF_LIFE:
                        text = "[end of life]";
                        break;
                    case SENSOR_NOT_READY:
                        text = "[not ready]";
                        break;
                    case SENSOR_READING_HIGH:
                        text = "[reading hi]";
                        break;
                    case SENSOR_READING_LOW:
                        text = "[reading lo]";
                        break;
                    case SENSOR_CAL_PENDING:
                        text = "[cal pending]";
                        break;
                    case SENSOR_CAL_ERROR:
                        text = "[cal error])";
                        break;
                    case SENSOR_TIME_UNKNOWN:
                        text = "[time unknown]";
                        break;
                    default:
                        text = "[sensor error]";
                }

            else text = "Calibration";

            if (results.first().getCalibrationDueMinutes() > 0) {
                long timer = ((results.first().getCgmDate().getTime() - now) / 60000L) + results.first().getCalibrationDueMinutes();
                if (timer > 0)
                    text += " " + (timer >= 60 ? results.first().getCalibrationDueMinutes() / 60 + "h" : "") + timer % 60 + "m";
                else
                    text = "Calibrate now!";
            }
        }

        return text;
    }

    private String iob() {
        long now = System.currentTimeMillis();
        String text = "";

        RealmResults<PumpStatusEvent> results = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(now - (4 * 60 * 60000L)))
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        if (results.size() > 0) {
            text = "IOB " + results.first().getActiveInsulin() + "u";
        }

        return text;
    }

    private String bolusing() {
        long now = System.currentTimeMillis();
        String text = "";

        RealmResults<PumpStatusEvent> results = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(now - (12 * 60 * 60000L)))
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        if (results.size() > 0 && !results.first().isBolusingNormal()
                && (results.first().isBolusingSquare() || results.first().isBolusingDual())) {
            text += "Bolusing: " + results.first().getBolusingDelivered() + "u "
                    + (results.first().getBolusingMinutesRemaining() >= 60 ? results.first().getBolusingMinutesRemaining() / 60 + "h" : "")
                    + results.first().getBolusingMinutesRemaining() % 60 + "m remain";
        }

        return text;
    }

    private String basal() {
        long now = System.currentTimeMillis();
        String text = "";

        RealmResults<PumpStatusEvent> results = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(now - (12 * 60 * 60000L)))
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        if (results.size() > 0) {

            if (results.first().isSuspended()) {
                text += "Basal: suspended";
                RealmResults<PumpHistoryBasal> suspend = historyRealm.where(PumpHistoryBasal.class)
                        .greaterThan("eventDate", new Date(now - (12 * 60 * 60000L)))
                        .equalTo("recordtype", PumpHistoryBasal.RECORDTYPE.SUSPEND.value())
                        .or()
                        .equalTo("recordtype", PumpHistoryBasal.RECORDTYPE.RESUME.value())
                        .sort("eventDate", Sort.DESCENDING)
                        .findAll();
                // check if most recent suspend is in history and show the start time
                if (suspend.size() > 0 && PumpHistoryBasal.RECORDTYPE.SUSPEND.equals(suspend.first().getRecordtype()))
                    text += TEXT_AT_TIME + dateFormatterShort.format(suspend.first().getEventDate());

            } else if (results.first().isTempBasalActive()) {
                float rate = results.first().getTempBasalRate();
                int percent = results.first().getTempBasalPercentage();
                int minutes = results.first().getTempBasalMinutesRemaining();
                int preset = results.first().getActiveTempBasalPattern();

                if (PumpHistoryParser.TEMP_BASAL_PRESET.TEMP_BASAL_PRESET_0.equals(preset))
                    text += "Temp Basal: ";
                else
                    text += dataStore.getNameTempBasalPreset(preset) + ": ";

                if (results.first().getTempBasalPercentage() != 0) {
                    rate += (percent * results.first().getBasalRate()) / 100;
                    text += percent + "% ~ ";
                }
                text += rate + "u " + minutes + "m remain";

            } else {
                int pattern = results.first().getActiveBasalPattern();
                if (pattern != 0)
                    text += dataStore.getNameBasalPattern(pattern) + ": ";
                else
                    text += "Basal: ";
                text += results.first().getBasalRate() + "u";
            }

        }

        return text;
    }

    private String lastBG() {
        long now = System.currentTimeMillis();
        String text = "";

        RealmResults<PumpHistoryBG> results = historyRealm.where(PumpHistoryBG.class)
                .notEqualTo("bg",0)
                .greaterThan("eventDate", new Date(now - (24 * 60 * 60000L)))
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        if (results.size() > 0) {
            text = "BG: " + strFormatSGV(results.first().getBg()) + TEXT_AT_TIME + dateFormatterShort.format(results.first().getBgDate());

            if (dataStore.isNsEnableCalibrationInfo()) {
                results = results.where()
                        .equalTo("calibrationFlag", true)
                        .sort("eventDate", Sort.DESCENDING)
                        .findAll();
                if (results.size() > 0 && results.first().isCalibration()) {
                    text += "   Cal: ⋊ " + results.first().getCalibrationFactor();
                }
            }
        }

        return text;
    }

    private String lastBolus() {
        long now = System.currentTimeMillis();
        String text = "";

        RealmResults<PumpHistoryBolus> results = historyRealm.where(PumpHistoryBolus.class)
                .greaterThan("eventDate", new Date(now - (24 * 60 * 60000L)))
                .equalTo("programmed", true)
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        if (results.size() > 0) {
            text += "Bolus: ";
            if (PumpHistoryParser.BOLUS_TYPE.DUAL_WAVE.equals(results.first().getBolusType())) {
                if (results.first().isSquareDelivered())
                    text += "dual " + results.first().getNormalDeliveredAmount() + "/" + results.first().getSquareDeliveredAmount() + "u:" + results.first().getSquareDeliveredDuration() + "m" + TEXT_AT_TIME + dateFormatterShort.format(results.first().getProgrammedDate());
                else if (results.first().isNormalDelivered())
                    text += "dual " + results.first().getNormalDeliveredAmount() + "/" + results.first().getSquareProgrammedAmount() + "u:" + results.first().getSquareProgrammedDuration() + "m" + TEXT_AT_TIME + dateFormatterShort.format(results.first().getProgrammedDate());
                else
                    text += "dual " + results.first().getNormalProgrammedAmount() + "/" + results.first().getSquareProgrammedAmount() + "u:" + results.first().getSquareProgrammedDuration() + "m" + TEXT_AT_TIME + dateFormatterShort.format(results.first().getProgrammedDate());

            } else if (PumpHistoryParser.BOLUS_TYPE.SQUARE_WAVE.equals(results.first().getBolusType())) {
                if (results.first().isSquareDelivered())
                    text += "square " + results.first().getSquareDeliveredAmount() + "u:" + results.first().getSquareDeliveredDuration() + "m" + TEXT_AT_TIME + dateFormatterShort.format(results.first().getProgrammedDate());
                else
                    text += "square " + results.first().getSquareProgrammedAmount() + "u:" + results.first().getSquareProgrammedDuration() + "m" + TEXT_AT_TIME + dateFormatterShort.format(results.first().getProgrammedDate());

            } else {
                if (results.first().isNormalDelivered())
                    text += results.first().getNormalDeliveredAmount() + "u" + TEXT_AT_TIME + dateFormatterShort.format(results.first().getProgrammedDate());
                else
                    text += results.first().getNormalProgrammedAmount() + "u" + TEXT_AT_TIME + dateFormatterShort.format(results.first().getProgrammedDate());
            }

        }

        return text;
    }

    public enum NOTIFICATION {
        NORMAL,
        BUSY,
        ERROR,
    }
}
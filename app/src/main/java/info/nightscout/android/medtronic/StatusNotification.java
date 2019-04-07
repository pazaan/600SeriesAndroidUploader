package info.nightscout.android.medtronic;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.math.RoundingMode;
import java.util.Date;

import info.nightscout.android.PumpAlert;
import info.nightscout.android.R;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.history.PumpHistoryParser;
import info.nightscout.android.model.medtronicNg.PumpHistoryAlarm;
import info.nightscout.android.model.medtronicNg.PumpHistoryBG;
import info.nightscout.android.model.medtronicNg.PumpHistoryBasal;
import info.nightscout.android.model.medtronicNg.PumpHistoryBolus;
import info.nightscout.android.model.medtronicNg.PumpHistoryCGM;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.store.DataStore;
import info.nightscout.android.utils.FormatKit;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static android.support.v4.app.NotificationCompat.VISIBILITY_PUBLIC;
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

    private NotificationCompat.Builder mNotificationBuilder;
    private NotificationManager mNotificationManager;

    private Realm realm;
    private Realm storeRealm;
    private Realm historyRealm;
    private DataStore dataStore;

    long currentTime;
    RealmResults<PumpStatusEvent> pumpStatusEventRealmResults;

    private NOTIFICATION mode;
    private long nextpoll;

    public enum NOTIFICATION {
        NORMAL,
        BUSY,
        ERROR,
    }

    public StatusNotification() {
    }

    public void endNotification() {
        Log.d(TAG, "endNotification called");
        while (updateThread != null) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
        }
        if (mNotificationManager != null) {
            mNotificationManager.cancelAll();
            mNotificationManager = null;
        }
    }

    public void initNotification(Context context) {
        Log.d(TAG, "initNotification called");

        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        String channel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? "status" : "";

        mNotificationBuilder = new NotificationCompat.Builder(context, channel)
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

    public void updateNotification() {
        Log.d(TAG, "updateNotification called");
        if (updateThread == null) {
            updateThread = new Update();
            updateThread.start();
        }
    }

    private Update updateThread;

    private class Update extends Thread {

        public void run() {

            try {

                realm = Realm.getDefaultInstance();
                storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
                historyRealm = Realm.getInstance(UploaderApplication.getHistoryConfiguration());
                dataStore = storeRealm.where(DataStore.class).findFirst();

                if (realm == null || storeRealm == null || historyRealm == null || dataStore == null) {
                    Log.e(TAG, "unexpected null for Realm");
                    return;
                }

                currentTime = System.currentTimeMillis();

                pumpStatusEventRealmResults = realm.where(PumpStatusEvent.class)
                        .greaterThan("eventDate", new Date(currentTime - 24 * 60 * 60000L))
                        .sort("eventDate", Sort.DESCENDING)
                        .findAll();

                long sgvTime = currentTime;
                long sgvAge = -1;
                int sgvValue = 0;

                String estimate = "";
                String sgv = "";
                String delta = "";

                RealmResults<PumpHistoryCGM> results = historyRealm.where(PumpHistoryCGM.class)
                        .greaterThan("eventDate", new Date(currentTime - 24 * 60 * 60000L))
                        .greaterThan("sgv", 0)
                        .sort("eventDate", Sort.DESCENDING)
                        .findAll();
                if (results.size() > 0) {
                    sgvValue = results.first().getSgv();
                    sgvTime = results.first().getEventDate().getTime();
                    sgvAge = (currentTime - sgvTime) / 60000L;

                    int deltaValue = 0;
                    if (results.size() > 1 && mode != NOTIFICATION.ERROR) {
                        int deltaTime = (int) ((sgvTime - results.get(1).getEventDate().getTime()) / 60000L);
                        if (sgvAge < 60 && deltaTime < 30) {
                            deltaValue = sgvValue - results.get(1).getSgv();
                        }
                    }

                    sgv = String.format(
                            results.first().isEstimate()
                                    ? FormatKit.getInstance().getString(R.string.notification__SGV_value_estimated)
                                    : FormatKit.getInstance().getString(R.string.notification__SGV_value),
                            FormatKit.getInstance().formatAsGlucose(sgvValue, false, true)
                    );

                    delta = String.format(
                            FormatKit.getInstance().getString(R.string.notification__DELTA_value),
                            (deltaValue > 0 ? "+" :"") +
                                    (FormatKit.getInstance().formatAsGlucose(deltaValue, false, 2))
                    );

                    estimate = results.first().isEstimate() ? FormatKit.getInstance().getString(R.string.notification__ESTIMATE) : "";

                } else sgv = FormatKit.getInstance().getString(R.string.notification__SGV_not_available);

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
                } else if (pumpStatusEventRealmResults.size() > 0
                        && currentTime - pumpStatusEventRealmResults.first().getEventDate().getTime() > 15 * 60000L) {
                    color = COLOR_SGV_STALE;
                }

                String next = "";
                if (mode == NOTIFICATION.NORMAL && nextpoll > 0)
                    next = String.format(
                            FormatKit.getInstance().getString(R.string.notification__NEXTPOLL_time),
                            FormatKit.getInstance().formatAsClock(nextpoll)
                    );
                else if (mode == NOTIFICATION.BUSY)
                    next = FormatKit.getInstance().getString(R.string.notification__BUSY);

                String iob = iob();
                String basal = basal();
                String bolus = bolus();
                String bolusing = bolusing();
                String bg = bg();
                String alert = alert();
                String alertmessage = alertMessage();
                String cgm = cgm();

                String title = String.format("%s%s",
                        sgv,
                        sgv.length() == 0 ? delta : delta.length() == 0 ? "" : "  " + delta
                );

                String content = String.format("%s%s%s",
                        iob,
                        iob.length() == 0 ? alert : alert.length() == 0 ? "" : "  " + alert,
                        iob.length() + alert.length() == 0 ? cgm : cgm.length() == 0 ? "" : "  " + cgm
                );

                String summary = String.format("%s%s",
                        next,
                        next.length() == 0 ? cgm : cgm.length() == 0 ? "" : " • " + cgm
                );

                if (mode == NOTIFICATION.ERROR) {
                    content = FormatKit.getInstance().getString(R.string.notification__ERROR);
                    summary = content;
                }

                NotificationCompat.InboxStyle sub = new NotificationCompat.InboxStyle()
                        .addLine(alertmessage)
                        .addLine(iob)
                        .addLine(basal)
                        .addLine(bg)
                        .addLine(bolus)
                        .addLine(bolusing)
                        .addLine(estimate)
                        .setSummaryText(summary);

                //mNotificationBuilder.setProgress(0, 0, false); // enable animated progress bar
                if (mode == NOTIFICATION.ERROR) {
                    color = COLOR_NO_DATA;
                    mNotificationBuilder.setSmallIcon(R.drawable.ic_error);
                } else if (mode == NOTIFICATION.BUSY) {
                    //mNotificationBuilder.setProgress(0, 0, true); // disable animated progress bar
                    mNotificationBuilder.setSmallIcon(R.drawable.busy_anim);
                } else
                    mNotificationBuilder.setSmallIcon(R.drawable.ic_notification);

                mNotificationBuilder.setStyle(sub);
                mNotificationBuilder.setWhen(sgvTime);
                mNotificationBuilder.setColor(color);

                mNotificationBuilder.setContentTitle(title);
                mNotificationBuilder.setContentText(content);

                mNotificationManager.notify(
                        SERVICE_NOTIFICATION_ID,
                        mNotificationBuilder.build());

            } catch (Exception e) {
                Log.e(TAG, "Unexpected Error! " + e);

            } finally {
                closeRealm();
            }

            updateThread = null;
        }
    }

    private void closeRealm() {
        if (historyRealm != null && !historyRealm.isClosed()) historyRealm.close();
        if (storeRealm != null && !storeRealm.isClosed()) storeRealm.close();
        if (realm != null && !realm.isClosed()) realm.close();
        pumpStatusEventRealmResults = null;
        dataStore = null;
        storeRealm = null;
        historyRealm = null;
        realm = null;
    }

    private String cgm() {

        RealmResults<PumpStatusEvent> results = pumpStatusEventRealmResults.where()
                .greaterThan("eventDate", new Date(currentTime - 15 * 60000L))
                .equalTo("cgmActive", true)
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        if (results.size() > 0) {

            PumpHistoryParser.CGM_EXCEPTION cgmException;
            if (results.first().isCgmException())
                cgmException = PumpHistoryParser.CGM_EXCEPTION.convert(
                        pumpStatusEventRealmResults.first().getCgmExceptionType());
            else if (results.first().isCgmCalibrating())
                cgmException = PumpHistoryParser.CGM_EXCEPTION.SENSOR_CAL_PENDING;
            else
                cgmException = PumpHistoryParser.CGM_EXCEPTION.NA;

            switch (cgmException) {
                case NA:
                    return String.format(
                            FormatKit.getInstance().getString(R.string.notification__CAL_remainingtime),
                            FormatKit.getInstance().formatMinutesAsHM(results.first().getCalibrationDueMinutes()));
                case SENSOR_INIT:
                    return String.format(
                            FormatKit.getInstance().getString(R.string.notification__WARMUP_remainingtime),
                            FormatKit.getInstance().formatMinutesAsHM(results.first().getCalibrationDueMinutes()));
                default:
                    return String.format(
                            FormatKit.getInstance().getString(R.string.notification__CGM_EXCEPTION),
                            cgmException.string());
            }
        }

        return "";
    }

    private String iob() {

        if (pumpStatusEventRealmResults.size() > 0
                && currentTime - pumpStatusEventRealmResults.first().getEventDate().getTime() < 4 * 60 * 60000L) {

            return String.format(
                    FormatKit.getInstance().getString(R.string.notification__IOB_value),
                    FormatKit.getInstance().formatAsInsulin((double) pumpStatusEventRealmResults.first().getActiveInsulin())
            );
        }

        return "";
    }

    private String basal() {

        if (pumpStatusEventRealmResults.size() > 0
                && currentTime - pumpStatusEventRealmResults.first().getEventDate().getTime() < 12 * 60 * 60000L) {

            if (pumpStatusEventRealmResults.first().isSuspended()) {
                RealmResults<PumpHistoryBasal> pumpHistoryBasalRealmResults = historyRealm.where(PumpHistoryBasal.class)
                        .greaterThan("eventDate", new Date(currentTime - 12 * 60 * 60000L))
                        .beginGroup()
                        .equalTo("recordtype", PumpHistoryBasal.RECORDTYPE.SUSPEND.value())
                        .or()
                        .equalTo("recordtype", PumpHistoryBasal.RECORDTYPE.RESUME.value())
                        .endGroup()
                        .sort("eventDate", Sort.DESCENDING)
                        .findAll();
                // check if most recent suspend is in history and show the start time
                if (pumpHistoryBasalRealmResults.size() > 0
                        && PumpHistoryBasal.RECORDTYPE.SUSPEND.equals(pumpHistoryBasalRealmResults.first().getRecordtype()))

                    return String.format(
                            FormatKit.getInstance().getString(R.string.notification__SUSPEND_time),
                            FormatKit.getInstance().formatAsClock(pumpHistoryBasalRealmResults.first().getEventDate().getTime())
                    );

                else

                    return FormatKit.getInstance().getString(R.string.notification__SUSPEND);

            } else if (pumpStatusEventRealmResults.first().isTempBasalActive()) {
                int percent = pumpStatusEventRealmResults.first().getTempBasalPercentage();
                int minutes = pumpStatusEventRealmResults.first().getTempBasalMinutesRemaining();
                int preset = pumpStatusEventRealmResults.first().getActiveTempBasalPattern();

                String rateString = "";
                if (percent != 0)
                    rateString = String.format("%s (%s)",
                            FormatKit.getInstance().formatAsInsulin((double) ((percent * pumpStatusEventRealmResults.first().getBasalRate()) / 100), 3),
                            FormatKit.getInstance().formatAsPercent(percent)
                    );
                else
                    rateString = FormatKit.getInstance().formatAsInsulin((double) pumpStatusEventRealmResults.first().getTempBasalRate(), 3);

                if (PumpHistoryParser.TEMP_BASAL_PRESET.TEMP_BASAL_PRESET_0.equals(preset))

                    return String.format(
                            FormatKit.getInstance().getString(R.string.notification__TEMPBASAL_rate_remainingtime),
                            rateString,
                            FormatKit.getInstance().formatMinutesAsDHM(minutes)
                    );

                else

                    return String.format(
                            FormatKit.getInstance().getString(R.string.notification__TEMPBASAL_rate_remainingtime_preset),
                            rateString,
                            FormatKit.getInstance().formatMinutesAsDHM(minutes),
                            FormatKit.getInstance().getNameTempBasalPreset(preset)
                    );

            } else {
                int pattern = pumpStatusEventRealmResults.first().getActiveBasalPattern();
                if (pattern != 0)

                    return String.format(
                            FormatKit.getInstance().getString(R.string.notification__BASAL_rate_pattern),
                            FormatKit.getInstance().formatAsInsulin((double) pumpStatusEventRealmResults.first().getBasalRate(), 3),
                            FormatKit.getInstance().getNameBasalPattern(pattern)
                    );

                else

                    return String.format(
                            FormatKit.getInstance().getString(R.string.notification__BASAL_rate),
                            FormatKit.getInstance().formatAsInsulin((double) pumpStatusEventRealmResults.first().getBasalRate(), 3)
                    );

            }

        }

        return "";
    }

    private String bg() {

        RealmResults<PumpHistoryBG> pumpHistoryBGRealmResults = historyRealm.where(PumpHistoryBG.class)
                .notEqualTo("bg",0)
                .greaterThan("eventDate", new Date(currentTime - 24 * 60 * 60000L))
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        if (pumpHistoryBGRealmResults.size() > 0) {

            String bg = FormatKit.getInstance().formatAsGlucose(pumpHistoryBGRealmResults.first().getBg(), false, true);
            String time = FormatKit.getInstance().formatAsClock(pumpHistoryBGRealmResults.first().getBgDate().getTime());
            String factor = "";

            if (dataStore.isNsEnableCalibrationInfo()) {
                pumpHistoryBGRealmResults = pumpHistoryBGRealmResults.where()
                        .equalTo("calibrationFlag", true)
                        .sort("eventDate", Sort.DESCENDING)
                        .findAll();
                if (pumpHistoryBGRealmResults.size() > 0 && pumpHistoryBGRealmResults.first().isCalibration())
                    factor = FormatKit.getInstance().formatAsDecimal(pumpHistoryBGRealmResults.first().getCalibrationFactor(), 1, 1, RoundingMode.DOWN);
            }

            if (factor.length() == 0)

                return String.format(
                        FormatKit.getInstance().getString(R.string.notification__BG_value_time),
                        bg,
                        time
                );

            else

                return String.format(
                        FormatKit.getInstance().getString(R.string.notification__BG_value_time_factor),
                        bg,
                        time,
                        factor
                );
        }

        return "";
    }

    private String bolusing() {

        if (pumpStatusEventRealmResults.size() > 0
                && currentTime - pumpStatusEventRealmResults.first().getEventDate().getTime() < 12 * 60 * 60000L
                && !pumpStatusEventRealmResults.first().isBolusingNormal()
                && (pumpStatusEventRealmResults.first().isBolusingSquare() || pumpStatusEventRealmResults.first().isBolusingDual())) {

            return String.format(
                    FormatKit.getInstance().getString(R.string.notification__BOLUSING_delivered_remainingtime),
                    FormatKit.getInstance().formatAsInsulin((double) pumpStatusEventRealmResults.first().getBolusingDelivered()),
                    FormatKit.getInstance().formatMinutesAsDHM(pumpStatusEventRealmResults.first().getBolusingMinutesRemaining())
            );
        }

        return "";
    }

    private String bolus() {

        RealmResults<PumpHistoryBolus> pumpHistoryBolusRealmResults = historyRealm.where(PumpHistoryBolus.class)
                .greaterThan("eventDate", new Date(currentTime - 24 * 60 * 60000L))
                .equalTo("programmed", true)
                .sort("eventDate", Sort.DESCENDING)
                .findAll();

        if (pumpHistoryBolusRealmResults.size() > 0) {

            if (PumpHistoryParser.BOLUS_TYPE.DUAL_WAVE.equals(pumpHistoryBolusRealmResults.first().getBolusType()))

                return String.format(
                        FormatKit.getInstance().getString(R.string.notification__DUALBOLUS_delivered_duration_time),
                        FormatKit.getInstance().formatAsInsulin(pumpHistoryBolusRealmResults.first().getNormalDeliveredAmount()),
                        pumpHistoryBolusRealmResults.first().isSquareDelivered()
                                ? FormatKit.getInstance().formatAsInsulin(pumpHistoryBolusRealmResults.first().getSquareDeliveredAmount())
                                : FormatKit.getInstance().formatAsInsulin(pumpHistoryBolusRealmResults.first().getSquareProgrammedAmount()),
                        pumpHistoryBolusRealmResults.first().isSquareDelivered()
                                ? FormatKit.getInstance().formatMinutesAsHM(pumpHistoryBolusRealmResults.first().getSquareDeliveredDuration())
                                : FormatKit.getInstance().formatMinutesAsHM(pumpHistoryBolusRealmResults.first().getSquareProgrammedDuration()),
                        FormatKit.getInstance().formatAsClock(pumpHistoryBolusRealmResults.first().getProgrammedDate().getTime())
                );

            else if (PumpHistoryParser.BOLUS_TYPE.SQUARE_WAVE.equals(pumpHistoryBolusRealmResults.first().getBolusType()))

                return String.format(
                        FormatKit.getInstance().getString(R.string.notification__SQUAREBOLUS_delivered_duration_time),
                        pumpHistoryBolusRealmResults.first().isSquareDelivered()
                                ? FormatKit.getInstance().formatAsInsulin(pumpHistoryBolusRealmResults.first().getSquareDeliveredAmount())
                                : FormatKit.getInstance().formatAsInsulin(pumpHistoryBolusRealmResults.first().getSquareProgrammedAmount()),
                        pumpHistoryBolusRealmResults.first().isSquareDelivered()
                                ? FormatKit.getInstance().formatMinutesAsHM(pumpHistoryBolusRealmResults.first().getSquareDeliveredDuration())
                                : FormatKit.getInstance().formatMinutesAsHM(pumpHistoryBolusRealmResults.first().getSquareProgrammedDuration()),
                        FormatKit.getInstance().formatAsClock(pumpHistoryBolusRealmResults.first().getProgrammedDate().getTime())
                );

            else

                return String.format(
                        FormatKit.getInstance().getString(R.string.notification__BOLUS_delivered_time),
                        pumpHistoryBolusRealmResults.first().isNormalDelivered()
                                ? FormatKit.getInstance().formatAsInsulin(pumpHistoryBolusRealmResults.first().getNormalDeliveredAmount())
                                : FormatKit.getInstance().formatAsInsulin(pumpHistoryBolusRealmResults.first().getNormalProgrammedAmount()),
                        FormatKit.getInstance().formatAsClock(pumpHistoryBolusRealmResults.first().getProgrammedDate().getTime())
                );

        }

        return "";
    }

    private String alert() {

        if (pumpStatusEventRealmResults.size() > 0
                && pumpStatusEventRealmResults.first().getAlert() > 0
                && currentTime - pumpStatusEventRealmResults.first().getEventDate().getTime() < 24 * 60 * 60000L) {
            return FormatKit.getInstance().getString(R.string.notification__ALERT);
        }

        return "";
    }

    private String alertMessage() {

        if (pumpStatusEventRealmResults.size() > 0
                && pumpStatusEventRealmResults.first().getAlert() > 0
                && currentTime - pumpStatusEventRealmResults.first().getEventDate().getTime() < 24 * 60 * 60000L) {

            PumpHistoryAlarm record = historyRealm.where(PumpHistoryAlarm.class)
                    .equalTo("faultNumber", pumpStatusEventRealmResults.first().getAlert())
                    .equalTo("alarmedRTC", pumpStatusEventRealmResults.first().getAlertRTC())
                    .findFirst();

            PumpAlert pumpAlert;
            if (record != null)
                pumpAlert = new PumpAlert().record(record).build();
            else
                pumpAlert = new PumpAlert().faultNumber(pumpStatusEventRealmResults.first().getAlert()).build();

            return String.format(
                    FormatKit.getInstance().getString(R.string.notification__ALERT_message),
                    pumpAlert.getMessage()
            );
        }

        return "";
    }

}
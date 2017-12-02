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
import info.nightscout.android.medtronic.service.MasterService;
import info.nightscout.android.model.medtronicNg.PumpHistoryBG;
import info.nightscout.android.model.medtronicNg.PumpHistoryBolus;
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
    private static StatusNotification instance;

    private NotificationCompat.Builder mNotificationBuilder;
    private NotificationManager mNotificationManager;

    private DateFormat dateFormatterFull = new SimpleDateFormat("h:mm:ss a", Locale.US);
    private DateFormat dateFormatterShort = new SimpleDateFormat("h:mm a", Locale.US);

    private Realm realm;
    private Realm storeRealm;
    private Realm historyRealm;
    private DataStore dataStore;

    private StatusNotification() {
    }

    public static StatusNotification getInstance() {
        Log.d(TAG, "getInstance called");
        if (StatusNotification.instance == null) {
            instance = new StatusNotification();
        }
        return instance;
    }

    public void endNotification () {
        Log.d(TAG, "endNotification called");

        if (historyRealm != null && !historyRealm.isClosed()) historyRealm.close();
        if (storeRealm != null && !storeRealm.isClosed()) storeRealm.close();
        if (realm != null && !realm.isClosed()) realm.close();

        if (mNotificationManager != null) {
            mNotificationManager.cancelAll();
            mNotificationManager = null;
        }

        if (instance != null) instance = null;
        return;
    }

    public void initNotification(Context context) {
        Log.d(TAG, "initNotification called");

        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationBuilder = new  NotificationCompat.Builder(context)
                .setContentTitle("600 Series Uploader")
                .setSmallIcon(R.drawable.ic_notification)
                .setVisibility(VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent);

        realm = Realm.getDefaultInstance();
        storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
        historyRealm = Realm.getInstance(UploaderApplication.getHistoryConfiguration());
        dataStore = storeRealm.where(DataStore.class).findFirst();
    }

    public void updateNotification(long nextpoll) {
        Log.d(TAG, "updateNotification called");

        long now = System.currentTimeMillis();

        String poll = "";
        if (nextpoll > 0)
            poll = "Next " + dateFormatterFull.format(nextpoll);

        long sgvtime = now;

        String sgv = "";
        String delta = "";
        RealmResults<PumpStatusEvent> sgvResults = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(now - (24 * 60 * 60000L)))
                .equalTo("validSGV", true)
                .findAllSorted("eventDate", Sort.DESCENDING);
        if (sgvResults.size() > 0) {
            sgv = "SGV " + strFormatSGV(sgvResults.first().getSgv());
            sgvtime = sgvResults.first().getCgmDate().getTime();
            if (sgvResults.size() > 1) {
                int diff =  sgvResults.first().getSgv() - sgvResults.get(1).getSgv();
                if (dataStore.isMmolxl())
                    delta = "" + (diff > 0 ? "+" : "") + new BigDecimal(diff / MMOLXLFACTOR ).setScale(2, BigDecimal.ROUND_HALF_UP);
                else
                    delta = "" + (diff > 0 ? "+" : "") + diff;
            }
        }
        else sgv = "No SGV available";

        String iob = iob();
        String basal = basal();
        String bolus = lastBolus();
        String bg = lastBG();
        String calibration = calibration();

        NotificationCompat.InboxStyle sub = new  NotificationCompat.InboxStyle()
                .addLine(iob)
                .addLine(basal)
                .addLine(bg)
                .addLine(bolus)
                .setSummaryText((poll.equals("") ? "" : poll + "  ") + calibration);

//        mNotificationBuilder.setProgress(0, 0, false);
        if (MasterService.noteError)
            mNotificationBuilder.setSmallIcon(R.drawable.ic_error);
        else if (MasterService.commsActive) {
//            mNotificationBuilder.setProgress(0, 0, true);
            mNotificationBuilder.setSmallIcon(R.drawable.busy_anim);
        }
        else
            mNotificationBuilder.setSmallIcon(R.drawable.ic_notification);

        mNotificationBuilder.setStyle(sub);
        mNotificationBuilder.setWhen(sgvtime);

        mNotificationBuilder.setContentTitle(sgv + "   " + delta);
        mNotificationBuilder.setContentText(iob + "   " + calibration);

        mNotificationManager.notify(
                SERVICE_NOTIFICATION_ID,
                mNotificationBuilder.build());
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

    private String calibration() {
        long now = System.currentTimeMillis();
        String text = "";

        RealmResults<PumpStatusEvent> results = realm.where(PumpStatusEvent.class)
            .greaterThan("eventDate", new Date(now - (15 * 60000L)))
            .equalTo("validCGM", true)
            .findAllSorted("eventDate", Sort.DESCENDING);

        if (results.size() > 0) {
            text = "Calibration ";
            if (results.first().isCgmCalibrating())
                text = "Calibrating...";
            else if (results.first().isCgmCalibrationComplete())
                text = "Calibration complete";
            else {
                if (results.first().isCgmWarmUp())
                    text = "Warmup ";
                if (results.first().getCalibrationDueMinutes() > 0)
                    text += (results.first().getCalibrationDueMinutes() >= 60 ? results.first().getCalibrationDueMinutes() / 60 + "h" : "") + results.first().getCalibrationDueMinutes() % 60 + "m";
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
                .greaterThan("eventDate", new Date(now - (12 * 60 * 60000L)))
                .findAllSorted("eventDate", Sort.DESCENDING);

        if (results.size() > 0) {
            text = "IOB " + results.first().getActiveInsulin() + "u";
        }

        return text;
    }

    private String basal() {
        long now = System.currentTimeMillis();
        String text = "";

        RealmResults<PumpStatusEvent> results = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(now - (12 * 60 * 60000L)))
                .findAllSorted("eventDate", Sort.DESCENDING);

        if (results.size() > 0) {

            if (results.first().isTempBasalActive()) {
                text = "Temp Basal: ";
                float rate = results.first().getTempBasalRate();
                int percent = results.first().getTempBasalPercentage();
                int minutes = results.first().getTempBasalMinutesRemaining();
                int preset = results.first().getActiveTempBasalPattern();
                if (results.first().getTempBasalPercentage() != 0) {
                    rate = (percent * results.first().getBasalRate()) / 100;
                    text += percent + "% ";
                }
                text += rate + "u " + minutes + "m remain";
                if (!PumpHistoryParser.TEMP_BASAL_PRESET.TEMP_BASAL_PRESET_0.equals(preset))
                    text += " ~ " + PumpHistoryParser.TextEN.valueOf(PumpHistoryParser.TEMP_BASAL_PRESET.convert(preset).name()).getText();

            } else {
                text = "Basal: " + results.first().getBasalRate() + "u";
                text += " ~ " + PumpHistoryParser.TextEN.valueOf(PumpHistoryParser.BASAL_PATTERN.convert(results.first().getActiveBasalPattern()).name()).getText();
            }
        }

        return text;
    }

    private String lastBG() {
        long now = System.currentTimeMillis();
        String text = "";

        RealmResults<PumpHistoryBG> results = historyRealm.where(PumpHistoryBG.class)
                .greaterThan("eventDate", new Date(now - (24 * 60 * 60000L)))
                .findAllSorted("eventDate", Sort.DESCENDING);

        if (results.size() > 0) {
            text = "Last BG: " + strFormatSGV(results.first().getBg()) + " at " + dateFormatterShort.format(results.first().getBgDate());

            /*
            results = results.where()
                    .equalTo("calibration",true)
                    .findAllSorted("eventDate", Sort.DESCENDING);
            if (results.size() > 0) {
                text += "   cal ⋊ " + results.first().getCalibrationFactor();
            }
            */
        }

        return text;
    }

    private String lastBolus() {
        long now = System.currentTimeMillis();
        String text = "";

        RealmResults<PumpHistoryBolus> results = historyRealm.where(PumpHistoryBolus.class)
                .greaterThan("eventDate", new Date(now - (24 * 60 * 60000L)))
                .equalTo("programmed", true)
                .findAllSorted("eventDate", Sort.DESCENDING);

        if (results.size() > 0) {

            if (PumpHistoryParser.BOLUS_TYPE.DUAL_WAVE.equals(results.first().getBolusType())) {
                if (results.first().isSquareDelivered())
                    text = "Last Bolus: dual" + results.first().getNormalDeliveredAmount() + "/" + results.first().getSquareDeliveredAmount() + "u:" + results.first().getSquareDeliveredDuration() + "m at " + dateFormatterShort.format(results.first().getProgrammedDate());
                else if (results.first().isNormalDelivered())
                    text = "Bolusing: dual " + results.first().getNormalDeliveredAmount() + "/" + results.first().getSquareProgrammedAmount() + "u:" + results.first().getSquareProgrammedDuration() + "m at " + dateFormatterShort.format(results.first().getProgrammedDate());
                else
                    text = "Bolusing: dual " + results.first().getNormalProgrammedAmount() + "/" + results.first().getSquareProgrammedAmount() + "u:"+ results.first().getSquareProgrammedDuration() + "m at " + dateFormatterShort.format(results.first().getProgrammedDate());

            } else if (PumpHistoryParser.BOLUS_TYPE.SQUARE_WAVE.equals(results.first().getBolusType())) {
                if (results.first().isSquareDelivered())
                    text = "Last Bolus: square " + results.first().getSquareDeliveredAmount() + "u:" + results.first().getSquareDeliveredDuration() + "m at " + dateFormatterShort.format(results.first().getProgrammedDate());
                else
                    text = "Bolusing: square " + results.first().getSquareProgrammedAmount() + "u:" + results.first().getSquareProgrammedDuration() + "m at " + dateFormatterShort.format(results.first().getProgrammedDate());

            } else {
                if (results.first().isNormalDelivered())
                    text = "Last Bolus: " + results.first().getNormalDeliveredAmount() + "u at " + dateFormatterShort.format(results.first().getProgrammedDate());
                else
                    text = "Bolusing: "  + results.first().getNormalProgrammedAmount() + "u at " + dateFormatterShort.format(results.first().getProgrammedDate());
            }

        }

        return text;
    }
}

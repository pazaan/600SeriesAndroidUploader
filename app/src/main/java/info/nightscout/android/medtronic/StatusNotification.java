package info.nightscout.android.medtronic;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
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
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static android.support.v4.app.NotificationCompat.VISIBILITY_PUBLIC;
import static info.nightscout.android.medtronic.MainActivity.MMOLXLFACTOR;
import static info.nightscout.android.medtronic.service.MasterService.SERVICE_NOTIFICATION_ID;

/**
 * Created by John on 8.9.17.
 */

public class StatusNotification {
    private static final String TAG = StatusNotification.class.getSimpleName();
    private static StatusNotification instance;

    private NotificationCompat.Builder mNotificationBuilder;
    private NotificationManager mNotificationManager;

    private DateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private DateFormat dateFormatterNote = new SimpleDateFormat("E HH:mm", Locale.US);
    private DateFormat dateFormatterNice = new SimpleDateFormat("h:mm:ss a", Locale.getDefault());
    private Realm realm;

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
        if (instance != null) {
            instance = null;
        }
        if (mNotificationManager != null) {
            mNotificationManager.cancelAll();
            mNotificationManager = null;
        }
        return;
    }

    public void initNotification(Context context) {
        Log.d(TAG, "initNotification called");

        /*
        RemoteViews remoteViews = new RemoteViews(this.getPackageName(), R.layout.notification);
        remoteViews.setTextViewText(R.id.textView1,"hello!");
        remoteViews.setTextViewText(R.id.textView2,"hows!");
        remoteViews.setTextViewText(R.id.textView3,"it!");
        remoteViews.setTextViewText(R.id.textView4,"hanging!");
        */

        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationBuilder = new  NotificationCompat.Builder(context)
                .setContentTitle("600 Series Uploader")
                .setSmallIcon(R.drawable.ic_notification)
                .setVisibility(VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent);
//                .setContent(remoteViews)
//                .setChannelId(CHANNEL_ID);
//                .setTicker("600 Series nightscout Uploader");
    }

    public void updateNotification(long nextpoll) {
        Log.d(TAG, "updateNotification called");
        realm = Realm.getDefaultInstance();

        String sgv = "";
        String delta = "";
        String bgl = "";
        String iob = "";
        String bolus = "";
        String basal = "";
        String cal = "";

        String poll = "";
        if (nextpoll > 0)
            poll = "Next " + dateFormatterNice.format(nextpoll);

        long now = System.currentTimeMillis();

        long sgvtime = now;
        RealmResults<PumpStatusEvent> cgmresults = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(System.currentTimeMillis() - (24 * 60 * 60 * 1000)))
                .equalTo("validSGV", true)
                .findAllSorted("eventDate", Sort.DESCENDING);
        if (cgmresults.size() > 0) {
            sgv = "SGV " + strFormatSGV(cgmresults.first().getSgv());
            sgvtime = cgmresults.first().getCgmDate().getTime();
            if (cgmresults.size() > 1) {

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(UploaderApplication.getAppContext());
                boolean isMmolxl = prefs.getBoolean("mmolxl", false);

                int diff =  cgmresults.first().getSgv() - cgmresults.get(1).getSgv();
                delta = "" + (diff > 0 ? "+" : "") + new BigDecimal(isMmolxl ? diff / MMOLXLFACTOR : diff).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
        }
        else
            sgv = "No SGV available";
/*
        RealmResults<PumpStatusEvent> cgmresults1 = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(System.currentTimeMillis() - (12 * 60 * 60 * 1000)))
                .equalTo("validBGL", true)
                .findAllSorted("eventDate", Sort.DESCENDING);
        if (cgmresults1.size() > 0)
            bgl = "Last BG: " + strFormatSGV(cgmresults1.first().getRecentBGL()) + " at " + dateFormatterNice.format(cgmresults1.first().getEventDate());

        RealmResults<PumpStatusEvent> cgmresults2 = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(System.currentTimeMillis() - (12 * 60 * 60 * 1000)))
                .equalTo("validBolus", true)
                .findAllSorted("eventDate", Sort.DESCENDING);
        if (cgmresults2.size() > 0)
            bolus = "Last Bolus: " + cgmresults2.first().getLastBolusAmount() + "u at " + dateFormatterNice.format(cgmresults2.first().getLastBolusDate());

        RealmResults<PumpStatusEvent> cgmresults3 = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(System.currentTimeMillis() - (15 * 60 * 1000)))
                .equalTo("validCGM", true)
                .findAllSorted("eventDate", Sort.DESCENDING);
        if (cgmresults3.size() > 0) {
            cal = "Calibration ";
            if (cgmresults3.first().isCgmCalibrating())
                cal = "Calibrating...";
            else if (cgmresults3.first().isCgmCalibrationComplete())
                cal = "Calibration complete";
            else {
                if (cgmresults3.first().isCgmWarmUp())
                    cal = "Warmup ";
                if (cgmresults3.first().getCalibrationDueMinutes() > 0)
                    cal += (cgmresults3.first().getCalibrationDueMinutes() >= 60 ? cgmresults3.first().getCalibrationDueMinutes() / 60 + "h" : "") + cgmresults3.first().getCalibrationDueMinutes() % 60 + "m";
                else
                    cal = "Calibrate now!";
            }
        }
*/

        RealmResults<PumpStatusEvent> cgmresults4 = realm.where(PumpStatusEvent.class)
                .greaterThan("eventDate", new Date(System.currentTimeMillis() - (12 * 60 * 60 * 1000)))
                .findAllSorted("eventDate", Sort.DESCENDING);
        if (cgmresults4.size() > 0) {
            iob = "IOB " + cgmresults4.first().getActiveInsulin() + "u";
            basal = "Basal " + cgmresults4.first().getBasalRate() + "u";
        }

        NotificationCompat.InboxStyle sub = new  NotificationCompat.InboxStyle()
                .addLine(iob)
                .addLine(basal)
                .addLine(bgl)
                .addLine(bolus)
                .setSummaryText(poll + "  " + cal);

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
        mNotificationBuilder.setContentText(iob + "   " + cal);

        mNotificationManager.notify(
                SERVICE_NOTIFICATION_ID,
                mNotificationBuilder.build());

        if (!realm.isClosed()) realm.close();
    }

    private String strFormatSGV(double sgvValue) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(UploaderApplication.getAppContext());
        boolean isMmolxl = prefs.getBoolean("mmolxl", false);
        boolean isMmolxlDecimals = prefs.getBoolean("mmolDecimals", false);

        NumberFormat sgvFormatter;
        if (isMmolxl) {
            if (isMmolxlDecimals) {
                sgvFormatter = new DecimalFormat("0.00");
            } else {
                sgvFormatter = new DecimalFormat("0.0");
            }
            return sgvFormatter.format(sgvValue / MMOLXLFACTOR);
        } else {
            sgvFormatter = new DecimalFormat("0");
            return sgvFormatter.format(sgvValue);
        }
    }

}

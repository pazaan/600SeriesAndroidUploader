package info.nightscout.android.medtronic.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import java.util.Date;

/**
 * Created by lgoedhart on 14/07/2016.
 */
public class MedtronicCnlAlarmReceiver extends WakefulBroadcastReceiver {
    private static final String TAG = MedtronicCnlAlarmReceiver.class.getSimpleName();
    private static final int ALARM_ID = 102; // Alarm id

    private static PendingIntent pi = null;
    private static AlarmManager am = null;

    @Override
    public void onReceive(final Context context, Intent intent) {
        // Start the IntentService
        Log.d(TAG, "Received broadcast message at " + new Date(System.currentTimeMillis()));
        Intent service = new Intent(context, MedtronicCnlIntentService.class);
        startWakefulService(context, service);
        restartAlarm();
    }

    public void setContext(Context context) {
        cancelAlarm();

        am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, MedtronicCnlAlarmReceiver.class);
        pi = PendingIntent.getBroadcast(context, ALARM_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // Setting the alarm in 15 seconds from now
    public void setAlarm() {
        setAlarm(System.currentTimeMillis());
    }

    // Setting the alarm to call onRecieve
    public void setAlarm(long millis) {
        if (am == null || pi == null)
            return;

        cancelAlarm();

        // don't trigger the past and at least 30 sec away
        if (millis < System.currentTimeMillis())
            millis = System.currentTimeMillis();

        Log.d(TAG, "AlarmManager set to fire   at " + new Date(millis));
        am.setExact(AlarmManager.RTC_WAKEUP, millis, pi);
    }

    // restarting the alarm after MedtronicCnlIntentService.POLL_PERIOD_MS from now
    public void restartAlarm() {
        setAlarm(System.currentTimeMillis() + MedtronicCnlIntentService.POLL_PERIOD_MS + MedtronicCnlIntentService.POLL_GRACE_PERIOD_MS);
    }

    // Cancel the alarm.
    public void cancelAlarm() {
        if (am == null || pi == null)
            return;

        am.cancel(pi);
    }

}

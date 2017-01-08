package info.nightscout.android.medtronic.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import java.util.Date;

import info.nightscout.android.medtronic.MainActivity;

/**
 * Created by lgoedhart on 14/07/2016.
 */
public class MedtronicCnlAlarmManager {
    private static final String TAG = MedtronicCnlAlarmManager.class.getSimpleName();
    private static final int ALARM_ID = 102; // Alarm id

    private static PendingIntent pendingIntent = null;
    private static AlarmManager alarmManager = null;
    private static long nextAlarm = Long.MAX_VALUE;

    public static void setContext(Context context) {
        cancelAlarm();

        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, MedtronicCnlAlarmReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(context, ALARM_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // Setting the alarm in 15 seconds from now
    public static void setAlarm() {
        setAlarm(System.currentTimeMillis());
    }

    // Setting the alarm to call onRecieve
    public static void setAlarm(long millis) {
        if (alarmManager == null || pendingIntent == null)
            return;

        long now = System.currentTimeMillis();
        // don't trigger the past
        if (millis < now)
            millis = now;

        // only accept alarm nearer than the last one
        if (nextAlarm < millis && nextAlarm >= now) {
            return;
        }

        cancelAlarm();

        nextAlarm = millis;

        Log.d(TAG, "AlarmManager set to fire   at " + new Date(millis));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(millis, null), pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Android 5.0.0 + 5.0.1 (e.g. Galaxy S4) has a bug.
            // Alarms are not exact. Fixed in 5.0.2 oder CM12
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, millis, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, millis, pendingIntent);
        }
    }

    // restarting the alarm after MedtronicCnlIntentService.POLL_PERIOD_MS from now
    public static void restartAlarm() {
        setAlarm(System.currentTimeMillis() + MainActivity.pollInterval + MedtronicCnlIntentService.POLL_GRACE_PERIOD_MS);
    }

    // Cancel the alarm.
    public static void cancelAlarm() {
        if (alarmManager == null || pendingIntent == null)
            return;

        alarmManager.cancel(pendingIntent);
    }

}

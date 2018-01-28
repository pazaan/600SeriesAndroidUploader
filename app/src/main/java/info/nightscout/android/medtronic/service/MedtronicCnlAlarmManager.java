package info.nightscout.android.medtronic.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Date;

/**
 * Created by lgoedhart on 14/07/2016.
 */
public class MedtronicCnlAlarmManager {
    private static final String TAG = MedtronicCnlAlarmManager.class.getSimpleName();
    private static final int ALARM_ID = 102;

    private static PendingIntent pendingIntent = null;
    private static AlarmManager alarmManager = null;

    public static void setContext(Context context) {
        cancelAlarm();

        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, MedtronicCnlAlarmReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(context, ALARM_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * set the alarm in the future
     *
     * @param inFuture number of millin in the future
     */
    public static void setAlarmAfterMillis(long inFuture) {
        setAlarm(System.currentTimeMillis() + inFuture);
    }

    // Setting the alarm to call onReceive
    public static void setAlarm(long millis) {
        if (alarmManager == null || pendingIntent == null)
            return;

        Log.d(TAG, "request to set Alarm at " + new Date(millis));

        long now = System.currentTimeMillis();
        // don't trigger the past
        if (millis < now)
            millis = now;

        cancelAlarm();

        Log.d(TAG, "Alarm set to fire at " + new Date(millis));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(millis, null), pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Android 5.0.0 + 5.0.1 (e.g. Galaxy S4) has a bug.
            // Alarms are not exact. Fixed in 5.0.2 and CM12
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, millis, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, millis, pendingIntent);
        }
    }

    // restarting the alarm after MedtronicCnlService.POLL_PERIOD_MS from now
    public static void restartAlarm() {
        // Due to potential of some versions of android to mangle alarms and clash with polling times
        // the default alarm reset is set to POLL_PERIOD_MS + 60 seconds
        // It's expected to trigger between polls if alarm has not been honored with a safe margin greater then
        // the around 10 minutes that some OS versions force during sleep
        setAlarmAfterMillis(MedtronicCnlService.POLL_PERIOD_MS + 60000L); // grace already accounted for when using current intent time to set default restart
    }

    // Cancel the alarm.
    public static void cancelAlarm() {
        if (alarmManager == null || pendingIntent == null)
            return;

        alarmManager.cancel(pendingIntent);
    }

}

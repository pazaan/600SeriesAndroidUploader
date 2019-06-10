package info.nightscout.android.utils;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.Log;

import java.math.RoundingMode;
import java.text.DateFormatSymbols;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Locale;

import info.nightscout.android.R;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.model.store.DataStore;
import io.realm.Realm;

import static info.nightscout.android.medtronic.MainActivity.MMOLXLFACTOR;

/**
 * Created by Pogman on 19.04.18.
 */

public class FormatKit {
    private static final String TAG = FormatKit.class.getSimpleName();

    private static FormatKit sInstance;
    private final Application mApplication;

    private FormatKit(Application application) {
        Log.d(TAG, "initialise instance");
        mApplication = application;
    }

    public static FormatKit init(Application application) {
        synchronized (FormatKit.class) {
            if (sInstance == null) {
                sInstance = new FormatKit(application);
            }
            return sInstance;
        }
    }

    public static FormatKit getInstance() {
        if (sInstance == null) throw new NullPointerException(TAG + " instance not initialised");
        return sInstance;
    }

    public String formatAsGrams(Double value) {
        DecimalFormat df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.getDefault()));
        df.setMinimumFractionDigits(0);
        df.setMaximumFractionDigits(1);
        return df.format(value) + mApplication.getApplicationContext().getString(R.string.gram_g);
    }

    public String formatAsExchanges(Double value) {
        DecimalFormat df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.getDefault()));
        df.setMinimumFractionDigits(0);
        df.setMaximumFractionDigits(1);
        return df.format(value) + mApplication.getApplicationContext().getString(R.string.gram_exchange_ex);
    }

    public String formatAsInsulin(Double value) {
        return formatAsInsulin(value, 2);
    }

    public String formatAsInsulin(Double value, int precision) {
        DecimalFormat df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.getDefault()));
        df.setMinimumFractionDigits(0);
        df.setMaximumFractionDigits(precision);
        return df.format(value) + mApplication.getApplicationContext().getString(R.string.insulin_U);
    }

    public String formatAsGlucose(int value) {
        return formatAsGlucose(value, false);
    }

    public String formatAsGlucose(int value, boolean tag) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mApplication.getApplicationContext());
        if (!sharedPreferences.getBoolean(getString(R.string.key_mmolxl), false)) return formatAsGlucoseMGDL(value, tag);
        return formatAsGlucoseMMOL(value, tag, 1);
    }

    public String formatAsGlucose(int value, boolean tag, boolean decimals) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mApplication.getApplicationContext());
        if (!sharedPreferences.getBoolean(getString(R.string.key_mmolxl), false)) return formatAsGlucoseMGDL(value, tag);
        return formatAsGlucoseMMOL(value, tag,
                decimals & sharedPreferences.getBoolean(getString(R.string.key_mmolDecimals), false) ? 2 : 1);
    }

    public String formatAsGlucose(int value, boolean tag, int precision) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mApplication.getApplicationContext());
        if (!sharedPreferences.getBoolean(getString(R.string.key_mmolxl), false)) return formatAsGlucoseMGDL(value, tag);
        return formatAsGlucoseMMOL(value, tag, precision);
    }

    public String formatAsGlucoseMGDL(int value, boolean tag) {
        return String.valueOf(value) + (tag ? " " + mApplication.getApplicationContext().getString(R.string.glucose_mgdl) : "");
    }

    public String formatAsGlucoseMMOL(int value, boolean tag, int precision) {
        DecimalFormat df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.getDefault()));
        df.setMinimumFractionDigits(1);
        df.setMaximumFractionDigits(precision);
        return df.format(value / MMOLXLFACTOR) + (tag ? " " + mApplication.getApplicationContext().getString(R.string.glucose_mmol) : "");
    }

    public String formatAsDecimal(double value, int precision) {
        DecimalFormat df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.getDefault()));
        df.setMinimumFractionDigits(precision);
        df.setMaximumFractionDigits(precision);
        return df.format(value);
    }

    public String formatAsDecimal(double value, int precisionMin, int precisionMax, RoundingMode round) {
        DecimalFormat df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.getDefault()));
        df.setMinimumFractionDigits(precisionMin);
        df.setMaximumFractionDigits(precisionMax);
        df.setRoundingMode(round);
        return df.format(value);
    }

    public String formatAsDecimalDiff(double value, int precision) {
        DecimalFormat df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.getDefault()));
        df.setMinimumFractionDigits(precision);
        df.setMaximumFractionDigits(precision);
        return (value < 0 ? "-" : "+") + df.format(Math.abs(value));
    }

    public String formatSecondsAsDiff(int seconds) {
        if (seconds >= 60 * 60)
            return (seconds < 0 ? "-" : "+") + formatMinutesAsDHM(Math.abs(seconds / 60));
        return (seconds < 0 ? "-" : "+") + formatSecondsAsDHMS(Math.abs(seconds));
    }

    public String formatSecondsAsDHMS(int seconds) {
        int d = seconds / 86400;
        int h = (seconds % 86400) / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return (d > 0 ? d + mApplication.getApplicationContext().getString(R.string.day_d) : "") +
                ((h | d) > 0 ? h + mApplication.getApplicationContext().getString(R.string.hour_h) : "") +
                ((h | d | m) > 0 ? m + mApplication.getApplicationContext().getString(R.string.minute_m) : "") +
                s + mApplication.getApplicationContext().getString(R.string.second_s);
    }

    public String formatMinutesAsDHM(int minutes) {
        int d = minutes / 1440;
        int h = (minutes % 1440) / 60;
        int m = minutes % 60;
        return (d > 0 ? d + mApplication.getApplicationContext().getString(R.string.day_d) : "") +
                ((h | d) > 0 ? h + mApplication.getApplicationContext().getString(R.string.hour_h) : "") +
                m + mApplication.getApplicationContext().getString(R.string.minute_m);
    }

    public String formatMinutesAsHM(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        return (h > 0 ? h + mApplication.getApplicationContext().getString(R.string.hour_h) : "") +
                (m > 0 ? m + mApplication.getApplicationContext().getString(R.string.minute_m) : (h > 0 ? "" : "0" + mApplication.getApplicationContext().getString(R.string.minute_m)));
    }

    public String formatMinutesAsM(int minutes) {
        return minutes + mApplication.getApplicationContext().getString(R.string.minute_m);
    }

    public String formatHoursAsDH(int hours) {
        int d = hours / 24;
        int h = hours % 24;
        return d + mApplication.getApplicationContext().getString(R.string.day_d) +
                h + mApplication.getApplicationContext().getString(R.string.hour_h);
    }

    public String formatHoursAsH(int hours) {
        return hours + mApplication.getApplicationContext().getString(R.string.hour_h);
    }

    public String formatDaysAsD(int days) {
        return days + mApplication.getApplicationContext().getString(R.string.day_d);
    }

    public String formatAsPercent(int value) {
        return Integer.toString(value) + "%";
    }

    public String formatAsClock(long time) {
        if (DateFormat.is24HourFormat(mApplication.getApplicationContext()))
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(time);
        else
            return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(time);
    }

    public String formatAsClockNoAmPm(long time) {
        if (DateFormat.is24HourFormat(mApplication.getApplicationContext()))
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(time);
        else
            return new SimpleDateFormat("h:mm", Locale.getDefault()).format(time);
    }

    public String formatAsClockSeconds(long time) {
        if (DateFormat.is24HourFormat(mApplication.getApplicationContext()))
            return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(time);
        else
            return new SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(time);
    }

    public String formatAsClockSecondsNoAmPm(long time) {
        if (DateFormat.is24HourFormat(mApplication.getApplicationContext()))
            return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(time);
        else
            return new SimpleDateFormat("h:mm:ss", Locale.getDefault()).format(time);
    }

    public String formatAsDayClock(long time) {
        return new SimpleDateFormat("E ", Locale.getDefault()).format(time) + formatAsClock(time);
    }

    public String formatAsDayClockSeconds(long time) {
        return new SimpleDateFormat("E ", Locale.getDefault()).format(time) + formatAsClockSeconds(time);
    }

    public String formatAsClock(int hours, int minutes) {
        DecimalFormat df = new DecimalFormat("00", DecimalFormatSymbols.getInstance(Locale.getDefault()));
        if (DateFormat.is24HourFormat(mApplication.getApplicationContext())) {
            return df.format(hours) + ":" + df.format(minutes);
        } else {
            return (String.format("%s:%s %s",
                    hours > 12 ? hours - 12 : hours,
                    df.format(minutes),
                    DateFormatSymbols.getInstance().getAmPmStrings()[hours < 12 ? 0 : 1]));
        }
    }

    public String formatAsHoursMinutes(int hours, int minutes) {
        DecimalFormat df = new DecimalFormat("00", DecimalFormatSymbols.getInstance(Locale.getDefault()));
        return hours + ":" + df.format(minutes);
    }

    public String formatAsMonth(long time) {
        return new SimpleDateFormat("M", Locale.getDefault()).format(time);
    }

    public String formatAsMonthName(long time) {
        return new SimpleDateFormat("MMMM", Locale.getDefault()).format(time);
    }

    public String formatAsMonthNameShort(long time) {
        return new SimpleDateFormat("MMM", Locale.getDefault()).format(time);
    }

    public String formatAsDay(long time) {
        return new SimpleDateFormat("d", Locale.getDefault()).format(time);
    }

    public String formatAsDayName(long time) {
        return new SimpleDateFormat("EEEE", Locale.getDefault()).format(time);
    }

    public String formatAsDayNameShort(long time) {
        return new SimpleDateFormat("EEE", Locale.getDefault()).format(time);
    }

    public String formatAsDayNameMonthNameDay(long time) {
        return new SimpleDateFormat("EEEE MMMM d", Locale.getDefault()).format(time);
    }

    public String formatAsYMD(long time) {
        return new SimpleDateFormat("yyyy/M/d", Locale.getDefault()).format(time);
    }

    public String formatAsYMDHMS(long time) {
        return new SimpleDateFormat("yyyy/M/d HH:mm:ss", Locale.getDefault()).format(time);
    }

    public String[] getStringArray(int id) {
        return mApplication.getResources().getStringArray(id);
    }

    public String getQuantityString(int id, int value) {
        String s;
        try {
            s = mApplication.getResources().getQuantityString(id, value, value);
        } catch (Exception e) {
            Log.e(TAG, String.format("Could not get string: id = %s", id));
            s = "[string id error]";
        }
        return s;
    }

    public String getQuantityString(String name, int value) {
        int id;
        try {
            Resources res = mApplication.getResources();
            id = res.getIdentifier(
                    name, "string", mApplication.getPackageName());
        } catch (Exception e) {
            Log.e(TAG, String.format("Could not get string: name = %s", name));
            return "[string name error]";
        }
        return getQuantityString(id, value);
    }

    public String getString(int id) {
        String s;
        try {
            s = mApplication.getResources().getString(id);
        } catch (Exception e) {
            Log.e(TAG, String.format("Could not get string: id = %s", id));
            s = "[string id error]";
        }
        return s;
    }

    public String getString(String name) {
        String s;
        try {
            Resources res = mApplication.getResources();
            s = res.getString(res.getIdentifier(
                    name, "string", mApplication.getPackageName()));
        } catch (Exception e) {
            Log.e(TAG, String.format("Could not get string: name = %s", name));
            s = "[string name error]";
        }
        return s;
    }

    public String getNameBasalPattern(int pattern) {
        String name = "";
        try {
            Realm storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
            DataStore dataStore = storeRealm.where(DataStore.class).findFirst();
            name = dataStore.getNameBasalPattern(pattern);
            storeRealm.close();
        } catch (Exception ignored) {}
        return name;
    }

    public String getNameBolusPreset(int preset) {
        String name = "";
        try {
            Realm storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
            DataStore dataStore = storeRealm.where(DataStore.class).findFirst();
            name = dataStore.getNameBolusPreset(preset);
            storeRealm.close();
        } catch (Exception ignored) {}
        return name;
    }

    public String getNameTempBasalPreset(int preset) {
        String name = "";
        try {
            Realm storeRealm = Realm.getInstance(UploaderApplication.getStoreConfiguration());
            DataStore dataStore = storeRealm.where(DataStore.class).findFirst();
            name = dataStore.getNameTempBasalPreset(preset);
            storeRealm.close();
        } catch (Exception ignored) {}
        return name;
    }

    // MongoDB Index Key Limit
    // The total size of an index entry, which can include structural overhead depending on the BSON type,
    // must be less than 1024 bytes.
    public String asMongoDBIndexKeySafe(String string) {
        int length = string.length();

        // json will escape "</div>" to "<\/div"

        String json = string
                .replace("\\", "\\\\")
                .replace("/", "\\/")
                .replace("\"", "\\\"");

        int jsonLength = json.length();

        int utf8Length = utf8Length(json);

        if (utf8Length < 1024) {
            Log.d(TAG, String.format("MongoDBIndexKeySafe: length: %d json: %d utf-8: %s", length, jsonLength, utf8Length));
            return string;
        } else {
            Log.e(TAG, String.format("MongoDBIndexKeySafe: length: %d json: %d utf-8: %s (max bytes >= 1024)", length, jsonLength, utf8Length));
            return "";
        }
    }

    public static int utf8Length(CharSequence sequence) {
        int count = 0;
        for (int i = 0, len = sequence.length(); i < len; i++) {
            char ch = sequence.charAt(i);
            if (ch <= 0x7F) {
                count++;
            } else if (ch <= 0x7FF) {
                count += 2;
            } else if (Character.isHighSurrogate(ch)) {
                count += 4;
                ++i;
            } else {
                count += 3;
            }
        }
        return count;
    }
}
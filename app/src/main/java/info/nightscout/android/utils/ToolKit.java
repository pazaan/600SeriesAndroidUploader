package info.nightscout.android.utils;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import info.nightscout.android.UploaderApplication;

/**
 * Created by John on 26.9.17.
 */

public class ToolKit {
    private static final String TAG = ToolKit.class.getSimpleName();

    private static final boolean debug_wakelocks = true;

    public static PowerManager.WakeLock getWakeLock(final String name, int millis) {
        final PowerManager pm = (PowerManager) UploaderApplication.getAppContext().getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, name);
        wl.acquire(millis);
        if (debug_wakelocks) Log.d(TAG, "getWakeLock: " + name + " " + wl.toString());
        return wl;
    }

    public static void releaseWakeLock(PowerManager.WakeLock wl) {
        if (debug_wakelocks) Log.d(TAG, "releaseWakeLock: " + wl.toString());
        if (wl.isHeld()) wl.release();
    }

    public static int getByteI(byte[] data, int offset) {
        return data[offset];
    }

    public static int getByteIU(byte[] data, int offset) {
        return data[offset] & 0x000000FF;
    }

    public static short getShort(byte[] data, int offset) {
        return (short) (data[offset] << 8 & 0xFF00 | data[offset + 1] & 0x00FF);
    }

    public static int getShortI(byte[] data, int offset) {
        return data[offset] << 8 & 0xFFFFFF00 | data[offset + 1] & 0x000000FF;
    }

    public static int getShortIU(byte[] data, int offset) {
        return data[offset] << 8 & 0x0000FF00 | data[offset + 1] & 0x000000FF;
    }

    public static long getShortL(byte[] data, int offset) {
        return data[offset] << 8 & 0xFFFFFFFFFFFFFF00L | data[offset + 1] & 0x000000FFL;
    }

    public static long getShortLU(byte[] data, int offset) {
        return data[offset] << 8 & 0x0000FF00L | data[offset + 1] & 0x000000FFL;
    }

    public static int getInt(byte[] data, int offset) {
        return data[offset] << 24 & 0xFF000000 | data[offset + 1] << 16 & 0x00FF0000 | data[offset + 2] << 8 & 0x0000FF00 | data[offset + 3] & 0x000000FF;
    }

    public static long getIntL(byte[] data, int offset) {
        return data[offset] << 24 & 0xFF000000 | data[offset + 1] << 16 & 0x00FF0000 | data[offset + 2] << 8 & 0x0000FF00 | data[offset + 3] & 0x000000FF;
    }

    public static long getIntLU(byte[] data, int offset) {
        return data[offset] << 24 & 0xFF000000L | data[offset + 1] << 16 & 0x00FF0000L | data[offset + 2] << 8 & 0x0000FF00L | data[offset + 3] & 0x000000FFL;
    }

    public static String getString(byte[] data, int offset, int size) {
        String string = "";
        for (int i = 0; i < size; i++) {
            string += (char) data[offset + i];
        }
        return string;
    }

}



package info.nightscout.android.utils;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

/**
 * Created by Pogman on 26.9.17.
 */

public class ToolKit {
    private static final String TAG = ToolKit.class.getSimpleName();

    private static final boolean debug_wakelocks = true;

    public static PowerManager.WakeLock getWakeLock(Context context, final String name, int millis) {
        try {
            final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, name);
            wl.acquire(millis);
            if (debug_wakelocks) Log.d(TAG, "getWakeLock: " + name + " " + wl.toString());
            return wl;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void acquireWakelock(PowerManager.WakeLock wl, int millis) {
        try {
            if (wl != null && wl.isHeld()) {
                if (debug_wakelocks) Log.d(TAG, "acquireWakelock: " + wl.toString() + " acquire " + millis + "ms");
                wl.acquire(millis);
            } else {
                if (debug_wakelocks) Log.d(TAG, "acquireWakelock: null / not held");
            }
        } catch (Exception ignored) {
        }
    }

    public static void releaseWakeLock(PowerManager.WakeLock wl) {
        if (debug_wakelocks) Log.d(TAG, "releaseWakeLock: " + wl.toString());
        try {
            if (wl != null && wl.isHeld()) wl.release();
        } catch (Exception ignored) {
        }
    }

    public static short read8toShort(byte[] data, int offset) {
        return (short) data[offset];
    }

    public static short read8toUShort(byte[] data, int offset) {
        return (short) (data[offset] & 0x00FF);
    }

    public static int read8toInt(byte[] data, int offset) {
        return data[offset];
    }

    public static int read8toUInt(byte[] data, int offset) {
        return data[offset] & 0x000000FF;
    }

    public static short read16BEtoShort(byte[] data, int offset) {
        return (short) (data[offset] << 8 & 0xFF00 | data[offset + 1] & 0x00FF);
    }

    public static int read16BEtoInt(byte[] data, int offset) {
        return data[offset] << 8 & 0xFFFFFF00 | data[offset + 1] & 0x000000FF;
    }

    public static int read16BEtoUInt(byte[] data, int offset) {
        return data[offset] << 8 & 0x0000FF00 | data[offset + 1] & 0x000000FF;
    }

    public static long read16BEtoLong(byte[] data, int offset) {
        return data[offset] << 8 & 0xFFFFFFFFFFFFFF00L | data[offset + 1] & 0x000000FFL;
    }

    public static long read16BEtoULong(byte[] data, int offset) {
        return data[offset] << 8 & 0x0000FF00L | data[offset + 1] & 0x000000FFL;
    }

    public static int read32BEtoInt(byte[] data, int offset) {
        return data[offset] << 24 & 0xFF000000 | data[offset + 1] << 16 & 0x00FF0000 | data[offset + 2] << 8 & 0x0000FF00 | data[offset + 3] & 0x000000FF;
    }

    public static long read32BEtoLong(byte[] data, int offset) {
        return data[offset] << 24 & 0xFF000000 | data[offset + 1] << 16 & 0x00FF0000 | data[offset + 2] << 8 & 0x0000FF00 | data[offset + 3] & 0x000000FF;
    }

    public static long read32BEtoULong(byte[] data, int offset) {
        return data[offset] << 24 & 0xFF000000L | data[offset + 1] << 16 & 0x00FF0000L | data[offset + 2] << 8 & 0x0000FF00L | data[offset + 3] & 0x000000FFL;
    }

    public static String readString(byte[] data, int offset, int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append((char) data[offset + i]);
        }
        return sb.toString();
    }

}



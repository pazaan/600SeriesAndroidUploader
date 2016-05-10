package com.nightscout.android.medtronic.message;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class MessageUtils {
    public static byte oneByteSum(byte[] bytes) {
        byte sum = 0;

        for (byte b : bytes) {
            sum += (short) b;
        }

        return sum;
    }

    public static int CRC16CCITT(byte[] data, int initialValue, int polynomial, int bytesToCheck) {
        // From http://introcs.cs.princeton.edu/java/61data/CRC16CCITT.java
        int crc = initialValue;
        for (int c = 0; c < bytesToCheck; c++) {
            byte b = data[c];
            for (int i = 0; i < 8; i++) {
                boolean bit = ((b >> (7 - i) & 1) == 1);
                boolean c15 = ((crc >> 15 & 1) == 1);
                crc <<= 1;
                if (c15 ^ bit) crc ^= polynomial;
            }
        }

        crc &= 0xffff;
        return crc;
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static String byteArrayToHexString(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for(byte b : in) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    public static Date decodeDateTime( long rtc, long offset ) {
        TimeZone currentTz = java.util.Calendar.getInstance().getTimeZone();
        GregorianCalendar gregorianCalendar = new GregorianCalendar(2000, 0, 1, 0, 0, 0);
        gregorianCalendar.setTimeZone(currentTz);

        long epochTime = gregorianCalendar.getTime().getTime();

        Date pumpDate = new Date(epochTime + (( rtc + offset ) * 1000 ) );
        return pumpDate;
    }
}

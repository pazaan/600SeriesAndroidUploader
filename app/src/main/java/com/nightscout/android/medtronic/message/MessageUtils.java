package com.nightscout.android.medtronic.message;

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

    static public short ccittChecksum(byte[] bytes) {
        // From http://stackoverflow.com/questions/7961964/android-crc-ccitt
        short crc = (short) 0xFFFF;
        int temp;
        int crc_byte;

        for (int byte_index = 0; byte_index < bytes.length; byte_index++) {
            crc_byte = bytes[byte_index];

            for (int bit_index = 0; bit_index < 8; bit_index++) {

                temp = ((crc >> 15)) ^ ((crc_byte >> 7));

                crc <<= 1;
                crc &= 0xFFFF;

                if (temp > 0) {
                    crc ^= 0x1021;
                    crc &= 0xFFFF;
                }

                crc_byte <<= 1;
                crc_byte &= 0xFF;
            }
        }

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
}

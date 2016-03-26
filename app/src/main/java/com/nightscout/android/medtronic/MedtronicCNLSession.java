package com.nightscout.android.medtronic;

import com.nightscout.android.medtronic.message.MessageUtils;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class MedtronicCNLSession {
    private final static byte[] HMAC = MessageUtils.hexStringToByteArray("e28fe4e5cf3c1eb6d6a2ec5a093093d4f397237dc60b3f2c1ef64f31e32077c4");
    private final static byte[] KEY = MessageUtils.hexStringToByteArray("57833334130906a587b7a0437bc28a69");

    private final static long linkMAC = 1055866 + 0x0023F70682000000L;
    private final static long pumpMAC = 1057941 + 0x0023F745EE000000L;

    private byte radioChannel;
    private int bayerSequenceNumber = 1;
    private int medtronicSequenceNumber = 1;

    public byte[] getHMAC() {
        return HMAC;
    }

    public static byte[] getKEY() {
        return KEY;
    }
    public byte[] getIV() {
        byte[] iv = HMAC;
        iv[0] = (byte) radioChannel;
        return iv;
    }

    public static long getLinkMAC() {
        return linkMAC;
    }

    public static long getPumpMAC() {
        return pumpMAC;
    }

    public int getBayerSequenceNumber() {
        return bayerSequenceNumber;
    }

    public int getMedtronicSequenceNumber() {
        return medtronicSequenceNumber;
    }

    public byte getRadioChannel() {
        return radioChannel;
    }
}

package com.nightscout.android.medtronic;

import com.nightscout.android.medtronic.message.MessageUtils;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class MedtronicCNLSession {
    // FIXME - Lennart's hard coded key and HMAC
    private final static byte[] HMAC = MessageUtils.hexStringToByteArray("e28fe4e5cf3c1eb6d6a2ec5a093093d4f397237dc60b3f2c1ef64f31e32077c4");
    private final static byte[] KEY = MessageUtils.hexStringToByteArray("57833334130906a587b7a0437bc28a69");

    // FIXME - Lennart's hard coded serial numbers
    private final static long linkMAC = 1055866 + 0x0023F70682000000L;
    private final static long pumpMAC = 1057941 + 0x0023F745EE000000L;

    private byte radioChannel;
    private int bayerSequenceNumber = 1;
    private int medtronicSequenceNumber = 1;

    public byte[] getHMAC() {
        return HMAC;
    }

    public static byte[] getKey() {
        return KEY;
    }
    public byte[] getIV() {
        byte[] iv = new byte[KEY.length];
        System.arraycopy(KEY,0,iv,0,KEY.length);
        iv[0] = radioChannel;
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

    public void incrBayerSequenceNumber() {
        bayerSequenceNumber++;
    }

    public void incrMedtronicSequenceNumber() {
        medtronicSequenceNumber++;
    }

    public void setRadioChannel(byte radioChannel) {
        this.radioChannel = radioChannel;
    }
}

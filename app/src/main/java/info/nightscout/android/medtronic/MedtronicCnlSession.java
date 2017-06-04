package info.nightscout.android.medtronic;

import org.apache.commons.lang3.ArrayUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class MedtronicCnlSession {
    private static final String HMAC_PADDING = "A4BD6CED9A42602564F413123";

    private byte[] HMAC;
    private byte[] key;

    private String stickSerial;

    private long linkMAC;
    private long pumpMAC;

    private byte radioChannel;
    private byte radioRSSI;

    private int bayerSequenceNumber = 1;
    private int medtronicSequenceNumber = 1;

    /*public byte[] getHMAC() {
        return HMAC;
    }*/

    public byte[] getHMAC() throws NoSuchAlgorithmException {
        String shortSerial = this.stickSerial.replaceAll("\\d+-", "");
        byte[] message = (shortSerial + HMAC_PADDING).getBytes();
        byte[] numArray;

        MessageDigest instance = MessageDigest.getInstance("SHA-256");
        instance.update(message);

        numArray = instance.digest();
        ArrayUtils.reverse(numArray);

        return numArray;
    }

    public byte[] getKey() {
        return key;
    }

    public byte[] getIV() {
        byte[] iv = new byte[key.length];
        System.arraycopy(key, 0, iv, 0, key.length);
        iv[0] = radioChannel;
        return iv;
    }

    public long getLinkMAC() {
        return linkMAC;
    }

    public void setLinkMAC(long linkMAC) {
        this.linkMAC = linkMAC;
    }

    public long getPumpMAC() {
        return pumpMAC;
    }

    public void setPumpMAC(long pumpMAC) {
        this.pumpMAC = pumpMAC;
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

    public byte getRadioRSSI() {
        return radioRSSI;
    }

    public int getRadioRSSIpercentage() {
        return (((int) radioRSSI & 0x00FF) * 100) / 0xA8;
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

    public void setRadioRSSI(byte radioRSSI) {
        this.radioRSSI = radioRSSI;
    }

    public void setHMAC(byte[] hmac) {
        this.HMAC = hmac;
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    public String getStickSerial() {
        return stickSerial;
    }

    public void setStickSerial(String stickSerial) {
        this.stickSerial = stickSerial;
    }
}

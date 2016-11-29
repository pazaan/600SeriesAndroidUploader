package info.nightscout.android.medtronic;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class MedtronicCnlSession {
    private byte[] HMAC;
    private byte[] key;

    private long linkMAC;
    private long pumpMAC;

    private byte radioChannel;
    private int bayerSequenceNumber = 1;
    private int medtronicSequenceNumber = 1;

    public byte[] getHMAC() {
        return HMAC;
    }

    public byte[] getKey() {
        return key;
    }
    public byte[] getIV() {
        byte[] iv = new byte[key.length];
        System.arraycopy(key,0,iv,0,key.length);
        iv[0] = radioChannel;
        return iv;
    }

    public long getLinkMAC() {
        return linkMAC;
    }

    public void setLinkMAC( long linkMAC ) {
        this.linkMAC = linkMAC;
    }

    public long getPumpMAC() {
        return pumpMAC;
    }

    public void setPumpMAC( long pumpMAC ) {
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

    public void incrBayerSequenceNumber() {
        bayerSequenceNumber++;
    }

    public void incrMedtronicSequenceNumber() {
        medtronicSequenceNumber++;
    }

    public void setRadioChannel(byte radioChannel) {
        this.radioChannel = radioChannel;
    }

    public void setHMAC( byte[] hmac ) {
        this.HMAC = hmac;
    }

    public void setKey( byte[] key ) {
        this.key = key;
    }
}

package info.nightscout.android.medtronic.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;

/**
 * Created by lgoedhart on 10/05/2016.
 */
public class RequestLinkKeyResponseMessage extends MedtronicResponseMessage {

    private byte[] key;

    protected RequestLinkKeyResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException {
        super(pumpSession, payload);

        ByteBuffer infoBuffer = ByteBuffer.allocate(55);
        infoBuffer.order(ByteOrder.BIG_ENDIAN);
        infoBuffer.put(this.encode(), 0x21, 55);

        setPackedLinkKey(infoBuffer.array());
    }

    public byte[] getKey() {
        return key;
    }

    private void setPackedLinkKey(byte[] packedLinkKey) {
        this.key = new byte[16];

        int pos = mPumpSession.getStickSerial().charAt(mPumpSession.getStickSerial().length() - 1) & 7;

        for (int i = 0; i < this.key.length; i++) {
            if ((packedLinkKey[pos + 1] & 1) == 1) {
                this.key[i] = (byte) ~packedLinkKey[pos];
            } else {
                this.key[i] = packedLinkKey[pos];
            }

            if (((packedLinkKey[pos + 1] >> 1) & 1) == 0) {
                pos += 3;
            } else {
                pos += 2;
            }
        }
    }
}
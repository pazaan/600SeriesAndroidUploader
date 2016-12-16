package info.nightscout.android.medtronic.message;

import android.util.Log;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;

/**
 * Created by lgoedhart on 27/03/2016.
 */
public class ChannelNegotiateResponseMessage extends MedtronicResponseMessage {
    private static final String TAG = ChannelNegotiateResponseMessage.class.getSimpleName();

    private byte radioChannel = 0;

    protected ChannelNegotiateResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException {
        super(pumpSession, payload);

        byte[] responseBytes = this.encode();

        Log.d(TAG, "negotiateChannel: Check response length");
        if (responseBytes.length > 46) {
            radioChannel = responseBytes[76];
        } else {
            radioChannel = ((byte) 0);
        }
    }

    public byte getRadioChannel() {
        return radioChannel;
    }
}

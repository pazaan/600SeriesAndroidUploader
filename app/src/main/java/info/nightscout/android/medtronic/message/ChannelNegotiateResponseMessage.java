package info.nightscout.android.medtronic.message;

import java.io.IOException;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;

/**
 * Created by lgoedhart on 27/03/2016.
 */
public class ChannelNegotiateResponseMessage extends ContourNextLinkBinaryResponseMessage {
    private static final String TAG = ChannelNegotiateResponseMessage.class.getSimpleName();

    private byte radioChannel = 0;
    private byte radioRSSI = 0;

    protected ChannelNegotiateResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException, IOException {
        super(payload);

        byte[] responseBytes = this.encode();

        if (responseBytes.length == 0x4F) {
            if (responseBytes[0x4C] == pumpSession.getRadioChannel()) {
                radioChannel = responseBytes[0x4C];
                radioRSSI = responseBytes[0x3B];
            }
        }
    }

    public byte getRadioChannel() {
        return radioChannel;
    }

    public byte getRadioRSSI() {
        return radioRSSI;
    }
}

package info.nightscout.android.medtronic.message;

import java.io.IOException;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;

/**
 * Created by John on 18.3.18.
 */

public class DiscoveryResponseMessage extends ContourNextLinkBinaryResponseMessage {
    private static final String TAG = DiscoveryResponseMessage.class.getSimpleName();

    protected DiscoveryResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException, IOException {
        super(payload);

        byte[] responseBytes = this.encode();
/*
        Log.d(TAG, "negotiateChannel: Check response length");
        if (responseBytes.length > 46) {
            radioChannel = responseBytes[76];
            radioRSSI = responseBytes[59];
            if (responseBytes[76] != pumpSession.getRadioChannel()) {
                throw new IOException(String.format(Locale.getDefault(), "Expected to get a message for channel %d. Got %d", pumpSession.getRadioChannel(), responseBytes[76]));
            }
        } else {
            radioChannel = ((byte) 0);
            radioRSSI = ((byte) 0);
        }
*/
    }

}

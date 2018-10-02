package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by Pogman on 8.10.17.
 */

public class AckMessage extends MedtronicSendMessageRequestMessage {
    private static final String TAG = AckMessage.class.getSimpleName();

    public AckMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException {
        super(MessageType.ACK_COMMAND, pumpSession, payload);
    }

    public AckMessage send(UsbHidDriver mDevice) throws IOException, TimeoutException, UnexpectedMessageException {
        sendToPump(mDevice,300, TAG);

        return null;
    }
}
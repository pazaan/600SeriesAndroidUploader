package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by Pogman on 10.10.17.
 */

public class MultipacketResendPacketsMessage extends MedtronicSendMessageRequestMessage {
    private static final String TAG = MultipacketResendPacketsMessage.class.getSimpleName();

    public MultipacketResendPacketsMessage(MedtronicCnlSession pumpSession,  byte[] payload) throws EncryptionException, ChecksumException {
        super(MessageType.MULTIPACKET_RESEND_PACKETS, pumpSession, payload);
    }

    public MultipacketResendPacketsMessage send(UsbHidDriver mDevice) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {

        sendToPump(mDevice, TAG);

        return null;
    }
}
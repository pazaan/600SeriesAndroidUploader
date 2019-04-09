package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by volker on 22.12.2016.
 */

public class EHSMMessage extends  MedtronicSendMessageRequestMessage<ContourNextLinkResponseMessage>{
    private static final String TAG = EHSMMessage.class.getSimpleName();

    protected EHSMMessage(MessageType messageType, MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException {
        super(messageType, pumpSession, payload);
    }

    @Override
    public ContourNextLinkResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {
        sendToPump(mDevice, TAG);
        return null;
    }
}

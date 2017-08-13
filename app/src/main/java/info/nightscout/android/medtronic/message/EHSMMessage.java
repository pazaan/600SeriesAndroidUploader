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
    protected EHSMMessage(SendMessageType sendMessageType, MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException {
        super(sendMessageType, pumpSession, payload);
    }

    @Override
    public ContourNextLinkResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, UnexpectedMessageException {

        // clear unexpected incoming messages
        clearMessage(mDevice);

        sendMessage(mDevice);
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
            }
        }

        // The End EHSM Session only has an 0x81 response
        if (readMessage_0x81(mDevice) != 48) {
            throw new UnexpectedMessageException("length of EHSMMessage response does not match");
        }
/*
        readMessage(mDevice);
        if (this.encode().length != 54) {
            throw new UnexpectedMessageException("length of EHSMMessage response does not match");
        }
*/
        return null;
    }
}

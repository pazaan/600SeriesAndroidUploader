package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;

/**
 * Created by volker on 22.12.2016.
 */

public class EHSMMessage extends  MedtronicSendMessageRequestMessage<ContourNextLinkResponseMessage>{
    protected EHSMMessage(SendMessageType sendMessageType, MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException {
        super(sendMessageType, pumpSession, payload);
    }

    @Override
    public ContourNextLinkResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException {
        sendMessage(mDevice);
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
            }
        }
        // The End EHSM Session only has an 0x81 response
        readMessage(mDevice);
        return null;
    }
}

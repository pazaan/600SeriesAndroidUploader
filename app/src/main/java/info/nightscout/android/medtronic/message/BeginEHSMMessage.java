package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class BeginEHSMMessage extends MedtronicSendMessageRequestMessage<ContourNextLinkResponseMessage> {
    public BeginEHSMMessage(MedtronicCnlSession pumpSession) throws EncryptionException, ChecksumException {
        super(SendMessageType.BEGIN_EHSM_SESSION, pumpSession, buildPayload());
    }

    protected static byte[] buildPayload() {
        // Not sure what the payload of a null byte means, but it's the same every time.
        return new byte[] { 0x00 };
    }

    public ContourNextLinkResponseMessage send(UsbHidDriver mDevice) throws IOException, TimeoutException {
        sendMessage(mDevice);

        // The Begin EHSM Session only has an 0x81 response
        readMessage(mDevice);
        return null;
    }
}

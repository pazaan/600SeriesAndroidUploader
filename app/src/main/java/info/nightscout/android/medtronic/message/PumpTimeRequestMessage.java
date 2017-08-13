package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class PumpTimeRequestMessage extends MedtronicSendMessageRequestMessage<PumpTimeResponseMessage> {
    public PumpTimeRequestMessage(MedtronicCnlSession pumpSession) throws EncryptionException, ChecksumException {
        super(SendMessageType.TIME_REQUEST, pumpSession, null);
    }

    @Override
    public PumpTimeResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {

        sendMessage(mDevice);
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
            }
        }
        // Read the 0x81
        readMessage_0x81(mDevice);
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
            }
        }
        // Read the 0x80
        byte[] payload = readMessage(mDevice);
        // if pump sends an unexpected response get the next response as pump can resend or send out of sequence and this avoids comms errors
        if (payload.length < 0x49) {
            payload = readMessage(mDevice);
        }

        // Pump sends additional 0x80 message when not using EHSM, lets clear this and any unexpected incoming messages
        clearMessage(mDevice);

        return this.getResponse(payload);
    }

    @Override
    protected PumpTimeResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException, UnexpectedMessageException {
        return new PumpTimeResponseMessage(mPumpSession, payload);
    }
}
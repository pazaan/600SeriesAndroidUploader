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
public class PumpStatusRequestMessage extends MedtronicSendMessageRequestMessage<PumpStatusResponseMessage> {
    private static final String TAG = PumpStatusRequestMessage.class.getSimpleName();

    public PumpStatusRequestMessage(MedtronicCnlSession pumpSession) throws EncryptionException, ChecksumException {
        super(MessageType.READ_PUMP_STATUS, pumpSession, null);
    }

    public PumpStatusResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {
        sendToPump(mDevice, mPumpSession, TAG);
        return getResponse(readFromPump(mDevice, mPumpSession, TAG));
    }

    @Override
    protected PumpStatusResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException, UnexpectedMessageException {
        return new PumpStatusResponseMessage(mPumpSession, payload);
    }
}


    /*

    public PumpStatusResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {
        sendMessage(mDevice);
        if (millis > 0) {
            try {
                Log.d(TAG, "waiting " + millis +" ms");
                Thread.sleep(millis);
            } catch (InterruptedException e) {
            }
        }
        byte[] payload;

        // Read the 0x80
        payload = readMessage(mDevice);
        if(payload[0x12] != (byte) 0x80 || payload.length == 0x26) {
            Log.e(TAG, "invalid message (updatePumpStatus 0x80 response)");
            throw new UnexpectedMessageException("invalid message (updatePumpStatus 0x80 response)");
        }
        // Check for unexpected response and get the next response as it may resend or send out of sequence and this avoids comms errors
        if (payload.length < 0x9C) {
            payload = readMessage(mDevice);
            if(payload[0x12] != (byte) 0x80 || payload.length == 0x26) {
                Log.e(TAG, "invalid message (updatePumpStatus 0x80 response 2nd message)");
                throw new UnexpectedMessageException("invalid message (updatePumpStatus 0x80 response 2nd message)");
            }
        }

        // Additional 0x80 message can be sent when not using EHSM, lets clear this and any unexpected incoming messages
//        clearMessage(mDevice);
        return this.getResponse(payload);
    }
*/


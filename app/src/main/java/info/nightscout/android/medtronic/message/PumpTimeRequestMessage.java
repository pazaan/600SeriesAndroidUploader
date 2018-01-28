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
    private static final String TAG = PumpTimeRequestMessage.class.getSimpleName();

    public PumpTimeRequestMessage(MedtronicCnlSession pumpSession) throws EncryptionException, ChecksumException {
        super(MessageType.READ_PUMP_TIME, pumpSession, null);
    }

    @Override
    public PumpTimeResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {
        sendToPump(mDevice, mPumpSession, TAG);
        return getResponse(readFromPump(mDevice, mPumpSession, TAG));
    }

    @Override
    protected PumpTimeResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException, UnexpectedMessageException {
        return new PumpTimeResponseMessage(mPumpSession, payload);
    }
}



/*
    @Override
    public PumpTimeResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {
        byte[] payload;
        /*
        sendMessage(mDevice);
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
            }
        }

        // Read the 0x80
        payload = readMessage(mDevice);
        if(payload[0x12] != (byte) 0x80 || payload.length == 0x26) {
            Log.e(TAG, "invalid message (getPumpTime 0x80 response)");
            throw new UnexpectedMessageException("invalid message (getPumpTime 0x80 response)");
        }
        // Check for unexpected response and get the next response as it may resend or send out of sequence and this avoids comms errors
        if (payload.length < 0x49) {
            payload = readMessage(mDevice);
            if(payload[0x12] != (byte) 0x80 || payload.length == 0x26) {
                Log.e(TAG, "invalid message (getPumpTime 0x80 response 2nd message)");
                throw new UnexpectedMessageException("invalid message (getPumpTime 0x80 response 2nd message)");
            }
        }

        // Additional 0x80 message can be sent when not using EHSM, lets clear this and any unexpected incoming messages
//        clearMessage(mDevice);
        return this.getResponse(payload);
    }
*/

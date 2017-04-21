package info.nightscout.android.medtronic.message;

import android.util.Log;

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
        super(SendMessageType.READ_PUMP_STATUS_REQUEST, pumpSession, null);
    }

    public PumpStatusResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {
        sendMessage(mDevice);
        if (millis > 0) {
            try {
                Log.d(TAG, "waiting " + millis +" ms");
                Thread.sleep(millis);
            } catch (InterruptedException e) {
            }
        }
        // Read the 0x81
        readMessage(mDevice);
        if (millis > 0) {
            try {
                Log.d(TAG, "waiting " + millis +" ms");
                Thread.sleep(millis);
            } catch (InterruptedException e) {
            }
        }
        PumpStatusResponseMessage response = this.getResponse(readMessage(mDevice));

        return response;
    }

    @Override
    protected PumpStatusResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException, UnexpectedMessageException {
        return new PumpStatusResponseMessage(mPumpSession, payload);
    }
}

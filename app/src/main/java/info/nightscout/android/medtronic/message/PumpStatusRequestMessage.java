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

    // TODO - this needs refactoring
    public PumpStatusResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {
        byte payload[];

        sendMessage(mDevice);
        if (millis > 0) {
            try {
                Log.d(TAG, "waiting " + millis +" ms");
                Thread.sleep(millis);
            } catch (InterruptedException e) {
            }
        }
        // Read the 0x81
        payload = readMessage_0x81(mDevice);
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
            }
        }

        // TODO confirm this CNL crash fix
        // attempted catch of very rare CNL crash as CNL seems to want a connection closed immediately after this type of 0x81 message or else stops responding soon after
        if (payload[0x1B] > 0) {
            //if (payload[0x1B] > 0 && payload[0x1C] == 0) {
            clearMessage(mDevice);
            throw new UnexpectedMessageException("connection lost during getPumpStatus");
        }

        // Read the 0x80
        payload = readMessage(mDevice);
        // if pump sends an unexpected response get the next response as pump can resend or send out of sequence and this avoids comms errors
        if (payload.length < 0x9C) {
            payload = readMessage(mDevice);
        }

        // clear unexpected incoming messages
        clearMessage(mDevice);

        return this.getResponse(payload);
    }

    @Override
    protected PumpStatusResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException, UnexpectedMessageException {
        return new PumpStatusResponseMessage(mPumpSession, payload);
    }
}

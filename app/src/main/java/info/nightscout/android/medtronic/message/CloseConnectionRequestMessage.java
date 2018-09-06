package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.utils.HexDump;

/**
 * Created by volker on 10.12.2016.
 */

public class CloseConnectionRequestMessage extends ContourNextLinkBinaryRequestMessage<CloseConnectionResponseMessage> {
    private static final String TAG = CloseConnectionRequestMessage.class.getSimpleName();

    public CloseConnectionRequestMessage(MedtronicCnlSession pumpSession, byte[] payload) throws ChecksumException {
        super(CommandType.CLOSE_CONNECTION, pumpSession, payload);
    }

    @Override
    public CloseConnectionResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {

//        clearMessage(mDevice, CLEAR_TIMEOUT_MS);
        clearMessage(mDevice, PRESEND_CLEAR_TIMEOUT_MS);

        sendMessage(mDevice);
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ignored) {
            }
        }

        byte payload[];
        while (true) {
            payload = readMessage(mDevice);
            if (payload.length < 0x21)
                Log.e(TAG, "response message size less then expected, length = " + payload.length);
            else if ((payload[0x12] & 0xFF) != 0x11)
                Log.e(TAG, "response message not a 0x11, got a 0x" + HexDump.toHexString(payload[0x12]));
            else break;
        }

        return this.getResponse(payload);

/*
        sendMessage(mDevice);
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ignored) {
            }
        }

        return this.getResponse(readMessage(mDevice));
*/
    }

    @Override
    protected CloseConnectionResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException {
        return new CloseConnectionResponseMessage(payload);
    }
}

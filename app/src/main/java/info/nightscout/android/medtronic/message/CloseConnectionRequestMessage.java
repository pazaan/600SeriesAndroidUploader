package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by volker on 10.12.2016.
 */

public class CloseConnectionRequestMessage extends ContourNextLinkBinaryRequestMessage<CloseConnectionResponseMessage> {
    public CloseConnectionRequestMessage(MedtronicCnlSession pumpSession, byte[] payload) throws ChecksumException {
        super(CommandType.CLOSE_CONNECTION, pumpSession, payload);
    }

    @Override
    public CloseConnectionResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {

        clearMessage(mDevice, CLEAR_TIMEOUT_MS);

        sendMessage(mDevice);
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ignored) {
            }
        }

        return this.getResponse(readMessage(mDevice));
    }

    @Override
    protected CloseConnectionResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException {
        return new CloseConnectionResponseMessage(payload);
    }
}

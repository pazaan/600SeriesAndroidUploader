package info.nightscout.android.medtronic.message;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;

/**
 * Created by volker on 10.12.2016.
 */

public class OpenConnectionRequestMessage extends ContourNextLinkBinaryRequestMessage<OpenConnectionResponseMessage> {
    public OpenConnectionRequestMessage(MedtronicCnlSession pumpSession, byte[] payload) throws ChecksumException {
        super(CommandType.OPEN_CONNECTION, pumpSession, payload);
    }
/*
    @Override
    public OpenConnectionResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {

        // clear unexpected incoming messages
        clearMessage(mDevice, 100);

        sendMessage(mDevice);
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
            }
        }

        return this.getResponse(readMessage(mDevice));
    }
*/
    @Override
    protected OpenConnectionResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException {
        return new OpenConnectionResponseMessage(payload);
    }
}

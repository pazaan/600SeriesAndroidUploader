package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;

/**
 * Created by volker on 10.12.2016.
 */

public class OpenConnectionRequestMessage extends ContourNextLinkBinaryRequestMessage {
    public OpenConnectionRequestMessage(MedtronicCnlSession pumpSession, byte[] payload) throws ChecksumException {
        super(CommandType.OPEN_CONNECTION, pumpSession, payload);
    }

    public OpenConnectionResponseMessage send(UsbHidDriver mDevice) throws IOException, TimeoutException, EncryptionException, ChecksumException {
        sendMessage(mDevice);

        OpenConnectionResponseMessage response = new OpenConnectionResponseMessage(readMessage(mDevice));

        // FIXME - We need to care what the response message is - wrong MAC and all that
        return response;
    }
}

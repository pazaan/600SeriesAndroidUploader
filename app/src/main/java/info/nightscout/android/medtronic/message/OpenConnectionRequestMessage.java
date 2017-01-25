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

public class OpenConnectionRequestMessage extends ContourNextLinkBinaryRequestMessage<OpenConnectionResponseMessage> {
    public OpenConnectionRequestMessage(MedtronicCnlSession pumpSession, byte[] payload) throws ChecksumException {
        super(CommandType.OPEN_CONNECTION, pumpSession, payload);
    }

    @Override
    protected OpenConnectionResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException {
        return new OpenConnectionResponseMessage(payload);
    }
}

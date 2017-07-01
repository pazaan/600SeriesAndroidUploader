package info.nightscout.android.medtronic.message;

import java.io.IOException;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;

/**
 * Created by volker on 10.12.2016.
 */

public class ReadInfoRequestMessage extends ContourNextLinkBinaryRequestMessage<ReadInfoResponseMessage> {
    public ReadInfoRequestMessage(MedtronicCnlSession pumpSession) throws ChecksumException {
        super(ContourNextLinkBinaryRequestMessage.CommandType.READ_INFO, pumpSession, null);
    }

    @Override
    protected ReadInfoResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException {
        return new ReadInfoResponseMessage(mPumpSession, payload);
    }
}

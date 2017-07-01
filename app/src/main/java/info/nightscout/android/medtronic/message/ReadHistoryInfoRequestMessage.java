package info.nightscout.android.medtronic.message;

import java.io.IOException;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class ReadHistoryInfoRequestMessage extends MedtronicSendMessageRequestMessage<ReadHistoryInfoResponseMessage> {
    public ReadHistoryInfoRequestMessage(MedtronicCnlSession pumpSession) throws EncryptionException, ChecksumException {
        super(SendMessageType.READ_BASAL_PATTERN_REQUEST, pumpSession, new byte[] {
                2,
                3,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0
        });
    }

    @Override
    protected ReadHistoryInfoResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException, UnexpectedMessageException {
        return new ReadHistoryInfoResponseMessage(mPumpSession, payload);
    }
}

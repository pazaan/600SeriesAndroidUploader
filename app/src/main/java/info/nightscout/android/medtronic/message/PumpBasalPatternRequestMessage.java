package info.nightscout.android.medtronic.message;

import java.io.IOException;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class PumpBasalPatternRequestMessage extends MedtronicSendMessageRequestMessage<PumpBasalPatternResponseMessage> {
    public PumpBasalPatternRequestMessage(MedtronicCnlSession pumpSession) throws EncryptionException, ChecksumException {
        super(SendMessageType.READ_BASAL_PATTERN_REQUEST, pumpSession, null);
    }

    @Override
    protected PumpBasalPatternResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException, UnexpectedMessageException {
        return new PumpBasalPatternResponseMessage(mPumpSession, payload);
    }
}

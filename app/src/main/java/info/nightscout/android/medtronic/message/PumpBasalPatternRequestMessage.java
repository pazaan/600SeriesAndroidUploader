package info.nightscout.android.medtronic.message;

import info.nightscout.android.medtronic.MedtronicCnlSession;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class PumpBasalPatternRequestMessage extends MedtronicSendMessage {
    public PumpBasalPatternRequestMessage(MedtronicCnlSession pumpSession) throws EncryptionException {
        super(SendMessageType.READ_BASAL_PATTERN_REQUEST, pumpSession, null);
    }
}

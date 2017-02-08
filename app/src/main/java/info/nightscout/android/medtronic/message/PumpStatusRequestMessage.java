package info.nightscout.android.medtronic.message;

import info.nightscout.android.medtronic.MedtronicCnlSession;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class PumpStatusRequestMessage extends MedtronicSendMessage {
    public PumpStatusRequestMessage(MedtronicCnlSession pumpSession) throws EncryptionException {
        super(SendMessageType.READ_PUMP_STATUS_REQUEST, pumpSession, null);
    }
}

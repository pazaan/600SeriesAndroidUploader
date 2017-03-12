package info.nightscout.android.medtronic.message;

import info.nightscout.android.medtronic.MedtronicCnlSession;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class EndEHSMMessage extends MedtronicSendMessage {
    public EndEHSMMessage(MedtronicCnlSession pumpSession) throws EncryptionException {
        super(SendMessageType.END_EHSM_SESSION, pumpSession, buildPayload());
    }

    protected static byte[] buildPayload() {
        // Not sure what the payload byte means, but it's the same every time.
        return new byte[] { 0x01 };
    }
}

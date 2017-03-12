package info.nightscout.android.medtronic.message;

import info.nightscout.android.medtronic.MedtronicCnlSession;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class BeginEHSMMessage extends MedtronicSendMessage {
    public BeginEHSMMessage(MedtronicCnlSession pumpSession) throws EncryptionException {
        super(SendMessageType.BEGIN_EHSM_SESSION, pumpSession, buildPayload());
    }

    protected static byte[] buildPayload() {
        // Not sure what the payload of a null byte means, but it's the same every time.
        return new byte[] { 0x00 };
    }
}

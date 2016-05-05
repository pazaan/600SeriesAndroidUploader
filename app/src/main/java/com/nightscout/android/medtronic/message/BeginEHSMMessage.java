package com.nightscout.android.medtronic.message;

import com.nightscout.android.medtronic.MedtronicCNLSession;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class BeginEHSMMessage extends MedtronicSendMessage {
    public BeginEHSMMessage(MedtronicCNLSession pumpSession) throws EncryptionException {
        super(SendMessageType.BEGIN_EHSM_SESSION, pumpSession, buildPayload());
    }

    protected static byte[] buildPayload() {
        // Not sure what the payload of a null byte means, but it's the same every time.
        return new byte[] { 0x00 };
    }
}

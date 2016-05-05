package com.nightscout.android.medtronic.message;

import com.nightscout.android.medtronic.MedtronicCNLSession;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class PumpTimeRequestMessage extends MedtronicSendMessage {
    public PumpTimeRequestMessage(MedtronicCNLSession pumpSession) throws EncryptionException {
        super(SendMessageType.TIME_REQUEST, pumpSession, null);
    }
}

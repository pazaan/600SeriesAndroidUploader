package com.nightscout.android.medtronic.message;

import com.nightscout.android.medtronic.MedtronicCNLSession;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class PumpStatusRequestMessage extends MedtronicSendMessage {
    public PumpStatusRequestMessage(MedtronicCNLSession pumpSession) throws EncryptionException {
        super(SendMessageType.READ_PUMP_STATUS_REQUEST, pumpSession, null);
    }
}

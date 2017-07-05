package info.nightscout.android.medtronic.message;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;

/**
 * Created by volker on 18.12.2016.
 */

public class MedtronicSendMessageResponseMessage extends MedtronicResponseMessage {
    protected MedtronicSendMessageResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException {
        super(pumpSession, payload);
    }
}

package info.nightscout.android.medtronic.message;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;

/**
 * Created by lgoedhart on 10/05/2016.
 */
public class OpenConnectionResponseMessage extends MedtronicResponseMessage {
    protected OpenConnectionResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws ChecksumException, EncryptionException {
        super(pumpSession, payload);
    }

}
package info.nightscout.android.medtronic.message;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class MedtronicResponseMessage extends ContourNextLinkResponseMessage {
    private static final String TAG = MedtronicResponseMessage.class.getSimpleName();

    protected MedtronicCnlSession mPumpSession;

    protected MedtronicResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException {
        super(payload);

        mPumpSession = pumpSession;
    }
}
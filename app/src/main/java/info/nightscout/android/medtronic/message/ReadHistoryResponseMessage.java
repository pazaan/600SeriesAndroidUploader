package info.nightscout.android.medtronic.message;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by Pogman on 6.10.17.
 */

public class ReadHistoryResponseMessage extends MedtronicSendMessageResponseMessage {
    private static final String TAG = ReadHistoryResponseMessage.class.getSimpleName();

    private byte[] eventData;

    protected ReadHistoryResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException, UnexpectedMessageException {
        super(pumpSession, payload);

        eventData = payload;
    }

    public byte[] getEventData() {
        return eventData;
    }
}


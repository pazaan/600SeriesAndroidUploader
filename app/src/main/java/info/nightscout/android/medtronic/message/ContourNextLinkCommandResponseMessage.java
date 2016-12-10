package info.nightscout.android.medtronic.message;

import info.nightscout.android.medtronic.MedtronicCnlSession;

/**
 * Created by volker on 10.12.2016.
 */
public class ContourNextLinkCommandResponseMessage extends ContourNextLinkBinaryMessage {

    public ContourNextLinkCommandResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws ChecksumException {
        super(CommandType.NO_TYPE, pumpSession, payload);
    }
}

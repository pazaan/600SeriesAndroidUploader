package info.nightscout.android.medtronic.message;

import info.nightscout.android.medtronic.MedtronicCnlSession;

/**
 * Created by lgoedhart on 27/03/2016.
 */
public class PumpBasalPatternResponseMessage extends MedtronicReceiveMessage {
    protected PumpBasalPatternResponseMessage(CommandType commandType, CommandAction commandAction, MedtronicCnlSession pumpSession, byte[] payload) {
        super(commandType, commandAction, pumpSession, payload);
    }

    public static ContourNextLinkMessage fromBytes(MedtronicCnlSession pumpSession, byte[] bytes) throws ChecksumException, EncryptionException {
        // TODO - turn this into a factory
        ContourNextLinkMessage message = MedtronicReceiveMessage.fromBytes(pumpSession, bytes);

        // TODO - Validate the MessageType

        return message;
    }
}

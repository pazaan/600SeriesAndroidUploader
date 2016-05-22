package info.nightscout.android.medtronic.message;

import info.nightscout.android.medtronic.MedtronicCNLSession;

/**
 * Created by lgoedhart on 27/03/2016.
 */
public class PumpTimeResponseMessage extends MedtronicReceiveMessage {
    protected PumpTimeResponseMessage(CommandType commandType, CommandAction commandAction, MedtronicCNLSession pumpSession, byte[] payload) {
        super(commandType, commandAction, pumpSession, payload);
    }

    public static ContourNextLinkMessage fromBytes(MedtronicCNLSession pumpSession, byte[] bytes) throws ChecksumException, EncryptionException {
        // TODO - turn this into a factory
        ContourNextLinkMessage message = MedtronicReceiveMessage.fromBytes(pumpSession, bytes);

        // TODO - Validate the MessageType

        return message;
    }
}

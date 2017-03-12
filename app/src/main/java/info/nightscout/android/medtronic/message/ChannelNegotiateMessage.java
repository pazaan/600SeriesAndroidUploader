package info.nightscout.android.medtronic.message;

import info.nightscout.android.medtronic.MedtronicCnlSession;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class ChannelNegotiateMessage extends MedtronicMessage {
    public ChannelNegotiateMessage(MedtronicCnlSession pumpSession) {
        super(CommandType.SEND_MESSAGE, CommandAction.CHANNEL_NEGOTIATE, pumpSession, buildPayload(pumpSession));
    }

    protected static byte[] buildPayload( MedtronicCnlSession pumpSession ) {
        ByteBuffer payload = ByteBuffer.allocate(26);
        payload.order(ByteOrder.LITTLE_ENDIAN);
        // The MedtronicMessage sequence number is always sent as 1 for this message,
        // even though the sequence should keep incrementing as normal
        payload.put((byte) 1);
        payload.put(pumpSession.getRadioChannel());
        byte[] unknownBytes = {0, 0, 0, 0x07, 0x07, 0, 0, 0x02};
        payload.put(unknownBytes);
        payload.putLong(pumpSession.getLinkMAC());
        payload.putLong(pumpSession.getPumpMAC());

        return payload.array();
    }
}

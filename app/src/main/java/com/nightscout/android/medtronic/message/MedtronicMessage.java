package com.nightscout.android.medtronic.message;

import com.nightscout.android.medtronic.MedtronicCNLSession;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class MedtronicMessage extends ContourNextLinkBinaryMessage {
    static int ENVELOPE_SIZE = 2;
    static int CRC_SIZE = 2;
    protected CommandAction mCommandAction = CommandAction.NO_TYPE;

    public enum CommandAction {
        NO_TYPE(0x0),
        CHANNEL_NEGOTIATE(0x03),
        PUMP_REQUEST(0x05),
        PUMP_RESPONSE(0x55);

        private byte value;

        CommandAction(int commandAction) {
            value = (byte) commandAction;
        }
    }

    protected MedtronicMessage(CommandType commandType, CommandAction commandAction, MedtronicCNLSession pumpSession) {
        super(commandType, pumpSession, null);
        mCommandAction = commandAction;
    }

    @Override
    protected void setPayload(byte[] payload) {
        /*
          MedtronicMessage:
          +---------------+-------------------+----------------------+--------------------+
          | CommandAction | byte Payload Size | byte[] Payload bytes | LE short CCITT CRC |
          +---------------+-------------------+----------------------+--------------------+
         */

        byte payloadLength = (byte) (payload == null ? 0 : payload.length);

        ByteBuffer payloadBuffer = ByteBuffer.allocate( ENVELOPE_SIZE + payloadLength + CRC_SIZE );
        payloadBuffer.order(ByteOrder.LITTLE_ENDIAN);

        payloadBuffer.put(mCommandAction.value);
        payloadBuffer.put(payloadLength);
        if (payloadLength != 0 ) {
            payloadBuffer.put(payload);
        }
        payloadBuffer.putShort(MessageUtils.ccittChecksum(payloadBuffer.array()));

        super.setPayload( payloadBuffer.array() );
    }
}

package com.nightscout.android.medtronic.message;

import com.nightscout.android.medtronic.MedtronicCNLSession;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class MedtronicSendMessage extends MedtronicMessage {
    static int ENVELOPE_SIZE = 11;
    static int ENCRYPTED_ENVELOPE_SIZE = 3;
    static int CRC_SIZE = 2;

    public enum SendMessageType {
        NO_TYPE(0x0),
        BEGIN_EHSM_SESSION(0x412),
        TIME_REQUEST(0x0403),
        READ_PUMP_STATUS_REQUEST(0x0112),
        END_EHSM_SESSION(0x412);

        private short value;

        SendMessageType(int messageType) {
            value = (short) messageType;
        }
    }

    protected MedtronicSendMessage(SendMessageType sendMessageType, MedtronicCNLSession pumpSession, byte[] payload) throws EncryptionException {
        super(CommandType.SEND_MESSAGE, CommandAction.PUMP_REQUEST, pumpSession, buildPayload(sendMessageType, pumpSession, payload));
    }

    /**
     * MedtronicSendMessage:
     * +-----------------+------------------------------+--------------+-------------------+--------------------------------+
     * | LE long pumpMAC | byte medtronicSequenceNumber | byte unknown | byte Payload size | byte[] Encrypted Payload bytes |
     * +-----------------+------------------------------+--------------+-------------------+--------------------------------+
     * <p/>
     * MedtronicSendMessage (decrypted payload):
     * +-------------------------+----------------------+----------------------+--------------------+
     * | byte sendSequenceNumber | BE short sendMessageType | byte[] Payload bytes | BE short CCITT CRC |
     * +-------------------------+----------------------+----------------------+--------------------+
     */
    protected static byte[] buildPayload(SendMessageType sendMessageType, MedtronicCNLSession pumpSession, byte[] payload) throws EncryptionException {
        byte payloadLength = (byte) (payload == null ? 0 : payload.length);

        ByteBuffer sendPayloadBuffer = ByteBuffer.allocate(ENCRYPTED_ENVELOPE_SIZE + payloadLength + CRC_SIZE);
        sendPayloadBuffer.order(ByteOrder.BIG_ENDIAN); // I know, this is the default - just being explicit

        sendPayloadBuffer.put(sendSequenceNumber(sendMessageType));
        sendPayloadBuffer.putShort(sendMessageType.value);
        if (payloadLength != 0) {
            sendPayloadBuffer.put(payload);
        }

        sendPayloadBuffer.putShort((short) MessageUtils.CRC16CCITT(sendPayloadBuffer.array(), 0xffff, 0x1021, ENCRYPTED_ENVELOPE_SIZE + payloadLength));

        ByteBuffer payloadBuffer = ByteBuffer.allocate( ENVELOPE_SIZE + sendPayloadBuffer.capacity() );
        payloadBuffer.order(ByteOrder.LITTLE_ENDIAN);

        payloadBuffer.putLong(pumpSession.getPumpMAC());
        payloadBuffer.put((byte) pumpSession.getMedtronicSequenceNumber());
        payloadBuffer.put((byte) 0x10);
        payloadBuffer.put((byte) sendPayloadBuffer.capacity());
        payloadBuffer.put(encrypt( pumpSession.getKey(), pumpSession.getIV(), sendPayloadBuffer.array()));

        return payloadBuffer.array();
    }

    protected static byte sendSequenceNumber(SendMessageType sendMessageType) {
        switch (sendMessageType) {
            case BEGIN_EHSM_SESSION:
                return (byte) 0x80;
            case TIME_REQUEST:
                return (byte) 0x02;
            case READ_PUMP_STATUS_REQUEST:
                return (byte) 0x03;
            default:
                return 0x00;
        }
    }
}

package com.nightscout.android.medtronic.message;

import com.nightscout.android.medtronic.MedtronicCNLSession;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class ContourNextLinkBinaryMessage {
    protected ByteBuffer mBayerEnvelope;
    protected ByteBuffer mBayerPayload;
    protected MedtronicCNLSession mPumpSession;
    protected CommandType mCommandType = CommandType.NO_TYPE;

    static int ENVELOPE_SIZE = 33;

    public enum CommandType {
        NO_TYPE(0x0),
        OPEN_CONNECTION(0x10),
        CLOSE_CONNECTION(0x11),
        SEND_MESSAGE(0x12),
        READ_INFO(0x14),
        REQUEST_LINK_KEY(0x16),
        SEND_LINK_KEY(0x17),
        RECEIVE_MESSAGE(0x80),
        SEND_MESSAGE_RESPONSE(0x81),
        REQUEST_LINK_KEY_RESPONSE(0x86);

        private byte value;

        CommandType(int commandType) {
            value = (byte) commandType;
        }
    }

    public ContourNextLinkBinaryMessage(CommandType commandType, MedtronicCNLSession pumpSession, byte[] payload) {
        mPumpSession = pumpSession;

        setPayload(payload);
    }

    protected void setPayload(byte[] payload) {
        if( payload != null ) {
            mBayerPayload = ByteBuffer.allocate( payload.length);
            mBayerPayload.put(payload);
        }

        mBayerEnvelope = ByteBuffer.allocate(ENVELOPE_SIZE);
        mBayerEnvelope.order(ByteOrder.LITTLE_ENDIAN);
        mBayerEnvelope.put((byte) 0x51);
        mBayerEnvelope.put((byte) 0x3);
        mBayerEnvelope.put("000000".getBytes()); // Text of Pump serial, but 000000 for 640g
        byte[] unknownBytes = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        mBayerEnvelope.put(unknownBytes);
        mBayerEnvelope.put(mCommandType.value);
        mBayerEnvelope.putInt(mPumpSession.getBayerSequenceNumber());
        byte[] unknownBytes2 = {0, 0, 0, 0, 0};
        mBayerEnvelope.put(unknownBytes2);
        mBayerEnvelope.putInt(mBayerPayload == null ? 0 : mBayerPayload.capacity());
        mBayerEnvelope.put(messageCrc());
    }

    private byte messageCrc() {
        byte sum = MessageUtils.oneByteSum(mBayerEnvelope.array());
        // Don't include the checkum byte in the checksum calculation!
        sum -= mBayerEnvelope.get(32);
        if (mBayerPayload != null) {
            sum += MessageUtils.oneByteSum(mBayerPayload.array());
        }
        return sum;
    }

    public byte[] encode() {
        if (mBayerPayload != null) {
            ByteBuffer out = ByteBuffer.allocate(mBayerEnvelope.capacity() + mBayerPayload.capacity());
            out.put(mBayerEnvelope.array());
            out.put(mBayerPayload.array());
            return out.array();
        } else {
            return mBayerEnvelope.array();
        }
    }

    public static ContourNextLinkBinaryMessage fromBytes(byte[] bytes) throws ChecksumException {
        ContourNextLinkBinaryMessage message = new ContourNextLinkBinaryMessage(CommandType.NO_TYPE, null, null);
        message.mBayerEnvelope = ByteBuffer.allocate(ENVELOPE_SIZE);
        message.mBayerEnvelope.put(bytes, 0, ENVELOPE_SIZE);
        int payloadSize = bytes.length - ENVELOPE_SIZE;
        message.mBayerPayload = ByteBuffer.allocate( payloadSize);
        message.mBayerPayload.put(bytes, ENVELOPE_SIZE, payloadSize);

        // Validate checksum
        byte messageChecksum = message.mBayerEnvelope.get(32);
        byte calculatedChecksum = message.messageCrc();

        if (messageChecksum != calculatedChecksum) {
            throw new ChecksumException(String.format("Expected to get %d. Got %d", (int) calculatedChecksum, (int) messageChecksum));
        }

        return message;
    }
}

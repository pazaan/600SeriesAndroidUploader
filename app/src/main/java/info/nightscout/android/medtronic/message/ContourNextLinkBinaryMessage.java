package info.nightscout.android.medtronic.message;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class ContourNextLinkBinaryMessage extends ContourNextLinkMessage {
    //protected ByteBuffer mBayerEnvelope;
    //protected ByteBuffer mBayerPayload;
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

        public int getValue() {
            return value;
        }
    }

    public ContourNextLinkBinaryMessage(CommandType commandType, MedtronicCnlSession pumpSession, byte[] payload) throws ChecksumException {
        super(buildPayload(commandType, pumpSession, payload));
        mCommandType = commandType;
        mPumpSession = pumpSession;

        // Validate checksum
        byte messageChecksum = this.mPayload.get(32);
        byte calculatedChecksum = (byte) (MessageUtils.oneByteSum(this.mPayload.array()) - messageChecksum);

        if (messageChecksum != calculatedChecksum) {
            throw new ChecksumException(String.format(Locale.getDefault(), "Expected to get %d. Got %d", (int) calculatedChecksum, (int) messageChecksum));
        }
    }

    public static ContourNextLinkMessage fromBytes(byte[] bytes) throws ChecksumException {
        ContourNextLinkMessage message = new ContourNextLinkMessage(bytes);
        message.validate();

        return message;
    }


    public void checkControlMessage(byte controlCharacter) throws IOException, TimeoutException, UnexpectedMessageException {
        checkControlMessage(mPayload.array(), controlCharacter);
    }


    /**
     * Handle incrementing sequence number
     *
     * @param mDevice
     * @throws IOException
     */
    protected void sendMessage(UsbHidDriver mDevice) throws IOException {
        super.sendMessage(mDevice);
        mPumpSession.incrBayerSequenceNumber();
    }

    protected static byte[] buildPayload(CommandType commandType, MedtronicCnlSession pumpSession, byte[] payload) {
        int payloadLength = payload == null ? 0 : payload.length;

        ByteBuffer payloadBuffer = ByteBuffer.allocate( ENVELOPE_SIZE + payloadLength );
        payloadBuffer.order(ByteOrder.LITTLE_ENDIAN);

        payloadBuffer.put((byte) 0x51);
        payloadBuffer.put((byte) 0x3);
        payloadBuffer.put("000000".getBytes()); // Text of PumpInfo serial, but 000000 for 640g
        byte[] unknownBytes = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        payloadBuffer.put(unknownBytes);
        payloadBuffer.put(commandType.value);
        payloadBuffer.putInt(pumpSession.getBayerSequenceNumber());
        byte[] unknownBytes2 = {0, 0, 0, 0, 0};
        payloadBuffer.put(unknownBytes2);
        payloadBuffer.putInt(payloadLength);
        payloadBuffer.put((byte) 0); // Placeholder for the CRC

        if( payloadLength != 0 ) {
            payloadBuffer.put(payload);
        }

        // Now that we have the payload, calculate the message CRC
        payloadBuffer.position(32);
        payloadBuffer.put(MessageUtils.oneByteSum(payloadBuffer.array()));

        return payloadBuffer.array();
    }

    protected void validate(ContourNextLinkMessage message)  throws ChecksumException {
        // Validate checksum
        byte messageChecksum = message.mPayload.get(32);
        byte calculatedChecksum = (byte) (MessageUtils.oneByteSum(message.mPayload.array()) - messageChecksum);

        if (messageChecksum != calculatedChecksum) {
            throw new ChecksumException(String.format(Locale.getDefault(), "Expected to get %d. Got %d", (int) calculatedChecksum, (int) messageChecksum));
        }
    }

    protected void checkControlMessage(byte[] msg, byte controlCharacter) throws IOException, TimeoutException, UnexpectedMessageException {
        if (msg.length != 1 || msg[0] != controlCharacter) {
            throw new UnexpectedMessageException(String.format(Locale.getDefault(), "Expected to get control character '%d' Got '%d'.",
                    (int) controlCharacter, (int) msg[0]));
        }
    }

}

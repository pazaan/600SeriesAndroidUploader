package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class ContourNextLinkBinaryRequestMessage extends ContourNextLinkRequestMessage {
    private final static int ENVELOPE_SIZE = 33;

    //protected ByteBuffer mBayerEnvelope;
    //protected ByteBuffer mBayerPayload;
    protected CommandType mCommandType = CommandType.NO_TYPE;
    protected MedtronicCnlSession mPumpSession;

    public ContourNextLinkBinaryRequestMessage(CommandType commandType, MedtronicCnlSession pumpSession, byte[] payload) throws ChecksumException {
        super(buildPayload(commandType, pumpSession, payload));

        this.mPumpSession = pumpSession;
        this.mCommandType = commandType;

        // Validate checksum
        byte messageChecksum = this.mPayload.get(32);
        byte calculatedChecksum = (byte) (MessageUtils.oneByteSum(this.mPayload.array()) - messageChecksum);

        if (messageChecksum != calculatedChecksum) {
            throw new ChecksumException(String.format(Locale.getDefault(), "Expected to get %d. Got %d", (int) calculatedChecksum, (int) messageChecksum));
        }
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
        payloadBuffer.put(commandType.getValue());
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

}

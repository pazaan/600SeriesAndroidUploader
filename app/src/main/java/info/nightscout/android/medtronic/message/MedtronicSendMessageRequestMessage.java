package info.nightscout.android.medtronic.message;

import android.util.Log;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.utils.HexDump;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
/**
 * Created by volker on 18.12.2016.
 */

public abstract class MedtronicSendMessageRequestMessage<T>  extends MedtronicRequestMessage<T> {
    private static final String TAG = MedtronicSendMessageRequestMessage.class.getSimpleName();

    static int ENVELOPE_SIZE = 11;
    static int ENCRYPTED_ENVELOPE_SIZE = 3;
    static int CRC_SIZE = 2;

    public enum MessageType {
        EHSM_SESSION(0x0412, 0x0412),
        CHANGE_PUMP_TIME(0x0404, 0x0405),
        READ_PUMP_TIME(0x0403, 0x0407),
        READ_PUMP_STATUS(0x0112, 0x013C),
        READ_BASAL_PATTERN(0x0116, 0x0123),
        READ_BOLUS_WIZARD_CARB_RATIOS(0x012B, 0x012C),
        READ_BOLUS_WIZARD_SENSITIVITY_FACTORS(0x012E, 0x012F),
        READ_BOLUS_WIZARD_BG_TARGETS(0x0131, 0x0132),
        READ_DEVICE_STRING(0x013A, 0x013B),
        READ_DEVICE_CHARACTERISTICS(0x0200, 0x0201),
        READ_HISTORY_INFO(0x030C, 0x030D),
        READ_HISTORY(0x0304, 0x0305),
        END_HISTORY_TRANSMISSION(0x030A, 0x030A),
        UNMERGED_HISTORY(0x030E, 0x030E),
        INITIATE_MULTIPACKET_TRANSFER(0xFF00, 0xFF00),
        MULTIPACKET_SEGMENT_TRANSMISSION(0xFF01, 0xFF01),
        MULTIPACKET_RESEND_PACKETS(0xFF02, 0xFF02),
        ACK_COMMAND(0x00FE ,0x00FE),
        NAK_COMMAND(0x00FF ,0x00FF),
        NO_TYPE(0x0000, 0x0000);

        private short request, response;

        MessageType(int request, int response) {
            this.request = (short) request;
            this.response = (short) response;
        }

        public boolean response(int response) {
            return (this.response & 0x0000FFFF) == response;
        }

        public byte[] response() {
            return new byte[]{(byte) (this.request >> 8), (byte) (this.request)};
        }

        public static MessageType convert(short value) {
            for (MessageType messageType : MessageType.values())
                if (messageType.response == value) return messageType;
            return MessageType.NO_TYPE;
        }
    }

    protected MedtronicSendMessageRequestMessage(MessageType messageType, MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException {
        super(CommandType.SEND_MESSAGE, CommandAction.TRANSMIT_PACKET, pumpSession, buildPayload(messageType, pumpSession, payload));
    }

    @Override
    protected ContourNextLinkResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException, UnexpectedMessageException {
        return null;
    }

    /**
     * MedtronicSendMessage:
     * +-----------------+------------------------------+--------------+-------------------+--------------------------------+
     * | LE long pumpMAC | byte medtronicSequenceNumber | byte unknown | byte Payload size | byte[] Encrypted Payload bytes |
     * +-----------------+------------------------------+--------------+-------------------+--------------------------------+
     * <p/>
     * MedtronicSendMessage (decrypted payload):
     * +-------------------------+--------------------------+----------------------+--------------------+
     * | byte sendSequenceNumber | BE short messageType | byte[] Payload bytes | BE short CCITT CRC |
     * +-------------------------+--------------------------+----------------------+--------------------+
     */
    protected static byte[] buildPayload(MessageType messageType, MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException {
        byte payloadLength = (byte) (payload == null ? 0 : payload.length);

        ByteBuffer sendPayloadBuffer = ByteBuffer.allocate(ENCRYPTED_ENVELOPE_SIZE + payloadLength + CRC_SIZE);
        sendPayloadBuffer.order(ByteOrder.BIG_ENDIAN); // I know, this is the default - just being explicit

        // note: pazaan tidepool docs have encrypted/speed mode flags the other way around but testing indicates that this is the way it should be
        // 0x10 always needed for encryption or else there is a timeout
        // 0x01 optional but using this does increase comms speed without needing to engage EHSM session request
        // 0x01 must be set when EHSM session is operational or risk pump radio channel changing
        // I suspect that BeginEHSM / EndEHSM are only ever needed if bulk data is being sent to pump!
        // The 0x01 flag may only be absolutely required when the pump sends a EHSM request during multi-packet transfers

        byte modeFlags = 0x10; // encrypted mode

        if (messageType == MessageType.EHSM_SESSION) {
            sendPayloadBuffer.put((byte) 0x80);
        } else {
            sendPayloadBuffer.put(pumpSession.getComDSequenceNumber());
            pumpSession.incrComDSequenceNumber();
            //if (pumpSession.getEHSMmode()) modeFlags |= 0x01; // high speed mode
            modeFlags |= 0x01; // high speed mode
        }

        sendPayloadBuffer.putShort(messageType.request);
        if (payloadLength != 0) {
            sendPayloadBuffer.put(payload);
        }

        sendPayloadBuffer.putShort((short) MessageUtils.CRC16CCITT(sendPayloadBuffer.array(), 0xFFFF, 0x1021, ENCRYPTED_ENVELOPE_SIZE + payloadLength));

        ByteBuffer payloadBuffer = ByteBuffer.allocate( ENVELOPE_SIZE + sendPayloadBuffer.capacity() );
        payloadBuffer.order(ByteOrder.LITTLE_ENDIAN);

        payloadBuffer.putLong(pumpSession.getPumpMAC());
        payloadBuffer.put(pumpSession.getMedtronicSequenceNumber());
        payloadBuffer.put(modeFlags);
        payloadBuffer.put((byte) sendPayloadBuffer.capacity());

        String outputString = HexDump.dumpHexString(sendPayloadBuffer.array());
        Log.d(TAG, String.format("*** REQUEST: %s (%04X) PAYLOAD: %s", messageType.name(), messageType.request, outputString));

        payloadBuffer.put(encrypt( pumpSession.getKey(), pumpSession.getIV(), sendPayloadBuffer.array()));

        return payloadBuffer.array();
    }

}

package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class MedtronicRequestMessage extends ContourNextLinkBinaryRequestMessage {
    static int ENVELOPE_SIZE = 2;
    static int CRC_SIZE = 2;

    protected MedtronicRequestMessage(CommandType commandType, CommandAction commandAction, MedtronicCnlSession pumpSession, byte[] payload) throws ChecksumException {
        super(commandType, pumpSession, buildPayload(commandAction, payload));
    }

    public enum SendMessageType {
        BEGIN_EHSM_SESSION(0x412),
        TIME_REQUEST(0x0403),
        READ_PUMP_STATUS_REQUEST(0x0112),
        READ_BASAL_PATTERN_REQUEST(0x0112),
        END_EHSM_SESSION(0x412),

        READ_HISTORY_INFO_MESSAGE(0x030C),
        READ_HISTORY_MESSAGE(0x0304),
        READ_TRACE_HISTORY_MESSAGE(0x0302),

        INITIATE_MULTIPACKET_TRANSFER_COMMAND(0xFF00),

        NO_TYPE(0x0);

        private short value;

        SendMessageType(int messageType) {
            value = (short) messageType;
        }

        public short getValue() {
            return value;
        }

        public boolean equals(short value) {
            return this.value == value;
        }
    }

    /**
     * MedtronicMessage:
     * +---------------+-------------------+----------------------+--------------------+
     * | CommandAction | byte Payload Size | byte[] Payload bytes | LE short CCITT CRC |
     * +---------------+-------------------+----------------------+--------------------+
     */
    protected static byte[] buildPayload(CommandAction commandAction, byte[] payload) {
        byte payloadLength = (byte) (payload == null ? 0 : payload.length);

        ByteBuffer payloadBuffer = ByteBuffer.allocate(ENVELOPE_SIZE + payloadLength + CRC_SIZE);
        payloadBuffer.order(ByteOrder.LITTLE_ENDIAN);

        payloadBuffer.put(commandAction.getValue());
        payloadBuffer.put((byte) (ENVELOPE_SIZE + payloadLength));
        if (payloadLength != 0) {
            payloadBuffer.put(payload != null ? payload : new byte[0]);
        }

        payloadBuffer.putShort((short) MessageUtils.CRC16CCITT(payloadBuffer.array(), 0xffff, 0x1021, ENVELOPE_SIZE + payloadLength));

        return payloadBuffer.array();
    }


    // TODO - maybe move the SecretKeySpec, IvParameterSpec and Cipher construction into the PumpSession?
    protected static byte[] encrypt(byte[] key, byte[] iv, byte[] clear) throws EncryptionException {
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        byte[] encrypted = new byte[0];

        try {
            Cipher cipher = Cipher.getInstance("AES/CFB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec);
            encrypted = cipher.doFinal(clear);
        } catch (Exception e) {
            throw new EncryptionException( "Could not encrypt Medtronic Message" );
        }
        return encrypted;
    }

    protected void sendMessage(UsbHidDriver mDevice) throws IOException {
        super.sendMessage(mDevice);
        mPumpSession.incrMedtronicSequenceNumber();
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

package com.nightscout.android.medtronic.message;

import com.nightscout.android.medtronic.MedtronicCNLSession;

import java.nio.ByteBuffer;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class MedtronicReceiveMessage extends MedtronicMessage {
    static int ENVELOPE_SIZE = 22;
    static int ENCRYPTED_ENVELOPE_SIZE = 3;
    static int CRC_SIZE = 2;

    protected MedtronicReceiveMessage(CommandType commandType, CommandAction commandAction, MedtronicCNLSession pumpSession, byte[] payload) {
        super(commandType, commandAction, pumpSession, payload);
    }

    public enum ReceiveMessageType {
        NO_TYPE(0x0),
        TIME_RESPONSE(0x407);

        private short value;

        ReceiveMessageType(int messageType) {
            value = (short) messageType;
        }
    }

    /**
     * MedtronicReceiveMessage:
     * +------------------+-----------------+-----------------+---------------------------------+-------------------+--------------------------------+
     * | LE short unknown | LE long pumpMAC | LE long linkMAC | byte[3] responseSequenceNumber? | byte Payload size | byte[] Encrypted Payload bytes |
     * +------------------+-----------------+-----------------+---------------------------------+-------------------+--------------------------------+
     * <p/>
     * MedtronicReceiveMessage (decrypted payload):
     * +----------------------------+-----------------------------+----------------------+--------------------+
     * | byte receiveSequenceNumber | BE short receiveMessageType | byte[] Payload bytes | BE short CCITT CRC |
     * +----------------------------+-----------------------------+----------------------+--------------------+
     */
    public static ContourNextLinkMessage fromBytes(MedtronicCNLSession pumpSession, byte[] bytes) throws ChecksumException, EncryptionException {
        // TODO - turn this into a factory
        ContourNextLinkMessage message = MedtronicMessage.fromBytes(bytes);

        // TODO - Validate the message, inner CCITT, serial numbers, etc

        // If there's not 57 bytes, then we got back a bad message. Not sure how to process these yet.
        if( bytes.length >= 57 ) {
            // Replace the encrypted bytes by their decrypted equivalent (same block size)
            byte encryptedPayloadSize = bytes[56];

            ByteBuffer encryptedPayload = ByteBuffer.allocate(encryptedPayloadSize);
            encryptedPayload.put(bytes, 57, encryptedPayloadSize);
            byte[] decryptedPayload = decrypt(pumpSession.getKey(), pumpSession.getIV(), encryptedPayload.array());

            // Now that we have the decrypted payload, rewind the mPayload, and overwrite the bytes
            // TODO - because this messes up the existing CCITT, do we want to have a separate buffer for the decrypted payload?
            // Should be fine provided we check the CCITT first...
            message.mPayload.position(57);
            message.mPayload.put(decryptedPayload);
        }
        return message;
    }
}

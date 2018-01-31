package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import info.nightscout.android.BuildConfig;
import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.medtronic.service.MedtronicCnlService;
import info.nightscout.android.utils.HexDump;

import static info.nightscout.android.utils.ToolKit.read16BEtoShort;
import static info.nightscout.android.utils.ToolKit.read32BEtoInt;
import static info.nightscout.android.utils.ToolKit.read16BEtoUInt;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public abstract class ContourNextLinkMessage {
    private static final String TAG = ContourNextLinkMessage.class.getSimpleName();

    public static final int CLEAR_TIMEOUT_MS = 1000; // note: 2000ms was used for v5.1
    public static final int ERROR_CLEAR_TIMEOUT_MS = 2000;
    public static final int PRESEND_CLEAR_TIMEOUT_MS = 100;

    public static final int READ_TIMEOUT_MS = 10000;
    public static final int CNL_READ_TIMEOUT_MS = 2000;
    public static final int PUMP_READ_TIMEOUT_MS = 10000;

    private static final int MULTIPACKET_TIMEOUT_MS = 1000;
    private static final int SEGMENT_RETRY = 10;

    private static final int USB_BLOCKSIZE = 64;
    private static final String USB_HEADER = "ABC";

    protected ByteBuffer mPayload;

    public enum CommandAction {
        NO_TYPE(0x0),
        CHANNEL_NEGOTIATE(0x03),
        PUMP_REQUEST(0x05),
        PUMP_RESPONSE(0x55);

        private byte value;

        CommandAction(int commandAction) {
            value = (byte) commandAction;
        }

        public byte getValue() {
            return value;
        }

        public boolean equals(byte value) {
            return this.value == value;
        }
    }

    public enum CommandType {
        OPEN_CONNECTION(0x10),
        CLOSE_CONNECTION(0x11),
        SEND_MESSAGE(0x12),
        READ_INFO(0x14),
        REQUEST_LINK_KEY(0x16),
        SEND_LINK_KEY(0x17),
        RECEIVE_MESSAGE(0x80),
        SEND_MESSAGE_RESPONSE(0x81),
        REQUEST_LINK_KEY_RESPONSE(0x86),

        NO_TYPE(0x0);

        private byte value;

        CommandType(int commandType) {
            value = (byte) commandType;
        }

        public byte getValue() {
            return value;
        }

        public boolean equals(byte value) {
            return this.value == value;
        }
    }

    protected ContourNextLinkMessage(byte[] bytes) {
        setPayload(bytes);
    }

    public byte[] encode() {
        return mPayload.array();
    }

    // FIXME - get rid of this - make a Builder instead
    protected void setPayload(byte[] payload) {
        if (payload != null) {
            mPayload = ByteBuffer.allocate(payload.length);
            mPayload.put(payload);
        }
    }

    protected void sendMessage(UsbHidDriver mDevice) throws IOException {
        int pos = 0;
        byte[] message = this.encode();

        while (message.length > pos) {
            ByteBuffer outputBuffer = ByteBuffer.allocate(USB_BLOCKSIZE);
            int sendLength = (pos + 60 > message.length) ? message.length - pos : 60;
            outputBuffer.put(USB_HEADER.getBytes());
            outputBuffer.put((byte) sendLength);
            outputBuffer.put(message, pos, sendLength);

            mDevice.write(outputBuffer.array(), 200);
            pos += sendLength;

            String outputString = HexDump.dumpHexString(outputBuffer.array());
            Log.d(TAG, "WRITE: " + outputString);
        }
    }

    protected byte[] readMessage(UsbHidDriver mDevice) throws IOException, TimeoutException {
        return readMessage(mDevice, READ_TIMEOUT_MS);
    }

    protected byte[] readMessage(UsbHidDriver mDevice, int timeout) throws IOException, TimeoutException {
        ByteArrayOutputStream responseMessage = new ByteArrayOutputStream();

        byte[] responseBuffer = new byte[USB_BLOCKSIZE];
        int bytesRead;
        int messageSize = 0;

        do {
            bytesRead = mDevice.read(responseBuffer, timeout);

            if (bytesRead == -1) {
                throw new TimeoutException("Timeout waiting for response from pump");
            } else if (bytesRead > 0) {
                // Validate the header
                ByteBuffer header = ByteBuffer.allocate(3);
                header.put(responseBuffer, 0, 3);
                String headerString = new String(header.array());
                if (!headerString.equals(USB_HEADER)) {
                    throw new IOException("Unexpected header received");
                }
                messageSize = responseBuffer[3];
                responseMessage.write(responseBuffer, 4, messageSize);
            } else {
                Log.w(TAG, "readMessage: got a zero-sized response.");
            }
        } while (bytesRead > 0 && messageSize == 60);

        String responseString = HexDump.dumpHexString(responseMessage.toByteArray());
        Log.d(TAG, "READ: " + responseString);

        return responseMessage.toByteArray();
    }

    // safety check to make sure a expected 0x81 response is received before next expected 0x80 response
    // very infrequent as clearMessage catches most issues but very important to save a CNL error situation

    protected byte[] readMessage_0x81(UsbHidDriver mDevice) throws IOException, TimeoutException {
        return readMessage_0x81(mDevice, READ_TIMEOUT_MS);
    }

    protected byte[] readMessage_0x81(UsbHidDriver mDevice, int timeout) throws IOException, TimeoutException {

        byte[] responseBytes;
        boolean doRetry;
        do {
            responseBytes = readMessage(mDevice, timeout);
            if (responseBytes[18] != (byte) 0x81) {
                doRetry = true;
                Log.d(TAG, "readMessage0x81: did not get 0x81 response, got " + HexDump.toHexString(responseBytes[18]));
                MedtronicCnlService.cnl0x81++;
            } else {
                doRetry = false;
            }

        } while (doRetry);

        return responseBytes;
    }

    // intercept unexpected messages from the CNL
    // these usually come from pump requests as it can occasionally resend message responses several times (possibly due to a missed CNL ACK during CNL-PUMP comms?)
    // mostly noted on the higher radio channels, channel 26 shows this the most
    // if these messages are not cleared the CNL will likely error needing to be unplugged to reset as it expects them to be read before any further commands are sent

    protected int clearMessage(UsbHidDriver mDevice) throws IOException {
        return clearMessage(mDevice, CLEAR_TIMEOUT_MS);
    }

    protected int clearMessage(UsbHidDriver mDevice, int timeout) throws IOException {
        int count = 0;
        boolean cleared = false;

        do {
            try {
                readMessage(mDevice, timeout);
                count++;
                MedtronicCnlService.cnlClear++;
            } catch (TimeoutException e) {
                cleared = true;
            }
        } while (!cleared);

        if (count > 0) {
            Log.d(TAG, "clearMessage: message stream cleared " + count + " messages.");
        }

        return count;
    }

    public enum ASCII {
        STX(0x02),
        EOT(0x04),
        ENQ(0x05),
        ACK(0x06),
        NAK(0x15);

        protected byte value;

        ASCII(int code) {
            this.value = (byte) code;
        }

        public byte getValue() {
            return value;
        }

        public boolean equals(byte value) {
            return this.value == value;
        }
    }



    protected byte[] sendToPump(UsbHidDriver mDevice, MedtronicCnlSession pumpSession, String tag) throws IOException, TimeoutException, EncryptionException, ChecksumException, UnexpectedMessageException {
        tag = " (" + tag + ")";
        byte[] payload;
        byte medtronicSequenceNumber = pumpSession.getMedtronicSequenceNumber();

        // extra safety check and delay, CNL is not happy when we miss incoming messages
        // the short delay may also help with state readiness
        clearMessage(mDevice, PRESEND_CLEAR_TIMEOUT_MS);

        sendMessage(mDevice);

        try {
            payload = readMessage_0x81(mDevice);
        } catch (TimeoutException e) {
            // ugh... there should always be a CNL 0x81 response and if we don't get one it usually ends with a E86 / E81 error on the CNL needing a unplug/plug cycle
            Log.e(TAG, "Timeout waiting for 0x81 response." + tag);
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new TimeoutException("Timeout waiting for 0x81 response" + tag);
        }

        Log.d(TAG, "0x81 response: payload.length=" + payload.length + (payload.length >= 0x30 ? " payload[0x21]=" + payload[0x21] + " payload[0x2C]=" + payload[0x2C] + " medtronicSequenceNumber=" + medtronicSequenceNumber + " payload[0x2D]=" + payload[0x2D] : "") + tag);

        // following errors usually have no further response from the CNL but occasionally they do
        // and these need to be read and cleared asap or yep E86 me baby and a unresponsive CNL
        // the extra delay from the clearMessage timeout may be helping here too by holding back any further downstream sends etc - investigate
        if (payload.length <= 0x21) {
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException("0x81 response was empty" + tag);  // *bad* CNL death soon after this, may want to end comms immediately
        } else if (payload.length != 0x30 && payload[0x21] != 0x55) {
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException("0x81 response was not a 0x55 message" + tag);
        } else if (payload[0x2C] != medtronicSequenceNumber) {
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException("0x81 sequence number does not match" + tag);
        } else if (payload[0x2D] == 0x04) {
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException("0x81 connection busy" + tag);
        } else if (payload[0x2D] != 0x02) {
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException("0x81 connection lost" + tag);
        }

        return payload;
    }

    protected byte[] readFromPump(UsbHidDriver mDevice, MedtronicCnlSession pumpSession, String tag) throws IOException, TimeoutException, EncryptionException, ChecksumException, UnexpectedMessageException {
        tag = " (" + tag + ")";

        MultipacketSession multipacketSession = null;
        byte[] tupple;
        byte[] payload = null;
        byte[] decrypted = null;

        boolean fetchMoreData = true;
        int retry = 0;
        int expectedSegments = 0;

        int cmd;

        while (fetchMoreData) {
            if (multipacketSession != null) {
                do {
                    if (expectedSegments < 1) {
                        tupple = multipacketSession.missingSegments();
                        new MultipacketResendPacketsMessage(pumpSession, tupple).send(mDevice);
                        expectedSegments = read16BEtoUInt(tupple, 0x02);
                    }
                    try {
                        payload = readMessage(mDevice, MULTIPACKET_TIMEOUT_MS);
                        retry = 0;
                    } catch (TimeoutException e) {
                        if (++retry >= SEGMENT_RETRY)
                            throw new TimeoutException("Timeout waiting for response from pump (multipacket)" + tag);
                        Log.d(TAG, "*** Multisession timeout, expecting:" + expectedSegments + " retry: " + retry);
                        expectedSegments = 0;
                    }
                } while (retry > 0);
            } else {
                try {
                    payload = readMessage(mDevice, READ_TIMEOUT_MS);
                } catch (TimeoutException e) {
                    throw new TimeoutException("Timeout waiting for response from pump" + tag);
                }
            }

            if (payload.length < 0x0039) {
                Log.d(TAG, "*** bad response" + HexDump.dumpHexString(payload, 0x12, payload.length - 0x14));
                fetchMoreData = true;

            } else {
                decrypted = decode(pumpSession, payload);

                cmd = read16BEtoUInt(decrypted, RESPONSE_COMMAND);
                Log.d(TAG, "CMD: " + HexDump.toHexString(cmd));

                if (MedtronicSendMessageRequestMessage.MessageType.EHSM_SESSION.response(cmd)) { // EHSM_SESSION(0)
                    Log.d(TAG, "*** EHSM response" + HexDump.dumpHexString(decrypted));
                    fetchMoreData = true;
                } else if (MedtronicSendMessageRequestMessage.MessageType.NAK_COMMAND.response(cmd)) {
                    Log.d(TAG, "*** NAK response" + HexDump.dumpHexString(decrypted));
                    clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS); // if multipacket was in progress we may need to clear 2 EHSM_SESSION(1) messages from pump
                    short nakcmd = read16BEtoShort(decrypted, 3);
                    byte nakcode = decrypted[5];
                    throw new UnexpectedMessageException("Pump sent a NAK(" + String.format("%02X", nakcmd) + ":" + String.format("%02X", nakcode) + ") response" + tag);
                    //fetchMoreData = false;
                } else if (MedtronicSendMessageRequestMessage.MessageType.INITIATE_MULTIPACKET_TRANSFER.response(cmd)) {
                    multipacketSession = new MultipacketSession(decrypted);
                    new AckMessage(pumpSession, MedtronicSendMessageRequestMessage.MessageType.INITIATE_MULTIPACKET_TRANSFER.response()).send(mDevice);
                    expectedSegments = multipacketSession.packetsToFetch;
                    fetchMoreData = true;
                } else if (MedtronicSendMessageRequestMessage.MessageType.MULTIPACKET_SEGMENT_TRANSMISSION.response(cmd)) {
                    if (multipacketSession == null) throw new UnexpectedMessageException("multipacketSession not initiated before segment received" + tag);
                    multipacketSession.addSegment(decrypted);
                    expectedSegments--;

                    if (multipacketSession.payloadComplete()) {
                        Log.d(TAG, "*** Multisession Complete");
                        new AckMessage(pumpSession, MedtronicSendMessageRequestMessage.MessageType.MULTIPACKET_SEGMENT_TRANSMISSION.response()).send(mDevice);

                        // read 0412 = EHSM_SESSION(1)
                        payload = readMessage(mDevice, READ_TIMEOUT_MS);
                        decrypted = decode(pumpSession, payload);
                        Log.d(TAG, "*** response" + HexDump.dumpHexString(decrypted));

                        return multipacketSession.response;
                        //fetchMoreData = false;
                    } else
                        fetchMoreData = true;

                } else if (MedtronicSendMessageRequestMessage.MessageType.END_HISTORY_TRANSMISSION.response(cmd)) {
                    Log.d(TAG, "*** END_HISTORY_TRANSMISSION response" + HexDump.dumpHexString(decrypted));
                    fetchMoreData = false;
                } else if (MedtronicSendMessageRequestMessage.MessageType.READ_PUMP_TIME.response(cmd)) {
                    Log.d(TAG, "*** READ_PUMP_TIME response" + HexDump.dumpHexString(decrypted));
                    fetchMoreData = false;
                } else if (MedtronicSendMessageRequestMessage.MessageType.READ_PUMP_STATUS.response(cmd)) {
                    Log.d(TAG, "*** READ_PUMP_STATUS response" + HexDump.dumpHexString(decrypted));
                    fetchMoreData = false;
                } else if (MedtronicSendMessageRequestMessage.MessageType.READ_HISTORY_INFO.response(cmd)) {
                    Log.d(TAG, "*** READ_HISTORY_INFO response" + HexDump.dumpHexString(decrypted));
                    fetchMoreData = false;
                } else if (MedtronicSendMessageRequestMessage.MessageType.READ_BASAL_PATTERN.response(cmd)) {
                    Log.d(TAG, "*** READ_BASAL_PATTERN response" + HexDump.dumpHexString(decrypted));
                    fetchMoreData = false;
                } else if (MedtronicSendMessageRequestMessage.MessageType.READ_BOLUS_WIZARD_CARB_RATIOS.response(cmd)) {
                    Log.d(TAG, "*** READ_BOLUS_WIZARD_CARB_RATIOS response" + HexDump.dumpHexString(decrypted));
                    fetchMoreData = false;
                } else if (MedtronicSendMessageRequestMessage.MessageType.READ_BOLUS_WIZARD_SENSITIVITY_FACTORS.response(cmd)) {
                    Log.d(TAG, "*** READ_BOLUS_WIZARD_SENSITIVITY_FACTORS response" + HexDump.dumpHexString(decrypted));
                    fetchMoreData = false;
                } else if (MedtronicSendMessageRequestMessage.MessageType.READ_BOLUS_WIZARD_BG_TARGETS.response(cmd)) {
                    Log.d(TAG, "*** READ_BOLUS_WIZARD_BG_TARGETS response" + HexDump.dumpHexString(decrypted));
                    fetchMoreData = false;
                } else {
                    Log.d(TAG, "*** ??? response" + HexDump.dumpHexString(decrypted));
                    fetchMoreData = true;
                }
            }
        }

        // when returning non-multipacket decrypted data we need to trim the 2 byte checksum
        return (decrypted != null ? Arrays.copyOfRange(decrypted, 0, decrypted.length - 2) : payload);
    }

    private class MultipacketSession {
        private int sessionSize;
        private int packetSize;
        private int lastPacketSize;
        private int packetsToFetch;
        private boolean[] segments;
        private byte[] response;

        private MultipacketSession(byte[] settings) {
            sessionSize = read32BEtoInt(settings, 0x0003);
            packetSize = read16BEtoUInt(settings, 0x0007);
            lastPacketSize = read16BEtoUInt(settings, 0x0009);
            packetsToFetch = read16BEtoUInt(settings, 0x000B);
            response = new byte[sessionSize + 1];
            segments  = new boolean[packetsToFetch];
            response[0] = settings[0]; // comDSequenceNumber
            Log.d(TAG, "*** Starting a new Multipacket Session. Expecting " + sessionSize + " bytes of data from " + packetsToFetch + " packets");
        }

        private int lastPacketNumber() {
            return packetsToFetch - 1;
        }

        // The number of segments we've actually fetched.
        private int segmentsFilled() {
            int count = 0;
            for (boolean segment : segments)
                if (segment) count++;
            return count;
        }

        private boolean payloadComplete() {
            return segmentsFilled() == packetsToFetch;
        }

        private void addSegment(byte[] data) throws UnexpectedMessageException {
            try {
                int packetNumber = read16BEtoUInt(data, 0x0003);
                int packetSize = data.length - 7;
                segments[packetNumber] = true;

                Log.d(TAG, "*** Got a Multipacket Segment: " + (packetNumber + 1) + " of " + this.packetsToFetch + ", count: " + segmentsFilled() + " [packetSize=" + packetSize + " " + this.packetSize + "/" + this.lastPacketSize + "]");

                if (packetNumber == lastPacketNumber() &&
                        packetSize != this.lastPacketSize) {
                    throw new UnexpectedMessageException("Multipacket Transfer last packet size mismatch");
                } else if (packetNumber != lastPacketNumber() &&
                        packetSize != this.packetSize) {
                    throw new UnexpectedMessageException("Multipacket Transfer packet size mismatch");
                }

                int to = (packetNumber * this.packetSize) + 1;
                int from = 5;
                while (from < packetSize + 5) this.response[to++] = data[from++];
            } catch (Exception e) {
                throw new UnexpectedMessageException("Multipacket Transfer bad segment data received");
            }
        }

        private byte[] missingSegments() {
            int packetNumber = 0;
            int missing = 0;
            for (boolean segment : segments) {
                if (segment) {
                    if (missing > 0) break;
                    packetNumber++;
                } else missing++;
            }

            Log.d(TAG, "*** Request Missing Multipacket Segments, position: " + (packetNumber + 1) + ", missing: " + missing);

            return new byte[]{(byte) (packetNumber >> 8), (byte) packetNumber, (byte) (missing >> 8), (byte) missing};
        }
    }

// refactor in progress to use constants for payload offsets

    public static final int MM_HEADER = 0x0000; // UInt8
    public static final int MM_DEVICETYPE = 0x0001; // UInt8
    public static final int MM_PUMPSERIAL = 0x0002; // String 6 bytes
    public static final int MM_COMMAND = 0x0012; // UInt8
    public static final int MM_SEQUENCE = 0x0013; // UInt32LE
    public static final int MM_PAYLOAD_SIZE = 0x001C; // UInt16LE
    public static final int MM_CRC = 0x0020; // UInt8
    public static final int MM_PAYLOAD = 0x0021; // data

    // {MM_PAYLOAD}
    public static final int NGP_COMMAND = 0x0000; // UInt8
    public static final int NGP_SIZE = 0x0001; // UInt8
    public static final int NGP_PAYLOAD = 0x0002; // data
    public static final int NGP_CRC = -0x0002; // UInt16LE

    // {NGP_PAYLOAD} when NGP_COMMAND=0x55
    public static final int NGP55_00 = 0x0000; // UInt8
    public static final int NGP55_06 = 0x0001; // UInt8 (maybe response flag??? seen 02 04 for 0x81 and 06 for 0x80 messages)
    public static final int NGP55_PUMP_MAC = 0x0002; // UInt64LE
    public static final int NGP55_LINK_MAC = 0x000A; // UInt64LE
    public static final int NGP55_SEQUENCE = 0x0012; // UInt8
    public static final int NGP55_U00 = 0x0013; // UInt8
    public static final int NGP55_U01 = 0x0014; // UInt8
    public static final int NGP55_ENCRYPTED_SIZE = 0x0015; // UInt8
    public static final int NGP55_PAYLOAD = 0x0016; // data

    // {NGP55_PAYLOAD}
    public static final int RESPONSE_SEQUENCE = 0x0000; // UInt8
    public static final int RESPONSE_COMMAND = 0x0001; // UInt16BE
//    public static final int RESPONSE_PAYLOAD = 0x0004; // data
    public static final int RESPONSE_PAYLOAD = 0x0003; // data
    public static final int RESPONSE_CRC = -0x0002; // UInt16BE

    // returns the dycrypted response payload only
    protected byte[] decode(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException {

        // TODO - Validate the message, inner CCITT, serial numbers, etc
        if (payload.length < MM_PAYLOAD + NGP_PAYLOAD + NGP55_PAYLOAD + RESPONSE_PAYLOAD ||
                (payload[MM_COMMAND] == CommandType.READ_INFO.getValue()) ||
                (payload[MM_COMMAND] == CommandType.REQUEST_LINK_KEY_RESPONSE.getValue())) {
            throw new EncryptionException("Message received for decryption wrong type/size");
        }

        byte encryptedPayloadSize = payload[MM_PAYLOAD + NGP_PAYLOAD + NGP55_ENCRYPTED_SIZE];

        if (encryptedPayloadSize == 0) {
            throw new EncryptionException( "Could not decrypt Medtronic Message (encryptedPayloadSize == 0)" );
        }

        ByteBuffer encryptedPayload = ByteBuffer.allocate(encryptedPayloadSize);
        encryptedPayload.put(payload, MM_PAYLOAD + NGP_PAYLOAD + NGP55_PAYLOAD, encryptedPayloadSize);
        byte[] decryptedPayload = decrypt(pumpSession.getKey(), pumpSession.getIV(), encryptedPayload.array());

        if (decryptedPayload == null) {
            throw new EncryptionException( "Could not decrypt Medtronic Message (decryptedPayload == null)" );
        }

        if (BuildConfig.DEBUG) {
            String outputString = HexDump.dumpHexString(decryptedPayload);
            Log.d(TAG, "DECRYPTED: " + outputString);
        }

        return decryptedPayload;
    }

    protected byte[] decrypt(byte[] key, byte[] iv, byte[] encrypted) throws EncryptionException {
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        byte[] decrypted;

        try {
            Cipher cipher = Cipher.getInstance("AES/CFB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec);
            decrypted = cipher.doFinal(encrypted);
        } catch (Exception e ) {
            throw new EncryptionException( "Could not decrypt Medtronic Message" );
        }
        return decrypted;
    }

}

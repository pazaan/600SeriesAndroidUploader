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

    public static final int CLEAR_TIMEOUT_MS = 1000;

    public static final int ERROR_CLEAR_TIMEOUT_MS = 21000;
    //public static final int ERROR_CLEAR_TIMEOUT_MS = 10000;
    public static final int PRESEND_CLEAR_TIMEOUT_MS = 100;

    public static final int READ_TIMEOUT_MS = 25000; // interestingly the cnl always(?) seems to send some sort of 0x80 message by the 20 sec mark, it may be important to read this late message to avoid a cnl error
    public static final int CNL_READ_TIMEOUT_MS = 2000;

    private static final int MULTIPACKET_TIMEOUT_MS = 2000; // minimum timeout
    private static final int MULTIPACKET_SEGMENT_MS = 70; // time allowance per segment
    private static final int SEGMENT_RETRY = 10;

    private static final int USB_BLOCKSIZE = 64;
    private static final String USB_HEADER = "ABC";

    private static final boolean debug_read = true;
    private static final boolean debug_write = true;

    protected ByteBuffer mPayload;

    public enum CommandAction {
        NO_TYPE(0x0),
        INITIALIZE(0x01),
        SCAN_NETWORK(0x02),
        JOIN_NETWORK(0x03),
        LEAVE_NETWORK(0x04),
        TRANSMIT_PACKET(0x05),
        READ_DATA(0x06),
        READ_STATUS(0x07),
        READ_NETWORK_STATUS(0x08),
        SET_SECURITY_MODE(0x0C),
        READ_STATISTICS(0x0D),
        SET_RF_MODE(0x0E),
        CLEAR_STATUS(0x10),
        SET_LINK_KEY(0x14);

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

    public enum NAK {
        NO_ERROR(0x00),
        PAUSE_IS_REQUESTED(0x02),
        SELF_TEST_HAS_FAILED(0x03),
        MESSAGE_WAS_REFUSED(0x04),
        TIMEOUT_ERROR(0x05),
        ELEMENT_VERSION_IS_NOT_CORRECT(0x06),
        DEVICE_HAS_ERROR(0x07),
        MESSAGE_IS_NOT_SUPPORTED(0x08), // CLP says 0x0B :\
        DATA_IS_OUT_OF_RANGE(0x09),
        DATA_IS_NOT_CONSISTENT(0x0A),
        FEATURE_IS_DISABLED(0x0B), // CLP says 0x0B here, too
        DEVICE_IS_BUSY(0x0C),
        DATA_DOES_NOT_EXIST(0x0D),
        HARDWARE_FAILURE(0x0E),
        DEVICE_IS_IN_WRONG_STATE(0x0F),
        DATA_IS_LOCKED_BY_ANOTHER(0x10),
        DATA_IS_NOT_LOCKED(0x11),
        CANNULA_FILL_CANNOT_BE_PERFORMED(0x12),
        DEVICE_IS_DISCONNECTED(0x13),
        EASY_BOLUS_IS_ACTIVE(0x14),
        PARAMETERS_ARE_NOT_AVAILABLE(0x15),
        MESSAGE_IS_OUT_OF_SEQUENCE(0x16),
        TEMP_BASAL_RATE_OUT_OF_RANGE(0x17),
        NA(-1);

        private byte value;

        NAK(int value) {
            this.value = (byte) value;
        }

        public byte value() {
            return value;
        }

        public boolean equals(byte value) {
            return this.value == value;
        }

        public static NAK convert(int value) {
            for (NAK nak : NAK.values())
                if (nak.value == value) return nak;
            return NAK.NA;
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

        long started = System.currentTimeMillis();
        long runtime;
        long timer;
        String info = "";

        while (message.length > pos) {
            ByteBuffer outputBuffer = ByteBuffer.allocate(USB_BLOCKSIZE);
            int sendLength = (pos + 60 > message.length) ? message.length - pos : 60;
            outputBuffer.put(USB_HEADER.getBytes());
            outputBuffer.put((byte) sendLength);
            outputBuffer.put(message, pos, sendLength);

            timer = System.currentTimeMillis();
            mDevice.write(outputBuffer.array(), 500); //200
            timer = System.currentTimeMillis() - timer;
            info += " [" + timer + "ms " + USB_BLOCKSIZE + " " + USB_HEADER + " " + sendLength + "]";

            if (debug_write)
                Log.d(TAG, "WRITE: [" + timer + "ms]" + HexDump.dumpHexString(outputBuffer.array()));

            pos += sendLength;
        }

        runtime = System.currentTimeMillis() - started;

        if (runtime > 100)
            Log.w(TAG, "WRITE: ??? runtime > 100ms, runtime:" + runtime + info);

        Log.d(TAG, "WRITE: [" + runtime + "ms]" + info);
    }

    protected byte[] readMessage(UsbHidDriver mDevice) throws IOException, TimeoutException {
        return readMessage(mDevice, READ_TIMEOUT_MS, debug_read);
    }

    protected byte[] readMessage(UsbHidDriver mDevice, int timeout) throws IOException, TimeoutException {
        return readMessage(mDevice, timeout, debug_read);
    }

    protected byte[] readMessage(UsbHidDriver mDevice, int timeout, boolean debug_read) throws IOException, TimeoutException {
        ByteArrayOutputStream responseMessage = new ByteArrayOutputStream();

        byte[] responseBuffer = new byte[USB_BLOCKSIZE];
        int bytesRead;
        int messageSize = 0;

        long started = System.currentTimeMillis();
        long runtime;
        long timer;
        String info = "";

        do {
            timer = System.currentTimeMillis();
            if (responseMessage.size() == 0)
                bytesRead = mDevice.read(responseBuffer, timeout);
            else
                bytesRead = mDevice.read(responseBuffer, 500);
            timer = System.currentTimeMillis() - timer;

            if (bytesRead == -1 && responseMessage.size() == 0) {
                runtime = System.currentTimeMillis() - started;
                Log.d(TAG, "READ: [" + runtime  + "ms/" + timeout + "ms] timeout" + info);
                throw new TimeoutException("Timeout waiting for response from pump");

            } else if (bytesRead > 0) {
                // Validate the header
                ByteBuffer header = ByteBuffer.allocate(3);
                header.put(responseBuffer, 0, 3);
                String headerString = new String(header.array());
                if (!headerString.equals(USB_HEADER))
                    throw new IOException("Unexpected header received");
                messageSize = responseBuffer[3];
                responseMessage.write(responseBuffer, 4, messageSize);
                info += " [" + timer + "ms " + bytesRead + " " + headerString + " " + messageSize + "]";

            } else if (bytesRead == 0){
                Log.e(TAG, "readMessage: got a zero-sized response.");
                throw new IOException("readMessage: got a zero-sized response");
            }
        } while (bytesRead > 0 && messageSize == 60);

        runtime = System.currentTimeMillis() - started;

        if (responseMessage.size() % 60 == 0)
            Log.w(TAG, "READ: ??? response divisible by 60, response size:" + responseMessage.size() + " " + info + HexDump.dumpHexString(responseMessage.toByteArray()));
        if (responseMessage.size() > 0 && runtime > 10000)
            Log.w(TAG, "READ: ??? runtime > 10000ms, runtime:" + runtime + info + HexDump.dumpHexString(responseMessage.toByteArray()));

        if (debug_read)
            Log.d(TAG, "READ: [" + runtime + "ms/" + timeout + "ms]" + info + HexDump.dumpHexString(responseMessage.toByteArray()));
        else
            Log.d(TAG, "READ: [" + runtime + "ms/" + timeout + "ms]" + info);

        return responseMessage.toByteArray();
    }

    // safety check to make sure a expected 0x81 response is received before next expected 0x80 response
    // very infrequent as clearMessage catches most issues but very important to save a CNL error situation

    // note (30/3/2018) this is extremely rare now due to reworked cnl stability fixes for v0.6.2
    // extended testing with near continuous history reading still catches the odd issue here

    protected byte[] readMessage_0x81(UsbHidDriver mDevice) throws IOException, TimeoutException {
        return readMessage_0x81(mDevice, READ_TIMEOUT_MS);
    }

    protected byte[] readMessage_0x81(UsbHidDriver mDevice, int timeout) throws IOException, TimeoutException {

        byte[] responseBytes;
        boolean doRetry;
        do {
            responseBytes = readMessage(mDevice, timeout);
            if (responseBytes.length <= 19) {
                doRetry = true;
                Log.w(TAG, "readMessage0x81: unexpected responseBytes size, length=" + responseBytes.length + HexDump.dumpHexString(responseBytes));
            } else if (responseBytes[18] != (byte) 0x81) {
                doRetry = true;
                Log.w(TAG, "readMessage0x81: did not get 0x81 response, got 0x" + HexDump.toHexString(responseBytes[18]) + HexDump.dumpHexString(responseBytes));
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
        Log.d(TAG, "CLEAR: [" + timeout + "ms]");
        int count = 0;
        boolean cleared = false;

        do {
            try {
                long timer = System.currentTimeMillis();
                readMessage(mDevice, timeout, true);
                timer = System.currentTimeMillis() - timer;
                if (timer > MedtronicCnlService.cnlClearTimer) MedtronicCnlService.cnlClearTimer = timer;
                MedtronicCnlService.cnlClear++;
                count++;
            } catch (TimeoutException e) {
                cleared = true;
            }
        } while (!cleared);

        if (count > 0) {
            Log.w(TAG, "CLEAR: message stream cleared " + count + " messages.");
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
        return sendToPump(mDevice, pumpSession, tag, PRESEND_CLEAR_TIMEOUT_MS);
    }

    protected byte[] sendToPump(UsbHidDriver mDevice, MedtronicCnlSession pumpSession, String tag, int timeout) throws IOException, TimeoutException, EncryptionException, ChecksumException, UnexpectedMessageException {
        tag = " (" + tag + ")";
        byte[] payload;
        byte medtronicSequenceNumber = pumpSession.getMedtronicSequenceNumber();

        // extra safety check and delay, CNL is not happy when we miss incoming messages
        // the short delay may also help with state readiness
        clearMessage(mDevice, timeout);

        sendMessage(mDevice);

        try {
            payload = readMessage_0x81(mDevice);
        } catch (TimeoutException e) {
            // ugh... there should always be a CNL 0x81 response and if we don't get one it usually ends with a E86 / E81 error on the CNL needing a unplug/plug cycle
            Log.e(TAG, "Timeout waiting for 0x81 response." + tag);
            clearMessage(mDevice, 20000);
            throw new TimeoutException("Timeout waiting for 0x81 response" + tag);
        }

        Log.d(TAG, "0x81 response: payload.length=" + payload.length + (payload.length >= 0x30 ? " payload[0x21]=" + payload[0x21] + " payload[0x2C]=" + payload[0x2C] + " medtronicSequenceNumber=" + medtronicSequenceNumber + " payload[0x2D]=" + payload[0x2D] : "") + tag);

        if (payload.length <= 0x21) {
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException("0x81 response was empty, connection lost" + tag);  // session needs to end or CNL will error
        } else if (payload.length != 0x30 && payload[0x21] != 0x55) {
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException("0x81 response was not a 0x55 message" + tag);
        } else if (payload[0x2C] != medtronicSequenceNumber) {
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException("0x81 sequence number does not match" + tag);
        } else if (payload[0x2D] == 0x04) {
            Log.w(TAG, "0x81 response: NOISY / BUSY" + tag);
        } else if (payload[0x2D] != 0x02) {
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException("0x81 unknown state flag" + tag);
        }

        return payload;
    }

    protected byte[] readFromPump(UsbHidDriver mDevice, MedtronicCnlSession pumpSession, String tag) throws IOException, TimeoutException, EncryptionException, ChecksumException, UnexpectedMessageException {
        tag = " (" + tag + ")";

        MultipacketSession multipacketSession = null;
        byte[] tupple;
        byte[] payload = null;
        byte[] decrypted = null;

        int retry = 0;
        int expectedSegments = 0;
        long expectedTime = 0;
        long timeout;

        short cmd;

        boolean fetchMoreData = true;

        while (fetchMoreData) {

            if (multipacketSession != null && !multipacketSession.payloadComplete()) {

                do {

                    if (expectedSegments < 1) {
                        tupple = multipacketSession.missingSegments();
                        new MultipacketResendPacketsMessage(pumpSession, tupple).send(mDevice);
                        expectedSegments = read16BEtoUInt(tupple, 0x02);
                        expectedTime = System.currentTimeMillis() + (MULTIPACKET_SEGMENT_MS * expectedSegments);
                    }

                    try {
                        // timeout adjusted to allow for large gaps of missing segments as the pump will keep sending until done
                        timeout = expectedTime - System.currentTimeMillis();
                        if (multipacketSession.segmentsFilled() == 0 && timeout < READ_TIMEOUT_MS)
                            // pump may have missed the initial ack, we need to wait the max timeout period
                            timeout = READ_TIMEOUT_MS;
                        else if (timeout < MULTIPACKET_TIMEOUT_MS)
                            timeout = MULTIPACKET_TIMEOUT_MS;
                        payload = readMessage(mDevice, (int) timeout);
                        retry = 0;

                    } catch (TimeoutException e) {
                        if (multipacketSession.segmentsFilled() == 0) {
                            Log.e(TAG, "*** Multisession timeout: failed no segments filled");
                            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                            throw new TimeoutException("Multisession timeout: failed no segments filled" + tag);
                        } else if (++retry >= SEGMENT_RETRY) {
                            Log.e(TAG, "*** Multisession timeout: retry failed");
                            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                            throw new TimeoutException("Multisession timeout, retry failed" + tag);
                        }

                        Log.w(TAG, "*** Multisession timeout: expecting:" + expectedSegments + " retry: " + retry);
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

            // minimum size?
            if (payload.length < 0x21) {
                Log.e(TAG, "*** response message size less then expected" + HexDump.dumpHexString(payload));
                clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                throw new UnexpectedMessageException("response message size less then expected" + tag);
            }
            // 0x80 message?
            if ((payload[0x12] & 0xFF) != 0x80) {
                Log.e(TAG, "*** response message not a 0x80" + HexDump.dumpHexString(payload));
                clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                throw new UnexpectedMessageException("response message not a 0x80" + tag);
            }
            int internal = payload[0x1C] & 0x000000FF | payload[0x1D] << 8 & 0x0000FF00; // 16bit LE internal payload size
            // correct size including internal payload?
            if (payload.length != (0x21 + internal)) {
                Log.e(TAG, "*** response message size mismatch" + HexDump.dumpHexString(payload));
                clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                throw new UnexpectedMessageException("response message size mismatch" + tag);
            }
            // 0x55 response?
            if (internal == 0 || (internal > 0 && payload[0x0021] != 0x55)) {
                Log.w(TAG, "*** response message internal payload not a 0x55" + HexDump.dumpHexString(payload));
                clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                throw new UnexpectedMessageException("response message internal payload not a 0x55, connection lost" + tag);
            }
            // pump response?
            if (internal == 0x0D && payload[0x26] == 0x02 && payload[0x29] == 0x03) {
                Log.w(TAG, "*** no response" + HexDump.dumpHexString(payload, 0x12, payload.length - 0x12));
                throw new UnexpectedMessageException("no response from pump" + tag);
            }
            // bad response?
            if (internal < 0x18) {
                Log.d(TAG, "*** bad response" + HexDump.dumpHexString(payload, 0x12, payload.length - 0x12));
                // if in a multipacket session then keep reading packets
                // this error can be common when run on debug emulator but not on actual devices
                if (multipacketSession == null)
                    throw new UnexpectedMessageException("bad response from pump" + tag);

            } else {

                try {
                    decrypted = decode(pumpSession, payload);
                } catch (EncryptionException e) {
                    clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                    throw e;
                } catch (ChecksumException e) {
                    clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                    throw e;
                } catch (Exception e) {
                    clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                    throw new EncryptionException("exception trying to decrypt message");
                }

                cmd = read16BEtoShort(decrypted, RESPONSE_COMMAND);
                Log.d(TAG, "*** RESPONSE: " + MedtronicSendMessageRequestMessage.MessageType.convert(cmd).name() + " (" + HexDump.toHexString(cmd) + ")");

                if (false) {
                    Log.d(TAG, "*** NAK test" + HexDump.dumpHexString(decrypted));
                    short nakcmdx = 999;
                    byte nakcodex = NAK.DEVICE_HAS_ERROR.value();
                    clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                    throw new UnexpectedMessageException("Pump sent a NAK(" + String.format("%02X", nakcmdx) + ":" + String.format("%02X", nakcodex) + ") response" + tag);
                }

                switch (MedtronicSendMessageRequestMessage.MessageType.convert(cmd)) {

                    // DEVICE_IS_IN_WRONG_STATE(0x0F) NAK can be sent when we issue a cmd while the pump is expecting something else ie is still in history sending mode
                    // DEVICE_HAS_ERROR(0x07) NAK can be sent when the pump is in a major alert state ie 'insulin flow blocked' and will not respond with any data until cleared on the pump
                    // Session should be ended immediately after a NAK (clear and close) to avoid any CNL issues

                    case NAK_COMMAND:
                        Log.d(TAG, "*** NAK response" + HexDump.dumpHexString(decrypted));
                        short nakcmd = read16BEtoShort(decrypted, 3);
                        byte nakcode = decrypted[5];
                        clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                        throw new UnexpectedMessageException("Pump sent a NAK(" + String.format("%02X", nakcmd) + ":" + String.format("%02X", nakcode) + ") response" + tag);

                    case EHSM_SESSION:
                        byte EHSMmode = (byte) (decrypted[0x03] & 1);
                        Log.d(TAG, "*** EHSM mode: " + EHSMmode);
                        if (EHSMmode == 1) fetchMoreData = false;
                        break;

                    case INITIATE_MULTIPACKET_TRANSFER:
                        try {
                            multipacketSession = new MultipacketSession(decrypted);
                        } catch (Exception e) {
                            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                            throw new UnexpectedMessageException("multipacketSession could not be initiated" + tag);
                        }
                        new AckMessage(pumpSession, MedtronicSendMessageRequestMessage.MessageType.INITIATE_MULTIPACKET_TRANSFER.response()).send(mDevice);
                        expectedSegments = multipacketSession.packetsToFetch;
                        expectedTime = System.currentTimeMillis() + (MULTIPACKET_SEGMENT_MS * expectedSegments);
                        break;

                    case MULTIPACKET_SEGMENT_TRANSMISSION:
                        if (multipacketSession == null) {
                            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                            throw new UnexpectedMessageException("multipacketSession not initiated before segment received" + tag);
                        }
                        if (multipacketSession.payloadComplete()) {
                            // sometimes the pump will resend the last packet again if it's only a few bytes
                            Log.d(TAG, "*** Multisession Complete - packet not needed");
                        } else {
                            try {
                                multipacketSession.addSegment(decrypted);
                            } catch (UnexpectedMessageException e) {
                                clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                                throw e;
                            } catch (Exception e) {
                                clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                                throw new UnexpectedMessageException("Multipacket: bad segment data received" + tag);
                            }
                            if (multipacketSession.payloadComplete()) {
                                Log.d(TAG, "*** Multisession Complete");
                                new AckMessage(pumpSession, MedtronicSendMessageRequestMessage.MessageType.MULTIPACKET_SEGMENT_TRANSMISSION.response()).send(mDevice);
                            }
                            expectedSegments--;
                        }
                        break;

                    case END_HISTORY_TRANSMISSION:
                        fetchMoreData = false;
                        break;

                    case READ_PUMP_TIME:
                        if (BuildConfig.DEBUG)
                            Log.d(TAG, "*** READ_PUMP_TIME PAYLOAD:" + HexDump.dumpHexString(decrypted));
                        fetchMoreData = false;
                        break;

                    case READ_PUMP_STATUS:
                        if (BuildConfig.DEBUG)
                            Log.d(TAG, "*** READ_PUMP_STATUS PAYLOAD:" + HexDump.dumpHexString(decrypted));
                        fetchMoreData = false;
                        break;

                    case READ_HISTORY_INFO:
                        if (BuildConfig.DEBUG)
                            Log.d(TAG, "*** READ_HISTORY_INFO PAYLOAD:" + HexDump.dumpHexString(decrypted));
                        fetchMoreData = false;
                        break;

                    case READ_BASAL_PATTERN:
                        if (BuildConfig.DEBUG)
                            Log.d(TAG, "*** READ_BASAL_PATTERN PAYLOAD:" + HexDump.dumpHexString(decrypted));
                        fetchMoreData = false;
                        break;

                    case READ_BOLUS_WIZARD_BG_TARGETS:
                        if (BuildConfig.DEBUG)
                            Log.d(TAG, "*** READ_BOLUS_WIZARD_BG_TARGETS PAYLOAD:" + HexDump.dumpHexString(decrypted));
                        fetchMoreData = false;
                        break;

                    case READ_BOLUS_WIZARD_CARB_RATIOS:
                        if (BuildConfig.DEBUG)
                            Log.d(TAG, "*** READ_BOLUS_WIZARD_CARB_RATIOS PAYLOAD:" + HexDump.dumpHexString(decrypted));
                        fetchMoreData = false;
                        break;

                    case READ_BOLUS_WIZARD_SENSITIVITY_FACTORS:
                        if (BuildConfig.DEBUG)
                            Log.d(TAG, "*** READ_BOLUS_WIZARD_SENSITIVITY_FACTORS PAYLOAD:" + HexDump.dumpHexString(decrypted));
                        fetchMoreData = false;
                        break;

                    default:
                        Log.e(TAG, "*** ??? PAYLOAD:" + HexDump.dumpHexString(decrypted));
                }

            }
        }

        if (multipacketSession != null)
            if (multipacketSession.payloadComplete()) return multipacketSession.response;
            else {
                clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                throw new UnexpectedMessageException("multipacketSession did not complete" + tag);
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

            Log.d(TAG, "*** Request Missing Multipacket Segments, position: " + (packetNumber + 1) + " of " + this.packetsToFetch + ", missing: " + missing);

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

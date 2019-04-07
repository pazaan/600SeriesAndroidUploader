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
import info.nightscout.android.utils.HexDump;

import static info.nightscout.android.utils.ToolKit.read16BEtoShort;
import static info.nightscout.android.utils.ToolKit.read32BEtoInt;
import static info.nightscout.android.utils.ToolKit.read16BEtoUInt;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public abstract class ContourNextLinkMessage {
    private static final String TAG = ContourNextLinkMessage.class.getSimpleName();

    public static final int ERROR_CLEAR_TIMEOUT_MS = 25000;
    public static final int PRESEND_CLEAR_TIMEOUT_MS = 50;

    public static final int READ_TIMEOUT_MS = 25000;
    public static final int CNL_READ_TIMEOUT_MS = 2000;

    private static final int MULTIPACKET_TIMEOUT_MS = 1000; // minimum timeout
    private static final int MULTIPACKET_SEGMENT_MS = 50; // time allowance per segment
    private static final int MULTIPACKET_SEGMENT_RETRY = 10;

    private static final int USB_BLOCKSIZE = 64;
    private static final String USB_HEADER = "ABC";

    private static final boolean DEBUG_READ = false; //BuildConfig.DEBUG;
    private static final boolean DEBUG_READ_MS = false; //BuildConfig.DEBUG;
    private static final boolean DEBUG_WRITE = false; //BuildConfig.DEBUG;
    private static final boolean DEBUG_WRITE_MS = false; //BuildConfig.DEBUG;

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
        long runtime = System.currentTimeMillis();
        long timer;
        String info = "";

        int pos = 0;
        byte[] message = this.encode();

        while (message.length > pos) {
            ByteBuffer outputBuffer = ByteBuffer.allocate(USB_BLOCKSIZE);
            int sendLength = (pos + 60 > message.length) ? message.length - pos : 60;
            outputBuffer.put(USB_HEADER.getBytes());
            outputBuffer.put((byte) sendLength);
            outputBuffer.put(message, pos, sendLength);

            if (DEBUG_WRITE_MS) {
                timer = System.currentTimeMillis();
                mDevice.write(outputBuffer.array(), 10000);
                timer = System.currentTimeMillis() - timer;
                info = String.format("%s [%sms %s %s %s]", info, timer, USB_BLOCKSIZE, USB_HEADER, sendLength);
            } else {
                mDevice.write(outputBuffer.array(), 10000);
            }
            if (DEBUG_WRITE) Log.d(TAG, "WRITE: packet:" + HexDump.dumpHexString(outputBuffer.array()));

            pos += sendLength;
        }

        runtime = System.currentTimeMillis() - runtime;
        if (runtime < 100) Log.d(TAG, String.format("WRITE: [%sms]%s", runtime, info));
        else Log.w(TAG, String.format("WRITE: runtime > 100ms [%sms]%s", runtime, info));
    }

    protected byte[] readMessage(UsbHidDriver mDevice) throws IOException, TimeoutException {
        return readMessage(mDevice, READ_TIMEOUT_MS);
    }

    protected byte[] readMessage(UsbHidDriver mDevice, int timeout) throws IOException, TimeoutException {
        long runtime = System.currentTimeMillis();
        long timer;
        String info = "";

        ByteArrayOutputStream responseMessage = new ByteArrayOutputStream();
        byte[] responseBuffer = new byte[USB_BLOCKSIZE];
        int bytesRead;
        int messageSize = 0;
        int expectedSize = 0;

        do {
            timer = System.currentTimeMillis();
            if (responseMessage.size() == 0)
                // initial read using the specified timeout
                bytesRead = mDevice.read(responseBuffer, timeout);
            else
                // once a read is in progress the additional reads should be immediate
                bytesRead = mDevice.read(responseBuffer, 10000);
            timer = System.currentTimeMillis() - timer;

            if (bytesRead > 0) {

                // Validate the header
                ByteBuffer header = ByteBuffer.allocate(3);
                header.put(responseBuffer, 0, 3);
                String headerString = new String(header.array());
                if (!headerString.equals(USB_HEADER))
                    throw new IOException("Unexpected header received" + HexDump.dumpHexString(responseBuffer));

                messageSize = responseBuffer[3];
                responseMessage.write(responseBuffer, 4, messageSize);

                if (DEBUG_READ_MS)
                    info = String.format("%s [%sms %s %s %s]", info, timer, bytesRead, headerString, messageSize);

                // get the expected size for 0x80 or 0x81 messages as they may be on a block boundary
                if (expectedSize == 0 && messageSize >= 0x21
                        && ((responseBuffer[0x12 + 4] & 0xFF) == 0x80 || (responseBuffer[0x12 + 4] & 0xFF) == 0x81)) {
                    expectedSize = 0x21 + responseBuffer[0x1C + 4] & 0x00FF | responseBuffer[0x1D + 4] << 8 & 0xFF00;
                }

            } else if (bytesRead == 0){
                Log.e(TAG, "readMessage: got a zero-sized response.");
                throw new IOException("readMessage: got a zero-sized response");
            }

        } while (bytesRead > 0 && messageSize == 60 && responseMessage.size() != expectedSize);

        runtime = System.currentTimeMillis() - runtime;
        info = String.format(" [%sms/%sms]%s", runtime, timeout, info);

        if (bytesRead == -1) {
            if (runtime > 10000) Log.w(TAG, "READ: runtime > 10000ms TIMEOUT" + info);
            else Log.d(TAG, "READ: TIMEOUT" + info);
            throw new TimeoutException("Timeout waiting for a read response " + info);
        }

        // a 'response divisible by 60' is in general a valid response on a block boundary, noted in log as it may also be due to a usb read error
        if (responseMessage.size() % 60 == 0)
            Log.w(TAG, String.format("READ: response divisible by 60, response size: %s expected size: %s%s", responseMessage.size(), expectedSize, info));

        if (runtime > 10000)
            Log.w(TAG, "READ: runtime > 10000ms" + info);

        if (DEBUG_READ)
            Log.d(TAG, "READ:" + info + HexDump.dumpHexString(responseMessage.toByteArray()));
        else
            Log.d(TAG, "READ:" + info);

        return responseMessage.toByteArray();
    }

    protected byte[] readResponse0x80(UsbHidDriver mDevice, int timeout, String tag) throws IOException, TimeoutException, UnexpectedMessageException {

        byte[] payload = readMessage(mDevice, timeout);

        // minimum 0x80 message size?
        if (payload.length <= 0x21) {
            Log.e(TAG, "readResponse0x80: message size <= 0x21");
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException(String.format("0x80 response message size less then expected (%s)", tag));
        }
        // 0x80 message?
        if ((payload[0x12] & 0xFF) != 0x80) {
            Log.e(TAG, "readResponse0x80: message not a 0x80" + HexDump.dumpHexString(payload));
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException(String.format("0x80 response message not a 0x80 (%s)", tag));
        }
        // message and internal payload size correct?
        if (payload.length != (0x21 + payload[0x1C] & 0x00FF | payload[0x1D] << 8 & 0xFF00)) {
            Log.e(TAG, "readResponse0x80: message size mismatch");
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException(String.format("0x80 response message size mismatch (%s)", tag));
        }

        // 1 byte response? (generally seen as a 0x00 or 0xFF, unknown meaning and high risk of CNL E86 follows)
        if (payload.length == 0x22) {
            Log.e(TAG, String.format("readResponse0x80: message with 1 byte internal payload: 0x%02X", payload[0x21]));
            // do not retry, end the session
            throw new UnexpectedMessageException(String.format("0x80 response message internal payload is 0x%02X, connection lost (%s)", payload[0x21], tag));
        }
        // internal 0x55 payload?
        else if (payload[0x21] != 0x55) {
            Log.e(TAG, "readResponse0x80: message no internal 0x55, internal payload: " + HexDump.dumpHexString(payload, 0x21, payload.length - 0x21));
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            // do not retry, end the session
            throw new UnexpectedMessageException(String.format("0x80 response message internal payload not a 0x55, connection lost (%s)", tag));
        }

        if (payload.length == 0x2E) {
            // no pump response?
            if (payload[0x24] == 0x00 && payload[0x25] == 0x00 && payload[0x26] == 0x02 && payload[0x27] == 0x00) {
                Log.w(TAG, "readResponse0x80: message containing '55 0B 00 00 00 02 00 00 03 00 00' (no pump response)");
                // stream is always clear after this message
                throw new UnexpectedMessageException(String.format("no response from pump (%s)", tag));
            }
            // no connect response?
            else if (payload[0x24] == 0x00 && payload[0x25] == 0x20 && payload[0x26] == 0x00 && payload[0x27] == 0x00) {
                Log.d(TAG, "readResponse0x80: message containing '55 0B 00 00 20 00 00 00 03 00 00' (no connect)");
            }
            // bad response?
            // seen during multipacket transfers, may indicate a full CNL receive buffer
            else if (payload[0x24] == 0x06 && (payload[0x25] & 0xFF) == 0x88 && payload[0x26] == 0x00 && payload[0x27] == 0x65) {
                Log.w(TAG, "readResponse0x80: message containing '55 0B 00 06 88 00 65 XX 03 00 00' (bad response)");
            }
        }

        // lost pump connection?
        else if (payload.length == 0x30 && payload[0x24] == 0x00 && payload[0x25] == 0x00 && payload[0x26] == 0x02 && payload[0x29] == 0x02 && payload[0x2B] == 0x01) {
            Log.e(TAG, "readResponse0x80: message containing '55 0D 00 00 00 02 00 00 02 00 01 XX XX' (lost pump connection)");
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            // do not retry, end the session
            throw new UnexpectedMessageException(String.format("connection lost (%s)", tag));
        }
        // connection
        else if (payload.length == 0x4F) {
            // network connect
            // 55 | 2C | 00 04 | xx xx xx xx xx | 02 | xx xx xx xx xx xx xx xx | 82 | 00 00 00 00 00 | 07 | 00 | xx | xx xx xx xx xx xx xx xx | 42 | 00 00 00 00 00 00 00 | xx
            // 55 | size | type | pump serial | ... | pump mac | ... | ... | ... | rssi | cnl mac | ... | ... | channel
            if (payload[0x24] == 0x04 && (payload[0x33] & 0xFF) == 0x82 && payload[0x44] == 0x42) {
                Log.d(TAG, "readResponse0x80: message containing network connect (pump connected)");
            }
            // non-standard network connect
            // -- | -- | 00 00 | -- -- -- -- -- | -- | -- -- -- -- -- -- -- -- | 83 | -- -- -- -- -- | -- | xx | -- | -- -- -- -- -- -- -- -- | 43 | -- -- -- -- -- -- -- | --
            else if (payload[0x24] == 0x00 && (payload[0x33] & 0xFF) == 0x83 && payload[0x44] == 0x43) {
                Log.e(TAG, "readResponse0x80: message containing non-standard network connect (lost pump connection)");
                // stream is always clear after this message
                // do not retry, end the session
                throw new UnexpectedMessageException(String.format("connection lost (%s)", tag));
            }
        }

        return payload;
    }

    protected byte[] readResponse0x81(UsbHidDriver mDevice, int timeout, String tag) throws IOException, TimeoutException, UnexpectedMessageException {
        byte[] payload;

        try {
            // an 0x81 response is always expected after sending a request
            // keep reading until we get it or timeout
            while (true) {
                payload = readMessage(mDevice, timeout);
                if (payload.length < 0x21)
                    Log.e(TAG, "readResponse0x81: message size less then expected, length = " + payload.length);
                else if ((payload[0x12] & 0xFF) != 0x81)
                    Log.e(TAG, "readResponse0x81: message not a 0x81, got a 0x" + HexDump.toHexString(payload[0x12]) + HexDump.dumpHexString(payload));
                else break;
            }
        } catch (TimeoutException e) {
            // ugh... there should always be a CNL 0x81 response and if we don't get one it usually ends with a E86 / E81 error on the CNL needing a unplug/plug cycle
            Log.e(TAG, "readResponse0x81: timeout waiting for 0x81 response.");
            throw new TimeoutException(String.format("Timeout waiting for 0x81 response (%s)", tag));
        }

        // empty response?
        if (payload.length <= 0x21) {
            Log.e(TAG, "readResponse0x81: message size <= 0x21");
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            // do not retry, end the session
            throw new UnexpectedMessageException(String.format("0x81 response was empty, connection lost (%s)", tag));
        }
        // message and internal payload size correct?
        else if (payload.length != (0x21 +  payload[0x1C] & 0x00FF | payload[0x1D] << 8 & 0xFF00)) {
            Log.e(TAG, "readResponse0x81: message size mismatch");
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException(String.format("0x81 response message size mismatch (%s)", tag));
        }
        // internal 0x55 payload?
        else if (payload[0x21] != 0x55) {
            Log.e(TAG, "readResponse0x81: message no internal 0x55, internal payload: " + HexDump.dumpHexString(payload, 0x21, payload.length - 0x21));
            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
            throw new UnexpectedMessageException(String.format("0x81 response was not a 0x55 message (%s)", tag));
        }

        // state flag?
        // standard response:
        // 55 | 0D | 00 04 | 00 00 00 00 03 00 01 | xx | xx
        // 55 | size | type | ... | seq | state
        if (payload.length == 0x30) {
            if (payload[0x2D] == 0x04) {
                Log.w(TAG, "readResponse0x81: message [0x2D]==0x04 (noisy/busy)");
            }
            else if (payload[0x2D] != 0x02) {
                Log.e(TAG, "readResponse0x81: message [0x2D]!=0x02 (unknown state)");
                clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                throw new UnexpectedMessageException(String.format("0x81 unknown state flag (%s)", tag));
            }
        }
        // connection
        else if (payload.length == 0x27 && payload[0x23] == 0x00 && payload[0x24] == 0x00) {
            Log.d(TAG, "readResponse0x81: message containing '55 04 00 00' (network not connected)");
        } else {
            Log.w(TAG, "readResponse0x81: unknown 0x55 message type");
        }

        return payload;
    }

    // intercept unexpected messages from the CNL
    // these usually come from pump requests as it can occasionally resend message responses several times (possibly due to a missed CNL ACK during CNL-PUMP comms?)
    // mostly noted on the higher radio channels, channel 26 shows this the most
    // if these messages are not cleared the CNL will likely error needing to be unplugged to reset as it expects them to be read before any further commands are sent

    // testing:
    // post-clear: send request --> read and drop any message that is not the expected 0x81 response
    // this works if only one message needs to be cleared with the next being the expected 0x81
    // if there is more then one message to be cleared then there is no 0x81 response and the CNL will E86 error
    // pre-clear: clear all messages in stream until timeout --> send request
    // consistently stable even with a small timeout, clears multiple messages with very rare miss
    // which will get caught using the post-clear method as fail-safe

    protected int clearMessage(UsbHidDriver mDevice, int timeout) throws IOException {
        Log.d(TAG, "CLEAR: [" + timeout + "ms]");
        int count = 0;
        boolean cleared = false;

        byte[] payload;

        do {
            try {
                payload = readMessage(mDevice, timeout);
                count++;

                // the following are always seen as the end of an incoming stream and can be considered as completed clear indicators

                // check for 'no pump response'
                // 55 | 0B | 00 00 | 00 02 00 00 03 00 00
                if (payload.length == 0x2E && payload[0x21] == 0x55
                        && payload[0x23] == 0x00 && payload[0x24] == 0x00 && payload[0x26] == 0x02 && payload[0x29] == 0x03) {
                    Log.w(TAG, "CLEAR: got 'no pump response' message indicating stream cleared");
                    cleared = true;
                }

                else if (payload.length == 0x30 && payload[0x21] == 0x55
                        && payload[0x24] == 0x00 && payload[0x25] == 0x00 && payload[0x26] == 0x02 && payload[0x29] == 0x02 && payload[0x2B] == 0x01) {
                    Log.w(TAG, "CLEAR: got message containing '55 0D 00 00 00 02 00 00 02 00 01 XX XX' (lost pump connection)");
                }

                // check for 'non-standard network connect'
                // standard 'network connect' 0x80 response
                // 55 | 2C | 00 04 | xx xx xx xx xx | 02 | xx xx xx xx xx xx xx xx | 82 | 00 00 00 00 00 | 07 | 00 | xx | xx xx xx xx xx xx xx xx | 42 | 00 00 00 00 00 00 00 | xx
                // 55 | size | type | pump serial | ... | pump mac | ... | ... | ... | rssi | cnl mac | ... | ... | channel
                // difference to the standard 'network connect' response
                // -- | -- | 00 00 | -- -- -- -- -- | -- | -- -- -- -- -- -- -- -- | 83 | -- -- -- -- -- | -- | xx | -- | -- -- -- -- -- -- -- -- | 43 | -- -- -- -- -- -- -- | --
                else if (payload.length == 0x4F && payload[0x21] == 0x55
                        && payload[0x23] == 0x00 && payload[0x24] == 0x00 && (payload[0x33] & 0xFF) == 0x83 && payload[0x44] == 0x43) {
                    Log.w(TAG, "CLEAR: got 'non-standard network connect' message indicating stream cleared");
                    cleared = true;
                }

            } catch (TimeoutException e) {
                cleared = true;
            }
        } while (!cleared);

        if (count > 0) Log.w(TAG, "CLEAR: message stream cleared " + count + " messages.");

        return count;
    }

    protected byte[] sendToPump(UsbHidDriver mDevice, String tag) throws IOException, TimeoutException, UnexpectedMessageException {
        return sendToPump(mDevice, PRESEND_CLEAR_TIMEOUT_MS, tag);
    }

    protected byte[] sendToPump(UsbHidDriver mDevice, int timeout, String tag) throws IOException, TimeoutException, UnexpectedMessageException {
        clearMessage(mDevice, timeout);
        sendMessage(mDevice);
        return readResponse0x81(mDevice, READ_TIMEOUT_MS, tag);
    }

    protected byte[] readFromPump(UsbHidDriver mDevice, MedtronicCnlSession pumpSession, String tag) throws IOException, TimeoutException, EncryptionException, ChecksumException, UnexpectedMessageException {
        MultipacketSession multipacketSession = null;
        byte[] tupple;
        byte[] payload = null;
        byte[] decrypted = null;

        int retry = 0;
        int expectedSegments = 0;
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
                    }

                    try {
                        // timeout adjusted for efficiency and to allow for large gaps of missing segments as the pump will keep sending until done
                        if (multipacketSession.segmentsFilled == 0)
                            // pump may have missed the initial ack, we need to wait the max timeout period
                            timeout = READ_TIMEOUT_MS;
                        else {
                            timeout = MULTIPACKET_SEGMENT_MS * expectedSegments;
                            if (timeout < MULTIPACKET_TIMEOUT_MS)
                                timeout = MULTIPACKET_TIMEOUT_MS;
                        }
                        payload = readResponse0x80(mDevice, (int) timeout, tag);
                        retry = 0;

                    } catch (TimeoutException e) {
                        if (multipacketSession.segmentsFilled == 0) {
                            Log.e(TAG, "*** Multisession timeout: failed no segments filled");
                            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                            throw new TimeoutException(String.format("Multisession timeout: failed, no segments filled (%s)", tag));
                        } else if ((multipacketSession.segmentsFilled * 100) / multipacketSession.packetsToFetch < 20) {
                            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                            throw new TimeoutException(String.format("Multisession timeout: failed, missed packets > 80%% (%s)", tag));
                        } else if (++retry >= MULTIPACKET_SEGMENT_RETRY) {
                            Log.e(TAG, "*** Multisession timeout: retry failed");
                            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                            throw new TimeoutException(String.format("Multisession timeout, retry failed (%s)", tag));
                        }

                        Log.w(TAG, String.format("*** Multisession timeout: count: %s/%s expecting: %s retry: %s", multipacketSession.segmentsFilled, multipacketSession.packetsToFetch, expectedSegments, retry));
                        expectedSegments = 0;
                    }

                } while (retry > 0);

            } else {
                try {
                    payload = readResponse0x80(mDevice, READ_TIMEOUT_MS, tag);
                } catch (TimeoutException e) {
                    throw new TimeoutException(String.format("Timeout waiting for response from pump (%s)", tag));
                }
            }

            // bad response?
            if (payload.length <= 0x2E) {
                // if in a multipacket session then keep reading packets
                if (multipacketSession == null) {
                    clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                    throw new UnexpectedMessageException(String.format("bad response from pump (%s)", tag));
                }

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
                    throw new EncryptionException(String.format("exception trying to decrypt message (%s)", tag));
                }

                cmd = read16BEtoShort(decrypted, NGP_RESPONSE_COMMAND);
                Log.d(TAG, String.format("*** RESPONSE: %s (%04X)", MedtronicSendMessageRequestMessage.MessageType.convert(cmd).name(), cmd));

                switch (MedtronicSendMessageRequestMessage.MessageType.convert(cmd)) {

                    // DEVICE_IS_IN_WRONG_STATE(0x0F) NAK can be sent when we issue a cmd while the pump is expecting something else ie is still in history sending mode
                    // DEVICE_HAS_ERROR(0x07) NAK can be sent when the pump is in a major alert state ie 'insulin flow blocked' and will not respond with any data until cleared on the pump
                    // Session should be ended immediately after a NAK (clear and close) to avoid any CNL issues

                    case NAK_COMMAND:
                        Log.d(TAG, "*** NAK response" + HexDump.dumpHexString(decrypted));
                        short nakcmd = read16BEtoShort(decrypted, 3);
                        byte nakcode = decrypted[5];
                        clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                        throw new UnexpectedMessageException(String.format("Pump sent a NAK(%02X:%02X) response (%s)", nakcmd, nakcode, tag));

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
                            throw new UnexpectedMessageException(String.format("multipacketSession could not be initiated (%s)", tag));
                        }
                        new AckMessage(pumpSession, MedtronicSendMessageRequestMessage.MessageType.INITIATE_MULTIPACKET_TRANSFER.response()).send(mDevice);
                        expectedSegments = multipacketSession.packetsToFetch;
                        break;

                    case MULTIPACKET_SEGMENT_TRANSMISSION:
                        if (multipacketSession == null) {
                            clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                            throw new UnexpectedMessageException(String.format("multipacketSession not initiated before segment received (%s)", tag));
                        }
                        if (multipacketSession.payloadComplete()) {
                            // sometimes the pump will resend the last packet again if it's only a few bytes
                            Log.d(TAG, "*** Multisession Complete - packet not needed");
                        } else {
                            try {
                                if (multipacketSession.addSegment(decrypted)) {
                                    expectedSegments--;
                                }
                            } catch (UnexpectedMessageException e) {
                                clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                                throw e;
                            } catch (Exception e) {
                                clearMessage(mDevice, ERROR_CLEAR_TIMEOUT_MS);
                                throw new UnexpectedMessageException(String.format("Multipacket: bad segment data received (%s)", tag));
                            }
                            if (multipacketSession.payloadComplete()) {
                                Log.d(TAG, "*** Multisession Complete");
                                new AckMessage(pumpSession, MedtronicSendMessageRequestMessage.MessageType.MULTIPACKET_SEGMENT_TRANSMISSION.response()).send(mDevice);
                            }
                        }
                        break;

                    case UNMERGED_HISTORY:
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
                throw new UnexpectedMessageException(String.format("multipacketSession did not complete (%s)", tag));
            }

        // when returning non-multipacket decrypted data we need to trim the 2 byte checksum
        return Arrays.copyOfRange(decrypted, 0, decrypted.length - 2);
    }

    private class MultipacketSession {
        private int sessionSize;
        private int packetSize;
        private int lastPacketSize;
        private int packetsToFetch;
        private int segmentsFilled;
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
            Log.d(TAG, String.format("*** Starting a new Multipacket Session. Expecting %s bytes of data from %s packets", sessionSize, packetsToFetch));
        }

        private int lastPacketNumber() {
            return packetsToFetch - 1;
        }

        private boolean payloadComplete() {
            return segmentsFilled == packetsToFetch;
        }

        private boolean addSegment(byte[] data) throws UnexpectedMessageException {
            int packetNumber = read16BEtoUInt(data, 0x0003);
            int packetSize = data.length - 7;

            if (segments[packetNumber]) {
                Log.w(TAG, String.format("*** Got a Repeated Multipacket Segment: %s of %s, count: %s [packetSize=%s %s/%s]", packetNumber + 1, packetsToFetch, segmentsFilled, packetSize, this.packetSize, this.lastPacketSize));
                return false;
            }

            if (packetNumber == lastPacketNumber() &&
                    packetSize != this.lastPacketSize) {
                throw new UnexpectedMessageException("Multipacket Transfer last packet size mismatch");
            } else if (packetNumber != lastPacketNumber() &&
                    packetSize != this.packetSize) {
                throw new UnexpectedMessageException("Multipacket Transfer packet size mismatch");
            }

            segments[packetNumber] = true;
            segmentsFilled++;

            Log.d(TAG, String.format("*** Got a Multipacket Segment: %s of %s, count: %s [packetSize=%s %s/%s]", packetNumber + 1, packetsToFetch, segmentsFilled, packetSize, this.packetSize, this.lastPacketSize));

            // from[], offset, to[], offset, size
            System.arraycopy(data, 5, response, (packetNumber * this.packetSize) + 1, packetSize);

            return true;
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

            Log.d(TAG, String.format("*** Request Missing Multipacket Segments, position: %s of %s, missing: %s", packetNumber + 1, packetsToFetch, missing));

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
    public static final int MM_RESPONSE_COMMAND = 0x0000; // UInt8
    public static final int MM_RESPONSE_SIZE = 0x0001; // UInt8
    public static final int MM_RESPONSE_PAYLOAD = 0x0002; // data
    public static final int MM_RESPONSE_CRC = -0x0002; // UInt16LE

    // {MM_RESPONSE_PAYLOAD}
    public static final int NGP_00 = 0x0000; // UInt8
    public static final int NGP_06 = 0x0001; // UInt8 (seen 02 04 for 0x81 and 06 for 0x80 messages)
    public static final int NGP_PUMP_MAC = 0x0002; // UInt64LE
    public static final int NGP_LINK_MAC = 0x000A; // UInt64LE
    public static final int NGP_SEQUENCE = 0x0012; // UInt8
    public static final int NGP_U00 = 0x0013; // UInt8
    public static final int NGP_U01 = 0x0014; // UInt8
    public static final int NGP_ENCRYPTED_SIZE = 0x0015; // UInt8
    public static final int NGP_PAYLOAD = 0x0016; // data

    // {NGP_PAYLOAD}
    public static final int NGP_RESPONSE_SEQUENCE = 0x0000; // UInt8
    public static final int NGP_RESPONSE_COMMAND = 0x0001; // UInt16BE
    public static final int NGP_RESPONSE_PAYLOAD = 0x0003; // data
    public static final int NGP_RESPONSE_CRC = -0x0002; // UInt16BE

    // returns the dycrypted response payload only
    protected byte[] decode(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException {
        // TODO - Validate the message, inner CCITT, serial numbers, etc

        if (CommandType.READ_INFO.equals(payload[MM_COMMAND])
                || CommandType.REQUEST_LINK_KEY_RESPONSE.equals(payload[MM_COMMAND])) {
            throw new EncryptionException("Message received for decryption wrong type");
        }

        int offset = MM_PAYLOAD + MM_RESPONSE_PAYLOAD + NGP_PAYLOAD;

        if (payload.length < offset + NGP_RESPONSE_PAYLOAD) {
            throw new EncryptionException("Message received for decryption wrong size");
        }

        byte encryptedPayloadSize = payload[MM_PAYLOAD + MM_RESPONSE_PAYLOAD + NGP_ENCRYPTED_SIZE];

        if (encryptedPayloadSize == 0 || offset + encryptedPayloadSize > payload.length) {
            throw new EncryptionException( "Could not decrypt Medtronic Message (encryptedPayloadSize out of range)" );
        }

        byte[] decryptedPayload = decrypt(pumpSession.getKey(), pumpSession.getIV(),
                Arrays.copyOfRange(payload, offset, offset + encryptedPayloadSize));

        if (decryptedPayload == null) {
            throw new EncryptionException( "Could not decrypt Medtronic Message (decryptedPayload == null)" );
        }

        return decryptedPayload;
    }

    private SecretKeySpec secretKeySpec;
    private IvParameterSpec ivSpec;

    protected byte[] decrypt(byte[] key, byte[] iv, byte[] encrypted) throws EncryptionException {
        if (secretKeySpec == null) secretKeySpec = new SecretKeySpec(key, "AES");
        if (ivSpec == null) ivSpec = new IvParameterSpec(iv);

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

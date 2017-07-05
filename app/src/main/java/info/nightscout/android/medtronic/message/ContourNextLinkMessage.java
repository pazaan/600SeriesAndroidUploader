package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.utils.HexDump;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public abstract class ContourNextLinkMessage {
    private static final String TAG = ContourNextLinkMessage.class.getSimpleName();

    private static final int USB_BLOCKSIZE = 64;
    private static final int READ_TIMEOUT_MS = 15000; //ASTM standard is 15 seconds (note was previously set at 10 seconds)
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
        ByteArrayOutputStream responseMessage = new ByteArrayOutputStream();

        byte[] responseBuffer = new byte[USB_BLOCKSIZE];
        int bytesRead;
        int messageSize = 0;

        do {
            bytesRead = mDevice.read(responseBuffer, READ_TIMEOUT_MS);

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

    protected int readMessage_0x81(UsbHidDriver mDevice) throws IOException, TimeoutException {

        int responseSize = 0;
        boolean doRetry;
        do {
            byte[] responseBytes = readMessage(mDevice);
            if (responseBytes[18] != (byte) 0x81) {
                doRetry = true;
                Log.d(TAG, "readMessage0x81: did not get 0x81 response, got " + responseBytes[18]);
            } else {
                doRetry = false;
                responseSize = responseBytes.length;
            }

        } while (doRetry);

        return responseSize;
    }

    // intercept unexpected messages from the CNL
    // these usually come from pump requests as it can occasionally resend message responses several times (possibly due to a missed CNL ACK during CNL-PUMP comms?)
    // mostly noted on the higher radio channels, channel 26 shows this the most
    // if these messages are not cleared the CNL will likely error needing to be unplugged to reset as it expects them to be read before any further commands are sent

    protected int clearMessage(UsbHidDriver mDevice) throws IOException {

        byte[] responseBuffer = new byte[USB_BLOCKSIZE];
        int bytesRead;
        int bytesClear = 0;

        do {
            bytesRead = mDevice.read(responseBuffer, 2000);
            if (bytesRead > 0) {
                bytesClear += bytesRead;
                String responseString = HexDump.dumpHexString(responseBuffer);
                Log.d(TAG, "READ: " + responseString);
            }
        } while (bytesRead > 0);

        if (bytesClear > 0) {
            Log.d(TAG, "clearMessage: message stream cleared bytes: " + bytesClear);
        }

        return bytesClear;
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
}

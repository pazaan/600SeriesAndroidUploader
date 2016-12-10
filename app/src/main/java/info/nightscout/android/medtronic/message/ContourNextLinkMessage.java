package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.utils.HexDump;

import static android.R.id.message;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class ContourNextLinkMessage {
    private static final String TAG = ContourNextLinkMessage.class.getSimpleName();

    private static final int USB_BLOCKSIZE = 64;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final String BAYER_USB_HEADER = "ABC";

    protected ByteBuffer mPayload;
    protected MedtronicCnlSession mPumpSession;

    protected ContourNextLinkMessage(byte[] bytes) {
        setPayload(bytes);
    }

    public byte[] encode() {
        return mPayload.array();
    }

    // TODO refactor
    public void send(ContourNextLinkMessageHandler handler) throws IOException {
        handler.sendMessage(this);
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
            outputBuffer.put(BAYER_USB_HEADER.getBytes());
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
                if (!headerString.equals(BAYER_USB_HEADER)) {
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

    protected void validate() {};


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

        public boolean equals(byte value) {
            return this.value == value;
        }
    }
}

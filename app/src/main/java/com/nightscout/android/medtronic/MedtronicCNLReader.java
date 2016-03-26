package com.nightscout.android.medtronic;

import android.util.Log;

import com.nightscout.android.USB.UsbHidDriver;
import com.nightscout.android.dexcom.USB.HexDump;
import com.nightscout.android.medtronic.message.ContourNextLinkBinaryMessage;
import com.nightscout.android.medtronic.message.ChannelNegotiateMessage;
import com.nightscout.android.medtronic.service.MedtronicCNLService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by lgoedhart on 24/03/2016.
 */
public class MedtronicCNLReader {

    private static final String TAG = MedtronicCNLService.class.getSimpleName();

    private static final int USB_BLOCKSIZE = 64;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final String BAYER_USB_HEADER = "ABC";

    private static final byte[] RADIO_CHANNELS = { 0x14, 0x11, 0x0e, 0x17, 0x1a };

    public enum ASCII {
        STX(0x02),
        EOT(0x04),
        ENQ(0x05),
        ACK(0x06),
        NAK(0x15);

        private byte value;

        ASCII(int code) {
            this.value = (byte) code;
        }
    }

    private UsbHidDriver mDevice;
    private MedtronicCNLSession mPumpSession = new MedtronicCNLSession();
    public String deviceInfo;

    public MedtronicCNLReader(UsbHidDriver device) {
        mDevice = device;
    }

    public byte[] readMessage() throws IOException {
        ByteArrayOutputStream responseMessage = new ByteArrayOutputStream();

        byte[] responseBuffer = new byte[USB_BLOCKSIZE];
        int bytesRead = 0;
        int messageSize = 0;

        do {
            bytesRead = mDevice.read(responseBuffer, READ_TIMEOUT_MS);
            ByteBuffer header = ByteBuffer.allocate(3);
            header.put(responseBuffer, 0, 3);
            String headerString = new String(header.array());
            if (!headerString.equals(BAYER_USB_HEADER)) {
                throw new IOException("Unexpected header received");
            }
            messageSize = responseBuffer[3];
            responseMessage.write(responseBuffer, 4, messageSize);
        } while (bytesRead > 0 && (messageSize + 4) == bytesRead);
        // TODO - how to deal with messages that finish on the boundary?

        String responseString = HexDump.dumpHexString(responseMessage.toByteArray());
        Log.d(TAG, "READ: " + responseString);

        return responseMessage.toByteArray();
    }

    public void sendMessage(byte[] message) throws IOException {

        int pos = 0;

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

    public void sendMessage(byte message) throws IOException {
        byte[] msg = {message};
        sendMessage(msg);
    }

    private void checkControlMessage(byte controlCharacter) throws IOException {
        byte[] msg = readMessage();

        if (msg.length != 1 || msg[0] != controlCharacter) {
            throw new RuntimeException(String.format("Expected to get an %d control character.", (int) controlCharacter));
        }
    }

    public void requestDeviceInfo() throws IOException {
        sendMessage((byte) 'X');

        // TODO - parse this into an ASTM record for the device info.
        byte[] response = readMessage();

        checkControlMessage(ASCII.ENQ.value);
    }

    public void enterControlMode() throws IOException {
        sendMessage(ASCII.NAK.value);
        checkControlMessage(ASCII.EOT.value);
        sendMessage(ASCII.ENQ.value);
        checkControlMessage(ASCII.ACK.value);

    }

    public void enterPassthroughMode() throws IOException {
        sendMessage("W|".getBytes());
        checkControlMessage(ASCII.ACK.value);
        sendMessage("Q|".getBytes());
        checkControlMessage(ASCII.ACK.value);
        sendMessage("1|".getBytes());
        checkControlMessage(ASCII.ACK.value);
    }

    public void openConnection() throws IOException {
        ContourNextLinkBinaryMessage message = new ContourNextLinkBinaryMessage(ContourNextLinkBinaryMessage.CommandType.OPEN_CONNECTION, mPumpSession, mPumpSession.getHMAC());
        message.send(this);
        sendMessage(message.encode());
        // FIXME - We need to care what the response message is - wrong MAC and all that
        readMessage();
    }

    public void requestReadInfo() throws IOException {
        ContourNextLinkBinaryMessage message = new ContourNextLinkBinaryMessage(ContourNextLinkBinaryMessage.CommandType.READ_INFO, mPumpSession, null);
        sendMessage(message.encode());
        // Don't care what the response message is at this stage
        readMessage();
    }

    public void negotiateChannel() throws IOException {
        for( byte channel: RADIO_CHANNELS ) {
            ChannelNegotiateMessage message = new ChannelNegotiateMessage( mPumpSession );
            sendMessage(message.encode());
        }
    }
}

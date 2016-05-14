package com.nightscout.android.medtronic;

import android.util.Log;

import com.nightscout.android.USB.UsbHidDriver;
import com.nightscout.android.dexcom.DexcomG4Activity;
import com.nightscout.android.dexcom.USB.HexDump;
import com.nightscout.android.medtronic.message.BeginEHSMMessage;
import com.nightscout.android.medtronic.message.ChannelNegotiateMessage;
import com.nightscout.android.medtronic.message.ChecksumException;
import com.nightscout.android.medtronic.message.ContourNextLinkBinaryMessage;
import com.nightscout.android.medtronic.message.ContourNextLinkCommandMessage;
import com.nightscout.android.medtronic.message.ContourNextLinkMessage;
import com.nightscout.android.medtronic.message.ContourNextLinkMessageHandler;
import com.nightscout.android.medtronic.message.EncryptionException;
import com.nightscout.android.medtronic.message.EndEHSMMessage;
import com.nightscout.android.medtronic.message.MedtronicMessage;
import com.nightscout.android.medtronic.message.MessageUtils;
import com.nightscout.android.medtronic.message.PumpStatusRequestMessage;
import com.nightscout.android.medtronic.message.PumpStatusResponseMessage;
import com.nightscout.android.medtronic.message.PumpTimeRequestMessage;
import com.nightscout.android.medtronic.message.PumpTimeResponseMessage;
import com.nightscout.android.medtronic.message.ReadInfoResponseMessage;
import com.nightscout.android.medtronic.message.UnexpectedMessageException;
import com.nightscout.android.medtronic.service.MedtronicCNLService;
import com.nightscout.android.upload.MedtronicNG.CGMRecord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by lgoedhart on 24/03/2016.
 */
public class MedtronicCNLReader implements ContourNextLinkMessageHandler {

    private static final String TAG = MedtronicCNLService.class.getSimpleName();

    private static final int USB_BLOCKSIZE = 64;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final String BAYER_USB_HEADER = "ABC";

    private static final byte[] RADIO_CHANNELS = {0x14, 0x11, 0x0e, 0x17, 0x1a};
    private UsbHidDriver mDevice;

    private MedtronicCNLSession mPumpSession = new MedtronicCNLSession();

    private String mStickSerial = null;

    public MedtronicCNLReader(UsbHidDriver device) {
        mDevice = device;
    }

    public String getStickSerial() {
        return mStickSerial;
    }

    public MedtronicCNLSession getPumpSession() {
        return mPumpSession;
    }

    public byte[] readMessage() throws IOException, TimeoutException {
        ByteArrayOutputStream responseMessage = new ByteArrayOutputStream();

        byte[] responseBuffer = new byte[USB_BLOCKSIZE];
        int bytesRead;
        int messageSize;

        do {
            bytesRead = mDevice.read(responseBuffer, READ_TIMEOUT_MS);

            if (bytesRead == 0) {
                throw new TimeoutException("Timeout waiting for response from pump");
            }

            // Validate the header
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

        // FIXME - remove debugging
        String responseString = HexDump.dumpHexString(responseMessage.toByteArray());
        Log.d(TAG, "READ: " + responseString);

        return responseMessage.toByteArray();
    }

    @Override
    public void sendMessage(ContourNextLinkMessage message) throws IOException {
        sendMessage(message.encode());
        if (message instanceof ContourNextLinkBinaryMessage) {
            mPumpSession.incrBayerSequenceNumber();
        }

        if (message instanceof MedtronicMessage) {
            mPumpSession.incrMedtronicSequenceNumber();
        }
    }

    @Override
    public ContourNextLinkMessage receiveMessage() {
        return null;
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

    // TODO - get rid of this - it should be in a message decoder
    private void checkControlMessage(byte[] msg, byte controlCharacter) throws IOException, TimeoutException, UnexpectedMessageException {
        if (msg.length != 1 || msg[0] != controlCharacter) {
            throw new UnexpectedMessageException(String.format("Expected to get control character '%d' Got '%d'.",
                    (int) controlCharacter, (int) msg[0]));
        }
    }

    public void requestDeviceInfo() throws IOException, TimeoutException, UnexpectedMessageException {
        new ContourNextLinkCommandMessage("X").send(this);

        boolean gotTimeout = false;

        // TODO - parse this into an ASTM record for the device info.
        try {
            // The stick will return either the ASTM message, or the ENQ first. The order can change,
            // so we need to handle both cases
            byte[] response1 = readMessage();
            byte[] response2 = readMessage();

            if (response1[0] == ASCII.EOT.value) {
                // response 1 is the ASTM message
                checkControlMessage(response2, ASCII.ENQ.value);
                extractStickSerial( new String( response1 ) );
            } else {
                // response 2 is the ASTM message
                checkControlMessage(response1, ASCII.ENQ.value);
                extractStickSerial( new String( response2 ) );
            }
        } catch (TimeoutException e) {
            // Terminate comms with the pump, then try again
            new ContourNextLinkCommandMessage(ASCII.EOT.value).send(this);
            gotTimeout = true;
        } finally {
            // If we timed out - try to start the session again.
            if (gotTimeout) {
                requestDeviceInfo();
            }
        }
    }

    private void extractStickSerial( String astmMessage ) {
        Pattern pattern = Pattern.compile( ".*?\\^(\\d{4}-\\d{7})\\^.*" );
        Matcher matcher = pattern.matcher( astmMessage );
        if( matcher.find() ) {
            mStickSerial = matcher.group(1);
        }
    }

    public void enterControlMode() throws IOException, TimeoutException, UnexpectedMessageException {
        new ContourNextLinkCommandMessage(ASCII.NAK.value).send(this);
        checkControlMessage(readMessage(), ASCII.EOT.value);
        new ContourNextLinkCommandMessage(ASCII.ENQ.value).send(this);
        checkControlMessage(readMessage(), ASCII.ACK.value);
    }

    public void enterPassthroughMode() throws IOException, TimeoutException, UnexpectedMessageException {
        new ContourNextLinkCommandMessage("W|").send(this);
        checkControlMessage(readMessage(), ASCII.ACK.value);
        new ContourNextLinkCommandMessage("Q|").send(this);
        checkControlMessage(readMessage(), ASCII.ACK.value);
        new ContourNextLinkCommandMessage("1|").send(this);
        checkControlMessage(readMessage(), ASCII.ACK.value);
    }

    public void openConnection() throws IOException, TimeoutException {
        new ContourNextLinkBinaryMessage(ContourNextLinkBinaryMessage.CommandType.OPEN_CONNECTION, mPumpSession, mPumpSession.getHMAC()).send(this);
        // FIXME - We need to care what the response message is - wrong MAC and all that
        readMessage();
    }

    public void requestReadInfo() throws IOException, TimeoutException, EncryptionException, ChecksumException {
        new ContourNextLinkBinaryMessage(ContourNextLinkBinaryMessage.CommandType.READ_INFO, mPumpSession, null).send(this);

        ContourNextLinkMessage response = ReadInfoResponseMessage.fromBytes(mPumpSession, readMessage());

        // FIXME - this needs to go into ReadInfoResponseMessage
        ByteBuffer infoBuffer = ByteBuffer.allocate(16);
        infoBuffer.order(ByteOrder.BIG_ENDIAN);
        infoBuffer.put(response.encode(), 0x21, 16);
        long linkMAC = infoBuffer.getLong(0);
        long pumpMAC = infoBuffer.getLong(8);

        this.getPumpSession().setLinkMAC( linkMAC );
        this.getPumpSession().setPumpMAC( pumpMAC );
    }

    public byte negotiateChannel() throws IOException, ChecksumException, TimeoutException {
        for (byte channel : RADIO_CHANNELS) {
            mPumpSession.setRadioChannel(channel);
            new ChannelNegotiateMessage(mPumpSession).send(this);

            // Don't care what the 0x81 response message is at this stage
            readMessage();
            // The 0x80 message
            ContourNextLinkMessage response = ContourNextLinkBinaryMessage.fromBytes(readMessage());
            byte[] responseBytes = response.encode();

            if (responseBytes.length > 46) {
                // Looks promising, let's check the last byte of the payload to make sure
                if (responseBytes[76] == mPumpSession.getRadioChannel()) {
                    break;
                } else {
                    throw new IOException(String.format("Expected to get a message for channel %d. Got %d", mPumpSession.getRadioChannel(), responseBytes[76]));
                }
            } else {
                mPumpSession.setRadioChannel((byte) 0);
            }
        }

        return mPumpSession.getRadioChannel();
    }

    public void beginEHSMSession() throws EncryptionException, IOException, TimeoutException {
        new BeginEHSMMessage(mPumpSession).send(this);
        // The Begin EHSM Session only has an 0x81 response
        readMessage();
    }

    public void getPumpTime(CGMRecord pumpRecord) throws EncryptionException, IOException, ChecksumException, TimeoutException {
        // FIXME - throw if not in EHSM mode (add a state machine)

        new PumpTimeRequestMessage(mPumpSession).send(this);
        // Read the 0x81
        readMessage();

        // Read the 0x80
        ContourNextLinkMessage response = PumpTimeResponseMessage.fromBytes(mPumpSession, readMessage());

        if (response.encode().length < 57) {
            // Invalid message. Don't try and parse it
            return;
        }

        // FIXME - this needs to go into PumpTimeResponseMessage
        ByteBuffer dateBuffer = ByteBuffer.allocate(8);
        dateBuffer.order(ByteOrder.BIG_ENDIAN);
        dateBuffer.put(response.encode(), 61, 8);
        long rtc = dateBuffer.getInt(0) & 0x00000000ffffffffL;
        long offset = dateBuffer.getInt(4);

        Date pumpDate = MessageUtils.decodeDateTime(rtc, offset);

        // Set displayTime to be an ISO 8601 date (so that it's parsable).
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        pumpRecord.displayTime = dateFormat.format(pumpDate);
        DexcomG4Activity.pumpStatusRecord.pumpDate = pumpDate;
    }

    public void getPumpStatus(CGMRecord pumpRecord) throws IOException, EncryptionException, ChecksumException, TimeoutException {
        // FIXME - throw if not in EHSM mode (add a state machine)

        new PumpStatusRequestMessage(mPumpSession).send(this);
        // Read the 0x81
        readMessage();

        // Read the 0x80
        ContourNextLinkMessage response = PumpStatusResponseMessage.fromBytes(mPumpSession, readMessage());

        if (response.encode().length < 57) {
            // Invalid message. Don't try and parse it
            return;
        }

        // FIXME - this needs to go into PumpStatusResponseMessage
        ByteBuffer statusBuffer = ByteBuffer.allocate(96);
        statusBuffer.order(ByteOrder.BIG_ENDIAN);
        statusBuffer.put(response.encode(), 0x39, 96);

        // Read the data into the record
        long rawActiveInsulin = statusBuffer.getShort(0x33) & 0x0000ffff;
        DexcomG4Activity.pumpStatusRecord.activeInsulin = new BigDecimal( rawActiveInsulin / 10000f ).setScale(3, BigDecimal.ROUND_HALF_UP);
        pumpRecord.sensorBGL = statusBuffer.getShort(0x35) & 0x0000ffff; // In mg/DL. 0 means no CGM reading
        long rtc;
        long offset;
        if( ( pumpRecord.sensorBGL & 0x200 ) == 0x200 ) {
            // Sensor error. Let's reset. FIXME - solve this more elegantly later
            pumpRecord.sensorBGL = 0;
            rtc = 0;
            offset = 0;
            pumpRecord.setTrend(CGMRecord.TREND.NOT_SET);
        } else {
            rtc = statusBuffer.getInt(0x37) & 0x00000000ffffffffL;
            offset = statusBuffer.getInt(0x3b);
            pumpRecord.setTrend(CGMRecord.fromMessageByte( statusBuffer.get(0x40)));
        }
        pumpRecord.sensorBGLDate = MessageUtils.decodeDateTime(rtc, offset);
        DexcomG4Activity.pumpStatusRecord.recentBolusWizard = statusBuffer.get(0x48) != 0;
        DexcomG4Activity.pumpStatusRecord.bolusWizardBGL = statusBuffer.getShort(0x49); // In mg/DL
        long rawReservoirAmount = statusBuffer.getInt(0x2b) &  0xffffffff;
        DexcomG4Activity.pumpStatusRecord.reservoirAmount = new BigDecimal( rawReservoirAmount / 10000f ).setScale(3, BigDecimal.ROUND_HALF_UP);
        DexcomG4Activity.pumpStatusRecord.batteryPercentage = ( statusBuffer.get(0x2a) );
    }

    public void endEHSMSession() throws EncryptionException, IOException, TimeoutException {
        new EndEHSMMessage(mPumpSession).send(this);
        // The End EHSM Session only has an 0x81 response
        readMessage();
    }

    public void closeConnection() throws IOException, TimeoutException {
        new ContourNextLinkBinaryMessage(ContourNextLinkBinaryMessage.CommandType.CLOSE_CONNECTION, mPumpSession, null).send(this);
        // FIXME - We need to care what the response message is - wrong MAC and all that
        readMessage();
    }

    public void endPassthroughMode() throws IOException, TimeoutException, UnexpectedMessageException {
        new ContourNextLinkCommandMessage("W|").send(this);
        checkControlMessage(readMessage(), ASCII.ACK.value);
        new ContourNextLinkCommandMessage("Q|").send(this);
        checkControlMessage(readMessage(), ASCII.ACK.value);
        new ContourNextLinkCommandMessage("0|").send(this);
        checkControlMessage(readMessage(), ASCII.ACK.value);
    }

    public void endControlMode() throws IOException, TimeoutException, UnexpectedMessageException {
        new ContourNextLinkCommandMessage(ASCII.EOT.value).send(this);
        checkControlMessage(readMessage(), ASCII.ENQ.value);
    }

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
}

package info.nightscout.android.medtronic;

import android.util.Log;

import org.apache.commons.lang3.ArrayUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.message.BeginEHSMMessage;
import info.nightscout.android.medtronic.message.ChannelNegotiateMessage;
import info.nightscout.android.medtronic.message.ChecksumException;
import info.nightscout.android.medtronic.message.ContourNextLinkBinaryMessage;
import info.nightscout.android.medtronic.message.ContourNextLinkCommandMessage;
import info.nightscout.android.medtronic.message.ContourNextLinkMessage;
import info.nightscout.android.medtronic.message.ContourNextLinkMessageHandler;
import info.nightscout.android.medtronic.message.EncryptionException;
import info.nightscout.android.medtronic.message.EndEHSMMessage;
import info.nightscout.android.medtronic.message.MedtronicMessage;
import info.nightscout.android.medtronic.message.MessageUtils;
import info.nightscout.android.medtronic.message.PumpBasalPatternRequestMessage;
import info.nightscout.android.medtronic.message.PumpBasalPatternResponseMessage;
import info.nightscout.android.medtronic.message.ReadInfoRequestMessage;
import info.nightscout.android.medtronic.message.PumpStatusRequestMessage;
import info.nightscout.android.medtronic.message.PumpStatusResponseMessage;
import info.nightscout.android.medtronic.message.PumpTimeRequestMessage;
import info.nightscout.android.medtronic.message.PumpTimeResponseMessage;
import info.nightscout.android.medtronic.message.ReadInfoResponseMessage;
import info.nightscout.android.medtronic.message.RequestLinkKeyResponseMessage;
import info.nightscout.android.medtronic.message.UnexpectedMessageException;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.utils.HexDump;

/**
 * Created by lgoedhart on 24/03/2016.
 */
public class MedtronicCnlReader implements ContourNextLinkMessageHandler {

    private static final String TAG = MedtronicCnlReader.class.getSimpleName();

    private static final int USB_BLOCKSIZE = 64;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final String BAYER_USB_HEADER = "ABC";

    private static final byte[] RADIO_CHANNELS = {0x14, 0x11, 0x0e, 0x17, 0x1a};
    private UsbHidDriver mDevice;

    private MedtronicCnlSession mPumpSession = new MedtronicCnlSession();

    private String mStickSerial = null;

    public MedtronicCnlReader(UsbHidDriver device) {
        mDevice = device;
    }

    private static PumpStatusEvent.CGM_TREND fromMessageByte(byte messageByte) {
        switch (messageByte) {
            case (byte) 0x60:
                return PumpStatusEvent.CGM_TREND.FLAT;
            case (byte) 0xc0:
                return PumpStatusEvent.CGM_TREND.DOUBLE_UP;
            case (byte) 0xa0:
                return PumpStatusEvent.CGM_TREND.SINGLE_UP;
            case (byte) 0x80:
                return PumpStatusEvent.CGM_TREND.FOURTY_FIVE_UP;
            case (byte) 0x40:
                return PumpStatusEvent.CGM_TREND.FOURTY_FIVE_DOWN;
            case (byte) 0x20:
                return PumpStatusEvent.CGM_TREND.SINGLE_DOWN;
            case (byte) 0x00:
                return PumpStatusEvent.CGM_TREND.DOUBLE_DOWN;
            default:
                return PumpStatusEvent.CGM_TREND.NOT_COMPUTABLE;
        }
    }

    public String getStickSerial() {
        return mStickSerial;
    }

    public MedtronicCnlSession getPumpSession() {
        return mPumpSession;
    }

    public byte[] readMessage() throws IOException, TimeoutException {
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
            throw new UnexpectedMessageException(String.format(Locale.getDefault(), "Expected to get control character '%d' Got '%d'.",
                    (int) controlCharacter, (int) msg[0]));
        }
    }

    public void requestDeviceInfo() throws IOException, TimeoutException, UnexpectedMessageException {
        new ContourNextLinkCommandMessage("X").send(this);

        boolean doRetry = false;

        // TODO - parse this into an ASTM record for the device info.
        try {
            // The stick will return either the ASTM message, or the ENQ first. The order can change,
            // so we need to handle both cases
            byte[] response1 = readMessage();
            byte[] response2 = readMessage();

            if (response1[0] == ASCII.EOT.value) {
                // response 1 is the ASTM message
                checkControlMessage(response2, ASCII.ENQ.value);
                extractStickSerial(new String(response1));
            } else {
                // response 2 is the ASTM message
                checkControlMessage(response1, ASCII.ENQ.value);
                extractStickSerial(new String(response2));
            }
        } catch (TimeoutException e) {
            // Terminate comms with the pump, then try again
            new ContourNextLinkCommandMessage(ASCII.EOT.value).send(this);
            doRetry = true;
        } finally {
            if (doRetry) {
                requestDeviceInfo();
            }
        }
    }

    private void extractStickSerial(String astmMessage) {
        Pattern pattern = Pattern.compile(".*?\\^(\\d{4}-\\d{7})\\^.*");
        Matcher matcher = pattern.matcher(astmMessage);
        if (matcher.find()) {
            mStickSerial = matcher.group(1);
        }
    }

    public void enterControlMode() throws IOException, TimeoutException, UnexpectedMessageException {
        boolean doRetry = false;

        try {
            new ContourNextLinkCommandMessage(ASCII.NAK.value).send(this);
            checkControlMessage(readMessage(), ASCII.EOT.value);
            new ContourNextLinkCommandMessage(ASCII.ENQ.value).send(this);
            checkControlMessage(readMessage(), ASCII.ACK.value);
        } catch (UnexpectedMessageException e2) {
            // Terminate comms with the pump, then try again
            new ContourNextLinkCommandMessage(ASCII.EOT.value).send(this);
            doRetry = true;
        } finally {
            if (doRetry) {
                enterControlMode();
            }
        }
    }

    public void enterPassthroughMode() throws IOException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin enterPasshtroughMode");
        new ContourNextLinkCommandMessage("W|").send(this);
        checkControlMessage(readMessage(), ASCII.ACK.value);
        new ContourNextLinkCommandMessage("Q|").send(this);
        checkControlMessage(readMessage(), ASCII.ACK.value);
        new ContourNextLinkCommandMessage("1|").send(this);
        checkControlMessage(readMessage(), ASCII.ACK.value);
        Log.d(TAG, "Finished enterPasshtroughMode");
    }

    public void openConnection() throws IOException, TimeoutException, NoSuchAlgorithmException, ChecksumException {
        Log.d(TAG, "Begin openConnection");
        new ContourNextLinkBinaryMessage(ContourNextLinkBinaryMessage.CommandType.OPEN_CONNECTION, mPumpSession, mPumpSession.getHMAC()).send(this);
        // FIXME - We need to care what the response message is - wrong MAC and all that
        readMessage();
        Log.d(TAG, "Finished openConnection");
    }

    public void requestReadInfo() throws IOException, TimeoutException, EncryptionException, ChecksumException {
        Log.d(TAG, "Begin requestReadInfo");

        ReadInfoResponseMessage response = new ReadInfoRequestMessage(mPumpSession).send(mDevice);

        long linkMAC = response.getLinkMAC();
        long pumpMAC = response.getPumpMAC();

        this.getPumpSession().setLinkMAC(linkMAC);
        this.getPumpSession().setPumpMAC(pumpMAC);
        Log.d(TAG, String.format("Finished requestReadInfo. linkMAC = '%d', pumpMAC = '%d", linkMAC, pumpMAC));
    }

    public void requestLinkKey() throws IOException, TimeoutException, EncryptionException, ChecksumException {
        Log.d(TAG, "Begin requestLinkKey");
        new ContourNextLinkBinaryMessage(ContourNextLinkBinaryMessage.CommandType.REQUEST_LINK_KEY, mPumpSession, null).send(this);

        ContourNextLinkMessage response = RequestLinkKeyResponseMessage.fromBytes(mPumpSession, readMessage());

        // FIXME - this needs to go into RequestLinkKeyResponseMessage
        ByteBuffer infoBuffer = ByteBuffer.allocate(55);
        infoBuffer.order(ByteOrder.BIG_ENDIAN);
        infoBuffer.put(response.encode(), 0x21, 55);

        byte[] packedLinkKey = infoBuffer.array();

        this.getPumpSession().setPackedLinkKey(packedLinkKey);

        Log.d(TAG, String.format("Finished requestLinkKey. linkKey = '%s'", this.getPumpSession().getKey()));
    }

    public byte negotiateChannel(byte lastRadioChannel) throws IOException, ChecksumException, TimeoutException {
        ArrayList<Byte> radioChannels = new ArrayList<>(Arrays.asList(ArrayUtils.toObject(RADIO_CHANNELS)));

        if (lastRadioChannel != 0x00) {
            // If we know the last channel that was used, shuffle the negotiation order
            Byte lastChannel = radioChannels.remove(radioChannels.indexOf(lastRadioChannel));

            if (lastChannel != null) {
                radioChannels.add(0, lastChannel);
            }
        }

        Log.d(TAG, "Begin negotiateChannel");
        for (byte channel : radioChannels) {
            Log.d(TAG, String.format("negotiateChannel: trying channel '%d'...", channel));
            mPumpSession.setRadioChannel(channel);
            new ChannelNegotiateMessage(mPumpSession).send(this);

            // Don't care what the 0x81 response message is at this stage
            Log.d(TAG, "negotiateChannel: Reading 0x81 message");
            readMessage();
            // The 0x80 message
            Log.d(TAG, "negotiateChannel: Reading 0x80 message");
            ContourNextLinkMessage response = ContourNextLinkBinaryMessage.fromBytes(readMessage());
            byte[] responseBytes = response.encode();

            Log.d(TAG, "negotiateChannel: Check response length");
            if (responseBytes.length > 46) {
                // Looks promising, let's check the last byte of the payload to make sure
                if (responseBytes[76] == mPumpSession.getRadioChannel()) {
                    break;
                } else {
                    throw new IOException(String.format(Locale.getDefault(), "Expected to get a message for channel %d. Got %d", mPumpSession.getRadioChannel(), responseBytes[76]));
                }
            } else {
                mPumpSession.setRadioChannel((byte) 0);
            }
        }

        Log.d(TAG, String.format("Finished negotiateChannel with channel '%d'", mPumpSession.getRadioChannel()));
        return mPumpSession.getRadioChannel();
    }

    public void beginEHSMSession() throws EncryptionException, IOException, TimeoutException, ChecksumException {
        Log.d(TAG, "Begin beginEHSMSession");
        new BeginEHSMMessage(mPumpSession).send(this);
        // The Begin EHSM Session only has an 0x81 response
        readMessage();
        Log.d(TAG, "Finished beginEHSMSession");
    }

    public Date getPumpTime() throws EncryptionException, IOException, ChecksumException, TimeoutException {
        Log.d(TAG, "Begin getPumpTime");
        // FIXME - throw if not in EHSM mode (add a state machine)

        new PumpTimeRequestMessage(mPumpSession).send(this);
        // Read the 0x81
        readMessage();

        // Read the 0x80
        ContourNextLinkMessage response = PumpTimeResponseMessage.fromBytes(mPumpSession, readMessage());

        if (response.encode().length < (61 + 8)) {
            // Invalid message. Return an invalid date.
            // TODO - deal with this more elegantly
            Log.e(TAG, "Invalid message received for getPumpTime");
            return new Date();
        }

        // FIXME - this needs to go into PumpTimeResponseMessage
        ByteBuffer dateBuffer = ByteBuffer.allocate(8);
        dateBuffer.order(ByteOrder.BIG_ENDIAN);
        dateBuffer.put(response.encode(), 0x3d, 8);
        long rtc = dateBuffer.getInt(0) & 0x00000000ffffffffL;
        long offset = dateBuffer.getInt(4);

        Log.d(TAG, "Finished getPumpTime with date " + MessageUtils.decodeDateTime(rtc, offset));
        return MessageUtils.decodeDateTime(rtc, offset);
    }

    public void getPumpStatus(PumpStatusEvent pumpRecord, long pumpTimeOffset) throws IOException, EncryptionException, ChecksumException, TimeoutException {
        Log.d(TAG, "Begin getPumpStatus");
        // FIXME - throw if not in EHSM mode (add a state machine)

        new PumpStatusRequestMessage(mPumpSession).send(this);
        // Read the 0x81
        readMessage();

        // Read the 0x80
        ContourNextLinkMessage response = PumpStatusResponseMessage.fromBytes(mPumpSession, readMessage());

        if (response.encode().length < (57 + 96)) {
            // Invalid message. Don't try and parse it
            // TODO - deal with this more elegantly
            Log.e(TAG, "Invalid message received for getPumpStatus");
            return;
        }

        // FIXME - this needs to go into PumpStatusResponseMessage
        ByteBuffer statusBuffer = ByteBuffer.allocate(96);
        statusBuffer.order(ByteOrder.BIG_ENDIAN);
        statusBuffer.put(response.encode(), 0x39, 96);

        // Status Flags
        pumpRecord.setSuspended((statusBuffer.get(0x03) & 0x01) != 0x00);
        pumpRecord.setBolusing((statusBuffer.get(0x03) & 0x02) != 0x00);
        pumpRecord.setDeliveringInsulin((statusBuffer.get(0x03) & 0x10) != 0x00);
        pumpRecord.setTempBasalActive((statusBuffer.get(0x03) & 0x20) != 0x00);
        pumpRecord.setCgmActive((statusBuffer.get(0x03) & 0x40) != 0x00);

        // Active basal pattern
        pumpRecord.setActiveBasalPattern(statusBuffer.get(0x1a));

        // Normal basal rate
        long rawNormalBasal = statusBuffer.getInt(0x1b);
        pumpRecord.setBasalRate(new BigDecimal(rawNormalBasal / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue());

        // Temp basal rate
        // TODO - need to figure this one out
        //long rawTempBasal = statusBuffer.getShort(0x21) & 0x0000ffff;
        //pumpRecord.setTempBasalRate(new BigDecimal(rawTempBasal / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue());

        // Temp basal percentage
        pumpRecord.setTempBasalPercentage(statusBuffer.get(0x23));

        // Temp basal minutes remaining
        pumpRecord.setTempBasalMinutesRemaining((short) (statusBuffer.getShort(0x24) & 0x0000ffff));

        // Units of insulin delivered as basal today
        // TODO - is this basal? Do we have a total Units delivered elsewhere?
        pumpRecord.setBasalUnitsDeliveredToday(statusBuffer.getInt(0x26));

        // Pump battery percentage
        pumpRecord.setBatteryPercentage((statusBuffer.get(0x2a)));

        // Reservoir amount
        long rawReservoirAmount = statusBuffer.getInt(0x2b);
        pumpRecord.setReservoirAmount(new BigDecimal(rawReservoirAmount / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue());

        // Amount of insulin left in pump (in minutes)
        byte insulinHours = statusBuffer.get(0x2f);
        byte insulinMinutes = statusBuffer.get(0x30);
        pumpRecord.setMinutesOfInsulinRemaining((short) ((insulinHours * 60) + insulinMinutes));

        // Active insulin
        long rawActiveInsulin = statusBuffer.getShort(0x33) & 0x0000ffff;
        pumpRecord.setActiveInsulin(new BigDecimal(rawActiveInsulin / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue());

        // CGM SGV
        pumpRecord.setSgv(statusBuffer.getShort(0x35) & 0x0000ffff); // In mg/DL. 0 means no CGM reading

        // SGV Date
        long rtc;
        long offset;
        if ((pumpRecord.getSgv() & 0x200) == 0x200) {
            // Sensor error. Let's reset. FIXME - solve this more elegantly later
            pumpRecord.setSgv(0);
            rtc = 0;
            offset = 0;
            pumpRecord.setCgmTrend(PumpStatusEvent.CGM_TREND.NOT_SET);
        } else {
            rtc = statusBuffer.getInt(0x37) & 0x00000000ffffffffL;
            offset = statusBuffer.getInt(0x3b);
            pumpRecord.setCgmTrend(fromMessageByte(statusBuffer.get(0x40)));
        }
        // TODO - this should go in the sgvDate, and eventDate should be the time of this poll.
        pumpRecord.setEventDate(new Date(MessageUtils.decodeDateTime(rtc, offset).getTime() - pumpTimeOffset));

        // Predictive low suspend
        // TODO - there is more status info in this byte other than just a boolean yes/no
        pumpRecord.setLowSuspendActive(statusBuffer.get(0x3f) != 0);

        // Recent Bolus Wizard BGL
        pumpRecord.setRecentBolusWizard(statusBuffer.get(0x48) != 0);
        pumpRecord.setBolusWizardBGL(statusBuffer.getShort(0x49) & 0x0000ffff); // In mg/DL

        Log.d(TAG, "Finished getPumpStatus");
    }

    public void getBasalPatterns() throws EncryptionException, IOException, ChecksumException, TimeoutException {
        Log.d(TAG, "Begin getBasalPatterns");
        // FIXME - throw if not in EHSM mode (add a state machine)

        new PumpBasalPatternRequestMessage(mPumpSession).send(this);
        // Read the 0x81
        readMessage();

        // Read the 0x80
        ContourNextLinkMessage response = PumpBasalPatternResponseMessage.fromBytes(mPumpSession, readMessage());

        // TODO - determine message validity
        /*
        if (response.encode().length < (61 + 8)) {
            // Invalid message.
            // TODO - deal with this more elegantly
            Log.e(TAG, "Invalid message received for getBasalPatterns");
            return;
        }
        */

        // FIXME - this needs to go into PumpBasalPatternResponseMessage
        ByteBuffer basalRatesBuffer = ByteBuffer.allocate(96);
        basalRatesBuffer.order(ByteOrder.BIG_ENDIAN);
        basalRatesBuffer.put(response.encode(), 0x39, 96);

        Log.d(TAG, "Finished getBasalPatterns");
    }

    public void endEHSMSession() throws EncryptionException, IOException, TimeoutException, ChecksumException {
        Log.d(TAG, "Begin endEHSMSession");
        new EndEHSMMessage(mPumpSession).send(this);
        // The End EHSM Session only has an 0x81 response
        readMessage();
        Log.d(TAG, "Finished endEHSMSession");
    }

    public void closeConnection() throws IOException, TimeoutException, ChecksumException {
        Log.d(TAG, "Begin closeConnection");
        new ContourNextLinkBinaryMessage(ContourNextLinkBinaryMessage.CommandType.CLOSE_CONNECTION, mPumpSession, null).send(this);
        // FIXME - We need to care what the response message is - wrong MAC and all that
        readMessage();
        Log.d(TAG, "Finished closeConnection");
    }

    public void endPassthroughMode() throws IOException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin endPassthroughMode");
        new ContourNextLinkCommandMessage("W|").send(this);
        checkControlMessage(readMessage(), ASCII.ACK.value);
        new ContourNextLinkCommandMessage("Q|").send(this);
        checkControlMessage(readMessage(), ASCII.ACK.value);
        new ContourNextLinkCommandMessage("0|").send(this);
        checkControlMessage(readMessage(), ASCII.ACK.value);
        Log.d(TAG, "Finished endPassthroughMode");
    }

    public void endControlMode() throws IOException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin endControlMode");
        new ContourNextLinkCommandMessage(ASCII.EOT.value).send(this);
        checkControlMessage(readMessage(), ASCII.ENQ.value);
        Log.d(TAG, "Finished endControlMode");
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

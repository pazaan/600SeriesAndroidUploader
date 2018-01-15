package info.nightscout.android.medtronic;

import android.util.Log;

import org.apache.commons.lang3.ArrayUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.message.BeginEHSMMessage;
import info.nightscout.android.medtronic.message.BolusWizardCarbRatiosRequestMessage;
import info.nightscout.android.medtronic.message.BolusWizardCarbRatiosResponseMessage;
import info.nightscout.android.medtronic.message.BolusWizardSensitivityRequestMessage;
import info.nightscout.android.medtronic.message.BolusWizardSensitivityResponseMessage;
import info.nightscout.android.medtronic.message.BolusWizardTargetsRequestMessage;
import info.nightscout.android.medtronic.message.BolusWizardTargetsResponseMessage;
import info.nightscout.android.medtronic.message.ChannelNegotiateRequestMessage;
import info.nightscout.android.medtronic.message.ChannelNegotiateResponseMessage;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.message.CloseConnectionRequestMessage;
import info.nightscout.android.medtronic.message.ContourNextLinkCommandMessage;
import info.nightscout.android.medtronic.message.DeviceInfoRequestCommandMessage;
import info.nightscout.android.medtronic.message.DeviceInfoResponseCommandMessage;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.message.EndEHSMMessage;
import info.nightscout.android.medtronic.message.MessageUtils;
import info.nightscout.android.medtronic.message.OpenConnectionRequestMessage;
import info.nightscout.android.medtronic.message.PumpBasalPatternRequestMessage;
import info.nightscout.android.medtronic.message.PumpBasalPatternResponseMessage;
import info.nightscout.android.medtronic.message.PumpStatusRequestMessage;
import info.nightscout.android.medtronic.message.PumpStatusResponseMessage;
import info.nightscout.android.medtronic.message.PumpTimeRequestMessage;
import info.nightscout.android.medtronic.message.PumpTimeResponseMessage;
import info.nightscout.android.medtronic.message.ReadHistoryInfoRequestMessage;
import info.nightscout.android.medtronic.message.ReadHistoryInfoResponseMessage;
import info.nightscout.android.medtronic.message.ReadHistoryRequestMessage;
import info.nightscout.android.medtronic.message.ReadHistoryResponseMessage;
import info.nightscout.android.medtronic.message.ReadInfoRequestMessage;
import info.nightscout.android.medtronic.message.ReadInfoResponseMessage;
import info.nightscout.android.medtronic.message.RequestLinkKeyRequestMessage;
import info.nightscout.android.medtronic.message.RequestLinkKeyResponseMessage;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.utils.HexDump;

import static info.nightscout.android.medtronic.message.ContourNextLinkMessage.CNL_READ_TIMEOUT_MS;

/**
 * Created by lgoedhart on 24/03/2016.
 */
public class MedtronicCnlReader {
    private static final String TAG = MedtronicCnlReader.class.getSimpleName();

    private static final byte[] RADIO_CHANNELS = {0x14, 0x11, 0x0e, 0x17, 0x1a};

    private UsbHidDriver mDevice;

    private MedtronicCnlSession mPumpSession = new MedtronicCnlSession();
    private String mStickSerial = null;

    private int cnlCommandMessageSleepMS = 0; // 500

    // provided by getPumpTime - move this to pump session???
    private Date sessionDate;
    private int sessionRTC;
    private int sessionOFFSET;
    private long sessionClockDifference;

    public MedtronicCnlReader(UsbHidDriver device) {
        mDevice = device;
    }

    public String getStickSerial() {
        return mStickSerial;
    }

    public MedtronicCnlSession getPumpSession() {
        return mPumpSession;
    }

    public Date getSessionDate() {
        return sessionDate;
    }

    public int getSessionRTC() {
        return sessionRTC;
    }

    public int getSessionOFFSET() {
        return sessionOFFSET;
    }

    public long getSessionClockDifference() {
        return sessionClockDifference;
    }

    public void setCnlCommandMessageSleepMS(int cnlCommandMessageSleepMS) {
        this.cnlCommandMessageSleepMS = cnlCommandMessageSleepMS;
    }

    public void requestDeviceInfo()
            throws IOException, TimeoutException, UnexpectedMessageException, ChecksumException, EncryptionException {
        DeviceInfoResponseCommandMessage response = new DeviceInfoRequestCommandMessage().send(mDevice);

        //TODO - extract more details form the device info.
        mStickSerial = response.getSerial();
    }

    public void enterControlMode() throws IOException, TimeoutException, UnexpectedMessageException, ChecksumException, EncryptionException {
        try {
            enterControlModeAttempt();
        } catch (TimeoutException e) {
            resetCNL();
            enterControlModeAttempt();
        }
    }

    private void enterControlModeAttempt() throws IOException, TimeoutException, UnexpectedMessageException, ChecksumException, EncryptionException {
        boolean doRetry;

        do {
            doRetry = false;
            try {
                new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.NAK)
                        .send(mDevice, cnlCommandMessageSleepMS, CNL_READ_TIMEOUT_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.EOT);
                new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.ENQ)
                        .send(mDevice, cnlCommandMessageSleepMS, CNL_READ_TIMEOUT_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
            } catch (UnexpectedMessageException e2) {
                try {
                    new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.EOT).send(mDevice);
                } catch (IOException ignored) {}
                finally {
                    doRetry = true;
                }
            }
        } while (doRetry);
    }

    public void enterPassthroughMode() throws IOException, TimeoutException, UnexpectedMessageException, ChecksumException, EncryptionException {
        Log.d(TAG, "Begin enterPasshtroughMode");
        new ContourNextLinkCommandMessage("W|")
                .send(mDevice, cnlCommandMessageSleepMS, CNL_READ_TIMEOUT_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        new ContourNextLinkCommandMessage("Q|")
                .send(mDevice, cnlCommandMessageSleepMS, CNL_READ_TIMEOUT_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        new ContourNextLinkCommandMessage("1|")
                .send(mDevice, cnlCommandMessageSleepMS, CNL_READ_TIMEOUT_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        Log.d(TAG, "Finished enterPasshtroughMode");
    }

    public void openConnection() throws IOException, TimeoutException, NoSuchAlgorithmException, ChecksumException, EncryptionException, UnexpectedMessageException {
        Log.d(TAG, "Begin openConnection");
        new OpenConnectionRequestMessage(mPumpSession, mPumpSession.getHMAC()).send(mDevice);
        Log.d(TAG, "Finished openConnection");
    }

    public void requestReadInfo() throws IOException, TimeoutException, EncryptionException, ChecksumException, UnexpectedMessageException {
        Log.d(TAG, "Begin requestReadInfo");
        ReadInfoResponseMessage response = new ReadInfoRequestMessage(mPumpSession).send(mDevice);

        long linkMAC = response.getLinkMAC();
        long pumpMAC = response.getPumpMAC();

        this.getPumpSession().setLinkMAC(linkMAC);
        this.getPumpSession().setPumpMAC(pumpMAC);
        Log.d(TAG, String.format("Finished requestReadInfo. linkMAC = '%s', pumpMAC = '%s'",
                Long.toHexString(linkMAC), Long.toHexString(pumpMAC)));
    }

    public void requestLinkKey() throws IOException, TimeoutException, EncryptionException, ChecksumException, UnexpectedMessageException {
        Log.d(TAG, "Begin requestLinkKey");

        RequestLinkKeyResponseMessage response = new RequestLinkKeyRequestMessage(mPumpSession).send(mDevice);
        this.getPumpSession().setKey(response.getKey());

        Log.d(TAG, String.format("Finished requestLinkKey. linkKey = '%s'", (Object) this.getPumpSession().getKey()));
    }

    public byte negotiateChannel(byte lastRadioChannel) throws IOException, ChecksumException, TimeoutException, EncryptionException, UnexpectedMessageException {
        ArrayList<Byte> radioChannels = new ArrayList<>(Arrays.asList(ArrayUtils.toObject(RADIO_CHANNELS)));

        // retry strategy: last,chan0,last,chan1,last,chan2,last,chan3,last
        if (lastRadioChannel != 0x00) {
            radioChannels.remove(radioChannels.indexOf(lastRadioChannel));
            radioChannels.add(4, lastRadioChannel);
            radioChannels.add(3, lastRadioChannel);
            radioChannels.add(2, lastRadioChannel);
            radioChannels.add(1, lastRadioChannel);
            radioChannels.add(0, lastRadioChannel);
        }

        Log.d(TAG, "Begin negotiateChannel " + radioChannels);
        for (byte channel : radioChannels) {
            Log.d(TAG, String.format("negotiateChannel: trying channel '%d'...", channel));
            mPumpSession.setRadioChannel(channel);
            ChannelNegotiateResponseMessage response = new ChannelNegotiateRequestMessage(mPumpSession).send(mDevice);

            if (response.getRadioChannel() == mPumpSession.getRadioChannel()) {
                mPumpSession.setRadioRSSI(response.getRadioRSSI());
                break;
            } else {
                mPumpSession.setRadioChannel((byte)0);
                mPumpSession.setRadioRSSI((byte)0);
            }
        }

        Log.d(TAG, String.format("Finished negotiateChannel with channel '%d'", mPumpSession.getRadioChannel()));
        return mPumpSession.getRadioChannel();
    }

    public void beginEHSMSession() throws EncryptionException, IOException, TimeoutException, ChecksumException, UnexpectedMessageException {
        Log.d(TAG, "Begin beginEHSMSession");
        new BeginEHSMMessage(mPumpSession).send(mDevice);
        Log.d(TAG, "Finished beginEHSMSession");
    }

    public Date getPumpTime() throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getPumpTime");

        Message message = new Message() {
            @Override
            PumpTimeResponseMessage request() throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {
                return new PumpTimeRequestMessage(mPumpSession).send(mDevice);
            }
        };

        PumpTimeResponseMessage response = (PumpTimeResponseMessage) message.execute();

        sessionRTC = response.getPumpTimeRTC();
        sessionOFFSET = response.getPumpTimeOFFSET();
        sessionDate = new Date(System.currentTimeMillis());
        sessionClockDifference = response.getPumpTime().getTime() - sessionDate.getTime();

        Log.d(TAG, "Finished getPumpTime with date " + response.getPumpTime());
        return response.getPumpTime();
    }

    public PumpStatusEvent updatePumpStatus(PumpStatusEvent pumpRecord) throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin updatePumpStatus");

        Message message = new Message() {
            @Override
            PumpStatusResponseMessage request() throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {
                return new PumpStatusRequestMessage(mPumpSession).send(mDevice);
            }
        };

        PumpStatusResponseMessage response = (PumpStatusResponseMessage) message.execute();

        response.updatePumpRecord(pumpRecord);

        Log.d(TAG, "Finished updatePumpStatus");
        return pumpRecord;
    }

    public byte[] getBasalPatterns() throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getBasalPatterns");

        ByteArrayOutputStream basalPatterns = new ByteArrayOutputStream();

        for (byte i = 1; i < 9; i ++) {
            PumpBasalPatternResponseMessage response = new PumpBasalPatternRequestMessage(mPumpSession, i).send(mDevice);
            basalPatterns.write(response.getBasalPattern());
        }

        Log.d(TAG, "Basal Pattern x8 data size: " + basalPatterns.size());

        Log.d(TAG, "Finished getBasalPatterns");
        return basalPatterns.toByteArray();
    }

    public byte[] getBolusWizardCarbRatios() throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getBolusWizardCarbRatios");

        BolusWizardCarbRatiosResponseMessage response = new BolusWizardCarbRatiosRequestMessage(mPumpSession).send(mDevice);

        Log.d(TAG, "Finished getBolusWizardCarbRatios");
        return response.getCarbRatios();
    }

    public byte[] getBolusWizardTargets() throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getBolusWizardTargets");

        BolusWizardTargetsResponseMessage response = new BolusWizardTargetsRequestMessage(mPumpSession).send(mDevice);

        Log.d(TAG, "Finished getBolusWizardTargets");
        return response.getTargets();
    }

    public byte[] getBolusWizardSensitivity() throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getBolusWizardCarbRatios");

        BolusWizardSensitivityResponseMessage response = new BolusWizardSensitivityRequestMessage(mPumpSession).send(mDevice);

        Log.d(TAG, "Finished getBolusWizardSensitivity");
        return response.getSensitivity();
    }

    public ReadHistoryInfoResponseMessage getHistoryInfo(long startTime, long endTime, int type) throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getHistoryInfo");

        int startRTC = (int) MessageUtils.rtcFromTime(startTime, sessionOFFSET);
        int endRTC = (int) MessageUtils.rtcFromTime(endTime, sessionOFFSET);
        ReadHistoryInfoResponseMessage response = new ReadHistoryInfoRequestMessage(mPumpSession, startRTC, endRTC, type).send(mDevice);

        Log.d(TAG, "Finished getHistoryInfo");

        return response;
    }

    public void getHistoryLogcat(long startTime, long endTime, int type) throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getHistory");

        int startRTC = (int) MessageUtils.rtcFromTime(startTime, sessionOFFSET);
        int endRTC = (int) MessageUtils.rtcFromTime(endTime, sessionOFFSET);

        ReadHistoryResponseMessage response = new ReadHistoryRequestMessage(mPumpSession, startRTC, endRTC, type).send(mDevice);
        new PumpHistoryParser(response.getEventData()).logcat();

        Log.d(TAG, "Finished getHistory");
    }

    public Date[] getHistory(long startTime, long endTime, final int type) throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getHistory");

        long maxRTC = sessionRTC & 0xFFFFFFFFL;
        long minRTC = maxRTC - ((90 * 24 * 60 * 60) - 3600);

        // adjust min RTC to allow for a new pump with <90 days on the RTC clock
        if (minRTC < 0x80000000L) minRTC = 0x80000000L;

        long reqStartRTC = MessageUtils.rtcFromTime(startTime, sessionOFFSET);
        long reqEndRTC = MessageUtils.rtcFromTime(endTime, sessionOFFSET);
        Log.d (TAG, "getHistory: reqStartRTC=" + HexDump.toHexString(reqStartRTC) + " reqEndRTC=" + HexDump.toHexString(reqEndRTC));

        // check RTC bounds as pump doesn't like out of range requests

        if (reqEndRTC < minRTC || reqStartRTC > maxRTC) {
            // out of RTC range, return start/end dates as processed period
            return new Date[] {new Date(startTime), new Date(endTime)};
        }

        if (reqEndRTC > maxRTC) {
            reqEndRTC = maxRTC;
            endTime = sessionDate.getTime();
        }

        if (reqStartRTC < minRTC) reqStartRTC = minRTC;

        final int startRTC = (int) reqStartRTC;
        final int endRTC = (int) reqEndRTC;
        Log.d (TAG, "getHistory: final startRTC=" + HexDump.toHexString(startRTC) + " endRTC=" + HexDump.toHexString(endRTC));

        Message message = new Message() {
            @Override
            ReadHistoryResponseMessage request() throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {
                return new ReadHistoryRequestMessage(mPumpSession, startRTC, endRTC, type).send(mDevice);
            }
        };

        ReadHistoryResponseMessage response = (ReadHistoryResponseMessage) message.execute();

        Date[] range = new PumpHistoryParser(response.getEventData()).process(sessionRTC, sessionOFFSET, sessionClockDifference, startTime, endTime);

        Log.d(TAG, "Finished getHistory");
        return range;
    }

    public void endEHSMSession() throws EncryptionException, IOException, TimeoutException, ChecksumException, UnexpectedMessageException {
        Log.d(TAG, "Begin endEHSMSession");
        new EndEHSMMessage(mPumpSession).send(mDevice);
        Log.d(TAG, "Finished endEHSMSession");
    }

    public void closeConnection() throws IOException, TimeoutException, ChecksumException, EncryptionException, NoSuchAlgorithmException, UnexpectedMessageException {
        Log.d(TAG, "Begin closeConnection");
        new CloseConnectionRequestMessage(mPumpSession, mPumpSession.getHMAC()).send(mDevice);
        Log.d(TAG, "Finished closeConnection");
    }

    public void endPassthroughMode() throws IOException, TimeoutException, UnexpectedMessageException, ChecksumException, EncryptionException {
        Log.d(TAG, "Begin endPassthroughMode");
        new ContourNextLinkCommandMessage("W|")
                .send(mDevice, cnlCommandMessageSleepMS, CNL_READ_TIMEOUT_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        new ContourNextLinkCommandMessage("Q|")
                .send(mDevice, cnlCommandMessageSleepMS, CNL_READ_TIMEOUT_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        new ContourNextLinkCommandMessage("0|")
                .send(mDevice, cnlCommandMessageSleepMS, CNL_READ_TIMEOUT_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        Log.d(TAG, "Finished endPassthroughMode");
    }

    public void endControlMode() throws IOException, TimeoutException, UnexpectedMessageException, ChecksumException, EncryptionException {
        Log.d(TAG, "Begin endControlMode");
        new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.EOT)
                .send(mDevice, cnlCommandMessageSleepMS, CNL_READ_TIMEOUT_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ENQ);
        Log.d(TAG, "Finished endControlMode");
    }

    // helps to recover a CNL in a timeout state when trying to connect
    public boolean resetCNL() {
        Log.d(TAG, "Begin resetCNL");
        boolean success = false;
        int retry = 10;

        do {
            try {
                new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.EOT)
                        .send(mDevice, 0, CNL_READ_TIMEOUT_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ENQ);
                success = true;
            } catch (IOException | EncryptionException | ChecksumException | UnexpectedMessageException | TimeoutException ignored) { }
        } while (!success && --retry > 0);

        Log.d(TAG, "Finished resetCNL");
        return success;
    }

    private class Message {

        Object request() throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {
            return null;
        }

        Object execute() throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {

            int unexpected = 0;
            int timeout = 0;

            Object response = null;

            do {
                try {
                    response = request();
                    unexpected = 0;
                    timeout = 0;
                } catch (UnexpectedMessageException e) {
                    Log.e(TAG, "Attempt: " + (unexpected + 1) + " UnexpectedMessageException: " + e.getMessage());
                    // needs to end immediately on these errors
                    if (e.getMessage().contains("0x81 response was empty") || e.getMessage().contains("NAK")) {
                        throw new UnexpectedMessageException(e.getMessage());
                    }
                    // retry (5x or around 30 seconds for attempts)
                    if (++unexpected >= 5) {
                        throw new UnexpectedMessageException("retry failed, " + e.getMessage());
                    }
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {}
                } catch (TimeoutException e) {
                    Log.e(TAG, "Attempt: " + (timeout + 1) + " TimeoutException: " + e.getMessage());
                    // needs to end immediately on these errors
                    if (e.getMessage().contains("Timeout waiting for 0x81 response")) {
                        throw new TimeoutException(e.getMessage());
                    }
                    // retry (3x or around 30 seconds for attempts)
                    if (++timeout >= 3) {
                        throw new TimeoutException("retry failed, " + e.getMessage());
                    }
                }
            } while (unexpected > 0 || timeout > 0);

            return response;
        }
    }

}

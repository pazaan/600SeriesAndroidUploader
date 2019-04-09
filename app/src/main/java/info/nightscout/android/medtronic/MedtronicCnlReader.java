package info.nightscout.android.medtronic;

import android.util.Log;

import org.apache.commons.lang3.ArrayUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.history.PumpHistoryParser;
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
import info.nightscout.android.medtronic.message.DiscoveryRequestMessage;
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

    public void requestDeviceInfo() throws IOException, TimeoutException, UnexpectedMessageException, ChecksumException, EncryptionException {
        Log.d(TAG, "Begin requestDeviceInfo");
        DeviceInfoResponseCommandMessage response = new DeviceInfoRequestCommandMessage().send(mDevice);

        //TODO - extract more details form the device info.
        mStickSerial = response.getSerial();

        Log.d(TAG, "Finished requestDeviceInfo");
    }

    public void enterControlMode() throws IOException, TimeoutException, UnexpectedMessageException, ChecksumException, EncryptionException {
        Log.d(TAG, "Begin enterControlMode");
        try {
            enterControlModeAttempt();
        } catch (TimeoutException e) {
            resetCNL();
            enterControlModeAttempt();
        }
        Log.d(TAG, "Finished enterControlMode");
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
                    new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.EOT).send(mDevice, cnlCommandMessageSleepMS, CNL_READ_TIMEOUT_MS);
                } catch (IOException ignored) {}
                finally {
                    doRetry = true;
                }
            }
        } while (doRetry);
    }

    public void enterPassthroughMode() throws IOException, TimeoutException, UnexpectedMessageException, ChecksumException, EncryptionException {
        Log.d(TAG, "Begin enterPassthroughMode");
        new ContourNextLinkCommandMessage("W|")
                .send(mDevice, cnlCommandMessageSleepMS, CNL_READ_TIMEOUT_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        new ContourNextLinkCommandMessage("Q|")
                .send(mDevice, cnlCommandMessageSleepMS, CNL_READ_TIMEOUT_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        new ContourNextLinkCommandMessage("1|")
                .send(mDevice, cnlCommandMessageSleepMS, CNL_READ_TIMEOUT_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        Log.d(TAG, "Finished enterPassthroughMode");
    }

    public void openConnection() throws IOException, TimeoutException, NoSuchAlgorithmException, ChecksumException, EncryptionException, UnexpectedMessageException {
        Log.d(TAG, "Begin openConnection");
        new OpenConnectionRequestMessage(mPumpSession, mPumpSession.getHMAC()).send(mDevice, 0 , CNL_READ_TIMEOUT_MS);
        Log.d(TAG, "Finished openConnection");
    }

    public void requestReadInfo() throws IOException, TimeoutException, EncryptionException, ChecksumException, UnexpectedMessageException {
        Log.d(TAG, "Begin requestReadInfo");
        ReadInfoResponseMessage response = new ReadInfoRequestMessage(mPumpSession).send(mDevice, 0 , CNL_READ_TIMEOUT_MS);

        long linkMAC = response.getLinkMAC();
        long pumpMAC = response.getPumpMAC();

        this.getPumpSession().setLinkMAC(linkMAC);
        this.getPumpSession().setPumpMAC(pumpMAC);
        Log.d(TAG, String.format("Finished requestReadInfo. linkMAC = '%s', pumpMAC = '%s'",
                Long.toHexString(linkMAC), Long.toHexString(pumpMAC)));
    }

    public void requestLinkKey() throws IOException, TimeoutException, EncryptionException, ChecksumException, UnexpectedMessageException {
        Log.d(TAG, "Begin requestLinkKey");

        RequestLinkKeyResponseMessage response = new RequestLinkKeyRequestMessage(mPumpSession).send(mDevice, 0 , CNL_READ_TIMEOUT_MS);
        this.getPumpSession().setKey(response.getKey());

        Log.d(TAG, String.format("Finished requestLinkKey. linkKey = '%s'", (Object) this.getPumpSession().getKey()));
    }

    public byte negotiateChannel(byte lastRadioChannel) throws IOException, ChecksumException, TimeoutException, EncryptionException, UnexpectedMessageException {
        ArrayList<Byte> radioChannels = new ArrayList<>(Arrays.asList(ArrayUtils.toObject(RADIO_CHANNELS)));

        // retry strategy: last,chan0,chan1,last,chan2,chan3,last
        if (lastRadioChannel != 0x00) {
            //noinspection RedundantCollectionOperation
            radioChannels.remove(radioChannels.indexOf(lastRadioChannel));
            radioChannels.add(4, lastRadioChannel);
            radioChannels.add(2, lastRadioChannel);
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

    public void discovery() throws EncryptionException, IOException, TimeoutException, ChecksumException, UnexpectedMessageException {
        Log.d(TAG, "Begin beginDiscoverySession");
        new DiscoveryRequestMessage(mPumpSession).send(mDevice);
        Log.d(TAG, "Finished beginDiscoverySession");
    }

    public void beginEHSMSession() throws EncryptionException, IOException, TimeoutException, ChecksumException, UnexpectedMessageException {
        Log.d(TAG, "Begin beginEHSMSession");
        new BeginEHSMMessage(mPumpSession).send(mDevice);
        Log.d(TAG, "Finished beginEHSMSession");
    }

    public Date getPumpTime() throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getPumpTime");

        RequestMessage requestMessage = new RequestMessage() {
            @Override
            PumpTimeResponseMessage request() throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {
                return new PumpTimeRequestMessage(mPumpSession).send(mDevice);
            }
        };

        PumpTimeResponseMessage response = (PumpTimeResponseMessage) requestMessage.execute();

        sessionRTC = response.getPumpTimeRTC();
        sessionOFFSET = response.getPumpTimeOFFSET();
        sessionDate = new Date(Calendar.getInstance().getTimeInMillis());
        sessionClockDifference = response.getPumpTime().getTime() - sessionDate.getTime();

        Log.d(TAG, "Finished getPumpTime with date " + response.getPumpTime());
        return response.getPumpTime();
    }

    public PumpStatusEvent updatePumpStatus(PumpStatusEvent pumpRecord) throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin updatePumpStatus");

        RequestMessage requestMessage = new RequestMessage() {
            @Override
            PumpStatusResponseMessage request() throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {
                return new PumpStatusRequestMessage(mPumpSession).send(mDevice);
            }
        };

        PumpStatusResponseMessage response = (PumpStatusResponseMessage) requestMessage.execute();

        response.updatePumpRecord(pumpRecord);

        Log.d(TAG, "Finished updatePumpStatus");
        return pumpRecord;
    }

    public PumpStatusResponseMessage updatePumpStatus() throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin updatePumpStatus");

        RequestMessage requestMessage = new RequestMessage() {
            @Override
            PumpStatusResponseMessage request() throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {
                return new PumpStatusRequestMessage(mPumpSession).send(mDevice);
            }
        };

        PumpStatusResponseMessage response = (PumpStatusResponseMessage) requestMessage.execute();

        Log.d(TAG, "Finished updatePumpStatus");
        return response;
    }

    public byte[] getBasalPatterns() throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getBasalPatterns");

        ByteArrayOutputStream basalPatterns = new ByteArrayOutputStream();

        for (byte i = 1; i < 9; i++) {

            final byte ii = i;
            RequestMessage requestMessage = new RequestMessage() {
                @Override
                PumpBasalPatternResponseMessage request() throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {
                    return new PumpBasalPatternRequestMessage(mPumpSession, ii).send(mDevice);
                }
            };

            PumpBasalPatternResponseMessage response = (PumpBasalPatternResponseMessage) requestMessage.execute();
            basalPatterns.write(response.getBasalPattern());
        }

        Log.d(TAG, "Basal Pattern x8 data size: " + basalPatterns.size());

        Log.d(TAG, "Finished getBasalPatterns");
        return basalPatterns.toByteArray();
    }

    public byte[] getBolusWizardCarbRatios() throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getBolusWizardCarbRatios");

        RequestMessage requestMessage = new RequestMessage() {
            @Override
            BolusWizardCarbRatiosResponseMessage request() throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {
                return new BolusWizardCarbRatiosRequestMessage(mPumpSession).send(mDevice);
            }
        };

        BolusWizardCarbRatiosResponseMessage response = (BolusWizardCarbRatiosResponseMessage) requestMessage.execute();

        Log.d(TAG, "Finished getBolusWizardCarbRatios");
        return response.getCarbRatios();
    }

    public byte[] getBolusWizardTargets() throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getBolusWizardTargets");

        RequestMessage requestMessage = new RequestMessage() {
            @Override
            BolusWizardTargetsResponseMessage request() throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {
                return new BolusWizardTargetsRequestMessage(mPumpSession).send(mDevice);
            }
        };

        BolusWizardTargetsResponseMessage response = (BolusWizardTargetsResponseMessage) requestMessage.execute();

        Log.d(TAG, "Finished getBolusWizardTargets");
        return response.getTargets();
    }

    public byte[] getBolusWizardSensitivity() throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getBolusWizardCarbRatios");

        RequestMessage requestMessage = new RequestMessage() {
            @Override
            BolusWizardSensitivityResponseMessage request() throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {
                return new BolusWizardSensitivityRequestMessage(mPumpSession).send(mDevice);
            }
        };

        BolusWizardSensitivityResponseMessage response = (BolusWizardSensitivityResponseMessage) requestMessage.execute();

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
        Log.d(TAG, "Begin getHistoryLogcat");

        int startRTC = (int) MessageUtils.rtcFromTime(startTime, sessionOFFSET);
        int endRTC = (int) MessageUtils.rtcFromTime(endTime, sessionOFFSET);

        ReadHistoryResponseMessage response = new ReadHistoryRequestMessage(mPumpSession, startRTC, endRTC, type).send(mDevice);
        new PumpHistoryParser(response.getEventData()).logcat();

        Log.d(TAG, "Finished getHistoryLogcat");
    }

    public ReadHistoryResponseMessage getHistory(long startTime, long endTime, final int type) throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getHistory");

        long maxRTC = sessionRTC & 0xFFFFFFFFL;
        long minRTC = maxRTC - ((90 * 24 * 60 * 60) - 3600);

        // adjust min RTC to allow for a new pump with <90 days on the RTC clock
        if (minRTC < 0x80000000L) minRTC = 0x80000001L;
        Log.d (TAG, "getHistory: minRTC=" + HexDump.toHexString(minRTC) + " maxRTC=" + HexDump.toHexString(maxRTC));

        long reqStartRTC = MessageUtils.rtcFromTime(startTime + sessionClockDifference, sessionOFFSET);
        long reqEndRTC = MessageUtils.rtcFromTime(endTime + sessionClockDifference, sessionOFFSET);
        Log.d (TAG, "getHistory: reqStartRTC=" + HexDump.toHexString(reqStartRTC) + " reqEndRTC=" + HexDump.toHexString(reqEndRTC));

        // check RTC bounds as pump doesn't like out of range requests

        if (reqEndRTC < minRTC || reqStartRTC > maxRTC) {
            Log.d (TAG, "getHistory: out of RTC range, no events for requested period");
            return null;
        }

        if (reqEndRTC > maxRTC) {
            reqEndRTC = maxRTC;
            endTime = sessionDate.getTime();
        }

        if (reqStartRTC < minRTC) reqStartRTC = minRTC;

        final int startRTC = (int) reqStartRTC;
        final int endRTC = (int) reqEndRTC;
        Log.d (TAG, "getHistory: final startRTC=" + HexDump.toHexString(startRTC) + " endRTC=" + HexDump.toHexString(endRTC));

        RequestMessage requestMessage = new RequestMessage() {
            @Override
            ReadHistoryResponseMessage request() throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {
                return new ReadHistoryRequestMessage(mPumpSession, startRTC, endRTC, type).send(mDevice);
            }
        };

        ReadHistoryResponseMessage response = (ReadHistoryResponseMessage) requestMessage.execute();

        response.setReqStartRTC(startRTC);
        response.setReqEndRTC(endRTC);
        response.setReqType(type);
        response.setReqStartTime(startTime);
        response.setReqEndTime(endTime);

        return response;
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

    private class RequestMessage {

        Object request() throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {
            return null;
        }

        Object execute() throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {

            long starttime = System.currentTimeMillis();
            long retrytime = 30000L;

            StringBuilder sb = new StringBuilder();

            int attempt = 0;

            Object response = null;

            do {
                try {
                    response = request();
                    attempt = 0;

                } catch (UnexpectedMessageException e) {
                    attempt++;
                    String error = String.format("Attempt %s: UnexpectedMessageException: %s", attempt, e.getMessage());
                    Log.e(TAG, error);
                    sb.append("\n").append(error);

                    // needs to end immediately on these errors
                    if (e.getMessage().contains("connection lost") || e.getMessage().contains("NAK")) {
                        throw new UnexpectedMessageException(sb.toString());
                    }

                    if (System.currentTimeMillis() - starttime >= retrytime)
                        throw new UnexpectedMessageException(sb.toString());

                } catch (TimeoutException e) {
                    attempt++;
                    String error = String.format("Attempt %s: TimeoutException: %s", attempt, e.getMessage());
                    Log.e(TAG, error);
                    sb.append("\n").append(error);

                    // needs to end immediately on these errors
                    if (e.getMessage().contains("Timeout waiting for 0x81 response")) {
                        throw new TimeoutException(sb.toString());
                    }

                    if (System.currentTimeMillis() - starttime >= retrytime)
                        throw new TimeoutException(sb.toString());
                }

            } while (attempt > 0);

            return response;
        }
    }

}

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
import info.nightscout.android.medtronic.message.ReadHistoryRequestMessage;
import info.nightscout.android.medtronic.message.ReadHistoryResponseMessage;
import info.nightscout.android.medtronic.message.ReadInfoRequestMessage;
import info.nightscout.android.medtronic.message.ReadInfoResponseMessage;
import info.nightscout.android.medtronic.message.RequestLinkKeyRequestMessage;
import info.nightscout.android.medtronic.message.RequestLinkKeyResponseMessage;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;

/**
 * Created by lgoedhart on 24/03/2016.
 */
public class MedtronicCnlReader {
    private static final String TAG = MedtronicCnlReader.class.getSimpleName();

    private static final byte[] RADIO_CHANNELS = {0x14, 0x11, 0x0e, 0x17, 0x1a};
    private UsbHidDriver mDevice;

    private MedtronicCnlSession mPumpSession = new MedtronicCnlSession();
    private String mStickSerial = null;

    private static final int SLEEP_MS = 0; //500;

    public MedtronicCnlReader(UsbHidDriver device) {
        mDevice = device;
    }

    public String getStickSerial() {
        return mStickSerial;
    }

    public MedtronicCnlSession getPumpSession() {
        return mPumpSession;
    }

    public void requestDeviceInfo()
            throws IOException, TimeoutException, UnexpectedMessageException, ChecksumException, EncryptionException {
        DeviceInfoResponseCommandMessage response = new DeviceInfoRequestCommandMessage().send(mDevice);

        //TODO - extract more details form the device info.
        mStickSerial = response.getSerial();
    }
/*
    public void enterControlMode() throws IOException, TimeoutException, UnexpectedMessageException, ChecksumException, EncryptionException {
        boolean doRetry;

        do {
            doRetry = false;
            try {
                new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.NAK)
                        .send(mDevice, SLEEP_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.EOT);
                new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.ENQ)
                        .send(mDevice, SLEEP_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
            } catch (UnexpectedMessageException e2) {
                try {
                    new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.EOT).send(mDevice);
                } catch (IOException e) {}
                finally {
                    doRetry = true;
                }
            }
        } while (doRetry);
    }
*/

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
                        .send(mDevice, SLEEP_MS, 2000).checkControlMessage(ContourNextLinkCommandMessage.ASCII.EOT);
                new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.ENQ)
                        .send(mDevice, SLEEP_MS, 2000).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
            } catch (UnexpectedMessageException e2) {
                try {
                    new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.EOT).send(mDevice);
                } catch (IOException e) {}
                finally {
                    doRetry = true;
                }
            }
        } while (doRetry);
    }

    /*
    public void enterControlMode() throws IOException, TimeoutException, UnexpectedMessageException, ChecksumException, EncryptionException {
        boolean doRetry;

        do {
            doRetry = false;
            try {
                new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.NAK)
                        .send(mDevice, SLEEP_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.EOT);
                new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.ENQ)
                        .send(mDevice, SLEEP_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);

            } catch (TimeoutException e2) {
                Log.d(TAG, "enterControlMode TimeoutException : trying to reset");
                try {
                    new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.EOT).send(mDevice);
                } catch (IOException e) {}
                finally {
                    doRetry = true;
                }

            } catch (UnexpectedMessageException e2) {
                Log.d(TAG, "enterControlMode UnexpectedMessageException : trying to reset");
                try {
                    new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.EOT).send(mDevice);
                } catch (IOException e) {}
                finally {
                    doRetry = true;
                }
            }
        } while (doRetry);
    }
*/

    public void enterPassthroughMode() throws IOException, TimeoutException, UnexpectedMessageException, ChecksumException, EncryptionException {
        Log.d(TAG, "Begin enterPasshtroughMode");
        new ContourNextLinkCommandMessage("W|")
                .send(mDevice, SLEEP_MS, 2000).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        new ContourNextLinkCommandMessage("Q|")
                .send(mDevice, SLEEP_MS, 2000).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        new ContourNextLinkCommandMessage("1|")
                .send(mDevice, SLEEP_MS, 2000).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
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

        if (lastRadioChannel != 0x00) {
            // If we know the last channel that was used, shuffle the negotiation order
            Byte lastChannel = radioChannels.remove(radioChannels.indexOf(lastRadioChannel));

            if (lastChannel != null) {
                radioChannels.add(0, lastChannel);
                radioChannels.add(5, lastChannel);  // retry last used channel again, this allows for transient noise if missed on first attempt when pump is in range
            }
        }

        Log.d(TAG, "Begin negotiateChannel");
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
        mPumpSession.setEHSMmode(true);
        Log.d(TAG, "Finished beginEHSMSession");
    }

    public Date getPumpTime() throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getPumpTime");

//        PumpTimeResponseMessage response = new PumpTimeRequestMessage(mPumpSession).send(mDevice);

        PumpTimeResponseMessage response = null;
        int unexpected = 0;
        int timeout = 0;
        do {
            try {
                response = new PumpTimeRequestMessage(mPumpSession).send(mDevice);
                unexpected = 0;
                timeout = 0;
            } catch (UnexpectedMessageException e) {
                Log.e(TAG, "Attempt: " + (unexpected + 1) + " UnexpectedMessageException: " + e.getMessage());
                if (e.getMessage().contains("0x81 response was empty")) {
                    throw new TimeoutException(e.getMessage() + " *** ending comms now!!!");
                }
                if (++unexpected >= 10) {
                    throw new UnexpectedMessageException("Retry failed: " + e.getMessage());
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e1) {}
            } catch (TimeoutException e) {
                Log.e(TAG, "Attempt: " + (timeout + 1) + " TimeoutException: " + e.getMessage());
                if (e.getMessage().contains("Timeout waiting for 0x81 response")) {
                    throw new TimeoutException(e.getMessage());
                }
                if (++timeout >= 3) {
                    throw new TimeoutException("Retry failed: " + e.getMessage());
                }
            }
        } while (unexpected > 0 || timeout > 0);

        Log.d(TAG, "Finished getPumpTime with date " + response.getPumpTime());
        return response.getPumpTime();
    }

    public PumpStatusEvent updatePumpStatus(PumpStatusEvent pumpRecord) throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin updatePumpStatus");

//        PumpStatusResponseMessage response = new PumpStatusRequestMessage(mPumpSession).send(mDevice);
//        response.updatePumpRecord(pumpRecord);

        PumpStatusResponseMessage response = null;
        int unexpected = 0;
        int timeout = 0;
        do {
            try {
                response = new PumpStatusRequestMessage(mPumpSession).send(mDevice);
                unexpected = 0;
                timeout = 0;
            } catch (UnexpectedMessageException e) {
                Log.e(TAG, "Attempt: " + (unexpected + 1) + " UnexpectedMessageException: " + e.getMessage());
                if (e.getMessage().contains("0x81 response was empty")) {
                    throw new TimeoutException(e.getMessage() + " *** ending comms now!!!");
                }
                if (++unexpected >= 10) {
                    throw new UnexpectedMessageException("Retry failed: " + e.getMessage());
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e1) {}
            } catch (TimeoutException e) {
                Log.e(TAG, "Attempt: " + (timeout + 1) + " TimeoutException: " + e.getMessage());
                if (e.getMessage().contains("Timeout waiting for 0x81 response")) {
                    throw new TimeoutException(e.getMessage());
                }
                if (++timeout >= 3) {
                    throw new TimeoutException("Retry failed: " + e.getMessage());
                }
            }
        } while (unexpected > 0 || timeout > 0);
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

    public void getHistoryInfo(long startTime, long endTime, int offset, int type) throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getHistoryInfo");

        int startRTC = MessageUtils.rtcFromTime(startTime, offset);
        int endRTC = MessageUtils.rtcFromTime(endTime, offset);
        new ReadHistoryInfoRequestMessage(mPumpSession, startRTC, endRTC, type).send(mDevice);

        Log.d(TAG, "Finished getHistoryInfo");
    }

    public void getHistory(long startTime, long endTime, int offset, int type) throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getHistory");

        int startRTC = MessageUtils.rtcFromTime(startTime, offset);
        int endRTC = MessageUtils.rtcFromTime(endTime, offset);
        new ReadHistoryInfoRequestMessage(mPumpSession, startRTC, endRTC, type).send(mDevice);

        ReadHistoryResponseMessage response = new ReadHistoryRequestMessage(mPumpSession, startRTC, endRTC, type).send(mDevice);
        response.logcat();

        Log.d(TAG, "Finished getHistory");
    }

    public Date[] getHistoryX(long startTime, long endTime, int offset, int type) throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getHistory");

        int startRTC = MessageUtils.rtcFromTime(startTime, offset);
        int endRTC = MessageUtils.rtcFromTime(endTime, offset);

        ReadHistoryResponseMessage response = null;
        int unexpected = 0;
        int timeout = 0;
        do {
            try {
                response = new ReadHistoryRequestMessage(mPumpSession, startRTC, endRTC, type).send(mDevice);
                unexpected = 0;
                timeout = 0;
            } catch (UnexpectedMessageException e) {
                Log.e(TAG, "Attempt: " + (unexpected + 1) + " UnexpectedMessageException: " + e.getMessage());
                if (e.getMessage().contains("0x81 response was empty")) {
                    throw new TimeoutException(e.getMessage() + " *** ending comms now!!!");
                }
                if (++unexpected >= 10) {
                    throw new UnexpectedMessageException("Retry failed: " + e.getMessage());
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e1) {}
            } catch (TimeoutException e) {
                Log.e(TAG, "Attempt: " + (timeout + 1) + " TimeoutException: " + e.getMessage());
                if (e.getMessage().contains("Timeout waiting for 0x81 response")) {
                    throw new TimeoutException(e.getMessage());
                }
                if (++timeout >= 3) {
                    throw new TimeoutException("Retry failed: " + e.getMessage());
                }
            }
        } while (unexpected > 0 || timeout > 0);

        Date[] range = response.updatePumpHistory();

        Log.d(TAG, "Finished getHistory");
        return range;
    }

    public void endEHSMSession() throws EncryptionException, IOException, TimeoutException, ChecksumException, UnexpectedMessageException {
        Log.d(TAG, "Begin endEHSMSession");
        new EndEHSMMessage(mPumpSession).send(mDevice);
        mPumpSession.setEHSMmode(false);
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
                .send(mDevice, SLEEP_MS, 2000).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        new ContourNextLinkCommandMessage("Q|")
                .send(mDevice, SLEEP_MS, 2000).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        new ContourNextLinkCommandMessage("0|")
                .send(mDevice, SLEEP_MS, 2000).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        Log.d(TAG, "Finished endPassthroughMode");
    }

    public void endControlMode() throws IOException, TimeoutException, UnexpectedMessageException, ChecksumException, EncryptionException {
        Log.d(TAG, "Begin endControlMode");
        new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.EOT)
                .send(mDevice, SLEEP_MS, 2000).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ENQ);
        Log.d(TAG, "Finished endControlMode");
    }

    public boolean resetCNL() {
        Log.d(TAG, "Begin resetCNL");
        boolean success = false;
        int retry = 5;

        do {
            try {
                new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.EOT)
                        .send(mDevice, SLEEP_MS, 2000).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ENQ);
                success = true;
            } catch (IOException e) {
            } catch (TimeoutException e) {
            } catch (UnexpectedMessageException e) {
            } catch (ChecksumException e) {
            } catch (EncryptionException e) {
            }
        } while (!success && --retry > 0);

        Log.d(TAG, "Finished resetCNL");
        return success;
    }

}

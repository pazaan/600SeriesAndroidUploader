package info.nightscout.android.medtronic;

import android.util.Log;

import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.message.BeginEHSMMessage;
import info.nightscout.android.medtronic.message.ChannelNegotiateRequestMessage;
import info.nightscout.android.medtronic.message.ChannelNegotiateResponseMessage;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.message.CloseConnectionRequestMessage;
import info.nightscout.android.medtronic.message.ContourNextLinkCommandMessage;
import info.nightscout.android.medtronic.message.DeviceInfoRequestCommandMessage;
import info.nightscout.android.medtronic.message.DeviceInfoResponseCommandMessage;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.message.EndEHSMMessage;
import info.nightscout.android.medtronic.message.OpenConnectionRequestMessage;
import info.nightscout.android.medtronic.message.PumpBasalPatternRequestMessage;
import info.nightscout.android.medtronic.message.PumpBasalPatternResponseMessage;
import info.nightscout.android.medtronic.message.PumpStatusRequestMessage;
import info.nightscout.android.medtronic.message.PumpStatusResponseMessage;
import info.nightscout.android.medtronic.message.PumpTimeRequestMessage;
import info.nightscout.android.medtronic.message.PumpTimeResponseMessage;
import info.nightscout.android.medtronic.message.ReadHistoryInfoRequestMessage;
import info.nightscout.android.medtronic.message.ReadHistoryInfoResponseMessage;
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

    private static final int SLEEP_MS = 500;

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

    public void enterPassthroughMode() throws IOException, TimeoutException, UnexpectedMessageException, ChecksumException, EncryptionException {
        Log.d(TAG, "Begin enterPasshtroughMode");
        new ContourNextLinkCommandMessage("W|")
                .send(mDevice, SLEEP_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        new ContourNextLinkCommandMessage("Q|")
                .send(mDevice, SLEEP_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        new ContourNextLinkCommandMessage("1|")
                .send(mDevice, SLEEP_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
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

    public byte negotiateChannel(byte lastRadioChannel) throws IOException, ChecksumException, TimeoutException, EncryptionException {
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
        Log.d(TAG, "Finished beginEHSMSession");
    }

    public Date getPumpTime() throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getPumpTime");

        PumpTimeResponseMessage response = new PumpTimeRequestMessage(mPumpSession).send(mDevice);

        Log.d(TAG, "Finished getPumpTime with date " + response.getPumpTime());
        return response.getPumpTime();
    }

    public PumpStatusEvent updatePumpStatus(PumpStatusEvent pumpRecord) throws IOException, EncryptionException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin updatePumpStatus");

        PumpStatusResponseMessage response = new PumpStatusRequestMessage(mPumpSession).send(mDevice);
        response.updatePumpRecord(pumpRecord);

        Log.d(TAG, "Finished updatePumpStatus");
        return pumpRecord;
    }

    public void getBasalPatterns() throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getBasalPatterns");
        // FIXME - throw if not in EHSM mode (add a state machine)

        PumpBasalPatternResponseMessage response = new PumpBasalPatternRequestMessage(mPumpSession).send(mDevice);

        Log.d(TAG, "Finished getBasalPatterns");
    }


    public void getHistory() throws EncryptionException, IOException, ChecksumException, TimeoutException, UnexpectedMessageException {
        Log.d(TAG, "Begin getHistory");
        // FIXME - throw if not in EHSM mode (add a state machine)

        ReadHistoryInfoResponseMessage response = new ReadHistoryInfoRequestMessage(mPumpSession).send(mDevice);

        Log.d(TAG, "Finished getHistory");
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
                .send(mDevice, SLEEP_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        new ContourNextLinkCommandMessage("Q|")
                .send(mDevice, SLEEP_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        new ContourNextLinkCommandMessage("0|")
                .send(mDevice, SLEEP_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ACK);
        Log.d(TAG, "Finished endPassthroughMode");
    }

    public void endControlMode() throws IOException, TimeoutException, UnexpectedMessageException, ChecksumException, EncryptionException {
        Log.d(TAG, "Begin endControlMode");
        new ContourNextLinkCommandMessage(ContourNextLinkCommandMessage.ASCII.EOT)
                .send(mDevice, SLEEP_MS).checkControlMessage(ContourNextLinkCommandMessage.ASCII.ENQ);
        Log.d(TAG, "Finished endControlMode");
    }
}

package info.nightscout.android.medtronic.message;

import java.io.IOException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class PumpBasalPatternRequestMessage extends MedtronicRequestMessage {
    public PumpBasalPatternRequestMessage(MedtronicCnlSession pumpSession) throws EncryptionException, ChecksumException {
        super(SendMessageType.READ_BASAL_PATTERN_REQUEST, pumpSession, null);
    }

    protected void sendMessage(UsbHidDriver mDevice) throws IOException {
        super.sendMessage(mDevice);
        mPumpSession.incrMedtronicSequenceNumber();
    }
}

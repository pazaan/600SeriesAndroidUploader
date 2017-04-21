package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class PumpBasalPatternRequestMessage extends MedtronicSendMessageRequestMessage {
    public PumpBasalPatternRequestMessage(MedtronicCnlSession pumpSession) throws EncryptionException, ChecksumException {
        super(SendMessageType.READ_BASAL_PATTERN_REQUEST, pumpSession, null);
    }

    public PumpBasalPatternResponseMessage send(UsbHidDriver mDevice) throws IOException, TimeoutException, ChecksumException, EncryptionException {
        sendMessage(mDevice);

        // Read the 0x81
        PumpBasalPatternResponseMessage response = new PumpBasalPatternResponseMessage(mPumpSession, readMessage(mDevice));

        return response;
    }
}

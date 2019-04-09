package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by Pogman on 8.11.17.
 */

public class BolusWizardSensitivityRequestMessage extends MedtronicSendMessageRequestMessage<BolusWizardSensitivityResponseMessage> {
    private static final String TAG = BolusWizardSensitivityRequestMessage.class.getSimpleName();

    public BolusWizardSensitivityRequestMessage(MedtronicCnlSession pumpSession) throws EncryptionException, ChecksumException {
        super(MessageType.READ_BOLUS_WIZARD_SENSITIVITY_FACTORS, pumpSession, null);
    }

    public BolusWizardSensitivityResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {
        sendToPump(mDevice, TAG);
        return getResponse(readFromPump(mDevice, mPumpSession, TAG));
    }

    @Override
    protected BolusWizardSensitivityResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException, UnexpectedMessageException {
        return new BolusWizardSensitivityResponseMessage(mPumpSession, payload);
    }
}

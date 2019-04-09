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

public class BolusWizardCarbRatiosRequestMessage extends MedtronicSendMessageRequestMessage<BolusWizardCarbRatiosResponseMessage> {
    private static final String TAG = BolusWizardCarbRatiosRequestMessage.class.getSimpleName();

    public BolusWizardCarbRatiosRequestMessage(MedtronicCnlSession pumpSession) throws EncryptionException, ChecksumException {
        super(MessageType.READ_BOLUS_WIZARD_CARB_RATIOS, pumpSession, null);
    }

    public BolusWizardCarbRatiosResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {
        sendToPump(mDevice, TAG);
        return getResponse(readFromPump(mDevice, mPumpSession, TAG));
    }

    @Override
    protected BolusWizardCarbRatiosResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException, UnexpectedMessageException {
        return new BolusWizardCarbRatiosResponseMessage(mPumpSession, payload);
    }
}

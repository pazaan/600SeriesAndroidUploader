package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by lgoedhart on 26/03/2016.
 */

public class PumpBasalPatternRequestMessage extends MedtronicSendMessageRequestMessage<PumpBasalPatternResponseMessage> {
    private static final String TAG = PumpBasalPatternRequestMessage.class.getSimpleName();

    public PumpBasalPatternRequestMessage(MedtronicCnlSession pumpSession, byte patternNumber) throws EncryptionException, ChecksumException {
        super(MessageType.READ_BASAL_PATTERN, pumpSession, buildPayload(patternNumber));
    }

    protected static byte[] buildPayload(byte patternNumber) {
        return new byte[]{patternNumber};
    }

    public PumpBasalPatternResponseMessage send(UsbHidDriver mDevice, int millis) throws IOException, TimeoutException, ChecksumException, EncryptionException, UnexpectedMessageException {
        sendToPump(mDevice, TAG);
        return getResponse(readFromPump(mDevice, mPumpSession, TAG));
    }

    @Override
    protected PumpBasalPatternResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException, UnexpectedMessageException {
        return new PumpBasalPatternResponseMessage(mPumpSession, payload);
    }
}

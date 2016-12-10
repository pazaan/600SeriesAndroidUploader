package info.nightscout.android.medtronic.message;

import java.io.IOException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class PumpTimeRequestMessage extends MedtronicRequestMessage {
    public PumpTimeRequestMessage(MedtronicCnlSession pumpSession) throws EncryptionException, ChecksumException {
        super(SendMessageType.TIME_REQUEST, pumpSession, null);
    }

    protected void sendMessage(UsbHidDriver mDevice) throws IOException {
        super.sendMessage(mDevice);
        mPumpSession.incrMedtronicSequenceNumber();
    }
}

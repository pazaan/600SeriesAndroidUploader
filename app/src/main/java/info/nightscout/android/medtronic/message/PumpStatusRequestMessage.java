package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class PumpStatusRequestMessage extends MedtronicRequestMessage {
    public PumpStatusRequestMessage(MedtronicCnlSession pumpSession) throws EncryptionException, ChecksumException {
        super(SendMessageType.READ_PUMP_STATUS_REQUEST, pumpSession, null);
    }

    public PumpStatusResponseMessage send(UsbHidDriver mDevice) throws IOException, TimeoutException, ChecksumException, EncryptionException {
        sendMessage(mDevice);

        // Read the 0x81
        readMessage(mDevice);

        PumpStatusResponseMessage response = new PumpStatusResponseMessage(mPumpSession, readMessage(mDevice));

        return response;
    }
}

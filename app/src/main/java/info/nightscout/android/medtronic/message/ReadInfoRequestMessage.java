package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;

/**
 * Created by volker on 10.12.2016.
 */

public class ReadInfoRequestMessage extends ContourNextLinkBinaryRequestMessage {
    public ReadInfoRequestMessage(MedtronicCnlSession pumpSession) throws ChecksumException {
        super(ContourNextLinkBinaryRequestMessage.CommandType.READ_INFO, pumpSession, null);
    }

    public ReadInfoResponseMessage send(UsbHidDriver mDevice) throws IOException, TimeoutException, EncryptionException, ChecksumException {
        sendMessage(mDevice);

        ReadInfoResponseMessage response = new ReadInfoResponseMessage(mPumpSession, readMessage(mDevice));

        return response;
    }
}

package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;

/**
 * Created by volker on 10.12.2016.
 */

public class RequestLinkKeyRequestMessage extends ContourNextLinkBinaryRequestMessage {
    public RequestLinkKeyRequestMessage(MedtronicCnlSession pumpSession) throws ChecksumException {
        super(CommandType.REQUEST_LINK_KEY, pumpSession, null);
    }

    public RequestLinkKeyResponseMessage send(UsbHidDriver mDevice) throws IOException, TimeoutException, EncryptionException, ChecksumException {
        sendMessage(mDevice);

        RequestLinkKeyResponseMessage response = new RequestLinkKeyResponseMessage(mPumpSession, readMessage(mDevice));

        return response;
    }
}

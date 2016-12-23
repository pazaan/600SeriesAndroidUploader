package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;

/**
 * Created by volker on 10.12.2016.
 */

public class RequestLinkKeyRequestMessage extends ContourNextLinkBinaryRequestMessage<RequestLinkKeyResponseMessage> {
    public RequestLinkKeyRequestMessage(MedtronicCnlSession pumpSession) throws ChecksumException {
        super(CommandType.REQUEST_LINK_KEY, pumpSession, null);
    }

    @Override
    protected RequestLinkKeyResponseMessage getResponse(byte[] payload) throws ChecksumException, EncryptionException {
        return new RequestLinkKeyResponseMessage(mPumpSession, payload);
    }
}

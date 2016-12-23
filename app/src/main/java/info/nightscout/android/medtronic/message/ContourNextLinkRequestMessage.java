package info.nightscout.android.medtronic.message;


import java.io.IOException;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

/**
 * Created by volker on 12.12.2016.
 */

public abstract class ContourNextLinkRequestMessage<T> extends ContourNextLinkMessage {
    protected ContourNextLinkRequestMessage(byte[] bytes) {
        super(bytes);
    }

    public T send(UsbHidDriver mDevice) throws IOException, TimeoutException, EncryptionException, ChecksumException, UnexpectedMessageException {
        return send(mDevice, 0);
    }

    public T send(UsbHidDriver mDevice, int millis) throws UnexpectedMessageException, EncryptionException, TimeoutException, ChecksumException, IOException {
        sendMessage(mDevice);
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
            }
        }

        T response = this.getResponse(readMessage(mDevice)); //new ContourNextLinkCommandResponse();

        // FIXME - We need to care what the response message is - wrong MAC and all that
        return response;
    }

    protected abstract <T> T getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException, UnexpectedMessageException, TimeoutException;

}

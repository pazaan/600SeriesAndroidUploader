package info.nightscout.android.medtronic.message;


import android.util.Log;

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
    private static final String TAG = ContourNextLinkRequestMessage.class.getSimpleName();

    protected ContourNextLinkRequestMessage(byte[] bytes) {
        super(bytes);
    }

    public T sendNoResponse(UsbHidDriver mDevice) throws IOException, TimeoutException, EncryptionException, ChecksumException, UnexpectedMessageException {
        sendMessage(mDevice);
        return null;
    }

    public T send(UsbHidDriver mDevice) throws IOException, TimeoutException, EncryptionException, ChecksumException, UnexpectedMessageException {
        return send(mDevice, 0);
    }

    public T send(UsbHidDriver mDevice, int millis) throws UnexpectedMessageException, EncryptionException, TimeoutException, ChecksumException, IOException {

        sendMessage(mDevice);
        if (millis > 0) {
            try {
                Log.d(TAG, "waiting " + millis +" ms");
                Thread.sleep(millis);
            } catch (InterruptedException ignored) {
            }
        }

        // FIXME - We need to care what the response message is - wrong MAC and all that
        return this.getResponse(readMessage(mDevice));
    }

    public T send(UsbHidDriver mDevice, int millis, int timeout) throws UnexpectedMessageException, EncryptionException, TimeoutException, ChecksumException, IOException {

        sendMessage(mDevice);
        if (millis > 0) {
            try {
                Log.d(TAG, "waiting " + millis +" ms");
                Thread.sleep(millis);
            } catch (InterruptedException ignored) {
            }
        }

        return this.getResponse(readMessage(mDevice, timeout));
    }

    protected abstract <T> T getResponse(byte[] payload) throws ChecksumException, EncryptionException, IOException, UnexpectedMessageException, TimeoutException;

}

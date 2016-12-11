package info.nightscout.android.medtronic.message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.USB.UsbHidDriver;
import info.nightscout.android.medtronic.MedtronicCnlSession;

/**
 * Created by lgoedhart on 26/03/2016.
 */
public class ContourNextLinkResponseMessage extends ContourNextLinkMessage {

    public ContourNextLinkResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws ChecksumException {
        super(pumpSession, payload);
        mPumpSession = pumpSession;

    }

}

package info.nightscout.android.medtronic.message;


import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.message.ContourNextLinkMessage;
import info.nightscout.android.medtronic.message.UnexpectedMessageException;

/**
 * Created by volker on 12.12.2016.
 */

public class ContourNextLinkRequestMessage extends ContourNextLinkMessage {
    protected ContourNextLinkRequestMessage(byte[] bytes) {
        super(bytes);
    }
}

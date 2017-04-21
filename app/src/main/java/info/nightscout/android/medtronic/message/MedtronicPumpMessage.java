package info.nightscout.android.medtronic.message;

import info.nightscout.android.medtronic.MedtronicCnlSession;

/**
 * Created by volker on 15.12.2016.
 */

public class MedtronicPumpMessage extends ContourNextLinkMessage {

    protected MedtronicPumpMessage(MedtronicCnlSession pumpSession, byte[] bytes) {
        super(bytes);
    }
}

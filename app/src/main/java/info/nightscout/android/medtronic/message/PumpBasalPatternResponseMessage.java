package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import info.nightscout.android.BuildConfig;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.model.medtronicNg.PumpInfo;
import info.nightscout.android.utils.HexDump;

/**
 * Created by lgoedhart on 27/03/2016.
 */
public class PumpBasalPatternResponseMessage extends MedtronicSendMessageResponseMessage {
    private static final String TAG = PumpBasalPatternResponseMessage.class.getSimpleName();

    protected PumpBasalPatternResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException {
        super(pumpSession, payload);

        // TODO - determine message validity
        /*
        if (response.encode().length < (61 + 8)) {
            // Invalid message.
            // TODO - deal with this more elegantly
            Log.e(TAG, "Invalid message received for getBasalPatterns");
            return;
        }
        */


        byte bufferSize = (byte) (this.encode()[0x38] - 2); // TODO - getting the size should be part of the superclass.
        ByteBuffer basalBuffer = ByteBuffer.allocate(bufferSize);
        basalBuffer.order(ByteOrder.BIG_ENDIAN);
        basalBuffer.put(this.encode(), 0x39, bufferSize);

        if (BuildConfig.DEBUG) {
            String outputString = HexDump.dumpHexString(basalBuffer.array());
            Log.d(TAG, "BASAL PAYLOAD: " + outputString);
        }
    }

    public void updateBasalPatterns(PumpInfo pumpInfo) {
    }
}

package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
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

        ByteBuffer basalRatesBuffer = ByteBuffer.allocate(payload.length);
        basalRatesBuffer.order(ByteOrder.BIG_ENDIAN);
        basalRatesBuffer.put(this.encode());

        String responseString = HexDump.dumpHexString(basalRatesBuffer.array());
        Log.d(TAG, "PumpStatus: " + responseString);

    }

}

package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import info.nightscout.android.BuildConfig;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.model.medtronicNg.PumpInfo;
import info.nightscout.android.utils.HexDump;

import static info.nightscout.android.utils.ToolKit.getByteIU;
import static info.nightscout.android.utils.ToolKit.getIntLU;
import static info.nightscout.android.utils.ToolKit.getShortIU;

/**
 * Created by lgoedhart on 27/03/2016.
 */
public class PumpBasalPatternResponseMessage extends MedtronicSendMessageResponseMessage {
    private static final String TAG = PumpBasalPatternResponseMessage.class.getSimpleName();

    protected PumpBasalPatternResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException, UnexpectedMessageException {
        super(pumpSession, payload);

        if (!MedtronicSendMessageRequestMessage.MessageType.READ_BASAL_PATTERN.response(getShortIU(payload, 0x01))) {
            Log.e(TAG, "Invalid message received for PumpBasalPattern");
            throw new UnexpectedMessageException("Invalid message received for PumpBasalPattern");
        }

        int pattern = getByteIU(payload, 0x03);
        int items = getByteIU(payload, 0x04);
        double rate;
        int time;

        Log.d(TAG, "Pattern: " + pattern + " Items: " + items);

        int offset = 5;
        for (int i = 0; i < items; i++) {
            rate = getIntLU(payload, offset + 0x00) / 10000.0;
            time = getByteIU(payload, offset + 0x04) * 30;
            Log.d(TAG, "Item: " + (i + 1) + " Rate: " + rate + " Time: " + time / 60 + "h" + time % 60 + "m");
            offset += 5;
        }

    }

    public void updateBasalPatterns(PumpInfo pumpInfo) {
    }
}

/*

Basal Patterns
Value 	Meaning
1 	Pattern 1
2 	Pattern 2
3 	Pattern 3
4 	Pattern 4
5 	Pattern 5
6 	Work Day
7 	Day Off
8 	Sick Day

*/
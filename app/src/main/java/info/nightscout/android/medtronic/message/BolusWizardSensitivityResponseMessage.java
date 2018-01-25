package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.util.Arrays;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

import static info.nightscout.android.utils.ToolKit.read8toUInt;
import static info.nightscout.android.utils.ToolKit.read16BEtoUInt;

/**
 * Created by Pogman on 8.11.17.
 */

public class BolusWizardSensitivityResponseMessage extends MedtronicSendMessageResponseMessage {
    private static final String TAG = BolusWizardSensitivityResponseMessage.class.getSimpleName();

    private byte[] sensitivity; // [8bit] count, { [32bitBE] isf_mgdl, [32bitBE] isf_mmol, [8bit] time period (mult 30 min) }

    protected BolusWizardSensitivityResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException, UnexpectedMessageException {
        super(pumpSession, payload);

        if (!MedtronicSendMessageRequestMessage.MessageType.READ_BOLUS_WIZARD_SENSITIVITY_FACTORS.response(read16BEtoUInt(payload, 0x01))) {
            Log.e(TAG, "Invalid message received for BolusWizardSensitivity");
            throw new UnexpectedMessageException("Invalid message received for BolusWizardSensitivity");
        }

        sensitivity = Arrays.copyOfRange(payload, 5, payload.length);
    }

    public byte[] getSensitivity() {
        return sensitivity;
    }

    public void logcat() {
        int index = 0;
        int isf_mgdl;
        double isf_mmol;
        int time;

        int items = read8toUInt(sensitivity, index++);
        Log.d(TAG, "Targets: Items: " + items);

        for (int i = 0; i < items; i++) {
            isf_mgdl = read16BEtoUInt(sensitivity, index);
            isf_mmol = read16BEtoUInt(sensitivity, index + 0x02) / 10.0;
            time = read8toUInt(sensitivity, index + 0x04) * 30;
            Log.d(TAG, "TimePeriod: " + (i + 1) + " isf_mgdl: " + isf_mgdl + " isf_mmol: " + isf_mmol + " Time: " + time / 60 + "h" + time % 60 + "m");
            index += 5;
        }
    }
}
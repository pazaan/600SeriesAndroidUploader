package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.util.Arrays;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

import static info.nightscout.android.utils.ToolKit.getByteIU;
import static info.nightscout.android.utils.ToolKit.getInt;
import static info.nightscout.android.utils.ToolKit.getShortIU;

/**
 * Created by John on 8.11.17.
 */

public class BolusWizardCarbRatiosResponseMessage extends MedtronicSendMessageResponseMessage {
    private static final String TAG = BolusWizardCarbRatiosResponseMessage.class.getSimpleName();

    private byte[] carbRatios; // [8bit] count, { [32bitBE] rate1, [32bitBE] rate2, [8bit] time period (mult 30 min) }

    protected BolusWizardCarbRatiosResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException, UnexpectedMessageException {
        super(pumpSession, payload);

        if (!MedtronicSendMessageRequestMessage.MessageType.READ_BOLUS_WIZARD_CARB_RATIOS.response(getShortIU(payload, 0x01))) {
            Log.e(TAG, "Invalid message received for BolusWizardCarbRatios");
            throw new UnexpectedMessageException("Invalid message received for BolusWizardCarbRatios");
        }

        carbRatios = Arrays.copyOfRange(payload, 5, payload.length - 2);
    }

    public byte[] getCarbRatios() {
        return carbRatios;
    }

    public void logcat() {
        int index = 0;
        double rate1;
        double rate2;
        int time;

        int items = getByteIU(carbRatios, index++);
        Log.d(TAG, "Carb Ratios: Items: " + items);

        for (int i = 0; i < items; i++) {
            rate1 = getInt(carbRatios, index + 0x00) / 10.0;
            rate2 = getInt(carbRatios, index + 0x04) / 1.0;
            time = getByteIU(carbRatios, index + 0x08) * 30;
            Log.d(TAG, "TimePeriod: " + (i + 1) + " Rate1: " + rate1 + " Rate2: " + rate2 + " Time: " + time / 60 + "h" + time % 60 + "m");
            index += 9;
        }
    }
}
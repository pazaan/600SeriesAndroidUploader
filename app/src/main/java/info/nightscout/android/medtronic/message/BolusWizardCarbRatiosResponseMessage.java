package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.util.Arrays;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;

import static info.nightscout.android.utils.ToolKit.read8toUInt;
import static info.nightscout.android.utils.ToolKit.read32BEtoInt;
import static info.nightscout.android.utils.ToolKit.read16BEtoUInt;

/**
 * Created by Pogman on 8.11.17.
 */

public class BolusWizardCarbRatiosResponseMessage extends MedtronicSendMessageResponseMessage {
    private static final String TAG = BolusWizardCarbRatiosResponseMessage.class.getSimpleName();

    private byte[] carbRatios; // [8bit] count, { [32bitBE] rate1, [32bitBE] rate2, [8bit] time period (mult 30 min) }

    protected BolusWizardCarbRatiosResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException, UnexpectedMessageException {
        super(pumpSession, payload);

        if (!MedtronicSendMessageRequestMessage.MessageType.READ_BOLUS_WIZARD_CARB_RATIOS.response(read16BEtoUInt(payload, 0x01))) {
            Log.e(TAG, "Invalid message received for BolusWizardCarbRatios");
            throw new UnexpectedMessageException("Invalid message received for BolusWizardCarbRatios");
        }

        carbRatios = Arrays.copyOfRange(payload, 5, payload.length);
    }

    public byte[] getCarbRatios() {
        return carbRatios;
    }

    public void logcat() {
        int index = 0;
        double rate1;
        double rate2;
        double carb2;
        int time;

        int items = read8toUInt(carbRatios, index++);
        Log.d(TAG, "Carb Ratios: Items: " + items);

        for (int i = 0; i < items; i++) {
            rate1 = read32BEtoInt(carbRatios, index) / 10.0; // Grams per One Unit Insulin
            rate2 = read32BEtoInt(carbRatios, index + 0x04) / 1000.0; // Units Insulin per One Exchange
            carb2 = 15 / rate2; // One Exchange = 15 Grams Carb
            time = read8toUInt(carbRatios, index + 0x08) * 30;
            Log.d(TAG, "TimePeriod: " + (i + 1) + " Rate1: " + rate1 + " Rate2: " + rate2 + " (as carb = " + carb2 + ") Time: " + time / 60 + "h" + time % 60 + "m");
            index += 9;
        }
    }
}
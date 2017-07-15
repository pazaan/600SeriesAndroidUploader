package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;

import info.nightscout.android.BuildConfig;
import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.utils.DataStore;
import info.nightscout.android.utils.HexDump;

/**
 * Created by lgoedhart on 27/03/2016.
 */
public class PumpStatusResponseMessage extends MedtronicSendMessageResponseMessage {
    private static final String TAG = PumpStatusResponseMessage.class.getSimpleName();

    // Data from the Medtronic Pump Status message
    private boolean suspended;
    private boolean bolusing;
    private boolean deliveringInsulin;
    private boolean tempBasalActive;
    private boolean cgmActive;
    private byte activeBasalPattern;
    private float basalRate;
    private float tempBasalRate;
    private byte tempBasalPercentage;
    private short tempBasalMinutesRemaining;
    private float basalUnitsDeliveredToday;
    private short batteryPercentage;
    private float reservoirAmount;
    private short minutesOfInsulinRemaining; // 25h == "more than 1 day"
    private float activeInsulin;
    private int sgv;
    private Date sgvDate;
    private boolean lowSuspendActive;
    private PumpStatusEvent.CGM_TREND cgmTrend;

    private boolean recentBolusWizard; // Whether a bolus wizard has been run recently
    private int bolusWizardBGL; // in mg/dL. 0 means no recent bolus wizard reading.

    protected PumpStatusResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException, UnexpectedMessageException {
        super(pumpSession, payload);

        if (this.encode().length < (57 + 96)) {
            // Invalid message. Don't try and parse it
            // TODO - deal with this more elegantly
            Log.e(TAG, "Invalid message received for updatePumpStatus");
            throw new UnexpectedMessageException("Invalid message received for updatePumpStatus");
        }

        byte bufferSize = (byte) (this.encode()[0x38] - 2); // TODO - getting the size should be part of the superclass.
        ByteBuffer statusBuffer = ByteBuffer.allocate(bufferSize);
        statusBuffer.order(ByteOrder.BIG_ENDIAN);
        statusBuffer.put(this.encode(), 0x39, bufferSize);

        if (BuildConfig.DEBUG) {
            String outputString = HexDump.dumpHexString(statusBuffer.array());
            Log.d(TAG, "PAYLOAD: " + outputString);
        }
        // Status Flags
        suspended = (statusBuffer.get(0x03) & 0x01) != 0x00;
        bolusing = (statusBuffer.get(0x03) & 0x02) != 0x00;
        deliveringInsulin = (statusBuffer.get(0x03) & 0x10) != 0x00;
        tempBasalActive = (statusBuffer.get(0x03) & 0x20) != 0x00;
        cgmActive = (statusBuffer.get(0x03) & 0x40) != 0x00;

        // Active basal pattern
        activeBasalPattern = statusBuffer.get(0x1a);

        // Normal basal rate
        long rawNormalBasal = statusBuffer.getInt(0x1b);
        basalRate = new BigDecimal(rawNormalBasal / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // Temp basal rate
        long rawTempBasal = statusBuffer.getShort(0x21) & 0x0000ffff;
        tempBasalRate = new BigDecimal(rawTempBasal / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // Temp basal percentage
        tempBasalPercentage = statusBuffer.get(0x23);

        // Temp basal minutes remaining
        tempBasalMinutesRemaining = (short) (statusBuffer.getShort(0x24) & 0x0000ffff);

        // Units of insulin delivered as basal today
        // TODO - is this basal? Do we have a total Units delivered elsewhere?
        basalUnitsDeliveredToday = statusBuffer.getInt(0x26);

        // Pump battery percentage
        batteryPercentage = statusBuffer.get(0x2a);

        // Reservoir amount
        long rawReservoirAmount = statusBuffer.getInt(0x2b);
        reservoirAmount = new BigDecimal(rawReservoirAmount / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // Amount of insulin left in pump (in minutes)
        byte insulinHours = statusBuffer.get(0x2f);
        byte insulinMinutes = statusBuffer.get(0x30);
        minutesOfInsulinRemaining = (short) ((insulinHours * 60) + insulinMinutes);

        // Active insulin
        long rawActiveInsulin = statusBuffer.getInt(0x31);
        activeInsulin = new BigDecimal(rawActiveInsulin / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // CGM SGV
        sgv = (statusBuffer.getShort(0x35) & 0x0000ffff); // In mg/DL. 0 means no CGM reading
        long rtc;
        long offset;
        if ((sgv & 0x200) == 0x200) {
            // Sensor error. Let's reset. FIXME - solve this more elegantly later
            sgv = 0;
            rtc = 0;
            offset = 0;
            cgmTrend = PumpStatusEvent.CGM_TREND.NOT_SET;
        } else {
            rtc = statusBuffer.getInt(0x37) & 0x00000000ffffffffL;
            offset = statusBuffer.getInt(0x3b);
            cgmTrend = PumpStatusEvent.CGM_TREND.fromMessageByte(statusBuffer.get(0x40));
        }

        // SGV Date
        sgvDate = MessageUtils.decodeDateTime(rtc, offset);
        Log.d(TAG, "original sgv date: " + sgvDate);

        // Predictive low suspend
        // TODO - there is more status info in this byte other than just a boolean yes/no
        lowSuspendActive = statusBuffer.get(0x3f) != 0;

        // Recent Bolus Wizard BGL
        recentBolusWizard = statusBuffer.get(0x48) != 0;
        bolusWizardBGL = statusBuffer.getShort(0x49) & 0x0000ffff; // In mg/DL
    }

    /**
     * update pumpRecord with data read from pump
     *
     * @param pumpRecord
     */
    public void updatePumpRecord(PumpStatusEvent pumpRecord) {
        // Status Flags
        pumpRecord.setSuspended(suspended);
        pumpRecord.setBolusing(bolusing);
        pumpRecord.setDeliveringInsulin(deliveringInsulin);
        pumpRecord.setTempBasalActive(tempBasalActive);
        pumpRecord.setCgmActive(cgmActive);

        // Active basal pattern
        pumpRecord.setActiveBasalPattern(activeBasalPattern);

        // Normal basal rate
        pumpRecord.setBasalRate(basalRate);

        // Temp basal rate
        pumpRecord.setTempBasalRate(tempBasalRate);

        // Temp basal percentage
        pumpRecord.setTempBasalPercentage(tempBasalPercentage);

        // Temp basal minutes remaining
        pumpRecord.setTempBasalMinutesRemaining(tempBasalMinutesRemaining);

        // Units of insulin delivered as basal today
        pumpRecord.setBasalUnitsDeliveredToday(basalUnitsDeliveredToday);

        // Pump battery percentage
        pumpRecord.setBatteryPercentage(batteryPercentage);

        // Reservoir amount
        pumpRecord.setReservoirAmount(reservoirAmount);

        // Amount of insulin left in pump (in minutes)
        pumpRecord.setMinutesOfInsulinRemaining(minutesOfInsulinRemaining);

        // Active insulin
        pumpRecord.setActiveInsulin(activeInsulin);

        // CGM SGV data
        pumpRecord.setSgv(sgv);
        pumpRecord.setSgvDate(new Date(sgvDate.getTime() - pumpRecord.getPumpTimeOffset()));
        pumpRecord.setCgmTrend(cgmTrend);

        // Predictive low suspend
        // TODO - there is more status info in this byte other than just a boolean yes/no
        pumpRecord.setLowSuspendActive(lowSuspendActive);

        // Recent Bolus Wizard BGL
        if (bolusWizardBGL > 0
                && (DataStore.getInstance().getLastPumpStatus().getSgvDate().getTime() - System.currentTimeMillis() > 15 * 60 * 1000
                || (DataStore.getInstance().getLastBolusWizardBGL() != bolusWizardBGL
                && DataStore.getInstance().getLastPumpStatus().getBolusWizardBGL() != bolusWizardBGL)
                )
        ) {
            pumpRecord.setRecentBolusWizard(true);
            pumpRecord.setBolusWizardBGL(bolusWizardBGL); // In mg/DL
        } else {
            pumpRecord.setRecentBolusWizard(false);
            pumpRecord.setBolusWizardBGL(0); // In mg/DL
        }
        DataStore.getInstance().setLastBolusWizardBGL(bolusWizardBGL);
    }
}

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
import info.nightscout.android.utils.HexDump;

/**
 * Created by lgoedhart on 27/03/2016.
 */
public class PumpStatusResponseMessage extends MedtronicSendMessageResponseMessage {
    private static final String TAG = PumpStatusResponseMessage.class.getSimpleName();

    // Data from the Medtronic Pump Status message

    private byte pumpStatus;
    private byte cgmStatus;
    private boolean suspended;
    private boolean bolusingNormal;
    private boolean bolusingSquare;
    private boolean bolusingDual;
    private boolean deliveringInsulin;
    private boolean tempBasalActive;
    private boolean cgmActive;
    private boolean cgmCalibrating;
    private boolean cgmCalibrationComplete;
    private boolean cgmException;
    private boolean cgmWarmUp;
    private byte activeBasalPattern;
    private float basalRate;
    private float tempBasalRate;
    private short tempBasalPercentage;
    private short tempBasalMinutesRemaining;
    private float basalUnitsDeliveredToday;
    private short batteryPercentage;
    private float reservoirAmount;
    private short minutesOfInsulinRemaining; // 25h == "more than 1 day"
    private float activeInsulin;
    private int sgv;
    private Date cgmDate;
    private byte cgmExceptionType;
    private boolean lowSuspendActive;
    private PumpStatusEvent.CGM_TREND cgmTrend;
    private boolean recentBolusWizard; // Whether a bolus wizard has been run recently
    private int recentBGL; // in mg/dL. 0 means no recent finger bg reading.
    private short alert;
    private Date alertDate;
    private float bolusingDelivered;
    private short bolusingMinutesRemaining;
    private short bolusingReference;
    private float lastBolusAmount;
    private Date lastBolusDate;
    private short lastBolusReference;
    private byte transmitterBattery;
    private byte transmitterControl;
    private short calibrationDueMinutes;
    private float sensorRateOfChange;

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
        pumpStatus = statusBuffer.get(0x03);
        cgmStatus = statusBuffer.get(0x41);

        suspended = (pumpStatus & 0x01) != 0x00;
        bolusingNormal = (pumpStatus & 0x02) != 0x00;
        bolusingSquare = (pumpStatus & 0x04) != 0x00;
        bolusingDual = (pumpStatus & 0x08) != 0x00;
        deliveringInsulin = (pumpStatus & 0x10) != 0x00;
        tempBasalActive = (pumpStatus & 0x20) != 0x00;
        cgmActive = (pumpStatus & 0x40) != 0x00;
        cgmCalibrating = (cgmStatus & 0x01) != 0x00;
        cgmCalibrationComplete = (cgmStatus & 0x02) != 0x00;
        cgmException = (cgmStatus & 0x04) != 0x00;

        // Active basal pattern
        activeBasalPattern = statusBuffer.get(0x1A);

        // Normal basal rate
        long rawNormalBasal = statusBuffer.getInt(0x1B);
        basalRate = new BigDecimal(rawNormalBasal / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // Temp basal rate
        long rawTempBasal = statusBuffer.getInt(0x1F) & 0x0000000000FFFFFFL;
        tempBasalRate = new BigDecimal(rawTempBasal / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // Temp basal percentage
        tempBasalPercentage =  (short) (statusBuffer.get(0x23) & 0x00FF);

        // Temp basal minutes remaining
        tempBasalMinutesRemaining = (short) (statusBuffer.getShort(0x24) & 0x00FF);

        // Units of insulin delivered as basal today
        long rawBasalUnitsDeliveredToday = statusBuffer.getInt(0x26) & 0x0000000000FFFFFFL;
        basalUnitsDeliveredToday = new BigDecimal(rawBasalUnitsDeliveredToday / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // Pump battery percentage
        batteryPercentage = statusBuffer.get(0x2A);

        // Reservoir amount
        long rawReservoirAmount = statusBuffer.getInt(0x2B) & 0x0000000000FFFFFFL;
        reservoirAmount = new BigDecimal(rawReservoirAmount / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // Amount of insulin left in pump (in minutes)
        byte insulinHours = statusBuffer.get(0x2F);
        byte insulinMinutes = statusBuffer.get(0x30);
        minutesOfInsulinRemaining = (short) ((insulinHours * 60) + insulinMinutes);

        // Active insulin
        long rawActiveInsulin = statusBuffer.getInt(0x31) & 0x0000000000FFFFFFL;
        activeInsulin = new BigDecimal(rawActiveInsulin / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // CGM SGV
        sgv = (statusBuffer.getShort(0x35) & 0x0000FFFF); // In mg/DL. 0x0000 = no CGM reading, 0x03NN = sensor exception
        cgmDate = MessageUtils.decodeDateTime((long) statusBuffer.getInt(0x37) & 0x00000000FFFFFFFFL, (long) statusBuffer.getInt(0x3B));
        Log.d(TAG, "original cgm/sgv date: " + cgmDate);

        if (cgmException) {
            cgmExceptionType = (byte) (sgv & 0x00FF);
            cgmTrend = PumpStatusEvent.CGM_TREND.NOT_SET;
            if (cgmExceptionType == 0x01) cgmWarmUp = true;
            sgv = 0;
        } else {
            cgmExceptionType = 0;
            cgmTrend = PumpStatusEvent.CGM_TREND.fromMessageByte((byte) (statusBuffer.get(0x40) & 0xF0)); // masked as low nibble can contain value when transmitter battery low
            cgmWarmUp = false;
        }

        // Predictive low suspend
        // TODO - there is more status info in this byte other than just a boolean yes/no
        // noted: 0x01=high 0x04=before high 0x08=before low 0x0A=low 0x80=suspend 0x92=suspend low
        lowSuspendActive = statusBuffer.get(0x3F) != 0;

        // Recent Bolus Wizard
        recentBolusWizard = statusBuffer.get(0x48) != 0;

        // Recent BGL
        recentBGL = statusBuffer.getShort(0x49) & 0x0000FFFF; // In mg/DL

        // Active alert
        alert = statusBuffer.getShort(0x4B);
        alertDate = MessageUtils.decodeDateTime((long) statusBuffer.getInt(0x4D) & 0x00000000FFFFFFFFL, (long) statusBuffer.getInt(0x51));

        // Now bolusing
        long rawBolusingDelivered = statusBuffer.getInt(0x04) & 0x0000000000FFFFFFL;
        bolusingDelivered = new BigDecimal(rawBolusingDelivered / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();
        bolusingMinutesRemaining = statusBuffer.getShort(0x0C);
        bolusingReference = statusBuffer.getShort(0x0E);

        // Last bolus
        long rawLastBolusAmount = statusBuffer.getInt(0x10) & 0x0000000000FFFFFFL;
        lastBolusAmount = new BigDecimal(rawLastBolusAmount / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();
        lastBolusDate = MessageUtils.decodeDateTime((long) statusBuffer.getInt(0x14) & 0x00000000FFFFFFFFL, 0);
        lastBolusReference = statusBuffer.getShort(0x18);

        // Calibration
        calibrationDueMinutes = statusBuffer.getShort(0x43);

        // Transmitter
        transmitterControl = statusBuffer.get(0x43);
        transmitterBattery  = statusBuffer.get(0x45);
        // Normalise transmitter battery to percentage shown on pump sensor status screen
        if (transmitterBattery == 0x3F) transmitterBattery = 100;
        else if (transmitterBattery == 0x2B) transmitterBattery = 73;
        else if (transmitterBattery == 0x27) transmitterBattery = 47;
        else if (transmitterBattery == 0x23) transmitterBattery = 20;
        else if (transmitterBattery == 0x10) transmitterBattery = 0;
        else transmitterBattery = 0;

        // Sensor
        long rawSensorRateOfChange = statusBuffer.getShort(0x46);
        sensorRateOfChange = new BigDecimal(rawSensorRateOfChange / 100f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();
    }

    /**
     * update pumpRecord with data read from pump
     *
     * @param pumpRecord
     */
    public void updatePumpRecord(PumpStatusEvent pumpRecord) {

        // Status Flags
        pumpRecord.setPumpStatus(pumpStatus);
        pumpRecord.setCgmStatus(cgmStatus);

        pumpRecord.setSuspended(suspended);
        pumpRecord.setBolusingNormal(bolusingNormal);
        pumpRecord.setBolusingSquare(bolusingSquare);
        pumpRecord.setBolusingDual(bolusingDual);
        pumpRecord.setDeliveringInsulin(deliveringInsulin);
        pumpRecord.setTempBasalActive(tempBasalActive);
        pumpRecord.setCgmActive(cgmActive);

        pumpRecord.setCgmCalibrating(cgmCalibrating);
        pumpRecord.setCgmCalibrationComplete(cgmCalibrationComplete);
        pumpRecord.setCgmException(cgmException);
        pumpRecord.setCgmWarmUp(cgmWarmUp);

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
        pumpRecord.setCgmPumpDate(cgmDate);
        pumpRecord.setCgmDate(new Date(cgmDate.getTime() - pumpRecord.getPumpTimeOffset()));
        pumpRecord.setCgmTrend(cgmTrend);
        pumpRecord.setCgmExceptionType(cgmExceptionType);

        // Predictive low suspend
        // TODO - there is more status info in this byte other than just a boolean yes/no
        pumpRecord.setLowSuspendActive(lowSuspendActive);

        // Recent BGL
        pumpRecord.setRecentBGL(recentBGL); // In mg/DL

        // Active alert
        pumpRecord.setAlert(alert);
        pumpRecord.setAlertPumpDate(alertDate);
        pumpRecord.setAlertDate(new Date(alertDate.getTime() - pumpRecord.getPumpTimeOffset()));

        // Now bolusing
        pumpRecord.setBolusingDelivered(bolusingDelivered);
        pumpRecord.setBolusingMinutesRemaining(bolusingMinutesRemaining);
        pumpRecord.setBolusingReference(bolusingReference);

        // Last bolus
        pumpRecord.setLastBolusAmount(lastBolusAmount);
        pumpRecord.setLastBolusPumpDate(lastBolusDate);
        pumpRecord.setLastBolusDate(new Date(lastBolusDate.getTime() - pumpRecord.getPumpTimeOffset()));
        pumpRecord.setLastBolusReference(lastBolusReference);

        // Calibration
        pumpRecord.setCalibrationDueMinutes(calibrationDueMinutes);

        // Transmitter
        pumpRecord.setTransmitterBattery(transmitterBattery);
        pumpRecord.setTransmitterControl(transmitterControl);

        // Sensor
        pumpRecord.setSensorRateOfChange(sensorRateOfChange);
    }
}
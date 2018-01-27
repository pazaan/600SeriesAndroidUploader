package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.math.BigDecimal;
import java.util.Date;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.model.medtronicNg.PumpStatusEvent;

import static info.nightscout.android.utils.ToolKit.read32BEtoInt;
import static info.nightscout.android.utils.ToolKit.read32BEtoULong;
import static info.nightscout.android.utils.ToolKit.read16BEtoShort;
import static info.nightscout.android.utils.ToolKit.read16BEtoUInt;
import static info.nightscout.android.utils.ToolKit.read16BEtoULong;

/**
 * Created by lgoedhart on 27/03/2016.
 */
public class PumpStatusResponseMessage extends MedtronicSendMessageResponseMessage {
    private static final String TAG = PumpStatusResponseMessage.class.getSimpleName();

    // Data from the Medtronic Pump add message

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
    private byte activeTempBasalPattern;
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
    private int cgmRTC;
    private int cgmOFFSET;

    private byte cgmExceptionType;
    private boolean lowSuspendActive;
    private PumpStatusEvent.CGM_TREND cgmTrend;
    private boolean recentBolusWizard; // Whether a bolus wizard has been run recently
    private int recentBGL; // in mg/dL. 0 means no recent finger bg reading.
    private short alert;

    private Date alertDate;
    private int alertRTC;
    private int alertOFFSET;

    private float bolusingDelivered;
    private short bolusingMinutesRemaining;
    private byte bolusingReference;
    private float lastBolusAmount;
    private Date lastBolusDate;
    private byte lastBolusReference;
    private byte transmitterBattery;
    private byte transmitterControl;
    private short calibrationDueMinutes;
    private float sensorRateOfChange;

    private byte[] payload; // save the payload for data mining on the 670G

    protected PumpStatusResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException, UnexpectedMessageException {
        super(pumpSession, payload);

        if (!MedtronicSendMessageRequestMessage.MessageType.READ_PUMP_STATUS.response(read16BEtoUInt(payload, 0x01))) {
            Log.e(TAG, "Invalid message received for PumpTime");
            throw new UnexpectedMessageException("Invalid message received for PumpStatus");
        }

        this.payload = payload; // save the payload for data mining

        // add Flags
        pumpStatus = payload[0x03];
        cgmStatus = payload[0x41];

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
        byte pattern = payload[0x1A];
        activeBasalPattern = (byte) (pattern & 0x0F);
        activeTempBasalPattern = (byte) (pattern >> 4 & 0x0F);

        // Normal basal rate
        long rawNormalBasal = read32BEtoULong(payload, 0x1B);
        basalRate = new BigDecimal(rawNormalBasal / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // Temp basal rate
        long rawTempBasal = read32BEtoULong(payload, 0x1F);
        tempBasalRate = new BigDecimal(rawTempBasal / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // Temp basal percentage
        tempBasalPercentage =  (short) (payload[0x23] & 0x00FF);

        // Temp basal minutes remaining
        tempBasalMinutesRemaining = (short) (read16BEtoShort(payload, 0x24) &  0x00FF);

        // Units of insulin delivered as basal today
        long rawBasalUnitsDeliveredToday = read32BEtoULong(payload, 0x26);
        basalUnitsDeliveredToday = new BigDecimal(rawBasalUnitsDeliveredToday / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // Pump battery percentage
        batteryPercentage = (short) (payload[0x2A] & 0x00FF);

        // Reservoir amount
        long rawReservoirAmount = read32BEtoULong(payload, 0x2B);
        reservoirAmount = new BigDecimal(rawReservoirAmount / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // Amount of insulin left in pump (in minutes)
        byte insulinHours = payload[0x2F];
        byte insulinMinutes = payload[0x30];
        minutesOfInsulinRemaining = (short) ((insulinHours * 60) + insulinMinutes);

        // Active insulin
        long rawActiveInsulin = read32BEtoULong(payload, 0x31);
        activeInsulin = new BigDecimal(rawActiveInsulin / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // CGM time
        cgmRTC = read32BEtoInt(payload, 0x37);
        cgmOFFSET = read32BEtoInt(payload, 0x3B);
        cgmDate = MessageUtils.decodeDateTime(cgmRTC & 0xFFFFFFFFL, cgmOFFSET);
        Log.d(TAG, "original cgm/sgv date: " + cgmDate);

        // CGM SGV
        sgv = read16BEtoUInt(payload, 0x35); // In mg/DL. 0x0000 = no CGM reading, 0x03NN = sensor exception

        if (sgv >= 0x0300) {
            cgmExceptionType = (byte) (sgv & 0x00FF);
            cgmTrend = PumpStatusEvent.CGM_TREND.NOT_SET;
            if (cgmExceptionType == 0x01) cgmWarmUp = true;
            sgv = 0;
        } else {
            cgmExceptionType = 0;
            cgmTrend = PumpStatusEvent.CGM_TREND.fromMessageByte((byte) (payload[0x40] & 0xF0)); // masked as low nibble can contain value when transmitter battery low
            cgmWarmUp = false;
        }

        // Predictive low suspend
        // TODO - there is more status info in this byte other than just a boolean yes/no
        // noted: 0x01=high 0x04=before high 0x08=before low 0x0A=low 0x80=suspend 0x92=suspend low
        lowSuspendActive = payload[0x3F] != 0;

        // Recent Bolus Wizard
        recentBolusWizard = payload[0x48] != 0;

        // Recent BGL
        recentBGL = read16BEtoUInt(payload, 0x49); // In mg/DL

        // Active alert
        alert = read16BEtoShort(payload, 0x4B);
        alertRTC = read32BEtoInt(payload, 0x4D);
        alertOFFSET = read32BEtoInt(payload, 0x51);
        alertDate = MessageUtils.decodeDateTime(alertRTC & 0xFFFFFFFFL, alertOFFSET);

        // Now bolusing
        long rawBolusingDelivered = read32BEtoULong(payload, 0x04);
        bolusingDelivered = new BigDecimal(rawBolusingDelivered / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();
        bolusingMinutesRemaining = read16BEtoShort(payload, 0x0C);
        bolusingReference = payload[0x0E];

        // Last bolus
        long rawLastBolusAmount = read32BEtoULong(payload, 0x10);
        lastBolusAmount = new BigDecimal(rawLastBolusAmount / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();
        lastBolusDate = MessageUtils.decodeDateTime(read32BEtoULong(payload, 0x14), 0);
        lastBolusReference = payload[0x18];

        // Calibration
        calibrationDueMinutes = read16BEtoShort(payload, 0x43);

        // Transmitter
        transmitterControl = payload[0x43];
        transmitterBattery = payload[0x45];
        // Normalise transmitter battery to percentage shown on pump sensor status screen
        transmitterBattery = (byte) (((transmitterBattery & 0x0F) * 100) / 15);

        // Sensor
        long rawSensorRateOfChange = read16BEtoULong(payload, 0x46);
        sensorRateOfChange = new BigDecimal(rawSensorRateOfChange / 100f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();
    }

    /**
     * update pumpRecord with data read from pump
     *
     * @param pumpRecord
     */
    public void updatePumpRecord(PumpStatusEvent pumpRecord) {

        //pumpRecord.setPayload(payload);  // save the payload for data mining

        // add Flags
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

        // Active temp basal pattern
        pumpRecord.setActiveTempBasalPattern(activeTempBasalPattern);

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

        // CGM time
        pumpRecord.setCgmRTC(cgmRTC);
        pumpRecord.setCgmOFFSET(cgmOFFSET);
        //pumpRecord.setCgmDate(new Date(cgmDate.getTime() - pumpRecord.getClockDifference()));

        // Date using cgmRTC + eventOFFSET as pump clock may have changed
        Date cgmEventDate = MessageUtils.decodeDateTime(cgmRTC & 0xFFFFFFFFL, pumpRecord.getEventOFFSET());
        pumpRecord.setCgmDate(new Date(cgmEventDate.getTime() - pumpRecord.getClockDifference()));

        // CGM SGV data
        pumpRecord.setSgv(sgv);
        pumpRecord.setCgmTrend(cgmTrend);
        pumpRecord.setCgmExceptionType(cgmExceptionType);

        // Predictive low suspend
        // TODO - there is more status info in this byte other than just a boolean yes/no
        pumpRecord.setLowSuspendActive(lowSuspendActive);

        // Recent BGL
        pumpRecord.setRecentBGL(recentBGL); // In mg/DL

        // Active alert
        pumpRecord.setAlert(alert);
        pumpRecord.setAlertRTC(cgmRTC);
        pumpRecord.setAlertOFFSET(cgmOFFSET);
        //pumpRecord.setAlertDate(new Date(alertDate.getTime() - pumpRecord.getClockDifference()));

        // Date using alertRTC + eventOFFSET as pump clock may have changed
        Date alertEventDate = MessageUtils.decodeDateTime(alertRTC & 0xFFFFFFFFL, pumpRecord.getEventOFFSET());
        pumpRecord.setAlertDate(new Date(alertEventDate.getTime() - pumpRecord.getClockDifference()));

        // Now bolusing
        pumpRecord.setBolusingDelivered(bolusingDelivered);
        pumpRecord.setBolusingMinutesRemaining(bolusingMinutesRemaining);
        pumpRecord.setBolusingReference(bolusingReference);

        // Last bolus
        pumpRecord.setLastBolusAmount(lastBolusAmount);
        pumpRecord.setLastBolusReference(lastBolusReference);
        pumpRecord.setLastBolusPumpDate(lastBolusDate);
        pumpRecord.setLastBolusDate(new Date(lastBolusDate.getTime() - pumpRecord.getClockDifference()));

        // Calibration
        pumpRecord.setCalibrationDueMinutes(calibrationDueMinutes);

        // Transmitter
        pumpRecord.setTransmitterBattery(transmitterBattery);
        pumpRecord.setTransmitterControl(transmitterControl);

        // Sensor
        pumpRecord.setSensorRateOfChange(sensorRateOfChange);
    }
}
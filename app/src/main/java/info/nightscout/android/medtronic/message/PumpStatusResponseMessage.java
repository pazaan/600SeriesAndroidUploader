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

import static info.nightscout.android.utils.ToolKit.getInt;
import static info.nightscout.android.utils.ToolKit.getIntL;
import static info.nightscout.android.utils.ToolKit.getIntLU;
import static info.nightscout.android.utils.ToolKit.getShort;
import static info.nightscout.android.utils.ToolKit.getShortIU;
import static info.nightscout.android.utils.ToolKit.getShortLU;

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

    private int cgmRTC;
    private int cgmOFFSET;

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

        if (!MedtronicSendMessageRequestMessage.MessageType.READ_PUMP_STATUS.response(getShortIU(payload, 0x01))) {
            Log.e(TAG, "Invalid message received for PumpTime");
            throw new UnexpectedMessageException("Invalid message received for PumpStatus");
        }

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
        activeBasalPattern = payload[0x1A];

        // Normal basal rate
        long rawNormalBasal = getIntLU(payload, 0x1B);
        basalRate = new BigDecimal(rawNormalBasal / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // Temp basal rate
        long rawTempBasal = getIntLU(payload, 0x1F);
        tempBasalRate = new BigDecimal(rawTempBasal / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // Temp basal percentage
        tempBasalPercentage =  (short) (payload[0x23] & 0x00FF);

        // Temp basal minutes remaining
        tempBasalMinutesRemaining = (short) (getShort(payload, 0x24) &  0x00FF);

        // Units of insulin delivered as basal today
        long rawBasalUnitsDeliveredToday = getIntLU(payload, 0x26);
        basalUnitsDeliveredToday = new BigDecimal(rawBasalUnitsDeliveredToday / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // Pump battery percentage
        batteryPercentage = (short) (payload[0x2A] & 0x00FF);

        // Reservoir amount
        long rawReservoirAmount = getIntLU(payload, 0x2B);
        reservoirAmount = new BigDecimal(rawReservoirAmount / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // Amount of insulin left in pump (in minutes)
        byte insulinHours = payload[0x2F];
        byte insulinMinutes = payload[0x30];
        minutesOfInsulinRemaining = (short) ((insulinHours * 60) + insulinMinutes);

        // Active insulin
        long rawActiveInsulin = getIntLU(payload, 0x31);
        activeInsulin = new BigDecimal(rawActiveInsulin / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();

        // CGM time
        cgmRTC = getInt(payload, 0x37);
        cgmOFFSET = getInt(payload, 0x3B);

        // CGM SGV
        sgv = getShortIU(payload, 0x35); // In mg/DL. 0x0000 = no CGM reading, 0x03NN = sensor exception
        cgmDate = MessageUtils.decodeDateTime(getIntLU(payload, 0x37), getIntL(payload, 0x3B));
        Log.d(TAG, "original cgm/sgv date: " + cgmDate);

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

        Log.d(TAG, "RTC= " + getIntLU(payload, 0x37) + "  OFFSET= " + getIntLU(payload, 0x3B));
//        cgmRTC = statusBuffer.getInt(0x37);
//        cgmOFFSET = statusBuffer.getInt(0x3B);

        // Predictive low suspend
        // TODO - there is more status info in this byte other than just a boolean yes/no
        // noted: 0x01=high 0x04=before high 0x08=before low 0x0A=low 0x80=suspend 0x92=suspend low
        lowSuspendActive = payload[0x3F] != 0;

        // Recent Bolus Wizard
        recentBolusWizard = payload[0x48] != 0;

        // Recent BGL
        recentBGL = getShortIU(payload, 0x49); // In mg/DL

        // Active alert
        alert = getShort(payload, 0x4B);
        alertDate = MessageUtils.decodeDateTime(getIntLU(payload, 0x4D), getIntL(payload, 0x51));

        // Now bolusing
        long rawBolusingDelivered = getIntLU(payload, 0x04);
        bolusingDelivered = new BigDecimal(rawBolusingDelivered / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();
        bolusingMinutesRemaining = getShort(payload, 0x0C);
        bolusingReference = getShort(payload, 0x0E);

        // Last bolus
        long rawLastBolusAmount = getIntLU(payload, 0x10);
        lastBolusAmount = new BigDecimal(rawLastBolusAmount / 10000f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();
        lastBolusDate = MessageUtils.decodeDateTime(getIntLU(payload, 0x14), 0);
        lastBolusReference = getShort(payload, 0x18);

        // Calibration
        calibrationDueMinutes = getShort(payload, 0x43);

        // Transmitter
        transmitterControl = payload[0x43];
        transmitterBattery  = payload[0x45];
        // Normalise transmitter battery to percentage shown on pump sensor status screen
        if (transmitterBattery == 0x3F) transmitterBattery = 100;
        else if (transmitterBattery == 0x2B) transmitterBattery = 73;
        else if (transmitterBattery == 0x27) transmitterBattery = 47;
        else if (transmitterBattery == 0x23) transmitterBattery = 20;
        else if (transmitterBattery == 0x10) transmitterBattery = 0;
        else transmitterBattery = 0;

        // Sensor
        long rawSensorRateOfChange = getShortLU(payload, 0x46);
        sensorRateOfChange = new BigDecimal(rawSensorRateOfChange / 100f).setScale(3, BigDecimal.ROUND_HALF_UP).floatValue();
    }

    /**
     * update pumpRecord with data read from pump
     *
     * @param pumpRecord
     */
    public void updatePumpRecord(PumpStatusEvent pumpRecord) {

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
package info.nightscout.android.model.medtronicNg;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.Index;

/**
 * Created by lgoedhart on 4/06/2016.
 */
public class PumpStatusEvent extends RealmObject {
    @Index
    private Date eventDate; // The actual time (uploader) of the this event (pumptime event date)

    @Index
    private int eventRTC; // RTC of pumptime request (as there is no RTC for status message)
    private int eventOFFSET; // OFFSET of pumptime request (as there is no OFFSET for status message)
    private long clockDifference; // uploader-pump clock difference

    private String deviceName;

    private byte[] payload; // save the payload for data mining on the 670G

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
    private String cgmTrend;
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
    private Date lastBolusPumpDate;
    private short lastBolusDuration;
    private byte lastBolusReference;
    private byte transmitterBattery;
    private byte transmitterControl;
    private short calibrationDueMinutes;
    private float sensorRateOfChange;

    private boolean oldSgvWhenNewExpected = false;
    private boolean validSGV = false;

    @Index
    private boolean uploaded = false;

    public PumpStatusEvent() {
        // The the eventDate to now.
        this.eventDate = new Date();
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public void setEventDate(Date eventDate) {
        this.eventDate = eventDate;
    }

    public Date getEventDate() {
        return eventDate;
    }

    public int getEventRTC() {
        return eventRTC;
    }

    public void setEventRTC(int eventRTC) {
        this.eventRTC = eventRTC;
    }

    public int getEventOFFSET() {
        return eventOFFSET;
    }

    public void setEventOFFSET(int eventOFFSET) {
        this.eventOFFSET = eventOFFSET;
    }

    public long getClockDifference() {
        return clockDifference;
    }

    public void setClockDifference(long clockDifference) {
        this.clockDifference = clockDifference;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public int getCgmRTC() {
        return cgmRTC;
    }

    public void setCgmRTC(int cgmRTC) {
        this.cgmRTC = cgmRTC;
    }

    public int getCgmOFFSET() {
        return cgmOFFSET;
    }

    public void setCgmOFFSET(int cgmOFFSET) {
        this.cgmOFFSET = cgmOFFSET;
    }

    public int getSgv() {
        return sgv;
    }

    public void setSgv(int sgv) {
        this.sgv = sgv;
    }

    public CGM_TREND getCgmTrend() {
        if (cgmTrend == null || !this.isCgmActive()) {
            return CGM_TREND.NOT_SET;
        } else {
            return CGM_TREND.valueOf(cgmTrend);
        }
    }

    public void setCgmTrend(String cgmTrend) {
        this.cgmTrend = cgmTrend;
    }

    public String getCgmTrendString() {
        return cgmTrend;
    }

    public void setCgmTrend(CGM_TREND cgmTrend) {
        if (cgmTrend != null)
            this.cgmTrend = cgmTrend.name();
        else
            this.cgmTrend = CGM_TREND.NOT_SET.name();
    }

    public float getActiveInsulin() {
        return activeInsulin;
    }

    public void setActiveInsulin(float activeInsulin) {
        this.activeInsulin = activeInsulin;
    }

    public short getBatteryPercentage() {
        return batteryPercentage;
    }

    public void setBatteryPercentage(short batteryPercentage) {
        this.batteryPercentage = batteryPercentage;
    }

    public float getReservoirAmount() {
        return reservoirAmount;
    }

    public void setReservoirAmount(float reservoirAmount) {
        this.reservoirAmount = reservoirAmount;
    }

    public boolean hasRecentBolusWizard() {
        return recentBolusWizard;
    }

    public int getRecentBGL() {
        return recentBGL;
    }

    public void setRecentBGL(int recentBGL) {
        this.recentBGL = recentBGL;
    }




    public boolean isUploaded() {
        return uploaded;
    }

    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }


    public boolean isOldSgvWhenNewExpected() {
        return oldSgvWhenNewExpected;
    }

    public void setOldSgvWhenNewExpected(boolean oldSgvWhenNewExpected) {
        this.oldSgvWhenNewExpected = oldSgvWhenNewExpected;
    }

    public boolean isValidSGV() {
        return validSGV;
    }

    public void setValidSGV(boolean validSGV) {
        this.validSGV = validSGV;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    public boolean isBolusingNormal() {
        return bolusingNormal;
    }

    public void setBolusingNormal(boolean bolusingNormal) {
        this.bolusingNormal = bolusingNormal;
    }

    public boolean isBolusingSquare() {
        return bolusingSquare;
    }

    public void setBolusingSquare(boolean bolusingSquare) {
        this.bolusingSquare = bolusingSquare;
    }

    public boolean isBolusingDual() {
        return bolusingDual;
    }

    public void setBolusingDual(boolean bolusingDual) {
        this.bolusingDual = bolusingDual;
    }

    public boolean isDeliveringInsulin() {
        return deliveringInsulin;
    }

    public void setDeliveringInsulin(boolean deliveringInsulin) {
        this.deliveringInsulin = deliveringInsulin;
    }

    public boolean isTempBasalActive() {
        return tempBasalActive;
    }

    public void setTempBasalActive(boolean tempBasalActive) {
        this.tempBasalActive = tempBasalActive;
    }

    public boolean isCgmActive() {
        return cgmActive;
    }

    public void setCgmActive(boolean cgmActive) {
        this.cgmActive = cgmActive;
    }

    public byte getActiveBasalPattern() {
        return activeBasalPattern;
    }

    public void setActiveBasalPattern(byte activeBasalPattern) {
        this.activeBasalPattern = activeBasalPattern;
    }

    public byte getActiveTempBasalPattern() {
        return activeTempBasalPattern;
    }

    public void setActiveTempBasalPattern(byte activeTempBasalPattern) {
        this.activeTempBasalPattern = activeTempBasalPattern;
    }

    public float getBasalRate() {
        return basalRate;
    }

    public void setBasalRate(float basalRate) {
        this.basalRate = basalRate;
    }

    public float getTempBasalRate() {
        return tempBasalRate;
    }

    public void setTempBasalRate(float tempBasalRate) {
        this.tempBasalRate = tempBasalRate;
    }

    public short getTempBasalPercentage() {
        return tempBasalPercentage;
    }

    public void setTempBasalPercentage(short tempBasalPercentage) {
        this.tempBasalPercentage = tempBasalPercentage;
    }

    public short getTempBasalMinutesRemaining() {
        return tempBasalMinutesRemaining;
    }

    public void setTempBasalMinutesRemaining(short tempBasalMinutesRemaining) {
        this.tempBasalMinutesRemaining = tempBasalMinutesRemaining;
    }

    public float getBasalUnitsDeliveredToday() {
        return basalUnitsDeliveredToday;
    }

    public void setBasalUnitsDeliveredToday(float basalUnitsDeliveredToday) {
        this.basalUnitsDeliveredToday = basalUnitsDeliveredToday;
    }

    public short getMinutesOfInsulinRemaining() {
        return minutesOfInsulinRemaining;
    }

    public void setMinutesOfInsulinRemaining(short minutesOfInsulinRemaining) {
        this.minutesOfInsulinRemaining = minutesOfInsulinRemaining;
    }

    public Date getCgmDate() {
        return cgmDate;
    }

    public void setCgmDate(Date cgmDate) {
        this.cgmDate = cgmDate;
    }

    public boolean isLowSuspendActive() {
        return lowSuspendActive;
    }

    public void setLowSuspendActive(boolean lowSuspendActive) {
        this.lowSuspendActive = lowSuspendActive;
    }

    public byte getPumpStatus() {
        return pumpStatus;
    }

    public void setPumpStatus(byte pumpStatus) {
        this.pumpStatus = pumpStatus;
    }

    public byte getCgmStatus() {
        return cgmStatus;
    }

    public void setCgmStatus(byte cgmStatus) {
        this.cgmStatus = cgmStatus;
    }

    public boolean isCgmCalibrating() {
        return cgmCalibrating;
    }

    public void setCgmCalibrating(boolean cgmCalibrating) {
        this.cgmCalibrating = cgmCalibrating;
    }

    public boolean isCgmCalibrationComplete() {
        return cgmCalibrationComplete;
    }

    public void setCgmCalibrationComplete(boolean cgmCalibrationComplete) {
        this.cgmCalibrationComplete = cgmCalibrationComplete;
    }

    public boolean isCgmException() {
        return cgmException;
    }

    public void setCgmException(boolean cgmException) {
        this.cgmException = cgmException;
    }

    public boolean isCgmWarmUp() {
        return cgmWarmUp;
    }

    public void setCgmWarmUp(boolean cgmWarmUp) {
        this.cgmWarmUp = cgmWarmUp;
    }

    public byte getCgmExceptionType() {
        return cgmExceptionType;
    }

    public void setCgmExceptionType(byte cgmExceptionType) {
        this.cgmExceptionType = cgmExceptionType;
    }

    public short getAlert() {
        return alert;
    }

    public void setAlert(short alert) {
        this.alert = alert;
    }

    public int getAlertRTC() {
        return alertRTC;
    }

    public void setAlertRTC(int alertRTC) {
        this.alertRTC = alertRTC;
    }

    public int getAlertOFFSET() {
        return alertOFFSET;
    }

    public void setAlertOFFSET(int alertOFFSET) {
        this.alertOFFSET = alertOFFSET;
    }

    public Date getAlertDate() {
        return alertDate;
    }

    public void setAlertDate(Date alertDate) {
        this.alertDate = alertDate;
    }

    public float getBolusingDelivered() {
        return bolusingDelivered;
    }

    public void setBolusingDelivered(float bolusingDelivered) {
        this.bolusingDelivered = bolusingDelivered;
    }

    public short getBolusingMinutesRemaining() {
        return bolusingMinutesRemaining;
    }

    public void setBolusingMinutesRemaining(short bolusingMinutesRemaining) {
        this.bolusingMinutesRemaining = bolusingMinutesRemaining;
    }

    public byte getBolusingReference() {
        return bolusingReference;
    }

    public void setBolusingReference(byte bolusingReference) {
        this.bolusingReference = bolusingReference;
    }

    public float getLastBolusAmount() {
        return lastBolusAmount;
    }

    public void setLastBolusAmount(float lastBolusAmount) {
        this.lastBolusAmount = lastBolusAmount;
    }

    public Date getLastBolusDate() {
        return lastBolusDate;
    }

    public void setLastBolusDate(Date lastBolusDate) {
        this.lastBolusDate = lastBolusDate;
    }

    public Date getLastBolusPumpDate() {
        return lastBolusPumpDate;
    }

    public void setLastBolusPumpDate(Date lastBolusPumpDate) {
        this.lastBolusPumpDate = lastBolusPumpDate;
    }

    public byte getLastBolusReference() {
        return lastBolusReference;
    }

    public void setLastBolusReference(byte lastBolusReference) {
        this.lastBolusReference = lastBolusReference;
    }

    public short getLastBolusDuration() {
        return lastBolusDuration;
    }

    public void setLastBolusDuration(short lastBolusDuration) {
        this.lastBolusDuration = lastBolusDuration;
    }

    public byte getTransmitterBattery() {
        return transmitterBattery;
    }

    public void setTransmitterBattery(byte transmitterBattery) {
        this.transmitterBattery = transmitterBattery;
    }

    public byte getTransmitterControl() {
        return transmitterControl;
    }

    public void setTransmitterControl(byte transmitterControl) {
        this.transmitterControl = transmitterControl;
    }

    public short getCalibrationDueMinutes() {
        return calibrationDueMinutes;
    }

    public void setCalibrationDueMinutes(short calibrationDueMinutes) {
        this.calibrationDueMinutes = calibrationDueMinutes;
    }

    public float getSensorRateOfChange() {
        return sensorRateOfChange;
    }

    public void setSensorRateOfChange(float sensorRateOfChange) {
        this.sensorRateOfChange = sensorRateOfChange;
    }

    @Override
    public String toString() {
        return "PumpStatusEvent{" +
                "eventDate=" + eventDate +
                ", deviceName='" + deviceName + '\'' +
                ", pumpStatus=" + pumpStatus +
                ", cgmStatus=" + cgmStatus +
                ", suspended=" + suspended +
                ", bolusingNormal=" + bolusingNormal +
                ", bolusingSquare=" + bolusingSquare +
                ", bolusingDual=" + bolusingDual +
                ", deliveringInsulin=" + deliveringInsulin +
                ", tempBasalActive=" + tempBasalActive +
                ", cgmActive=" + cgmActive +
                ", cgmCalibrating=" + cgmCalibrating +
                ", cgmCalibrationComplete=" + cgmCalibrationComplete +
                ", cgmException=" + cgmException +
                ", cgmWarmUp=" + cgmWarmUp +
                ", activeBasalPattern=" + activeBasalPattern +
                ", basalRate=" + basalRate +
                ", tempBasalRate=" + tempBasalRate +
                ", tempBasalPercentage=" + tempBasalPercentage +
                ", tempBasalMinutesRemaining=" + tempBasalMinutesRemaining +
                ", basalUnitsDeliveredToday=" + basalUnitsDeliveredToday +
                ", batteryPercentage=" + batteryPercentage +
                ", reservoirAmount=" + reservoirAmount +
                ", minutesOfInsulinRemaining=" + minutesOfInsulinRemaining +
                ", activeInsulin=" + activeInsulin +
                ", sgv=" + sgv +
                ", cgmDate=" + cgmDate +
                ", cgmExceptionType=" + cgmExceptionType +
                ", lowSuspendActive=" + lowSuspendActive +
                ", cgmTrend='" + cgmTrend + '\'' +
                ", recentBolusWizard=" + recentBolusWizard +
                ", recentBGL=" + recentBGL +
                ", alert=" + alert +
                ", alertDate=" + alertDate +
                ", bolusingDelivered=" + bolusingDelivered +
                ", bolusingMinutesRemaining=" + bolusingMinutesRemaining +
                ", bolusingReference=" + bolusingReference +
                ", lastBolusAmount=" + lastBolusAmount +
                ", lastBolusDate=" + lastBolusDate +
                ", lastBolusPumpDate=" + lastBolusPumpDate +
                ", lastBolusDuration=" + lastBolusDuration +
                ", lastBolusReference=" + lastBolusReference +
                ", transmitterBattery=" + transmitterBattery +
                ", transmitterControl=" + transmitterControl +
                ", calibrationDueMinutes=" + calibrationDueMinutes +
                ", sensorRateOfChange=" + sensorRateOfChange +
                ", oldSgvWhenNewExpected=" + oldSgvWhenNewExpected +
                ", validSGV=" + validSGV +
                ", uploaded=" + uploaded +
                '}';
    }

    public enum CGM_TREND {
        NONE,
        DOUBLE_UP,
        SINGLE_UP,
        FOURTY_FIVE_UP,
        FLAT,
        FOURTY_FIVE_DOWN,
        SINGLE_DOWN,
        DOUBLE_DOWN,
        NOT_COMPUTABLE,
        RATE_OUT_OF_RANGE,
        NOT_SET;

        public static CGM_TREND fromMessageByte(byte messageByte) {
            switch (messageByte) {
                case (byte) 0x60:
                    return PumpStatusEvent.CGM_TREND.FLAT;
                case (byte) 0xc0:
                    return PumpStatusEvent.CGM_TREND.DOUBLE_UP;
                case (byte) 0xa0:
                    return PumpStatusEvent.CGM_TREND.SINGLE_UP;
                case (byte) 0x80:
                    return PumpStatusEvent.CGM_TREND.FOURTY_FIVE_UP;
                case (byte) 0x40:
                    return PumpStatusEvent.CGM_TREND.FOURTY_FIVE_DOWN;
                case (byte) 0x20:
                    return PumpStatusEvent.CGM_TREND.SINGLE_DOWN;
                case (byte) 0x00:
                    return PumpStatusEvent.CGM_TREND.DOUBLE_DOWN;
                default:
                    return PumpStatusEvent.CGM_TREND.NOT_COMPUTABLE;
            }
        }
    }
}

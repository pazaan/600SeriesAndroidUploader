package info.nightscout.android.model.medtronicNg;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;

/**
 * Created by lgoedhart on 4/06/2016.
 */
public class PumpStatusEvent extends RealmObject {
    @Index
    private Date eventDate; // The actual time of the event (assume the capture device eventDate/time is accurate)
    private Date pumpDate; // The eventDate/time on the pump at the time of the event
    private String deviceName;

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
    private String cgmTrend;

    private boolean recentBolusWizard; // Whether a bolus wizard has been run recently
    private int bolusWizardBGL; // in mg/dL. 0 means no recent bolus wizard reading.

    @Ignore
    private long pumpTimeOffset; // millis the pump is ahead

    @Index
    private boolean uploaded = false;

    public PumpStatusEvent() {
        // The the eventDate to now.
        this.eventDate = new Date();
    }

    public Date getEventDate() {
        return eventDate;
    }

    // No EventDate setter. The eventDate is set at the time that the PumpStatusEvent is created.

    public Date getPumpDate() {
        return pumpDate;
    }

    public void setPumpDate(Date pumpDate) {
        this.pumpDate = pumpDate;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
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

    public int getBolusWizardBGL() {
        return bolusWizardBGL;
    }

    public void setBolusWizardBGL(int bolusWizardBGL) {
        this.bolusWizardBGL = bolusWizardBGL;
    }

    public boolean isUploaded() {
        return uploaded;
    }

    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    public boolean isBolusing() {
        return bolusing;
    }

    public void setBolusing(boolean bolusing) {
        this.bolusing = bolusing;
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

    public byte getTempBasalPercentage() {
        return tempBasalPercentage;
    }

    public void setTempBasalPercentage(byte tempBasalPercentage) {
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

    public Date getSgvDate() {
        return sgvDate;
    }

    public void setSgvDate(Date sgvDate) {
        this.sgvDate = sgvDate;
    }

    public boolean isLowSuspendActive() {
        return lowSuspendActive;
    }

    public void setLowSuspendActive(boolean lowSuspendActive) {
        this.lowSuspendActive = lowSuspendActive;
    }

    public boolean isRecentBolusWizard() {
        return recentBolusWizard;
    }

    public void setRecentBolusWizard(boolean recentBolusWizard) {
        this.recentBolusWizard = recentBolusWizard;
    }

    public long getPumpTimeOffset() {
        return pumpTimeOffset;
    }

    public void setPumpTimeOffset(long pumpTimeOffset) {
        this.pumpTimeOffset = pumpTimeOffset;
    }

    @Override
    public String toString() {
        return "PumpStatusEvent{" +
                "eventDate=" + eventDate +
                ", pumpDate=" + pumpDate +
                ", deviceName='" + deviceName + '\'' +
                ", suspended=" + suspended +
                ", bolusing=" + bolusing +
                ", deliveringInsulin=" + deliveringInsulin +
                ", tempBasalActive=" + tempBasalActive +
                ", cgmActive=" + cgmActive +
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
                ", sgvDate=" + sgvDate +
                ", lowSuspendActive=" + lowSuspendActive +
                ", cgmTrend='" + cgmTrend + '\'' +
                ", recentBolusWizard=" + recentBolusWizard +
                ", bolusWizardBGL=" + bolusWizardBGL +
                ", pumpTimeOffset=" + pumpTimeOffset +
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

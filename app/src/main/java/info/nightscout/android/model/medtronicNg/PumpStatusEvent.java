package info.nightscout.android.model.medtronicNg;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.Index;

/**
 * Created by lgoedhart on 4/06/2016.
 */
public class PumpStatusEvent extends RealmObject {
    @Index
    private Date eventDate; // The actual time of the event (assume the capture device eventDate/time is accurate)
    private Date pumpDate; // The eventDate/time on the pump at the time of the event
    private String deviceName;
    private int sgv;
    private String cgmTrend;
    private float activeInsulin;
    private short batteryPercentage;
    private float reservoirAmount;
    private boolean recentBolusWizard; // Whether a bolus wizard has been run recently
    private int bolusWizardBGL; // in mg/dL. 0 means no recent bolus wizard reading.
    @Index
    private boolean uploaded = false;

    public Date getEventDate() {
        return eventDate;
    }

    public void setEventDate(Date eventDate) {
        this.eventDate = eventDate;
    }

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
        return CGM_TREND.valueOf(cgmTrend);
    }

    public void setCgmTrend(CGM_TREND cgmTrend) {
        this.cgmTrend = cgmTrend.name();
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

    public void setRecentBolusWizard(boolean recentBolusWizard) {
        this.recentBolusWizard = recentBolusWizard;
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
        NOT_SET
    }
}

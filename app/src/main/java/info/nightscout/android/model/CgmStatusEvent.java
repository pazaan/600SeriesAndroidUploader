package info.nightscout.android.model;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.Index;

/**
 * Created by lgoedhart on 4/06/2016.
 */
public class CgmStatusEvent extends RealmObject {
    @Index
    private Date eventDate; // The actual time of the event (assume the capture device eventDate/time is accurate)
    private Date pumpDate; // The eventDate/time on the pump at the time of the event
    private String deviceName;
    private int sgv;
    private String trend;
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

    public TREND getTrend() {
        return TREND.valueOf(trend);
    }

    public void setTrend(TREND trend) {
        this.trend = trend.name();
    }

    public boolean isUploaded() {
        return uploaded;
    }

    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }

    public enum TREND {
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

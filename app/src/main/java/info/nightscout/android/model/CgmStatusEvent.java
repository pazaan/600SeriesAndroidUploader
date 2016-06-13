package info.nightscout.android.model;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.Index;

/**
 * Created by lgoedhart on 4/06/2016.
 */
public class CgmStatusEvent extends RealmObject {
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

    @Index
    private Date eventDate; // The actual time of the event (assume the capture device eventDate/time is accurate)
    private Date pumpDate; // The eventDate/time on the pump at the time of the event
    private int sgv;
    private String trend;

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
}

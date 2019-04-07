package info.nightscout.android.model.medtronicNg;

import java.util.Date;

import io.realm.RealmObject;

/**
 * Created by Pogman on 23.10.17.
 */

public class HistorySegment extends RealmObject {
    private Date fromDate;
    private Date toDate;
    private byte historyType;

    public void addSegment(Date date, byte historyType) {
        this.historyType = historyType;
        this.fromDate = date;
        this.toDate = date;
    }

    public Date getFromDate() {
        return fromDate;
    }

    public void setFromDate(Date fromDate) {
        this.fromDate = fromDate;
    }

    public Date getToDate() {
        return toDate;
    }

    public void setToDate(Date toDate) {
        this.toDate = toDate;
    }

    public byte getHistoryType() {
        return historyType;
    }

    public void setHistoryType(byte historyType) {
        this.historyType = historyType;
    }
}

package info.nightscout.android.model.medtronicNg;

import io.realm.RealmObject;

/**
 * Created by lennart on 22/1/17.
 */

public class BasalRate extends RealmObject {
    private long start;
    private float rate;

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public float getRate() {
        return rate;
    }

    public void setRate(float rate) {
        this.rate = rate;
    }
}

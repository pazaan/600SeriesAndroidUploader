package info.nightscout.android.model.medtronicNg;

import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;

/**
 * Created by lennart on 22/1/17.
 */

public class BasalSchedule extends RealmObject {
    @PrimaryKey
    private byte scheduleNumber;
    private RealmList<BasalRate> schedule;

    @Index
    private boolean uploaded = false;

    public byte getScheduleNumber() {
        return scheduleNumber;
    }

    public void setScheduleNumber(byte scheduleNumber) {
        this.scheduleNumber = scheduleNumber;
    }

    public String getName() {
        // TODO - internationalise
        String[] patternNames = {
                "Pattern 1",
                "Pattern 2",
                "Pattern 3",
                "Pattern 4",
                "Pattern 5",
                "Workday",
                "Day Off",
                "Sick Day",

        };
        return patternNames[this.scheduleNumber - 1];
    }

    public RealmList<BasalRate> getSchedule() {
        return schedule;
    }

    public void setSchedule(RealmList<BasalRate> schedule) {
        this.schedule = schedule;
    }

    public boolean isUploaded() {
        return uploaded;
    }

    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }
}
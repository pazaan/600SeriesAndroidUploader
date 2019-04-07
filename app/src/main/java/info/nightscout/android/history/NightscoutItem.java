package info.nightscout.android.history;

import info.nightscout.android.upload.nightscout.EntriesEndpoints;
import info.nightscout.android.upload.nightscout.ProfileEndpoints;
import info.nightscout.android.upload.nightscout.TreatmentsEndpoints;

/**
 * Created by Pogman on 22.11.17.
 */

public class NightscoutItem {

    private TreatmentsEndpoints.Treatment treatment;
    private EntriesEndpoints.Entry entry;
    private ProfileEndpoints.Profile profile;

    private long timestamp;

    private MODE mode;

    public enum MODE {
        CHECK,
        UPDATE,
        DELETE
    }

    public NightscoutItem setMode(MODE mode) {
        this.mode = mode;
        return this;
    }

    public NightscoutItem ack(boolean uploadACK) {
        mode = uploadACK ? MODE.UPDATE : MODE.CHECK;
        return this;
    }

    public NightscoutItem update() {
        mode = MODE.UPDATE;
        return this;
    }

    public NightscoutItem check() {
        mode = MODE.CHECK;
        return this;
    }

    public NightscoutItem delete() {
        mode = MODE.DELETE;
        return this;
    }

    public EntriesEndpoints.Entry entry() {
        entry = new EntriesEndpoints.Entry();
        return entry;
    }

    public TreatmentsEndpoints.Treatment treatment() {
        treatment = new TreatmentsEndpoints.Treatment();
        return treatment;
    }

    public ProfileEndpoints.Profile profile() {
        profile = new ProfileEndpoints.Profile();
        return profile;
    }

    public EntriesEndpoints.Entry getEntry() {
        return entry;
    }

    public TreatmentsEndpoints.Treatment getTreatment() {
        return treatment;
    }

    public ProfileEndpoints.Profile getProfile() {
        return profile;
    }

    public boolean isEntry() {
        return entry != null;
    }

    public boolean isTreatment() {
        return treatment != null;
    }

    public boolean isProfile() {
        return profile != null;
    }

    public MODE getMode() {
        return mode;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }
}

package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.android.medtronic.PumpHistoryParser;
import info.nightscout.android.model.store.DataStore;
import info.nightscout.api.TreatmentsEndpoints;
import info.nightscout.api.UploadItem;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;

/**
 * Created by Pogman on 26.10.17.
 */

public class PumpHistoryBasal extends RealmObject implements PumpHistoryInterface {
    @Ignore
    private static final String TAG = PumpHistoryBasal.class.getSimpleName();

    @Index
    private Date eventDate;

    @Index
    private boolean uploadREQ = false;
    private boolean uploadACK = false;

    private boolean xdripREQ = false;
    private boolean xdripACK = false;

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    @Index
    private int programmedRTC;
    private int programmedOFFSET;
    private Date programmedDate;
    private boolean programmed = false;

    @Index
    private int completedRTC;
    private int completedOFFSET;
    private Date completedDate;
    private boolean completed = false;

    private int type;
    private int preset;

    private double rate;
    private int percentageOfRate;
    private int duration;
    private boolean canceled;

    private boolean suspend = false;
    private int suspendReason;

    private boolean resume = false;
    private int resumeReason;

    @Override
    public List nightscout(DataStore dataStore) {
        List<UploadItem> uploadItems = new ArrayList<>();

        if (dataStore.isNsEnableTreatments()) {

            UploadItem uploadItem = new UploadItem();
            uploadItems.add(uploadItem);
            TreatmentsEndpoints.Treatment treatment = uploadItem.ack(uploadACK).treatment();

            treatment.setKey600(key);
            treatment.setEventType("Temp Basal");
            String notes = "";

            if (suspend) {
                treatment.setCreated_at(programmedDate);
                treatment.setDuration((float) duration);
                treatment.setAbsolute((float) 0);
                notes = PumpHistoryParser.TextEN.NS_SUSPEND.getText() + ": " +
                        PumpHistoryParser.TextEN.valueOf(PumpHistoryParser.SUSPEND_REASON.convert(suspendReason).name()).getText();

            } else if (resume) {
                treatment.setCreated_at(programmedDate);
                treatment.setDuration((float) 0);
                notes = PumpHistoryParser.TextEN.NS_RESUME.getText() + ": " +
                        PumpHistoryParser.TextEN.valueOf(PumpHistoryParser.RESUME_REASON.convert(resumeReason).name()).getText();

            } else {
                treatment.setCreated_at(programmedDate);

                notes += "Temp Basal:";

                if (PumpHistoryParser.TEMP_BASAL_TYPE.PERCENT.equals(type)) {
                    treatment.setPercent((float) (percentageOfRate - 100));
                    notes += " " + percentageOfRate + "%";
                } else {
                    treatment.setAbsolute((float) rate);
                    notes += " " + rate + "U";
                }

                notes += ", duration " + duration + " minutes";

                if (!PumpHistoryParser.TEMP_BASAL_PRESET.TEMP_BASAL_PRESET_0.equals(preset))
                    notes += " [" + dataStore.getNameTempBasalPreset(preset) + "]";

                if (!canceled) {
                    treatment.setDuration((float) duration);
                } else {
                    int minutes = (int) Math.ceil((completedRTC - programmedRTC) / 60);
                    treatment.setDuration((float) minutes);
                    notes += " * canceled, duration " + minutes + " minutes";
                    uploadItem.update();
                }
            }

            treatment.setNotes(notes);
        }

        return uploadItems;
    }

    public static void temp(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                            boolean completed,
                            int preset,
                            int type,
                            double rate,
                            int percentageOfRate,
                            int duration,
                            boolean canceled) {

        PumpHistoryBasal object = realm.where(PumpHistoryBasal.class)
                .equalTo("programmedRTC", eventRTC)
                .or()
                .equalTo("completedRTC", eventRTC)
                .findFirst();
        if (object == null) {
            if (!completed) {
                object = realm.where(PumpHistoryBasal.class)
                        .equalTo("programmed", false)
                        .equalTo("completed", true)
                        .greaterThan("completedRTC", eventRTC)
                        .lessThan("completedRTC", eventRTC + (duration + 1) * 60)
                        .findFirst();
            } else {
                object = realm.where(PumpHistoryBasal.class)
                        .equalTo("programmed", true)
                        .equalTo("completed", false)
                        .greaterThan("programmedRTC", eventRTC - (duration + 1) * 60)
                        .lessThan("programmedRTC", eventRTC)
                        .findFirst();
            }
            if (object == null) {
                Log.d(TAG, "*new*" + " temp basal");
                object = realm.createObject(PumpHistoryBasal.class);
                object.setEventDate(eventDate);
            } else {
                Log.d(TAG, "*update*" + " temp basal");
            }
        }
        if (!object.isProgrammed() || !object.isCompleted()) {
            object.setPreset(preset);
            object.setType(type);
            object.setRate(rate);
            object.setPercentageOfRate(percentageOfRate);
            object.setDuration(duration);
            object.setCanceled(canceled);
            if (!completed) {
                Log.d(TAG, "*update*" + " temp basal programmed");
                object.setProgrammed(true);
                object.setProgrammedDate(eventDate);
                object.setProgrammedRTC(eventRTC);
                object.setProgrammedOFFSET(eventOFFSET);
                object.setKey("BASAL" + String.format("%08X", eventRTC));
                object.setUploadREQ(true);
            } else {
                Log.d(TAG, "*update*" + " temp basal completed");
                object.setCompleted(true);
                object.setCompletedDate(eventDate);
                object.setCompletedRTC(eventRTC);
                object.setCompletedOFFSET(eventOFFSET);
                if (object.isProgrammed() && canceled) object.setUploadREQ(true);
            }
        }
    }

    public static void suspend(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                               int reason) {

        PumpHistoryBasal object = realm.where(PumpHistoryBasal.class)
                .equalTo("suspend", true)
                .equalTo("programmedRTC", eventRTC)
                .findFirst();
        if (object == null) {
            Log.d(TAG, "*new*" + " suspend basal");
            object = realm.createObject(PumpHistoryBasal.class);
            object.setEventDate(eventDate);
            object.setProgrammedDate(eventDate);
            object.setProgrammedRTC(eventRTC);
            object.setProgrammedOFFSET(eventOFFSET);
            object.setDuration(24 * 60);
            object.setSuspendReason(reason);
            object.setSuspend(true);
            object.setKey("SUSPEND" + String.format("%08X", eventRTC));
            object.setUploadREQ(true);
        }
    }

    public static void resume(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                              int reason) {

        PumpHistoryBasal object = realm.where(PumpHistoryBasal.class)
                .equalTo("resume", true)
                .equalTo("programmedRTC", eventRTC)
                .findFirst();
        if (object == null) {
            Log.d(TAG, "*new*" + " resume basal");
            object = realm.createObject(PumpHistoryBasal.class);
            object.setEventDate(eventDate);
            object.setProgrammedDate(eventDate);
            object.setProgrammedRTC(eventRTC);
            object.setProgrammedOFFSET(eventOFFSET);
            object.setResumeReason(reason);
            object.setResume(true);
            object.setKey("RESUME" + String.format("%08X", eventRTC));
            object.setUploadREQ(true);
            // look for corresponding suspend and update it's duration
            object = realm.where(PumpHistoryBasal.class)
                    .equalTo("suspend", true)
                    .equalTo("duration", 24 * 60)
                    .greaterThan("programmedRTC", eventRTC - 24 * 60 * 60)
                    .lessThan("programmedRTC", eventRTC)
                    .findFirst();
            if (object != null) {
                object.setDuration((eventRTC - object.getProgrammedRTC()) / 60);
                object.setUploadREQ(true);
            }
        }
    }

    @Override
    public Date getEventDate() {
        return eventDate;
    }

    @Override
    public void setEventDate(Date eventDate) {
        this.eventDate = eventDate;
    }

    @Override
    public boolean isUploadREQ() {
        return uploadREQ;
    }

    @Override
    public void setUploadREQ(boolean uploadREQ) {
        this.uploadREQ = uploadREQ;
    }

    @Override
    public boolean isUploadACK() {
        return uploadACK;
    }

    @Override
    public void setUploadACK(boolean uploadACK) {
        this.uploadACK = uploadACK;
    }

    @Override
    public boolean isXdripREQ() {
        return xdripREQ;
    }

    @Override
    public void setXdripREQ(boolean xdripREQ) {
        this.xdripREQ = xdripREQ;
    }

    @Override
    public boolean isXdripACK() {
        return xdripACK;
    }

    @Override
    public void setXdripACK(boolean xdripACK) {
        this.xdripACK = xdripACK;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public void setKey(String key) {
        this.key = key;
    }

    public boolean isProgrammed() {
        return programmed;
    }

    public void setProgrammed(boolean programmed) {
        this.programmed = programmed;
    }

    public int getProgrammedRTC() {
        return programmedRTC;
    }

    public void setProgrammedRTC(int programmedRTC) {
        this.programmedRTC = programmedRTC;
    }

    public int getProgrammedOFFSET() {
        return programmedOFFSET;
    }

    public void setProgrammedOFFSET(int programmedOFFSET) {
        this.programmedOFFSET = programmedOFFSET;
    }

    public Date getProgrammedDate() {
        return programmedDate;
    }

    public void setProgrammedDate(Date programmedDate) {
        this.programmedDate = programmedDate;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public int getCompletedRTC() {
        return completedRTC;
    }

    public void setCompletedRTC(int completedRTC) {
        this.completedRTC = completedRTC;
    }

    public int getCompletedOFFSET() {
        return completedOFFSET;
    }

    public void setCompletedOFFSET(int completedOFFSET) {
        this.completedOFFSET = completedOFFSET;
    }

    public Date getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(Date completedDate) {
        this.completedDate = completedDate;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getPreset() {
        return preset;
    }

    public void setPreset(int preset) {
        this.preset = preset;
    }

    public void setSuspendReason(int suspendReason) {
        this.suspendReason = suspendReason;
    }

    public void setResumeReason(int resumeReason) {
        this.resumeReason = resumeReason;
    }

    public double getRate() {
        return rate;
    }

    public void setRate(double rate) {
        this.rate = rate;
    }

    public int getPercentageOfRate() {
        return percentageOfRate;
    }

    public void setPercentageOfRate(int percentageOfRate) {
        this.percentageOfRate = percentageOfRate;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public boolean isSuspend() {
        return suspend;
    }

    public void setSuspend(boolean suspend) {
        this.suspend = suspend;
    }

    public int getSuspendReason() {
        return suspendReason;
    }

    public boolean isResume() {
        return resume;
    }

    public void setResume(boolean resume) {
        this.resume = resume;
    }

    public int getResumeReason() {
        return resumeReason;
    }
}

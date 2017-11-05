package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import info.nightscout.api.TreatmentsEndpoints;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;

/**
 * Created by John on 26.10.17.
 */

public class PumpHistoryBasal extends RealmObject implements PumpHistory {
    @Ignore
    private static final String TAG = PumpHistoryBasal.class.getSimpleName();

    @Index
    private Date eventDate;
    @Index
    private Date eventEndDate; // event deleted when this is stale

    private boolean uploadREQ = false;
    private boolean uploadACK = false;
    private boolean xdripREQ = false;
    private boolean xdripACK = false;

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    private boolean programmed = false;
    private int programmedRTC;
    private int programmedOFFSET;
    private Date programmedDate;

    private boolean completed = false;
    private int completedRTC;
    private int completedOFFSET;
    private Date completedDate;

    private int preset;
    private int type;
    private double rate;
    private int percentageOfRate;
    private int duration;
    private boolean canceled;

    private boolean suspend = false;
    private int suspendRTC;
    private int suspendOFFSET;
    private Date suspendDate;
    private int suspendReason;

    private boolean resume = false;
    private int resumeRTC;
    private int resumeOFFSET;
    private Date resumeDate;
    private int resumeReason;

    @Override
    public List Nightscout() {
        Log.d(TAG, "*history* BASAL do da thing! ");

        List list = new ArrayList();
        list.add("treatment");
        if (uploadACK) list.add("update"); else list.add("new");
        TreatmentsEndpoints.Treatment treatment = new TreatmentsEndpoints.Treatment();
        list.add(treatment);

        treatment.setKey600(key);

        if (suspend) {
            treatment.setEventType("Temp Basal");
            treatment.setCreated_at(suspendDate);
            treatment.setDuration((float) 24 * 60);
            treatment.setAbsolute((float) 0);
            treatment.setNotes("Pump suspended insulin delivery, reason = " + suspendReason);

        } else if (resume) {
            treatment.setEventType("Temp Basal");
            treatment.setCreated_at(resumeDate);
            treatment.setDuration((float) 0);
            treatment.setNotes("Pump resumed insulin delivery, reason = " + resumeReason);

        } else {
            treatment.setEventType("Temp Basal");
            treatment.setCreated_at(programmedDate);

            if (percentageOfRate > 0)
                treatment.setPercent((float) percentageOfRate - 100);
            else
                treatment.setAbsolute((float) rate);

            if (!canceled)
                treatment.setDuration((float) duration);
            else
                treatment.setDuration((float) (completedRTC - programmedRTC) / 60);

            if (!completed) {
                treatment.setDuration((float) duration);
                treatment.setNotes("Temp Basal in progress");
            } else {
                treatment.setDuration((float) (completedRTC - programmedRTC) / 60);
                String notes = "Temp Basal: rate " + rate + "U, percent " + percentageOfRate + "%, duration " + duration;
                if (canceled)
                    notes += " * canceled, duration " + (completedRTC - programmedRTC) / 60 + " minutes";
                treatment.setNotes(notes);
            }
        }

        return list;
    }

    public static void stale(Realm realm, Date date) {
        final RealmResults results = realm.where(PumpHistoryBasal.class)
                .lessThan("eventDate", date)
                .findAll();
        if (results.size() > 0) {
            Log.d(TAG, "deleting " + results.size() + " records from realm");
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    results.deleteAllFromRealm();
                }
            });
        }
    }

    public static void records(Realm realm) {
        DateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);
        final RealmResults<PumpHistoryBasal> results = realm.where(PumpHistoryBasal.class)
                .findAllSorted("eventDate", Sort.ASCENDING);
        Log.d(TAG, "records: " + results.size() + (results.size() > 0 ? " start: "+ dateFormatter.format(results.first().getEventDate()) + " end: " + dateFormatter.format(results.last().getEventDate()) : ""));
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
                if (!completed) {
                    object.setEventDate(eventDate);
                    object.setEventEndDate(new Date(eventDate.getTime() + duration * 60 * 1000));
                } else {
                    object.setEventDate(new Date(eventDate.getTime() - duration * 60 * 1000));
                    object.setEventEndDate(eventDate);
                }
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
                if (object.isProgrammed()) object.setUploadREQ(true);
            }
        }
    }

    public static void suspend(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                            int reason) {

        PumpHistoryBasal object = realm.where(PumpHistoryBasal.class)
                .equalTo("suspendRTC", eventRTC)
                .findFirst();
        if (object == null) {
            Log.d(TAG, "*new*" + " suspend basal");
            object = realm.createObject(PumpHistoryBasal.class);
            object.setEventDate(eventDate);
            object.setEventEndDate(new Date(eventDate.getTime() + 24 * 60 * 60 * 1000L));
            object.setSuspendDate(eventDate);
            object.setSuspendRTC(eventRTC);
            object.setSuspendOFFSET(eventOFFSET);
            object.setSuspendReason(reason);
            object.setSuspend(true);
            object.setKey("SUSPEND" + String.format("%08X", eventRTC));
            object.setUploadREQ(true);
        }
    }

    public static void resume(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                               int reason) {

        PumpHistoryBasal object = realm.where(PumpHistoryBasal.class)
                .equalTo("resumeRTC", eventRTC)
                .findFirst();
        if (object == null) {
            Log.d(TAG, "*new*" + " resume basal");
            object = realm.createObject(PumpHistoryBasal.class);
            object.setEventDate(eventDate);
            object.setEventEndDate(new Date(eventDate.getTime() + 24 * 60 * 60 * 1000L));
            object.setResumeDate(eventDate);
            object.setResumeRTC(eventRTC);
            object.setResumeOFFSET(eventOFFSET);
            object.setResumeReason(reason);
            object.setResume(true);
            object.setKey("RESUME" + String.format("%08X", eventRTC));
            object.setUploadREQ(true);
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
    public Date getEventEndDate() {
        return eventEndDate;
    }

    @Override
    public void setEventEndDate(Date eventEndDate) {
        this.eventEndDate = eventEndDate;
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

    public int getPreset() {
        return preset;
    }

    public void setPreset(int preset) {
        this.preset = preset;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
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

    public int getSuspendRTC() {
        return suspendRTC;
    }

    public void setSuspendRTC(int suspendRTC) {
        this.suspendRTC = suspendRTC;
    }

    public int getSuspendOFFSET() {
        return suspendOFFSET;
    }

    public void setSuspendOFFSET(int suspendOFFSET) {
        this.suspendOFFSET = suspendOFFSET;
    }

    public Date getSuspendDate() {
        return suspendDate;
    }

    public void setSuspendDate(Date suspendDate) {
        this.suspendDate = suspendDate;
    }

    public int getSuspendReason() {
        return suspendReason;
    }

    public void setSuspendReason(int suspendReason) {
        this.suspendReason = suspendReason;
    }

    public boolean isResume() {
        return resume;
    }

    public void setResume(boolean resume) {
        this.resume = resume;
    }

    public int getResumeRTC() {
        return resumeRTC;
    }

    public void setResumeRTC(int resumeRTC) {
        this.resumeRTC = resumeRTC;
    }

    public int getResumeOFFSET() {
        return resumeOFFSET;
    }

    public void setResumeOFFSET(int resumeOFFSET) {
        this.resumeOFFSET = resumeOFFSET;
    }

    public Date getResumeDate() {
        return resumeDate;
    }

    public void setResumeDate(Date resumeDate) {
        this.resumeDate = resumeDate;
    }

    public int getResumeReason() {
        return resumeReason;
    }

    public void setResumeReason(int resumeReason) {
        this.resumeReason = resumeReason;
    }
}

package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import info.nightscout.api.EntriesEndpoints;
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

public class PumpHistoryBolus extends RealmObject implements PumpHistory {
    @Ignore
    private static final String TAG = PumpHistoryBolus.class.getSimpleName();

    @Index
    private Date eventDate;
    @Index
    private Date eventEndDate; // event deleted when this is stale

    private boolean uploadREQ = false;
    private boolean uploadACK = false;
    private boolean xdripREQ = false;
    private boolean xdripACK = false;

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    private int bolusType; // normal=0 square=1 dual=2
    private int bolusSource; // manual=0 wizard=1
    private int bolusRef = -1;

    private int presetBolusNumber;
    private double normalProgrammedAmount;
    private double normalDeliveredAmount;
    private double squareProgrammedAmount;
    private double squareDeliveredAmount;
    private int squareProgrammedDuration;
    private int squareDeliveredDuration;
    private double activeInsulin;

    private boolean programmed = false;
    private int programmedRTC;
    private int programmedOFFSET;
    private Date programmedDate;

    private boolean normalDelivered = false;
    private int normalDeliveredRTC;
    private int normalDeliveredOFFSET;
    private Date normalDeliveredDate;

    private boolean squareDelivered = false;
    private int squareDeliveredRTC;
    private int squareDeliveredOFFSET;
    private Date squareDeliveredDate;

    private boolean estimate = false;
    private int estimateRTC;
    private int estimateOFFSET;

    private int bgUnits;
    private int carbUnits;
    private double bgInput;
    private double carbInput;
    private double isf;
    private double carbRatio;
    private double lowBgTarget;
    private double highBgTarget;
    private double correctionEstimate;
    private double foodEstimate;
    private double iob;
    private double iobAdjustment;
    private double bolusWizardEstimate;
    private int bolusStepSize;
    private boolean estimateModifiedByUser;
    private double finalEstimate;

    @Override
    public List Nightscout() {
        Log.d(TAG, "*history* BOLUS do da thing! " + "type: " + bolusType);

        List list = new ArrayList();
        list.add("treatment");
        if (uploadACK) list.add("update"); else list.add("new");
        TreatmentsEndpoints.Treatment treatment = new TreatmentsEndpoints.Treatment();
        list.add(treatment);

        String notes = "";
        treatment.setKey600(key);
        treatment.setCreated_at(eventDate);
        if (bolusType == 0) {
            treatment.setEventType("Bolus");
            treatment.setInsulin((float) normalProgrammedAmount);
        } else if (bolusType == 1) {
            treatment.setEventType("Combo Bolus");
            treatment.setEnteredinsulin(String.valueOf(squareProgrammedAmount));
            treatment.setDuration((float) squareProgrammedDuration);
            treatment.setSplitNow("0");
            treatment.setSplitExt("100");
            treatment.setRelative((float) 2);
            notes = "Square Bolus: " + squareProgrammedAmount + "U, duration " + squareProgrammedDuration + " minutes";
        } else if (bolusType == 2) {
            treatment.setEventType("Combo Bolus");
            treatment.setEnteredinsulin(String.valueOf(normalProgrammedAmount + squareProgrammedAmount));
            treatment.setDuration((float) squareProgrammedDuration);
            treatment.setInsulin((float) normalProgrammedAmount);
            int splitNow = (int) (normalProgrammedAmount * (100 / (normalProgrammedAmount + squareProgrammedAmount)));
            int splitExt = 100 - splitNow;
            treatment.setSplitNow(String.valueOf(splitNow));
            treatment.setSplitExt(String.valueOf(splitExt));
            treatment.setRelative((float) 2);
            notes = "Dual Bolus: normal " + normalProgrammedAmount + "U, square " + squareProgrammedAmount + "U, duration " + squareProgrammedDuration + " minutes";
        } else {
            treatment.setEventType("Note");
            notes = "Unknown event";
        }

        if (normalDelivered && normalProgrammedAmount != normalDeliveredAmount)
            notes += " * cancelled, delivered " + normalDeliveredAmount + "U";
        else if (squareDelivered && squareProgrammedAmount != squareDeliveredAmount)
            notes += " * ended before expected duration, delivered " + squareDeliveredAmount + "U in " + squareDeliveredDuration + " minutes";

        if (estimate) {
            treatment.setEventType("Meal Bolus");
            treatment.setCarbs((float) carbInput);
            if (notes != "") notes += "  ";
            notes += "Wizard: "
                    + "Carb " + carbInput + "G " + foodEstimate + "U"
                    + ", Correction " + correctionEstimate + "U"
                    + ", IOB " + iob + "U"
                    + ", IOB adjustment " + iobAdjustment + "U"
                    + ", Target " + lowBgTarget + "-" + highBgTarget
                    + ", Ratio " + carbRatio
                    + ", ISF " + isf;
        }

        treatment.setNotes(notes);

        return list;
    }

    public static void stale(Realm realm, Date date) {
        final RealmResults results = realm.where(PumpHistoryBolus.class)
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
        final RealmResults<PumpHistoryBolus> results = realm.where(PumpHistoryBolus.class)
                .findAllSorted("eventDate", Sort.ASCENDING);
        Log.d(TAG, "records: " + results.size() + (results.size() > 0 ? " start: "+ dateFormatter.format(results.first().getEventDate()) + " end: " + dateFormatter.format(results.last().getEventDate()) : ""));
    }

    public static void bolus(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                             int bolusType,
                             boolean programmed, boolean normalDelivered, boolean squareDelivered,
                             int bolusRef,
                             int bolusSource,
                             int presetBolusNumber,
                             double normalProgrammedAmount, double normalDeliveredAmount,
                             double squareProgrammedAmount, double squareDeliveredAmount,
                             int squareProgrammedDuration, int squareDeliveredDuration,
                             double activeInsulin) {

        PumpHistoryBolus object = realm.where(PumpHistoryBolus.class)
                .beginGroup()
                .equalTo("bolusRef", bolusRef)
                .greaterThanOrEqualTo("programmedRTC", eventRTC - 12 * 60 * 60)
                .lessThanOrEqualTo("programmedRTC", eventRTC + 12 * 60 * 60)
                .endGroup()
                .or()
                .beginGroup()
                .equalTo("bolusRef", bolusRef)
                .greaterThanOrEqualTo("normalDeliveredRTC", eventRTC - 12 * 60 * 60)
                .lessThanOrEqualTo("normalDeliveredRTC", eventRTC + 12 * 60 * 60)
                .endGroup()
                .or()
                .beginGroup()
                .equalTo("bolusRef", bolusRef)
                .greaterThanOrEqualTo("squareDeliveredRTC", eventRTC - 12 * 60 * 60)
                .lessThanOrEqualTo("squareDeliveredRTC", eventRTC + 12 * 60 * 60)
                .endGroup()
                .findFirst();

        if (object == null) {
            // look for a bolus estimate
            if (bolusSource == 1) {
                object = realm.where(PumpHistoryBolus.class)
                        .equalTo("bolusRef", -1)
                        .equalTo("estimate", true)
                        .greaterThan("estimateRTC", eventRTC - 1 * 60)
                        .findFirst();
            }
            if (object == null) {
                Log.d(TAG, "*new*" + " Ref: " + bolusRef + " create new bolus event");
                object = realm.createObject(PumpHistoryBolus.class);
            } else {
                Log.d(TAG, "*update*" + " Ref: " + bolusRef + " estimate found, add bolus event");
            }
            object.setEventDate(new Date(eventDate.getTime() - 12 * 60 * 60 * 1000));
            object.setEventEndDate(new Date(eventDate.getTime() + 12 * 60 * 60 * 1000));
            object.setBolusType(bolusType);
            object.setBolusRef(bolusRef);
            object.setBolusSource(bolusSource);
            object.setPresetBolusNumber(presetBolusNumber);
            object.setActiveInsulin(activeInsulin);
        }
        if (programmed && !object.isProgrammed()) {
            Log.d(TAG, "*update*" + " Ref: " + bolusRef + " update bolus event programming");
            object.setEventDate(eventDate);
            object.setProgrammed(true);
            object.setProgrammedDate(eventDate);
            object.setProgrammedRTC(eventRTC);
            object.setProgrammedOFFSET(eventOFFSET);
            object.setNormalProgrammedAmount(normalProgrammedAmount);
            object.setSquareProgrammedAmount(squareProgrammedAmount);
            object.setSquareProgrammedDuration(squareProgrammedDuration);
            object.setKey("BOLUS" + String.format("%08X", eventRTC));
            object.setUploadREQ(true);
        }
        if (normalDelivered && !object.isNormalDelivered()) {
            Log.d(TAG, "*update*" + " Ref: " + bolusRef + " update bolus event normal delivered");
            if (bolusType == 0) object.setEventEndDate(eventDate);
            object.setNormalDelivered(true);
            object.setNormalDeliveredDate(eventDate);
            object.setNormalDeliveredRTC(eventRTC);
            object.setNormalDeliveredOFFSET(eventOFFSET);
            object.setNormalDeliveredAmount(normalDeliveredAmount);
            if (object.isProgrammed() && normalProgrammedAmount != normalDeliveredAmount) object.setUploadREQ(true);
        }
        if (squareDelivered && !object.isSquareDelivered()) {
            Log.d(TAG, "*update*" + " Ref: " + bolusRef + " update bolus event square delivered");
            object.setEventEndDate(eventDate);
            object.setSquareDelivered(true);
            object.setSquareDeliveredDate(eventDate);
            object.setSquareDeliveredRTC(eventRTC);
            object.setSquareDeliveredOFFSET(eventOFFSET);
            object.setSquareDeliveredAmount(squareDeliveredAmount);
            object.setSquareDeliveredDuration(squareDeliveredDuration);
            if (object.isProgrammed() && squareProgrammedAmount != squareDeliveredAmount) object.setUploadREQ(true);
        }
    }

    public static void estimate(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                                int bgUnits,
                                int carbUnits,
                                double bgInput,
                                double carbInput,
                                double isf,
                                double carbRatio,
                                double lowBgTarget,
                                double highBgTarget,
                                double correctionEstimate,
                                double foodEstimate,
                                double iob,
                                double iobAdjustment,
                                double bolusWizardEstimate,
                                int bolusStepSize,
                                boolean estimateModifiedByUser,
                                double finalEstimate) {

        PumpHistoryBolus object = realm.where(PumpHistoryBolus.class)
                .equalTo("estimate", true)
                .equalTo("estimateRTC", eventRTC)
                .findFirst();
        if (object == null) {
            object = realm.where(PumpHistoryBolus.class)
                    .notEqualTo("bolusRef", -1)
                    .equalTo("estimate", false)
                    .equalTo("bolusSource", 1)
                    .greaterThan("programmedRTC", eventRTC - 60)
                    .lessThan("programmedRTC", eventRTC + 60)
                    .findFirst();
            if (object == null) {
                Log.d(TAG, "*new*" + " Ref: n/a create new bolus event *estimate*");
                object = realm.createObject(PumpHistoryBolus.class);
                object.setEventDate(eventDate);
                object.setEventEndDate(new Date(eventDate.getTime() + 12 * 60 * 60 * 1000));
            } else {
                Log.d(TAG, "*update*" + " Ref: " + object.getBolusRef() + " adding estimate to bolus event");
            }
            object.setEstimate(true);
            object.setEstimateRTC(eventRTC);
            object.setEstimateOFFSET(eventOFFSET);
            object.setBgUnits(bgUnits);
            object.setCarbUnits(carbUnits);
            object.setBgInput(bgInput);
            object.setCarbInput(carbInput);
            object.setIsf(isf);
            object.setCarbRatio(carbRatio);
            object.setLowBgTarget(lowBgTarget);
            object.setHighBgTarget(highBgTarget);
            object.setCorrectionEstimate(correctionEstimate);
            object.setFoodEstimate(foodEstimate);
            object.setIob(iob);
            object.setIobAdjustment(iobAdjustment);
            object.setBolusWizardEstimate(bolusWizardEstimate);
            object.setBolusStepSize(bolusStepSize);
            object.setEstimateModifiedByUser(estimateModifiedByUser);
            object.setFinalEstimate(finalEstimate);
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

    public int getBolusType() {
        return bolusType;
    }

    public void setBolusType(int bolusType) {
        this.bolusType = bolusType;
    }

    public int getBolusSource() {
        return bolusSource;
    }

    public void setBolusSource(int bolusSource) {
        this.bolusSource = bolusSource;
    }

    public int getBolusRef() {
        return bolusRef;
    }

    public void setBolusRef(int bolusRef) {
        this.bolusRef = bolusRef;
    }

    public int getPresetBolusNumber() {
        return presetBolusNumber;
    }

    public void setPresetBolusNumber(int presetBolusNumber) {
        this.presetBolusNumber = presetBolusNumber;
    }

    public double getNormalProgrammedAmount() {
        return normalProgrammedAmount;
    }

    public void setNormalProgrammedAmount(double normalProgrammedAmount) {
        this.normalProgrammedAmount = normalProgrammedAmount;
    }

    public double getNormalDeliveredAmount() {
        return normalDeliveredAmount;
    }

    public void setNormalDeliveredAmount(double normalDeliveredAmount) {
        this.normalDeliveredAmount = normalDeliveredAmount;
    }

    public double getSquareProgrammedAmount() {
        return squareProgrammedAmount;
    }

    public void setSquareProgrammedAmount(double squareProgrammedAmount) {
        this.squareProgrammedAmount = squareProgrammedAmount;
    }

    public double getSquareDeliveredAmount() {
        return squareDeliveredAmount;
    }

    public void setSquareDeliveredAmount(double squareDeliveredAmount) {
        this.squareDeliveredAmount = squareDeliveredAmount;
    }

    public int getSquareProgrammedDuration() {
        return squareProgrammedDuration;
    }

    public void setSquareProgrammedDuration(int squareProgrammedDuration) {
        this.squareProgrammedDuration = squareProgrammedDuration;
    }

    public int getSquareDeliveredDuration() {
        return squareDeliveredDuration;
    }

    public void setSquareDeliveredDuration(int squareDeliveredDuration) {
        this.squareDeliveredDuration = squareDeliveredDuration;
    }

    public double getActiveInsulin() {
        return activeInsulin;
    }

    public void setActiveInsulin(double activeInsulin) {
        this.activeInsulin = activeInsulin;
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

    public boolean isNormalDelivered() {
        return normalDelivered;
    }

    public void setNormalDelivered(boolean normalDelivered) {
        this.normalDelivered = normalDelivered;
    }

    public int getNormalDeliveredRTC() {
        return normalDeliveredRTC;
    }

    public void setNormalDeliveredRTC(int normalDeliveredRTC) {
        this.normalDeliveredRTC = normalDeliveredRTC;
    }

    public int getNormalDeliveredOFFSET() {
        return normalDeliveredOFFSET;
    }

    public void setNormalDeliveredOFFSET(int normalDeliveredOFFSET) {
        this.normalDeliveredOFFSET = normalDeliveredOFFSET;
    }

    public Date getNormalDeliveredDate() {
        return normalDeliveredDate;
    }

    public void setNormalDeliveredDate(Date normalDeliveredDate) {
        this.normalDeliveredDate = normalDeliveredDate;
    }

    public boolean isSquareDelivered() {
        return squareDelivered;
    }

    public void setSquareDelivered(boolean squareDelivered) {
        this.squareDelivered = squareDelivered;
    }

    public int getSquareDeliveredRTC() {
        return squareDeliveredRTC;
    }

    public void setSquareDeliveredRTC(int squareDeliveredRTC) {
        this.squareDeliveredRTC = squareDeliveredRTC;
    }

    public int getSquareDeliveredOFFSET() {
        return squareDeliveredOFFSET;
    }

    public void setSquareDeliveredOFFSET(int squareDeliveredOFFSET) {
        this.squareDeliveredOFFSET = squareDeliveredOFFSET;
    }

    public Date getSquareDeliveredDate() {
        return squareDeliveredDate;
    }

    public void setSquareDeliveredDate(Date squareDeliveredDate) {
        this.squareDeliveredDate = squareDeliveredDate;
    }

    public boolean isEstimate() {
        return estimate;
    }

    public void setEstimate(boolean estimate) {
        this.estimate = estimate;
    }

    public int getEstimateRTC() {
        return estimateRTC;
    }

    public void setEstimateRTC(int estimateRTC) {
        this.estimateRTC = estimateRTC;
    }

    public int getEstimateOFFSET() {
        return estimateOFFSET;
    }

    public void setEstimateOFFSET(int estimateOFFSET) {
        this.estimateOFFSET = estimateOFFSET;
    }

    public int getBgUnits() {
        return bgUnits;
    }

    public void setBgUnits(int bgUnits) {
        this.bgUnits = bgUnits;
    }

    public int getCarbUnits() {
        return carbUnits;
    }

    public void setCarbUnits(int carbUnits) {
        this.carbUnits = carbUnits;
    }

    public double getBgInput() {
        return bgInput;
    }

    public void setBgInput(double bgInput) {
        this.bgInput = bgInput;
    }

    public double getCarbInput() {
        return carbInput;
    }

    public void setCarbInput(double carbInput) {
        this.carbInput = carbInput;
    }

    public double getIsf() {
        return isf;
    }

    public void setIsf(double isf) {
        this.isf = isf;
    }

    public double getCarbRatio() {
        return carbRatio;
    }

    public void setCarbRatio(double carbRatio) {
        this.carbRatio = carbRatio;
    }

    public double getLowBgTarget() {
        return lowBgTarget;
    }

    public void setLowBgTarget(double lowBgTarget) {
        this.lowBgTarget = lowBgTarget;
    }

    public double getHighBgTarget() {
        return highBgTarget;
    }

    public void setHighBgTarget(double highBgTarget) {
        this.highBgTarget = highBgTarget;
    }

    public double getCorrectionEstimate() {
        return correctionEstimate;
    }

    public void setCorrectionEstimate(double correctionEstimate) {
        this.correctionEstimate = correctionEstimate;
    }

    public double getFoodEstimate() {
        return foodEstimate;
    }

    public void setFoodEstimate(double foodEstimate) {
        this.foodEstimate = foodEstimate;
    }

    public double getIob() {
        return iob;
    }

    public void setIob(double iob) {
        this.iob = iob;
    }

    public double getIobAdjustment() {
        return iobAdjustment;
    }

    public void setIobAdjustment(double iobAdjustment) {
        this.iobAdjustment = iobAdjustment;
    }

    public double getBolusWizardEstimate() {
        return bolusWizardEstimate;
    }

    public void setBolusWizardEstimate(double bolusWizardEstimate) {
        this.bolusWizardEstimate = bolusWizardEstimate;
    }

    public int getBolusStepSize() {
        return bolusStepSize;
    }

    public void setBolusStepSize(int bolusStepSize) {
        this.bolusStepSize = bolusStepSize;
    }

    public boolean isEstimateModifiedByUser() {
        return estimateModifiedByUser;
    }

    public void setEstimateModifiedByUser(boolean estimateModifiedByUser) {
        this.estimateModifiedByUser = estimateModifiedByUser;
    }

    public double getFinalEstimate() {
        return finalEstimate;
    }

    public void setFinalEstimate(double finalEstimate) {
        this.finalEstimate = finalEstimate;
    }
}


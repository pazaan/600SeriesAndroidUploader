package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

public class PumpHistoryBolus extends RealmObject implements PumpHistoryInterface {
    @Ignore
    private static final String TAG = PumpHistoryBolus.class.getSimpleName();

    @Index
    private Date eventDate;

    @Index
    private boolean uploadREQ = false;
    private boolean uploadACK = false;

    private boolean xdripREQ = false;
    private boolean xdripACK = false;

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    private int bolusRef = -1;

    private int bolusType; // normal=0 square=1 dual=2
    private int bolusPreset;
    private int bolusSource;

    private double totalProgrammedAmount;

    private double normalProgrammedAmount;
    private double normalDeliveredAmount;
    private double squareProgrammedAmount;
    private double squareDeliveredAmount;
    private int squareProgrammedDuration;
    private int squareDeliveredDuration;
    private double activeInsulin;

    @Index
    private int programmedRTC;
    private int programmedOFFSET;
    private Date programmedDate;
    private boolean programmed = false;

    @Index
    private int normalDeliveredRTC;
    private int normalDeliveredOFFSET;
    private Date normalDeliveredDate;
    private boolean normalDelivered = false;

    @Index
    private int squareDeliveredRTC;
    private int squareDeliveredOFFSET;
    private Date squareDeliveredDate;
    private boolean squareDelivered = false;

    @Index
    private int estimateRTC;
    private int estimateOFFSET;
    private boolean estimate = false;

    private int bgUnits;
    private int carbUnits;
    private int bolusStepSize;
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
    private double finalEstimate;
    private boolean estimateModifiedByUser;

    @Override
    public List nightscout(DataStore dataStore) {
        List<UploadItem> uploadItems = new ArrayList<>();

        if (dataStore.isNsEnableTreatments()) {

            UploadItem uploadItem = new UploadItem();
            uploadItems.add(uploadItem);
            TreatmentsEndpoints.Treatment treatment = uploadItem.ack(uploadACK).treatment();

            String notes = "";

            treatment.setCreated_at(programmedDate);
            treatment.setKey600(key);

            if (!PumpHistoryParser.BOLUS_PRESET.BOLUS_PRESET_0.equals(bolusPreset)) {
                notes += "[" + dataStore.getNameBolusPreset(bolusPreset) + "] ";
            }

            if (PumpHistoryParser.BOLUS_TYPE.NORMAL_BOLUS.equals(bolusType)) {
                treatment.setEventType("Bolus");
                treatment.setInsulin((float) normalProgrammedAmount);

            } else if (PumpHistoryParser.BOLUS_TYPE.SQUARE_WAVE.equals(bolusType)) {
                treatment.setEventType("Combo Bolus");
                treatment.setEnteredinsulin(String.valueOf(squareProgrammedAmount));
                treatment.setDuration((float) squareProgrammedDuration);
                treatment.setSplitNow("0");
                treatment.setSplitExt("100");
                treatment.setRelative((float) 2);
                notes += "Square Bolus: " + squareProgrammedAmount + "U, duration " + squareProgrammedDuration + " minutes";

            } else if (PumpHistoryParser.BOLUS_TYPE.DUAL_WAVE.equals(bolusType)) {
                treatment.setEventType("Combo Bolus");
                treatment.setEnteredinsulin(String.valueOf(normalProgrammedAmount + squareProgrammedAmount));
                treatment.setDuration((float) squareProgrammedDuration);
                treatment.setInsulin((float) normalProgrammedAmount);
                int splitNow = (int) (normalProgrammedAmount * (100 / (normalProgrammedAmount + squareProgrammedAmount)));
                int splitExt = 100 - splitNow;
                treatment.setSplitNow(String.valueOf(splitNow));
                treatment.setSplitExt(String.valueOf(splitExt));
                treatment.setRelative((float) 2);
                notes += "Dual Bolus: normal " + normalProgrammedAmount + "U, square " + squareProgrammedAmount + "U, duration " + squareProgrammedDuration + " minutes";

            } else {
                treatment.setEventType("Note");
                notes = "Unknown event";
            }

            if (normalDelivered && normalProgrammedAmount != normalDeliveredAmount) {
                treatment.setInsulin((float) normalDeliveredAmount);
                if (PumpHistoryParser.BOLUS_TYPE.NORMAL_BOLUS.equals(bolusType))
                    notes += "Normal Bolus: " + normalProgrammedAmount + "U";
                notes += " * cancelled, delivered " + normalDeliveredAmount + "U";
                uploadItem.update();

            } else if (squareDelivered && squareProgrammedAmount != squareDeliveredAmount) {
                treatment.setEnteredinsulin(String.valueOf(normalDeliveredAmount + squareDeliveredAmount));
                treatment.setDuration((float) squareDeliveredDuration);
                treatment.setInsulin((float) normalDeliveredAmount);
                int splitNow = (int) (normalDeliveredAmount * (100 / (normalDeliveredAmount + squareDeliveredAmount)));
                int splitExt = 100 - splitNow;
                treatment.setSplitNow(String.valueOf(splitNow));
                treatment.setSplitExt(String.valueOf(splitExt));
                treatment.setRelative((float) 2);
                notes += " * ended before expected duration, square delivered " + squareDeliveredAmount + "U in " + squareDeliveredDuration + " minutes";
                uploadItem.update();
            }

            if (estimate) {
                // only change NS event type when dual/square (combo) is not in use
                if (PumpHistoryParser.BOLUS_TYPE.NORMAL_BOLUS.equals(bolusType))
                    treatment.setEventType("Meal Bolus");

                // conversion for unit type
                double carbInputAsGrams;
                double carbRatioAsGrams;
                String exchanges;
                int gramsPerExchange = dataStore.getNsGramsPerExchange();
                if (PumpHistoryParser.CARB_UNITS.EXCHANGES.equals(carbUnits)) {
                    carbInputAsGrams = gramsPerExchange * carbInput;
                    carbRatioAsGrams = gramsPerExchange / carbRatio;
                    exchanges = String.format(Locale.US, ", ex %.2fu/%sg", carbRatio, gramsPerExchange);
                } else {
                    carbInputAsGrams = carbInput;
                    carbRatioAsGrams = carbRatio;
                    exchanges = "";
                }

                treatment.setCarbs((float) carbInputAsGrams);

                if (!notes.equals("")) notes += " : ";
                //String wiztag = "WIZ: ";
                String wiztag = "";

                switch (PumpHistoryParser.BOLUS_SOURCE.convert(bolusSource)) {
                    case BOLUS_WIZARD:
                        // for mg/dl remove the decimal placing ie. "123.0" = "123"
                        if (PumpHistoryParser.BG_UNITS.MG_DL.equals(bgUnits))
                            notes += wiztag + "carb %.0fg %.1fU, {%.0f~%.0f} %.1fU, iob %.1f %.1fU (%.0fg/u, isf %.0f/u" + exchanges + ")";
                        else
                            notes += wiztag + "carb %.0fg %.1fU, {%.1f~%.1f} %.1fU, iob %.1f %.1fU (%.0fg/u, isf %.1f/u" + exchanges + ")";
                        notes = String.format(Locale.US, notes,
                                carbInputAsGrams,
                                foodEstimate,
                                lowBgTarget,
                                highBgTarget,
                                correctionEstimate,
                                iob,
                                iobAdjustment > 0 ? -iobAdjustment : iobAdjustment,
                                carbRatioAsGrams,
                                isf);
                        break;
                    case CLOSED_LOOP_FOOD_BOLUS:
                        notes += String.format(Locale.US,
                                wiztag + "carb %.0fg %.1fU (%.0fg/u" + exchanges + ")",
                                carbInputAsGrams,
                                foodEstimate,
                                carbRatioAsGrams);
                        break;
                    case CLOSED_LOOP_BG_CORRECTION:
                        notes += String.format(Locale.US,
                                wiztag + "correction %.1fU",
                                correctionEstimate);
                        break;
                    case CLOSED_LOOP_BG_CORRECTION_AND_FOOD_BOLUS:
                        notes += String.format(Locale.US,
                                wiztag + "carb %.0fg %.1fU, correction %.1fU (%.0fg/u" + exchanges + ")",
                                carbInputAsGrams,
                                foodEstimate,
                                correctionEstimate,
                                carbRatioAsGrams);
                        break;
                }

            }

            //notes += " [DEBUG: source=" + bolusSource + " ref=" + bolusRef + " rtc=" + String.format("%08X", programmedRTC) + " correction=" + correctionEstimate +  " final=" + finalEstimate + "]";

            // nightscout does not have a square bolus type so a combo type is used but due to no normal bolus part
            // there is no tag shown in the main graph, a note is sent to NS to compensate for this
            // only needed when no carbs / wizard in use
            if (PumpHistoryParser.BOLUS_TYPE.SQUARE_WAVE.equals(bolusType) && carbInput == 0) {
                UploadItem uploadItem2 = new UploadItem();
                uploadItems.add(uploadItem2);
                TreatmentsEndpoints.Treatment treatment2 = uploadItem2.treatment();
                uploadItem2.mode(uploadItem.getMode());
                treatment2.setEventType("Note");
                treatment2.setKey600(key + "NOTE");
                treatment2.setCreated_at(programmedDate);
                treatment2.setNotes(notes);
            } else
                treatment.setNotes(notes);
        }

        return uploadItems;
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
                .greaterThanOrEqualTo("programmedRTC", eventRTC - (8 * 60 * 60))
                .lessThanOrEqualTo("programmedRTC", eventRTC + (8 * 60 * 60))
                .endGroup()
                .or()
                .beginGroup()
                .equalTo("bolusRef", bolusRef)
                .greaterThanOrEqualTo("normalDeliveredRTC", eventRTC - (8 * 60 * 60))
                .lessThanOrEqualTo("normalDeliveredRTC", eventRTC + (8 * 60 * 60))
                .endGroup()
                .or()
                .beginGroup()
                .equalTo("bolusRef", bolusRef)
                .greaterThanOrEqualTo("squareDeliveredRTC", eventRTC - (8 * 60 * 60))
                .lessThanOrEqualTo("squareDeliveredRTC", eventRTC + (8 * 60 * 60))
                .endGroup()
                .findFirst();

        if (object == null) {
            // look for a bolus estimate
            if (PumpHistoryParser.BOLUS_SOURCE.BOLUS_WIZARD.equals(bolusSource)
                    || PumpHistoryParser.BOLUS_SOURCE.CLOSED_LOOP_BG_CORRECTION.equals(bolusSource)
                    || PumpHistoryParser.BOLUS_SOURCE.CLOSED_LOOP_FOOD_BOLUS.equals(bolusSource)
                    || PumpHistoryParser.BOLUS_SOURCE.CLOSED_LOOP_BG_CORRECTION_AND_FOOD_BOLUS.equals(bolusSource)) {
                object = realm.where(PumpHistoryBolus.class)
                        .equalTo("bolusRef", -1)
                        .equalTo("estimate", true)
                        .equalTo("finalEstimate", normalProgrammedAmount + squareProgrammedAmount)
                        .greaterThan("estimateRTC", eventRTC - (5 * 60))
                        .lessThanOrEqualTo("estimateRTC", eventRTC)
                        .findFirst();
            }
            if (object == null) {
                Log.d(TAG, "*new*" + " Ref: " + bolusRef + " create new bolus event");
                object = realm.createObject(PumpHistoryBolus.class);
            } else {
                Log.d(TAG, "*update*" + " Ref: " + bolusRef + " estimate found, add bolus event");
            }
            object.setEventDate(eventDate);
            object.setBolusType(bolusType);
            object.setBolusRef(bolusRef);
            object.setBolusSource(bolusSource);
            object.setBolusPreset(presetBolusNumber);
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
            object.setTotalProgrammedAmount(normalProgrammedAmount + squareProgrammedAmount);
            object.setSquareProgrammedDuration(squareProgrammedDuration);
            object.setKey("BOLUS" + String.format("%08X", eventRTC));
            object.setUploadREQ(true);
        }

        if (normalDelivered && !object.isNormalDelivered()) {
            Log.d(TAG, "*update*" + " Ref: " + bolusRef + " update bolus event normal delivered");
            object.setNormalDelivered(true);
            object.setNormalDeliveredDate(eventDate);
            object.setNormalDeliveredRTC(eventRTC);
            object.setNormalDeliveredOFFSET(eventOFFSET);
            object.setNormalDeliveredAmount(normalDeliveredAmount);
            if (object.isProgrammed() && normalProgrammedAmount != normalDeliveredAmount) object.setUploadREQ(true);
        }

        if (squareDelivered && !object.isSquareDelivered()) {
            Log.d(TAG, "*update*" + " Ref: " + bolusRef + " update bolus event square delivered");
            object.setSquareDelivered(true);
            object.setSquareDeliveredDate(eventDate);
            object.setSquareDeliveredRTC(eventRTC);
            object.setSquareDeliveredOFFSET(eventOFFSET);
            object.setSquareDeliveredAmount(squareDeliveredAmount);
            object.setSquareDeliveredDuration(squareDeliveredDuration);
            if (object.isProgrammed() && squareProgrammedAmount != squareDeliveredAmount) object.setUploadREQ(true);
        }
    }

    public static void bolusWizardEstimate(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
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

        // unused or canceled wizard
        if (finalEstimate == 0) return;

        PumpHistoryBolus object = realm.where(PumpHistoryBolus.class)
                .equalTo("estimate", true)
                .equalTo("estimateRTC", eventRTC)
                .findFirst();
        if (object == null) {
            // look for a corresponding programmed bolus
            object = realm.where(PumpHistoryBolus.class)
                    .notEqualTo("bolusRef", -1)
                    .equalTo("estimate", false)
                    .equalTo("totalProgrammedAmount", finalEstimate)
                    .greaterThan("programmedRTC", eventRTC - 60)
                    .lessThan("programmedRTC", eventRTC + (5 * 60))
                    .beginGroup()
                    .equalTo("bolusSource", PumpHistoryParser.BOLUS_SOURCE.BOLUS_WIZARD.get())
                    .or()
                    .equalTo("bolusSource", PumpHistoryParser.BOLUS_SOURCE.CLOSED_LOOP_BG_CORRECTION.get())
                    .or()
                    .equalTo("bolusSource", PumpHistoryParser.BOLUS_SOURCE.CLOSED_LOOP_FOOD_BOLUS.get())
                    .or()
                    .equalTo("bolusSource", PumpHistoryParser.BOLUS_SOURCE.CLOSED_LOOP_BG_CORRECTION_AND_FOOD_BOLUS.get())
                    .endGroup()
                    .findFirst();
            if (object == null) {
                Log.d(TAG, "*new*" + " Ref: n/a create new bolus event *estimate*");
                object = realm.createObject(PumpHistoryBolus.class);
                object.setEventDate(eventDate);
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

    public int getBolusRef() {
        return bolusRef;
    }

    public void setBolusRef(int bolusRef) {
        this.bolusRef = bolusRef;
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

    public int getBolusPreset() {
        return bolusPreset;
    }

    public void setBolusPreset(int bolusPreset) {
        this.bolusPreset = bolusPreset;
    }

    public double getTotalProgrammedAmount() {
        return totalProgrammedAmount;
    }

    public void setTotalProgrammedAmount(double totalProgrammedAmount) {
        this.totalProgrammedAmount = totalProgrammedAmount;
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

    public int getBolusStepSize() {
        return bolusStepSize;
    }

    public void setBolusStepSize(int bolusStepSize) {
        this.bolusStepSize = bolusStepSize;
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

    public double getFinalEstimate() {
        return finalEstimate;
    }

    public void setFinalEstimate(double finalEstimate) {
        this.finalEstimate = finalEstimate;
    }

    public boolean isEstimateModifiedByUser() {
        return estimateModifiedByUser;
    }

    public void setEstimateModifiedByUser(boolean estimateModifiedByUser) {
        this.estimateModifiedByUser = estimateModifiedByUser;
    }
}
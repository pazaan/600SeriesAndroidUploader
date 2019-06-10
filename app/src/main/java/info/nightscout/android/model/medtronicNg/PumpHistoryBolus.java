package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.android.R;
import info.nightscout.android.history.HistoryUtils;
import info.nightscout.android.history.MessageItem;
import info.nightscout.android.history.PumpHistoryParser;
import info.nightscout.android.history.PumpHistorySender;
import info.nightscout.android.utils.FormatKit;
import info.nightscout.android.upload.nightscout.TreatmentsEndpoints;
import info.nightscout.android.history.NightscoutItem;
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
    private String senderREQ = "";
    @Index
    private String senderACK = "";
    @Index
    private String senderDEL = "";

    @Index
    private Date eventDate;
    @Index
    private long pumpMAC;

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    @Index
    private int bolusRef = -1;

    private byte bolusType;
    private byte bolusPreset;
    @Index
    private byte bolusSource;

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
    @Index
    private boolean estimate = false;

    private byte bgUnits;
    private byte carbUnits;
    private byte bolusStepSize;
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
    public List<NightscoutItem> nightscout(PumpHistorySender pumpHistorySender, String senderID) {
        List<NightscoutItem> nightscoutItems = new ArrayList<>();

        if (!pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.BOLUS)) {
            HistoryUtils.nightscoutDeleteTreatment(nightscoutItems, this, senderID);
            return nightscoutItems;
        }

        TreatmentsEndpoints.Treatment treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID, programmedDate);

        String formatSeperator = pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.FORMAT_HTML) ? " <br>" : " ";
        StringBuilder notes = new StringBuilder();

        if (!PumpHistoryParser.BOLUS_PRESET.BOLUS_PRESET_0.equals(bolusPreset)) {
            notes.append(String.format("[%s]",
                    pumpHistorySender.getList(senderID, PumpHistorySender.SENDEROPT.BOLUS_PRESET, bolusPreset - 1)));
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
            treatment.setRelative((float) ((squareProgrammedAmount * 60) / squareProgrammedDuration));
            notes.append(String.format("%s%s %s %s %s",
                    notes.length() == 0 ? "" : " ",
                    FormatKit.getInstance().getString(R.string.text__Square_Bolus),
                    FormatKit.getInstance().formatAsInsulin(squareProgrammedAmount),
                    FormatKit.getInstance().getString(R.string.text__duration),
                    FormatKit.getInstance().formatMinutesAsHM(squareProgrammedDuration)));

        } else if (PumpHistoryParser.BOLUS_TYPE.DUAL_WAVE.equals(bolusType)) {
            treatment.setEventType("Combo Bolus");
            treatment.setEnteredinsulin(String.valueOf(normalProgrammedAmount + squareProgrammedAmount));
            treatment.setDuration((float) squareProgrammedDuration);
            treatment.setInsulin((float) normalProgrammedAmount);
            int splitNow = (int) (normalProgrammedAmount * (100 / (normalProgrammedAmount + squareProgrammedAmount)));
            int splitExt = 100 - splitNow;
            treatment.setSplitNow(String.valueOf(splitNow));
            treatment.setSplitExt(String.valueOf(splitExt));
            treatment.setRelative((float) ((squareProgrammedAmount * 60) / squareProgrammedDuration));
            notes.append(String.format("%s%s %s : %s %s %s",
                    notes.length() == 0 ? "" : " ",
                    FormatKit.getInstance().getString(R.string.text__Dual_Bolus),
                    FormatKit.getInstance().formatAsInsulin(normalProgrammedAmount),
                    FormatKit.getInstance().formatAsInsulin(squareProgrammedAmount),
                    FormatKit.getInstance().getString(R.string.text__duration),
                    FormatKit.getInstance().formatMinutesAsHM(squareProgrammedDuration)));

        } else {
            Log.e(TAG, "unknown bolus event");
            nightscoutItems.clear();
            return nightscoutItems;
        }

        if (normalDelivered && normalProgrammedAmount != normalDeliveredAmount) {
            treatment.setInsulin((float) normalDeliveredAmount);
            // normal programmed
            if (PumpHistoryParser.BOLUS_TYPE.NORMAL_BOLUS.equals(bolusType))
                notes.append(String.format("%s%s %s",
                        notes.length() == 0 ? "" : " ",
                        FormatKit.getInstance().getString(R.string.text__Normal_Bolus),
                        FormatKit.getInstance().formatAsInsulin(normalProgrammedAmount)));
                // dual programmed but cancelled during delivery of normal part
            else if (PumpHistoryParser.BOLUS_TYPE.DUAL_WAVE.equals(bolusType)) {
                treatment.setEnteredinsulin(String.valueOf(normalDeliveredAmount));
                treatment.setDuration((float) 0);
                treatment.setSplitNow("100");
                treatment.setSplitExt("0");
                treatment.setRelative((float) 0);
            }
            notes.append(String.format("%s* %s: %s %s",
                    notes.length() == 0 ? "" : formatSeperator,
                    FormatKit.getInstance().getString(R.string.text__cancelled),
                    FormatKit.getInstance().getString(R.string.text__delivered),
                    FormatKit.getInstance().formatAsInsulin(normalDeliveredAmount)));
            // always write a canceled bolus to NS
            nightscoutItems.get(0).update();

        } else if (squareDelivered && squareProgrammedAmount != squareDeliveredAmount) {
            treatment.setEnteredinsulin(String.valueOf(normalDeliveredAmount + squareDeliveredAmount));
            treatment.setDuration((float) squareDeliveredDuration);
            treatment.setInsulin((float) normalDeliveredAmount);
            int splitNow = (int) (normalDeliveredAmount * (100 / (normalDeliveredAmount + squareDeliveredAmount)));
            int splitExt = 100 - splitNow;
            treatment.setSplitNow(String.valueOf(splitNow));
            treatment.setSplitExt(String.valueOf(splitExt));
            treatment.setRelative((float) ((squareDeliveredAmount * 60) / squareDeliveredDuration));
            notes.append(String.format("%s* %s: %s %s : %s %s %s",
                    notes.length() == 0 ? "" : formatSeperator,
                    FormatKit.getInstance().getString(R.string.text__cancelled),
                    FormatKit.getInstance().getString(R.string.text__delivered),
                    FormatKit.getInstance().formatAsInsulin(normalDeliveredAmount),
                    FormatKit.getInstance().formatAsInsulin(squareDeliveredAmount),
                    FormatKit.getInstance().getString(R.string.text__duration),
                    FormatKit.getInstance().formatMinutesAsHM(squareDeliveredDuration)));
            // always write a canceled bolus to NS
            nightscoutItems.get(0).update();
        }

        if (estimate) {

            // conversion for unit type
            double carbInputAsGrams;
            double carbRatioAsGrams;
            double gramsPerExchange = Double.parseDouble(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.GRAMS_PER_EXCHANGE, "15"));
            String gramsPerU;
            if (PumpHistoryParser.CARB_UNITS.EXCHANGES.equals(carbUnits)) {
                carbInputAsGrams = gramsPerExchange * carbInput;
                carbRatioAsGrams = gramsPerExchange / carbRatio;
                gramsPerU = String.format("(%s/%s %s/%s/%s)",
                        FormatKit.getInstance().formatAsGrams(carbRatioAsGrams),
                        FormatKit.getInstance().getString(R.string.insulin_U),
                        FormatKit.getInstance().formatAsInsulin(carbRatio),
                        FormatKit.getInstance().formatAsGrams(gramsPerExchange),
                        FormatKit.getInstance().getString(R.string.gram_exchange_ex));
            } else {
                carbInputAsGrams = carbInput;
                carbRatioAsGrams = carbRatio;
                gramsPerU = String.format("(%s/%s)",
                        FormatKit.getInstance().formatAsGrams(carbRatioAsGrams),
                        FormatKit.getInstance().getString(R.string.insulin_U));
            }

            // only change NS event type when dual/square (combo) is not in use
            if (PumpHistoryParser.BOLUS_TYPE.NORMAL_BOLUS.equals(bolusType))
                if (carbInputAsGrams != 0 || foodEstimate != 0)
                    treatment.setEventType("Meal Bolus");
                else
                    treatment.setEventType("Correction Bolus");

            treatment.setCarbs((float) carbInputAsGrams);

            switch (PumpHistoryParser.BOLUS_SOURCE.convert(bolusSource)) {
                case BOLUS_WIZARD:
                    if (carbInputAsGrams != 0 || foodEstimate != 0) {
                        notes.append(String.format("%s%s %s %s %s",
                                notes.length() == 0 ? "" : formatSeperator,
                                FormatKit.getInstance().getString(R.string.text__carb),
                                FormatKit.getInstance().formatAsGrams(carbInputAsGrams),
                                FormatKit.getInstance().formatAsInsulin(foodEstimate),
                                gramsPerU));
                    }
                    if (bgInput != 0 || correctionEstimate != 0) {
                        notes.append(String.format("%s%s %s %s (%s~%s %s/%s)",
                                notes.length() == 0 ? "" : formatSeperator,
                                FormatKit.getInstance().getString(R.string.text__bg),
                                FormatKit.getInstance().formatAsDecimal(bgInput, PumpHistoryParser.BG_UNITS.MMOL_L.equals(bgUnits) ? 1 : 0),
                                FormatKit.getInstance().formatAsInsulin(correctionEstimate),
                                FormatKit.getInstance().formatAsDecimal(lowBgTarget, PumpHistoryParser.BG_UNITS.MMOL_L.equals(bgUnits) ? 1 : 0),
                                FormatKit.getInstance().formatAsDecimal(highBgTarget, PumpHistoryParser.BG_UNITS.MMOL_L.equals(bgUnits) ? 1 : 0),
                                FormatKit.getInstance().formatAsDecimal(isf, PumpHistoryParser.BG_UNITS.MMOL_L.equals(bgUnits) ? 1 : 0),
                                FormatKit.getInstance().getString(R.string.insulin_U)));
                    }
                    if (iobAdjustment != 0) {
                        notes.append(String.format("%s%s %s %s",
                                notes.length() == 0 ? "" : formatSeperator,
                                FormatKit.getInstance().getString(R.string.text__iob),
                                FormatKit.getInstance().formatAsInsulin(iob),
                                FormatKit.getInstance().formatAsInsulin(iobAdjustment > 0 ? -iobAdjustment : iobAdjustment)));
                    }
                    break;
                case CLOSED_LOOP_FOOD_BOLUS:
                    notes.append(String.format("%s%s %s %s %s",
                            notes.length() == 0 ? "" : formatSeperator,
                            FormatKit.getInstance().getString(R.string.text__carb),
                            FormatKit.getInstance().formatAsGrams(carbInputAsGrams),
                            FormatKit.getInstance().formatAsInsulin(foodEstimate),
                            gramsPerU));
                    break;
                case CLOSED_LOOP_BG_CORRECTION:
                    notes.append(String.format("%s%s %s",
                            notes.length() == 0 ? "" : formatSeperator,
                            FormatKit.getInstance().getString(R.string.text__correction),
                            FormatKit.getInstance().formatAsInsulin(correctionEstimate)));
                    break;
                case CLOSED_LOOP_BG_CORRECTION_AND_FOOD_BOLUS:
                    notes.append(String.format("%s%s %s %s %s%s%s %s",
                            notes.length() == 0 ? "" : formatSeperator,
                            FormatKit.getInstance().getString(R.string.text__carb),
                            FormatKit.getInstance().formatAsGrams(carbInputAsGrams),
                            FormatKit.getInstance().formatAsInsulin(foodEstimate),
                            gramsPerU,
                            formatSeperator,
                            FormatKit.getInstance().getString(R.string.text__correction),
                            FormatKit.getInstance().formatAsInsulin(correctionEstimate)));
                    break;
            }

        }

        // nightscout does not have a square bolus type so a combo type is used but due to no normal bolus part
        // there is no tag shown in the main graph, a note is sent to NS to compensate for this
        // only needed when no carbs / wizard in use
        if (PumpHistoryParser.BOLUS_TYPE.SQUARE_WAVE.equals(bolusType) && carbInput == 0) {
            treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID, programmedDate);
            treatment.setEventType("Note");
            treatment.setKey600(key + "NOTE");
        }

        treatment.setNotes(notes.toString());

        return nightscoutItems;
    }

    @Override
    public List<MessageItem> message(PumpHistorySender pumpHistorySender, String senderID) {
        List<MessageItem> messageItems = new ArrayList<>();

        Date date;
        String title;
        String message = "";

        if (normalDelivered && normalProgrammedAmount != normalDeliveredAmount) {
            date = normalDeliveredDate;
            title = FormatKit.getInstance().getString(R.string.text__Bolus);
            message += String.format("%s: %s %s",
                    FormatKit.getInstance().getString(R.string.text__cancelled),
                    FormatKit.getInstance().getString(R.string.text__delivered),
                    FormatKit.getInstance().formatAsInsulin(normalDeliveredAmount));

        } else if (normalDelivered && squareDelivered && squareProgrammedAmount != squareDeliveredAmount) {
            date = squareDeliveredDate;
            title = FormatKit.getInstance().getString(R.string.text__Bolus);
            message += String.format("%s: %s %s : %s %s %s",
                    FormatKit.getInstance().getString(R.string.text__cancelled),
                    FormatKit.getInstance().getString(R.string.text__delivered),
                    FormatKit.getInstance().formatAsInsulin(normalDeliveredAmount),
                    FormatKit.getInstance().formatAsInsulin(squareDeliveredAmount),
                    FormatKit.getInstance().getString(R.string.text__duration),
                    FormatKit.getInstance().formatMinutesAsHM(squareDeliveredDuration));

        } else if (squareDelivered && squareProgrammedAmount != squareDeliveredAmount) {
            date = squareDeliveredDate;
            title = FormatKit.getInstance().getString(R.string.text__Bolus);
            message += String.format("%s: %s %s %s %s",
                    FormatKit.getInstance().getString(R.string.text__cancelled),
                    FormatKit.getInstance().getString(R.string.text__delivered),
                    FormatKit.getInstance().formatAsInsulin(squareDeliveredAmount),
                    FormatKit.getInstance().getString(R.string.text__duration),
                    FormatKit.getInstance().formatMinutesAsHM(squareDeliveredDuration));
        } else {
            date = programmedDate;
            title = FormatKit.getInstance().getString(R.string.text__Bolus);

            if (estimate) {
                message = String.format("%s %s ",
                        FormatKit.getInstance().getString(R.string.daily_totals_heading__food),
                        PumpHistoryParser.CARB_UNITS.EXCHANGES.equals(carbUnits) ?
                                FormatKit.getInstance().formatAsExchanges(carbInput) :
                                FormatKit.getInstance().formatAsGrams(carbInput));
            }

            if (PumpHistoryParser.BOLUS_TYPE.SQUARE_WAVE.equals(bolusType)) {
                message += String.format("%s %s %s %s%s",
                        FormatKit.getInstance().getString(R.string.text__Square),
                        FormatKit.getInstance().formatAsInsulin(squareProgrammedAmount),
                        FormatKit.getInstance().getString(R.string.text__duration),
                        FormatKit.getInstance().formatMinutesAsHM(squareProgrammedDuration),
                        squareDelivered ? String.format(" (%s)",
                                FormatKit.getInstance().getString(R.string.text__completed)) : ""
                );
            } else if (PumpHistoryParser.BOLUS_TYPE.DUAL_WAVE.equals(bolusType)) {
                message += String.format("%s %s : %s %s %s%s",
                        FormatKit.getInstance().getString(R.string.text__Dual),
                        FormatKit.getInstance().formatAsInsulin(normalProgrammedAmount),
                        FormatKit.getInstance().formatAsInsulin(squareProgrammedAmount),
                        FormatKit.getInstance().getString(R.string.text__duration),
                        FormatKit.getInstance().formatMinutesAsHM(squareProgrammedDuration),
                        squareDelivered ? String.format(" (%s)",
                                FormatKit.getInstance().getString(R.string.text__completed)) : ""
                );
            } else {
                message += String.format("%s %s",
                        FormatKit.getInstance().getString(R.string.text__Normal),
                        FormatKit.getInstance().formatAsInsulin(normalProgrammedAmount));
            }
        }

        messageItems.add(new MessageItem()
                .type(MessageItem.TYPE.BOLUS)
                .date(date)
                .clock(FormatKit.getInstance().formatAsClock(date.getTime()))
                .title(title)
                .message(message));

        return messageItems;
    }

    public static void bolus(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            byte bolusType,
            boolean programmed, boolean normalDelivered, boolean squareDelivered,
            int bolusRef,
            byte bolusSource,
            byte presetBolusNumber,
            double normalProgrammedAmount, double normalDeliveredAmount,
            double squareProgrammedAmount, double squareDeliveredAmount,
            int squareProgrammedDuration, int squareDeliveredDuration,
            double activeInsulin) {

        PumpHistoryBolus record = realm.where(PumpHistoryBolus.class)
                .beginGroup()
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("bolusRef", bolusRef)
                .greaterThanOrEqualTo("programmedRTC", HistoryUtils.offsetRTC(eventRTC, -8 * 60 * 60))
                .lessThanOrEqualTo("programmedRTC", HistoryUtils.offsetRTC(eventRTC, 8 * 60 * 60))
                .endGroup()
                .or()
                .beginGroup()
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("bolusRef", bolusRef)
                .greaterThanOrEqualTo("normalDeliveredRTC", HistoryUtils.offsetRTC(eventRTC, -8 * 60 * 60))
                .lessThanOrEqualTo("normalDeliveredRTC", HistoryUtils.offsetRTC(eventRTC, 8 * 60 * 60))
                .endGroup()
                .or()
                .beginGroup()
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("bolusRef", bolusRef)
                .greaterThanOrEqualTo("squareDeliveredRTC", HistoryUtils.offsetRTC(eventRTC, -8 * 60 * 60))
                .lessThanOrEqualTo("squareDeliveredRTC", HistoryUtils.offsetRTC(eventRTC, 8 * 60 * 60))
                .endGroup()
                .findFirst();

        if (record == null) {
            // look for a bolus estimate
            if (PumpHistoryParser.BOLUS_SOURCE.BOLUS_WIZARD.equals(bolusSource)
                    || PumpHistoryParser.BOLUS_SOURCE.CLOSED_LOOP_BG_CORRECTION.equals(bolusSource)
                    || PumpHistoryParser.BOLUS_SOURCE.CLOSED_LOOP_FOOD_BOLUS.equals(bolusSource)
                    || PumpHistoryParser.BOLUS_SOURCE.CLOSED_LOOP_BG_CORRECTION_AND_FOOD_BOLUS.equals(bolusSource)) {
                record = realm.where(PumpHistoryBolus.class)
                        .equalTo("pumpMAC", pumpMAC)
                        .equalTo("bolusRef", -1)
                        .equalTo("estimate", true)
                        .equalTo("finalEstimate", normalProgrammedAmount + squareProgrammedAmount)
                        .greaterThan("estimateRTC", HistoryUtils.offsetRTC(eventRTC, -5 * 60))
                        .lessThanOrEqualTo("estimateRTC", eventRTC)
                        .findFirst();
            }
            if (record == null) {
                Log.d(TAG, "*new*" + " Ref: " + bolusRef + " create new bolus event");
                record = realm.createObject(PumpHistoryBolus.class);
                record.pumpMAC = pumpMAC;
            } else {
                Log.d(TAG, "*update*" + " Ref: " + bolusRef + " estimate found, add bolus event");
            }
            record.eventDate = eventDate;
            record.bolusType = bolusType;
            record.bolusRef = bolusRef;
            record.bolusSource = bolusSource;
            record.bolusPreset = presetBolusNumber;
            record.activeInsulin = activeInsulin;
        }

        if (programmed && !record.isProgrammed()) {
            Log.d(TAG, "*update*" + " Ref: " + bolusRef + " update bolus event programming");
            record.eventDate = eventDate;
            record.programmed = true;
            record.programmedDate = eventDate;
            record.programmedRTC = eventRTC;
            record.programmedOFFSET = eventOFFSET;
            record.normalProgrammedAmount = normalProgrammedAmount;
            record.squareProgrammedAmount = squareProgrammedAmount;
            record.totalProgrammedAmount = normalProgrammedAmount + squareProgrammedAmount;
            record.squareProgrammedDuration = squareProgrammedDuration;
            record.key = HistoryUtils.key("BOLUS", eventRTC);
            pumpHistorySender.setSenderREQ(record);
        }

        if (normalDelivered && !record.isNormalDelivered()) {
            Log.d(TAG, "*update*" + " Ref: " + bolusRef + " update bolus event normal delivered");
            record.normalDelivered = true;
            record.normalDeliveredDate = eventDate;
            record.normalDeliveredRTC = eventRTC;
            record.normalDeliveredOFFSET = eventOFFSET;
            record.normalDeliveredAmount = normalDeliveredAmount;
            if (record.programmed && normalProgrammedAmount != normalDeliveredAmount)
                pumpHistorySender.setSenderREQ(record);
        }

        if (squareDelivered && !record.isSquareDelivered()) {
            Log.d(TAG, "*update*" + " Ref: " + bolusRef + " update bolus event square delivered");
            record.squareDelivered = true;
            record.squareDeliveredDate = eventDate;
            record.squareDeliveredRTC = eventRTC;
            record.squareDeliveredOFFSET = eventOFFSET;
            record.squareDeliveredAmount = squareDeliveredAmount;
            record.squareDeliveredDuration = squareDeliveredDuration;
            if (record.programmed && squareProgrammedAmount != squareDeliveredAmount)
                pumpHistorySender.setSenderREQ(record);
        }
    }

    public static void bolusWizardEstimate(
            PumpHistorySender pumpHistorySender, Realm realm, long pumpMAC,
            Date eventDate, int eventRTC, int eventOFFSET,
            byte bgUnits,
            byte carbUnits,
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
            byte bolusStepSize,
            boolean estimateModifiedByUser,
            double finalEstimate) {

        // unused or canceled wizard
        if (finalEstimate == 0) return;

        PumpHistoryBolus record = realm.where(PumpHistoryBolus.class)
                .equalTo("pumpMAC", pumpMAC)
                .equalTo("estimate", true)
                .equalTo("estimateRTC", eventRTC)
                .findFirst();
        if (record == null) {
            // look for a corresponding programmed bolus
            record = realm.where(PumpHistoryBolus.class)
                    .equalTo("pumpMAC", pumpMAC)
                    .notEqualTo("bolusRef", -1)
                    .equalTo("estimate", false)
                    .equalTo("totalProgrammedAmount", finalEstimate)
                    .greaterThan("programmedRTC", HistoryUtils.offsetRTC(eventRTC, - 60))
                    .lessThan("programmedRTC", HistoryUtils.offsetRTC(eventRTC, 5 * 60))
                    .beginGroup()
                    .equalTo("bolusSource", PumpHistoryParser.BOLUS_SOURCE.BOLUS_WIZARD.value())
                    .or()
                    .equalTo("bolusSource", PumpHistoryParser.BOLUS_SOURCE.CLOSED_LOOP_BG_CORRECTION.value())
                    .or()
                    .equalTo("bolusSource", PumpHistoryParser.BOLUS_SOURCE.CLOSED_LOOP_FOOD_BOLUS.value())
                    .or()
                    .equalTo("bolusSource", PumpHistoryParser.BOLUS_SOURCE.CLOSED_LOOP_BG_CORRECTION_AND_FOOD_BOLUS.value())
                    .endGroup()
                    .findFirst();
            if (record == null) {
                Log.d(TAG, "*new*" + " Ref: n/a create new bolus event *estimate*");
                record = realm.createObject(PumpHistoryBolus.class);
                record.pumpMAC = pumpMAC;
                record.eventDate = eventDate;
            } else {
                Log.d(TAG, "*update*" + " Ref: " + record.getBolusRef() + " adding estimate to bolus event");
            }
            record.estimate = true;
            record.estimateRTC = eventRTC;
            record.estimateOFFSET = eventOFFSET;
            record.bgUnits = bgUnits;
            record.carbUnits = carbUnits;
            record.bgInput = bgInput;
            record.carbInput = carbInput;
            record.isf = isf;
            record.carbRatio = carbRatio;
            record.lowBgTarget = lowBgTarget;
            record.highBgTarget = highBgTarget;
            record.correctionEstimate = correctionEstimate;
            record.foodEstimate = foodEstimate;
            record.iob = iob;
            record.iobAdjustment = iobAdjustment;
            record.bolusWizardEstimate = bolusWizardEstimate;
            record.bolusStepSize = bolusStepSize;
            record.estimateModifiedByUser = estimateModifiedByUser;
            record.finalEstimate = finalEstimate;
        }
    }

    @Override
    public String getSenderREQ() {
        return senderREQ;
    }

    @Override
    public void setSenderREQ(String senderREQ) {
        this.senderREQ = senderREQ;
    }

    @Override
    public String getSenderACK() {
        return senderACK;
    }

    @Override
    public void setSenderACK(String senderACK) {
        this.senderACK = senderACK;
    }

    @Override
    public String getSenderDEL() {
        return senderDEL;
    }

    @Override
    public void setSenderDEL(String senderDEL) {
        this.senderDEL = senderDEL;
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
    public String getKey() {
        return key;
    }

    @Override
    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public long getPumpMAC() {
        return pumpMAC;
    }

    @Override
    public void setPumpMAC(long pumpMAC) {
        this.pumpMAC = pumpMAC;
    }

    public int getBolusRef() {
        return bolusRef;
    }

    public byte getBolusType() {
        return bolusType;
    }

    public byte getBolusPreset() {
        return bolusPreset;
    }

    public byte getBolusSource() {
        return bolusSource;
    }

    public double getTotalProgrammedAmount() {
        return totalProgrammedAmount;
    }

    public double getNormalProgrammedAmount() {
        return normalProgrammedAmount;
    }

    public double getNormalDeliveredAmount() {
        return normalDeliveredAmount;
    }

    public double getSquareProgrammedAmount() {
        return squareProgrammedAmount;
    }

    public double getSquareDeliveredAmount() {
        return squareDeliveredAmount;
    }

    public int getSquareProgrammedDuration() {
        return squareProgrammedDuration;
    }

    public int getSquareDeliveredDuration() {
        return squareDeliveredDuration;
    }

    public double getActiveInsulin() {
        return activeInsulin;
    }

    public int getProgrammedRTC() {
        return programmedRTC;
    }

    public int getProgrammedOFFSET() {
        return programmedOFFSET;
    }

    public Date getProgrammedDate() {
        return programmedDate;
    }

    public boolean isProgrammed() {
        return programmed;
    }

    public int getNormalDeliveredRTC() {
        return normalDeliveredRTC;
    }

    public int getNormalDeliveredOFFSET() {
        return normalDeliveredOFFSET;
    }

    public Date getNormalDeliveredDate() {
        return normalDeliveredDate;
    }

    public boolean isNormalDelivered() {
        return normalDelivered;
    }

    public int getSquareDeliveredRTC() {
        return squareDeliveredRTC;
    }

    public int getSquareDeliveredOFFSET() {
        return squareDeliveredOFFSET;
    }

    public Date getSquareDeliveredDate() {
        return squareDeliveredDate;
    }

    public boolean isSquareDelivered() {
        return squareDelivered;
    }

    public int getEstimateRTC() {
        return estimateRTC;
    }

    public int getEstimateOFFSET() {
        return estimateOFFSET;
    }

    public boolean isEstimate() {
        return estimate;
    }

    public byte getBgUnits() {
        return bgUnits;
    }

    public byte getCarbUnits() {
        return carbUnits;
    }

    public byte getBolusStepSize() {
        return bolusStepSize;
    }

    public double getBgInput() {
        return bgInput;
    }

    public double getCarbInput() {
        return carbInput;
    }

    public double getIsf() {
        return isf;
    }

    public double getCarbRatio() {
        return carbRatio;
    }

    public double getLowBgTarget() {
        return lowBgTarget;
    }

    public double getHighBgTarget() {
        return highBgTarget;
    }

    public double getCorrectionEstimate() {
        return correctionEstimate;
    }

    public double getFoodEstimate() {
        return foodEstimate;
    }

    public double getIob() {
        return iob;
    }

    public double getIobAdjustment() {
        return iobAdjustment;
    }

    public double getBolusWizardEstimate() {
        return bolusWizardEstimate;
    }

    public double getFinalEstimate() {
        return finalEstimate;
    }

    public boolean isEstimateModifiedByUser() {
        return estimateModifiedByUser;
    }
}
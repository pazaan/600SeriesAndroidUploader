package info.nightscout.android.history;

import android.support.annotation.NonNull;
import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import info.nightscout.android.R;
import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.exception.IntegrityException;
import info.nightscout.android.medtronic.message.MessageUtils;
import info.nightscout.android.model.medtronicNg.PumpHistoryAlarm;
import info.nightscout.android.model.medtronicNg.PumpHistoryBG;
import info.nightscout.android.model.medtronicNg.PumpHistoryBasal;
import info.nightscout.android.model.medtronicNg.PumpHistoryBolus;
import info.nightscout.android.model.medtronicNg.PumpHistoryCGM;
import info.nightscout.android.model.medtronicNg.PumpHistoryDaily;
import info.nightscout.android.model.medtronicNg.PumpHistoryLoop;
import info.nightscout.android.model.medtronicNg.PumpHistoryMarker;
import info.nightscout.android.model.medtronicNg.PumpHistoryMisc;
import info.nightscout.android.model.medtronicNg.PumpHistoryPattern;
import info.nightscout.android.model.medtronicNg.PumpHistorySystem;
import info.nightscout.android.utils.HexDump;
import info.nightscout.android.utils.FormatKit;
import io.realm.Realm;

import static info.nightscout.android.utils.ToolKit.read8toUInt;
import static info.nightscout.android.utils.ToolKit.read32BEtoInt;
import static info.nightscout.android.utils.ToolKit.read32BEtoLong;
import static info.nightscout.android.utils.ToolKit.read32BEtoULong;
import static info.nightscout.android.utils.ToolKit.read16BEtoInt;
import static info.nightscout.android.utils.ToolKit.read16BEtoUInt;
import static info.nightscout.android.utils.ToolKit.readString;

/**
 * Created by Pogman on 7.11.17.
 */

public class PumpHistoryParser {
    private static final String TAG = PumpHistoryParser.class.getSimpleName();

    private PumpHistorySender pumpHistorySender;

    private Realm historyRealm;

    private byte[] eventData;

    private EventType eventType;
    private int eventSize;
    private long eventOldest;
    private long eventNewest;
    private int eventRTC;
    private int eventOFFSET;
    private Date eventDate;

    private int index;
    private int event;

    private long pumpMAC;
    private int pumpRTC;
    private int pumpOFFSET;
    private long pumpClockDifference;
    private double pumpDRIFT;

    private IntegrityException integrityException;

    public PumpHistoryParser(byte[] eventData) {
        this.eventData = eventData;
    }

    private DateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);

    public Date[] process(PumpHistorySender pumpHistorySender,
                          final long pumpMAC, final int pumpRTC, final int pumpOFFSET, final long pumpClockDifference,
                          long startTime, long endTime, long parseFrom, long parseTo) throws IntegrityException {

        this.pumpHistorySender = pumpHistorySender;

        //eventOldest = startTime;
        //eventNewest = endTime;

        eventOldest = 0;
        eventNewest = 0;

        parser(pumpMAC, pumpRTC, pumpOFFSET, pumpClockDifference, parseFrom, parseTo);

        // event date range returned by pump as it is usually more then requested
        return new Date[]{eventOldest == 0 ? null : new Date(eventOldest), eventNewest == 0 ? null : new Date(eventNewest)};
    }

    private void parser(final long pumpMAC, final int pumpRTC, final int pumpOFFSET, final long pumpClockDifference, final long parseFrom, final long parseTo) throws IntegrityException {
        historyRealm = Realm.getInstance(UploaderApplication.getHistoryConfiguration());

        this.pumpMAC = pumpMAC;
        this.pumpRTC = pumpRTC;
        this.pumpOFFSET = pumpOFFSET;
        this.pumpClockDifference = pumpClockDifference;
        this.pumpDRIFT = 4.0 / (24 * 60 * 60);

        index = 0;
        event = 0;

        historyRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(@NonNull Realm realm) {

                try {

                    while (index < eventData.length) {

                        eventType = EventType.convert(read8toUInt(eventData, index));
                        eventSize = read8toUInt(eventData, index + 0x02);
                        eventRTC = read32BEtoInt(eventData, index + 0x03);
                        eventOFFSET = read32BEtoInt(eventData, index + 0x07);
                        int adjustedRTC = eventRTC + (int) ((double) (pumpRTC - eventRTC) * pumpDRIFT);
                        Date timestamp = MessageUtils.decodeDateTime((long) adjustedRTC & 0xFFFFFFFFL, (long) pumpOFFSET);

                        eventDate = new Date(timestamp.getTime() - pumpClockDifference);

                        long eventTime = eventDate.getTime();
                        if (eventTime > eventNewest || eventNewest == 0) eventNewest = eventTime;
                        if (eventTime < eventOldest || eventOldest == 0) eventOldest = eventTime;

                        if ((parseFrom == 0 || eventTime >= parseFrom) && (parseTo == 0 || eventTime <= parseTo)) {

                            switch (eventType) {
                                case SENSOR_GLUCOSE_READINGS_EXTENDED:
                                    sensorGlucoseReadingsExtended();
                                    break;
                                case NORMAL_BOLUS_PROGRAMMED:
                                    normalBolusProgrammed();
                                    break;
                                case NORMAL_BOLUS_DELIVERED:
                                    normalBolusDelivered();
                                    break;
                                case SQUARE_BOLUS_PROGRAMMED:
                                    squareBolusProgrammed();
                                    break;
                                case SQUARE_BOLUS_DELIVERED:
                                    squareBolusDelivered();
                                    break;
                                case DUAL_BOLUS_PROGRAMMED:
                                    dualBolusProgrammed();
                                    break;
                                case DUAL_BOLUS_PART_DELIVERED:
                                    dualBolusPartDelivered();
                                    break;
                                case BOLUS_WIZARD_ESTIMATE:
                                    bolusWizardEstimate();
                                    break;
                                case MEAL_WIZARD_ESTIMATE:
                                    mealWizardEstimate();
                                    break;
                                case TEMP_BASAL_PROGRAMMED:
                                    tempBasalProgrammed();
                                    break;
                                case TEMP_BASAL_COMPLETE:
                                    tempBasalComplete();
                                    break;
                                case BASAL_PATTERN_SELECTED:
                                    basalPatternSelected();
                                    break;
                                case INSULIN_DELIVERY_STOPPED:
                                    insulinDeliveryStopped();
                                    break;
                                case INSULIN_DELIVERY_RESTARTED:
                                    insulinDeliveryRestarted();
                                    break;
                                case BG_READING:
                                    bgReading();
                                    break;
                                case CLOSED_LOOP_BG_READING:
                                    closedLoopBgReading();
                                    break;
                                case CLOSED_LOOP_TRANSITION:
                                    closedLoopTransition();
                                    break;
                                case BASAL_SEGMENT_START:
                                    basalSegmentStart();
                                    break;
                                case CALIBRATION_COMPLETE:
                                    calibrationComplete();
                                    break;
                                case GLUCOSE_SENSOR_CHANGE:
                                    glucoseSensorChange();
                                    break;
                                case BATTERY_INSERTED:
                                    batteryInserted();
                                    break;
                                case CANNULA_FILL_DELIVERED:
                                    cannulaFillDelivered();
                                    break;
                                case FOOD_EVENT_MARKER:
                                    foodEventMarker();
                                    break;
                                case EXERCISE_EVENT_MARKER:
                                    exerciseEventMarker();
                                    break;
                                case INJECTION_EVENT_MARKER:
                                    injectionEventMarker();
                                    break;
                                case OTHER_EVENT_MARKER:
                                    otherEventMarker();
                                    break;
                                case ALARM_NOTIFICATION:
                                    alarmNotification();
                                    break;
                                case ALARM_CLEARED:
                                    alarmCleared();
                                    break;
                                case DAILY_TOTALS:
                                    dailyTotals();
                                    break;
                                case CLOSED_LOOP_DAILY_TOTALS:
                                    closedLoopDailyTotals();
                                    break;

                                // currently 670G temp targets are not implemented
                                /*
                                case CLOSED_LOOP_ALARM_AUTO_CLEARED:
                                    debugParser();
                                    break;
                                case CLOSED_LOOP_TEMP_TARGET_STARTED:
                                    debugParser();
                                    break;
                                case CLOSED_LOOP_TEMP_TARGET_ENDED:
                                    debugParser();
                                    break;
                                */
                            }

                        }

                        event++;
                        index += eventSize;
                    }

                } catch (IntegrityException e) {
                    integrityException = e;
                }
            }
        });

        historyRealm.close();

        if (integrityException != null) throw integrityException;
    }

    private void debugParser() {
        PumpHistorySystem.debugParser(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                eventType, eventData, eventSize, index);
    }

    private void sensorGlucoseReadingsExtended() throws IntegrityException {
        int minutesBetweenReadings = read8toUInt(eventData, index + 0x0B);
        int numberOfReadings = read8toUInt(eventData, index + 0x0C);

        int pos = index + 15;
        for (int i = 0; i < numberOfReadings; i++) {

            int sgv = read16BEtoUInt(eventData, pos) & 0x03FF;
            double isig = read16BEtoUInt(eventData, pos + 2) / 100.0;

            int vctrraw = (((eventData[pos] >> 2 & 3) << 8) | eventData[pos + 4] & 0x000000FF);
            if ((vctrraw & 0x0200) != 0) vctrraw |= 0xFFFFFE00;
            double vctr = vctrraw / 100.0;

            double rateOfChange = read16BEtoInt(eventData, pos + 5) / 100.0;
            byte sensorStatus = eventData[pos + 7];
            byte readingStatus = eventData[pos + 8];

            boolean backfilledData = (readingStatus & 1) == 1;
            boolean settingsChanged = (readingStatus & 2) == 2;
            boolean noisyData = sensorStatus == 1;
            boolean discardData = sensorStatus == 2;
            boolean sensorError = sensorStatus == 3;

            byte sensorException = 0;

            if (sgv >= 0x0300) {
                sensorException = (byte) sgv;
                sgv = 0;
            }

            int thisRTC = eventRTC - (i * minutesBetweenReadings * 60);
            int adjustedRTC = thisRTC + (int) ((double) (pumpRTC - thisRTC) * pumpDRIFT);
            Date timestamp = MessageUtils.decodeDateTime((long) adjustedRTC & 0xFFFFFFFFL, (long) pumpOFFSET);
            Date thisDate = new Date(timestamp.getTime() - pumpClockDifference);

            PumpHistoryCGM.cgmFromHistory(
                    pumpHistorySender, historyRealm, pumpMAC,
                    thisDate, thisRTC, eventOFFSET,
                    sgv,
                    isig,
                    vctr,
                    rateOfChange,
                    sensorStatus,
                    readingStatus,
                    backfilledData,
                    settingsChanged,
                    noisyData,
                    discardData,
                    sensorError,
                    sensorException);

            pos += 9;

            long eventTime = thisDate.getTime();
            if (eventTime > eventNewest || eventNewest == 0) eventNewest = eventTime;
            if (eventTime < eventOldest || eventOldest == 0) eventOldest = eventTime;
        }
    }

    private void normalBolusProgrammed() {
        byte bolusSource = eventData[index + 0x0B];
        int bolusRef = read8toUInt(eventData, index + 0x0C);
        byte presetBolusNumber = eventData[index + 0x0D];
        double normalProgrammedAmount = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
        double activeInsulin = read32BEtoInt(eventData, index + 0x12) / 10000.0;
        PumpHistoryBolus.bolus(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                BOLUS_TYPE.NORMAL_BOLUS.value(), true, false, false,
                bolusRef,
                bolusSource,
                presetBolusNumber,
                normalProgrammedAmount, 0,
                0, 0,
                0, 0,
                activeInsulin);
    }

    private void normalBolusDelivered() {
        byte bolusSource = eventData[index + 0x0B];
        int bolusRef = read8toUInt(eventData, index + 0x0C);
        byte presetBolusNumber = eventData[index + 0x0D];
        double normalProgrammedAmount = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
        double normalDeliveredAmount = read32BEtoInt(eventData, index + 0x12) / 10000.0;
        double activeInsulin = read32BEtoInt(eventData, index + 0x16) / 10000.0;

        if(bolusSource == BOLUS_SOURCE.CLOSED_LOOP_MICRO_BOLUS.value) {
            PumpHistoryLoop.microbolus(
                    pumpHistorySender, historyRealm, pumpMAC,
                    eventDate, eventRTC, eventOFFSET,
                    bolusRef,
                    normalDeliveredAmount);
        } else {
            PumpHistoryBolus.bolus(
                    pumpHistorySender, historyRealm, pumpMAC,
                    eventDate, eventRTC, eventOFFSET,
                    BOLUS_TYPE.NORMAL_BOLUS.value(), false, true, false,
                    bolusRef,
                    bolusSource,
                    presetBolusNumber,
                    normalProgrammedAmount, normalDeliveredAmount,
                    0, 0,
                    0, 0,
                    activeInsulin);
        }
    }

    private void squareBolusProgrammed() {
        byte bolusSource = eventData[index + 0x0B];
        int bolusRef = read8toUInt(eventData, index + 0x0C);
        byte presetBolusNumber = eventData[index + 0x0D];
        double squareProgrammedAmount = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
        int squareProgrammedDuration = read16BEtoUInt(eventData, index + 0x12);
        double activeInsulin = read32BEtoInt(eventData, index + 0x14) / 10000.0;
        PumpHistoryBolus.bolus(pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                BOLUS_TYPE.SQUARE_WAVE.value(), true, false, false,
                bolusRef,
                bolusSource,
                presetBolusNumber,
                0, 0,
                squareProgrammedAmount, 0,
                squareProgrammedDuration, 0,
                activeInsulin);
    }

    private void squareBolusDelivered() {
        byte bolusSource = eventData[index + 0x0B];
        int bolusRef = read8toUInt(eventData, index + 0x0C);
        byte presetBolusNumber = eventData[index + 0x0D];
        double squareProgrammedAmount = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
        double squareDeliveredAmount = read32BEtoInt(eventData, index + 0x12) / 10000.0;
        int squareProgrammedDuration = read16BEtoUInt(eventData, index + 0x16);
        int squareDeliveredDuration = read16BEtoUInt(eventData, index + 0x18);
        double activeInsulin = read32BEtoInt(eventData, index + 0x1A) / 10000.0;
        PumpHistoryBolus.bolus(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                BOLUS_TYPE.SQUARE_WAVE.value(), false, false, true,
                bolusRef,
                bolusSource,
                presetBolusNumber,
                0, 0,
                squareProgrammedAmount, squareDeliveredAmount,
                squareProgrammedDuration, squareDeliveredDuration,
                activeInsulin);
    }

    private void dualBolusProgrammed() {
        byte bolusSource = eventData[index + 0x0B];
        int bolusRef = read8toUInt(eventData, index + 0x0C);
        byte presetBolusNumber = eventData[index + 0x0D];
        double normalProgrammedAmount = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
        double squareProgrammedAmount = read32BEtoInt(eventData, index + 0x12) / 10000.0;
        int squareProgrammedDuration = read16BEtoUInt(eventData, index + 0x16);
        double activeInsulin = read32BEtoInt(eventData, index + 0x18) / 10000.0;
        PumpHistoryBolus.bolus(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                BOLUS_TYPE.DUAL_WAVE.value(), true, false, false,
                bolusRef,
                bolusSource,
                presetBolusNumber,
                normalProgrammedAmount, 0,
                squareProgrammedAmount, 0,
                squareProgrammedDuration, 0,
                activeInsulin);
    }

    private void dualBolusPartDelivered() {
        byte bolusSource = eventData[index + 0x0B];
        int bolusRef = read8toUInt(eventData, index + 0x0C);
        byte presetBolusNumber = eventData[index + 0x0D];
        double normalProgrammedAmount = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
        double squareProgrammedAmount = read32BEtoInt(eventData, index + 0x12) / 10000.0;
        double deliveredAmount = read32BEtoInt(eventData, index + 0x16) / 10000.0;
        int bolusPart = read8toUInt(eventData, index + 0x1A);
        int squareProgrammedDuration = read16BEtoUInt(eventData, index + 0x1B);
        int squareDeliveredDuration = read16BEtoUInt(eventData, index + 0x1D);
        double activeInsulin = read32BEtoInt(eventData, index + 0x1F) / 10000.0;
        PumpHistoryBolus.bolus(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                BOLUS_TYPE.DUAL_WAVE.value(), false, bolusPart == 1, bolusPart == 2,
                bolusRef,
                bolusSource,
                presetBolusNumber,
                normalProgrammedAmount, bolusPart == 1 ? deliveredAmount : 0,
                squareProgrammedAmount, bolusPart == 2 ? deliveredAmount : 0,
                squareProgrammedDuration, squareDeliveredDuration,
                activeInsulin);
    }

    private void bolusWizardEstimate() {
        byte bgUnits = eventData[index + 0x0B];
        byte carbUnits = eventData[index + 0x0C];
        double bgInput = read16BEtoUInt(eventData, index + 0x0D) / (BG_UNITS.MMOL_L.equals(bgUnits) ? 10.0 : 1.0);
        double carbInput = read16BEtoUInt(eventData, index + 0x0F) / (CARB_UNITS.EXCHANGES.equals(carbUnits) ? 10.0 : 1.0);
        double isf = read16BEtoUInt(eventData, index + 0x11) / (BG_UNITS.MMOL_L.equals(bgUnits) ? 10.0 : 1.0);
        double carbRatio = read32BEtoULong(eventData, index + 0x13) / (CARB_UNITS.EXCHANGES.equals(carbUnits) ? 1000.0 : 10.0);
        double lowBgTarget = read16BEtoUInt(eventData, index + 0x17) / (BG_UNITS.MMOL_L.equals(bgUnits) ? 10.0 : 1.0);
        double highBgTarget = read16BEtoUInt(eventData, index + 0x19) / (BG_UNITS.MMOL_L.equals(bgUnits) ? 10.0 : 1.0);
        double correctionEstimate = read32BEtoLong(eventData, index + 0x1B) / 10000.0;
        double foodEstimate = read32BEtoULong(eventData, index + 0x1F) / 10000.0;
        double iob = read32BEtoInt(eventData, index + 0x23) / 10000.0;
        double iobAdjustment = read32BEtoInt(eventData, index + 0x27) / 10000.0;
        double bolusWizardEstimate = read32BEtoInt(eventData, index + 0x2B) / 10000.0;
        byte bolusStepSize = eventData[index + 0x2F];
        boolean estimateModifiedByUser = (read8toUInt(eventData, index + 0x30) & 1) == 1;
        double finalEstimate = read32BEtoInt(eventData, index + 0x31) / 10000.0;
        PumpHistoryBolus.bolusWizardEstimate(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                bgUnits,
                carbUnits,
                bgInput,
                carbInput,
                isf,
                carbRatio,
                lowBgTarget,
                highBgTarget,
                correctionEstimate,
                foodEstimate,
                iob,
                iobAdjustment,
                bolusWizardEstimate,
                bolusStepSize,
                estimateModifiedByUser,
                finalEstimate);
    }

    private void mealWizardEstimate() {
        byte bgUnits = (byte) (eventData[index + 0x0B] & 1);
        byte carbUnits = (byte) ((eventData[index + 0x0B] & 2) >> 1);
        double bgInput = read16BEtoUInt(eventData, index + 0x0C) / (BG_UNITS.MMOL_L.equals(bgUnits) ? 10.0 : 1.0);
        double carbInput = read16BEtoUInt(eventData, index + 0x0E) / (CARB_UNITS.EXCHANGES.equals(carbUnits) ? 10.0 : 1.0);
        double carbRatio = read32BEtoULong(eventData, index + 0x1C) / (CARB_UNITS.EXCHANGES.equals(carbUnits) ? 1000.0 : 10.0);
        double correctionEstimate = read32BEtoLong(eventData, index + 0x10) / 10000.0;
        double foodEstimate = read32BEtoULong(eventData, index + 0x14) / 10000.0;
        double bolusWizardEstimate = read32BEtoInt(eventData, index + 0x18) / 10000.0;
        double finalEstimate = bolusWizardEstimate;

        PumpHistoryBolus.bolusWizardEstimate(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                bgUnits,
                carbUnits,
                bgInput,
                carbInput,
                0,
                carbRatio,
                0,
                0,
                correctionEstimate,
                foodEstimate,
                0,
                0,
                bolusWizardEstimate,
                (byte) 0,
                false,
                finalEstimate);
    }

    private void tempBasalProgrammed() {
        byte preset = eventData[index + 0x0B];
        byte type = eventData[index + 0x0C];
        double rate = read32BEtoInt(eventData, index + 0x0D) / 10000.0;
        int percentageOfRate = read8toUInt(eventData, index + 0x11);
        int duration = read16BEtoUInt(eventData, index + 0x12);
        PumpHistoryBasal.programmed(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                preset,
                type,
                rate,
                percentageOfRate,
                duration);
    }

    private void tempBasalComplete() {
        byte preset = eventData[index + 0x0B];
        byte type = eventData[index + 0x0C];
        double rate = read32BEtoInt(eventData, index + 0x0D) / 10000.0;
        int percentageOfRate = read8toUInt(eventData, index + 0x11);
        int duration = read16BEtoUInt(eventData, index + 0x12);
        boolean canceled = (eventData[index + 0x14] & 1) == 1;
        PumpHistoryBasal.completed(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                preset,
                type,
                rate,
                percentageOfRate,
                duration,
                canceled);
    }

    private void basalPatternSelected() {
        byte oldPatternNumber = eventData[index + 0x0B];
        byte newPatternNumber = eventData[index + 0x0C];
        PumpHistoryPattern.pattern(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                oldPatternNumber,
                newPatternNumber);
    }

    private void insulinDeliveryStopped() {
        byte reason = eventData[index + 0x0B];
        PumpHistoryBasal.suspend(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                reason);
    }

    private void insulinDeliveryRestarted() {
        byte reason = eventData[index + 0x0B];
        PumpHistoryBasal.resume(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                reason);
    }

    private void bgReading() {
        int bg = read16BEtoUInt(eventData, index + 0x0C);
        byte bgUnits = (byte) (eventData[index + 0x0B] & 1);
        byte bgSource = eventData[index + 0x0E];
        boolean calibrationFlag = ((eventData[index + 0x0B] & 0x02) == 2) || bgSource == BG_SOURCE.SENSOR_CAL.value;
        byte bgContext;
        if (calibrationFlag) bgContext = BG_CONTEXT.BG_SENT_FOR_CALIB.value();
        else bgContext = BG_CONTEXT.BG_READING_RECEIVED.value();
        String serial = new StringBuffer(readString(eventData, index + 0x0F, eventSize - 0x0F)).reverse().toString();
        PumpHistoryBG.bg(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                calibrationFlag,
                bg,
                bgUnits,
                bgSource,
                bgContext,
                serial);
    }

    private void closedLoopBgReading() {
        int bg = read16BEtoUInt(eventData, index + 0x0B);
        byte bgUnits = (byte) (eventData[index + 0x16] & 1);
        byte bgContext = (byte) ((eventData[index + 0x16] & 0xF8) >> 3);
        boolean calibrationFlag = BG_CONTEXT.BG_SENT_FOR_CALIB.equals(bgContext) || BG_CONTEXT.ENTERED_IN_SENSOR_CALIB.equals(bgContext);
        PumpHistoryBG.bg(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                calibrationFlag,
                bg,
                bgUnits,
                BG_SOURCE.NA.value(),
                bgContext,
                "");
    }

    private void closedLoopTransition() {
        byte transitionValue = eventData[index + 0x0B];
        byte transitionReason = eventData[index + 0x0C];
        PumpHistoryLoop.transition(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                transitionValue,
                transitionReason);
    }

    private void basalSegmentStart() {
        byte pattern = eventData[index + 0x0B];
        byte segment = eventData[index + 0x0C];
        double rate = read32BEtoInt(eventData, index + 0x0D) / 10000.0;
        PumpHistoryLoop.basal(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                pattern);
    }

    private void calibrationComplete() {
        double calFactor = read16BEtoUInt(eventData, index + 0xB) / 100.0;
        int bgTarget = read16BEtoUInt(eventData, index + 0xD);
        PumpHistoryBG.calibration(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                calFactor,
                bgTarget);
    }

    private void glucoseSensorChange() {
        PumpHistoryMisc.sensor(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                PumpHistoryMisc.RECORDTYPE.CHANGE_SENSOR);
    }

    private void batteryInserted() {
        PumpHistoryMisc.item(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                PumpHistoryMisc.RECORDTYPE.CHANGE_BATTERY);
    }

    private void rewind() {
        PumpHistoryMisc.item(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                PumpHistoryMisc.RECORDTYPE.CHANGE_CANNULA);
    }

    private void cannulaFillDelivered() {
        byte type = eventData[index + 0x0B];
        double delivered = read32BEtoInt(eventData, index + 0x0C) / 10000.0;
        double remaining = read32BEtoInt(eventData, index + 0x10) / 10000.0;
        PumpHistoryMisc.cannula(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                type,
                delivered,
                remaining);
    }

    private void foodEventMarker() {
        byte carbUnits = eventData[index + 0x0B + 0x08];
        double carbInput = read16BEtoUInt(eventData, index + +0x0B + 0x09) / (CARB_UNITS.EXCHANGES.equals(carbUnits) ? 10.0 : 1.0);
        PumpHistoryMarker.marker(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                PumpHistoryMarker.RECORDTYPE.FOOD,
                0,
                carbUnits,
                carbInput,
                0);
    }

    private void exerciseEventMarker() {
        int duration = read16BEtoUInt(eventData, index + 0x0B + 0x08);
        PumpHistoryMarker.marker(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                PumpHistoryMarker.RECORDTYPE.EXERCISE,
                duration,
                (byte) 0,
                0,
                0);
    }

    private void injectionEventMarker() {
        double insulin = read32BEtoInt(eventData, index + 0x0B + 0x08) / 10000.0;
        PumpHistoryMarker.marker(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                PumpHistoryMarker.RECORDTYPE.EXERCISE,
                0,
                (byte) 0,
                0,
                insulin);
    }

    private void otherEventMarker() {
        PumpHistoryMarker.marker(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                PumpHistoryMarker.RECORDTYPE.OTHER,
                0,
                (byte) 0,
                0,
                0);
    }

    private void alarmNotification() throws IntegrityException {
        int faultNumber = read16BEtoInt(eventData, index + 0x0B);
        byte notificationMode = eventData[index + 0x11];
        boolean extraData = (eventData[index + 0x12] & 2) == 2;
        boolean alarmHistory = (eventData[index + 0x12] & 4) == 4;
        byte[] alarmData = null;
        if (extraData) alarmData = Arrays.copyOfRange(eventData,index + 0x13,index + 0x1B);
        PumpHistoryAlarm.alarm(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                faultNumber,
                notificationMode,
                alarmHistory,
                extraData,
                alarmData);
    }

    private void alarmCleared() {
        int faultNumber = read16BEtoInt(eventData, index + 0x0B);
        PumpHistoryAlarm.cleared(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                faultNumber);
    }

    private void dailyTotals() {
        int rtc = read32BEtoInt(eventData, index + 0x0B);
        int offset = read32BEtoInt(eventData, index + 0x0B + 0x04);
        int duration = read16BEtoUInt(eventData, index + 0x0B + 0x08);

        int meterBgCount = read8toUInt(eventData, index + 0x0B + 0x0A);
        int meterBgAverage = read16BEtoUInt(eventData, index + 0x0B + 0x0B);
        int lowMeterBg = read16BEtoUInt(eventData, index + 0x0B + 0x0D);
        int highMeterBg = read16BEtoUInt(eventData, index + 0x0B + 0x0F);
        int manuallyEnteredBgCount = read8toUInt(eventData, index + 0x0B + 0x11);
        int manuallyEnteredBgAverage = read16BEtoUInt(eventData, index + 0x0B + 0x12);
        int lowManuallyEnteredBg = read16BEtoUInt(eventData, index + 0x0B + 0x14);
        int highManuallyEnteredBg = read16BEtoUInt(eventData, index + 0x0B + 0x16);
        int bgAverage = read16BEtoUInt(eventData, index + 0x0B + 0x18);

        double totalInsulin = read32BEtoInt(eventData, index + 0x0B + 0x1A) / 10000.0;
        double basalInsulin = read32BEtoInt(eventData, index + 0x0B + 0x1E) / 10000.0;
        int basalPercent = read8toUInt(eventData, index + 0x0B + 0x22);
        double bolusInsulin = read32BEtoInt(eventData, index + 0x0B + 0x23) / 10000.0;
        int bolusPercent = read8toUInt(eventData, index + 0x0B + 0x27);

        int carbUnits = read8toUInt(eventData, index + 0x0B + 0x28);
        //not sure this is the correct conversion?
        double totalFoodInput = read16BEtoUInt(eventData, index + 0x0B + 0x29) / (CARB_UNITS.EXCHANGES.equals(carbUnits) ? 10.0 : 1.0);
        int bolusWizardUsageCount = read8toUInt(eventData, index + 0x0B + 0x2B);
        double totalBolusWizardInsulinAsFoodOnlyBolus = read32BEtoInt(eventData, index + 0x0B + 0x2C) / 10000.0;
        double totalBolusWizardInsulinAsCorrectionOnlyBolus = read32BEtoInt(eventData, index + 0x0B + 0x30) / 10000.0;
        double totalBolusWizardInsulinAsFoodAndCorrection = read32BEtoInt(eventData, index + 0x0B + 0x34) / 10000.0;
        double totalManualBolusInsulin = read32BEtoInt(eventData, index + 0x0B + 0x38) / 10000.0;
        int bolusWizardFoodOnlyBolusCount = read8toUInt(eventData, index + 0x0B + 0x3C);
        int bolusWizardCorrectionOnlyBolusCount = read8toUInt(eventData, index + 0x0B + 0x3D);
        int bolusWizardFoodAndCorrectionBolusCount = read8toUInt(eventData, index + 0x0B + 0x3E);
        int manualBolusCount = read8toUInt(eventData, index + 0x0B + 0x3F);

        int sgCount = read16BEtoUInt(eventData, index + 0x0B + 0x40);
        int sgAverage = read16BEtoUInt(eventData, index + 0x0B + 0x42);
        int sgStddev = read16BEtoUInt(eventData, index + 0x0B + 0x44);
        int sgDurationAboveHigh = read16BEtoUInt(eventData, index + 0x0B + 0x46);
        int percentAboveHigh = read8toUInt(eventData, index + 0x0B + 0x48);
        int sgDurationWithinLimit = read16BEtoUInt(eventData, index + 0x0B + 0x49);
        int percentWithinLimit = read8toUInt(eventData, index + 0x0B + 0x4B);
        int sgDurationBelowLow = read16BEtoUInt(eventData, index + 0x0B + 0x4C);
        int percentBelowLow = read8toUInt(eventData, index + 0x0B + 0x4E);
        int lgsSuspensionDuration = read16BEtoUInt(eventData, index + 0x0B + 0x4F);

        int highPredictiveAlerts = read16BEtoUInt(eventData, index + 0x0B + 0x51);
        int lowPredictiveAlerts = read16BEtoUInt(eventData, index + 0x0B + 0x53);
        int lowBgAlerts = read16BEtoUInt(eventData, index + 0x0B + 0x55);
        int highBgAlerts = read16BEtoUInt(eventData, index + 0x0B + 0x57);
        int risingRateAlerts = read16BEtoUInt(eventData, index + 0x0B + 0x59);
        int fallingRateAlerts = read16BEtoUInt(eventData, index + 0x0B + 0x5B);
        int lowGlucoseSuspendAlerts = read16BEtoUInt(eventData, index + 0x0B + 0x5D);
        int predictiveLowGlucoseSuspendAlerts = read16BEtoUInt(eventData, index + 0x0B + 0x5F);

        int startAdjustedRTC = rtc + (int) ((double) (pumpRTC - rtc) * pumpDRIFT);
        Date startTimestamp = MessageUtils.decodeDateTime((long) startAdjustedRTC & 0xFFFFFFFFL, (long) pumpOFFSET);
        Date startDate = new Date(startTimestamp.getTime() - pumpClockDifference);

        int endAdjustedRTC = rtc + (duration * 60) + (int) ((double) (pumpRTC - rtc) * pumpDRIFT);
        Date endTimestamp = MessageUtils.decodeDateTime((long) endAdjustedRTC & 0xFFFFFFFFL, (long) pumpOFFSET);
        Date endDate = new Date(endTimestamp.getTime() - pumpClockDifference);

        PumpHistoryDaily.dailyTotals(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                PumpHistoryDaily.TYPE.DAILY_TOTALS.value(),
                startDate,
                endDate,
                rtc,
                offset,
                duration,

                meterBgCount,
                meterBgAverage,
                lowMeterBg,
                highMeterBg,
                manuallyEnteredBgCount,
                manuallyEnteredBgAverage,
                lowManuallyEnteredBg,
                highManuallyEnteredBg,
                meterBgCount + manuallyEnteredBgCount,
                bgAverage,
                0,

                totalInsulin,
                basalInsulin,
                basalPercent,
                bolusInsulin,
                bolusPercent,

                carbUnits,
                bolusWizardUsageCount,
                0,

                totalFoodInput,
                totalBolusWizardInsulinAsFoodOnlyBolus,
                totalBolusWizardInsulinAsCorrectionOnlyBolus,
                totalBolusWizardInsulinAsFoodAndCorrection,
                0,
                0,
                0,
                totalManualBolusInsulin,
                bolusWizardFoodOnlyBolusCount,
                bolusWizardCorrectionOnlyBolusCount,
                bolusWizardFoodAndCorrectionBolusCount,
                0,
                0,
                0,
                manualBolusCount,

                sgCount,
                sgAverage,
                sgStddev,
                sgDurationAboveHigh,
                percentAboveHigh,
                sgDurationWithinLimit,
                percentWithinLimit,
                sgDurationBelowLow,
                percentBelowLow,
                lgsSuspensionDuration,

                highPredictiveAlerts,
                lowPredictiveAlerts,
                lowBgAlerts,
                highBgAlerts,
                risingRateAlerts,
                fallingRateAlerts,
                lowGlucoseSuspendAlerts,
                predictiveLowGlucoseSuspendAlerts,

                0,
                0,
                0,
                0,
                0,
                0
        );
    }

    private void closedLoopDailyTotals() {
        int rtc = read32BEtoInt(eventData, index + 0x0B);
        int offset = read32BEtoInt(eventData, index + 0x0B + 0x04);
        int duration = read16BEtoUInt(eventData, index + 0x0B + 0x08);

        int numBg = read8toUInt(eventData, index + 0x0B + 0x0A);
        int msbAvgBg = read16BEtoUInt(eventData, index + 0x0B + 0x0B);
        int lowMeterBg = read16BEtoUInt(eventData, index + 0x0B + 0x0D);
        int highMeterBg = read16BEtoUInt(eventData, index + 0x0B + 0x0F);
        int bgStandardDeviation = read16BEtoUInt(eventData, index + 0x0B + 0x11);

        double dailyTotalAllInsulinDelivered = read32BEtoInt(eventData, index + 0x0B + 0x13) / 10000.0;
        double dailyTotalBasalInsulinDelivered = read32BEtoInt(eventData, index + 0x0B + 0x17) / 10000.0;
        int percentageDailyTotalBasalInsulinDelivered = read8toUInt(eventData, index + 0x0B + 0x1B);
        double dailyTotalBolusInsulinDelivered = read32BEtoInt(eventData, index + 0x0B + 0x1C) / 10000.0;
        int percentageDailyTotalBolusInsulinDelivered = read8toUInt(eventData, index + 0x0B + 0x20);

        double dailyTotalFoodInput = read16BEtoUInt(eventData, index + 0x0B + 0x21);
        double totalBWIFoodOnlyBolus = read32BEtoInt(eventData, index + 0x0B + 0x23) / 10000.0;
        double totalBWICorrectionOnlyBolus = read32BEtoInt(eventData, index + 0x0B + 0x27) / 10000.0;
        double totalBWIFoodCorrectionBolus = read32BEtoInt(eventData, index + 0x0B + 0x2B) / 10000.0;
        double totalOfMealWizardInsulinDeliveredFoodOnlyBolus = read32BEtoInt(eventData, index + 0x0B + 0x2F) / 10000.0;
        double totalOfMealWizardInsulinDeliveredCorrectionOnlyBolus = read32BEtoInt(eventData, index + 0x0B + 0x33) / 10000.0;
        double totalOfMealWizardInsulinDeliveredFoodCorrectionBolus = read32BEtoInt(eventData, index + 0x0B + 0x37) / 10000.0;
        int numOfBolusWizardFoodOnlyBoluses = read8toUInt(eventData, index + 0x0B + 0x3B);
        int numOfBolusWizardCorrectionOnlyBoluses = read8toUInt(eventData, index + 0x0B + 0x3C);
        int numOfBolusWizardFoodCorrectionBoluses = read8toUInt(eventData, index + 0x0B + 0x3D);
        int numOfMealWizardFoodOnlyBoluses = read8toUInt(eventData, index + 0x0B + 0x3E);
        int numOfMealWizardCorrectionOnlyBoluses = read8toUInt(eventData, index + 0x0B + 0x3F);
        int numOfMealWizardFoodCorrectionBoluses = read8toUInt(eventData, index + 0x0B + 0x40);
        int numOfManualBoluses = read8toUInt(eventData, index + 0x0B + 0x41);

        int numOfSensorGlucoseReading = read16BEtoUInt(eventData, index + 0x0B + 0x42);
        int avgSensorGlucoseMesurement = read16BEtoUInt(eventData, index + 0x0B + 0x44);
        int sensorStandardDeviation = read16BEtoUInt(eventData, index + 0x0B + 0x46);
        int durationOfSensorReadingsAboveHiLimit = read16BEtoUInt(eventData, index + 0x0B + 0x48);
        int percentOfdurationOfSensorReadingsAboveHiLimit = read8toUInt(eventData, index + 0x0B + 0x4A);
        int durationofSensorReadingsWithinLimit = read16BEtoUInt(eventData, index + 0x0B + 0x4B);
        int percentOfDurationOfSensorReadingsWithinLimit = read8toUInt(eventData, index + 0x0B + 0x4D);
        int durationOfSensorReadingsBelowLowLimit = read16BEtoUInt(eventData, index + 0x0B + 0x4E);
        int percentofDurationOfSensorReadingsBelowLowLimit = read8toUInt(eventData, index + 0x0B + 0x50);
        int durationSuspend = read16BEtoUInt(eventData, index + 0x0B + 0x51);

        int numberOfThresholdSuspendAlarms = read16BEtoUInt(eventData, index + 0x0B + 0x53);
        int numberOfPredictiveSuspendQuietAndPredictiveSuspendAnnunciateAlarms = read16BEtoUInt(eventData, index + 0x0B + 0x55);

        double totalOfMicroBolusInsulinDelivered = read32BEtoInt(eventData, index + 0x0B + 0x57) / 10000.0;
        int totalTimeInCLActiveMode = read16BEtoUInt(eventData, index + 0x0B + 0x5B);
        int totalTimeInTherapyTargetRange = read16BEtoUInt(eventData, index + 0x0B + 0x5D);
        int totalTimeInAboveTherapyTargetRangeHiLimit = read16BEtoUInt(eventData, index + 0x0B + 0x5F);
        int totalTimeInBelowTherapyTargetRangeLowLimit = read16BEtoUInt(eventData, index + 0x0B + 0x61);
        int numberOfClosedLoopMicroBoluses = read16BEtoUInt(eventData, index + 0x0B + 0x63);

        int startAdjustedRTC = rtc + (int) ((double) (pumpRTC - rtc) * pumpDRIFT);
        Date startTimestamp = MessageUtils.decodeDateTime((long) startAdjustedRTC & 0xFFFFFFFFL, (long) pumpOFFSET);
        Date startDate = new Date(startTimestamp.getTime() - pumpClockDifference);

        int endAdjustedRTC = rtc + (duration * 60) + (int) ((double) (pumpRTC - rtc) * pumpDRIFT);
        Date endTimestamp = MessageUtils.decodeDateTime((long) endAdjustedRTC & 0xFFFFFFFFL, (long) pumpOFFSET);
        Date endDate = new Date(endTimestamp.getTime() - pumpClockDifference);

        double totalManualBolusInsulin = Math.abs(dailyTotalBolusInsulinDelivered - totalBWIFoodOnlyBolus - totalBWICorrectionOnlyBolus - totalBWIFoodCorrectionBolus - totalOfMealWizardInsulinDeliveredFoodOnlyBolus - totalOfMealWizardInsulinDeliveredCorrectionOnlyBolus - totalOfMealWizardInsulinDeliveredFoodCorrectionBolus);

        PumpHistoryDaily.dailyTotals(
                pumpHistorySender, historyRealm, pumpMAC,
                eventDate, eventRTC, eventOFFSET,
                PumpHistoryDaily.TYPE.CLOSED_LOOP_DAILY_TOTALS.value(),
                startDate,
                endDate,
                rtc,
                offset,
                duration,

                numBg,
                msbAvgBg,
                lowMeterBg,
                highMeterBg,
                0,
                0,
                0,
                0,
                numBg,
                msbAvgBg,
                bgStandardDeviation,

                dailyTotalAllInsulinDelivered,
                dailyTotalBasalInsulinDelivered,
                percentageDailyTotalBasalInsulinDelivered,
                dailyTotalBolusInsulinDelivered,
                percentageDailyTotalBolusInsulinDelivered,

                CARB_UNITS.NA.value(),
                numOfBolusWizardFoodOnlyBoluses + numOfBolusWizardCorrectionOnlyBoluses + numOfBolusWizardFoodCorrectionBoluses,
                numOfMealWizardFoodOnlyBoluses + numOfMealWizardCorrectionOnlyBoluses + numOfMealWizardFoodCorrectionBoluses,
                dailyTotalFoodInput,
                totalBWIFoodOnlyBolus,
                totalBWICorrectionOnlyBolus,
                totalBWIFoodCorrectionBolus,
                totalOfMealWizardInsulinDeliveredFoodOnlyBolus,
                totalOfMealWizardInsulinDeliveredCorrectionOnlyBolus,
                totalOfMealWizardInsulinDeliveredFoodCorrectionBolus,
                totalManualBolusInsulin,
                numOfBolusWizardFoodOnlyBoluses,
                numOfBolusWizardCorrectionOnlyBoluses,
                numOfBolusWizardFoodCorrectionBoluses,
                numOfMealWizardFoodOnlyBoluses,
                numOfMealWizardCorrectionOnlyBoluses,
                numOfMealWizardFoodCorrectionBoluses,
                numOfManualBoluses,

                numOfSensorGlucoseReading,
                avgSensorGlucoseMesurement,
                sensorStandardDeviation,
                durationOfSensorReadingsAboveHiLimit,
                percentOfdurationOfSensorReadingsAboveHiLimit,
                durationofSensorReadingsWithinLimit,
                percentOfDurationOfSensorReadingsWithinLimit,
                durationOfSensorReadingsBelowLowLimit,
                percentofDurationOfSensorReadingsBelowLowLimit,
                durationSuspend,

                0,
                0,
                0,
                0,
                0,
                0,
                numberOfThresholdSuspendAlarms,
                numberOfPredictiveSuspendQuietAndPredictiveSuspendAnnunciateAlarms,

                numberOfClosedLoopMicroBoluses,
                totalOfMicroBolusInsulinDelivered,
                totalTimeInCLActiveMode,
                totalTimeInTherapyTargetRange,
                totalTimeInAboveTherapyTargetRangeHiLimit,
                totalTimeInBelowTherapyTargetRangeLowLimit
        );
    }

// events to logcat, no clock difference/drift adjustments just the pump event times

    public void logcat() {
        float MMOLXLFACTOR = 18.016f;
        String result;

        index = 0;
        event = 0;

        while (index < eventData.length) {

            eventType = EventType.convert(read8toUInt(eventData, index));
            eventSize = read8toUInt(eventData, index + 0x02);

            eventRTC = read32BEtoInt(eventData, index + 0x03);
            eventOFFSET = read32BEtoInt(eventData, index + 0x07);
            Date timestamp = MessageUtils.decodeDateTime(eventRTC & 0xFFFFFFFFL, eventOFFSET);

            result = "[" + event + "] " + eventType + " " + dateFormatter.format(timestamp);

            if (eventType == EventType.BG_READING) {
                boolean calibrationFlag = (eventData[index + 0x0B] & 0x02) == 2;
                int bgUnits = eventData[index + 0x0B] & 1;
                int bg = read16BEtoUInt(eventData, index + 0x0C);
                int bgSource = read8toUInt(eventData, index + 0x0E);
                byte bgContext = -1;
                String serial = new StringBuffer(readString(eventData, index + 0x0F, eventSize - 0x0F)).reverse().toString();
                result += " BG:" + bg + " Unit:" + bgUnits + " Source:" + bgSource + " Context:" + bgContext + " Calibration:" + calibrationFlag + " Serial:" + serial;

            } else if (eventType == EventType.CLOSED_LOOP_BG_READING) {
                int bg = read16BEtoUInt(eventData, index + 0x0B);
                byte bgUnits = (byte) (eventData[index + 0x16] & 1);
                byte bgSource = -1;
                byte bgContext = (byte) ((eventData[index + 0x16] & 0xF8) >> 3);
                boolean calibrationFlag = BG_CONTEXT.BG_SENT_FOR_CALIB.equals(bgContext) || BG_CONTEXT.ENTERED_IN_SENSOR_CALIB.equals(bgContext);
                String serial = "";
                result += " BG:" + bg + " Unit:" + bgUnits + " Source:" + bgSource + " Context:" + bgContext + " Calibration:" + calibrationFlag + " Serial:" + serial;

            } else if (eventType == EventType.CALIBRATION_COMPLETE) {
                double calFactor = read16BEtoUInt(eventData, index + 0xB) / 100.0;
                int bgTarget = read16BEtoUInt(eventData, index + 0xD);
                result += " bgTarget:" + bgTarget + " calFactor:" + calFactor;

            } else if (eventType == EventType.BOLUS_WIZARD_ESTIMATE) {
                int bgUnits = read8toUInt(eventData, index + 0x0B);
                int carbUnits = read8toUInt(eventData, index + 0x0C);
                double bgInput = read16BEtoUInt(eventData, index + 0x0D) / (BG_UNITS.MMOL_L.equals(bgUnits) ? 0.1 : 1.0);
                double carbInput = read16BEtoUInt(eventData, index + 0x0F) / (CARB_UNITS.EXCHANGES.equals(carbUnits) ? 10.0 : 1.0);
                double isf = read16BEtoUInt(eventData, index + 0x11) / (BG_UNITS.MMOL_L.equals(bgUnits) ? 10.0 : 1.0);
                double carbRatio = read32BEtoULong(eventData, index + 0x13) / (CARB_UNITS.EXCHANGES.equals(carbUnits) ? 1000.0 : 10.0);
                double lowBgTarget = read16BEtoUInt(eventData, index + 0x17) / (BG_UNITS.MMOL_L.equals(bgUnits) ? 10.0 : 1.0);
                double highBgTarget = read16BEtoUInt(eventData, index + 0x19) / (BG_UNITS.MMOL_L.equals(bgUnits) ? 10.0 : 1.0);
                double correctionEstimate = read32BEtoLong(eventData, index + 0x1B) / 10000.0;
                double foodEstimate = read32BEtoULong(eventData, index + 0x1F) / 10000.0;
                double iob = read32BEtoInt(eventData, index + 0x23) / 10000.0;
                double iobAdjustment = read32BEtoInt(eventData, index + 0x27) / 10000.0;
                double bolusWizardEstimate = read32BEtoInt(eventData, index + 0x2B) / 10000.0;
                int bolusStepSize = read8toUInt(eventData, index + 0x2F);
                boolean estimateModifiedByUser = (read8toUInt(eventData, index + 0x30) & 1) == 1;
                double finalEstimate = read32BEtoInt(eventData, index + 0x31) / 10000.0;
                result += " bgUnits:" + bgUnits + " carbUnits:" + carbUnits;
                result += " bgInput:" + bgInput + " carbInput:" + carbInput;
                result += " isf:" + isf + " carbRatio:" + carbRatio;
                result += " lowBgTarget:" + lowBgTarget + " highBgTarget:" + highBgTarget;
                result += " correctionEstimate:" + correctionEstimate + " foodEstimate:" + foodEstimate;
                result += " iob:" + iob + " iobAdjustment:" + iobAdjustment;
                result += " bolusWizardEstimate:" + bolusWizardEstimate + " bolusStepSize:" + bolusStepSize;
                result += " estimateModifiedByUser:" + estimateModifiedByUser + " finalEstimate:" + finalEstimate;

            } else if (eventType == EventType.MEAL_WIZARD_ESTIMATE) {
                int bgUnits = read8toUInt(eventData, index + 0x0B) & 1;
                int carbUnits = (read8toUInt(eventData, index + 0x0B) & 2) >> 1;
                int bolusStepSize = 0;
                double bgInput = read16BEtoUInt(eventData, index + 0x0C) / (BG_UNITS.MMOL_L.equals(bgUnits) ? 10.0 : 1.0);
                double carbInput = read16BEtoUInt(eventData, index + 0x0E) / (CARB_UNITS.EXCHANGES.equals(carbUnits) ? 10.0 : 1.0);
                double carbRatio = read32BEtoULong(eventData, index + 0x1C) / (CARB_UNITS.EXCHANGES.equals(carbUnits) ? 1000.0 : 10.0);
                double correctionEstimate = read32BEtoLong(eventData, index + 0x10) / 10000.0;
                double foodEstimate = read32BEtoULong(eventData, index + 0x14) / 10000.0;
                double bolusWizardEstimate = read32BEtoInt(eventData, index + 0x18) / 10000.0;
                double finalEstimate = bolusWizardEstimate;
                double isf = 0;
                double lowBgTarget = 0;
                double highBgTarget = 0;
                double iob = 0;
                double iobAdjustment = 0;
                boolean estimateModifiedByUser = false;
                result += " bgUnits:" + bgUnits + " carbUnits:" + carbUnits;
                result += " bgInput:" + bgInput + " carbInput:" + carbInput;
                result += " isf:" + isf + " carbRatio:" + carbRatio;
                result += " lowBgTarget:" + lowBgTarget + " highBgTarget:" + highBgTarget;
                result += " correctionEstimate:" + correctionEstimate + " foodEstimate:" + foodEstimate;
                result += " iob:" + iob + " iobAdjustment:" + iobAdjustment;
                result += " bolusWizardEstimate:" + bolusWizardEstimate + " bolusStepSize:" + bolusStepSize;
                result += " estimateModifiedByUser:" + estimateModifiedByUser + " finalEstimate:" + finalEstimate;

            } else if (eventType == EventType.NORMAL_BOLUS_PROGRAMMED) {
                int bolusSource = read8toUInt(eventData, index + 0x0B);
                int bolusRef = read8toUInt(eventData, index + 0x0C);
                int presetBolusNumber = read8toUInt(eventData, index + 0x0D);
                double programmedAmount = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
                double activeInsulin = read32BEtoInt(eventData, index + 0x12) / 10000.0;
                result += " Source:" + bolusSource + " Ref:" + bolusRef + " Preset:" + presetBolusNumber;
                result += " Prog:" + programmedAmount + " Active:" + activeInsulin;

            } else if (eventType == EventType.NORMAL_BOLUS_DELIVERED) {
                int bolusSource = read8toUInt(eventData, index + 0x0B);
                int bolusRef = read8toUInt(eventData, index + 0x0C);
                int presetBolusNumber = read8toUInt(eventData, index + 0x0D);
                double programmedAmount = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
                double deliveredAmount = read32BEtoInt(eventData, index + 0x12) / 10000.0;
                double activeInsulin = read32BEtoInt(eventData, index + 0x16) / 10000.0;
                if(bolusSource == BOLUS_SOURCE.CLOSED_LOOP_MICRO_BOLUS.value) {
                    result = "[" + event + "] " + "CLOSED_LOOP_MICRO_BOLUS " + dateFormatter.format(timestamp);
                }
                result += " Source:" + bolusSource + " Ref:" + bolusRef + " Preset:" + presetBolusNumber;
                result += " Prog:" + programmedAmount + " Del:" + deliveredAmount + " Active:" + activeInsulin;

            } else if (eventType == EventType.DUAL_BOLUS_PROGRAMMED) {
                int bolusSource = read8toUInt(eventData, index + 0x0B);
                int bolusRef = read8toUInt(eventData, index + 0x0C);
                int presetBolusNumber = read8toUInt(eventData, index + 0x0D);
                double normalProgrammedAmount = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
                double squareProgrammedAmount = read32BEtoInt(eventData, index + 0x12) / 10000.0;
                int programmedDuration = read16BEtoUInt(eventData, index + 0x16);
                double activeInsulin = read32BEtoInt(eventData, index + 0x18) / 10000.0;
                result += " Source:" + bolusSource + " Ref:" + bolusRef + " Preset:" + presetBolusNumber;
                result += " Norm:" + normalProgrammedAmount + " Sqr:" + squareProgrammedAmount;
                result += " Dur:" + programmedDuration + " Active:" + activeInsulin;

            } else if (eventType == EventType.DUAL_BOLUS_PART_DELIVERED) {
                int bolusSource = read8toUInt(eventData, index + 0x0B);
                int bolusRef = read8toUInt(eventData, index + 0x0C);
                int presetBolusNumber = read8toUInt(eventData, index + 0x0D);
                double normalProgrammedAmount = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
                double squareProgrammedAmount = read32BEtoInt(eventData, index + 0x12) / 10000.0;
                double deliveredAmount = read32BEtoInt(eventData, index + 0x16) / 10000.0;
                int bolusPart = read8toUInt(eventData, index + 0x1A);
                int programmedDuration = read16BEtoUInt(eventData, index + 0x1B);
                int deliveredDuration = read16BEtoUInt(eventData, index + 0x1D);
                double activeInsulin = read32BEtoInt(eventData, index + 0x1F) / 10000.0;
                result += " Source:" + bolusSource + " Ref:" + bolusRef + " Preset:" + presetBolusNumber;
                result += " Norm:" + normalProgrammedAmount + " Sqr:" + squareProgrammedAmount;
                result += " Del:" + deliveredAmount + " Part:" + bolusPart;
                result += " Dur:" + programmedDuration + " delDur:" + deliveredDuration + " Active:" + activeInsulin;

            } else if (eventType == EventType.SQUARE_BOLUS_PROGRAMMED) {
                int bolusSource = read8toUInt(eventData, index + 0x0B);
                int bolusRef = read8toUInt(eventData, index + 0x0C);
                int presetBolusNumber = read8toUInt(eventData, index + 0x0D);
                double programmedAmount = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
                int programmedDuration = read16BEtoUInt(eventData, index + 0x12);
                double activeInsulin = read32BEtoInt(eventData, index + 0x14) / 10000.0;
                result += " Source:" + bolusSource + " Ref:" + bolusRef + " Preset:" + presetBolusNumber;
                result += " Prog:" + programmedAmount;
                result += " Dur:" + programmedDuration + " Active:" + activeInsulin;

            } else if (eventType == EventType.SQUARE_BOLUS_DELIVERED) {
                int bolusSource = read8toUInt(eventData, index + 0x0B);
                int bolusRef = read8toUInt(eventData, index + 0x0C);
                int presetBolusNumber = read8toUInt(eventData, index + 0x0D);
                double programmedAmount = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
                double deliveredAmount = read32BEtoInt(eventData, index + 0x12) / 10000.0;
                int programmedDuration = read16BEtoUInt(eventData, index + 0x16);
                int deliveredDuration = read16BEtoUInt(eventData, index + 0x18);
                double activeInsulin = read32BEtoInt(eventData, index + 0x1A) / 10000.0;
                result += " Source:" + bolusSource + " Ref:" + bolusRef + " Preset:" + presetBolusNumber;
                result += " Prog:" + programmedAmount + " Del:" + deliveredAmount;
                result += " Dur:" + programmedDuration + " delDur:" + deliveredDuration + " Active:" + activeInsulin;

            } else if (eventType == EventType.TEMP_BASAL_PROGRAMMED) {
                int preset = read8toUInt(eventData, index + 0x0B);
                int type = read8toUInt(eventData, index + 0x0C);
                double rate = read32BEtoInt(eventData, index + 0x0D) / 10000.0;
                int percentageOfRate = read8toUInt(eventData, index + 0x11);
                int duration = read16BEtoUInt(eventData, index + 0x12);
                result += " Preset:" + preset + " Type:" + type;
                result += " Rate:" + rate + " Percent:" + percentageOfRate;
                result += " Dur:" + duration;

            } else if (eventType == EventType.TEMP_BASAL_COMPLETE) {
                int preset = read8toUInt(eventData, index + 0x0B);
                int type = read8toUInt(eventData, index + 0x0C);
                double rate = read32BEtoInt(eventData, index + 0x0D) / 10000.0;
                int percentageOfRate = read8toUInt(eventData, index + 0x11);
                int duration = read16BEtoUInt(eventData, index + 0x12);
                boolean canceled = (eventData[index + 0x14] & 1) == 1;
                result += " Preset:" + preset + " Type:" + type;
                result += " Rate:" + rate + " Percent:" + percentageOfRate;
                result += " Dur:" + duration + " Canceled:" + canceled;

            } else if (eventType == EventType.BASAL_SEGMENT_START) {
                int pattern = read8toUInt(eventData, index + 0x0B);
                int segment = read8toUInt(eventData, index + 0x0C);
                double rate = read32BEtoInt(eventData, index + 0x0D) / 10000.0;
                result += " Pattern:" + pattern + " Segment:" + segment + " Rate:" + rate;

            } else if (eventType == EventType.INSULIN_DELIVERY_STOPPED
                    || eventType == EventType.INSULIN_DELIVERY_RESTARTED) {
                int reason = read8toUInt(eventData, index + 0x0B);
                result += " Reason: " + reason;

            } else if (eventType == EventType.NETWORK_DEVICE_CONNECTION) {
                boolean flag1 = (eventData[index + 0x0B] & 0x01) == 1;
                int value1 = read8toUInt(eventData, index + 0x0C);
                boolean flag2 = (eventData[index + 0x0D] & 0x01) == 1;
                String serial = new StringBuffer(readString(eventData, index + 0x0E, eventSize - 0x0E)).reverse().toString();
                result += " Flag1:" + flag1 + " Flag2:" + flag2 + " Value1:" + value1 + " Serial:" + serial;

            } else if (eventType == EventType.SENSOR_GLUCOSE_READINGS_EXTENDED) {
                int minutesBetweenReadings = read8toUInt(eventData, index + 0x0B);
                int numberOfReadings = read8toUInt(eventData, index + 0x0C);
                int predictedSg = read16BEtoUInt(eventData, index + 0x0D);
                result += " Min:" + minutesBetweenReadings + " Num:" + numberOfReadings + " SGP:" + predictedSg;

                int pos = index + 15;
                for (int i = 0; i < numberOfReadings; i++) {

                    Date sgtimestamp = MessageUtils.decodeDateTime(eventRTC & 0xFFFFFFFFL - (i * minutesBetweenReadings * 60), eventOFFSET);

                    int sg = read16BEtoUInt(eventData, pos) & 0x03FF;
                    double isig = read16BEtoUInt(eventData, pos + 2) / 100.0;

                    int vctrraw = (((eventData[pos] >> 2 & 3) << 8) | eventData[pos + 4] & 0x000000FF);
                    if ((vctrraw & 0x0200) != 0) vctrraw |= 0xFFFFFE00;
                    double vctr = vctrraw / 100.0;

                    double rateOfChange = read16BEtoInt(eventData, pos + 5) / 100.0;
                    int sensorStatus = read8toUInt(eventData, pos + 7);
                    int readingStatus = read8toUInt(eventData, pos + 8);

                    boolean backfilledData = (readingStatus & 1) == 1;
                    boolean settingsChanged = (readingStatus & 2) == 2;
                    boolean noisyData = sensorStatus == 1;
                    boolean discardData = sensorStatus == 2;
                    boolean sensorError = sensorStatus == 3;

                    int sensorException = 0;

                    if (sg >= 0x0300) {
                        sensorException = sg & 0x00FF;
                        sg = 0;
                        result += "\n! " + sgtimestamp;
                    } else {
                        result += "\n* " + sgtimestamp;
                    }

                    result += " SGV:" + sg + " ISIG:" + isig + " VCTR:" + vctr + " ROC:" + rateOfChange + " STAT:" + readingStatus
                            + " BF:" + backfilledData + " SC:" + settingsChanged + " NS:" + noisyData + " DD:" + discardData + " SE:" + sensorError + " Exception:" + sensorException;

                    pos += 9;
                }

            } else if (eventType == EventType.OLD_BOLUS_WIZARD_INSULIN_SENSITIVITY
                    || eventType == EventType.NEW_BOLUS_WIZARD_INSULIN_SENSITIVITY) {
                int units = read8toUInt(eventData, index + 0x0B); // 0=mgdl 1=mmol
                int numberOfSegments = read8toUInt(eventData, index + 0x0C);
                result += " Units: " + units + " Segments: " + numberOfSegments;
                int pos = index + 0x0D;
                for (int i = 0; i < numberOfSegments; i++) {
                    int start = read8toUInt(eventData, pos) * 30;
                    double amount = read16BEtoUInt(eventData, pos + 1) / (units == 0 ? 1.0 : 10.0);
                    String time = (start / 60 < 10 ? "0" : "") +  start / 60 + (start % 60 < 30 ? ":00" : ":30");
                    result += " ["  + time + " " + amount + "]";
                    pos += 3;
                }

            } else if (eventType == EventType.OLD_BOLUS_WIZARD_INSULIN_TO_CARB_RATIOS
                    || eventType == EventType.NEW_BOLUS_WIZARD_INSULIN_TO_CARB_RATIOS) {
                int units = read8toUInt(eventData, index + 0x0B); // 0=grams 1=exchanges
                int numberOfSegments = read8toUInt(eventData, index + 0x0C);
                result += " Units: " + units + " Segments: " + numberOfSegments;
                int pos = index + 0x0D;
                for (int i = 0; i < numberOfSegments; i++) {
                    int start = read8toUInt(eventData, pos) * 30;
                    double amount = read32BEtoULong(eventData, pos + 1) / (units == 0 ? 10.0 : 1000.0);
                    String time = (start / 60 < 10 ? "0" : "") +  start / 60 + (start % 60 < 30 ? ":00" : ":30");
                    result += " ["  + time + " " + amount + "]";
                    pos += 5;
                }

            } else if (eventType == EventType.OLD_BOLUS_WIZARD_BG_TARGETS
                    || eventType == EventType.NEW_BOLUS_WIZARD_BG_TARGETS) {
                int units = read8toUInt(eventData, index + 0x0B); // 0=mgdl 1=mmol
                int numberOfSegments = read8toUInt(eventData, index + 0x0C);
                result += " Units: " + units + " Segments: " + numberOfSegments;
                int pos = index + 0x0D;
                for (int i = 0; i < numberOfSegments; i++) {
                    int start = read8toUInt(eventData, pos) * 30;
                    double high = read16BEtoUInt(eventData, pos + 1) / (units == 0 ? 1.0 : 10.0);
                    double low = read16BEtoUInt(eventData, pos + 3) / (units == 0 ? 1.0 : 10.0);
                    String time = (start / 60 < 10 ? "0" : "") +  start / 60 + (start % 60 < 30 ? ":00" : ":30");
                    result += " ["  + time + " " + low + "-" + high + "]";
                    pos += 5;
                }

            } else if (eventType == EventType.OLD_BASAL_PATTERN
                    || eventType == EventType.NEW_BASAL_PATTERN) {
                int pPatternNumber = read8toUInt(eventData, index + 0x0B);
                int numberOfSegments = read8toUInt(eventData, index + 0x0C);
                result += " Pattern: " + pPatternNumber + " Segments: " + numberOfSegments;
                int pos = index + 0x0D;
                for (int i = 0; i < numberOfSegments; i++) {
                    double rate = read32BEtoULong(eventData, pos) / 10000.0;
                    int start = read8toUInt(eventData, pos + 4) * 30;
                    String time = (start / 60 < 10 ? "0" : "") +  start / 60 + (start % 60 < 30 ? ":00" : ":30");
                    result += " [" + time + " " + rate + "U]";
                    pos += 5;
                }

            } else if (eventType == EventType.BASAL_PATTERN_SELECTED) {
                int oldPatternNumber = read8toUInt(eventData, index + 0x0B);
                int newPatternNumber = read8toUInt(eventData, index + 0x0C);
                result += " oldPatternNumber:" + oldPatternNumber + " newPatternNumber:" + newPatternNumber;

            } else if (eventType == EventType.CLOSED_LOOP_TRANSITION) {
                int transitionValue = read8toUInt(eventData, index + 0x0B);
                int transitionReason = read8toUInt(eventData, index + 0x0C);
                result += " transitionValue:" + transitionValue + " transitionReason:" + transitionReason + "]";

            } else if (eventType == EventType.CANNULA_FILL_DELIVERED) {
                int type = read8toUInt(eventData, index + 0x0B);
                double delivered = read32BEtoInt(eventData, index + 0x0C) / 10000.0;
                double remaining = read32BEtoInt(eventData, index + 0x10) / 10000.0;
                result += " type:" + type + " delivered:" + delivered + " remaining:" + remaining;

            } else if (eventType == EventType.DAILY_TOTALS) {
                int rtc = read32BEtoInt(eventData, index + 0x0B);
                int offset = read32BEtoInt(eventData, index + 0x0B + 0x04);
                int duration = read16BEtoUInt(eventData, index + 0x0B + 0x08);

                int meterBgCount = read8toUInt(eventData, index + 0x0B + 0x0A);
                int meterBgAverage = read16BEtoUInt(eventData, index + 0x0B + 0x0B);
                int lowMeterBg = read16BEtoUInt(eventData, index + 0x0B + 0x0D);
                int highMeterBg = read16BEtoUInt(eventData, index + 0x0B + 0x0F);
                int manuallyEnteredBgCount = read8toUInt(eventData, index + 0x0B + 0x11);
                int manuallyEnteredBgAverage = read16BEtoUInt(eventData, index + 0x0B + 0x12);
                int lowManuallyEnteredBg = read16BEtoUInt(eventData, index + 0x0B + 0x14);
                int highManuallyEnteredBg = read16BEtoUInt(eventData, index + 0x0B + 0x16);
                int bgAverage = read16BEtoUInt(eventData, index + 0x0B + 0x18);

                double totalInsulin = read32BEtoInt(eventData, index + 0x0B + 0x1A) / 10000.0;
                double basalInsulin = read32BEtoInt(eventData, index + 0x0B + 0x1E) / 10000.0;
                int basalPercent = read8toUInt(eventData, index + 0x0B + 0x22);
                double bolusInsulin = read32BEtoInt(eventData, index + 0x0B + 0x23) / 10000.0;
                int bolusPercent = read8toUInt(eventData, index + 0x0B + 0x27);

                int carbUnits = read8toUInt(eventData, index + 0x0B + 0x28);
                //int totalFoodInput = read16BEtoUInt(eventData, index + 0x0B + 0x29);
                //not sure this is the correct conversion?
                double totalFoodInput = read16BEtoUInt(eventData, index + 0x0B + 0x29) / (CARB_UNITS.EXCHANGES.equals(carbUnits) ? 10.0 : 1.0);
                int bolusWizardUsageCount = read8toUInt(eventData, index + 0x0B + 0x2B);
                double totalBolusWizardInsulinAsFoodOnlyBolus = read32BEtoInt(eventData, index + 0x0B + 0x2C) / 10000.0;
                double totalBolusWizardInsulinAsCorrectionOnlyBolus = read32BEtoInt(eventData, index + 0x0B + 0x30) / 10000.0;
                double totalBolusWizardInsulinAsFoodAndCorrection = read32BEtoInt(eventData, index + 0x0B + 0x34) / 10000.0;
                double totalManualBolusInsulin = read32BEtoInt(eventData, index + 0x0B + 0x38) / 10000.0;
                int bolusWizardFoodOnlyBolusCount = read8toUInt(eventData, index + 0x0B + 0x3C);
                int bolusWizardCorrectionOnlyBolusCount = read8toUInt(eventData, index + 0x0B + 0x3D);
                int bolusWizardFoodAndCorrectionBolusCount = read8toUInt(eventData, index + 0x0B + 0x3E);
                int manualBolusCount = read8toUInt(eventData, index + 0x0B + 0x3F);

                int sgCount = read16BEtoUInt(eventData, index + 0x0B + 0x40);
                int sgAverage = read16BEtoUInt(eventData, index + 0x0B + 0x42);
                int sgStddev = read16BEtoUInt(eventData, index + 0x0B + 0x44);
                int sgDurationAboveHigh = read16BEtoUInt(eventData, index + 0x0B + 0x46);
                int percentAboveHigh = read8toUInt(eventData, index + 0x0B + 0x48);
                int sgDurationWithinLimit = read16BEtoUInt(eventData, index + 0x0B + 0x49);
                int percentWithinLimit = read8toUInt(eventData, index + 0x0B + 0x4B);
                int sgDurationBelowLow = read16BEtoUInt(eventData, index + 0x0B + 0x4C);
                int percentBelowLow = read8toUInt(eventData, index + 0x0B + 0x4E);
                int lgsSuspensionDuration = read16BEtoUInt(eventData, index + 0x0B + 0x4F);

                int highPredictiveAlerts = read16BEtoUInt(eventData, index + 0x0B + 0x51);
                int lowPredictiveAlerts = read16BEtoUInt(eventData, index + 0x0B + 0x53);
                int lowBgAlerts = read16BEtoUInt(eventData, index + 0x0B + 0x55);
                int highBgAlerts = read16BEtoUInt(eventData, index + 0x0B + 0x57);
                int risingRateAlerts = read16BEtoUInt(eventData, index + 0x0B + 0x59);
                int fallingRateAlerts = read16BEtoUInt(eventData, index + 0x0B + 0x5B);
                int lowGlucoseSuspendAlerts = read16BEtoUInt(eventData, index + 0x0B + 0x5D);
                int predictiveLowGlucoseSuspendAlerts = read16BEtoUInt(eventData, index + 0x0B + 0x5F);

                Date date = MessageUtils.decodeDateTime(rtc & 0xFFFFFFFFL, offset);

                result += String.format(Locale.US, " RTC:%08X OFFSET:%08X\n" +
                                "Date:%s Duration:%s\n" +
                                "TDD:%s Basal:%s %s%% Bolus:%s %s%%\n" +
                                "BG: Count:%s Average:%s(%.1f)\n" +
                                "Meter: Count:%s Average:%s(%.1f) Low:%s(%.1f) High:%s(%.1f)\n" +
                                "Manual: Count:%s Average:%s(%.1f) Low:%s(%.1f) High:%s(%.1f)\n" +
                                "SG: Count:%s Average:%s(%.1f) StdDev:%s(%.1f) High:%s%%(%s) InRange:%s%%(%s) Low:%s%%(%s) Suspended:%s\n" +
                                "Alert: BeforeHigh:%s BeforeLow:%s Low:%s High:%s Rise:%s Fall:%s LGS:%s PLGS:%s\n" +
                                "Wiz: Count:%s Units:%s FoodInput:%s Food:%s(%s) Correction:%s(%s) Food+Cor:%s(%s) Manual:%s(%s)",
                        rtc, offset,
                        date, duration,
                        totalInsulin, basalInsulin, basalPercent, bolusInsulin, bolusPercent,
                        meterBgCount + manuallyEnteredBgCount, bgAverage, bgAverage / MMOLXLFACTOR,
                        meterBgCount, meterBgAverage, meterBgAverage / MMOLXLFACTOR, lowMeterBg, lowMeterBg / MMOLXLFACTOR, highMeterBg, highMeterBg / MMOLXLFACTOR,
                        manuallyEnteredBgCount, manuallyEnteredBgAverage, manuallyEnteredBgAverage / MMOLXLFACTOR, lowManuallyEnteredBg, lowManuallyEnteredBg / MMOLXLFACTOR, highManuallyEnteredBg, highManuallyEnteredBg / MMOLXLFACTOR,
                        sgCount, sgAverage, sgAverage / MMOLXLFACTOR, sgStddev, sgStddev / MMOLXLFACTOR, percentAboveHigh, sgDurationAboveHigh, percentWithinLimit, sgDurationWithinLimit, percentBelowLow, sgDurationBelowLow, lgsSuspensionDuration,
                        highPredictiveAlerts, lowPredictiveAlerts, lowBgAlerts, highBgAlerts, risingRateAlerts, fallingRateAlerts, lowGlucoseSuspendAlerts, predictiveLowGlucoseSuspendAlerts,
                        bolusWizardUsageCount, carbUnits, totalFoodInput,
                        totalBolusWizardInsulinAsFoodOnlyBolus, bolusWizardFoodOnlyBolusCount, totalBolusWizardInsulinAsCorrectionOnlyBolus, bolusWizardCorrectionOnlyBolusCount, totalBolusWizardInsulinAsFoodAndCorrection, bolusWizardFoodAndCorrectionBolusCount,
                        totalManualBolusInsulin, manualBolusCount);

            } else if (eventType == EventType.CLOSED_LOOP_DAILY_TOTALS) {
                int rtc = read32BEtoInt(eventData, index + 0x0B);
                int offset = read32BEtoInt(eventData, index + 0x0B + 0x04);
                int duration = read16BEtoUInt(eventData, index + 0x0B + 0x08);

                int numBg = read8toUInt(eventData, index + 0x0B + 0x0A);
                int msbAvgBg = read16BEtoUInt(eventData, index + 0x0B + 0x0B);
                int lowMeterBg = read16BEtoUInt(eventData, index + 0x0B + 0x0D);
                int highMeterBg = read16BEtoUInt(eventData, index + 0x0B + 0x0F);
                int bgStandardDeviation = read16BEtoUInt(eventData, index + 0x0B + 0x11);

                double dailyTotalAllInsulinDelivered = read32BEtoInt(eventData, index + 0x0B + 0x13) / 10000.0;
                double dailyTotalBasalInsulinDelivered = read32BEtoInt(eventData, index + 0x0B + 0x17) / 10000.0;
                int percentageDailyTotalBasalInsulinDelivered = read8toUInt(eventData, index + 0x0B + 0x1B);
                double dailyTotalBolusInsulinDelivered = read32BEtoInt(eventData, index + 0x0B + 0x1C) / 10000.0;
                int percentageDailyTotalBolusInsulinDelivered = read8toUInt(eventData, index + 0x0B + 0x20);

                double dailyTotalFoodInput = read16BEtoUInt(eventData, index + 0x0B + 0x21);
                double totalBWIFoodOnlyBolus = read32BEtoInt(eventData, index + 0x0B + 0x23) / 10000.0;
                double totalBWICorrectionOnlyBolus = read32BEtoInt(eventData, index + 0x0B + 0x27) / 10000.0;
                double totalBWIFoodCorrectionBolus = read32BEtoInt(eventData, index + 0x0B + 0x2B) / 10000.0;
                double totalOfMealWizardInsulinDeliveredFoodOnlyBolus = read32BEtoInt(eventData, index + 0x0B + 0x2F) / 10000.0;
                double totalOfMealWizardInsulinDeliveredCorrectionOnlyBolus = read32BEtoInt(eventData, index + 0x0B + 0x33) / 10000.0;
                double totalOfMealWizardInsulinDeliveredFoodCorrectionBolus = read32BEtoInt(eventData, index + 0x0B + 0x37) / 10000.0;
                int numOfBolusWizardFoodOnlyBoluses = read8toUInt(eventData, index + 0x0B + 0x3B);
                int numOfBolusWizardCorrectionOnlyBoluses = read8toUInt(eventData, index + 0x0B + 0x3C);
                int numOfBolusWizardFoodCorrectionBoluses = read8toUInt(eventData, index + 0x0B + 0x3D);
                int numOfMealWizardFoodOnlyBoluses = read8toUInt(eventData, index + 0x0B + 0x3E);
                int numOfMealWizardCorrectionOnlyBoluses = read8toUInt(eventData, index + 0x0B + 0x3F);
                int numOfMealWizardFoodCorrectionBoluses = read8toUInt(eventData, index + 0x0B + 0x40);
                int numOfManualBoluses = read8toUInt(eventData, index + 0x0B + 0x41);

                int numOfSensorGlucoseReading = read16BEtoUInt(eventData, index + 0x0B + 0x42);
                int avgSensorGlucoseMesurement = read16BEtoUInt(eventData, index + 0x0B + 0x44);
                int sensorStandardDeviation = read16BEtoUInt(eventData, index + 0x0B + 0x46);
                int durationOfSensorReadingsAboveHiLimit = read16BEtoUInt(eventData, index + 0x0B + 0x48);
                int percentOfdurationOfSensorReadingsAboveHiLimit = read8toUInt(eventData, index + 0x0B + 0x4A);
                int durationofSensorReadingsWithinLimit = read16BEtoUInt(eventData, index + 0x0B + 0x4B);
                int percentOfDurationOfSensorReadingsWithinLimit = read8toUInt(eventData, index + 0x0B + 0x4D);
                int durationOfSensorReadingsBelowLowLimit = read16BEtoUInt(eventData, index + 0x0B + 0x4E);
                int percentofDurationOfSensorReadingsBelowLowLimit = read8toUInt(eventData, index + 0x0B + 0x50);
                int durationSuspend = read16BEtoUInt(eventData, index + 0x0B + 0x51);

                int numberOfThresholdSuspendAlarms = read16BEtoUInt(eventData, index + 0x0B + 0x53);
                int numberOfPredictiveSuspendQuietAndPredictiveSuspendAnnunciateAlarms = read16BEtoUInt(eventData, index + 0x0B + 0x55);

                double totalOfMicroBolusInsulinDelivered = read32BEtoInt(eventData, index + 0x0B + 0x57) / 10000.0;
                int totalTimeInCLActiveMode = read16BEtoUInt(eventData, index + 0x0B + 0x5B);
                int totalTimeInTherapyTargetRange = read16BEtoUInt(eventData, index + 0x0B + 0x5D);
                int totalTimeInAboveTherapyTargetRangeHiLimit = read16BEtoUInt(eventData, index + 0x0B + 0x5F);
                int totalTimeInBelowTherapyTargetRangeLowLimit = read16BEtoUInt(eventData, index + 0x0B + 0x61);
                int numberOfClosedLoopMicroBoluses = read16BEtoUInt(eventData, index + 0x0B + 0x63);

                int unknown1 = read8toUInt(eventData, index + 0x0B + 0x65);
                int unknown2 = read8toUInt(eventData, index + 0x0B + 0x66);

                Date date = MessageUtils.decodeDateTime(rtc & 0xFFFFFFFFL, offset);

                result += String.format(Locale.US, " RTC:%08X OFFSET:%08X\n" +
                                "Date:%s Duration:%s\n" +
                                "TDD:%s Basal:%s %s%% Bolus:%s %s%%\n" +
                                "Meter: Count:%s Average:%s(%.1f) Low:%s(%.1f) High:%s(%.1f)  StdDev:%s(%.1f)\n" +
                                "SG: Count:%s Average:%s(%.1f) StdDev:%s(%.1f) High:%s%%(%s) InRange:%s%%(%s) Low:%s%%(%s) Suspended:%s\n" +
                                "Alert: ThresholdSuspend:%s PredictiveSuspend:%s Unknown1:%s Unknown2:%s\n" +
                                "CL: MicroBolus:%s x%s Active:%s InRange:%s Above:%s Below:%s\n" +
                                "Wiz: FoodInput:%s BWFood:%s x%s BWCorrection:%s x%s BWFood+Cor:%s x%s MWFood:%s x%s MWCorrection:%s x%s MWFood+Cor:%s x%s Manual:x%s)",
                        rtc, offset,
                        date, duration,
                        dailyTotalAllInsulinDelivered, dailyTotalBasalInsulinDelivered, percentageDailyTotalBasalInsulinDelivered, dailyTotalBolusInsulinDelivered, percentageDailyTotalBolusInsulinDelivered,
                        numBg, msbAvgBg, msbAvgBg / MMOLXLFACTOR, lowMeterBg, lowMeterBg / MMOLXLFACTOR, highMeterBg, highMeterBg / MMOLXLFACTOR, bgStandardDeviation, bgStandardDeviation / MMOLXLFACTOR,
                        numOfSensorGlucoseReading, avgSensorGlucoseMesurement, avgSensorGlucoseMesurement / MMOLXLFACTOR, sensorStandardDeviation, sensorStandardDeviation / MMOLXLFACTOR,
                        percentOfdurationOfSensorReadingsAboveHiLimit, durationOfSensorReadingsAboveHiLimit, percentOfDurationOfSensorReadingsWithinLimit, durationofSensorReadingsWithinLimit, percentofDurationOfSensorReadingsBelowLowLimit, durationOfSensorReadingsBelowLowLimit,
                        durationSuspend,
                        numberOfThresholdSuspendAlarms, numberOfPredictiveSuspendQuietAndPredictiveSuspendAnnunciateAlarms, unknown1, unknown2,
                        totalOfMicroBolusInsulinDelivered, numberOfClosedLoopMicroBoluses, totalTimeInCLActiveMode, totalTimeInTherapyTargetRange, totalTimeInAboveTherapyTargetRangeHiLimit, totalTimeInBelowTherapyTargetRangeLowLimit,
                        dailyTotalFoodInput,
                        totalBWIFoodOnlyBolus, numOfBolusWizardFoodOnlyBoluses, totalBWICorrectionOnlyBolus,numOfBolusWizardCorrectionOnlyBoluses, totalBWIFoodCorrectionBolus, numOfBolusWizardFoodCorrectionBoluses,
                        totalOfMealWizardInsulinDeliveredFoodOnlyBolus, numOfMealWizardFoodOnlyBoluses, totalOfMealWizardInsulinDeliveredCorrectionOnlyBolus, numOfMealWizardCorrectionOnlyBoluses, totalOfMealWizardInsulinDeliveredFoodCorrectionBolus, numOfMealWizardFoodCorrectionBoluses,
                        numOfManualBoluses
                );

            } else if (eventType == EventType.FOOD_EVENT_MARKER) {
                int rtc = read32BEtoInt(eventData, index + 0x0B);
                int offset = read32BEtoInt(eventData, index + 0x0B + 0x04);
                int carbUnits = read8toUInt(eventData, index + 0x0B + 0x08);
                double carbInput = read16BEtoUInt(eventData, index + + 0x0B + 0x09) / (CARB_UNITS.EXCHANGES.equals(carbUnits) ? 10.0 : 1.0);
                Date date = MessageUtils.decodeDateTime(rtc & 0xFFFFFFFFL, offset);
                result += String.format(Locale.US,
                        " RTC:%08X OFFSET:%08X Date:%s carbUnits:%s carbInput:%s",
                        rtc, offset, date, carbUnits, carbInput);

            } else if (eventType == EventType.EXERCISE_EVENT_MARKER) {
                int rtc = read32BEtoInt(eventData, index + 0x0B);
                int offset = read32BEtoInt(eventData, index + 0x0B + 0x04);
                int duration = read16BEtoUInt(eventData, index + 0x0B + 0x08);
                Date date = MessageUtils.decodeDateTime(rtc & 0xFFFFFFFFL, offset);
                result += String.format(Locale.US,
                        " RTC:%08X OFFSET:%08X Date:%s Duration:%s",
                        rtc, offset, date, duration);

            } else if (eventType == EventType.INJECTION_EVENT_MARKER) {
                int rtc = read32BEtoInt(eventData, index + 0x0B);
                int offset = read32BEtoInt(eventData, index + 0x0B + 0x04);
                double insulin = read32BEtoInt(eventData, index + 0x0B + 0x08) / 10000.0;
                Date date = MessageUtils.decodeDateTime(rtc & 0xFFFFFFFFL, offset);
                result += String.format(Locale.US,
                        " RTC:%08X OFFSET:%08X Date:%s Insulin:%s",
                        rtc, offset, date, insulin);

            } else if (eventType == EventType.LOW_RESERVOIR) {
                int warningType = read8toUInt(eventData, index + 0x0B);
                int hoursRemaining = read8toUInt(eventData, index + 0x0C);
                int minutesRemaining = read8toUInt(eventData, index + 0x0D);
                double unitsRemaining = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
                result += String.format(Locale.US,
                        " warningType:%s hoursRemaining:%s minutesRemaining:%s unitsRemaining:%s",
                        warningType, hoursRemaining, minutesRemaining, unitsRemaining);

            } else if (eventType == EventType.ALARM_NOTIFICATION) {
                int faultNumber = read16BEtoInt(eventData, index + 0x0B);
                int notificationMode = read8toUInt(eventData, index + 0x11);
                boolean extraData = (eventData[index + 0x12] & 2) == 2;
                boolean alarmHistory = (eventData[index + 0x12] & 4) == 4;
                byte[] alarmData = Arrays.copyOfRange(eventData,index + 0x13,index + 0x1D);
                result += String.format(Locale.US,
                        " faultNumber:%s mode:%s extra:%s history:%s data:%s",
                        faultNumber, notificationMode, extraData, alarmHistory, HexDump.toHexString(alarmData));

            } else if (eventType == EventType.ALARM_CLEARED) {
                int faultNumber = read16BEtoInt(eventData, index + 0x0B);
                result += String.format(Locale.US,
                        " faultNumber:%s",
                        faultNumber);

            } else if (eventType == EventType.CLOSED_LOOP_ALARM_AUTO_CLEARED) {
                result += HexDump.dumpHexString(eventData, index + 0x0B, eventSize - 0x0B);

            } else if (eventType == EventType.OTHER_EVENT_MARKER
                    || eventType == EventType.GLUCOSE_SENSOR_CHANGE
                    || eventType == EventType.BATTERY_INSERTED
                    || eventType == EventType.BATTERY_REMOVED
                    || eventType == EventType.REWIND
                    || eventType == EventType.START_OF_DAY_MARKER
                    || eventType == EventType.END_OF_DAY_MARKER
            ) {
                result += " (no data)";

            }

            //else result += HexDump.dumpHexString(eventData, index + 0x0B, eventSize - 0x0B);

            if (eventType != EventType.PLGM_CONTROLLER_STATE
                    //&& eventType != EventType.ALARM_NOTIFICATION
                    //&& eventType != EventType.ALARM_CLEARED
                    && eventType != EventType.CLOSED_LOOP_STATUS_DATA
                    && eventType != EventType.CLOSED_LOOP_PERIODIC_DATA
            )
                Log.d(TAG, result);

            index += eventSize;
            event++;
        }
    }

    public enum EventType {
        TIME_RESET(0x02),
        USER_TIME_DATE_CHANGE(0x03),
        SOURCE_ID_CONFIGURATION(0x04),
        NETWORK_DEVICE_CONNECTION(0x05),
        AIRPLANE_MODE(0x06),
        START_OF_DAY_MARKER(0x07),
        END_OF_DAY_MARKER(0x08),
        PLGM_CONTROLLER_STATE(0x0B),
        CLOSED_LOOP_STATUS_DATA(0x0C),
        CLOSED_LOOP_PERIODIC_DATA(0x0D),
        CLOSED_LOOP_DAILY_DATA(0x0E),
        NORMAL_BOLUS_PROGRAMMED(0x15),
        SQUARE_BOLUS_PROGRAMMED(0x16),
        DUAL_BOLUS_PROGRAMMED(0x17),
        CANNULA_FILL_DELIVERED(0x1A),
        TEMP_BASAL_PROGRAMMED(0x1B),
        BASAL_PATTERN_SELECTED(0x1C),
        BASAL_SEGMENT_START(0x1D),
        INSULIN_DELIVERY_STOPPED(0x1E),
        INSULIN_DELIVERY_RESTARTED(0x1F),
        SELF_TEST_REQUESTED(0x20),
        SELF_TEST_RESULTS(0x21),
        TEMP_BASAL_COMPLETE(0x22),
        BOLUS_SUSPENDED(0x24),
        SUSPENDED_BOLUS_RESUMED(0x25),
        SUSPENDED_BOLUS_CANCELED(0x26),
        BOLUS_CANCELED(0x27),
        ALARM_NOTIFICATION(0x28),
        ALARM_CLEARED(0x2A),
        LOW_RESERVOIR(0x2B),
        BATTERY_INSERTED(0x2C),
        FOOD_EVENT_MARKER(0x2E),
        EXERCISE_EVENT_MARKER(0x2F),
        INJECTION_EVENT_MARKER(0x30),
        OTHER_EVENT_MARKER(0x31),
        BG_READING(0x32),
        CODE_UPDATE(0x33),
        MISSED_MEAL_BOLUS_REMINDER_EXPIRED(0x34),
        REWIND(0x36),
        BATTERY_REMOVED(0x37),
        CALIBRATION_COMPLETE(0x38),
        ACTIVE_INSULIN_CLEARED(0x39),
        DAILY_TOTALS(0x3C),
        BOLUS_WIZARD_ESTIMATE(0x3D),
        MEAL_WIZARD_ESTIMATE(0x3E),
        CLOSED_LOOP_DAILY_TOTALS(0x3F),
        USER_SETTINGS_SAVE(0x50),
        USER_SETTINGS_RESET_TO_DEFAULTS(0x51),
        OLD_BASAL_PATTERN(0x52),
        NEW_BASAL_PATTERN(0x53),
        OLD_PRESET_TEMP_BASAL(0x54),
        NEW_PRESET_TEMP_BASAL(0x55),
        OLD_PRESET_BOLUS(0x56),
        NEW_PRESET_BOLUS(0x57),
        MAX_BASAL_RATE_CHANGE(0x58),
        MAX_BOLUS_CHANGE(0x59),
        PERSONAL_REMINDER_CHANGE(0x5A),
        MISSED_MEAL_BOLUS_REMINDER_CHANGE(0x5B),
        BOLUS_INCREMENT_CHANGE(0x5C),
        BOLUS_WIZARD_SETTINGS_CHANGE(0x5D),
        OLD_BOLUS_WIZARD_INSULIN_SENSITIVITY(0x5E),
        NEW_BOLUS_WIZARD_INSULIN_SENSITIVITY(0x5F),
        OLD_BOLUS_WIZARD_INSULIN_TO_CARB_RATIOS(0x60),
        NEW_BOLUS_WIZARD_INSULIN_TO_CARB_RATIOS(0x61),
        OLD_BOLUS_WIZARD_BG_TARGETS(0x62),
        NEW_BOLUS_WIZARD_BG_TARGETS(0x63),
        DUAL_BOLUS_OPTION_CHANGE(0x64),
        SQUARE_BOLUS_OPTION_CHANGE(0x65),
        EASY_BOLUS_OPTION_CHANGE(0x66),
        BG_REMINDER_OPTION_CHANGE(0x68),
        BG_REMINDER_TIME(0x69),
        AUDIO_VIBRATE_MODE_CHANGE(0x6A),
        TIME_FORMAT_CHANGE(0x6B),
        LOW_RESERVOIR_WARNING_CHANGE(0x6C),
        LANGUAGE_CHANGE(0x6D),
        STARTUP_WIZARD_START_END(0x6E),
        REMOTE_BOLUS_OPTION_CHANGE(0x6F),
        AUTO_SUSPEND_CHANGE(0x72),
        BOLUS_DELIVERY_RATE_CHANGE(0x73),
        DISPLAY_OPTION_CHANGE(0x77),
        SET_CHANGE_REMINDER_CHANGE(0x78),
        BLOCK_MODE_CHANGE(0x79),
        BOLUS_WIZARD_SETTINGS_SUMMARY(0x7B),
        CLOSED_LOOP_BG_READING(0x82),
        CLOSED_LOOP_OPTION_CHANGE(0x86),
        CLOSED_LOOP_SETTINGS_CHANGED(0x87),
        CLOSED_LOOP_TEMP_TARGET_STARTED(0x88),
        CLOSED_LOOP_TEMP_TARGET_ENDED(0x89),
        CLOSED_LOOP_ALARM_AUTO_CLEARED(0x8A),
        SENSOR_SETTINGS_CHANGE(0xC8),
        OLD_SENSOR_WARNING_LEVELS(0xC9),
        NEW_SENSOR_WARNING_LEVELS(0xCA),
        GENERAL_SENSOR_SETTINGS_CHANGE(0xCB),
        SENSOR_GLUCOSE_READINGS(0xCC),
        SENSOR_GLUCOSE_GAP(0xCD),
        GLUCOSE_SENSOR_CHANGE(0xCE),
        SENSOR_CALIBRATION_REJECTED(0xCF),
        SENSOR_ALERT_SILENCE_STARTED(0xD0),
        SENSOR_ALERT_SILENCE_ENDED(0xD1),
        OLD_LOW_SENSOR_WARNING_LEVELS(0xD2),
        NEW_LOW_SENSOR_WARNING_LEVELS(0xD3),
        OLD_HIGH_SENSOR_WARNING_LEVELS(0xD4),
        NEW_HIGH_SENSOR_WARNING_LEVELS(0xD5),
        SENSOR_GLUCOSE_READINGS_EXTENDED(0xD6),
        NORMAL_BOLUS_DELIVERED(0xDC),
        SQUARE_BOLUS_DELIVERED(0xDD),
        DUAL_BOLUS_PART_DELIVERED(0xDE),
        CLOSED_LOOP_TRANSITION(0xDF),
        NO_TYPE(0x00);

        private int event;

        EventType(int event) {
            this.event = event;
        }

        public static EventType convert(int value) {
            for (EventType eventType : EventType.values())
                if (eventType.event == value) return eventType;
            return EventType.NO_TYPE;
        }
    }

    public enum CGM_EXCEPTION {
        SENSOR_OK(0x00,
                R.string.sensor_state__ok,
                R.string.sensor_state__ok),
        SENSOR_INIT(0x01,
                R.string.sensor_state__warm_up,
                R.string.sensor_state__warm_up_abbreviation),
        SENSOR_CAL_NEEDED(0x02,
                R.string.sensor_state__calibrate_now,
                R.string.sensor_state__calibrate_now_abbreviation),
        SENSOR_ERROR(0x03,
                R.string.sensor_state__sg_not_available,
                R.string.sensor_state__sg_not_available_abbreviation),
        SENSOR_CAL_ERROR(0x04,
                R.string.sensor_state__cal_error,
                R.string.sensor_state__cal_error_abbreviation),
        SENSOR_CHANGE_SENSOR_ERROR(0x05,
                R.string.sensor_state__change_sensor,
                R.string.sensor_state__change_sensor_abbreviation),
        SENSOR_END_OF_LIFE(0x06,
                R.string.sensor_state__sensor_expired,
                R.string.sensor_state__sensor_expired_abbreviation),
        SENSOR_NOT_READY(0x07,
                R.string.sensor_state__not_ready,
                R.string.sensor_state__not_ready_abbreviation),
        SENSOR_READING_HIGH(0x08,
                R.string.sensor_state__reading_high,
                R.string.sensor_state__reading_high_abbreviation),
        SENSOR_READING_LOW(0x09,
                R.string.sensor_state__reading_low,
                R.string.sensor_state__reading_low_abbreviation),
        SENSOR_CAL_PENDING(0x0A,
                R.string.sensor_state__calibrating,
                R.string.sensor_state__calibrating_abbreviation),
        SENSOR_CHANGE_CAL_ERROR(0x0B,
                R.string.sensor_state__cal_error_change_sensor,
                R.string.sensor_state__cal_error_change_sensor_abbreviation),
        SENSOR_TIME_UNKNOWN(0x0C,
                R.string.sensor_state__time_unknown,
                R.string.sensor_state__time_unknown_abbreviation),
        NA(-1,
                R.string.sensor_state__sensor_error,
                R.string.sensor_state__sensor_error_abbreviation);

        private int value;
        private int stringId;
        private int abbreviationId;

        CGM_EXCEPTION(int value, int stringId, int abbreviationId) {
            this.value = value;
            this.stringId = stringId;
            this.abbreviationId = abbreviationId;
        }

        public int value() {
            return this.value;
        }

        public int stringId() {
            return this.stringId;
        }

        public String string() {
            return FormatKit.getInstance().getString(this.stringId);
        }

        public int abbriviationId() {
            return this.abbreviationId;
        }

        public String abbriviation() {
            return FormatKit.getInstance().getString(this.abbreviationId);
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static CGM_EXCEPTION convert(int value) {
            for (CGM_EXCEPTION cgm_exception : CGM_EXCEPTION.values())
                if (cgm_exception.value == value) return cgm_exception;
            return CGM_EXCEPTION.NA;
        }
    }

    public enum SUSPEND_REASON {
        ALARM_SUSPEND(1,
                FormatKit.getInstance().getString(R.string.pump_suspend__alarm_suspend)),
        USER_SUSPEND(2,
                FormatKit.getInstance().getString(R.string.pump_suspend__user_suspend)),
        AUTO_SUSPEND(3,
                FormatKit.getInstance().getString(R.string.pump_suspend__auto_suspend)),
        LOWSG_SUSPEND(4
                , FormatKit.getInstance().getString(R.string.pump_suspend__low_glucose_suspend)),
        SET_CHANGE_SUSPEND(5,
                FormatKit.getInstance().getString(R.string.pump_suspend__set_change_suspend)),
        PLGM_PREDICTED_LOW_SG(10,
                FormatKit.getInstance().getString(R.string.pump_suspend__predicted_low_glucose_suspend)),
        NA(-1, "");

        private int value;
        private String string;

        SUSPEND_REASON(int value, String string) {
            this.value = value;
            this.string = string;
        }

        public byte value() {
            return (byte) this.value;
        }

        public String string() {
            return this.string;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static SUSPEND_REASON convert(int value) {
            for (SUSPEND_REASON suspend_reason : SUSPEND_REASON.values())
                if (suspend_reason.value == value) return suspend_reason;
            return SUSPEND_REASON.NA;
        }
    }

    public enum RESUME_REASON {
        USER_SELECTS_RESUME(1,
                FormatKit.getInstance().getString(R.string.pump_resume__user_resumed)),
        USER_CLEARS_ALARM(2,
                FormatKit.getInstance().getString(R.string.pump_resume__user_clears_alarm)),
        LGM_MANUAL_RESUME(3,
                FormatKit.getInstance().getString(R.string.pump_resume__low_glucose_manual_resume)),
        LGM_AUTO_RESUME_MAX_SUSP(4,
                FormatKit.getInstance().getString(R.string.pump_resume__low_glucose_auto_resume_max_suspend_period)), // After an auto suspend, but no CGM data afterwards.
        LGM_AUTO_RESUME_PSG_SG(5,
                FormatKit.getInstance().getString(R.string.pump_resume__low_glucose_auto_resume_preset_glucose_reached)), // When SG reaches the Preset SG level
        LGM_MANUAL_RESUME_VIA_DISABLE(6,
                FormatKit.getInstance().getString(R.string.pump_resume__low_glucose_manual_resume_via_disable)),
        NA(-1, "");

        private int value;
        private String string;

        RESUME_REASON(int value, String string) {
            this.value = value;
            this.string = string;
        }

        public byte value() {
            return (byte) this.value;
        }

        public String string() {
            return this.string;
        }
        public boolean equals(int value) {
            return this.value == value;
        }

        public static RESUME_REASON convert(int value) {
            for (RESUME_REASON resume_reason : RESUME_REASON.values())
                if (resume_reason.value == value) return resume_reason;
            return RESUME_REASON.NA;
        }
    }

    public enum BOLUS_SOURCE {
        MANUAL(0),
        BOLUS_WIZARD(1),
        EASY_BOLUS(2),
        PRESET_BOLUS(4),
        CLOSED_LOOP_MICRO_BOLUS(5),
        CLOSED_LOOP_BG_CORRECTION(6),
        CLOSED_LOOP_FOOD_BOLUS(7),
        CLOSED_LOOP_BG_CORRECTION_AND_FOOD_BOLUS(8),
        NA(-1);

        private int value;

        BOLUS_SOURCE(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) this.value;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static BOLUS_SOURCE convert(int value) {
            for (BOLUS_SOURCE bolus_source : BOLUS_SOURCE.values())
                if (bolus_source.value == value) return bolus_source;
            return BOLUS_SOURCE.NA;
        }
    }

    public enum BG_SOURCE {
        EXTERNAL_METER(1),
        BOLUS_WIZARD(2),
        BG_EVENT_MARKER(3),
        SENSOR_CAL(4),
        NA(-1);

        private int value;

        BG_SOURCE(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) this.value;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static BG_SOURCE convert(int value) {
            for (BG_SOURCE bg_source : BG_SOURCE.values())
                if (bg_source.value == value) return bg_source;
            return BG_SOURCE.NA;
        }
    }

    public enum BG_CONTEXT {
        BG_READING_RECEIVED(0),
        USER_ACCEPTED_REMOTE_BG(1),
        USER_REJECTED_REMOTE_BG(2),
        REMOTE_BG_ACCEPTANCE_SCREEN_TIMEOUT(3),
        BG_SI_PASS_RESULT_RECD_FRM_GST(4),
        BG_SI_FAIL_RESULT_RECD_FRM_GST(5),
        BG_SENT_FOR_CALIB(6),
        USER_REJECTED_SENSOR_CALIB(7),
        ENTERED_IN_BG_ENTRY(8),
        ENTERED_IN_MEAL_WIZARD(9),
        ENTERED_IN_BOLUS_WIZRD(10),
        ENTERED_IN_SENSOR_CALIB(11),
        ENTERED_AS_BG_MARKER(12),
        NA(-1);

        private int value;

        BG_CONTEXT(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) this.value;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static BG_CONTEXT convert(int value) {
            for (BG_CONTEXT bg_context : BG_CONTEXT.values())
                if (bg_context.value == value) return bg_context;
            return BG_CONTEXT.NA;
        }
    }

    public enum BG_UNITS {
        MG_DL(0),
        MMOL_L(1),
        NA(-1);

        private int value;

        BG_UNITS(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) this.value;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static BG_UNITS convert(int value) {
            for (BG_UNITS bg_units : BG_UNITS.values())
                if (bg_units.value == value) return bg_units;
            return BG_UNITS.NA;
        }
    }

    public enum CARB_UNITS {
        GRAMS(0),
        EXCHANGES(1),
        NA(-1);

        private int value;

        CARB_UNITS(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) this.value;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static CARB_UNITS convert(int value) {
            for (CARB_UNITS carb_units : CARB_UNITS.values())
                if (carb_units.value == value) return carb_units;
            return CARB_UNITS.NA;
        }
    }

    public enum BG_ORIGIN {
        MANUALLY_ENTERED(0),
        RECEIVED_FROM_RF(1),
        NA(-1);

        private int value;

        BG_ORIGIN(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) this.value;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static BG_ORIGIN convert(int value) {
            for (BG_ORIGIN bg_origin : BG_ORIGIN.values())
                if (bg_origin.value == value) return bg_origin;
            return BG_ORIGIN.NA;
        }
    }

    public enum TEMP_BASAL_TYPE {
        ABSOLUTE(0),
        PERCENT(1),
        NA(-1);

        private int value;

        TEMP_BASAL_TYPE(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) this.value;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static TEMP_BASAL_TYPE convert(int value) {
            for (TEMP_BASAL_TYPE temp_basal_type : TEMP_BASAL_TYPE.values())
                if (temp_basal_type.value == value) return temp_basal_type;
            return TEMP_BASAL_TYPE.NA;
        }
    }

    public enum BOLUS_STEP_SIZE {
        STEP_0_POINT_025(0),
        STEP_0_POINT_05(1),
        STEP_0_POINT_1(2),
        NA(-1);

        private int value;

        BOLUS_STEP_SIZE(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) this.value;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static BOLUS_STEP_SIZE convert(int value) {
            for (BOLUS_STEP_SIZE bolus_step_size : BOLUS_STEP_SIZE.values())
                if (bolus_step_size.value == value) return bolus_step_size;
            return BOLUS_STEP_SIZE.NA;
        }
    }

    public enum CANNULA_FILL_TYPE {
        TUBING_FILL(0),
        CANULLA_FILL(1),
        NA(-1);

        private int value;

        CANNULA_FILL_TYPE(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) this.value;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static CANNULA_FILL_TYPE convert(int value) {
            for (CANNULA_FILL_TYPE cannula_fill_type : CANNULA_FILL_TYPE.values())
                if (cannula_fill_type.value == value) return cannula_fill_type;
            return CANNULA_FILL_TYPE.NA;
        }
    }

    public enum CL_TRANSITION_REASON {
        INTO_ACTIVE_DUE_TO_GLUCOSE_SENSOR_CALIBRATION(0,
                FormatKit.getInstance().getString(R.string.automode_transition__into_active_due_to_sensor_calibration)),
        OUT_OF_ACTIVE_DUE_TO_USER_OVERRIDE(1,
                FormatKit.getInstance().getString(R.string.automode_transition__out_of_active_due_to_user_override)),
        OUT_OF_ACTIVE_DUE_TO_ALARM(2,
                FormatKit.getInstance().getString(R.string.automode_transition__out_of_active_due_to_alarm)),
        OUT_OF_ACTIVE_DUE_TO_TIMEOUT_FROM_SAFE_BASAL(3,
                FormatKit.getInstance().getString(R.string.automode_transition__out_of_active_due_to_timeout_from_safe_basal)),
        OUT_OF_ACTIVE_DUE_TO_PROLONGED_HIGH_SG(4,
                FormatKit.getInstance().getString(R.string.automode_transition__out_of_active_due_to_prolonged_high_sg)),
        NA(-1, "");

        private int value;
        private String string;

        CL_TRANSITION_REASON(int value, String string) {
            this.value = value;
            this.string = string;
        }

        public byte value() {
            return (byte) this.value;
        }

        public String string() {
            return this.string;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static CL_TRANSITION_REASON convert(int value) {
            for (CL_TRANSITION_REASON cl_transition_reason : CL_TRANSITION_REASON.values())
                if (cl_transition_reason.value == value) return cl_transition_reason;
            return CL_TRANSITION_REASON.NA;
        }
    }

    public enum CL_TRANSITION_VALUE {
        CL_OUT_OF_ACTIVE(0),
        CL_INTO_ACTIVE(1),
        NA(-1);

        private int value;

        CL_TRANSITION_VALUE(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) this.value;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static CL_TRANSITION_VALUE convert(int value) {
            for (CL_TRANSITION_VALUE cl_transition_value : CL_TRANSITION_VALUE.values())
                if (cl_transition_value.value == value) return cl_transition_value;
            return CL_TRANSITION_VALUE.NA;
        }
    }

    public enum BOLUS_TYPE {
        NORMAL_BOLUS(0),
        SQUARE_WAVE(1),
        DUAL_WAVE(2),
        NA(-1);

        private int value;

        BOLUS_TYPE(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) this.value;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static BOLUS_TYPE convert(int value) {
            for (BOLUS_TYPE bolus_type : BOLUS_TYPE.values())
                if (bolus_type.value == value) return bolus_type;
            return BOLUS_TYPE.NA;
        }
    }

    public enum DUAL_BOLUS_PART {
        NORMAL_BOLUS(1),
        SQUARE_WAVE(2),
        NA(-1);

        private int value;

        DUAL_BOLUS_PART(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) this.value;
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static DUAL_BOLUS_PART convert(int value) {
            for (DUAL_BOLUS_PART dual_bolus_part : DUAL_BOLUS_PART.values())
                if (dual_bolus_part.value == value) return dual_bolus_part;
            return DUAL_BOLUS_PART.NA;
        }
    }

    public enum BOLUS_PRESET {
        BOLUS_PRESET_0(0),
        BOLUS_PRESET_1(1),
        BOLUS_PRESET_2(2),
        BOLUS_PRESET_3(3),
        BOLUS_PRESET_4(4),
        BOLUS_PRESET_5(5),
        BOLUS_PRESET_6(6),
        BOLUS_PRESET_7(7),
        BOLUS_PRESET_8(8),
        NA(-1);

        private int value;

        BOLUS_PRESET(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) this.value;
        }

        public String string() {
            return FormatKit.getInstance().getNameBolusPreset(value);
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static BOLUS_PRESET convert(int value) {
            for (BOLUS_PRESET bolus_preset : BOLUS_PRESET.values())
                if (bolus_preset.value == value) return bolus_preset;
            return BOLUS_PRESET.NA;
        }
    }

    public enum TEMP_BASAL_PRESET {
        TEMP_BASAL_PRESET_0(0),
        TEMP_BASAL_PRESET_1(1),
        TEMP_BASAL_PRESET_2(2),
        TEMP_BASAL_PRESET_3(3),
        TEMP_BASAL_PRESET_4(4),
        TEMP_BASAL_PRESET_5(5),
        TEMP_BASAL_PRESET_6(6),
        TEMP_BASAL_PRESET_7(7),
        TEMP_BASAL_PRESET_8(8),
        NA(-1);

        private int value;

        TEMP_BASAL_PRESET(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) this.value;
        }

        public String string() {
            return FormatKit.getInstance().getNameTempBasalPreset(value);
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static TEMP_BASAL_PRESET convert(int value) {
            for (TEMP_BASAL_PRESET temp_basal_preset : TEMP_BASAL_PRESET.values())
                if (temp_basal_preset.value == value) return temp_basal_preset;
            return TEMP_BASAL_PRESET.NA;
        }
    }

    public enum BASAL_PATTERN {
        BASAL_PATTERN_1(1),
        BASAL_PATTERN_2(2),
        BASAL_PATTERN_3(3),
        BASAL_PATTERN_4(4),
        BASAL_PATTERN_5(5),
        BASAL_PATTERN_6(6),
        BASAL_PATTERN_7(7),
        BASAL_PATTERN_8(8),
        NA(-1);

        private int value;

        BASAL_PATTERN(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) this.value;
        }

        public String string() {
            return FormatKit.getInstance().getNameBasalPattern(value);
        }

        public boolean equals(int value) {
            return this.value == value;
        }

        public static BASAL_PATTERN convert(int value) {
            for (BASAL_PATTERN basal_pattern : BASAL_PATTERN.values())
                if (basal_pattern.value == value) return basal_pattern;
            return BASAL_PATTERN.NA;
        }
    }

}

/*

EventType.DAILY_TOTALS

     ?0 ?1 ?2 ?3 ?4 ?5 ?6 ?7 ?8 ?9 ?A ?B ?C ?D ?E ?F
0x00 CR CR CR CR CO CO CO CO CD CD BG BA BA BL BL BH
0x10 BH MG MA MA ML ML MH MH BV BV TD TD TD TD TB TB
0x20 TB TB PB TI TI TI TI PI WW WA WA WB WC WC WC WC
0x30 WD WD WD WD WE WE WE WE WF WF WF WF WG WH WI WJ
0x40 ST ST SA SA SD SD SX SX SH SY SY SI SZ SZ SL SK
0x50 SK AA AA AB AB AC AC AD AD AE AE AF AF AG AG AH
0x60 AH

CR = RTC
CO - OFFSET
CD = DURATION (mins)

BG = METER_BG_COUNT
BA = METER_BG_AVERAGE (mg/dL)
BL = LOW_METER_BG (mg/dL)
BH = HIGH_METER_BG (mg/dL)
MG = MANUALLY_ENTERED_BG_COUNT
MA = MANUALLY_ENTERED_BG_AVERAGE (mg/dL)
ML = LOW_MANUALLY_ENTERED_BG (mg/dL)
MH = HIGH_MANUALLY_ENTERED_BG (mg/dL)
BV = BG_AVERAGE (mg/dL)

TD = TOTAL_INSULIN (div 10000)
TB = BASAL_INSULIN (div 10000)
PB = BASAL_PERCENT
TI = BOLUS_INSULIN (div 10000)
PI = BOLUS_PERCENT

WW = CARB_UNITS
WA = TOTAL_FOOD_INPUT
WB = BOLUS_WIZARD_USAGE_COUNT
WC = TOTAL_BOLUS_WIZARD_INSULIN_AS_FOOD_ONLY_BOLUS (div 10000)
WD = TOTAL_BOLUS_WIZARD_INSULIN_AS_CORRECTION_ONLY_BOLUS (div 10000)
WE = TOTAL_BOLUS_WIZARD_INSULIN_AS_FOOD_AND_CORRECTION (div 10000)
WF = TOTAL_MANUAL_BOLUS_INSULIN (div 10000)
WG = BOLUS_WIZARD_FOOD_ONLY_BOLUS_COUNT
WH = BOLUS_WIZARD_CORRECTION_ONLY_BOLUS_COUNT
WI = BOLUS_WIZARD_FOOD_AND_CORRECTION_BOLUS_COUNT
WJ = MANUAL_BOLUS_COUNT

ST = SG_COUNT
SA = SG_AVERAGE (mg/dL)
SD = SG_STDDEV (mg/dL)
SX = SG_DURATION_ABOVE_HIGH (mins)
SH = PERCENT_ABOVE_HIGH
SX = SG_DURATION_WITHIN_LIMIT (mins)
SI = PERCENT_WITHIN_LIMIT
SZ = SG_DURATION_BELOW_LOW (mins)
SL = PERCENT_BELOW_LOW
SK = LGS_SUSPENSION_DURATION (mins)

AA = HIGH_PREDICTIVE_ALERTS
AB = LOW_PREDICTIVE_ALERTS
AC = LOW_BG_ALERTS
AD = HIGH_BG_ALERTS
AE = RISING_RATE_ALERTS
AF = FALLING_RATE_ALERTS
AG = LOW_GLUCOSE_SUSPEND_ALERTS
AH = PREDICTIVE_LOW_GLUCOSE_SUSPEND_ALERTS


EventType.CLOSED_LOOP_DAILY_TOTALS

     ?0 ?1 ?2 ?3 ?4 ?5 ?6 ?7 ?8 ?9 ?A ?B ?C ?D ?E ?F
0x00 CR CR CR CR CO CO CO CO CD CD BG BA BA BL BL BH
0x10 BH BS BS TD TD TD TD TB TB TB TB PB TI TI TI TI
0x20 PI WW WW WA WA WA WA WB WB WB WB WC WC WC WC WD
0x30 WD WD WD WE WE WE WE WF WF WF WF WG WH WI WJ WK
0x40 WL WM ST ST SA SA SD SD SX SX SH SY SY SI SZ SZ
0x50 SL SK SK AA AA AB AB LA LA LA LA LB LB LC LC LD
0x60 LD LE LE LF LF QA QB

CR = RTC
CO - OFFSET
CD = DURATION (mins) "Duration covered by this data"

BG = numBg
BA = msbAvgBg (mg/dL) "Avg meter BG"
BL = lowMeterBg (mg/dL) "Low meter BG"
BH = highMeterBg (mg/dL) "High meter BG"
BS = bgStandardDeviation (mg/dL) "BG Standard Deviation"

TD = dailyTotalAllInsulinDelivered (div 10000)
TB = dailyTotalBasalInsulinDelivered (div 10000)
PB = percentageDailyTotalBasalInsulinDelivered "Percentage of Daily Total Delivered as Basal Insulin"
TI = dailyTotalBolusInsulinDelivered (div 10000)
PI = percentageDailyTotalBolusInsulinDelivered "Percentage of Daily Total Delivered as Bolus Insulin"

WW = dailyTotalFoodInput "Total daily Food Input"
WA = totalBWIFoodOnlyBolus (div 10000)
WB = totalBWICorrectionOnlyBolus (div 10000)
WC = totalBWIFoodCorrectionBolus (div 10000)
WD = totalOfMealWizardInsulinDeliveredFoodOnlyBolus (div 10000)
WE = totalOfMealWizardInsulinDeliveredCorrectionOnlyBolus (div 10000)
WF = totalOfMealWizardInsulinDeliveredFoodCorrectionBolus (div 10000)
WG = numOfBolusWizardFoodOnlyBoluses
WH = numOfBolusWizardCorrectionOnlyBoluses
WI = numOfBolusWizardFoodCorrectionBoluses
WJ = numOfMealWizardFoodOnlyBoluses
WK = numOfMealWizardCorrectionOnlyBoluses
WL = numOfMealWizardFoodCorrectionBoluses
WM = numOfManualBoluses

ST = numOfSensorGlucoseReading "Number of Sensor Glucose Readings"
SA = avgSensorGlucoseMesurement (mg/dL) "Avg sensor glucose measurement in mg/dl"
SD = sensorStandardDeviation (mg/dL) "Sensor standard deviation in mg/dl"
SX = durationOfSensorReadingsAboveHiLimit (mins) "Duration of Sensor Readings above Hi Limit in mins"
SH = percentOfdurationOfSensorReadingsAboveHiLimit "Percentage of duration of Sensor Readings Above High Limit"
SX = durationofSensorReadingsWithinLimit (mins) "Duration of Sensor Readings within Limit in mins"
SI = percentOfDurationOfSensorReadingsWithinLimit "Percentage of duration of Sensor Readings Within Limit"
SZ = durationOfSensorReadingsBelowLowLimit (mins) "Duration of Sensor Readings below low Limit  in mins"
SL = percentofDurationOfSensorReadingsBelowLowLimit "Percentage of duration of Sensor Readings Below Low Limit"
SK = durationSuspend (mins) "Duration of Insulin suspension due to threshold suspend or predictive suspend"

AA = numberOfThresholdSuspendAlarms "Number of Threshold alarms"
AB = numberOfPredictiveSuspendQuietAndPredictiveSuspendAnnunciateAlarms "Number of predictive suspend quiet and predictive suspend annunciate alarms"

LA = totalOfMicroBolusInsulinDelivered (div 10000)
LB = totalTimeInCLActiveMode "Total time spent in active mode in mins"
LC = totalTimeInTherapyTargetRange "Total time spent in therapy target range in mins"
LD = totalTimeInAboveTherapyTargetRangeHiLimit "Total time spent above Therapy target range above limit in mins"
LE = totalTimeInBelowTherapyTargetRangeLowLimit "Total time spent below Therapy target range low limit in mins"
LF = numberOfClosedLoopMicroBoluses

QA = unknown1
QB = unknown2


Alarms:

#103 alarmData: HM-------- HM=Hours(hour,min)
#105 alarmData: UUUU------ U=Insulin(div 10000)
#108 alarmData: HMR------- HM=Clock(hour,min) R=Personal Reminder(1/2/3/4/5/6/7=BG Check/8=Medication)
#109 alarmData: X--------- X=Days (days since last set change)
#802 alarmData: XSS-HMSS-- X=Snooze(mins) S=SGV(mgdl) HM=Clock(hour,min)
#805 alarmData: XSS-HMSS-- X=Snooze(mins) S=SGV(mgdl) HM=Clock(hour,min)
#816 alarmData: XSS------- X=Snooze(mins) S=SGV(mgdl)
#817 alarmData: XSS------- X=Before(mins) S=SGV(mgdl)
#869 alarmData: HM-------- HM=Clock(hour,min)

*/

package info.nightscout.android.medtronic;

import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import info.nightscout.android.UploaderApplication;
import info.nightscout.android.medtronic.message.MessageUtils;
import info.nightscout.android.model.medtronicNg.PumpHistoryBG;
import info.nightscout.android.model.medtronicNg.PumpHistoryBasal;
import info.nightscout.android.model.medtronicNg.PumpHistoryBolus;
import info.nightscout.android.model.medtronicNg.PumpHistoryCGM;
import info.nightscout.android.model.medtronicNg.PumpHistoryLoop;
import info.nightscout.android.model.medtronicNg.PumpHistoryMisc;
import info.nightscout.android.model.medtronicNg.PumpHistoryPattern;
import info.nightscout.android.utils.HexDump;
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

    private int pumpRTC;
    private int pumpOFFSET;
    private long pumpClockDifference;
    private double pumpDRIFT;

    public PumpHistoryParser(byte[] eventData) {
        this.eventData = eventData;
    }

    private DateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);

    public Date[] process(final int pumpRTC, final int pumpOFFSET, final long pumpClockDifference, long startTime, long endTime) {

        eventOldest = startTime;
        eventNewest = endTime;

        parser(pumpRTC, pumpOFFSET, pumpClockDifference);

        // event date range returned by pump as it is usually more then requested
        return new Date[]{eventOldest == 0 ? null : new Date(eventOldest), eventNewest == 0 ? null : new Date(eventNewest)};
    }

    private void parser(final int pumpRTC, final int pumpOFFSET, final long pumpClockDifference) {
        historyRealm = Realm.getInstance(UploaderApplication.getHistoryConfiguration());

        this.pumpRTC = pumpRTC;
        this.pumpOFFSET = pumpOFFSET;
        this.pumpClockDifference = pumpClockDifference;
        this.pumpDRIFT = 4.0 / (24 * 60 * 60);

        index = 0;
        event = 0;

        historyRealm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {

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

                    switch (eventType) {
                        case SENSOR_GLUCOSE_READINGS_EXTENDED:
                            SENSOR_GLUCOSE_READINGS_EXTENDED();
                            break;
                        case NORMAL_BOLUS_PROGRAMMED:
                            NORMAL_BOLUS_PROGRAMMED();
                            break;
                        case NORMAL_BOLUS_DELIVERED:
                            NORMAL_BOLUS_DELIVERED();
                            break;
                        case SQUARE_BOLUS_PROGRAMMED:
                            SQUARE_BOLUS_PROGRAMMED();
                            break;
                        case SQUARE_BOLUS_DELIVERED:
                            SQUARE_BOLUS_DELIVERED();
                            break;
                        case DUAL_BOLUS_PROGRAMMED:
                            DUAL_BOLUS_PROGRAMMED();
                            break;
                        case DUAL_BOLUS_PART_DELIVERED:
                            DUAL_BOLUS_PART_DELIVERED();
                            break;
                        case BOLUS_WIZARD_ESTIMATE:
                            BOLUS_WIZARD_ESTIMATE();
                            break;
                        case MEAL_WIZARD_ESTIMATE:
                            MEAL_WIZARD_ESTIMATE();
                            break;
                        case TEMP_BASAL_PROGRAMMED:
                            TEMP_BASAL_PROGRAMMED();
                            break;
                        case TEMP_BASAL_COMPLETE:
                            TEMP_BASAL_COMPLETE();
                            break;
                        case BASAL_PATTERN_SELECTED:
                            BASAL_PATTERN_SELECTED();
                            break;
                        case INSULIN_DELIVERY_STOPPED:
                            INSULIN_DELIVERY_STOPPED();
                            break;
                        case INSULIN_DELIVERY_RESTARTED:
                            INSULIN_DELIVERY_RESTARTED();
                            break;
                        case BG_READING:
                            BG_READING();
                            break;
                        case CLOSED_LOOP_BG_READING:
                            CLOSED_LOOP_BG_READING();
                            break;
                        case CLOSED_LOOP_TRANSITION:
                            CLOSED_LOOP_TRANSITION();
                            break;
                        case BASAL_SEGMENT_START:
                            BASAL_SEGMENT_START();
                            break;
                        case CALIBRATION_COMPLETE:
                            CALIBRATION_COMPLETE();
                            break;
                        case GLUCOSE_SENSOR_CHANGE:
                            GLUCOSE_SENSOR_CHANGE();
                            break;
                        case BATTERY_INSERTED:
                            BATTERY_INSERTED();
                            break;
                        case REWIND:
                            REWIND();
                            break;
                    }

                    event++;
                    index += eventSize;
                }

            }
        });

        historyRealm.close();
    }

    private void SENSOR_GLUCOSE_READINGS_EXTENDED() {
        int minutesBetweenReadings = read8toUInt(eventData, index + 0x0B);
        int numberOfReadings = read8toUInt(eventData, index + 0x0C);

        int pos = index + 15;
        for (int i = 0; i < numberOfReadings; i++) {

            int sgv = read16BEtoUInt(eventData, pos) & 0x03FF;
            double isig = read16BEtoUInt(eventData, pos + 2) / 100.0;
            double vctr = (((eventData[pos] >> 2 & 3) << 8) | eventData[pos + 4] & 0x000000FF) / 100.0;
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

            PumpHistoryCGM.event(historyRealm, thisDate, thisRTC, eventOFFSET,
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

    private void NORMAL_BOLUS_PROGRAMMED() {
        int bolusSource = read8toUInt(eventData, index + 0x0B);
        int bolusRef = read8toUInt(eventData, index + 0x0C);
        int presetBolusNumber = read8toUInt(eventData, index + 0x0D);
        double normalProgrammedAmount = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
        double activeInsulin = read32BEtoInt(eventData, index + 0x12) / 10000.0;
        PumpHistoryBolus.bolus(historyRealm, eventDate, eventRTC, eventOFFSET,
                BOLUS_TYPE.NORMAL_BOLUS.get(), true, false, false,
                bolusRef,
                bolusSource,
                presetBolusNumber,
                normalProgrammedAmount, 0,
                0, 0,
                0, 0,
                activeInsulin);
    }

    private void NORMAL_BOLUS_DELIVERED() {
        int bolusSource = read8toUInt(eventData, index + 0x0B);
        int bolusRef = read8toUInt(eventData, index + 0x0C);
        int presetBolusNumber = read8toUInt(eventData, index + 0x0D);
        double normalProgrammedAmount = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
        double normalDeliveredAmount = read32BEtoInt(eventData, index + 0x12) / 10000.0;
        double activeInsulin = read32BEtoInt(eventData, index + 0x16) / 10000.0;

        if(bolusSource == BOLUS_SOURCE.CLOSED_LOOP_MICRO_BOLUS.value) {
            PumpHistoryLoop.microbolus(historyRealm, eventDate, eventRTC, eventOFFSET,
                    bolusRef,
                    normalDeliveredAmount);
        } else {
            PumpHistoryBolus.bolus(historyRealm, eventDate, eventRTC, eventOFFSET,
                    BOLUS_TYPE.NORMAL_BOLUS.get(), false, true, false,
                    bolusRef,
                    bolusSource,
                    presetBolusNumber,
                    normalProgrammedAmount, normalDeliveredAmount,
                    0, 0,
                    0, 0,
                    activeInsulin);
        }
    }

    private void SQUARE_BOLUS_PROGRAMMED() {
        int bolusSource = read8toUInt(eventData, index + 0x0B);
        int bolusRef = read8toUInt(eventData, index + 0x0C);
        int presetBolusNumber = read8toUInt(eventData, index + 0x0D);
        double squareProgrammedAmount = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
        int squareProgrammedDuration = read16BEtoUInt(eventData, index + 0x12);
        double activeInsulin = read32BEtoInt(eventData, index + 0x14) / 10000.0;
        PumpHistoryBolus.bolus(historyRealm, eventDate, eventRTC, eventOFFSET,
                BOLUS_TYPE.SQUARE_WAVE.get(), true, false, false,
                bolusRef,
                bolusSource,
                presetBolusNumber,
                0, 0,
                squareProgrammedAmount, 0,
                squareProgrammedDuration, 0,
                activeInsulin);
    }

    private void SQUARE_BOLUS_DELIVERED() {
        int bolusSource = read8toUInt(eventData, index + 0x0B);
        int bolusRef = read8toUInt(eventData, index + 0x0C);
        int presetBolusNumber = read8toUInt(eventData, index + 0x0D);
        double squareProgrammedAmount = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
        double squareDeliveredAmount = read32BEtoInt(eventData, index + 0x12) / 10000.0;
        int squareProgrammedDuration = read16BEtoUInt(eventData, index + 0x16);
        int squareDeliveredDuration = read16BEtoUInt(eventData, index + 0x18);
        double activeInsulin = read32BEtoInt(eventData, index + 0x1A) / 10000.0;
        PumpHistoryBolus.bolus(historyRealm, eventDate, eventRTC, eventOFFSET,
                BOLUS_TYPE.SQUARE_WAVE.get(), false, false, true,
                bolusRef,
                bolusSource,
                presetBolusNumber,
                0, 0,
                squareProgrammedAmount, squareDeliveredAmount,
                squareProgrammedDuration, squareDeliveredDuration,
                activeInsulin);
    }

    private void DUAL_BOLUS_PROGRAMMED() {
        int bolusSource = read8toUInt(eventData, index + 0x0B);
        int bolusRef = read8toUInt(eventData, index + 0x0C);
        int presetBolusNumber = read8toUInt(eventData, index + 0x0D);
        double normalProgrammedAmount = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
        double squareProgrammedAmount = read32BEtoInt(eventData, index + 0x12) / 10000.0;
        int squareProgrammedDuration = read16BEtoUInt(eventData, index + 0x16);
        double activeInsulin = read32BEtoInt(eventData, index + 0x18) / 10000.0;
        PumpHistoryBolus.bolus(historyRealm, eventDate, eventRTC, eventOFFSET,
                BOLUS_TYPE.DUAL_WAVE.get(), true, false, false,
                bolusRef,
                bolusSource,
                presetBolusNumber,
                normalProgrammedAmount, 0,
                squareProgrammedAmount, 0,
                squareProgrammedDuration, 0,
                activeInsulin);
    }

    private void DUAL_BOLUS_PART_DELIVERED() {
        int bolusSource = read8toUInt(eventData, index + 0x0B);
        int bolusRef = read8toUInt(eventData, index + 0x0C);
        int presetBolusNumber = read8toUInt(eventData, index + 0x0D);
        double normalProgrammedAmount = read32BEtoInt(eventData, index + 0x0E) / 10000.0;
        double squareProgrammedAmount = read32BEtoInt(eventData, index + 0x12) / 10000.0;
        double deliveredAmount = read32BEtoInt(eventData, index + 0x16) / 10000.0;
        int bolusPart = read8toUInt(eventData, index + 0x1A);
        int squareProgrammedDuration = read16BEtoUInt(eventData, index + 0x1B);
        int squareDeliveredDuration = read16BEtoUInt(eventData, index + 0x1D);
        double activeInsulin = read32BEtoInt(eventData, index + 0x1F) / 10000.0;
        PumpHistoryBolus.bolus(historyRealm, eventDate, eventRTC, eventOFFSET,
                BOLUS_TYPE.DUAL_WAVE.get(), false, bolusPart == 1, bolusPart == 2,
                bolusRef,
                bolusSource,
                presetBolusNumber,
                normalProgrammedAmount, bolusPart == 1 ? deliveredAmount : 0,
                squareProgrammedAmount, bolusPart == 2 ? deliveredAmount : 0,
                squareProgrammedDuration, squareDeliveredDuration,
                activeInsulin);
    }

    private void BOLUS_WIZARD_ESTIMATE() {
        int bgUnits = read8toUInt(eventData, index + 0x0B);
        int carbUnits = read8toUInt(eventData, index + 0x0C);
        double bgInput = read16BEtoUInt(eventData, index + 0x0D) / (bgUnits == BG_UNITS.MMOL_L.get() ? 10.0 : 1.0);
        double carbInput = read16BEtoUInt(eventData, index + 0x0F) / (carbUnits == CARB_UNITS.EXCHANGES.get() ? 10.0 : 1.0);
        double isf = read16BEtoUInt(eventData, index + 0x11) / (bgUnits == BG_UNITS.MMOL_L.get() ? 10.0 : 1.0);
        double carbRatio = read32BEtoULong(eventData, index + 0x13) / (carbUnits == CARB_UNITS.EXCHANGES.get() ? 1000.0 : 10.0);
        double lowBgTarget = read16BEtoUInt(eventData, index + 0x17) / (bgUnits == BG_UNITS.MMOL_L.get() ? 10.0 : 1.0);
        double highBgTarget = read16BEtoUInt(eventData, index + 0x19) / (bgUnits == BG_UNITS.MMOL_L.get() ? 10.0 : 1.0);
        double correctionEstimate = read32BEtoLong(eventData, index + 0x1B) / 10000.0;
        double foodEstimate = read32BEtoULong(eventData, index + 0x1F) / 10000.0;
        double iob = read32BEtoInt(eventData, index + 0x23) / 10000.0;
        double iobAdjustment = read32BEtoInt(eventData, index + 0x27) / 10000.0;
        double bolusWizardEstimate = read32BEtoInt(eventData, index + 0x2B) / 10000.0;
        int bolusStepSize = read8toUInt(eventData, index + 0x2F);
        boolean estimateModifiedByUser = (read8toUInt(eventData, index + 0x30) & 1) == 1;
        double finalEstimate = read32BEtoInt(eventData, index + 0x31) / 10000.0;
        PumpHistoryBolus.bolusWizardEstimate(historyRealm, eventDate, eventRTC, eventOFFSET,
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

    private void MEAL_WIZARD_ESTIMATE() {
        int bgUnits = read8toUInt(eventData, index + 0x0B) & 1;
        int carbUnits = (read8toUInt(eventData, index + 0x0B) & 2) >> 1;
        double bgInput = read16BEtoUInt(eventData, index + 0x0C) / (bgUnits == BG_UNITS.MMOL_L.get() ? 10.0 : 1.0);
        double carbInput = read16BEtoUInt(eventData, index + 0x0E) / (carbUnits == CARB_UNITS.EXCHANGES.get() ? 10.0 : 1.0);
        double carbRatio = read32BEtoULong(eventData, index + 0x1C) / (carbUnits == CARB_UNITS.EXCHANGES.get() ? 1000.0 : 10.0);
        double correctionEstimate = read32BEtoLong(eventData, index + 0x10) / 10000.0;
        double foodEstimate = read32BEtoULong(eventData, index + 0x14) / 10000.0;
        double bolusWizardEstimate = read32BEtoInt(eventData, index + 0x18) / 10000.0;
        double finalEstimate = bolusWizardEstimate;

        PumpHistoryBolus.bolusWizardEstimate(historyRealm, eventDate, eventRTC, eventOFFSET,
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
                0,
                false,
                finalEstimate);
    }

    private void TEMP_BASAL_PROGRAMMED() {
        int preset = read8toUInt(eventData, index + 0x0B);
        int type = read8toUInt(eventData, index + 0x0C);
        double rate = read32BEtoInt(eventData, index + 0x0D) / 10000.0;
        int percentageOfRate = read8toUInt(eventData, index + 0x11);
        int duration = read16BEtoUInt(eventData, index + 0x12);
        PumpHistoryBasal.temp(historyRealm, eventDate, eventRTC, eventOFFSET,
                false,
                preset,
                type,
                rate,
                percentageOfRate,
                duration,
                false);
    }

    private void TEMP_BASAL_COMPLETE() {
        int preset = read8toUInt(eventData, index + 0x0B);
        int type = read8toUInt(eventData, index + 0x0C);
        double rate = read32BEtoInt(eventData, index + 0x0D) / 10000.0;
        int percentageOfRate = read8toUInt(eventData, index + 0x11);
        int duration = read16BEtoUInt(eventData, index + 0x12);
        boolean canceled = (eventData[index + 0x14] & 1) == 1;
        PumpHistoryBasal.temp(historyRealm, eventDate, eventRTC, eventOFFSET,
                true,
                preset,
                type,
                rate,
                percentageOfRate,
                duration,
                canceled);
    }

    private void BASAL_PATTERN_SELECTED() {
        int oldPatternNumber = read8toUInt(eventData, index + 0x0B);
        int newPatternNumber = read8toUInt(eventData, index + 0x0C);
        PumpHistoryPattern.change(historyRealm, eventDate, eventRTC, eventOFFSET,
                oldPatternNumber,
                newPatternNumber);
    }

    private void INSULIN_DELIVERY_STOPPED() {
        int reason = read8toUInt(eventData, index + 0x0B);
        PumpHistoryBasal.suspend(historyRealm, eventDate, eventRTC, eventOFFSET,
                reason);
    }

    private void INSULIN_DELIVERY_RESTARTED() {
        int reason = read8toUInt(eventData, index + 0x0B);
        PumpHistoryBasal.resume(historyRealm, eventDate, eventRTC, eventOFFSET,
                reason);
    }

    private void BG_READING() {
        int bg = read16BEtoUInt(eventData, index + 0x0C);
        byte bgUnits = (byte) (eventData[index + 0x0B] & 1);
        byte bgSource = eventData[index + 0x0E];
        byte bgContext = -1;
        boolean calibrationFlag = ((eventData[index + 0x0B] & 0x02) == 2) || bgSource == BG_SOURCE.SENSOR_CAL.value;
        String serial = new StringBuffer(readString(eventData, index + 0x0F, eventSize - 0x0F)).reverse().toString();
        PumpHistoryBG.bg(historyRealm, eventDate, eventRTC, eventOFFSET,
                calibrationFlag,
                bg,
                bgUnits,
                bgSource,
                bgContext,
                serial);
    }

    private void CLOSED_LOOP_BG_READING() {
        int bg = read16BEtoUInt(eventData, index + 0x0B);
        byte bgUnits = (byte) (eventData[index + 0x16] & 1);
        byte bgSource = -1;
        byte bgContext = (byte) ((eventData[index + 0x16] & 0xF8) >> 3);
        boolean calibrationFlag = bgContext == BG_CONTEXT.BG_SENT_FOR_CALIB.value || bgContext == BG_CONTEXT.ENTERED_IN_SENSOR_CALIB.value;
        String serial = "";
        PumpHistoryBG.bg(historyRealm, eventDate, eventRTC, eventOFFSET,
                calibrationFlag,
                bg,
                bgUnits,
                bgSource,
                bgContext,
                serial);
    }

    private void CLOSED_LOOP_TRANSITION() {
        boolean loopActive = (eventData[index + 0x0B] & 1) == 1;
        byte loopStatus = eventData[index + 0x0C];
        PumpHistoryLoop.mode(historyRealm, eventDate, eventRTC, eventOFFSET,
                loopActive,
                loopStatus);
    }

    private void BASAL_SEGMENT_START() {
        byte pattern = eventData[index + 0x0B];
        byte segment = eventData[index + 0x0C];
        double rate = read32BEtoInt(eventData, index + 0x0D) / 10000.0;
        PumpHistoryLoop.basal(historyRealm, eventDate, eventRTC, eventOFFSET,
                pattern);
    }

private void CALIBRATION_COMPLETE() {
        double calFactor = read16BEtoUInt(eventData, index + 0xB) / 100.0;
        int bgTarget = read16BEtoUInt(eventData, index + 0xD);
        PumpHistoryBG.calibration(historyRealm, eventDate, eventRTC, eventOFFSET,
                calFactor,
                bgTarget);
    }

    private void GLUCOSE_SENSOR_CHANGE() {
        PumpHistoryMisc.item(historyRealm, eventDate, eventRTC, eventOFFSET,
                1);
    }

    private void BATTERY_INSERTED() {
        PumpHistoryMisc.item(historyRealm, eventDate, eventRTC, eventOFFSET,
                2);
    }

    private void REWIND() {
        PumpHistoryMisc.item(historyRealm, eventDate, eventRTC, eventOFFSET,
                3);
    }

    // events to logcat, no clock difference/drift adjustments just the pump event times

    public void logcat() {
        String result;

        index = 0;
        event = 0;

        while (index < eventData.length && event < 10000) {

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
                boolean calibrationFlag = bgContext == BG_CONTEXT.BG_SENT_FOR_CALIB.value || bgContext == BG_CONTEXT.ENTERED_IN_SENSOR_CALIB.value;
                String serial = "";
                result += " BG:" + bg + " Unit:" + bgUnits + " Source:" + bgSource + " Context:" + bgContext + " Calibration:" + calibrationFlag + " Serial:" + serial;

            } else if (eventType == EventType.CALIBRATION_COMPLETE) {
                double calFactor = read16BEtoUInt(eventData, index + 0xB) / 100.0;
                int bgTarget = read16BEtoUInt(eventData, index + 0xD);
                result += " bgTarget:" + bgTarget + " calFactor:" + calFactor;

            } else if (eventType == EventType.BOLUS_WIZARD_ESTIMATE) {
                int bgUnits = read8toUInt(eventData, index + 0x0B);
                int carbUnits = read8toUInt(eventData, index + 0x0C);
                double bgInput = read16BEtoUInt(eventData, index + 0x0D) / (bgUnits == 1 ? 0.1 : 1.0);
                double carbInput = read16BEtoUInt(eventData, index + 0x0F) / (bgUnits == 1 ? 10.0 : 1.0);
                double isf = read16BEtoUInt(eventData, index + 0x11) / (bgUnits == 1 ? 10.0 : 1.0);
                double carbRatio = read32BEtoULong(eventData, index + 0x13) / (carbUnits == 1 ? 1000.0 : 10.0);
                double lowBgTarget = read16BEtoUInt(eventData, index + 0x17) / (bgUnits == 1 ? 10.0 : 1.0);
                double highBgTarget = read16BEtoUInt(eventData, index + 0x19) / (bgUnits == 1 ? 10.0 : 1.0);
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
                double bgInput = read16BEtoUInt(eventData, index + 0x0C) / (bgUnits == BG_UNITS.MMOL_L.get() ? 10.0 : 1.0);
                double carbInput = read16BEtoUInt(eventData, index + 0x0E) / (carbUnits == CARB_UNITS.EXCHANGES.get() ? 10.0 : 1.0);
                double carbRatio = read32BEtoULong(eventData, index + 0x1C) / (carbUnits == CARB_UNITS.EXCHANGES.get() ? 1000.0 : 10.0);
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
                    double vctr = (((eventData[pos] >> 2 & 3) << 8) | eventData[pos + 4] & 0x000000FF) / 100.0;
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
                int value0 = read8toUInt(eventData, index + 0x0B);
                int value1 = read8toUInt(eventData, index + 0x0C);
                boolean loopActive = (eventData[index + 0x0B] & 1) == 1;
                int loopStatus = read8toUInt(eventData, index + 0x0C);
                result += " loopActive:" + loopActive + " loopStatus:" + loopStatus + " [value0:" + value0 + " value1:" + value1 + "]";

            }

            //else result += HexDump.dumpHexString(eventData, index + 0x0B, eventSize - 0x0B);

            if (eventType != EventType.PLGM_CONTROLLER_STATE
                    && eventType != EventType.ALARM_NOTIFICATION
                    && eventType != EventType.ALARM_CLEARED
                    && eventType != EventType.CLOSED_LOOP_STATUS_DATA
                    && eventType != EventType.CLOSED_LOOP_PERIODIC_DATA
                    )
                Log.d(TAG, result);

            index += eventSize;
            event++;
        }
    }

    private enum EventType {
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

    public enum SUSPEND_REASON {
        ALARM_SUSPEND(1), // Battery change, cleared occlusion, etc
        USER_SUSPEND(2),
        AUTO_SUSPEND(3),
        LOWSG_SUSPEND(4),
        SET_CHANGE_SUSPEND(5), // AKA NOTSEATED_SUSPEND
        PLGM_PREDICTED_LOW_SG(10),
        NA(-1);

        private int value;

        SUSPEND_REASON(int value) {
            this.value = value;
        }

        public int get() {
            return this.value;
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
        USER_SELECTS_RESUME(1),
        USER_CLEARS_ALARM(2),
        LGM_MANUAL_RESUME(3),
        LGM_AUTO_RESUME_MAX_SUSP(4), // After an auto suspend, but no CGM data afterwards.
        LGM_AUTO_RESUME_PSG_SG(5), // When SG reaches the Preset SG level
        LGM_MANUAL_RESUME_VIA_DISABLE(6),
        NA(-1);

        private int value;

        RESUME_REASON(int value) {
            this.value = value;
        }

        public int get() {
            return this.value;
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

        public int get() {
            return this.value;
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

        public int get() {
            return this.value;
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

        public int get() {
            return this.value;
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

        public int get() {
            return this.value;
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

        public int get() {
            return this.value;
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

        public int get() {
            return this.value;
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

        public int get() {
            return this.value;
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

        public int get() {
            return this.value;
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

        public int get() {
            return this.value;
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

    public enum BOLUS_TYPE {
        NORMAL_BOLUS(0),
        SQUARE_WAVE(1),
        DUAL_WAVE(2),
        NA(-1);

        private int value;

        BOLUS_TYPE(int value) {
            this.value = value;
        }

        public int get() {
            return this.value;
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

        public int get() {
            return this.value;
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

        public int get() {
            return this.value;
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

        public int get() {
            return this.value;
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

        public int get() {
            return this.value;
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

    public enum TextEN {
        // NIGHTSCOUT
        NS_SUSPEND("Pump suspended insulin delivery"),
        NS_RESUME("Pump resumed insulin delivery"),
        // TREND_NAME
        NS_TREND_NONE("NONE"),
        NS_TREND_DOUBLE_UP("DoubleUp"),
        NS_TREND_SINGLE_UP("SingleUp"),
        NS_TREND_FOURTY_FIVE_UP("FortyFiveUp"),
        NS_TREND_FLAT("Flat"),
        NS_TREND_FOURTY_FIVE_DOWN("FortyFiveDown"),
        NS_TREND_SINGLE_DOWN("SingleDown"),
        NS_TREND_DOUBLE_DOWN("DoubleDown"),
        NS_TREND_NOT_COMPUTABLE("NOT COMPUTABLE"),
        NS_TREND_RATE_OUT_OF_RANGE("RATE OUT OF RANGE"),
        NS_TREND_NOT_SET("NONE"),

        // SUSPEND_REASON
        ALARM_SUSPEND("Alarm suspend"),
        USER_SUSPEND("User suspend"),
        AUTO_SUSPEND("Auto suspend"),
        LOWSG_SUSPEND("Low glucose suspend"),
        SET_CHANGE_SUSPEND("Set change suspend"),
        PLGM_PREDICTED_LOW_SG("Predicted low glucose suspend"),
        // RESUME_REASON
        USER_SELECTS_RESUME("User resumed"),
        USER_CLEARS_ALARM("User cleared alarm"),
        LGM_MANUAL_RESUME("Low glucose manual resume"),
        LGM_AUTO_RESUME_MAX_SUSP("Low glucose auto resume - max suspend period"),
        LGM_AUTO_RESUME_PSG_SG("Low glucose auto resume - preset glucose reached"),
        LGM_MANUAL_RESUME_VIA_DISABLE("Low glucose manual resume via disable"),
        // BASAL_PATTERN_NAME
        BASAL_PATTERN_1("Basal 1"),
        BASAL_PATTERN_2("Basal 2"),
        BASAL_PATTERN_3("Basal 3"),
        BASAL_PATTERN_4("Basal 4"),
        BASAL_PATTERN_5("Basal 5"),
        BASAL_PATTERN_6("Workday"),
        BASAL_PATTERN_7("Day Off"),
        BASAL_PATTERN_8("Sick Day"),
        // TEMP_BASAL_PRESET_NAME
        TEMP_BASAL_PRESET_0("Not Preset"),
        TEMP_BASAL_PRESET_1("Temp 1"),
        TEMP_BASAL_PRESET_2("Temp 2"),
        TEMP_BASAL_PRESET_3("Temp 3"),
        TEMP_BASAL_PRESET_4("Temp 4"),
        TEMP_BASAL_PRESET_5("High Activity"),
        TEMP_BASAL_PRESET_6("Moderate Activity"),
        TEMP_BASAL_PRESET_7("Low Activity"),
        TEMP_BASAL_PRESET_8("Sick"),
        // BOLUS_PRESET_NAME
        BOLUS_PRESET_0("Not Preset"),
        BOLUS_PRESET_1("Bolus 1"),
        BOLUS_PRESET_2("Bolus 2"),
        BOLUS_PRESET_3("Bolus 3"),
        BOLUS_PRESET_4("Bolus 4"),
        BOLUS_PRESET_5("Breakfast"),
        BOLUS_PRESET_6("Lunch"),
        BOLUS_PRESET_7("Dinner"),
        BOLUS_PRESET_8("Snack"),

        NA("");

        private String text;

        TextEN(String text) {
            this.text = text;
        }

        public String getText() {
            return this.text;
        }
    }

}

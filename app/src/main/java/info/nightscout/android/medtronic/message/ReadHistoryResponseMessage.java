package info.nightscout.android.medtronic.message;

import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import info.nightscout.android.medtronic.MedtronicCnlSession;
import info.nightscout.android.medtronic.exception.ChecksumException;
import info.nightscout.android.medtronic.exception.EncryptionException;
import info.nightscout.android.medtronic.exception.UnexpectedMessageException;
import info.nightscout.android.utils.HexDump;

import static info.nightscout.android.utils.ToolKit.getByteIU;
import static info.nightscout.android.utils.ToolKit.getInt;
import static info.nightscout.android.utils.ToolKit.getIntL;
import static info.nightscout.android.utils.ToolKit.getIntLU;
import static info.nightscout.android.utils.ToolKit.getShortI;
import static info.nightscout.android.utils.ToolKit.getShortIU;
import static info.nightscout.android.utils.ToolKit.getString;

/**
 * Created by John on 6.10.17.
 */

public class ReadHistoryResponseMessage extends MedtronicSendMessageResponseMessage {
    private static final String TAG = ReadHistoryResponseMessage.class.getSimpleName();

    protected ReadHistoryResponseMessage(MedtronicCnlSession pumpSession, byte[] payload) throws EncryptionException, ChecksumException, UnexpectedMessageException {
        super(pumpSession, payload);

        new HistoryParser(payload).logcat();
    }

    private DateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);

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
    }

    private EventType EventIs(int value) {
        for (EventType type : EventType.values()) {
            if (type.event == value) {
                return type;
            }
        }
        return EventType.NO_TYPE;
    }

    private class HistoryParser {
        private byte[] eventData;

        private HistoryParser(byte[] eventData) {
            this.eventData = eventData;
        }

        private void run() {
            // something something something complete
        }

        private void logcat() {
            int offset = 0;
            int event = 0;

            while (offset < eventData.length && event < 50000) {

                EventType eventType = EventIs(getByteIU(eventData, offset + 0x00));
                int size = getByteIU(eventData, offset + 0x02);

                long rtc = getIntLU(eventData, offset + 0x03);
                long off = getIntL(eventData, offset + 0x07);
                Date timestamp = MessageUtils.decodeDateTime(rtc, off);

                String result = "[" + event + "] " + eventType + " " + dateFormatter.format(timestamp);

                if (eventType == EventType.BG_READING) {
                    boolean calibrationFlag = (eventData[offset + 0x0B] & 0x02) == 2;
                    int bgUnits = eventData[offset + 0x0B] & 1;
                    int bg = getShortIU(eventData, offset + 0x0C);
                    int bgSource = getByteIU(eventData, offset + 0x0E);
                    String serial = new StringBuffer(getString(eventData, offset + 0x0F, size - 0x0F)).reverse().toString();
                    result += " BG:" + bg + " Unit:" + bgUnits + " Source:" + bgSource + " Calibration:" + calibrationFlag + " Serial:" + serial;

                } else if (eventType == EventType.CALIBRATION_COMPLETE) {
                    double calFactor = getShortIU(eventData, offset + 0xB) / 100.0;
                    int bgTarget = getShortIU(eventData, offset + 0xD);
                    result += " bgTarget:" + bgTarget + " calFactor:" + calFactor;

                } else if (eventType == EventType.BOLUS_WIZARD_ESTIMATE) {
                    int bgUnits = getByteIU(eventData, offset + 0x0B);
                    int carbUnits = getByteIU(eventData, offset + 0x0C);
                    double bgInput = getShortIU(eventData, offset + 0x0D) / (bgUnits == 1 ? 10.0 : 1.0);
                    double carbInput = getShortIU(eventData, offset + 0x0F) / (bgUnits == 1 ? 10.0 : 1.0);
                    double isf = getShortIU(eventData, offset + 0x11)  / (bgUnits == 1 ? 10.0 : 1.0);
                    double carbRatio = getIntLU(eventData, offset + 0x13)  / (carbUnits == 1 ? 1000.0 : 10.0);
                    double lowBgTarget = getShortIU(eventData, offset + 0x17) / (bgUnits == 1 ? 10.0 : 1.0);
                    double highBgTarget = getShortIU(eventData, offset + 0x19) / (bgUnits == 1 ? 10.0 : 1.0);
                    double correctionEstimate = getIntL(eventData, offset + 0x1B) / 10000.0;
                    double foodEstimate = getIntLU(eventData, offset + 0x1F) / 10000.0;
                    double iob = getInt(eventData, offset + 0x23) / 10000.0;
                    double iobAdjustment = getInt(eventData, offset + 0x27) / 10000.0;
                    double bolusWizardEstimate = getInt(eventData, offset + 0x2B) / 10000.0;
                    int bolusStepSize = getByteIU(eventData, offset + 0x2F);
                    boolean estimateModifiedByUser = (getByteIU(eventData, offset + 0x30) & 1) == 1;
                    double finalEstimate = getInt(eventData, offset + 0x31) / 10000.0;
                    result += " bgUnits:" + bgUnits + " carbUnits:" + carbUnits;
                    result += " bgInput:" + bgInput + " carbInput:" + carbInput;
                    result += " isf:" + isf + " carbRatio:" + carbRatio;
                    result += " lowBgTarget:" + lowBgTarget + " highBgTarget:" + highBgTarget;
                    result += " correctionEstimate:" + correctionEstimate + " foodEstimate:" + foodEstimate;
                    result += " iob:" + iob + " iobAdjustment:" + iobAdjustment;
                    result += " bolusWizardEstimate:" + bolusWizardEstimate + " bolusStepSize:" + bolusStepSize;
                    result += " estimateModifiedByUser:" + estimateModifiedByUser + " finalEstimate:" + finalEstimate;

                } else if (eventType == EventType.NORMAL_BOLUS_PROGRAMMED) {
                    int bolusSource = getByteIU(eventData, offset + 0x0B);
                    int bolusRef = getByteIU(eventData, offset + 0x0C);
                    int presetBolusNumber = getByteIU(eventData, offset + 0x0D);
                    double programmedAmount = getInt(eventData, offset + 0x0E) / 10000.0;
                    double activeInsulin = getInt(eventData, offset + 0x12) / 10000.0;
                    result += " Source:" + bolusSource + " Ref:" + bolusRef + " Preset:" + presetBolusNumber;
                    result += " Prog:" + programmedAmount + " Active:" + activeInsulin;

                } else if (eventType == EventType.NORMAL_BOLUS_DELIVERED) {
                    int bolusSource = getByteIU(eventData, offset + 0x0B);
                    int bolusRef = getByteIU(eventData, offset + 0x0C);
                    int presetBolusNumber = getByteIU(eventData, offset + 0x0D);
                    double programmedAmount = getInt(eventData, offset + 0x0E) / 10000.0;
                    double deliveredAmount = getInt(eventData, offset + 0x12) / 10000.0;
                    double activeInsulin = getInt(eventData, offset + 0x16) / 10000.0;
                    result += " Source:" + bolusSource + " Ref:" + bolusRef + " Preset:" + presetBolusNumber;
                    result += " Prog:" + programmedAmount + " Del:" + deliveredAmount + " Active:" + activeInsulin;

                } else if (eventType == EventType.DUAL_BOLUS_PROGRAMMED) {
                    int bolusSource = getByteIU(eventData, offset + 0x0B);
                    int bolusRef = getByteIU(eventData, offset + 0x0C);
                    int presetBolusNumber = getByteIU(eventData, offset + 0x0D);
                    double normalProgrammedAmount = getInt(eventData, offset + 0x0E) / 10000.0;
                    double squareProgrammedAmount = getInt(eventData, offset + 0x12) / 10000.0;
                    int programmedDuration = getShortIU(eventData, offset + 0x16);
                    double activeInsulin = getInt(eventData, offset + 0x18) / 10000.0;
                    result += " Source:" + bolusSource + " Ref:" + bolusRef + " Preset:" + presetBolusNumber;
                    result += " Norm:" + normalProgrammedAmount + " Sqr:" + squareProgrammedAmount;
                    result += " Dur:" + programmedDuration + " Active:" + activeInsulin;

                } else if (eventType == EventType.DUAL_BOLUS_PART_DELIVERED) {
                    int bolusSource = getByteIU(eventData, offset + 0x0B);
                    int bolusRef = getByteIU(eventData, offset + 0x0C);
                    int presetBolusNumber = getByteIU(eventData, offset + 0x0D);
                    double normalProgrammedAmount = getInt(eventData, offset + 0x0E) / 10000.0;
                    double squareProgrammedAmount = getInt(eventData, offset + 0x12) / 10000.0;
                    double deliveredAmount = getInt(eventData, offset + 0x16) / 10000.0;
                    int bolusPart = getByteIU(eventData, offset + 0x1A);
                    int programmedDuration = getShortIU(eventData, offset + 0x1B);
                    int deliveredDuration = getShortIU(eventData, offset + 0x1D);
                    double activeInsulin = getInt(eventData, offset + 0x1F) / 10000.0;
                    result += " Source:" + bolusSource + " Ref:" + bolusRef + " Preset:" + presetBolusNumber;
                    result += " Norm:" + normalProgrammedAmount + " Sqr:" + squareProgrammedAmount;
                    result += " Del:" + deliveredAmount + " Part:" + bolusPart;
                    result += " Dur:" + programmedDuration + " delDur:" + deliveredDuration + " Active:" + activeInsulin;

                } else if (eventType == EventType.SQUARE_BOLUS_PROGRAMMED) {
                    int bolusSource = getByteIU(eventData, offset + 0x0B);
                    int bolusRef = getByteIU(eventData, offset + 0x0C);
                    int presetBolusNumber = getByteIU(eventData, offset + 0x0D);
                    double programmedAmount = getInt(eventData, offset + 0x0E) / 10000.0;
                    int programmedDuration = getShortIU(eventData, offset + 0x12);
                    double activeInsulin = getInt(eventData, offset + 0x14) / 10000.0;
                    result += " Source:" + bolusSource + " Ref:" + bolusRef + " Preset:" + presetBolusNumber;
                    result += " Prog:" + programmedAmount;
                    result += " Dur:" + programmedDuration + " Active:" + activeInsulin;

                } else if (eventType == EventType.SQUARE_BOLUS_DELIVERED) {
                    int bolusSource = getByteIU(eventData, offset + 0x0B);
                    int bolusRef = getByteIU(eventData, offset + 0x0C);
                    int presetBolusNumber = getByteIU(eventData, offset + 0x0D);
                    double programmedAmount = getInt(eventData, offset + 0x0E) / 10000.0;
                    double deliveredAmount = getInt(eventData, offset + 0x12) / 10000.0;
                    int programmedDuration = getShortIU(eventData, offset + 0x16);
                    int deliveredDuration = getShortIU(eventData, offset + 0x18);
                    double activeInsulin = getInt(eventData, offset + 0x1A) / 10000.0;
                    result += " Source:" + bolusSource + " Ref:" + bolusRef + " Preset:" + presetBolusNumber;
                    result += " Prog:" + programmedAmount + " Del:" + deliveredAmount;
                    result += " Dur:" + programmedDuration + " delDur:" + deliveredDuration + " Active:" + activeInsulin;

                } else if (eventType == EventType.TEMP_BASAL_PROGRAMMED) {
                    int preset = getByteIU(eventData, offset + 0x0B);
                    int type = getByteIU(eventData, offset + 0x0C);
                    double rate = getInt(eventData, offset + 0x0D) / 10000.0;
                    int percentageOfRate = getByteIU(eventData, offset + 0x11);
                    int duration = getShortIU(eventData, offset + 0x12);
                    result += " Preset:" + preset + " Type:" + type;
                    result += " Rate:" + rate + " Percent:" + percentageOfRate;
                    result += " Dur:" + duration;

                } else if (eventType == EventType.TEMP_BASAL_COMPLETE) {
                    int preset = getByteIU(eventData, offset + 0x0B);
                    int type = getByteIU(eventData, offset + 0x0C);
                    double rate = getInt(eventData, offset + 0x0D) / 10000.0;
                    int percentageOfRate = getByteIU(eventData, offset + 0x11);
                    int duration = getShortIU(eventData, offset + 0x12);
                    boolean canceled = (eventData[offset + 0x14] & 1) == 1;
                    result += " Preset:" + preset + " Type:" + type;
                    result += " Rate:" + rate + " Percent:" + percentageOfRate;
                    result += " Dur:" + duration + " Canceled:" + canceled;

                } else if (eventType == EventType.BASAL_SEGMENT_START) {
                    int preset = getByteIU(eventData, offset + 0x0B);
                    int segment = getByteIU(eventData, offset + 0x0C);
                    double rate = getInt(eventData, offset + 0x0D) / 10000.0;
                    result += " Preset:" + preset + " Segment:" + segment + " Rate:" + rate;

                } else if (eventType == EventType.NETWORK_DEVICE_CONNECTION) {
                    boolean flag1 = (eventData[offset + 0x0B] & 0x01) == 1;
                    int value1 = getByteIU(eventData, offset + 0x0C);
                    boolean flag2 = (eventData[offset + 0x0D] & 0x01) == 1;
                    String serial = new StringBuffer(getString(eventData, offset + 0x0E, size - 0x0E)).reverse().toString();
                    result += " Flag1:" + flag1 + " Flag2:" + flag2 + " Value1:" + value1 + " Serial:" + serial;

                } else if (eventType == EventType.SENSOR_GLUCOSE_READINGS_EXTENDED) {
                    int minutesBetweenReadings = getByteIU(eventData, offset + 0x0B);
                    int numberOfReadings = getByteIU(eventData, offset + 0x0C);
                    int predictedSg = getShortIU(eventData, offset + 0x0D);
                    result += " Min:" + minutesBetweenReadings + " Num:" + numberOfReadings + " SGP:" + predictedSg;

                    int pos = offset + 15;
                    for (int i = 0; i < numberOfReadings; i++) {

                        Date sgtimestamp = MessageUtils.decodeDateTime(rtc - (i * minutesBetweenReadings * 60), off);

                        int sg = getShortIU(eventData, pos + 0) & 0x03FF;
                        double isig = getShortIU(eventData, pos + 2) / 100.0;
                        double vctr = (((eventData[pos + 0] >> 2 & 3) << 8) | eventData[pos + 4] & 0x000000FF) / 100.0;
                        double rateOfChange = getShortI(eventData, pos + 5) / 100.0;
                        int sensorStatus = getByteIU(eventData, pos + 7);
                        int readingStatus = getByteIU(eventData, pos + 8);

                        boolean backfilledData = (readingStatus & 1) == 1;
                        boolean settingsChanged = (readingStatus & 2) == 2;
                        boolean noisyData = sensorStatus == 1;
                        boolean discardData = sensorStatus == 2;
                        boolean sensorError = sensorStatus == 3;

                        int sensorException = 0;
                        int sensorEx = 0;

                        if (sg > 0x1FF) {
                            sensorException = sg & 0x00FF;
                            sg = 0;
                            result += "\n! " + sgtimestamp;
                        } else {
                            result += "\n* " + sgtimestamp;
                        }

                        result += " SGV:" + sg + " ISIG:" + isig + " VCTR:" + vctr + " ROC:" + rateOfChange + " STAT:" + readingStatus
                                + " BF:" + backfilledData + " SC:" + settingsChanged + " NS:" + noisyData + " DD:" + discardData + " SE:" + sensorError + " Exception:" + sensorException + "/" + sensorEx;

                        pos += 9;
                    }

                } else {
                    //result += HexDump.dumpHexString(eventData, offset + 0x0B, size - 0x0B);
                }

                if (eventType != EventType.PLGM_CONTROLLER_STATE
                        && eventType != EventType.ALARM_NOTIFICATION
                        && eventType != EventType.ALARM_CLEARED)
                    Log.d(TAG, result);

                offset += size;
                event++;
            }

        }
    }

}

/*
sequence for reservoir change

INSULIN_DELIVERY_STOPPED
REWIND
CANNULA_FILL_DELIVERED
CANNULA_FILL_DELIVERED
INSULIN_DELIVERY_RESTARTED

sequence for sensor change

GLUCOSE_SENSOR_CHANGE

sequence for battery change

BATTERY_REMOVED
INSULIN_DELIVERY_STOPPED
BATTERY_INSERTED
INSULIN_DELIVERY_RESTARTED
*/
package info.nightscout.android;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.android.medtronic.message.MessageUtils;
import info.nightscout.android.model.medtronicNg.PumpHistoryAlarm;
import info.nightscout.android.utils.FormatKit;
import info.nightscout.android.utils.ToolKit;

public class PumpAlert {
    private static final String TAG = PumpAlert.class.getSimpleName();

    private int code = 0;
    private TYPE type = TYPE.NA;
    private PRIORITY priority = PRIORITY.LOWEST;
    private String message = "";
    private String extended = "";

    private int id = 0;
    private List<String> extras = new ArrayList<>();

    private boolean units = true;

    private boolean cleared;
    private boolean silenced;
    private boolean repeated;

    private int faultNumber;
    private int notificationMode;
    private boolean alarmHistory;
    private boolean extraData;

    private Date alarmedDate;
    private Date clearedDate;

    private Date pumpDate;

    private byte[] alarmData;

    public enum TYPE {
        NA,
        PUMP,
        SENSOR,
        REMINDER,
        SMARTGUARD,
        AUTOMODE,
        SYSTEM
    }

    public enum PRIORITY {
        REDUNDANT(-9),
        LOWEST(-2),
        LOW(-1),
        NORMAL(0),
        HIGH(1),
        EMERGENCY(2);

        private int value;

        PRIORITY(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }
    }

    public PumpAlert record(PumpHistoryAlarm record) {
        faultNumber = record.getFaultNumber();
        notificationMode = record.getNotificationMode();
        alarmHistory = record.isAlarmHistory();
        extraData = record.isExtraData();
        alarmData = record.getAlarmData();
        cleared = record.isCleared();
        silenced = record.isSilenced();
        repeated = record.isRepeated();
        clearedDate = record.getClearedDate();
        alarmedDate = record.getAlarmedDate();
        pumpDate = MessageUtils.decodeDateTime(record.getAlarmedRTC() & 0xFFFFFFFFL, record.getAlarmedOFFSET());
        return this;
    }

    public PumpAlert units(boolean units) {
        this.units = units;
        return this;
    }

    public PumpAlert faultNumber(int faultNumber) {
        this.faultNumber = faultNumber;
        return this;
    }

    public PumpAlert build() {

        code = faultNumber;

        switch (code) {

            case 4:
                alert(TYPE.PUMP, PRIORITY.EMERGENCY).id(R.string.alert_4).format();
                break;
            case 6:
                alert(TYPE.PUMP, PRIORITY.EMERGENCY).id(R.string.alert_6).format();
                break;
            case 7:
                alert(TYPE.PUMP, PRIORITY.EMERGENCY).id(R.string.alert_7).format();
                break;
            case 8:
                alert(TYPE.PUMP, PRIORITY.EMERGENCY).id(R.string.alert_8).format();
                break;
            case 11:
                alert(TYPE.PUMP, PRIORITY.EMERGENCY).id(R.string.alert_11).format();
                break;
            case 15:
                alert(TYPE.PUMP, PRIORITY.EMERGENCY).id(R.string.alert_15).format();
                break;
            case 53:
                alert(TYPE.PUMP, PRIORITY.EMERGENCY).id(R.string.alert_53).format();
                break;
            case 58:
                alert(TYPE.PUMP, PRIORITY.NORMAL).id(R.string.alert_58).format();
                break;
            case 61:
                alert(TYPE.PUMP, PRIORITY.LOWEST).id(R.string.alert_61).format();
                break;
            case 66:
                alert(TYPE.PUMP, PRIORITY.LOWEST).id(R.string.alert_66).format();
                break;
            case 70:
                alert(TYPE.PUMP, PRIORITY.LOW).id(R.string.alert_70).format();
                break;
            case 71:
                alert(TYPE.PUMP, PRIORITY.LOW).id(R.string.alert_71).insulin(0).format();
                break;
            case 72:
                alert(TYPE.PUMP, PRIORITY.LOW).id(R.string.alert_72).insulin(0).format();
                break;
            case 73:
                alert(TYPE.PUMP, PRIORITY.HIGH).id(R.string.alert_73).format();
                break;
            case 84:
                alert(TYPE.PUMP, PRIORITY.LOW).id(R.string.alert_84).format();
                break;

            case 100:
                alert(TYPE.PUMP, PRIORITY.HIGH).id(R.string.alert_100).format();
                break;
            case 104:
                alert(TYPE.PUMP, PRIORITY.NORMAL).id(R.string.alert_104).format();
                break;
            case 105:
                alert(TYPE.PUMP, PRIORITY.NORMAL).id(R.string.alert_105).insulin(0).format();
                break;
            case 106:
                alert(TYPE.PUMP, PRIORITY.NORMAL).id(R.string.alert_106).hours(0).format();
                break;
            case 113:
                alert(TYPE.PUMP, PRIORITY.HIGH).id(R.string.alert_113).format();
                break;
            case 117:
                alert(TYPE.PUMP, PRIORITY.LOW).id(R.string.alert_117).format();
                break;

            case 775:
                alert(TYPE.SENSOR, PRIORITY.HIGH).id(R.string.alert_775).format();
                break;
            case 776:
                alert(TYPE.SENSOR, PRIORITY.HIGH).id(R.string.alert_776).format();
                break;
            case 777:
                alert(TYPE.SENSOR, PRIORITY.HIGH).id(R.string.alert_777).format();
                break;
            case 780:
                alert(TYPE.SENSOR, PRIORITY.LOW).id(R.string.alert_780).format();
                break;
            case 781:
                alert(TYPE.SENSOR, PRIORITY.LOW).id(R.string.alert_781).format();
                break;
            case 784:
                alert(TYPE.SENSOR, PRIORITY.HIGH).id(R.string.alert_784).format();
                break;
            case 786:
                alert(TYPE.SENSOR, PRIORITY.HIGH).id(R.string.alert_786).clock(0).format();
                break;
            case 787:
                alert(TYPE.SENSOR, PRIORITY.HIGH).id(R.string.alert_787).format();
                break;
            case 788:
                alert(TYPE.SENSOR, PRIORITY.HIGH).id(R.string.alert_788).format();
                break;
            case 789:
                alert(TYPE.SENSOR, PRIORITY.LOW).id(R.string.alert_789).format();
                break;
            case 790:
                alert(TYPE.SENSOR, PRIORITY.LOW).id(R.string.alert_790).format();
                break;
            case 791:
                alert(TYPE.SENSOR, PRIORITY.LOW).id(R.string.alert_791).format();
                break;
            case 794:
                alert(TYPE.SENSOR, PRIORITY.HIGH).id(R.string.alert_794).format();
                break;
            case 795:
                alert(TYPE.SENSOR, PRIORITY.LOW).id(R.string.alert_795).format();
                break;
            case 796:
                alert(TYPE.SENSOR, PRIORITY.LOW).id(R.string.alert_796).format();
                break;
            case 797:
                alert(TYPE.SENSOR, PRIORITY.LOWEST).id(R.string.alert_797).format();
                break;
            case 798:
                alert(TYPE.SENSOR, PRIORITY.LOWEST).id(R.string.alert_798).format();
                break;
            case 799:
                alert(TYPE.SENSOR, PRIORITY.LOWEST).id(R.string.alert_799).format();
                break;
            case 801:
                alert(TYPE.SENSOR, PRIORITY.LOW).id(R.string.alert_801).format();
                break;
            case 802:
                alert(TYPE.SENSOR, PRIORITY.EMERGENCY).id(R.string.alert_802).glucose(1).format();
                break;
            case 803:
                alert(TYPE.SENSOR, PRIORITY.EMERGENCY).id(R.string.alert_803).format();
                break;
            case 805:
                alert(TYPE.SENSOR, PRIORITY.HIGH).id(R.string.alert_805).glucose(1).format();
                break;
            case 806:
                alert(TYPE.SMARTGUARD, PRIORITY.LOW).id(R.string.alert_806).format();
                break;
            case 807:
                alert(TYPE.SMARTGUARD, PRIORITY.LOW).id(R.string.alert_807).clock(4).format();
                break;
            case 808:
                alert(TYPE.SMARTGUARD, PRIORITY.LOW).id(R.string.alert_808).format();
                break;
            case 809:
                alert(TYPE.SMARTGUARD, PRIORITY.LOW).id(R.string.alert_809).glucose(1).format();
                break;
            case 810:
                alert(TYPE.SMARTGUARD, PRIORITY.LOW).id(R.string.alert_810).format();
                break;
            case 811:
                alert(TYPE.PUMP, PRIORITY.NORMAL).id(R.string.alert_811).format();
                break;
            case 812:
                alert(TYPE.PUMP, PRIORITY.EMERGENCY).id(R.string.alert_812).format();
                break;
            case 814:
                alert(TYPE.PUMP, PRIORITY.NORMAL).id(R.string.alert_814).format();
                break;
            case 815:
                alert(TYPE.PUMP, PRIORITY.NORMAL).id(R.string.alert_815).format();
                break;
            case 816:
                alert(TYPE.SENSOR, PRIORITY.EMERGENCY).id(R.string.alert_816).glucose(1).format();
                break;
            case 817:
                alert(TYPE.SENSOR, PRIORITY.HIGH).id(R.string.alert_817).glucose(1).format();
                break;
            case 869:
                alert(TYPE.REMINDER, PRIORITY.NORMAL).id(R.string.alert_869).clock(0).format();
                break;
            case 870:
                alert(TYPE.SENSOR, PRIORITY.NORMAL).id(R.string.alert_870).format();
                break;

            // not shown in the pump alarm history, only as an active alert

            case 103:
                alert(TYPE.REMINDER, PRIORITY.NORMAL).id(R.string.alert_103).hours(0).format();
                break;
            case 107:
                alert(TYPE.REMINDER, PRIORITY.NORMAL).id(R.string.alert_107).format();
                break;
            case 108:
                alert(TYPE.REMINDER, PRIORITY.NORMAL).id(R.string.alert_108).list(2, R.string.alert_108_list).clock(0).format();
                break;
            case 109:
                alert(TYPE.REMINDER, PRIORITY.NORMAL).id(R.string.alert_109).list(0, R.string.alert_109_list).format();
                break;
            case 110:
                alert(TYPE.SENSOR, PRIORITY.LOWEST).id(R.string.alert_110).format();
                break;

            // 670G auto mode alerts

            // *** 819 and 820 may be other way around? ***

            // Auto Mode off|Basal X started. Would you like to review the Auto Mode Readiness screen
            // user turned off sensor / suspend has not been cleared within 4 hours / user in Safe Basal for maximum of 90 minutes
            // history:false
            // TW: *** seen when AM stopped after Auto Mode exit High SG
            case 819:
                alert(TYPE.AUTOMODE, PRIORITY.LOW).id(R.string.alert_819).pattern(0).format();
                break;

            // Auto Mode exit|Basal X started.Would you like to review the Auto Mode Readiness screen?
            // user turned off sensor / suspend has not been cleared within 4 hours / user in Safe Basal for maximum of 90 minutes
            // history:false
            // TW: Auto Mode exit|User disabled *** seen when AM stopped to change sensor
            case 820:
                alert(TYPE.AUTOMODE, PRIORITY.HIGH).id(R.string.alert_820).pattern(0).format();
                break;

            // Auto Mode min delivery|Auto Mode has been at minimum delivery for 2:30 hours. Enter BG to continue in Auto Mode.
            // history:true
            case 821:
                alert(TYPE.AUTOMODE, PRIORITY.HIGH).id(R.string.alert_821).pattern(0).format();
                break;

            // Auto Mode max delivery|Auto Mode has been at maximum delivery for 4 hours. Enter BG to continue in Auto Mode.
            // history:true
            case 822:
                alert(TYPE.AUTOMODE, PRIORITY.HIGH).id(R.string.alert_822).format();
                break;

            // Auto Mode exit High SG|Manual Mode started. Check infusion site. Check ketones. Consider an injection. Monitor your BG. Basal Pattern %1$s resumed.
            // history:true
            case 823:
                alert(TYPE.AUTOMODE, PRIORITY.HIGH).id(R.string.alert_823).pattern(0).format();
                break;
            case 824:
                alert(TYPE.AUTOMODE, PRIORITY.HIGH).id(R.string.alert_824).pattern(0).format();
                break;

            // *** unknown ***
            // history:true
            //case 825:

            // *** unknown ***
            //case 826:

            // *** unconfirmed ***
            // BG required|Enter a new BG for Auto Mode.
            // history:true
            // note: seen after a sensor Alert On Low, may contain sgv data
            case 827:
                alert(TYPE.AUTOMODE, PRIORITY.REDUNDANT).id(R.string.alert_827).format();
                break;

            // *** unknown ***
            //case 828:

            // BG required|Enter a new BG for Auto Mode.
            // history:true
            case 829:
                alert(TYPE.AUTOMODE, PRIORITY.REDUNDANT).id(R.string.alert_829).format();
                break;
            case 830:
                alert(TYPE.AUTOMODE, PRIORITY.REDUNDANT).id(R.string.alert_830).format();
                break;
            case 831:
                alert(TYPE.AUTOMODE, PRIORITY.REDUNDANT).id(R.string.alert_831).format();
                break;

            // *** unconfirmed ***
            // Cal required for Auto Mode|Enter a BG and calibrate sensor for Auto Mode
            // history:true
            // note: seen after a BG entered but no cal occurred and a cal is still required
            case 832:
                alert(TYPE.AUTOMODE, PRIORITY.NORMAL).id(R.string.alert_832).format();
                break;

            // Bolus recommended|For %1$s entered, a correction bolus is recommended.
            // history:false
            // note: if user does a bolus this message and bolus can obscure each other, option to disable this alert in NS? option for PO? note alert if bolus given?
            case 833:
                alert(TYPE.PUMP, PRIORITY.REDUNDANT).id(R.string.alert_833).glucose(0).format();
                break;

            // unknown alert

            default:
                code = 0;
                alert(TYPE.NA, PRIORITY.LOW).id((pumpDate != null && alarmHistory) ? R.string.alert_unknown_in_history : R.string.alert_unknown_not_in_history).format();
        }

        return this;
    }

    private PumpAlert alert(TYPE type, PRIORITY priority) {
        this.type = type;
        this.priority = priority;
        return this;
    }

    private PumpAlert id(int id) {
        this.id = id;
        return this;
    }

    private PumpAlert format() {
        try {
            String parts[] = String.format(FormatKit.getInstance().getString(id), extras.toArray()).split("\\|");
            message = parts[0];
            extended = parts.length == 2 ? parts[1] : "";
        } catch (Exception e) {
            Log.e(TAG, "could not format final alert string: " + e);
            message = "[format error]";
        }
        return this;
    }

    private PumpAlert value(int offset) {
        if (alarmData != null)
            extras.add(FormatKit.getInstance().getNameBasalPattern(alarmData[offset]));
        else extras.add("~");
        return this;
    }

    private PumpAlert pattern(int offset) {
        if (alarmData != null)
            extras.add(FormatKit.getInstance().getNameBasalPattern(alarmData[offset]));
        else extras.add("~");
        return this;
    }

    private PumpAlert glucose(int offset) {
        if (alarmData != null) {
            int sgv = ToolKit.read16BEtoInt(alarmData, offset) & 0xFFFF;
            if (sgv < 0x0300)
                extras.add(FormatKit.getInstance().formatAsGlucose(sgv, units));
            else extras.add("");
        }
        else extras.add("");
        return this;
    }

    private PumpAlert clock(int offset) {
        if (alarmData != null)
            extras.add(FormatKit.getInstance().formatAsClock(alarmData[offset] & 0xFF, alarmData[offset + 1] & 0xFF));
        else extras.add("~");
        return this;
    }

    private PumpAlert hours(int offset) {
        if (alarmData != null)
            extras.add(FormatKit.getInstance().formatAsHoursMinutes(alarmData[offset] & 0xFF, alarmData[offset + 1] & 0xFF));
        else extras.add("~");
        return this;
    }

    private PumpAlert insulin(int offset) {
        if (alarmData != null)
            extras.add(FormatKit.getInstance().formatAsInsulin(ToolKit.read32BEtoInt(alarmData, offset) / 10000.0));
        else extras.add("~");
        return this;
    }

    private PumpAlert list(int offset, int id) {
        if (alarmData != null) {
            int index = alarmData[offset] - 1;
            String[] list = FormatKit.getInstance().getString(id).split("\\|");
            if (index >0 && index <= list.length)
                extras.add(list[index]);
            else {
                Log.e(TAG, String.format("list index out of range, length = %s index = %s", list.length, index));
                extras.add("~");
            }
        }
        else extras.add("~");
        return this;
    }

    public String getTitle() {
        switch (type) {
            case PUMP:
                return FormatKit.getInstance().getString(R.string.alert_pump);
            case SENSOR:
                return FormatKit.getInstance().getString(R.string.alert_sensor);
            case REMINDER:
                return FormatKit.getInstance().getString(R.string.alert_reminder);
            case SMARTGUARD:
                return FormatKit.getInstance().getString(R.string.alert_smartguard);
            case AUTOMODE:
                return FormatKit.getInstance().getString(R.string.alert_automode);
            default:
                return FormatKit.getInstance().getString(R.string.alert_na);
        }
    }

    public String getTitleAlt() {
        if (cleared) {
            switch (type) {
                case REMINDER:
                    return FormatKit.getInstance().getString(R.string.alert_cleared_reminder);
                default:
                    return FormatKit.getInstance().getString(R.string.alert_cleared);
            }
        } else if (silenced) {
            switch (type) {
                default:
                    return FormatKit.getInstance().getString(R.string.alert_silenced);
            }
        } else {
            return getTitle();
        }
    }

    public String getInfo(boolean isAppended) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("%s#%s [Mode:%s History:%s Data:%s",
                isAppended ? " " : "",
                faultNumber, notificationMode, alarmHistory, extraData));

        if (extraData && alarmData != null) {
            sb.append(" ");
            for (byte b : alarmData) sb.append(String.format("%02X", b));
        }
        sb.append("]");

        if (pumpDate != null){
            sb.append(String.format(" [Pump: %s]", FormatKit.getInstance().formatAsYMDHMS(pumpDate.getTime())));
        }

        return sb.toString();
    }

    public String getCleared(boolean isAppended) {
        if (!cleared) return "";
        int duration = (int) ((clearedDate.getTime() - alarmedDate.getTime()) / 1000L);
        return String.format("%s%s %s%s",
                isAppended ? " (" : "",
                FormatKit.getInstance().getString(R.string.alert_cleared_tag),
                FormatKit.getInstance().formatSecondsAsDHMS(duration),
                isAppended ? ")" : ""
        );
    }

    public String getSilenced(boolean isAppended) {
        if (!silenced) return "";
        return String.format("%s%s%s",
                isAppended ? " (" : "",
                FormatKit.getInstance().getString(R.string.alert_silenced_tag),
                isAppended ? ")" : ""
        );
    }

    public String getRepeated(boolean isAppended) {
        if (!repeated) return "";
        return String.format("%s%s%s",
                isAppended ? " (" : "",
                FormatKit.getInstance().getString(R.string.alert_repeated_tag),
                isAppended ? ")" : ""
        );
    }

    public boolean isAlertKnown() {
        return code > 0;
    }

    public int getCode() {
        return code;
    }

    public TYPE getType() {
        return type;
    }

    public int getPriority() {
        return priority.value();
    }

    public String getMessage() {
        return message;
    }

    public String getExtended() {
        return extended;
    }

    public String getComplete() {
        return extended.length() == 0 ? message : message + ". " + extended;
    }

}

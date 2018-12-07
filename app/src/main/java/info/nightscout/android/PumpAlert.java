package info.nightscout.android;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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

    private int faultNumber;
    private int notificationMode;
    private boolean alarmHistory;
    private boolean extraData;

    private Date alarmedDate;
    private Date clearedDate;

    private byte[] alarmData;

    public enum TYPE {
        NA,
        PUMP,
        SENSOR,
        REMINDER,
        AUTOMODE,
        SYSTEM;
    }

    public enum PRIORITY {
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
        clearedDate = record.getClearedDate();
        alarmedDate = record.getAlarmedDate();
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

            case 7:
                alert(TYPE.PUMP, PRIORITY.EMERGENCY).id(R.string.alert_7).format();
                break;
            case 8:
                alert(TYPE.PUMP, PRIORITY.EMERGENCY).id(R.string.alert_8).format();
                break;
            case 11:
                alert(TYPE.PUMP, PRIORITY.EMERGENCY).id(R.string.alert_11).format();
                break;
            case 58:
                alert(TYPE.PUMP, PRIORITY.NORMAL).id(R.string.alert_58).format();
                break;
            case 61:
                alert(TYPE.PUMP, PRIORITY.LOWEST).id(R.string.alert_61).format();
                break;
            case 70:
                alert(TYPE.PUMP, PRIORITY.LOW).id(R.string.alert_70).format();
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
                alert(TYPE.PUMP, PRIORITY.LOWEST).id(R.string.alert_806).format();
                break;
            case 807:
                alert(TYPE.PUMP, PRIORITY.LOWEST).id(R.string.alert_807).clock(4).format();
                break;
            case 808:
                alert(TYPE.PUMP, PRIORITY.NORMAL).id(R.string.alert_808).format();
                break;
            case 809:
                alert(TYPE.PUMP, PRIORITY.NORMAL).id(R.string.alert_809).glucose(1).format();
                break;
            case 810:
                alert(TYPE.PUMP, PRIORITY.LOWEST).id(R.string.alert_810).format();
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

            // need verifying

            case 6:
                alert(TYPE.PUMP, PRIORITY.LOW).id(R.string.alert_6).format();
                break;

            // auto mode alerts - best guess! ;)

            case 820:
                alert(TYPE.AUTOMODE, PRIORITY.NORMAL).id(R.string.alert_820).pattern(0).format();
                break;
            case 821:
                alert(TYPE.AUTOMODE, PRIORITY.LOW).id(R.string.alert_821).format();
                break;
            case 823:
                alert(TYPE.AUTOMODE, PRIORITY.NORMAL).id(R.string.alert_823).pattern(0).format();
                break;
            case 824:
                alert(TYPE.AUTOMODE, PRIORITY.LOW).id(R.string.alert_824).format();
                break;

            case 830:
                alert(TYPE.AUTOMODE, PRIORITY.LOW).id(R.string.alert_830).format();
                break;
            case 831:
                alert(TYPE.AUTOMODE, PRIORITY.LOW).id(R.string.alert_831).format();
                break;

            case 833:
                alert(TYPE.PUMP, PRIORITY.LOWEST).id(R.string.alert_833).glucose(0).format();
                break;

            // unknown alert

            default:
                code = 0;
                alert(TYPE.NA, PRIORITY.LOW).id(R.string.alert_0).format();
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

        return sb.toString();
    }

    public String getSilenced(boolean isAppended) {
        if (!silenced) return "";
        return String.format("%s%s%s",
                isAppended ? " (" : "",
                FormatKit.getInstance().getString(R.string.alert_silenced_tag),
                isAppended ? ")" : ""
        );
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

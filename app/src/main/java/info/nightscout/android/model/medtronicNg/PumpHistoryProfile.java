package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import info.nightscout.android.medtronic.PumpHistoryParser;
import info.nightscout.api.ProfileEndpoints;
import info.nightscout.api.TreatmentsEndpoints;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;

import static info.nightscout.android.utils.ToolKit.read8toUInt;
import static info.nightscout.android.utils.ToolKit.read32BEtoInt;
import static info.nightscout.android.utils.ToolKit.read32BEtoULong;
import static info.nightscout.android.utils.ToolKit.read16BEtoUInt;

/**
 * Created by John on 7.11.17.
 */

public class PumpHistoryProfile extends RealmObject implements PumpHistoryInterface {
    @Ignore
    private static final String TAG = PumpHistoryProfile.class.getSimpleName();

    @Index
    private Date eventDate;

    @Index
    private boolean uploadREQ = false;
    private boolean uploadACK = false;

    private boolean xdripREQ = false;
    private boolean xdripACK = false;

    private String key; // unique identifier for nightscout, key = "ID" + RTC as 8 char hex ie. "CGM6A23C5AA"

    @Index
    private int profileRTC;
    private int profileOFFSET;

    private boolean profileSwitch = false;
    private int oldPatternNumber;
    private int newPatternNumber;

    private boolean profileDefine = false;
    private int defaultProfile;
    private int units;
    private int carbsPerHour;
    private int insulinDuration;

    private byte[] basalPatterns;
    private byte[] carbRatios;
    private byte[] sensitivity;
    private byte[] targets;

    @Override
    public List nightscout() {
        List list = new ArrayList();

        if (profileSwitch) {

            TreatmentsEndpoints.Treatment treatment = new TreatmentsEndpoints.Treatment();
            list.add("treatment");
            list.add(uploadACK ? "update" : "new");
            list.add(treatment);

            treatment.setKey600(key);
            treatment.setCreated_at(eventDate);
            treatment.setEventType("Profile Switch");

            String oldName = PumpHistoryParser.TextEN.valueOf(PumpHistoryParser.BASAL_PATTERN.convert(oldPatternNumber).name()).getText();
            String newName = PumpHistoryParser.TextEN.valueOf(PumpHistoryParser.BASAL_PATTERN.convert(newPatternNumber).name()).getText();

            treatment.setProfile(newName);
            treatment.setNotes("Changed profile from " + oldName + " to " + newName);

        } else if (profileDefine) {

            TreatmentsEndpoints.Treatment treatment = new TreatmentsEndpoints.Treatment();
            list.add("treatment");
            list.add(uploadACK ? "update" : "new");
            list.add(treatment);

            treatment.setKey600(key);
            treatment.setCreated_at(eventDate);
            treatment.setEventType("Note");
            treatment.setNotes("Profile updated");

            ProfileEndpoints.Profile profile = new ProfileEndpoints.Profile();
            list.add("profile");
            list.add(uploadACK ? "update" : "new");
            list.add(profile);

            TimeZone tz = TimeZone.getDefault();
            Date startdate = new Date(eventDate.getTime() - 90 * 24 * 60 * 60000L) ; // active from date
            String timezone = tz.getID();  // (Time Zone) - time zone local to the patient. Should be set.
            String units = "mmol"; // (Profile Units) - blood glucose units used in the profile, either "mgdl" or "mmol"   ??? get from uploader or NS ???
            String carbshr = "20"; // (Carbs per Hour) - The number of carbs that are processed per hour          ??? set as 30/35 as an general average for now ???
            String dia = "3"; // (Insulin duration) - value should be the duration of insulin action to use in calculating how much insulin is left active. Defaults to 3 hours.
            String delay = "20"; // NS default value - delay from action to activation for insulin?

            profile.setKey600(key);
            profile.setCreated_at(eventDate);
            profile.setStartDate(startdate);
            profile.setMills("" + startdate.getTime());
//            profile.setStartDate("1970-01-01T00:00:00");

            profile.setDefaultProfile("Basal 1");
            profile.setUnits(units);

            ProfileEndpoints.Store store = new ProfileEndpoints.Store(); // <-- BasalProfile x8 as named on Pump
            profile.setStore(store);

            BasalProfile bp = new BasalProfile();
            bp.startdate = startdate;
            bp.timezone = timezone;
            bp.units = units;
            bp.carbshr = carbshr;
            bp.dia = dia;
            bp.delay = delay;
            bp.parseCarbRatios();
            bp.parseSensitivity();
            bp.parseTargets();

            store.setBasal1(bp.makeProfile());
            store.setBasal2(bp.makeProfile());
            store.setBasal3(bp.makeProfile());
            store.setBasal4(bp.makeProfile());
            store.setBasal5(bp.makeProfile());
            store.setWorkday(bp.makeProfile());
            store.setDayoff(bp.makeProfile());
            store.setSickday(bp.makeProfile());
        }

        return list;
    }

    private class BasalProfile {
        List<ProfileEndpoints.TimePeriod> carbratio;
        List<ProfileEndpoints.TimePeriod> sens;
        List<ProfileEndpoints.TimePeriod> target_low;
        List<ProfileEndpoints.TimePeriod> target_high;
        Date startdate;
        String timezone;
        String delay;
        String carbshr;
        String dia;
        String units;
        int index = 0;

        private ProfileEndpoints.BasalProfile makeProfile() {
            ProfileEndpoints.BasalProfile basalProfile = new ProfileEndpoints.BasalProfile();

            basalProfile.setStartDate(startdate);
            basalProfile.setTimezone(timezone);
            basalProfile.setDelay(delay);
            basalProfile.setCarbs_hr(carbshr);
            basalProfile.setDia(dia);
            basalProfile.setUnits(units);

            basalProfile.setCarbratio(carbratio);
            basalProfile.setSens(sens);
            basalProfile.setTarget_low(target_low);
            basalProfile.setTarget_high(target_high);

            basalProfile.setBasal(parseBasalPattern());

            return basalProfile;
        }

        private List<ProfileEndpoints.TimePeriod> parseBasalPattern() {
            List<ProfileEndpoints.TimePeriod> basalpattern = new ArrayList();

            int pattern = read8toUInt(basalPatterns, index++);
            int items = read8toUInt(basalPatterns, index++);
            double rate;
            int time;

            if (items == 0)
                basalpattern.add(addPeriod(0, "0.0"));
            else {
                for (int i = 0; i < items; i++) {
                    rate = read32BEtoULong(basalPatterns, index) / 10000.0;
                    time = read8toUInt(basalPatterns, index + 4) * 30;
                    basalpattern.add(addPeriod(time, "" + rate));
                    index += 5;
                }
            }

            return basalpattern;
        }

        private void parseCarbRatios() {
            carbratio = new ArrayList();
            int index = 0;
            int rate1;
            int rate2;
            int time;

            int items = read8toUInt(carbRatios, index++);
            if (items == 0)
                carbratio.add(addPeriod(0, "0"));
            else {
                for (int i = 0; i < items; i++) {
                    rate1 = read32BEtoInt(carbRatios, index + 0) / 10;
                    rate2 = read32BEtoInt(carbRatios, index + 4);
                    time = read8toUInt(carbRatios, index + 8) * 30;
                    carbratio.add(addPeriod(time, "" + rate1));
                    index += 9;
                }
            }
        }

        private void parseSensitivity() {
            sens = new ArrayList();
            int index = 0;
            int isf_mgdl;
            double isf_mmol;
            int time;

            int items = read8toUInt(sensitivity, index++);
            if (items == 0)
                sens.add(addPeriod(0, "0"));
            else {
                for (int i = 0; i < items; i++) {
                    isf_mgdl = read16BEtoUInt(sensitivity, index + 0);
                    isf_mmol = read16BEtoUInt(sensitivity, index + 2) / 10.0;
                    time = read8toUInt(sensitivity, index + 4) * 30;
                    sens.add(addPeriod(time, "" + (units.equals("mgdl") ? isf_mgdl : isf_mmol)));
                    index += 5;
                }
            }
        }

        private void parseTargets() {
            target_low = new ArrayList();
            target_high = new ArrayList();
            int index = 0;
            int hi_mgdl;
            double hi_mmol;
            int lo_mgdl;
            double lo_mmol;
            int time;

            int items = read8toUInt(targets, index++);
            if (items == 0) {
                target_low.add(addPeriod(0, "0"));
                target_high.add(addPeriod(0, "0"));
            } else {
                for (int i = 0; i < items; i++) {
                    hi_mgdl = read16BEtoUInt(targets, index + 0);
                    hi_mmol = read16BEtoUInt(targets, index + 2) / 10.0;
                    lo_mgdl = read16BEtoUInt(targets, index + 4);
                    lo_mmol = read16BEtoUInt(targets, index + 6) / 10.0;
                    time = read8toUInt(targets, index + 8) * 30;
                    target_low.add(addPeriod(time, "" + (units.equals("mgdl") ? lo_mgdl : lo_mmol)));
                    target_high.add(addPeriod(time, "" + (units.equals("mgdl") ? hi_mgdl : hi_mmol)));
                    index += 9;
                }
            }
        }

        private ProfileEndpoints.TimePeriod addPeriod(int time, String value) {
            ProfileEndpoints.TimePeriod period = new ProfileEndpoints.TimePeriod();
            period.setTimeAsSeconds("" + (time * 60));
            period.setTime((time / 60 < 10 ? "0" : "") + (time / 60) + (time % 60 < 10 ? ":0" : ":") + (time % 60));
            period.setValue(value);
            return period;
        }
    }

    public static void profile(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                               byte[] basalPatterns,
                               byte[] carbRatios,
                               byte[] sensitivity,
                               byte[] targets) {
        Log.d(TAG, "*new*" + " profile");
        // create new entry
        PumpHistoryProfile object = realm.createObject(PumpHistoryProfile.class);
        object.setKey("PRO" + String.format("%08X", eventRTC));
        object.setProfileDefine(true);
        object.setEventDate(eventDate);
        object.setProfileRTC(eventRTC);
        object.setProfileOFFSET(eventOFFSET);
        object.setBasalPatterns(basalPatterns);
        object.setCarbRatios(carbRatios);
        object.setSensitivity(sensitivity);
        object.setTargets(targets);
        object.setUploadREQ(true);
    }

    public static void select(Realm realm, Date eventDate, int eventRTC, int eventOFFSET,
                              int oldPatternNumber,
                              int newPatternNumber) {

        PumpHistoryProfile object = realm.where(PumpHistoryProfile.class)
                .equalTo("profileRTC", eventRTC)
                .findFirst();
        if (object == null) {
            Log.d(TAG, "*new*" + " profile basal pattern switch");
            // create new entry
            object = realm.createObject(PumpHistoryProfile.class);
            object.setKey("PRO" + String.format("%08X", eventRTC));
            object.setProfileSwitch(true);
            object.setEventDate(eventDate);
            object.setProfileRTC(eventRTC);
            object.setProfileOFFSET(eventOFFSET);
            object.setOldPatternNumber(oldPatternNumber);
            object.setNewPatternNumber(newPatternNumber);
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

    public int getProfileRTC() {
        return profileRTC;
    }

    public void setProfileRTC(int profileRTC) {
        this.profileRTC = profileRTC;
    }

    public int getProfileOFFSET() {
        return profileOFFSET;
    }

    public void setProfileOFFSET(int profileOFFSET) {
        this.profileOFFSET = profileOFFSET;
    }

    public boolean isProfileSwitch() {
        return profileSwitch;
    }

    public void setProfileSwitch(boolean profileSwitch) {
        this.profileSwitch = profileSwitch;
    }

    public int getOldPatternNumber() {
        return oldPatternNumber;
    }

    public void setOldPatternNumber(int oldPatternNumber) {
        this.oldPatternNumber = oldPatternNumber;
    }

    public int getNewPatternNumber() {
        return newPatternNumber;
    }

    public void setNewPatternNumber(int newPatternNumber) {
        this.newPatternNumber = newPatternNumber;
    }

    public boolean isProfileDefine() {
        return profileDefine;
    }

    public void setProfileDefine(boolean profileDefine) {
        this.profileDefine = profileDefine;
    }

    public int getDefaultProfile() {
        return defaultProfile;
    }

    public void setDefaultProfile(int defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    public int getUnits() {
        return units;
    }

    public void setUnits(int units) {
        this.units = units;
    }

    public int getCarbsPerHour() {
        return carbsPerHour;
    }

    public void setCarbsPerHour(int carbsPerHour) {
        this.carbsPerHour = carbsPerHour;
    }

    public int getInsulinDuration() {
        return insulinDuration;
    }

    public void setInsulinDuration(int insulinDuration) {
        this.insulinDuration = insulinDuration;
    }

    public byte[] getBasalPatterns() {
        return basalPatterns;
    }

    public void setBasalPatterns(byte[] basalPatterns) {
        this.basalPatterns = basalPatterns;
    }

    public byte[] getCarbRatios() {
        return carbRatios;
    }

    public void setCarbRatios(byte[] carbRatios) {
        this.carbRatios = carbRatios;
    }

    public byte[] getSensitivity() {
        return sensitivity;
    }

    public void setSensitivity(byte[] sensitivity) {
        this.sensitivity = sensitivity;
    }

    public byte[] getTargets() {
        return targets;
    }

    public void setTargets(byte[] targets) {
        this.targets = targets;
    }
}

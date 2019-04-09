package info.nightscout.android.model.medtronicNg;

import android.util.Log;

import com.google.gson.internal.LinkedHashTreeMap;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import info.nightscout.android.history.HistoryUtils;
import info.nightscout.android.history.MessageItem;
import info.nightscout.android.history.NightscoutItem;
import info.nightscout.android.history.PumpHistorySender;
import info.nightscout.android.upload.nightscout.ProfileEndpoints;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;

import static info.nightscout.android.utils.ToolKit.read8toUInt;
import static info.nightscout.android.utils.ToolKit.read32BEtoInt;
import static info.nightscout.android.utils.ToolKit.read32BEtoULong;
import static info.nightscout.android.utils.ToolKit.read16BEtoUInt;

/**
 * Created by Pogman on 7.11.17.
 */

public class PumpHistoryProfile extends RealmObject implements PumpHistoryInterface {
    @Ignore
    private static final String TAG = PumpHistoryProfile.class.getSimpleName();
    @Ignore
    private static final String AUTOMODE = "Auto Mode";

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
    private int profileRTC;
    private int profileOFFSET;

    private String units;
    private double insulinDuration;
    private double insulinDelay;
    private double carbsPerHour;
    private byte defaultProfile;

    private byte[] basalPatterns;
    private byte[] carbRatios;
    private byte[] sensitivity;
    private byte[] targets;

    @Override
    public List<NightscoutItem> nightscout(PumpHistorySender pumpHistorySender, String senderID) {
        List<NightscoutItem> nightscoutItems = new ArrayList<>();
/*
        if (pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.TREATMENTS)) {
            TreatmentsEndpoints.Treatment treatment = HistoryUtils.nightscoutTreatment(nightscoutItems, this, senderID);
            treatment.setEventType("Note");
            treatment.setNotes("Profile updated");
        }
*/
        NightscoutItem nightscoutItem = new NightscoutItem();
        nightscoutItems.add(nightscoutItem);
        ProfileEndpoints.Profile profile = nightscoutItem.ack(senderACK.contains(senderID)).profile();

        TimeZone tz = TimeZone.getDefault();
        Date startdate = new Date(eventDate.getTime()); // - 90 * 24 * 60 * 60000L) ; // active from date
        String timezone = tz.getID();  // (Time Zone) - time zone local to the patient. Should be set.

        if (pumpHistorySender.isOpt(senderID, PumpHistorySender.SENDEROPT.PROFILE_OFFSET)) startdate = new Date(eventDate.getTime() - 365 * 24 * 60 * 60000L) ; // active from date

        profile.setKey600(key);
        profile.setCreated_at(eventDate);
        profile.setStartDate(startdate);
        profile.setMills("" + startdate.getTime());
        profile.setDefaultProfile(defaultProfile == 9 ? AUTOMODE : pumpHistorySender.getList(senderID, PumpHistorySender.SENDEROPT.BASAL_PATTERN,defaultProfile - 1));
        profile.setUnits(units);

        Map<String, ProfileEndpoints.BasalProfile> basalProfileMap = new LinkedHashTreeMap<>();
        profile.setBasalProfileMap(basalProfileMap);

        BasalProfile bp = new BasalProfile();
        bp.startdate = startdate;
        bp.timezone = timezone;
        bp.units = units; // (Profile Units) - blood glucose units used in the profile, either "mg/dl" or "mmol"
        bp.carbshr = Double.toString(carbsPerHour); // (Carbs per Hour) - The number of carbs that are processed per hour. Default 20.
        bp.dia = Double.toString(insulinDuration); // (Insulin duration) - value should be the duration of insulin action to use in calculating how much insulin is left active. Default 3 hours.
        bp.delay = Double.toString(insulinDelay); // delay from action to activation for insulin? Default 20.
        bp.carbsPerExchange = Integer.parseInt(pumpHistorySender.getVar(senderID, PumpHistorySender.SENDEROPT.GRAMS_PER_EXCHANGE));
        bp.parseCarbRatios();
        bp.parseSensitivity();
        bp.parseTargets();

        basalProfileMap.put(pumpHistorySender.getList(senderID, PumpHistorySender.SENDEROPT.BASAL_PATTERN, 0), bp.newProfile().parseBasalPattern().makeProfile());
        basalProfileMap.put(pumpHistorySender.getList(senderID, PumpHistorySender.SENDEROPT.BASAL_PATTERN, 1), bp.newProfile().parseBasalPattern().makeProfile());
        basalProfileMap.put(pumpHistorySender.getList(senderID, PumpHistorySender.SENDEROPT.BASAL_PATTERN, 2), bp.newProfile().parseBasalPattern().makeProfile());
        basalProfileMap.put(pumpHistorySender.getList(senderID, PumpHistorySender.SENDEROPT.BASAL_PATTERN, 3), bp.newProfile().parseBasalPattern().makeProfile());
        basalProfileMap.put(pumpHistorySender.getList(senderID, PumpHistorySender.SENDEROPT.BASAL_PATTERN, 4), bp.newProfile().parseBasalPattern().makeProfile());
        basalProfileMap.put(pumpHistorySender.getList(senderID, PumpHistorySender.SENDEROPT.BASAL_PATTERN, 5), bp.newProfile().parseBasalPattern().makeProfile());
        basalProfileMap.put(pumpHistorySender.getList(senderID, PumpHistorySender.SENDEROPT.BASAL_PATTERN, 6), bp.newProfile().parseBasalPattern().makeProfile());
        basalProfileMap.put(pumpHistorySender.getList(senderID, PumpHistorySender.SENDEROPT.BASAL_PATTERN, 7), bp.newProfile().parseBasalPattern().makeProfile());

        // "Auto Mode" profile using zero rate pattern for 670G pump
        basalProfileMap.put(AUTOMODE, bp.newProfile().emptyBasalPattern().makeProfile());

        return nightscoutItems;
    }

    private class BasalProfile {
        ProfileEndpoints.BasalProfile basalProfile;
        List<ProfileEndpoints.TimePeriod> basal;
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
        int carbsPerExchange;
        int index = 0;

        DecimalFormat df;

        private BasalProfile() {
            df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
            df.setMaximumFractionDigits(4);
        }

        private BasalProfile newProfile() {
            basalProfile = new ProfileEndpoints.BasalProfile();
            return this;
        }

        private ProfileEndpoints.BasalProfile makeProfile() {
            basalProfile.setStartDate(startdate);
            basalProfile.setTimezone(timezone);
            basalProfile.setDelay(delay);
            basalProfile.setCarbs_hr(carbshr);
            basalProfile.setDia(dia);
            basalProfile.setUnits(units);

            basalProfile.setBasal(basal);
            basalProfile.setCarbratio(carbratio);
            basalProfile.setSens(sens);
            basalProfile.setTarget_low(target_low);
            basalProfile.setTarget_high(target_high);

            return basalProfile;
        }

        private BasalProfile emptyBasalPattern() {
            basal = new ArrayList<>();
            // NS has an issue with a "0" rate basal pattern
            // using "0.000001" as workaround
            basal.add(addPeriod(0, "0.000001"));
            return this;
        }

        private BasalProfile parseBasalPattern() {
            basal = new ArrayList<>();

            int pattern = read8toUInt(basalPatterns, index++);
            int items = read8toUInt(basalPatterns, index++);
            double rate;
            int time;

            if (items == 0)
                basal.add(addPeriod(0, "0.000001"));
            else {
                for (int i = 0; i < items; i++) {
                    rate = read32BEtoULong(basalPatterns, index) / 10000.0;
                    time = read8toUInt(basalPatterns, index + 4) * 30;
                    basal.add(addPeriod(time, df.format(rate)));
                    index += 5;
                }
            }

            return this;
        }

        private void parseCarbRatios() {
            carbratio = new ArrayList<>();
            int index = 0;
            double rate1;
            double rate2;
            int time;

            int items = read8toUInt(carbRatios, index++);
            if (items == 0)
                carbratio.add(addPeriod(0, "0"));
            else {
                for (int i = 0; i < items; i++) {
                    // pump grams/unit rate
                    rate1 = read32BEtoInt(carbRatios, index) / 10.0;
                    // pump units/exchange rate (converted by dividing into carbsPerExchange default=15g, the standard amount of grams per exchange)
                    rate2 = carbsPerExchange / (read32BEtoInt(carbRatios, index + 4) / 1000.0);
                    time = read8toUInt(carbRatios, index + 8) * 30;
                    carbratio.add(addPeriod(time, df.format(rate1 > 0 ? rate1 : rate2)));
                    index += 9;
                }
            }
        }

        private void parseSensitivity() {
            sens = new ArrayList<>();
            int index = 0;
            int isf_mgdl;
            double isf_mmol;
            int time;

            int items = read8toUInt(sensitivity, index++);
            if (items == 0)
                sens.add(addPeriod(0, "0"));
            else {
                for (int i = 0; i < items; i++) {
                    isf_mgdl = read16BEtoUInt(sensitivity, index);
                    isf_mmol = read16BEtoUInt(sensitivity, index + 2) / 10.0;
                    time = read8toUInt(sensitivity, index + 4) * 30;
                    sens.add(addPeriod(time, df.format(units.equals("mmol") ? isf_mmol : isf_mgdl)));
                    index += 5;
                }
            }
        }

        private void parseTargets() {
            target_low = new ArrayList<>();
            target_high = new ArrayList<>();
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
                    hi_mgdl = read16BEtoUInt(targets, index);
                    hi_mmol = read16BEtoUInt(targets, index + 2) / 10.0;
                    lo_mgdl = read16BEtoUInt(targets, index + 4);
                    lo_mmol = read16BEtoUInt(targets, index + 6) / 10.0;
                    time = read8toUInt(targets, index + 8) * 30;
                    target_low.add(addPeriod(time, df.format(units.equals("mmol") ? lo_mmol : lo_mgdl)));
                    target_high.add(addPeriod(time, df.format(units.equals("mmol") ? hi_mmol : hi_mgdl)));
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

    public static void profile(
            PumpHistorySender pumpHistorySender, Realm realm,
            Date eventDate, int eventRTC, int eventOFFSET,
            String units,
            double insulinDuration,
            double insulinDelay,
            double carbsPerHour,
            byte defaultProfile,
            byte[] basalPatterns,
            byte[] carbRatios,
            byte[] sensitivity,
            byte[] targets) {
        Log.d(TAG, "*new*" + " profile");
        // create new entry
        PumpHistoryProfile record = realm.createObject(PumpHistoryProfile.class);
        record.key = HistoryUtils.key("PRO", eventRTC);
        record.eventDate = eventDate;
        record.profileRTC = eventRTC;
        record.profileOFFSET = eventOFFSET;
        record.units = units;
        record.insulinDuration = insulinDuration;
        record.insulinDelay = insulinDelay;
        record.carbsPerHour = carbsPerHour;
        record.defaultProfile = defaultProfile;
        record.basalPatterns = basalPatterns;
        record.carbRatios = carbRatios;
        record.sensitivity = sensitivity;
        record.targets = targets;
        pumpHistorySender.setSenderREQ(record);
    }

    @Override
    public List<MessageItem> message(PumpHistorySender pumpHistorySender, String senderID) {return new ArrayList<>();}

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

    public int getProfileRTC() {
        return profileRTC;
    }

    public int getProfileOFFSET() {
        return profileOFFSET;
    }

    public String getUnits() {
        return units;
    }

    public double getInsulinDuration() {
        return insulinDuration;
    }

    public double getInsulinDelay() {
        return insulinDelay;
    }

    public double getCarbsPerHour() {
        return carbsPerHour;
    }

    public byte getDefaultProfile() {
        return defaultProfile;
    }

    public byte[] getBasalPatterns() {
        return basalPatterns;
    }

    public byte[] getCarbRatios() {
        return carbRatios;
    }

    public byte[] getSensitivity() {
        return sensitivity;
    }

    public byte[] getTargets() {
        return targets;
    }
}

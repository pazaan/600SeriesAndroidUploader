package info.nightscout.android.upload.nightscout;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import info.nightscout.android.UploaderApplication;
import info.nightscout.android.model.medtronicNg.PumpHistory;
import info.nightscout.api.EntriesEndpoints;
import info.nightscout.api.ProfileEndpoints;
import info.nightscout.api.TreatmentsEndpoints;
import info.nightscout.api.UploadApi;
import io.realm.Realm;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Created by John on 1.11.17.
 */

/*
Nightscout notes:

Device - POST a single device status, POST does not support bulk upload (have not checked QUERY & GET & DELETE support)
Entries - QUERY support, GET & POST & DELETE a single entry, POST has bulk support
Treatments - QUERY support, GET & POST & DELETE a single treatment, POST has bulk support
Profile - no QUERY support, GET returns all profile sets, can POST & DELETE a single profile set, POST does not support bulk upload
*/

public class NightScoutUploadXXX {
    private static final String TAG = NightScoutUploadXXX.class.getSimpleName();

    NightScoutUploadXXX() {}

    boolean doRESTUpload(String url,
                         String secret,
                         List<PumpHistory> records) throws Exception {
        return isUploaded(records, url, secret);
    }

    private boolean isUploaded(List<PumpHistory> records,
                               String baseURL,
                               String secret) throws Exception {

        UploadApi uploadApi = new UploadApi(baseURL, formToken(secret));

        boolean eventsUploaded = uploadEvents(
                uploadApi.getEntriesEndpoints(),
                uploadApi.getTreatmentsEndpoints(),
                uploadApi.getProfileEndpoints(),
                records);

        return eventsUploaded;
    }

    private boolean uploadEvents(
            EntriesEndpoints entriesEndpoints,
            TreatmentsEndpoints treatmentsEndpoints,
            ProfileEndpoints profileEndpoints,
            List<PumpHistory> records
    ) throws Exception {

        List<EntriesEndpoints.Entry> entries = new ArrayList<>();
        List<TreatmentsEndpoints.Treatment> treatments = new ArrayList<>();
        List<ProfileEndpoints.Profile> profiles = new ArrayList<>();

        Realm historyRealm = Realm.getInstance(UploaderApplication.getHistoryConfiguration());
        boolean success = true;

        for (final PumpHistory record : records) {

            List list = record.Nightscout();
            Iterator iterator = list.iterator();
            while(iterator.hasNext()) {
                String type = (String) iterator.next();
                String cmd = (String) iterator.next();
                if (type == "entry") {
                    success &= processEntry(cmd, (EntriesEndpoints.Entry) iterator.next(), entriesEndpoints);
                } else if (type == "treatment") {
                    success &= processTreatment(cmd, (TreatmentsEndpoints.Treatment) iterator.next(), treatmentsEndpoints);
                } else if (type == "profile")
                    success &= processProfile(cmd, (ProfileEndpoints.Profile) iterator.next(), profileEndpoints);
            }
            if (success) {
                historyRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        record.setUploadACK(true);
                        record.setUploadREQ(false);
                    }
                });
            }
        }

        historyRealm.close();

/*
        // careful here as we can have a partial upload and error later thus no upload ACKs set
        boolean uploaded = true;
        if (entries.size() > 0) {
            Response<ResponseBody> result = entriesEndpoints.sendEntries(entries).execute();
            uploaded = result.isSuccessful();
        }
        if (treatments.size() > 0) {
            Response<ResponseBody> result = treatmentsEndpoints.sendTreatments(treatments).execute();
            uploaded = uploaded && result.isSuccessful();
        }
        if (profiles.size() > 0) {
            Response<ResponseBody> result = profileEndpoints.sendProfiles(profiles).execute();
            uploaded = uploaded && result.isSuccessful();
        }
        return uploaded;
*/
        return success;
    }

    private boolean processEntry(String cmd, EntriesEndpoints.Entry entry, EntriesEndpoints entriesEndpoints) throws Exception {

        String key = entry.getKey600();
        Response<List<EntriesEndpoints.Entry>> response = entriesEndpoints.checkKey("2017", key).execute();

        if (response.isSuccessful()) {
            List<EntriesEndpoints.Entry> list = response.body();
            int size = list.size();
            if (size > 0) {
                Log.d(TAG, "found " + list.size() + " already in nightscout for KEY: " + key);

                if (cmd == "update" || cmd == "delete" || size > 1) {
                    Response<ResponseBody> responseBody = entriesEndpoints.deleteKey("2017", key).execute();
                    if (responseBody.isSuccessful()) {
                        Log.d(TAG, "deleted " + size + " with KEY: " + key);
                    } else {
                        Log.d(TAG, "no DELETE response from nightscout site");
                        return false;
                    }
                } else return true;
            }

            if (cmd == "update" || cmd == "new") {
                Log.d(TAG, "new item sending to nightscout entries, KEY: " + key);
                Response<ResponseBody> responseBody = entriesEndpoints.sendEntry(entry).execute();
                if (!responseBody.isSuccessful()) {
                    Log.d(TAG, "no POST response from nightscout site");
                    return false;
                }
            }

        } else {
            Log.d(TAG, "no response from nightscout site!");
            return false;
        }

        return true;
    }

    private boolean processEntryX(String cmd, EntriesEndpoints.Entry entry, EntriesEndpoints entriesEndpoints) throws Exception {

        String key = entry.getKey600();
        Response<List<EntriesEndpoints.Entry>> response = entriesEndpoints.checkKey("2017", key).execute();

        if (response.isSuccessful()) {
            List<EntriesEndpoints.Entry> list = response.body();
            int size = list.size();
            if (size > 0) {
                Log.d(TAG, "found " + list.size() + " already in nightscout for KEY: " + key);

                if (cmd == "update" || cmd == "delete" || size > 1) {
                    Response<ResponseBody> responseBody = entriesEndpoints.deleteKey("2017", key).execute();
                    if (responseBody.isSuccessful()) {
                        Log.d(TAG, "deleted " + size + " with KEY: " + key);
                    } else {
                        Log.d(TAG, "no DELETE response from nightscout site");
                        return false;
                    }
                } else return true;
            }

            if (cmd == "update" || cmd == "new") {
                Log.d(TAG, "new item sending to nightscout entries, KEY: " + key);
                Response<ResponseBody> responseBody = entriesEndpoints.sendEntry(entry).execute();
                if (!responseBody.isSuccessful()) {
                    Log.d(TAG, "no POST response from nightscout site");
                    return false;
                }
            }

        } else {
            Log.d(TAG, "no response from nightscout site!");
            return false;
        }

        return true;
    }

    private boolean processTreatment(String cmd, TreatmentsEndpoints.Treatment treatment, TreatmentsEndpoints treatmentsEndpoints) throws Exception {

        String key = treatment.getKey600();
        Response<List<TreatmentsEndpoints.Treatment>> response = treatmentsEndpoints.checkKey("2017", key).execute();

        if (response.isSuccessful()) {
            List<TreatmentsEndpoints.Treatment> list = response.body();
            int size = list.size();
            if (size > 0) {
                Log.d(TAG, "found " + list.size() + " already in nightscout for KEY: " + key);

                while (size > 0 && (cmd == "update" || cmd == "delete" || size > 1)) {
                    Response<ResponseBody> responseBody = treatmentsEndpoints.deleteID(list.get(size - 1).get_id()).execute();
                    if (responseBody.isSuccessful()) {
                        Log.d(TAG, "deleted this item! KEY: " + key + " ID: " + list.get(size - 1).get_id());
                    } else {
                        Log.d(TAG, "no DELETE response from nightscout site");
                        return false;
                    }
                    size--;
                }

                if (size > 0) return true;
            }

            if (cmd == "update" || cmd == "new") {
                Log.d(TAG, "new item sending to nightscout treatments, KEY: " + key);
                Response<ResponseBody> responseBody = treatmentsEndpoints.sendTreatment(treatment).execute();
                if (!responseBody.isSuccessful()) {
                    Log.d(TAG, "no POST response from nightscout site");
                    return false;
                }
            }

        } else {
            Log.d(TAG, "no response from nightscout site!");
            return false;
        }

        return true;
    }

    private boolean processTreatmentX(String cmd, TreatmentsEndpoints.Treatment treatment, TreatmentsEndpoints treatmentsEndpoints) throws Exception {

        String key = treatment.getKey600();
        Response<List<TreatmentsEndpoints.Treatment>> response = treatmentsEndpoints.checkKey("2017", key).execute();

        if (response.isSuccessful()) {
            List<TreatmentsEndpoints.Treatment> list = response.body();
            int size = list.size();
            if (size > 0) {
                Log.d(TAG, "found " + list.size() + " already in nightscout for KEY: " + key);

                while (size > 0 && (cmd == "update" || cmd == "delete" || size > 1)) {
                    Response<ResponseBody> responseBody = treatmentsEndpoints.deleteID(list.get(size - 1).get_id()).execute();
                    if (responseBody.isSuccessful()) {
                        Log.d(TAG, "deleted this item! KEY: " + key + " ID: " + list.get(size - 1).get_id());
                    } else {
                        Log.d(TAG, "no DELETE response from nightscout site");
                        return false;
                    }
                    size--;
                }

                if (size > 0) return true;
            }

            if (cmd == "update" || cmd == "new") {
                Log.d(TAG, "new item sending to nightscout treatments, KEY: " + key);
                Response<ResponseBody> responseBody = treatmentsEndpoints.sendTreatment(treatment).execute();
                if (!responseBody.isSuccessful()) {
                    Log.d(TAG, "no POST response from nightscout site");
                    return false;
                }
            }

        } else {
            Log.d(TAG, "no response from nightscout site!");
            return false;
        }

        return true;
    }

    private boolean processProfile(String cmd, ProfileEndpoints.Profile profile, ProfileEndpoints profileEndpoints) throws Exception {

        String key = profile.getKey600();
        Response<List<ProfileEndpoints.Profile>> response = profileEndpoints.getProfiles().execute();

        if (response.isSuccessful()) {

            List<ProfileEndpoints.Profile> list = response.body();

            if (list.size() > 0) {
                Log.d(TAG, "found " + list.size() + " profiles sets in nightscout");
                String foundID = null;
                for (ProfileEndpoints.Profile item : list) {
                    if (item.getKey600() == key) {
                        Log.d(TAG, "found already in nightscout for KEY: " + key);
                        foundID = item.get_id();
                        break;
                    }
                }
                if (foundID != null) {
                    if (cmd == "update" || cmd == "delete") {
                        Response<ResponseBody> responseBody = profileEndpoints.deleteID(foundID).execute();
                        if (responseBody.isSuccessful()) {
                            Log.d(TAG, "deleted this item! KEY: " + key + " ID: " + foundID);
                        } else {
                            Log.d(TAG, "no DELETE response from nightscout site");
                            return false;
                        }
                    } else return true;
                }
            }

            if (cmd == "update" || cmd == "new") {
                Log.d(TAG, "new item sending to nightscout profile, KEY: " + key);
                Response<ResponseBody> responseBody = profileEndpoints.sendProfile(profile).execute();
                if (!responseBody.isSuccessful()) {
                    Log.d(TAG, "no POST response from nightscout site");
                    return false;
                }
            }

        } else {
            Log.d(TAG, "no response from nightscout site!");
            return false;
        }

        return true;
    }

    @NonNull
    private String formToken(String secret) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] bytes = secret.getBytes("UTF-8");
        digest.update(bytes, 0, bytes.length);
        bytes = digest.digest();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}

/*


        Response<List<ProfileEndpoints.Profile>> profileResponse = profileEndpoints.getProfiles().execute();
        if (profileResponse.isSuccessful()) {
            List<ProfileEndpoints.Profile> profileslist = profileResponse.body();
            Log.d(TAG, "!!! profile found " + profileslist.size());
            int setnum = 1;
            for (ProfileEndpoints.Profile each : profileslist) {
                Log.d(TAG, "!!! profile: " + setnum + each.get_id() + " StartDate: " + each.getStartDate() + " Created_at:" + each.getCreated_at() + " defaultProfile: " + each.getDefaultProfile()) ;
            }

            ProfileEndpoints.Profile profile = new ProfileEndpoints.Profile();
            profile.setCreated_at("2006-10-10T10:10:10.110Z");
            profile.setDefaultProfile("Sick Day"); // named BasalProfile
            profile.setStartDate("2006-10-10T10:10:10.110Z"); // active from date
            profile.setUnits("mmol"); // "mgdl" or "mmol"
            profile.setKey600("PROFILE01234567");

            ProfileEndpoints.Store store = new ProfileEndpoints.Store(); // <-- BasalProfile x8 as named on Pump
            profile.setStore(store);

            ProfileEndpoints.BasalProfile basalProfile = new ProfileEndpoints.BasalProfile(); // for each named profile
            store.setBasal1(basalProfile);
            store.setBasal2(basalProfile);
            store.setBasal3(basalProfile);
            store.setBasal4(basalProfile);
            store.setBasal5(basalProfile);
            store.setWorkday(basalProfile);
            store.setDayoff(basalProfile);
            store.setSickday(basalProfile);

            basalProfile.setStartDate("2011-10-10T10:10:10.110Z"); // active from date
            basalProfile.setTimezone("UTC"); // (Time Zone) - time zone local to the patient. Should be set.
            basalProfile.setUnits("mmol"); // (Profile Units) - blood glucose units used in the profile, either "mgdl" or "mmol"   ??? get from uploader or NS ???
            basalProfile.setCarbs_hr("35"); // (Carbs per Hour) - The number of carbs that are processed per hour          ??? set as 30/35 as an general average for now ???
            basalProfile.setDia("3:33"); // (Insulin duration) - value should be the duration of insulin action to use in calculating how much insulin is left active. Defaults to 3 hours.

            ProfileEndpoints.TimePeriod[] basal = new ProfileEndpoints.TimePeriod[6]; // array of these go into BasalProfile
            ProfileEndpoints.TimePeriod[] carbratio = new ProfileEndpoints.TimePeriod[1]; // array of these go into BasalProfile
            ProfileEndpoints.TimePeriod[] sens = new ProfileEndpoints.TimePeriod[1]; // array of these go into BasalProfile
            ProfileEndpoints.TimePeriod[] target_low = new ProfileEndpoints.TimePeriod[1]; // array of these go into BasalProfile
            ProfileEndpoints.TimePeriod[] target_high = new ProfileEndpoints.TimePeriod[1]; // array of these go into BasalProfile

            basal[0] = new ProfileEndpoints.TimePeriod();
            basal[1] = new ProfileEndpoints.TimePeriod();
            basal[2] = new ProfileEndpoints.TimePeriod();
            basal[3] = new ProfileEndpoints.TimePeriod();
            basal[4] = new ProfileEndpoints.TimePeriod();
            basal[5] = new ProfileEndpoints.TimePeriod();
            carbratio[0] = new ProfileEndpoints.TimePeriod();
            sens[0] = new ProfileEndpoints.TimePeriod();
            target_low[0] = new ProfileEndpoints.TimePeriod();
            target_high[0] = new ProfileEndpoints.TimePeriod();

            basalProfile.setBasal(basal); // The basal rate set on the pump.
            basalProfile.setCarbratio(carbratio); // (Carb Ratio) - grams per unit of insulin.
            basalProfile.setSens(sens); // (Insulin sensitivity) How much one unit of insulin will normally lower blood glucose.
            basalProfile.setTarget_low(target_low); // Lower target for correction boluses.
            basalProfile.setTarget_high(target_high); // Upper target for correction boluses.

            basal[0].setTime("00:00");
            basal[0].setValue("0.9");
            basal[1].setTime("05:00");
            basal[1].setValue("0.85");
            basal[2].setTime("10:30");
            basal[2].setValue("1.5");
            basal[3].setTime("13:00");
            basal[3].setValue("0.85");
            basal[4].setTime("17:00");
            basal[4].setValue("0.85");
            basal[5].setTime("20:30");
            basal[5].setValue("0.9");

            carbratio[0].setTime("00:00");
            carbratio[0].setValue("12");
            sens[0].setTime("00:00");
            sens[0].setValue("24");
            target_low[0].setTime("00:00");
            target_low[0].setValue("6");
            target_high[0].setTime("00:00");
            target_high[0].setValue("8");

            profiles.add(profile);

            /*
            Response<ResponseBody> responseBody = profileEndpoints.deleteID(profileslist.get(0).get_id()).execute();
            if (responseBody.isSuccessful()) {
                Log.d(TAG, "!!! deleted this item!" + " ID: " + profileslist.get(0).get_id());
            } else {
                Log.d(TAG, "!!! no delete response from nightscout site");
            }


            ProfileEndpoints.Profile profile = new ProfileEndpoints.Profile();
            profile = profileslist.get(0);
            profile.set_id(null);
            profile.setDefaultProfile("Love 1");

            /*
            ProfileEndpoints.Profile first = profileslist.get(0);
            if (first.getDefaultProfile() == "Test 2") first.setDefaultProfile("Love 1");
            if (first.getDefaultProfile() == "Love 1") first.setDefaultProfile("Nice 1");
            if (first.getDefaultProfile() == "Nice 1") first.setDefaultProfile("Well 1");
            if (first.getDefaultProfile() == "Well 1") first.setDefaultProfile("Test 2");

//            profiles.add(profile);
        }



 */


/*

    private void processEntry(String cmd, EntriesEndpoints.Entry entry, EntriesEndpoints entriesEndpoints, List<EntriesEndpoints.Entry> entries) throws Exception {

        Response<List<EntriesEndpoints.Entry>> response = entriesEndpoints.checkKey(entry.getKey600()).execute();

        if (response.isSuccessful()) {
            List<EntriesEndpoints.Entry> list = response.body();
            if (list.size() > 0) {
                Log.d(TAG, "found " + list.size() + " already in nightscout for KEY: " + entry.getKey600());

                if (cmd == "update" || cmd == "delete") {
                    Response<ResponseBody> responseBody = entriesEndpoints.deleteID(list.get(0).get_id()).execute();
                    if (responseBody.isSuccessful()) {
                        Log.d(TAG, "deleted this item! KEY: " + list.get(0).getKey600() + " ID: " + list.get(0).get_id());
                        if (cmd == "update") {
                            Log.d(TAG, "new updated item sending to nightscout entries, KEY: " + entry.getKey600());
                            entries.add(entry);
                        }
                    } else {
                        Log.d(TAG, "no delete response from nightscout site");
                    }
                }

            } else if (cmd == "new" || cmd == "update") {
                Log.d(TAG, "new item sending to nightscout entries, KEY: " + entry.getKey600());
                entries.add(entry);
            } else {
                Log.d(TAG, "bad command! " + cmd);
            }
        } else {
            Log.d(TAG, "no response from nightscout site!");
        }
    }

    private void processTreatment(String cmd, TreatmentsEndpoints.Treatment treatment, TreatmentsEndpoints treatmentsEndpoints, List<TreatmentsEndpoints.Treatment> treatments) throws Exception {

        Response<List<TreatmentsEndpoints.Treatment>> response = treatmentsEndpoints.checkKey(treatment.getKey600()).execute();

        if (response.isSuccessful()) {
            List<TreatmentsEndpoints.Treatment> list = response.body();
            if (list.size() > 0) {
                Log.d(TAG, "found " + list.size() + " already in nightscout for KEY: " + treatment.getKey600());

                if (cmd == "update" || cmd == "delete") {
                    Response<ResponseBody> responseBody = treatmentsEndpoints.deleteID(list.get(0).get_id()).execute();
                    if (responseBody.isSuccessful()) {
                        Log.d(TAG, "deleted this item! KEY: " + list.get(0).getKey600() + " ID: " + list.get(0).get_id());
                        if (cmd == "update") {
                            Log.d(TAG, "new updated item sending to nightscout entries, KEY: " + treatment.getKey600());
                            treatments.add(treatment);
                        }
                    } else {
                        Log.d(TAG, "no delete response from nightscout site");
                    }
                }

            } else if (cmd == "new" || cmd == "update") {
                Log.d(TAG, "new item sending to nightscout entries, KEY: " + treatment.getKey600());
                treatments.add(treatment);
            } else {
                Log.d(TAG, "bad command! " + cmd);
            }
        } else {
            Log.d(TAG, "no response from nightscout site!");
        }
    }




                if (type == "entry") {
                    EntriesEndpoints.Entry entry = (EntriesEndpoints.Entry) iterator.next();
                    Response<List<EntriesEndpoints.Entry>> entriesResponse = entriesEndpoints.checkKey(entry.getKey600()).execute();

                    if (entriesResponse.isSuccessful()) {
                        List<EntriesEndpoints.Entry> entriesList = entriesResponse.body();
                        if (entriesList.size() > 0) {
                            Log.d(TAG, "found " + entriesList.size() + " already in nightscout for KEY: " + entry.getKey600());

                            if (cmd == "update" || cmd == "delete") {
                                Response<ResponseBody> responseBody = entriesEndpoints.deleteID(entriesList.get(0).get_id()).execute();
                                if (responseBody.isSuccessful()) {
                                    Log.d(TAG, "deleted this item! KEY: " + entriesList.get(0).getKey600() + " ID: " + entriesList.get(0).get_id());
                                    if (cmd == "update") {
                                        Log.d(TAG, "new updated item sending to nightscout entries, KEY: " + entry.getKey600());
                                        entries.add(entry);
                                    }
                                } else {
                                    Log.d(TAG, "no delete response from nightscout site");
                                }
                            }

                        } else if (cmd == "new" || cmd == "update") {
                            Log.d(TAG, "new item sending to nightscout entries, KEY: " + entry.getKey600());
                            entries.add(entry);
                        } else {
                            Log.d(TAG, "bad command! " + type + " " + cmd);
                        }
                    } else {
                        Log.d(TAG, "no response from nightscout site!");
                    }

                } else if (type == "treatment") {
                    TreatmentsEndpoints.Treatment treatment = (TreatmentsEndpoints.Treatment) iterator.next();
                    Response<List<TreatmentsEndpoints.Treatment>> treatmentResponse = treatmentsEndpoints.checkKey(treatment.getKey600()).execute();

                    if (treatmentResponse.isSuccessful()) {
                        List<TreatmentsEndpoints.Treatment> treatmentslist = treatmentResponse.body();
                        if (treatmentslist.size() > 0) {
                            Log.d(TAG, "found " + treatmentslist.size() + " already in nightscout for KEY: " + treatment.getKey600());

                            if (cmd == "update" || cmd == "delete") {
                                Response<ResponseBody> responseBody = treatmentsEndpoints.deleteID(treatmentslist.get(0).get_id()).execute();
                                if (responseBody.isSuccessful()) {
                                    Log.d(TAG, "deleted this item! KEY: " + treatmentslist.get(0).getKey600() + " ID: " + treatmentslist.get(0).get_id());
                                    if (cmd == "update") {
                                        Log.d(TAG, "new updated item sending to nightscout treatments, KEY: " + treatment.getKey600());
                                        treatments.add(treatment);
                                    }
                                } else {
                                    Log.d(TAG, "no delete response from nightscout site");
                                }
                            }

                        } else if (cmd == "new" || cmd == "update") {
                            Log.d(TAG, "new item sending to nightscout treatments, KEY: " + treatment.getKey600());
                            treatments.add(treatment);
                        } else {
                            Log.d(TAG, "bad command! " + type + " " + cmd);
                        }
                    } else {
                        Log.d(TAG, "no response from nightscout site!");
                    }

                } else if (type == "profile") {
                    ProfileEndpoints.Profile profile = (ProfileEndpoints.Profile) iterator.next();
                    Response<List<ProfileEndpoints.Profile>> profileResponse = profileEndpoints.checkKey(profile.getKey600()).execute();

                    if (profileResponse.isSuccessful()) {
                        List<ProfileEndpoints.Profile> profileslist = profileResponse.body();
                        if (profileslist.size() > 0) {
                            Log.d(TAG, "found " + profileslist.size() + " already in nightscout for KEY: " + profile.getKey600());

                            if (cmd == "update" || cmd == "delete") {
                                Response<ResponseBody> responseBody = profileEndpoints.deleteID(profileslist.get(0).get_id()).execute();
                                if (responseBody.isSuccessful()) {
                                    Log.d(TAG, "deleted this item! KEY: " + profileslist.get(0).getKey600() + " ID: " + profileslist.get(0).get_id());
                                    if (cmd == "update") {
                                        Log.d(TAG, "new updated item sending to nightscout profiles, KEY: " + profile.getKey600());
                                        profiles.add(profile);
                                    }
                                } else {
                                    Log.d(TAG, "no delete response from nightscout site");
                                }
                            }

                        } else if (cmd == "new" || cmd == "update") {
                            Log.d(TAG, "new item sending to nightscout profiles, KEY: " + profile.getKey600());
                            profiles.add(profile);
                        } else {
                            Log.d(TAG, "bad command! " + type + " " + cmd);
                        }
                    } else {
                        Log.d(TAG, "no response from nightscout site!");
                    }
                }
            }

 */

    /*
        Realm historyRealm = Realm.getInstance(UploaderApplication.getHistoryConfiguration());
           if (success) {
                historyRealm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        record.setUploadACK(true);
                        record.setUploadREQ(false);
                    }
                });
            }

//        historyRealm.close();

    long entryNewest = 0;
        long entryOldest = 0;
                    EntriesEndpoints.Entry entry = (EntriesEndpoints.Entry) iterator.next();
                    long time = entry.getDate();
                    if (time > entryNewest || entryNewest == 0) entryNewest = time;
                    if (time < entryOldest || entryOldest == 0) entryOldest = time;
*/

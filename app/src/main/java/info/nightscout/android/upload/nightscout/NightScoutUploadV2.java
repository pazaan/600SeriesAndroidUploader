package info.nightscout.android.upload.nightscout;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import info.nightscout.android.model.medtronicNg.PumpHistory;
import info.nightscout.api.EntriesEndpoints;
import info.nightscout.api.ProfileEndpoints;
import info.nightscout.api.TreatmentsEndpoints;
import info.nightscout.api.UploadApi;
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

public class NightScoutUploadV2 {
    private static final String TAG = NightScoutUploadV2.class.getSimpleName();

    private EntriesEndpoints entriesEndpoints;
    private TreatmentsEndpoints treatmentsEndpoints;
    private ProfileEndpoints profileEndpoints;

    NightScoutUploadV2() {}

    boolean doRESTUpload(String url,
                         String secret,
                         List<PumpHistory> records) throws Exception {
        return isUploaded(records, url, secret);
    }

    private boolean isUploaded(List<PumpHistory> records,
                               String baseURL,
                               String secret) throws Exception {

        UploadApi uploadApi = new UploadApi(baseURL, formToken(secret));
        entriesEndpoints = uploadApi.getEntriesEndpoints();
        treatmentsEndpoints = uploadApi.getTreatmentsEndpoints();
        profileEndpoints = uploadApi.getProfileEndpoints();

        return uploadEvents(records);
    }

    private boolean uploadEvents(List<PumpHistory> records) throws Exception {

        List<EntriesEndpoints.Entry> entries = new ArrayList<>();
        List<TreatmentsEndpoints.Treatment> treatments = new ArrayList<>();

        boolean success = true;

        for (PumpHistory record : records) {

            List list = record.Nightscout();
            Iterator iterator = list.iterator();
            while (success && iterator.hasNext()) {
                String type = (String) iterator.next();
                String cmd = (String) iterator.next();
                if (type == "entry") {
                    success &= processEntry(cmd, (EntriesEndpoints.Entry) iterator.next(), entries);
                } else if (type == "treatment") {
                    success &= processTreatment(cmd, (TreatmentsEndpoints.Treatment) iterator.next(), treatments);
                } else if (type == "profile") {
                    success &= processProfile(cmd, (ProfileEndpoints.Profile) iterator.next());
                }
            }

            if (!success) break;
        }

        // bulk uploading for entries and treatments

        if (success && entries.size() > 0) {
            Response<ResponseBody> result = entriesEndpoints.sendEntries(entries).execute();
            success &= result.isSuccessful();
        }
        if (success && treatments.size() > 0) {
            Response<ResponseBody> result = treatmentsEndpoints.sendTreatments(treatments).execute();
            success &= result.isSuccessful();
        }

        return success;
    }

    private boolean processEntry(String cmd, EntriesEndpoints.Entry entry, List<EntriesEndpoints.Entry> entries) throws Exception {

        String key = entry.getKey600();
        Response<List<EntriesEndpoints.Entry>> response = entriesEndpoints.checkKey("2017", key).execute();

        if (response.isSuccessful()) {
            List<EntriesEndpoints.Entry> list = response.body();
            int count = list.size();
            if (count > 0) {
                Log.d(TAG, "found " + list.size() + " already in nightscout for KEY: " + key);

                if (cmd == "update" || cmd == "delete" || count > 1) {
                    Response<ResponseBody> responseBody = entriesEndpoints.deleteKey("2017", key).execute();
                    if (responseBody.isSuccessful()) {
                        Log.d(TAG, "deleted " + count + " with KEY: " + key);
                    } else {
                        Log.d(TAG, "no DELETE response from nightscout site");
                        return false;
                    }
                } else return true;
            }

            if (cmd == "update" || cmd == "new") {
                Log.d(TAG, "queued item for nightscout entries bulk upload, KEY: " + key);
                entries.add(entry);
            }

        } else {
            Log.d(TAG, "no response from nightscout site!");
            return false;
        }

        return true;
    }

    private boolean processTreatment(String cmd, TreatmentsEndpoints.Treatment treatment, List<TreatmentsEndpoints.Treatment> treatments) throws Exception {

        String key = treatment.getKey600();
        Response<List<TreatmentsEndpoints.Treatment>> response = treatmentsEndpoints.checkKey("2017", key).execute();

        if (response.isSuccessful()) {
            List<TreatmentsEndpoints.Treatment> list = response.body();
            int count = list.size();
            if (count > 0) {
                Log.d(TAG, "found " + list.size() + " already in nightscout for KEY: " + key);

                while (count > 0 && (cmd == "update" || cmd == "delete" || count > 1)) {
                    Response<ResponseBody> responseBody = treatmentsEndpoints.deleteID(list.get(count - 1).get_id()).execute();
                    if (responseBody.isSuccessful()) {
                        Log.d(TAG, "deleted this item! KEY: " + key + " ID: " + list.get(count - 1).get_id());
                    } else {
                        Log.d(TAG, "no DELETE response from nightscout site");
                        return false;
                    }
                    count--;
                }

                if (count > 0) return true;
            }

            if (cmd == "update" || cmd == "new") {
                Log.d(TAG, "queued item for nightscout treatments bulk upload, KEY: " + key);
                treatments.add(treatment);
            }

        } else {
            Log.d(TAG, "no response from nightscout site!");
            return false;
        }

        return true;
    }

    private boolean processProfile(String cmd, ProfileEndpoints.Profile profile) throws Exception {

        String key = profile.getKey600();
        Response<List<ProfileEndpoints.Profile>> response = profileEndpoints.getProfiles().execute();

        if (response.isSuccessful()) {

            List<ProfileEndpoints.Profile> list = response.body();

            if (list.size() > 0) {
                Log.d(TAG, "found " + list.size() + " profiles sets in nightscout");

                String foundID = null;
                String foundKey = null;
                int count = 0;

                for (ProfileEndpoints.Profile item : list) {
                    foundKey = item.getKey600();
                    if (foundKey != null && foundKey.equals(key)) count++;
                }

                if (count > 0) {
                    Log.d(TAG, "found " + count + " already in nightscout for KEY: " + key);

                    if (cmd == "update" || cmd == "delete" || count > 1) {
                        for (ProfileEndpoints.Profile item : list) {
                            foundKey = item.getKey600();
                            if (foundKey != null && foundKey.equals(key)) {
                                foundID = item.get_id();
                                Response<ResponseBody> responseBody = profileEndpoints.deleteID(foundID).execute();
                                if (responseBody.isSuccessful()) {
                                    Log.d(TAG, "deleted this item! KEY: " + key + " ID: " + foundID);
                                } else {
                                    Log.d(TAG, "no DELETE response from nightscout site");
                                    return false;
                                }
                                if (--count == 1) break;
                            }
                        }
                    }
                }

                if (count > 0) return true;
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

package info.nightscout.android.upload.nightscout;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import info.nightscout.android.model.medtronicNg.PumpHistory;
import info.nightscout.api.EntriesEndpoints;
import info.nightscout.api.TreatmentsEndpoints;
import info.nightscout.api.UploadApi;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Created by John on 1.11.17.
 */

public class NightScoutUploadV2 {
    private static final String TAG = NightScoutUploadV2.class.getSimpleName();

    private static final SimpleDateFormat ISO8601_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());
    private static final SimpleDateFormat NOTE_DATE_FORMAT = new SimpleDateFormat("E h:mm a", Locale.getDefault());

    NightScoutUploadV2() {

    }

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
                records);

        return eventsUploaded;
    }

    private boolean uploadEvents(
            EntriesEndpoints entriesEndpoints,
            TreatmentsEndpoints treatmentsEndpoints,
            List<PumpHistory> records
    ) throws Exception {

        List<EntriesEndpoints.Entry> entries = new ArrayList<>();
        List<TreatmentsEndpoints.Treatment> treatments = new ArrayList<>();

        for (PumpHistory record : records) {

            List list = record.Nightscout();
            Iterator iterator = list.iterator();
            while(iterator.hasNext()) {
                String type = (String) iterator.next();
                String cmd = (String) iterator.next();

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
                }
            }

        }

        boolean uploaded = true;
        if (entries.size() > 0) {
            Response<ResponseBody> result = entriesEndpoints.sendEntries(entries).execute();
            uploaded = result.isSuccessful();
        }
        if (treatments.size() > 0) {
            Response<ResponseBody> result = treatmentsEndpoints.sendTreatments(treatments).execute();
            uploaded = uploaded && result.isSuccessful();
        }

        return uploaded;
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

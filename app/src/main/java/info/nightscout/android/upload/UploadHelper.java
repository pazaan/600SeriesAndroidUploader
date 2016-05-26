package info.nightscout.android.upload;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.qos.logback.classic.Logger;
import info.nightscout.android.R;
import info.nightscout.android.medtronic.Medtronic640gActivity;
import info.nightscout.android.medtronic.MedtronicConstants;
import info.nightscout.android.upload.MedtronicNG.CGMRecord;

public class UploadHelper extends AsyncTask<Record, Integer, Long> {

	private Logger log = (Logger) LoggerFactory.getLogger(UploadHelper.class.getName());
    private static final String TAG = "DexcomUploadHelper";
    private SharedPreferences settings = null;// common application preferences
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss aa", Locale.getDefault());
    private static final int SOCKET_TIMEOUT = 60 * 1000;
    private static final int CONNECTION_TIMEOUT = 30 * 1000;

    Context context;
	private List<JSONObject> recordsNotUploadedList = new ArrayList<JSONObject>();
    private List<JSONObject> recordsNotUploadedListJson = new ArrayList<JSONObject>();

    public static final Object isModifyingRecordsLock = new Object();

    public UploadHelper(Context context) {
        this(context, Medtronic640gActivity.CNL_24);
    }
    
    public UploadHelper(Context context, int cgmSelected) {
        this.context = context;
		settings = context.getSharedPreferences(MedtronicConstants.PREFS_NAME, 0);
        synchronized (isModifyingRecordsLock) {
	        try {
	        	long currentTime = System.currentTimeMillis();
	    		long diff = currentTime - settings.getLong("lastDestroy", 0);
	    		if (diff != currentTime && diff > (6*MedtronicConstants.TIME_60_MIN_IN_MS)) {
	    			log.debug("Remove older records");
	    			SharedPreferences.Editor editor = settings.edit();
	    			if (settings.contains("recordsNotUploaded"))
	    				editor.remove("recordsNotUploaded");
	    			if (settings.contains("recordsNotUploadedJson"))
	    				editor.remove("recordsNotUploadedJson");
	            	editor.apply();
	    		}
	        	if (settings.contains("recordsNotUploaded")){
	        		JSONArray recordsNotUploaded = new JSONArray(settings.getString("recordsNotUploaded","[]"));
	        		for (int i = 0; i < recordsNotUploaded.length(); i++){
	        			recordsNotUploadedList.add(recordsNotUploaded.getJSONObject(i));
	        		}
	        		log.debug("retrieve older json records -->" +recordsNotUploaded.length());
	            	SharedPreferences.Editor editor = settings.edit();
	            	editor.remove("recordsNotUploaded");
	            	editor.apply();
	            }	
	        	if (settings.contains("recordsNotUploadedJson")){
	        		JSONArray recordsNotUploadedJson = new JSONArray(settings.getString("recordsNotUploadedJson","[]"));
	        		for (int i = 0; i < recordsNotUploadedJson.length(); i++){
	        			recordsNotUploadedListJson.add(recordsNotUploadedJson.getJSONObject(i));
	        		}
	        		log.debug("retrieve older json records -->" +recordsNotUploadedJson.length());
	            	SharedPreferences.Editor editor = settings.edit();
	            	editor.remove("recordsNotUploadedJson");
	            	editor.apply();
	            }	
			} catch (Exception e) {
				log.debug("ERROR Retrieving older list, I have lost them");
				recordsNotUploadedList = new ArrayList<>();
				recordsNotUploadedListJson = new ArrayList<>();
				SharedPreferences.Editor editor = settings.edit();
				if (settings.contains("recordsNotUploaded"))
					editor.remove("recordsNotUploaded");
				if (settings.contains("recordsNotUploadedJson"))
					editor.remove("recordsNotUploadedJson");
	        	editor.apply();
			}
        }
    }

	/**
     * doInBackground
     */
    protected Long doInBackground(Record... records) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
        Boolean enableRESTUpload = prefs.getBoolean("EnableRESTUpload", false);
        try{
        	if (enableRESTUpload) {
                long start = System.currentTimeMillis();
                Log.i(TAG, String.format("Starting upload of %s record using a REST API", records.length));
                log.info(String.format("Starting upload of %s record using a REST API", records.length));
                doRESTUpload(prefs, records);
                Log.i(TAG, String.format("Finished upload of %s record using a REST API in %s ms", records.length, System.currentTimeMillis() - start));
                log.info(String.format("Finished upload of %s record using a REST API in %s ms", records.length, System.currentTimeMillis() - start));
            }
        }catch(Exception e){
        	log.error("ERROR uploading data!!!!!", e);
        }
        
        
        return 1L;
    }

    protected void onPostExecute(Long result) {
        super.onPostExecute(result);
        Log.i(TAG, "Post execute, Result: " + result + ", Status: FINISHED");
        log.info("Post execute, Result: " + result + ", Status: FINISHED");

    }

    private void doRESTUpload(SharedPreferences prefs, Record... records) {
        String apiScheme = "https://";
		String apiUrl = "";
		String apiSecret = prefs.getString(context.getString(R.string.preference_api_secret), "YOURAPISECRET");

		// Add the extra match for "KEY@" to support the previous single field
		Pattern p = Pattern.compile("(.*\\/\\/)?(.*@)?([^\\/]*)(.*)");
		Matcher m = p.matcher(prefs.getString(context.getString(R.string.preference_nightscout_url), ""));

		if( m.find() ) {
			apiUrl = m.group(3);

			// Only override apiSecret from URL (the "old" way), if the API secret preference is empty
			if( apiSecret.equals("YOURAPISECRET") || apiSecret.equals("") ) {
				apiSecret = ( m.group(2) == null ) ? "" : m.group(2).replace("@", "");
			}

			// Override the URI scheme if it's been provided in the preference)
			if( m.group(1) != null && !m.group(1).equals("") ) {
				apiScheme = m.group(1);
			}
		}

		// Update the preferences to match what we expect. Only really used from converting from the
		// old format to the new format. Aren't we nice for managing backward compatibility?
		prefs.edit().putString(context.getString(R.string.preference_api_secret), apiSecret ).apply();
		prefs.edit().putString(context.getString(R.string.preference_nightscout_url), String.format("%s%s", apiScheme, apiUrl ) ).apply();

		String uploadUrl = String.format("%s%s@%s/api/v1/", apiScheme, apiSecret, apiUrl );

		try {
			doRESTUploadTo(uploadUrl, records);
		} catch (Exception e) {
			Log.e(TAG, "Unable to do REST API Upload to: " + uploadUrl, e);
			log.error("Unable to do REST API Upload to: " + uploadUrl, e);
		}
    }

    private void doRESTUploadTo(String baseURI, Record[] records) {
		try {
            int apiVersion = 0;
            if (baseURI.endsWith("/v1/")) apiVersion = 1;

            String baseURL;
            String secret = null;
            String[] uriParts = baseURI.split("@");

            if (uriParts.length == 1 && apiVersion == 0) {
                baseURL = uriParts[0];
            } else if (uriParts.length == 1) {
            	if (recordsNotUploadedListJson.size() > 0){
                 	JSONArray jsonArray = new JSONArray(recordsNotUploadedListJson);
                 	SharedPreferences.Editor editor = settings.edit();
                 	editor.putString("recordsNotUploaded", jsonArray.toString());
                 	editor.apply();
                 }
                throw new Exception("Starting with API v1, a pass phase is required");
            } else if (uriParts.length == 2 && apiVersion > 0) {
                secret = uriParts[0];
                baseURL = uriParts[1];

                // new format URL!

                if (secret.contains("http")) {
					if (secret.contains("https")) {
                        baseURL = "https://" + baseURL;
                    } else {
                        baseURL = "http://" + baseURL;
                    }
                    String[] uriParts2 = secret.split("//");
                    secret = uriParts2[1];
                }


            } else {
            	if (recordsNotUploadedListJson.size() > 0){
                 	JSONArray jsonArray = new JSONArray(recordsNotUploadedListJson);
                 	SharedPreferences.Editor editor = settings.edit();
                 	editor.putString("recordsNotUploadedJson", jsonArray.toString());
                 	editor.apply();
                 }
                throw new Exception(String.format("Unexpected baseURI: %s, uriParts.length: %s, apiVersion: %s", baseURI, uriParts.length, apiVersion));
            }

            

            HttpParams params = new BasicHttpParams();
            HttpConnectionParams.setSoTimeout(params, SOCKET_TIMEOUT);
            HttpConnectionParams.setConnectionTimeout(params, CONNECTION_TIMEOUT);

            DefaultHttpClient httpclient = new DefaultHttpClient(params);

            postDeviceStatus(baseURL,httpclient);

            if (recordsNotUploadedListJson.size() > 0){
            	List<JSONObject> auxList = new ArrayList<JSONObject>(recordsNotUploadedListJson);
            	recordsNotUploadedListJson.clear();
        		for (int i = 0; i < auxList.size(); i++){
        			JSONObject json = auxList.get(i);
        			String postURL = baseURL; 
                	postURL += "entries";
                    Log.i(TAG, "postURL: " + postURL);

                    HttpPost post = new HttpPost(postURL);

                    if (apiVersion > 0) {
                        if (secret == null || secret.isEmpty()) {
                        	 if (auxList.size() > 0){
                             	JSONArray jsonArray = new JSONArray(auxList);
                             	SharedPreferences.Editor editor = settings.edit();
                             	editor.putString("recordsNotUploaded", jsonArray.toString());
                             	editor.apply();
                             }
                            throw new Exception("Starting with API v1, a pass phase is required");
                        } else {
                            MessageDigest digest = MessageDigest.getInstance("SHA-1");
                            byte[] bytes = secret.getBytes("UTF-8");
                            digest.update(bytes, 0, bytes.length);
                            bytes = digest.digest();
                            StringBuilder sb = new StringBuilder(bytes.length * 2);
                            for (byte b: bytes) {
                                sb.append(String.format("%02x", b & 0xff));
                            }
                            String token = sb.toString();
                            post.setHeader("api-secret", token);
                        }
                    }

                    String jsonString = json.toString();

                    Log.i(TAG, "Upload JSON: " + jsonString);
                    log.debug("JSON to Upload "+ jsonString);

                    try {
                        StringEntity se = new StringEntity(jsonString);
                        post.setEntity(se);
                        post.setHeader("Accept", "application/json");
                        post.setHeader("Content-type", "application/json");

    					ResponseHandler responseHandler = new BasicResponseHandler();
                        httpclient.execute(post, responseHandler);
                    } catch (Exception e) {
                        Log.w(TAG, "Unable to post data to: '" + post.getURI().toString() + "'", e);
                        log.warn("Unable to post data to: '" + post.getURI().toString() + "'", e);
                    }
        		}
        	}
            
            for (Record record : records) {
            	String postURL = baseURL; 
            	if (record instanceof GlucometerRecord){
					postURL +=  "entries";
            	}else{
					postURL += "entries";
            	}
                Log.i(TAG, "postURL: " + postURL);
                log.info( "postURL: " + postURL);

                HttpPost post = new HttpPost(postURL);

                if (apiVersion > 0) {
                    if (secret == null || secret.isEmpty()) {
                    	 if (recordsNotUploadedListJson.size() > 0){
                          	JSONArray jsonArray = new JSONArray(recordsNotUploadedListJson);
                          	SharedPreferences.Editor editor = settings.edit();
                          	editor.putString("recordsNotUploadedJson", jsonArray.toString());
                          	editor.apply();
                          }
                        throw new Exception("Starting with API v1, a pass phase is required");
                    } else {
                        MessageDigest digest = MessageDigest.getInstance("SHA-1");
                        byte[] bytes = secret.getBytes("UTF-8");
                        digest.update(bytes, 0, bytes.length);
                        bytes = digest.digest();
                        StringBuilder sb = new StringBuilder(bytes.length * 2);
                        for (byte b: bytes) {
                            sb.append(String.format("%02x", b & 0xff));
                        }
                        String token = sb.toString();
                        post.setHeader("api-secret", token);
                    }
                }

                JSONObject json = new JSONObject();

                try {
					populateV1APIEntry(json, record);
                } catch (Exception e) {
                    Log.w(TAG, "Unable to populate entry, apiVersion: " + apiVersion, e);
                    log.warn("Unable to populate entry, apiVersion: " + apiVersion, e);
                    continue;
                }

                String jsonString = json.toString();

                Log.i(TAG, "Upload JSON: " + jsonString);
                log.info("Upload JSON: " + jsonString);

                try {
                    StringEntity se = new StringEntity(jsonString);
                    post.setEntity(se);
                    post.setHeader("Accept", "application/json");
                    post.setHeader("Content-type", "application/json");

					ResponseHandler responseHandler = new BasicResponseHandler();
                    httpclient.execute(post, responseHandler);
                } catch (Exception e) {
					//Only EGV records are important enough.
					if (recordsNotUploadedListJson.size() > 49){
                        recordsNotUploadedListJson.remove(0);
                        recordsNotUploadedListJson.add(49,json);
                    }else{
                        recordsNotUploadedListJson.add(json);
                    }

					Log.w(TAG, "Unable to post data to: '" + post.getURI().toString() + "'", e);
                    log.warn( "Unable to post data to: '" + post.getURI().toString() + "'", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to post data", e);
            log.error("Unable to post data", e);
        }
        if (recordsNotUploadedListJson.size() > 0){
        	synchronized (isModifyingRecordsLock) {
    	        try {
	        		JSONArray recordsNotUploadedJson = new JSONArray(settings.getString("recordsNotUploadedJson","[]"));
	        		if (recordsNotUploadedJson.length() > 0 && recordsNotUploadedJson.length() < recordsNotUploadedListJson.size()){
    	        		for (int i = 0; i < recordsNotUploadedJson.length(); i++){
    	        			if (recordsNotUploadedListJson.size() > 49){
    	        				recordsNotUploadedListJson.remove(0);
    	        				recordsNotUploadedListJson.add(49, recordsNotUploadedJson.getJSONObject(i));
							}else{
								recordsNotUploadedListJson.add(recordsNotUploadedJson.getJSONObject(i));
							}
    	        		}
	        		}else{
	        			for (int i = 0; i < recordsNotUploadedListJson.size(); i++){
							recordsNotUploadedJson.put(recordsNotUploadedListJson.get(i));
    	        		}
	        			int start = 0;
	        			if (recordsNotUploadedJson.length() > 50){
	        				start = recordsNotUploadedJson.length() - 51;
	        			}
	        			recordsNotUploadedListJson.clear();
	        			for (int i = start; i < recordsNotUploadedJson.length(); i++){
							recordsNotUploadedListJson.add(recordsNotUploadedJson.getJSONObject(i));
    	        		}
	        		}
	        		log.debug("retrieve older json records -->" +recordsNotUploadedJson.length());
	            	SharedPreferences.Editor editor = settings.edit();
	            	editor.remove("recordsNotUploadedJson");
	            	editor.apply();
    			} catch (Exception e) {
    				log.debug("ERROR RETRIEVING OLDER LISTs, I HAVE LOST THEM");	
    				SharedPreferences.Editor editor = settings.edit();
    				if (settings.contains("recordsNotUploadedJson"))
    					editor.remove("recordsNotUploadedJson");
    	        	editor.apply();
    			}
    	        JSONArray jsonArray = new JSONArray(recordsNotUploadedListJson);
            	SharedPreferences.Editor editor = settings.edit();
            	editor.putString("recordsNotUploadedJson", jsonArray.toString());
            	editor.apply();
            }
        }
    }

	private void postDeviceStatus(String baseURL, DefaultHttpClient httpclient) throws Exception {
        String devicestatusURL = baseURL + "devicestatus";
        Log.i(TAG, "devicestatusURL: " + devicestatusURL);
        log.info("devicestatusURL: " + devicestatusURL);

        JSONObject json = new JSONObject();
        json.put("uploaderBattery", Medtronic640gActivity.batLevel);
		json.put("device", Medtronic640gActivity.pumpStatusRecord.getDeviceName() );

		JSONObject pumpInfo = new JSONObject();
		pumpInfo.put( "clock", Medtronic640gActivity.pumpStatusRecord.pumpDate );
		pumpInfo.put( "reservoir", Medtronic640gActivity.pumpStatusRecord.reservoirAmount);

		JSONObject iob = new JSONObject();
		iob.put( "timestamp", Medtronic640gActivity.pumpStatusRecord.pumpDate );
		iob.put( "bolusiob", Medtronic640gActivity.pumpStatusRecord.activeInsulin );

		JSONObject battery = new JSONObject();
		battery.put( "percent", Medtronic640gActivity.pumpStatusRecord.batteryPercentage );

		pumpInfo.put( "iob", iob );
		pumpInfo.put( "battery", battery );
		json.put( "pump", pumpInfo );
        String jsonString = json.toString();
		Log.i(TAG, "Device Status JSON: " + jsonString);
		log.debug("Device Status JSON: "+ jsonString);

        HttpPost post = new HttpPost(devicestatusURL);
        StringEntity se = new StringEntity(jsonString);
        post.setEntity(se);
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-type", "application/json");

        ResponseHandler responseHandler = new BasicResponseHandler();
        httpclient.execute(post, responseHandler);
    }
    
    private void populateV1APIEntry(JSONObject json, Record oRecord) throws Exception {
		if (oRecord instanceof CGMRecord) {
			json.put("date", ((CGMRecord) oRecord).sgvDate.getTime());
			json.put("dateString",  oRecord.displayTime);
		} else {
			Date date = DATE_FORMAT.parse(oRecord.displayTime);
			json.put("date", date.getTime());
		}

    	if (oRecord instanceof CGMRecord){
				CGMRecord pumpRecord = (CGMRecord) oRecord;
				json.put("sgv", pumpRecord.sgv);
				json.put("direction", pumpRecord.direction);
				json.put("device", pumpRecord.getDeviceName());
				json.put("type", "sgv");
    	}
    }

	private static String convertStreamToString(InputStream is) {

	    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
	    StringBuilder sb = new StringBuilder();

	    String line = null;
	    try {
	        while ((line = reader.readLine()) != null) {
	            sb.append(line).append("\n");
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
	    } finally {
	        try {
	            is.close();
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	    }
	    return sb.toString();
	}

}

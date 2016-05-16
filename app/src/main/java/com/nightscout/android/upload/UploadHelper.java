package com.nightscout.android.upload;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientOptions.Builder;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.nightscout.android.dexcom.DexcomG4Activity;
import com.nightscout.android.dexcom.EGVRecord;
import com.nightscout.android.medtronic.MedtronicConstants;
import com.nightscout.android.medtronic.MedtronicReader;
import com.nightscout.android.upload.MedtronicNG.CGMRecord;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import ch.qos.logback.classic.Logger;

import static com.mongodb.client.model.Filters.eq;

public class UploadHelper extends AsyncTask<Record, Integer, Long> {
	
	private Logger log = (Logger) LoggerFactory.getLogger(MedtronicReader.class.getName());
    private static final String TAG = "DexcomUploadHelper";
    private SharedPreferences settings = null;// common application preferences
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss aa", Locale.getDefault());
    private static final int SOCKET_TIMEOUT = 60 * 1000;
    private static final int CONNECTION_TIMEOUT = 30 * 1000;

    Context context;
    private int cgmSelected = DexcomG4Activity.DEXCOMG4;
    private ArrayList<Messenger> mClients;
    private List<JSONObject> recordsNotUploadedList = new ArrayList<JSONObject>();
    private List<JSONObject> recordsNotUploadedListJson = new ArrayList<JSONObject>();
    public String dbURI = null;
    public String collectionName = null;
    public String dsCollectionName = null;
    public String gdCollectionName = null;
    public String devicesCollectionName = "devices";
    public MongoDatabase db = null;
    public MongoCollection<Document> dexcomData = null;
    public MongoCollection<Document> glucomData = null;
    public MongoCollection<Document> deviceData = null;
    public MongoCollection<Document> dsCollection = null;
    public static Boolean isModifyingRecords = false;
    public static Object isModifyingRecordsLock = new Object();
    private MongoClient client = null;
    public UploadHelper(Context context) {
        this(context, DexcomG4Activity.DEXCOMG4);
    }
    
    public UploadHelper(Context context, int cgmSelected) {
        this.context = context;
        this.cgmSelected = cgmSelected; 
        this.mClients = null;
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
	            	editor.commit();
	    		}
	        	if (settings.contains("recordsNotUploaded")){
	        		JSONArray recordsNotUploaded = new JSONArray(settings.getString("recordsNotUploaded","[]"));
	        		for (int i = 0; i < recordsNotUploaded.length(); i++){
	        			recordsNotUploadedList.add(recordsNotUploaded.getJSONObject(i));
	        		}
	        		log.debug("retrieve older json records -->" +recordsNotUploaded.length());
	            	SharedPreferences.Editor editor = settings.edit();
	            	editor.remove("recordsNotUploaded");
	            	editor.commit();
	            }	
	        	if (settings.contains("recordsNotUploadedJson")){
	        		JSONArray recordsNotUploadedJson = new JSONArray(settings.getString("recordsNotUploadedJson","[]"));
	        		for (int i = 0; i < recordsNotUploadedJson.length(); i++){
	        			recordsNotUploadedListJson.add(recordsNotUploadedJson.getJSONObject(i));
	        		}
	        		log.debug("retrieve older json records -->" +recordsNotUploadedJson.length());
	            	SharedPreferences.Editor editor = settings.edit();
	            	editor.remove("recordsNotUploadedJson");
	            	editor.commit();
	            }	
			} catch (Exception e) {
				log.debug("ERROR Retrieving older list, I have lost them");
				recordsNotUploadedList = new ArrayList<JSONObject>();
				recordsNotUploadedListJson = new ArrayList<JSONObject>();
				SharedPreferences.Editor editor = settings.edit();
				if (settings.contains("recordsNotUploaded"))
					editor.remove("recordsNotUploaded");
				if (settings.contains("recordsNotUploadedJson"))
					editor.remove("recordsNotUploadedJson");
	        	editor.commit();
			}
        }
    }
    
    public UploadHelper(Context context, int cgmSelected, ArrayList<Messenger> mClients) {
    	this(context, cgmSelected);
        this.mClients = mClients;
    }
    /**
     * Sends a message to reconnect to mongo
     */
    private void sendRefreshDBConnToUI() {    	
    	if (mClients != null && mClients.size() > 0){
	        for (int i=mClients.size()-1; i>=0; i--) {
	            try {
	            	Message mSend = null;
	            	mSend = Message.obtain(null, MedtronicConstants.MSG_REFRESH_DB_CONNECTION); 
	                mClients.get(i).send(mSend);
	            } catch (RemoteException e) {
	                // The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
	                mClients.remove(i);
	            }
	        }
    	}
    }
    
    
    
    /**
     * Sends a message to be printed in the display (DEBUG)
     * @param valuetosend
     * @param clear, if true, the display is cleared before printing "valuetosend"
     */
    private void sendMessageToUI(String valuetosend, boolean clear) {    	
    	if (mClients != null && mClients.size() > 0){
	        for (int i=mClients.size()-1; i>=0; i--) {
	            try {
	            	Message mSend = null;
	            	if (clear){
	            		mSend = Message.obtain(null, MedtronicConstants.MSG_MEDTRONIC_CGM_CLEAR_DISPLAY);
	            		mClients.get(i).send(mSend);
	            		continue;
	            	}
	            	mSend = Message.obtain(null, MedtronicConstants.MSG_MEDTRONIC_CGM_MESSAGE_RECEIVED); 
	            	Bundle b = new Bundle();
	                b.putString("data", valuetosend);
	            	mSend.setData(b);
	                mClients.get(i).send(mSend);
	
	            } catch (RemoteException e) {
	                // The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
	                mClients.remove(i);
	            }
	        }
    	}
    }
    
    /**
     * Sends an error message to be printed in the display (DEBUG) if it is repeated, It is not printed again. If UI is not visible, It will launch a pop-up message.
     * @param valuetosend
     */
	private void sendErrorMessageToUI(String valuetosend) {
		Log.e("medtronicCGMService", valuetosend);
		log.error("Send Error Message to UI "+ valuetosend);
		if (mClients != null && mClients.size() > 0) {
			for (int i = mClients.size() - 1; i >= 0; i--) {
				try {
					Message mSend = null;
					mSend = Message
							.obtain(null,
                                    MedtronicConstants.MSG_MEDTRONIC_CGM_ERROR_RECEIVED);
					Bundle b = new Bundle();
					b.putString("data", valuetosend);
					mSend.setData(b);
					mClients.get(i).send(mSend);

				} catch (RemoteException e) {
					// The client is dead. Remove it from the list; we are going
					// through the list from back to front so this is safe to do
					// inside the loop.
					mClients.remove(i);
				}
			}
		} 
	}
	
	
	
	private JSONArray doGetRequest(HttpClient client, String url, String filter, String sort, String limit, String apiKey){
		JSONArray result = null;
		 URI nUri = null;
		 String query = "";
		 if (filter != null && filter.length() > 0) {
			 query += filter + "&";
		 }
		 if (sort != null && sort.length() > 0) {
			 query += sort + "&";
		 }
		 if (limit != null && limit.length() > 0) {
			 query += limit + "&";
		 }
		    try {
				nUri = new URI("https", null, "api.mongolab.com", 443, url, query + "apiKey="+apiKey,null);
			} catch (URISyntaxException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
		    //URIUtils.
		    HttpGet getRequest = new HttpGet(nUri);
		    HttpPost postRequest = null;
		    getRequest.addHeader("accept", "application/json");
		    try {
				HttpResponse response = client.execute(getRequest);
				InputStream instream = response.getEntity().getContent();
		        String sResult = convertStreamToString(instream);
		        // now you have the string representation of the HTML request
		        System.out.println("RESPONSE: " + sResult);
		        instream.close();
		        result = new JSONArray(sResult);
			} catch (ClientProtocolException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (Exception e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
		return result;
	}
	
	private boolean doPutRequest(HttpClient client, String url, String filter, String apiKey, JSONObject data){
		 String query = "";
		 if (filter != null && filter.length() > 0) {
			 query += filter + "&";
		 }
	
		try {
			URI nUri = null;
		    try {
				nUri = new URI("https", null, "api.mongolab.com", 443, url, query + "&apiKey="+apiKey,null);
			} catch (URISyntaxException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
				return false;
			}
			HttpPut putRequest = new HttpPut(nUri);
			putRequest.setHeader("Accept", "application/json");
			putRequest.setHeader("Content-type", "application/json");
	        StringEntity se = new StringEntity(data.toString());
	        putRequest.setEntity(se);
	        HttpResponse resp = client.execute(putRequest);
	        if (resp.getStatusLine().getStatusCode() > 201) {
	        	Log.e("UploaderHelper", "The can't be uploaded");
				log.error("The record can't be uploaded Code: "+resp.getStatusLine().getStatusCode());
				return false;
	        }
		}catch(IllegalArgumentException ex){
			log.error("UploaderHelper", "Illegal record");
			return false;
		}catch (Exception e){
			Log.e("UploaderHelper", "The retried can't be uploaded");
			log.error("The retried record can't be uploaded ", e);
			return false;
		}
		return true;
	}
	
	private boolean doPostRequest(HttpClient client, String url, String apiKey, JSONObject data){
		URI nUri = null;
		try {
			nUri = new URI("https", null, "api.mongolab.com", 443, url, "apiKey="+apiKey, null);
			HttpPost postRequest = new HttpPost(nUri);
			postRequest.setHeader("Accept", "application/json");
	        postRequest.setHeader("Content-type", "application/json");
	        StringEntity se = new StringEntity(data.toString());
	        postRequest.setEntity(se);
	        HttpResponse resp = client.execute(postRequest);
	        if (resp.getStatusLine().getStatusCode() > 201) {
	        	Log.e("UploaderHelper", "The can't be uploaded");
				log.error("The record can't be uploaded Code: "+resp.getStatusLine().getStatusCode());
				return false;
	        }
		}catch(IllegalArgumentException ex){
			log.error("UploaderHelper", "Illegal record");
			return false;
		}catch (Exception e){
			Log.e("UploaderHelper", "The retried can't be uploaded");
			log.error("The retried record can't be uploaded ", e);
			return false;
		}
		return true;
	}
    
    /**
     * 
     * @return constant String to identify the selected Device
     */
    private String getSelectedDeviceName(){
    	switch (cgmSelected){
	    	case DexcomG4Activity.MEDTRONIC_CGM:
	    		return "Medtronic_CGM";
	    	default:
	    		return "dexcom";
    	}
    }
    /**
     * doInBackground
     */
    protected Long doInBackground(Record... records) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
        Boolean enableRESTUpload = prefs.getBoolean("EnableRESTUpload", false);
        Boolean enableMongoUpload = prefs.getBoolean("EnableMongoUpload", false);
        try{
        	if (enableRESTUpload) {
                long start = System.currentTimeMillis();
                Log.i(TAG, String.format("Starting upload of %s record using a REST API", records.length));
                log.info(String.format("Starting upload of %s record using a REST API", records.length));
                doRESTUpload(prefs, records);
                Log.i(TAG, String.format("Finished upload of %s record using a REST API in %s ms", records.length, System.currentTimeMillis() - start));
                log.info(String.format("Finished upload of %s record using a REST API in %s ms", records.length, System.currentTimeMillis() - start));
            }else if (enableMongoUpload) {
            	if (!prefs.getBoolean("isMongoRest", false)){
	                long start = System.currentTimeMillis();
	                Log.i(TAG, String.format("Starting upload of %s record using Mongo", records.length));
	                log.info(String.format("Starting upload of %s record using Mongo "+dbURI, records.length));
	                doMongoUpload(prefs, records);
	                Log.i(TAG, String.format("Finished upload of %s record using a Mongo in %s ms", records.length, System.currentTimeMillis() - start));
	                log.info(String.format("Finished upload of %s record using a Mongo in %s ms", records.length, System.currentTimeMillis() - start));
            	} else {
            		long start = System.currentTimeMillis();
	                Log.i(TAG, String.format("Starting upload of %s record using Mongo", records.length));
	                log.info(String.format("Starting upload of %s record using Mongo "+dbURI, records.length));
	                doMongoRestUpload(prefs, records);
	                Log.i(TAG, String.format("Finished upload of %s record using a Mongo in %s ms", records.length, System.currentTimeMillis() - start));
	                log.info(String.format("Finished upload of %s record using a Mongo in %s ms", records.length, System.currentTimeMillis() - start));
            	}
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
        String baseURLSettings = prefs.getString("API Base URL", "");
        ArrayList<String> baseURIs = new ArrayList<String>();

        try {
            for (String baseURLSetting : baseURLSettings.split(" ")) {
                String baseURL = baseURLSetting.trim();
                if (baseURL.isEmpty()) continue;
                baseURIs.add(baseURL + (baseURL.endsWith("/") ? "" : "/"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to process API Base URL setting: " + baseURLSettings, e);
            log.error("Unable to process API Base URL setting: " + baseURLSettings, e);
            return;
        }

        for (String baseURI : baseURIs) {
            try {
                doRESTUploadTo(baseURI, records);
            } catch (Exception e) {
                Log.e(TAG, "Unable to do REST API Upload to: " + baseURI, e);
                log.error("Unable to do REST API Upload to: " + baseURI, e);
            }
        }
    }
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void doRESTUploadTo(String baseURI, Record[] records) {
    	Integer typeSaved = null;
        try {
            int apiVersion = 0;
            if (baseURI.endsWith("/v1/")) apiVersion = 1;

            String baseURL = null;
            String secret = null;
            String[] uriParts = baseURI.split("@");

            if (uriParts.length == 1 && apiVersion == 0) {
                baseURL = uriParts[0];
            } else if (uriParts.length == 1 && apiVersion > 0) {
            	if (recordsNotUploadedListJson.size() > 0){
                 	JSONArray jsonArray = new JSONArray(recordsNotUploadedListJson);
                 	SharedPreferences.Editor editor = settings.edit();
                 	editor.putString("recordsNotUploaded", jsonArray.toString());
                 	editor.commit();
                 }
                throw new Exception("Starting with API v1, a pass phase is required");
            } else if (uriParts.length == 2 && apiVersion > 0) {
                secret = uriParts[0];
                baseURL = uriParts[1];

                // new format URL!

                if (secret.contains("http")) {
                    String b = "http://";
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
                 	editor.commit();
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
                             	editor.commit();
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

                    Log.i(TAG, "DEXCOM JSON: " + jsonString);
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
            		typeSaved = 0;
            		postURL +=  "entries";
            	}else if (record instanceof MedtronicPumpRecord){
            		typeSaved = 3;
            		postURL += "deviceentries";
            	}else{
            		typeSaved = 0;
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
                          	editor.commit();
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
                    if (apiVersion >= 1)
                        populateV1APIEntry(json, record);
                    else
                        populateLegacyAPIEntry(json, record);
                } catch (Exception e) {
                    Log.w(TAG, "Unable to populate entry, apiVersion: " + apiVersion, e);
                    log.warn("Unable to populate entry, apiVersion: " + apiVersion, e);
                    continue;
                }

                String jsonString = json.toString();

                Log.i(TAG, "DEXCOM JSON: " + jsonString);
                log.info("DEXCOM JSON: " + jsonString);

                try {
                    StringEntity se = new StringEntity(jsonString);
                    post.setEntity(se);
                    post.setHeader("Accept", "application/json");
                    post.setHeader("Content-type", "application/json");

					ResponseHandler responseHandler = new BasicResponseHandler();
                    httpclient.execute(post, responseHandler);
                } catch (Exception e) {
                    if ((typeSaved != null) && (typeSaved == 0)){//Only EGV records are important enough.
    	                if (recordsNotUploadedListJson.size() > 49){
    	                	recordsNotUploadedListJson.remove(0);
    	                	recordsNotUploadedListJson.add(49,json);
    	            	}else{
    	            		recordsNotUploadedListJson.add(json);
    	            	}
                    }

                    Log.w(TAG, "Unable to post data to: '" + post.getURI().toString() + "'", e);
                    log.warn( "Unable to post data to: '" + post.getURI().toString() + "'", e);
                }
            }
            postDeviceStatus(baseURL, httpclient);
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
	            	editor.commit();	
    			} catch (Exception e) {
    				log.debug("ERROR RETRIEVING OLDER LISTs, I HAVE LOST THEM");	
    				SharedPreferences.Editor editor = settings.edit();
    				if (settings.contains("recordsNotUploadedJson"))
    					editor.remove("recordsNotUploadedJson");
    	        	editor.commit();
    			}
    	        JSONArray jsonArray = new JSONArray(recordsNotUploadedListJson);
            	SharedPreferences.Editor editor = settings.edit();
            	editor.putString("recordsNotUploadedJson", jsonArray.toString());
            	editor.commit();
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
	private void postDeviceStatus(String baseURL, DefaultHttpClient httpclient) throws Exception {
        String devicestatusURL = baseURL + "devicestatus";
        Log.i(TAG, "devicestatusURL: " + devicestatusURL);
        log.info("devicestatusURL: " + devicestatusURL);

        JSONObject json = new JSONObject();
        json.put("uploaderBattery", DexcomG4Activity.batLevel);
		json.put("device", DexcomG4Activity.pumpStatusRecord.getDeviceName() );

		JSONObject pumpInfo = new JSONObject();
		pumpInfo.put( "clock", DexcomG4Activity.pumpStatusRecord.pumpDate );
		pumpInfo.put( "reservoir", DexcomG4Activity.pumpStatusRecord.reservoirAmount);

		JSONObject iob = new JSONObject();
		iob.put( "timestamp", DexcomG4Activity.pumpStatusRecord.pumpDate );
		iob.put( "bolusiob", DexcomG4Activity.pumpStatusRecord.activeInsulin );

		JSONObject battery = new JSONObject();
		battery.put( "percent", DexcomG4Activity.pumpStatusRecord.batteryPercentage );

		pumpInfo.put( "iob", iob );
		pumpInfo.put( "battery", battery );
		json.put( "pump", pumpInfo );
        String jsonString = json.toString();

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
			json.put("date", ((CGMRecord) oRecord).sensorBGLDate.getTime());
			json.put("dateString",  oRecord.displayTime);
		} else {
			Date date = DATE_FORMAT.parse(oRecord.displayTime);
			json.put("date", date.getTime());
		}

    	if (oRecord instanceof GlucometerRecord) {
			json.put("gdValue", ((GlucometerRecord) oRecord).numGlucometerValue);
			json.put("device", getSelectedDeviceName());
			json.put("type", "mbg");
			json.put("mbg", ((GlucometerRecord) oRecord).numGlucometerValue);
		}else if (oRecord instanceof CGMRecord){
				CGMRecord pumpRecord = (CGMRecord) oRecord;
				json.put("sgv", pumpRecord.sensorBGL);
				json.put("direction", pumpRecord.direction);
				json.put("device", pumpRecord.getDeviceName());
				json.put("type", "sgv");
    	}else if (oRecord instanceof EGVRecord){
    		EGVRecord record = (EGVRecord) oRecord;
    		json.put("device", getSelectedDeviceName());
            json.put("sgv", Integer.parseInt(record.bGValue));
            json.put("direction", record.trend);
            if (cgmSelected == DexcomG4Activity.MEDTRONIC_CGM && (oRecord instanceof MedtronicSensorRecord)){
            	json.put("isig", ((MedtronicSensorRecord)record).isig);
            	json.put("calibrationFactor", ((MedtronicSensorRecord)record).calibrationFactor);
            	json.put("calibrationStatus", ((MedtronicSensorRecord)record).calibrationStatus);
            	json.put("unfilteredGlucose", ((MedtronicSensorRecord)record).unfilteredGlucose);
            	json.put("isCalibrating", ((MedtronicSensorRecord)record).isCalibrating);
            }
    	}else if (oRecord instanceof MedtronicPumpRecord){
    		MedtronicPumpRecord pumpRecord = (MedtronicPumpRecord) oRecord;
    		json.put("name", pumpRecord.getDeviceName());
    		json.put("deviceId", pumpRecord.deviceId);
    		json.put("insulinLeft", pumpRecord.insulinLeft);
    		json.put("alarm", pumpRecord.alarm);
    		json.put("status", pumpRecord.status);
    		json.put("temporaryBasal", pumpRecord.temporaryBasal);
    		json.put("batteryStatus", pumpRecord.batteryStatus);
    		json.put("batteryVoltage", pumpRecord.batteryVoltage);
    		json.put("isWarmingUp", pumpRecord.isWarmingUp);
    	}
        
    }

    private void populateLegacyAPIEntry(JSONObject json, Record oRecord) throws Exception {
    	Date date = DATE_FORMAT.parse(oRecord.displayTime);
    	json.put("timestamp", date.getTime());
    	
    	if (oRecord instanceof GlucometerRecord){
    		 json.put("gdValue", ((GlucometerRecord)oRecord).numGlucometerValue);
    	}else if (oRecord instanceof EGVRecord){
    		EGVRecord record = (EGVRecord) oRecord;
    		json.put("device", getSelectedDeviceName());
            json.put("sgv", Integer.parseInt(record.bGValue));
            json.put("direction", record.trend);
            if (cgmSelected == DexcomG4Activity.MEDTRONIC_CGM && (oRecord instanceof MedtronicSensorRecord)){
            	json.put("isig", ((MedtronicSensorRecord)record).isig);
            	json.put("calibrationFactor", ((MedtronicSensorRecord)record).calibrationFactor);
            	json.put("calibrationStatus", ((MedtronicSensorRecord)record).calibrationStatus);
            	json.put("unfilteredGlucose", ((MedtronicSensorRecord)record).unfilteredGlucose);
            	json.put("isCalibrating", ((MedtronicSensorRecord)record).isCalibrating);
            }
    	}else if (oRecord instanceof MedtronicPumpRecord){
    		MedtronicPumpRecord pumpRecord = (MedtronicPumpRecord) oRecord;
    		json.put("name", pumpRecord.getDeviceName());
    		json.put("deviceId", pumpRecord.deviceId);
    		json.put("insulinLeft", pumpRecord.insulinLeft);
    		json.put("alarm", pumpRecord.alarm);
    		json.put("status", pumpRecord.status);
    		json.put("temporaryBasal", pumpRecord.temporaryBasal);
    		json.put("batteryStatus", pumpRecord.batteryStatus);
    		json.put("batteryVoltage", pumpRecord.batteryVoltage);
    		json.put("isWarmingUp", pumpRecord.isWarmingUp);
    	}
    }
	private static String convertStreamToString(InputStream is) {

	    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
	    StringBuilder sb = new StringBuilder();

	    String line = null;
	    try {
	        while ((line = reader.readLine()) != null) {
	            sb.append(line + "\n");
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
	
	private void doMongoRestUpload(SharedPreferences prefs, Record...records) {
	    HttpParams params = new BasicHttpParams();
	    //HttpConnectionParams.setSoTimeout(params, 60000);
	    HttpConnectionParams.setConnectionTimeout(params, 60000);

	    DefaultHttpClient httpclient = new DefaultHttpClient(params);
	    String dbName = "";
	    String[] splitted = dbURI.split(":");
	    if (splitted.length >= 4 ){
    		dbName = prefs.getString("dbName", "");
    	}
	    String entriesUrl =  "/api/1/databases/"+dbName + "/collections/"+collectionName;
	    String deviceStatusUrl =  "/api/1/databases/"+dbName + "/collections/"+devicesCollectionName;
	    String gdCollectionUrl =  "/api/1/databases/"+dbName + "/collections/"+gdCollectionName;
	    String dsCollectioncUrl =  "/api/1/databases/"+dbName + "/collections/"+dsCollectionName;
	    String apiKey = prefs.getString("apiKey", "aaaaa");
	    String filter = "q={'type':{$ne:'mbg'}}&";
	    String sort =  "s={'date':-1}&";
	    String limit = "l=1&";
	    JSONObject testData = null;
	    URI nUri = null;
	    try {
			nUri = new URI("https", null, "api.mongolab.com", 443, entriesUrl, filter + sort + limit + "apiKey="+apiKey,null);
		} catch (URISyntaxException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
	    //URIUtils.
	    HttpGet getRequest = new HttpGet(nUri);
	    HttpPost postRequest = null;
	    if (recordsNotUploadedList.size() > 0){
        	Log.i(TAG, "The number of not uploaded EGV records to retry " + recordsNotUploadedList.size());
        	log.warn("The number of not uploaded EGV records to retry " + recordsNotUploadedList.size());
        	List<JSONObject> auxList = new ArrayList<JSONObject>(recordsNotUploadedList);
        	recordsNotUploadedList = new ArrayList<JSONObject>();
    		for (int i = 0; i < auxList.size(); i++){
    			try{
        			JSONObject ob = auxList.get(i);
        			if (ob != null){
            			Iterator<String> keys = ob.keys();
            			boolean atLeastOne= false;
            			testData = new JSONObject();
            			while (keys.hasNext()){
            				String key = keys.next();
            				if (ob.get(key) != null){
            					testData.put(key, ob.get(key));
            					atLeastOne = true;
            				}
            			}
            			if (atLeastOne){
            				doPostRequest(httpclient, entriesUrl, apiKey, testData);
                        }
        			}
    			}catch(IllegalArgumentException ex){
    				log.error("UploaderHelper", "Illegal record");
    			}catch (Exception e){
    				Log.e("UploaderHelper", "The retried can't be uploaded");
    				log.error("The retried record can't be uploaded ", e);
    				sendErrorMessageToUI("Error retrying the upload of a stored mongo record "+ e.getLocalizedMessage());
    				try {
    					if (recordsNotUploadedList.size() > 49){
    				    	recordsNotUploadedList.remove(0);
    				    	recordsNotUploadedList.add(49, new JSONObject(testData.toString()));
    					}else{
    						recordsNotUploadedList.add(new JSONObject(testData.toString()));
    					}	
					} catch (Exception e2) {
						// TODO: handle exception
					}
    				
    			}
    		}
    	}
	    
	    Log.i(TAG, "The number of EGV records being sent to MongoDB is " + records.length);
        log.info("The number of EGV records being sent to MongoDB is " + records.length);
        Boolean isWarmingUp = false;
        Boolean recordsTry = false;
        Integer typeSaved = 0;
        for (Record oRecord : records) {
        	recordsTry = true;
        	try{
            	testData = new JSONObject();
            	Date date = DATE_FORMAT.parse(oRecord.displayTime);
                testData.put("date", date.getTime());
                testData.put("dateString", oRecord.displayTime);
                typeSaved = null;
                if (oRecord instanceof EGVRecord){
            		EGVRecord record = (EGVRecord) oRecord; 
                    // make db object
            		testData.put("device", getSelectedDeviceName());    
                    testData.put("sgv", record.bGValue);
                    testData.put("type", "sgv");
                    testData.put("direction", record.trend);
                    typeSaved = 0;
                    if (cgmSelected == DexcomG4Activity.MEDTRONIC_CGM && (oRecord instanceof MedtronicSensorRecord)){
                    	typeSaved = 1;
                    	testData.put("isig", ((MedtronicSensorRecord)record).isig);
                    	testData.put("calibrationFactor", ((MedtronicSensorRecord)record).calibrationFactor);
                    	testData.put("calibrationStatus", ((MedtronicSensorRecord)record).calibrationStatus);
                    	testData.put("unfilteredGlucose", ((MedtronicSensorRecord)record).unfilteredGlucose);
                    	testData.put("isCalibrating", ((MedtronicSensorRecord)record).isCalibrating);
                    	log.info("Testing isCheckedWUP -->", prefs.getBoolean("isCheckedWUP", false));
                    	if (!prefs.getBoolean("isCheckedWUP", false)){
                    		log.info("Testing isCheckedWUP -->GET INTO");
	                		MedtronicPumpRecord pumpRecord = new MedtronicPumpRecord();
	                		JSONArray previousRecordCursor  = doGetRequest(httpclient, deviceStatusUrl, "q={'deviceId':{$eq:'"+prefs.getString("medtronic_cgm_id", "")+"'}}", null, null, apiKey);
	                		if (previousRecordCursor != null && previousRecordCursor.length() > 0){
	                			JSONObject previousRecord = previousRecordCursor.getJSONObject(0);
								previousRecord.put("date", testData.get("date"));
								previousRecord.put("dateString", testData.get("dateString"));
								JSONObject job = new JSONObject(previousRecord.toString());
								isWarmingUp = job.getBoolean("isWarmingUp");
								log.info("Testing isCheckedWUP -->NEXT -->ISWUP?? "+ isWarmingUp);
								if (isWarmingUp){
									pumpRecord.mergeCurrentWithDBObject(previousRecord);
									log.info("Uploading a DeviceRecord");
									filter = "q={'_id':{$eq:'"+previousRecord.get("_id")+"'}}";
									doPutRequest(httpclient, deviceStatusUrl, filter, apiKey, previousRecord);
									prefs.edit().putBoolean("isCheckedWUP", true).commit();
								}
	                		} 
                    	}
                    }
                    log.info("Uploading a EGVRecord");
                    boolean result = doPostRequest(httpclient, entriesUrl, apiKey, testData);
                    if (!result) {
                    	throw new Exception("Error uploading record ");
                    }
            	}else if (oRecord instanceof GlucometerRecord){
            		typeSaved = 2;
            		GlucometerRecord gdRecord = (GlucometerRecord) oRecord;
            		
            		testData.put("gdValue", gdRecord.numGlucometerValue);
            		log.info("Uploading a GlucometerRecord");
            		if (gdCollectionName != null && gdCollectionName.length() > 0)
            			doPostRequest(httpclient, gdCollectionUrl, apiKey, testData);
            		
        			 testData.put("device", getSelectedDeviceName());
                     testData.put("type", "mbg");
                     testData.put("mbg", gdRecord.numGlucometerValue);
                     log.info("Uploading a Glucometer Record!");
                     doPostRequest(httpclient, entriesUrl, apiKey, testData);
        		
            	}else if (oRecord instanceof MedtronicPumpRecord){
            		typeSaved = 3;
            		MedtronicPumpRecord pumpRecord = (MedtronicPumpRecord) oRecord;
            		filter = "q={'deviceId':{$eq:'"+pumpRecord.deviceId+"'}}";
            		JSONArray previousRecordCursor = doGetRequest(httpclient, deviceStatusUrl, filter, null, "1", apiKey);
            		if (previousRecordCursor != null && previousRecordCursor.length() > 0){
            			JSONObject previousRecord = previousRecordCursor.getJSONObject(0);
						previousRecord.put("date", testData.get("date"));
						previousRecord.put("dateString", testData.get("dateString"));
						isWarmingUp = pumpRecord.isWarmingUp;
						pumpRecord.mergeCurrentWithDBObject(previousRecord);
						log.info("Uploading a DeviceRecord");
						filter = "q={'_id':{$eq:'"+previousRecord.get("_id")+"'}}";
						doPutRequest(httpclient, deviceStatusUrl, filter, apiKey, previousRecord);
            		}else{
            			testData.put("name", pumpRecord.getDeviceName());
                		testData.put("deviceId", pumpRecord.deviceId);
                		testData.put("insulinLeft", pumpRecord.insulinLeft);
                		testData.put("alarm", pumpRecord.alarm);
                		testData.put("status", pumpRecord.status);
                		testData.put("temporaryBasal", pumpRecord.temporaryBasal);
                		testData.put("batteryStatus", pumpRecord.batteryStatus);
                		testData.put("batteryVoltage", pumpRecord.batteryVoltage);
                		isWarmingUp = pumpRecord.isWarmingUp;
                		testData.put("isWarmingUp", pumpRecord.isWarmingUp);
                		log.info("Uploading a DeviceRecord");
                		 doPostRequest(httpclient, deviceStatusUrl, apiKey, testData);
            		}
            	}
        	}catch(IllegalArgumentException ex){
				Log.e("UploaderHelper", "Illegal record");
			}catch(Exception ex2){
				sendErrorMessageToUI("Error uploading mongo record "+ ex2.getLocalizedMessage());
				 if ((typeSaved != null && (typeSaved == 0  ||typeSaved == 1 ))){//Only EGV records are important enough.
					 sendErrorMessageToUI("It is a SGV record, I am going to store it and retry the upload later.");
					 if (isWarmingUp){
						 prefs.edit().putBoolean("isCheckedWUP", false);
					 }
					 log.warn("added to records not uploaded");
					try {
						 if (recordsNotUploadedList.size() > 49){
						    	recordsNotUploadedList.remove(0);
						    	recordsNotUploadedList.add(49, new JSONObject(testData.toString()));
							}else{
								recordsNotUploadedList.add(new JSONObject(testData.toString()));
							}
					} catch (Exception e) {
						// TODO: handle exception
					}
				    
				 }else if (typeSaved == 3){
					 prefs.edit().putBoolean("isWarmingUp", isWarmingUp);
				 }
        		 Log.w(TAG, "Unable to upload data to mongo in loop");
        		 log.warn("Unable to upload data to mongo in loop");
        	}
        }
        try{
            //Uploading devicestatus
            boolean update = true;
            if (prefs.contains("lastBatteryUpdated")){
            	long lastTimeUpdated = prefs.getLong("lastBatteryUpdated", 0);
            	if (lastTimeUpdated > 0){
            		long current = System.currentTimeMillis();
            		long diff = current - lastTimeUpdated;
            		if (diff < MedtronicConstants.TIME_5_MIN_IN_MS)
            			update = false;
            		else{
            			SharedPreferences.Editor editor = prefs.edit();
            			editor.putLong("lastBatteryUpdated", current);
            			editor.commit();
            		}	
            	}else{
            		SharedPreferences.Editor editor = prefs.edit();
        			editor.putLong("lastBatteryUpdated", System.currentTimeMillis());
        			editor.commit();
            	}
            }else{
            	SharedPreferences.Editor editor = prefs.edit();
    			editor.putLong("lastBatteryUpdated", System.currentTimeMillis());
    			editor.commit();
            }
            if (update){
                JSONObject devicestatus = new JSONObject();
                devicestatus.put("uploaderBattery", DexcomG4Activity.batLevel);
                devicestatus.put("created_at", new Date());
                log.debug("Update Battery");
                doPostRequest(httpclient, dsCollectioncUrl, apiKey, devicestatus);
            }
        
        } catch (Exception e) {
        	if (client != null){
        		client.close();
        		client = null;
        	}
            Log.e(TAG, "Unable to upload battery data to mongo", e);
            log.error("Unable to upload battery data to mongo", e);
            StringBuffer sb1 = new StringBuffer("");
      		 sb1.append("EXCEPTION!!!!!! "+ e.getMessage()+" "+e.getCause());
      		 for (StackTraceElement st : e.getStackTrace()){
      			 sb1.append(st.toString());
      		 }
      		sendMessageToUI(sb1.toString(), false);
      	
        }
       
    	if (client != null){
    		client.close();
    		client = null;
    	}
    	if (recordsNotUploadedList.size() > 0){
        	synchronized (isModifyingRecordsLock) {
    	        try {
	        		JSONArray recordsNotUploaded = new JSONArray(settings.getString("recordsNotUploaded","[]"));
	        		if (recordsNotUploaded.length() > 0 && recordsNotUploaded.length() < recordsNotUploadedList.size()){
    	        		for (int i = 0; i < recordsNotUploaded.length(); i++){
    	        			if (recordsNotUploadedList.size() > 49){
    	        				recordsNotUploadedList.remove(0);
    	        				recordsNotUploadedList.add(49, recordsNotUploaded.getJSONObject(i));
							}else{
								recordsNotUploadedList.add(recordsNotUploaded.getJSONObject(i));
							}
    	        		}
	        		}else{
	        			for (int i = 0; i < recordsNotUploadedList.size(); i++){
							recordsNotUploaded.put(recordsNotUploadedList.get(i));
    	        		}
	        			recordsNotUploadedList.clear();
	        			int start = 0;
	        			if (recordsNotUploaded.length() > 50){
	        				start = recordsNotUploaded.length() - 51;
	        			}
	        			for (int i = start; i < recordsNotUploaded.length(); i++){
	        				recordsNotUploadedList.add(recordsNotUploaded.getJSONObject(i));
    	        		}
	        		}
	        		log.debug("retrieving older json records -->" +recordsNotUploaded.length());
	            	SharedPreferences.Editor editor = settings.edit();
	            	editor.remove("recordsNotUploaded");
	            	editor.commit();	
    			} catch (Exception e) {
    				log.debug("ERROR RETRIVING OLDER LISTs, I have lost them");	
    				SharedPreferences.Editor editor = settings.edit();
    				if (settings.contains("recordsNotUploaded"))
    					editor.remove("recordsNotUploaded");
    	        	editor.commit();
    			}
    	        JSONArray jsonArray = new JSONArray(recordsNotUploadedList);
            	SharedPreferences.Editor editor = settings.edit();
            	editor.putString("recordsNotUploaded", jsonArray.toString());
            	editor.commit();
            }
        }
	}
    private void doMongoUpload(SharedPreferences prefs, Record... records) {
        Integer typeSaved = null;
        boolean recordsTry = false;
        boolean refreshAsked = false;
        if (dbURI != null) {
        	MongoCursor<Document> cursor = null;
            Document testData = new Document();
            try {
                // connect to db
                Builder b = MongoClientOptions.builder();
                b.heartbeatConnectTimeout(150000);
                b.heartbeatFrequency(120000);
                b.heartbeatSocketTimeout(150000);
                b.maxWaitTime(150000);
                b.connectTimeout(150000);
                boolean bAchieved = false;
                String user = "";
                String password = "";
                String source = "";
                String host = "";
                String port = "";
                int iPort = -1;
                if  (dbURI.length() > 0){
                	String[] splitted = dbURI.split(":");
                	if (splitted.length >= 4 ){
                		user = splitted[1].substring(2);
                		if (splitted[2].indexOf("@") < 0)
                			bAchieved = false;
                		else{
                			password = splitted[2].substring(0,splitted[2].indexOf("@"));
                			host = splitted[2].substring(splitted[2].indexOf("@")+1, splitted[2].length());
                			if (splitted[3].indexOf("/") < 0)
                				bAchieved = false;
                			else{
                				port = splitted[3].substring(0, splitted[3].indexOf("/"));
                				source = splitted[3].substring(splitted[3].indexOf("/")+1, splitted[3].length());
                				try{
                				iPort = Integer.parseInt(port);
                				}catch(Exception ne){
                					iPort = -1;
                				}
                				if (iPort > -1)
                					bAchieved = true;
                			}
                		}
                	}
                }
                log.debug("Uri TO CHANGE user "+user+" host "+source+" password "+password);
                if (bAchieved){
                	log.debug("URI CHANGE Achieved");
	                MongoCredential mc = MongoCredential.createMongoCRCredential(user, source , password.toCharArray());
	                ServerAddress  sa = new ServerAddress(host, iPort);
	                List<MongoCredential> lcredential = new ArrayList<MongoCredential>();
	                lcredential.add(mc);
	                if (sa != null && sa.getHost() != null && sa.getHost().indexOf("localhost") < 0){
	                	client = new MongoClient(sa, lcredential, b.build());
	                	
	                }
                }
                
                if (recordsNotUploadedList.size() > 0){
                	Log.i(TAG, "The number of not uploaded EGV records to retry " + recordsNotUploadedList.size());
                	log.warn("The number of not uploaded EGV records to retry " + recordsNotUploadedList.size());
                	List<JSONObject> auxList = new ArrayList<JSONObject>(recordsNotUploadedList);
                	recordsNotUploadedList = new ArrayList<JSONObject>();
            		for (int i = 0; i < auxList.size(); i++){
            			try{
	            			JSONObject ob = auxList.get(i);
	            			if (ob != null){
		            			Iterator<String> keys = ob.keys();
		            			boolean atLeastOne= false;
		            			testData = new Document();
		            			while (keys.hasNext()){
		            				String key = keys.next();
		            				if (ob.get(key) != null){
		            					testData.put(key, ob.get(key));
		            					atLeastOne = true;
		            				}
		            			}
		            			if (atLeastOne)
		            				dexcomData.insertOne(testData);
	            			}
            			}catch(IllegalArgumentException ex){
            				log.error("UploaderHelper", "Illegal record");
            			}catch (Exception e){
            				Log.e("UploaderHelper", "The retried can't be uploaded");
            				log.error("The retried record can't be uploaded ", e);
            				sendErrorMessageToUI("Error retrying the upload of a stored mongo record "+ e.getLocalizedMessage());
            				if (recordsNotUploadedList.size() > 49){
						    	recordsNotUploadedList.remove(0);
						    	recordsNotUploadedList.add(49, new JSONObject(testData.toJson()));
							}else{
								recordsNotUploadedList.add(new JSONObject(testData.toJson()));
							}
            				if (!refreshAsked){
                      			sendRefreshDBConnToUI();
                      			refreshAsked = true;
                      		}
            			}
            		}
            	}
                Log.i(TAG, "The number of EGV records being sent to MongoDB is " + records.length);
                log.info("The number of EGV records being sent to MongoDB is " + records.length);
                Boolean isWarmingUp = false;
                for (Record oRecord : records) {
                	recordsTry = true;
                	try{
	                	testData = new Document();
	                	Date date = DATE_FORMAT.parse(oRecord.displayTime);
	                    testData.put("date", date.getTime());
	                    testData.put("dateString", oRecord.displayTime);
	                    typeSaved = null;
	                    if (oRecord instanceof EGVRecord && dexcomData != null){
	                		EGVRecord record = (EGVRecord) oRecord; 
		                    // make db object
	                		testData.put("device", getSelectedDeviceName());    
		                    testData.put("sgv", record.bGValue);
		                    testData.put("type", "sgv");
		                    testData.put("direction", record.trend);
		                    typeSaved = 0;
		                    if (cgmSelected == DexcomG4Activity.MEDTRONIC_CGM && (oRecord instanceof MedtronicSensorRecord)){
		                    	typeSaved = 1;
		                    	testData.put("isig", ((MedtronicSensorRecord)record).isig);
		                    	testData.put("calibrationFactor", ((MedtronicSensorRecord)record).calibrationFactor);
		                    	testData.put("calibrationStatus", ((MedtronicSensorRecord)record).calibrationStatus);
		                    	testData.put("unfilteredGlucose", ((MedtronicSensorRecord)record).unfilteredGlucose);
		                    	testData.put("isCalibrating", ((MedtronicSensorRecord)record).isCalibrating);
		                    	log.info("Testing isCheckedWUP -->", prefs.getBoolean("isCheckedWUP", false));
		                    	if (!prefs.getBoolean("isCheckedWUP", false) && deviceData != null){
		                    		log.info("Testing isCheckedWUP -->GET INTO");
			                		MedtronicPumpRecord pumpRecord = new MedtronicPumpRecord();
			                		cursor = deviceData.find(eq("deviceId",prefs.getString("medtronic_cgm_id", ""))).iterator();
			                		if (cursor.hasNext()){
			                			Document previousRecord = cursor.next();
										previousRecord.put("date", testData.get("date"));
										previousRecord.put("dateString", testData.get("dateString"));
										JSONObject job = new JSONObject(previousRecord.toJson());
										isWarmingUp = job.getBoolean("isWarmingUp");
										log.info("Testing isCheckedWUP -->NEXT -->ISWUP?? "+ isWarmingUp);
										if (isWarmingUp){
											pumpRecord.mergeCurrentWithDBObject(previousRecord);
											log.info("Uploading a DeviceRecord");
											deviceData.replaceOne(eq("_id", previousRecord.get("_id")), previousRecord);
											prefs.edit().putBoolean("isCheckedWUP", true).commit();
										}
			                		}
		                    	}
		                    }
		                    log.info("Uploading a EGVRecord");
		                    dexcomData.insertOne(testData);
	                	}else if (oRecord instanceof GlucometerRecord && (glucomData != null || dexcomData != null)){
	                		typeSaved = 2;
	                		GlucometerRecord gdRecord = (GlucometerRecord) oRecord;
	                		if (glucomData != null){//To be deprecated
		                		testData.put("gdValue", gdRecord.numGlucometerValue);
		                		log.info("Uploading a GlucometerRecord");
		                		glucomData.insertOne(testData);
	                		}
	                		if (dexcomData != null){
	                			 testData.put("device", getSelectedDeviceName());
	                             testData.put("type", "mbg");
	                             testData.put("mbg", gdRecord.numGlucometerValue);
	                             log.info("Uploading a Glucometer Record!");
	 		                     dexcomData.insertOne(testData);
	                		}
	                	}else if (oRecord instanceof MedtronicPumpRecord && deviceData != null){
	                		typeSaved = 3;
	                		MedtronicPumpRecord pumpRecord = (MedtronicPumpRecord) oRecord;
	                		HashMap<String, Object> filter = new HashMap<String, Object>();
	                		filter.put("deviceId", pumpRecord.deviceId);
	                		cursor = deviceData.find(eq("deviceId",pumpRecord.deviceId)).iterator();
	                		if (cursor.hasNext()){
	                			Document previousRecord = cursor.next();
								previousRecord.put("date", testData.get("date"));
								previousRecord.put("dateString", testData.get("dateString"));
								isWarmingUp = pumpRecord.isWarmingUp;
								pumpRecord.mergeCurrentWithDBObject(previousRecord);
								log.info("Uploading a DeviceRecord");
								deviceData.replaceOne(eq("_id", previousRecord.get("_id")), previousRecord);
	                		}else{
	                			testData.put("name", pumpRecord.getDeviceName());
	                    		testData.put("deviceId", pumpRecord.deviceId);
	                    		testData.put("insulinLeft", pumpRecord.insulinLeft);
	                    		testData.put("alarm", pumpRecord.alarm);
	                    		testData.put("status", pumpRecord.status);
	                    		testData.put("temporaryBasal", pumpRecord.temporaryBasal);
	                    		testData.put("batteryStatus", pumpRecord.batteryStatus);
	                    		testData.put("batteryVoltage", pumpRecord.batteryVoltage);
	                    		isWarmingUp = pumpRecord.isWarmingUp;
	                    		testData.put("isWarmingUp", pumpRecord.isWarmingUp);
	                    		log.info("Uploading a DeviceRecord");
	                			deviceData.insertOne(testData);
	                		}
	                		if (cursor != null)
	                    		cursor.close();
	                	}
                	}catch(IllegalArgumentException ex){
        				Log.e("UploaderHelper", "Illegal record");
        				if (cursor != null)
                    		cursor.close();
        			}catch(Exception ex2){
        				sendErrorMessageToUI("Error uploading mongo record "+ ex2.getLocalizedMessage());
                		if (cursor != null)
                    		cursor.close();
						 if ((typeSaved != null && (typeSaved == 0  ||typeSaved == 1 ))){//Only EGV records are important enough.
							 sendErrorMessageToUI("It is a SGV record, I am going to store it and retry the upload later.");
							 if (isWarmingUp){
								 prefs.edit().putBoolean("isCheckedWUP", false);
							 }
							 log.warn("added to records not uploaded");
						    if (recordsNotUploadedList.size() > 49){
						    	recordsNotUploadedList.remove(0);
						    	recordsNotUploadedList.add(49, new JSONObject(testData.toJson()));
							}else{
								recordsNotUploadedList.add(new JSONObject(testData.toJson()));
							}
						 }else if (typeSaved == 3){
							 prefs.edit().putBoolean("isWarmingUp", isWarmingUp);
						 }
                		 Log.w(TAG, "Unable to upload data to mongo in loop");
                		 log.warn("Unable to upload data to mongo in loop");
                		 if (!refreshAsked){
                   			sendRefreshDBConnToUI();
                   			refreshAsked = true;
                   		}
                	}
                }
            } catch (Exception e) {
            	if (cursor != null){
            		cursor.close();
            		cursor = null;
            	}
            	if (client != null){
            		client.close();
            		client = null;
            	} 
            	if (!recordsTry){
            		sendErrorMessageToUI("Error uploading mongo records "+ e.getLocalizedMessage());
            		sendErrorMessageToUI("If there are SGV records, I am going to store them to try a later upload. ");
            		for (Record oRecord : records) {
                       	testData = new Document();
	                	Date date = null;
	                	try {
							date = DATE_FORMAT.parse(oRecord.displayTime);
						} catch (ParseException e1) {
							date = new Date();
						}
	                    testData.put("date", date.getTime());
	                    testData.put("dateString", oRecord.displayTime);
	                    typeSaved = null;
	                    if (oRecord instanceof EGVRecord){
	                		EGVRecord record = (EGVRecord) oRecord; 
		                    // make db object
	                		testData.put("device", getSelectedDeviceName());    
		                    testData.put("sgv", record.bGValue);
		                    testData.put("direction", record.trend);
		                    typeSaved = 0;
		                    if (cgmSelected == DexcomG4Activity.MEDTRONIC_CGM && (oRecord instanceof MedtronicSensorRecord)){
		                    	typeSaved = 1;
		                    	testData.put("isig", ((MedtronicSensorRecord)record).isig);
		                    	testData.put("calibrationFactor", ((MedtronicSensorRecord)record).calibrationFactor);
		                    	testData.put("calibrationStatus", ((MedtronicSensorRecord)record).calibrationStatus);
		                    	testData.put("unfilteredGlucose", ((MedtronicSensorRecord)record).unfilteredGlucose);
		                    	testData.put("isCalibrating", ((MedtronicSensorRecord)record).isCalibrating);
		                    }
	                	}
	                    if ((typeSaved != null && (typeSaved == 0  ||typeSaved == 1 ))){//Only EGV records are important enough.
	                    	log.warn("added to records not uploaded");
	                    	try{
							    if (recordsNotUploadedList.size() > 49){
							    	recordsNotUploadedList.remove(0);
							    	recordsNotUploadedList.add(49, new JSONObject(testData.toJson()));
								}else{
									recordsNotUploadedList.add(new JSONObject(testData.toJson()));
								}
	                    	}catch(Exception ex){
	                    		StringBuffer sb1 = new StringBuffer("");
	                    		sb1.append("EXCEPTION!!!!!! "+ ex.getMessage()+" "+ex.getCause());
	                     		 for (StackTraceElement st : ex.getStackTrace()){
	                     			 sb1.append(st.toString());
	                     		 }
	                     		 sendMessageToUI(sb1.toString(), false);
	                    	}
						 }
                    }
            	}
                Log.e(TAG, "Unable to upload data to mongo", e);
                log.error("Unable to upload data to mongo", e);
                StringBuffer sb1 = new StringBuffer("");
          		 sb1.append("EXCEPTION!!!!!! "+ e.getMessage()+" "+e.getCause());
          		 for (StackTraceElement st : e.getStackTrace()){
          			 sb1.append(st.toString());
          		 }
          		 sendMessageToUI(sb1.toString(), false);
          		if (!refreshAsked){
          			sendRefreshDBConnToUI();
          			refreshAsked = true;
          		}
            }
            try{
            	if (db != null){
	                //Uploading devicestatus
	                boolean update = true;
	                if (prefs.contains("lastBatteryUpdated")){
	                	long lastTimeUpdated = prefs.getLong("lastBatteryUpdated", 0);
	                	if (lastTimeUpdated > 0){
	                		long current = System.currentTimeMillis();
	                		long diff = current - lastTimeUpdated;
	                		if (diff < MedtronicConstants.TIME_5_MIN_IN_MS)
	                			update = false;
	                		else{
	                			SharedPreferences.Editor editor = prefs.edit();
	                			editor.putLong("lastBatteryUpdated", current);
	                			editor.commit();
	                		}	
	                	}else{
	                		SharedPreferences.Editor editor = prefs.edit();
	            			editor.putLong("lastBatteryUpdated", System.currentTimeMillis());
	            			editor.commit();
	                	}
	                }else{
	                	SharedPreferences.Editor editor = prefs.edit();
	        			editor.putLong("lastBatteryUpdated", System.currentTimeMillis());
	        			editor.commit();
	                }
	                if (update){
		                Document devicestatus = new Document();
		                devicestatus.put("uploaderBattery", DexcomG4Activity.batLevel);
		                devicestatus.put("created_at", new Date());
		                log.debug("Update Battery");
		                dsCollection.insertOne(devicestatus);
	                }
            	}
            } catch (Exception e) {
            	if (cursor != null){
            		cursor.close();
            		cursor = null;
            	}
            	if (client != null){
            		client.close();
            		client = null;
            	}
                Log.e(TAG, "Unable to upload battery data to mongo", e);
                log.error("Unable to upload battery data to mongo", e);
                StringBuffer sb1 = new StringBuffer("");
          		 sb1.append("EXCEPTION!!!!!! "+ e.getMessage()+" "+e.getCause());
          		 for (StackTraceElement st : e.getStackTrace()){
          			 sb1.append(st.toString());
          		 }
          		sendMessageToUI(sb1.toString(), false);
          		if (!refreshAsked){
          			sendRefreshDBConnToUI();
          			refreshAsked = true;
          		}
            }
            if (cursor != null){
        		cursor.close();
        		cursor = null;
        	}
        	if (client != null){
        		client.close();
        		client = null;
        	}
        	if (recordsNotUploadedList.size() > 0){
            	synchronized (isModifyingRecordsLock) {
        	        try {
    	        		JSONArray recordsNotUploaded = new JSONArray(settings.getString("recordsNotUploaded","[]"));
    	        		if (recordsNotUploaded.length() > 0 && recordsNotUploaded.length() < recordsNotUploadedList.size()){
        	        		for (int i = 0; i < recordsNotUploaded.length(); i++){
        	        			if (recordsNotUploadedList.size() > 49){
        	        				recordsNotUploadedList.remove(0);
        	        				recordsNotUploadedList.add(49, recordsNotUploaded.getJSONObject(i));
    							}else{
    								recordsNotUploadedList.add(recordsNotUploaded.getJSONObject(i));
    							}
        	        		}
    	        		}else{
    	        			for (int i = 0; i < recordsNotUploadedList.size(); i++){
								recordsNotUploaded.put(recordsNotUploadedList.get(i));
        	        		}
    	        			recordsNotUploadedList.clear();
    	        			int start = 0;
    	        			if (recordsNotUploaded.length() > 50){
    	        				start = recordsNotUploaded.length() - 51;
    	        			}
    	        			for (int i = start; i < recordsNotUploaded.length(); i++){
    	        				recordsNotUploadedList.add(recordsNotUploaded.getJSONObject(i));
        	        		}
    	        		}
    	        		log.debug("retrieving older json records -->" +recordsNotUploaded.length());
    	            	SharedPreferences.Editor editor = settings.edit();
    	            	editor.remove("recordsNotUploaded");
    	            	editor.commit();	
        			} catch (Exception e) {
        				log.debug("ERROR RETRIVING OLDER LISTs, I have lost them");	
        				SharedPreferences.Editor editor = settings.edit();
        				if (settings.contains("recordsNotUploaded"))
        					editor.remove("recordsNotUploaded");
        	        	editor.commit();
        			}
        	        JSONArray jsonArray = new JSONArray(recordsNotUploadedList);
                	SharedPreferences.Editor editor = settings.edit();
                	editor.putString("recordsNotUploaded", jsonArray.toString());
                	editor.commit();
                }
            }
        }

    }
}

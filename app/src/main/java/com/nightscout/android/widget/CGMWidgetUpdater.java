package com.nightscout.android.widget;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;

import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import com.nightscout.android.R;
import com.nightscout.android.dexcom.DexcomG4Activity;
import com.nightscout.android.dexcom.EGVRecord;
import com.nightscout.android.medtronic.MedtronicConstants;
import com.nightscout.android.upload.MedtronicSensorRecord;
import com.nightscout.android.upload.Record;

public class CGMWidgetUpdater extends Service{
	
	public static int UPDATE_FREQUENCY_SEC = 10;
	@Override  
    public void onCreate()  
    {  
        super.onCreate();  
    }  
  
    @Override  
    public int onStartCommand(Intent intent, int flags, int startId)  
    {  
        buildUpdate();

        return super.onStartCommand(intent, flags, startId);  
    }  
  
    private void buildUpdate()  
    {  
    	
    	RemoteViews views = null;
    	KeyguardManager myKM = (KeyguardManager) getBaseContext().getSystemService(Context.KEYGUARD_SERVICE);
    	if( myKM.inKeyguardRestrictedInputMode()) {
    		views = new RemoteViews(getPackageName(), R.layout.widget_lock);
    	} else {
    		views = new RemoteViews(getPackageName(), R.layout.widget_main);
    		Intent intent = new Intent(getBaseContext(), DexcomG4Activity.class);
	        PendingIntent pendingIntent = PendingIntent.getActivity(getBaseContext(), 7, intent, 0);
	        views.setOnClickPendingIntent(R.id.imageButton1, pendingIntent);
    	}
    	Record auxRecord =  CGMWidgetUpdater.this.loadClassFile(new File(getBaseContext().getFilesDir(), "save.bin"));
        updateValues(auxRecord, views);
        
        // Push update for this widget to the home screen  
        ComponentName thisWidget = new ComponentName(this, CGMWidget.class);  
        AppWidgetManager manager = AppWidgetManager.getInstance(this);  
        manager.updateAppWidget(thisWidget, views);
    }  
    
    private void updateValues(Record auxRecord, RemoteViews views){
    	if (auxRecord instanceof MedtronicSensorRecord){
    		SharedPreferences prefs = PreferenceManager
    				.getDefaultSharedPreferences(getBaseContext());
        	MedtronicSensorRecord record = (MedtronicSensorRecord) auxRecord;
        	boolean isCalibrating = record.isCalibrating;
	    	String calib = "---";
	    	if (isCalibrating)
	    		calib = "*";
	    	else
	    		calib = MedtronicConstants.getWidgetCalAppend(record.calibrationStatus);
	    	if (prefs.getBoolean("isWarmingUp",false)){
	    		calib = "";
	    		record.bGValue = "W._Up";
	    		record.trendArrow="---";
	    	}
	    	views.setTextViewText(R.id.sgv_id, record.bGValue+calib);
	    	views.setTextViewText(R.id.arrow_id, record.trendArrow);
        	
        }else if (auxRecord instanceof EGVRecord){
        	EGVRecord record = (EGVRecord)auxRecord;
        	views.setTextViewText(R.id.sgv_id, record.bGValue);
	    	views.setTextViewText(R.id.arrow_id, record.trendArrow);
        }else{
        	views.setTextViewText(R.id.sgv_id, "---");
	    	views.setTextViewText(R.id.arrow_id, "---");
        }	            
    }
    public Record loadClassFile(File f) {
   	 ObjectInputStream ois = null;
       try {
           ois = new ObjectInputStream(new FileInputStream(f));
           Object o = ois.readObject();
           ois.close();
           return (Record) o;
       } catch (Exception ex) {
           Log.w("CGMWidget", " unable to loadEGVRecord");
           try{
	            if (ois != null)
	            	ois.close();
           }catch(Exception e){
           	Log.e("CGMWidget", " Error closing ObjectInputStream");
           }
       }
       return new Record();
   }
    @Override  
    public IBinder onBind(Intent intent)  
    {  
        return null;  
    }  
}
package com.nightscout.android.widget;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.Calendar;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import com.nightscout.android.R;
import com.nightscout.android.dexcom.DexcomG4Activity;
import com.nightscout.android.dexcom.EGVRecord;
import com.nightscout.android.medtronic.MedtronicConstants;
import com.nightscout.android.upload.MedtronicSensorRecord;
import com.nightscout.android.upload.Record;

public class CGMWidget extends AppWidgetProvider {
	private PendingIntent service = null;  
	 public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
	        final int N = appWidgetIds.length;

		 // Perform this loop procedure for each App Widget that belongs to this provider
	        for (int i=0; i<N; i++) {
	            int appWidgetId = appWidgetIds[i];

	            // Create an Intent to launch ExampleActivity
	            Intent intent = new Intent(context, DexcomG4Activity.class);
	            PendingIntent pendingIntent = PendingIntent.getActivity(context, 7, intent, 0);

	            // Get the layout for the App Widget and attach an on-click listener
	            // to the button
	            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_main);
	            views.setOnClickPendingIntent(R.id.imageButton1, pendingIntent);
	            Record auxRecord =  CGMWidget.this.loadClassFile(new File(context.getFilesDir(), "save.bin"));
	            if (auxRecord instanceof MedtronicSensorRecord){
	    	    	MedtronicSensorRecord record = (MedtronicSensorRecord) auxRecord;
	    	    	boolean isCalibrating = record.isCalibrating;
	    	    	String calib = "---";
	    	    	if (isCalibrating)
	    	    		calib = "*";
	    	    	else
	    	    		calib = MedtronicConstants.getWidgetCalAppend(record.calibrationStatus);
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
	            // Tell the AppWidgetManager to perform an update on the current app widget
	            appWidgetManager.updateAppWidget(appWidgetId, views);
	            
	            final AlarmManager m = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);  
	            
	            final Calendar TIME = Calendar.getInstance();  
	            TIME.set(Calendar.MINUTE, 0);  
	            TIME.set(Calendar.SECOND, 0);  
	            TIME.set(Calendar.MILLISECOND, 0);  
	      
	            final Intent in = new Intent(context, CGMWidgetUpdater.class);  
	      
	            if (service == null)  
	            {  
	                service = PendingIntent.getService(context, 0, in, PendingIntent.FLAG_CANCEL_CURRENT);  
	            }  
	      
	            m.setRepeating(AlarmManager.RTC, TIME.getTime().getTime(), 1000 * 30, service);  
	        }
	    }
	 @Override  
	    public void onDisabled(Context context)  
	    {  
	        final AlarmManager m = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);  
	  
	        m.cancel(service);  
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
    
	
}

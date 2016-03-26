package com.nightscout.android.upload;

import com.nightscout.android.dexcom.EGVRecord;
import com.nightscout.android.medtronic.MedtronicConstants;

public class MedtronicSensorRecord extends EGVRecord {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7653259513544666489L;
	
	public float isig = 0;
    public float calibrationFactor = 0;
    public int calibrationStatus = MedtronicConstants.WITHOUT_ANY_CALIBRATION;
    public float unfilteredGlucose = 0;
    public boolean isCalibrating = false;
    public long displayDateTime = 0;
	
	
    public void setIsig(float isig) {
		this.isig = isig;
	}
    public void setCalibrationFactor(float calibrationFactor) {
		this.calibrationFactor = calibrationFactor;
	}
    public void setCalibrationStatus(int calibrationStatus) {
		this.calibrationStatus = calibrationStatus;
	}
	public void setUnfilteredGlucose(float unfilteredGlucose) {
		this.unfilteredGlucose = unfilteredGlucose;
	}
	public long getDisplayDateTime() {
		return displayDateTime;
	}
}

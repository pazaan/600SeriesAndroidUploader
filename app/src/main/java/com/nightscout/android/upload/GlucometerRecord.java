package com.nightscout.android.upload;

import java.io.Serializable;

public class GlucometerRecord  extends Record implements Serializable{
    public float numGlucometerValue = 0;
    public long lastDate = 0;
    
    private static final long serialVersionUID = 4654897648L;	
    
    public void setNumGlucometerValue(int numGlucometerValue) {
		this.numGlucometerValue = numGlucometerValue;
	}

	public void setLastDate(long lastDate) {
		this.lastDate = lastDate;
	}

}

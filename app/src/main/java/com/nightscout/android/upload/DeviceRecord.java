package com.nightscout.android.upload;

import java.io.Serializable;

public class DeviceRecord extends Record implements Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 6321618305992689901L;
	
	public String deviceId = "";
	
	protected String deviceName = "";
	
	public String getDeviceName(){
		return deviceName;
	}
	public void setDeviceName( String deviceName ){
		this.deviceName = deviceName;
	}
}

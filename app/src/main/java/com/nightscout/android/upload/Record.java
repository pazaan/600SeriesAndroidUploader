package com.nightscout.android.upload;

import java.io.Serializable;

public class Record implements Serializable {
	 public String displayTime = "---";

	/**
	 * 
	 */
	private static final long serialVersionUID = -1381174446348390503L;
	
	public void setDisplayTime (String input) {
    	this.displayTime = input;
    }

}

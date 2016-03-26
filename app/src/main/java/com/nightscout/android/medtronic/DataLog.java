package com.nightscout.android.medtronic;

import java.util.Date;

public class DataLog {
	 int numEntries;
	  Date[] dateField = new Date [4096];
	  char[] entryType = new char [4096];
	  int[] glucose = new int [4096];
	  int[] calFactor = new int [4096];
}

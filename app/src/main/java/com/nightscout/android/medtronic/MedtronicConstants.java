package com.nightscout.android.medtronic;

public class MedtronicConstants {
		
	//Answer types
	public static final int DATA_ANSWER = 0;
    public static final int COMMAND_ANSWER = 1;
    public static final int FILTER_COMMAND = 2;
    public static final int UNKNOWN_ANSWER = 3;
    public static final int CRC_ERROR = 4;
    
    //General constants
    public static final int DEVICE_ID_LENGTH = 3;
    public static final int NUMBER_OF_RETRIES = 5;
    public static final int NUMBER_OF_EGVRECORDS = 20;
    public static final int TIMEOUT = 3000; 
    public static final int WAIT_ANSWER = 5000;
    public static final int FIVE_SECONDS__MS = 5000;
    public static final int TIME_5_MIN_IN_MS = 300000;
    public static final int TIME_15_MIN_IN_MS = 900000;
    public static final int TIME_10_MIN_IN_MS = 600000;
    public static final int TIME_20_MIN_IN_MS = 1200000;
    public static final int TIME_23_MIN_IN_MS = 1380000;
    public static final int TIME_30_MIN_IN_MS = 1800000;
    public static final int TIME_60_MIN_IN_MS = 3600000;
    public static final int TIME_90_MIN_IN_MS = 5400000;
    public static final int TIME_12_HOURS_IN_MS = 43200000;
    public static final String PREFS_NAME = "MyPrefsFile";
    public static final float SENSOR_CONVERSION_CONSTANT_VALUE = 160.72f;
    public static final float SENSOR_CONVERSION_CONSTANT_VALUE2 = Float.valueOf("5.8E-4").floatValue();
    public static final float SENSOR_CONVERSION_CONSTANT_VALUE3 = Float.valueOf("6.25E-6").floatValue();
    public static final float SENSOR_CONVERSION_CONSTANT_VALUE4 = Float.valueOf("1.5E-6").floatValue();
    public static final int SENSOR_CONVERSION_CONSTANT_VALUE5 = 65536;
    
    public static final String MONGO_URI = "https://api.mongolab.com/api/1/databases/";
    
    public static final int CALIBRATION_SENSOR = 0;
	public static final int CALIBRATION_GLUCOMETER = 1;
	public static final int CALIBRATION_MANUAL = 2;
    
	
    //Calibration status
    public static final int	WITHOUT_ANY_CALIBRATION = 0;
    public static final int	CALIBRATED = 1;
    public static final int	CALIBRATION_MORE_THAN_12H_OLD = 2;
    public static final int	LAST_CALIBRATION_FAILED_USING_PREVIOUS = 3;
    public static final int	CALIBRATED_IN_15MIN = 4;
    public static final int	CALIBRATING = 5;
    public static final int	CALIBRATING2 = 6;
    
    //Calibration status string
    public static final String	WITHOUT_ANY_CALIBRATION_STR = "Not calibrated";
    public static final String	CALIBRATED_STR = "Calibrated";
    public static final String	CALIBRATION_MORE_THAN_12H_OLD_STR = "Last Calibration > 12H";
    public static final String	LAST_CALIBRATION_FAILED_USING_PREVIOUS_STR = "Using Prev. Calibration";
    public static final String	CALIBRATED_IN_15MIN_STR = "Calibrated between 15min. and 20min.";
    public static final String	CALIBRATING_STR = "Calibrating, wait 15 to 20min.";
    public static final String	CALIBRATING2_STR = "Calibrating, 2 values received wait 5min. more";
    
	//Medtronic commands
	public static final byte MEDTRONIC_WAKE_UP = (byte)0x5d;
	public static final byte MEDTRONIC_GET_PUMP_MODEL = (byte)0x8d;
	public static final byte MEDTRONIC_GET_ALARM_MODE = (byte)0x75;
	public static final byte MEDTRONIC_GET_PUMP_STATE = (byte)0x83;
	public static final byte MEDTRONIC_GET_TEMPORARY_BASAL = (byte)0x98;
	public static final byte MEDTRONIC_READ_PAGE_COMMAND = (byte)0x9a;
	public static final byte MEDTRONIC_GET_BATTERY_STATUS = (byte)0x72;
	public static final byte MEDTRONIC_GET_REMAINING_INSULIN = (byte)0x73;
	public static final byte MEDTRONIC_GET_REMOTE_CONTROL_IDS = (byte)0x76;
	public static final byte MEDTRONIC_GET_PARADIGM_LINK_IDS = (byte)0x95;
	public static final byte MEDTRONIC_GET_SENSORID = (byte)0xcf;
	public static final byte MEDTRONIC_GET_LAST_PAGE = (byte)0xcd;
	public static final byte MEDTRONIC_GET_CALIBRATION_FACTOR = (byte)0x9c;
	public static final byte MEDTRONIC_ACK = (byte)0x06;
	public static final byte MEDTRONIC_INIT = (byte)0xff;
	//Device class
	public static final byte MEDTRONIC_PUMP = (byte)0xa7;
	public static final byte MEDTRONIC_SENSOR1 = (byte)0xaa;
	public static final byte MEDTRONIC_SENSOR2 = (byte)0xab;
	public static final byte MEDTRONIC_GL = (byte)0xa5;
	
	//Messages
	public static final int MSG_REGISTER_CLIENT = 0; 
	public static final int MSG_UNREGISTER_CLIENT = 1;
	public static final int MSG_MEDTRONIC_CGM_MESSAGE_RECEIVED = 2;
	public static final int MSG_MEDTRONIC_CGM_TEST_MESSAGE_REQUEST = 3;
	public static final int MSG_MEDTRONIC_CGM_CLEAR_DISPLAY = 4;
	public static final int MSG_MEDTRONIC_CGM_NO_PERMISSION = 5;
	public static final int MSG_MEDTRONIC_CGM_USB_GRANTED = 6;
	public static final int MSG_MEDTRONIC_CGM_REQUEST_PERMISSION = 7;
	public static final int MSG_MEDTRONIC_CGM_ERROR_RECEIVED = 8;
	public static final int MSG_MEDTRONIC_SEND_MANUAL_CALIB_VALUE = 9;
	public static final int MSG_MEDTRONIC_SEND_GET_SENSORCAL_FACTOR = 10;
	public static final int MSG_MEDTRONIC_SEND_GET_PUMP_INFO = 11;
	public static final int MSG_MEDTRONIC_SEND_INSTANT_CALIB_VALUE = 12;
	public static final int MSG_MEDTRONIC_SEND_INSTANT_GLUC_VALUE = 13;
	public static final int MSG_MEDTRONIC_CALIBRATION_DONE = 14;
	public static final int MSG_MEDTRONIC_GLUCMEASURE_DETECTED = 15;
	public static final int MSG_MEDTRONIC_GLUCMEASURE_APPROVED = 16;
	public static final int MSG_REFRESH_DB_CONNECTION = 17;
	
	
	public static String getCalibrationStrValue(int val){
		switch(val){
		case CALIBRATED:
			return CALIBRATED_STR;
		case LAST_CALIBRATION_FAILED_USING_PREVIOUS:
			return LAST_CALIBRATION_FAILED_USING_PREVIOUS_STR;
		case CALIBRATED_IN_15MIN:
			return CALIBRATED_IN_15MIN_STR;
		case CALIBRATION_MORE_THAN_12H_OLD:
			return CALIBRATION_MORE_THAN_12H_OLD_STR;
		case CALIBRATING:
			return CALIBRATING_STR;
		case CALIBRATING2:
			return CALIBRATING2_STR;
		default:
			return WITHOUT_ANY_CALIBRATION_STR;
		}
	}
	public static String getWidgetCalAppend(int val){
		switch(val){
		case CALIBRATED:
			return "";
		case LAST_CALIBRATION_FAILED_USING_PREVIOUS:
			return "?!";
		case CALIBRATED_IN_15MIN:
			return "!";
		case CALIBRATION_MORE_THAN_12H_OLD:
			return "?";
		case CALIBRATING:
			return "*";
		case CALIBRATING2:
			return "+";
		default:
			return "NC";
		}
	}
}

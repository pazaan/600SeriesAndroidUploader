package com.nightscout.android.medtronic;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.LoggerFactory;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView.BufferType;

import ch.qos.logback.classic.Logger;

import com.nightscout.android.dexcom.USB.HexDump;
import com.nightscout.android.upload.GlucometerRecord;
import com.nightscout.android.upload.MedtronicPumpRecord;
import com.nightscout.android.upload.MedtronicSensorRecord;
import com.nightscout.android.upload.Record;
import com.physicaloid.lib.Physicaloid;

/**
 * Class: MedtronicReader This class manages all read operations over all the
 * medtronic devices which are registered in Medtronic's pump. This class also
 * holds the shared variables to know when the application is commanding or it
 * has finished a request.
 * 
 * @author lmmarguenda
 * 
 */
public class MedtronicReader {
	private Logger log = (Logger) LoggerFactory.getLogger(MedtronicReader.class
			.getName());
	private static final String TAG = MedtronicReader.class.getSimpleName();
	public Physicaloid mSerialDevice;

	protected Context context = null;
	protected byte[] idPump = null;
	protected byte[] idGluc = null;
	protected byte[] idSensor = null;
	protected byte[] notFinishedRead = null;

	public String bGValue;
	public String displayTime;
	public String trend;
	public MedtronicPumpRecord lastMedtronicPumpRecord = null;// last medtronic
	// pump info
	// received
	public int crcErrorBytesToDiscard = 0;
	public boolean isCalibrating = false;
	public int calibrationStatus = MedtronicConstants.WITHOUT_ANY_CALIBRATION;
	public float calibrationIsigValue = -1f;
	public float calibrationFactor = -1f;
	public long lastCalibrationDate = 0;
	public long lastGlucometerDate = 0;
	public long lastGlucometerMessageDate = 0;
	public long lastSensorValueDate = 0;
	public float lastGlucometerValue = -1f;
	public float[] glucoseFilter = { 0.3f, 0.6f, 0.1f };
	public byte[] expectedSensorSortNumberForCalibration = { (byte) 0xff,
			(byte) 0xff }; // expected indexes of the next sensor reading for
	// correct calibration
	public GlucometerRecord lastGlucometerRecord = null;// last glucometer
	// record read
	public byte expectedSensorSortNumber = (byte) 0xff; // expected index of the
	// next sensor reading
	public Boolean expectedSensorSortNumberLock = false; // expectedSensorSortNumber
	// Lock for
	// synchronize
	public float previousValue = -1f; // last sensor value read
	public MedtronicSensorRecord previousRecord = null; // last sensor record
	public Byte lastCommandSend = null; // last command sent from this
	// application to the pump.
	public Boolean sendingCommand = false;// shared variable, It tells us that
	public Object sendingCommandLock = new Object();
	// the receptor is sending a command
	// and we have no received the ACK
	public Boolean processingCommand = false;// shared variable, It tells us
	public Object processingCommandLock = new Object();
	// that our service is launching
	// a set of commands, and it has
	// not ended yet.
	public Boolean waitingCommand = false; // shared variable, It tells us that
	public Object waitingCommandLock = new Object();
	// the receptor has sent a message
	// but we do not have the answer
	// yet.
	public CircleList<Record> lastRecordsInMemory = new CircleList<Record>(10);// array
	private Object lastRecordsListLock = new Object();
	// to
	// store
	// last
	// Records
	public ArrayList<String> knownDevices = null; // list of devices that we
	// are going to listen (pump
	// included).
	public int lastElementsAdded = 0; // last records read from sensor
	ArrayList<Messenger> mClients = new ArrayList<Messenger>();
	private byte[] lastGlucometerMessage = null; // last glucometer message
	// received
	SharedPreferences settings = null;
	SharedPreferences prefs = null;
	Integer calibrationSelected = MedtronicConstants.CALIBRATION_GLUCOMETER;
	Object calibrationSelectedLock = new Object();
	HistoricGetterThread hGetter = null;
	int historicPageIndex = -1;
	int historicPageShift = 0;
	DataLog datalog = new DataLog();
	Handler mHandlerCheckLastRead = null;
	Runnable checkLastRead = null;
	Handler mHandlerSensorCalibration = null;
	Runnable getCalibrationFromSensor = null;

	/**
	 * Constructor
	 * 
	 * @param device
	 * @param context
	 */
	public MedtronicReader(Physicaloid device, Context context,
			ArrayList<Messenger> mClients, HistoricGetterThread hGetter) {
		this.settings = context.getSharedPreferences(
				MedtronicConstants.PREFS_NAME, 0);
		this.context = context;
		this.mClients = mClients;
		this.hGetter = hGetter;
		knownDevices = new ArrayList<String>();
		mSerialDevice = device;
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		synchronized (calibrationSelectedLock) {
			if (prefs.contains("calibrationType")) {
				String type = prefs.getString("calibrationType", "3");
				if ("3".equalsIgnoreCase(type))
					calibrationSelected = MedtronicConstants.CALIBRATION_MANUAL;
				else if ("2".equalsIgnoreCase(type)) {
					calibrationSelected = MedtronicConstants.CALIBRATION_SENSOR;
				} else
					calibrationSelected = MedtronicConstants.CALIBRATION_GLUCOMETER;
			}
		}
		if (prefs.contains("medtronic_cgm_id")) {
			if (prefs.getString("medtronic_cgm_id", "").length() > 0) {
				knownDevices.add(prefs.getString("medtronic_cgm_id", ""));
				idPump = HexDump.hexStringToByteArray(prefs.getString(
						"medtronic_cgm_id", ""));
			}
		}
		if (prefs.contains("glucometer_cgm_id")) {
			if (prefs.getString("glucometer_cgm_id", "").length() > 0) {
				knownDevices.add(prefs.getString("glucometer_cgm_id", ""));
				idGluc = HexDump.hexStringToByteArray(prefs.getString(
						"glucometer_cgm_id", ""));
			}
		}
		if (prefs.contains("sensor_cgm_id")) {
			if (prefs.getString("sensor_cgm_id", "").length() > 0) {
				String sensorID = HexDump.toHexString(Integer.parseInt(prefs
						.getString("sensor_cgm_id", "0")));
				while (sensorID != null && sensorID.length() > 6) {
					sensorID = sensorID.substring(1);
				}
				log.debug("SensorID inserted "
						+ prefs.getString("sensor_cgm_id", "0")
						+ " transformed to " + sensorID);
				knownDevices.add(sensorID);
				idSensor = HexDump.hexStringToByteArray(sensorID);
			}
		}
		String listPrevKnownDevices = "";

		if (settings.contains("knownDevices")) {
			listPrevKnownDevices = settings.getString("knownDevices", "");
			String[] splitted = listPrevKnownDevices.split(",");
			for (int i = 0; i < splitted.length; i++) {
				// log.debug("splitted["+i+"]= "+splitted[i]);
				if (splitted[i] != null
						&& splitted[i].replaceAll(" ", "").length() > 0) {
					if (!knownDevices.contains(splitted[i]))
						knownDevices.add(splitted[i]);
				}
			}
		}
		storeKnownDevices();
		/*long currentTime = System.currentTimeMillis();
		long diff = currentTime - settings.getLong("lastDestroy", 0);
		if (diff > (2 * MedtronicConstants.TIME_12_HOURS_IN_MS)) {
			Log.i("Medtronic", "BORRA TODO");
			log.debug("REMOVE ALL PREFERENCES TOO MUCH TIME WITHOUT READING; DATA IS OBSOLETE");
			SharedPreferences.Editor editor = settings.edit();
			editor.remove("lastGlucometerMessage");
			editor.remove("previousValue");
			editor.remove("expectedSensorSortNumber");
			editor.remove("isCalibrating");
			if (settings.contains("calibrationStatus"))
				calibrationStatus = settings.getInt("calibrationStatus",
						MedtronicConstants.WITHOUT_ANY_CALIBRATION);
			if (settings.contains("calibrationFactor"))
				calibrationFactor = (float) settings.getFloat(
						"calibrationFactor", (float) this.calibrationFactor);
			if (settings.contains("lastCalibrationDate"))
				lastCalibrationDate = settings
				.getLong("lastCalibrationDate", 0);
			checkCalibrationOutOfTime();
			editor.remove("lastGlucometerValue");
			editor.remove("lastGlucometerDate");
			editor.remove("expectedSensorSortNumber");
			editor.remove("expectedSensorSortNumberForCalibration0");
			editor.remove("expectedSensorSortNumberForCalibration1");
			editor.remove("lastSensorValueDate");
			editor.remove("last_read");
			editor.commit();
			return;
		}*/

		if (settings.contains("lastSensorValueDate"))
			lastSensorValueDate = settings.getLong("lastSensorValueDate", 0);
		if (settings.contains("calibrationStatus"))
			calibrationStatus = settings.getInt("calibrationStatus",
					MedtronicConstants.WITHOUT_ANY_CALIBRATION);
		if (settings.contains("isCalibrating"))
			isCalibrating = settings.getBoolean("isCalibrating", false);
		if (settings.contains("lastGlucometerMessage")
				&& settings.getString("lastGlucometerMessage", "").length() > 0)
			lastGlucometerMessage = HexDump.hexStringToByteArray(settings
					.getString("lastGlucometerMessage", ""));
		if (settings.contains("calibrationFactor"))
			calibrationFactor = (float) settings.getFloat("calibrationFactor",
					(float) this.calibrationFactor);
		if (settings.contains("lastCalibrationDate"))
			lastCalibrationDate = settings.getLong("lastCalibrationDate", 0);
		if (settings.contains("previousValue"))
			previousValue = (float) settings.getFloat("previousValue",
					(float) this.previousValue);
		if (settings.contains("expectedSensorSortNumber")
				&& settings.getString("expectedSensorSortNumber", "").length() > 0) {
			expectedSensorSortNumber = HexDump.hexStringToByteArray(settings
					.getString("expectedSensorSortNumber", ""))[0];

		}
		if (settings.contains("lastGlucometerValue")
				&& settings.getFloat("lastGlucometerValue", -1) > 0) {
			lastGlucometerValue = settings.getFloat("lastGlucometerValue", -1);
		}
		if (settings.contains("lastGlucometerDate")
				&& settings.getLong("lastGlucometerDate", -1) > 0)
			lastGlucometerDate = settings.getLong("lastGlucometerDate", -1);
		if ((settings.contains("expectedSensorSortNumberForCalibration0") && settings
				.getString("expectedSensorSortNumberForCalibration0", "")
				.length() > 0)
				&& settings.contains("expectedSensorSortNumberForCalibration1")
				&& settings.getString(
						"expectedSensorSortNumberForCalibration1", "").length() > 0) {
			expectedSensorSortNumberForCalibration[0] = HexDump
					.hexStringToByteArray(settings.getString(
							"expectedSensorSortNumberForCalibration0", ""))[0];
			expectedSensorSortNumberForCalibration[1] = HexDump
					.hexStringToByteArray(settings.getString(
							"expectedSensorSortNumberForCalibration1", ""))[0];
		} else {
			if (isCalibrating) {
				expectedSensorSortNumberForCalibration[0] = (byte) 0x00;
				expectedSensorSortNumberForCalibration[1] = (byte) 0x71;
			}
		}
		checkCalibrationOutOfTime();
		if (settings.contains("last_read")) {
			String lastRead = settings.getString("lastRead", "");
			if (lastRead.length() > 0) {
				byte[] last_read = HexDump.hexStringToByteArray(lastRead);
				ArrayList<byte[]> bufferedMessages = parseMessageData(
						last_read, last_read.length);
				if (bufferedMessages != null && bufferedMessages.size() > 0)
					processBufferedMessages(bufferedMessages);
			}
		}
	}

	/**
	 * This method checks if the message received has its source in one of the
	 * devices registered.
	 * 
	 * @param readData
	 * @return true, if I "know" the source of this message.
	 */
	private boolean isMessageFromMyDevices(byte[] readData) {
		int initByte = firstByteOfDeviceId(readData);
		if (initByte < 0 || readData.length < initByte){
			log.error("Error checking initByte and received length, I can't check If is from 'My devices'");
			return false;
		}
		for (String knownDevice : knownDevices) {
			int nBytes = knownDevice.length() / 2;
			if (knownDevice.length() % 2 > 0 && knownDevice.length() > 2) {
				nBytes++;
			}
			if (readData.length < (nBytes + initByte)){
				log.error("Error checking received length, I can't check If is from 'My devices'");
				return false;
			}
			String deviceCode = HexDump.toHexString(readData, initByte, nBytes);
			
			if (knownDevice.equals(deviceCode))
				return true;
			else
				log.error("Current Known Device "+knownDevice+" Message Received From "+deviceCode);
		}
		return false;
	}

	/**
	 * Sends a message glucMessage
	 * message.
	 * 
	 * @param valuetosend
	 * @param clear
	 *            , if true, the display is cleared before printing
	 *            "valuetosend"
	 */
	private void sendGlucMessageToUI(float valuetosend, boolean calibration, boolean isCalFactorFromPump) {
		// log.debug("MedtronicReader Sends to UI "+valuetosend);
		if (mClients != null && mClients.size() > 0) {
			for (int i = mClients.size() - 1; i >= 0; i--) {
				try {
					Message mSend = null;
					
					mSend = Message
							.obtain(null,
									MedtronicConstants.MSG_MEDTRONIC_GLUCMEASURE_DETECTED);
					Bundle b = new Bundle();
					b.putFloat("data", valuetosend);
					b.putBoolean("calibrating", calibration);
					b.putBoolean("isCalFactorFromPump", isCalFactorFromPump);
					mSend.setData(b);
					mClients.get(i).send(mSend);

				} catch (RemoteException e) {
					// The client is dead. Remove it from the list; we are going
					// through the list from back to front so this is safe to do
					// inside the loop.
					mClients.remove(i);
				}
			}
		} /*
		 * else { displayMessage(valuetosend); }
		 */
	}
	private void sendMessageToUI(String valuetosend, boolean clear) {
		Log.i("medtronicReader", valuetosend);
		// log.debug("MedtronicReader Sends to UI "+valuetosend);
		if (mClients != null && mClients.size() > 0) {
			for (int i = mClients.size() - 1; i >= 0; i--) {
				try {
					Message mSend = null;
					if (clear) {
						mSend = Message
								.obtain(null,
										MedtronicConstants.MSG_MEDTRONIC_CGM_CLEAR_DISPLAY);
						mClients.get(i).send(mSend);
						continue;
					}
					mSend = Message
							.obtain(null,
									MedtronicConstants.MSG_MEDTRONIC_CGM_MESSAGE_RECEIVED);
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
		} /*
		 * else { displayMessage(valuetosend); }
		 */
	}
	/**
	 * 
	 * @param readData
	 * @return index of the first byte which contains the ID of the device.
	 */
	private int firstByteOfDeviceId(byte[] readData) {
		if (readData.length < 3)
			return -1;
		switch (readData[2]) {
		case MedtronicConstants.MEDTRONIC_PUMP:
		case MedtronicConstants.MEDTRONIC_GL:
			return 3;
		case MedtronicConstants.MEDTRONIC_SENSOR1:
		case MedtronicConstants.MEDTRONIC_SENSOR2:
			return 4;
		default:
			return -1;
		}
	}

	/**
	 * 
	 * @param readData
	 * @return index of the first byte after device ID
	 */
	private int firstByteAfterDeviceId(byte[] readData) {
		int initByte = firstByteOfDeviceId(readData);
		if (initByte < 0 || readData.length < initByte)
			return -1;
		for (String knownDevice : knownDevices) {
			if (knownDevice == null
					|| knownDevice.replaceAll(" ", "").length() == 0)
				continue;

			int nBytes = knownDevice.length() / 2;
			if (knownDevice.length() % 2 > 0 && knownDevice.length() > 2) {
				nBytes++;
			}

			if (readData.length < (nBytes + initByte))
				return -1;
			String deviceCode = HexDump.toHexString(readData, initByte, nBytes);

			if (knownDevice.equals(deviceCode))
				return (nBytes + initByte);
		}
		return -1;
	}

	/**
	 * This function checks that the first byte of the received message is
	 * correct.
	 * 
	 * @param first
	 * @return true, if the first byte is one of the send/receive values
	 */
	private boolean checkFirstByte(byte first) {
		return (first == (byte) 0x02) || (first == (byte) 0x81)
				|| (first == (byte) 0x01) || (first == (byte) 0xC1)
				|| (first == (byte) 0x03) || (first == (byte) 0x13);
	}

	/**
	 * 
	 * @param first
	 * @return A constant which tell us the kind of answer received.
	 */
	private int getAnswerType(byte first) {
		if (first == (byte) 0x02)
			return MedtronicConstants.DATA_ANSWER;
		else if ((first == (byte) 0x81) || (first == (byte) 0x01)
				|| (first == (byte) 0xC1))
			return MedtronicConstants.COMMAND_ANSWER;
		else if ((first == (byte) 0x03) || (first == (byte) 0x13))
			return MedtronicConstants.FILTER_COMMAND;
		else if (first == (byte) 0x82)
			return MedtronicConstants.CRC_ERROR;
		else
			return MedtronicConstants.UNKNOWN_ANSWER;
	}

	/**
	 * This method checks if the calibration has got too old (over 12 hours)
	 */
	private void checkCalibrationOutOfTime() {
		if ((calibrationFactor > 0)
				&& (calibrationStatus != MedtronicConstants.WITHOUT_ANY_CALIBRATION)
				&& calibrationStatus != MedtronicConstants.LAST_CALIBRATION_FAILED_USING_PREVIOUS
				&& calibrationStatus != MedtronicConstants.CALIBRATION_MORE_THAN_12H_OLD) {
			if (lastCalibrationDate > 0
					&& (System.currentTimeMillis() - lastCalibrationDate) > MedtronicConstants.TIME_12_HOURS_IN_MS) {
				calibrationStatus = MedtronicConstants.CALIBRATION_MORE_THAN_12H_OLD;
				SharedPreferences.Editor editor = settings.edit();
				editor.putInt("calibrationStatus", calibrationStatus);
				editor.commit();
			}
		}

	}

	/**
	 * This method reads from the serial device, and process the answer
	 * 
	 * @param context
	 * @return String, for debug or notification purposes
	 */
	public ArrayList<byte[]> readFromReceiver(Context context, int size) {
		ArrayList<byte[]> bufferedMessages = null;
		byte[] readFromDevice = new byte[1024];
		int read = 0;
		if (size >= 0) {
			log.debug("readFromReceiver!! a leer " + size + " bytes!!");
			try {
				read = mSerialDevice.read(readFromDevice);
			} catch (Exception e) {
				Log.e(TAG, "Unable to read from serial device", e);
				log.error("Unable to read from serial device", e);
				return null;
			}
		}
		if (read > 0) {
			Log.d("medtronic", "READ " + read);

			log.debug("Stream Received; bytes read: " + read);// +"  "+HexDump.toHexString(Arrays.copyOfRange(readFromDevice,0,read)));
		} else
			log.debug("NOTHING TO READ");
		if (read > 0) {
			SharedPreferences.Editor editor = settings.edit();
			editor.putLong("lastDestroy", System.currentTimeMillis());
			editor.commit();
			try {

				bufferedMessages = parseMessageData(
						Arrays.copyOfRange(readFromDevice, 0, read), read);
				checkCalibrationOutOfTime();
			} catch (Exception e) {
				StringBuffer sb1 = new StringBuffer("");
				sb1.append("EXCEPTION!!!!!! " + e.getMessage() + " "
						+ e.getCause());
				for (StackTraceElement st : e.getStackTrace()) {
					sb1.append(st.toString());
				}
				sendMessageToUI(sb1.toString(), false);
				bufferedMessages = new ArrayList<byte[]>();
			}
		}
		return bufferedMessages;
	}

	/**
	 * This method process all the parsed messages got using "readFromReceiver"
	 * function
	 * 
	 * @param bufferedMessages
	 *            , List of parsed messages.
	 */
	public String processBufferedMessages(ArrayList<byte[]> bufferedMessages) {
		StringBuffer sResponse = new StringBuffer("");
		int calibrationSelectedAux = 0;
		log.debug("processBufferedMessages");
		synchronized (calibrationSelectedLock) {
			calibrationSelectedAux = calibrationSelected;
		}
		try {
			for (byte[] readData : bufferedMessages) {
				if (checkFirstByte(readData[0])) {
					switch (getAnswerType(readData[0])) {
					case MedtronicConstants.DATA_ANSWER:
						log.debug("IS DATA ANSWER");
						if (isMessageFromMyDevices(readData)) {
							log.debug("IS FROM MY DEVICES");
							switch (readData[2]) {
							case MedtronicConstants.MEDTRONIC_PUMP:
								log.debug("IS A PUMP MESSAGE");
								sResponse.append(
										processPumpDataMessage(readData,
												calibrationSelectedAux))
												.append("\n");
								if (lastMedtronicPumpRecord == null) {
									lastMedtronicPumpRecord = new MedtronicPumpRecord();
									calculateDate(lastMedtronicPumpRecord,
											new Date(), 0);
									lastMedtronicPumpRecord.deviceId = prefs
											.getString("medtronic_cgm_id", "");
								}
								lastMedtronicPumpRecord.isWarmingUp = prefs
										.getBoolean("isWarmingUp", false);
								break;
							case MedtronicConstants.MEDTRONIC_GL: {
								//if (calibrationSelectedAux == MedtronicConstants.CALIBRATION_GLUCOMETER) {
									log.debug("GLUCOMETER DATA RECEIVED");
									if (lastGlucometerMessage == null
											|| lastGlucometerMessage.length == 0) {
										lastGlucometerMessage = Arrays
												.copyOfRange(readData, 0,
														readData.length);
										SharedPreferences.Editor editor = settings
												.edit();
										editor.putString(
												"lastGlucometerMessage",
												HexDump.toHexString(lastGlucometerMessage));
										editor.commit();
									} else {
										boolean isEqual = Arrays
												.equals(lastGlucometerMessage,
														readData);
										if (isEqual
												&& (System.currentTimeMillis()
														- lastGlucometerDate < MedtronicConstants.TIME_15_MIN_IN_MS)) {
											continue;
										}
										lastGlucometerDate = System
												.currentTimeMillis();
										lastGlucometerMessage = Arrays
												.copyOfRange(readData, 0,
														readData.length);
									}
									sResponse.append(
											processGlucometerDataMessage(
													readData, false)).append(
															"\n"); // Cambiado a false
									if (lastGlucometerValue > 0) {
											isCalibrating = calibrationSelectedAux == MedtronicConstants.CALIBRATION_GLUCOMETER;
										if (previousRecord == null) {
											MedtronicSensorRecord auxRecord = new MedtronicSensorRecord();
											 
												auxRecord.isCalibrating = calibrationSelectedAux == MedtronicConstants.CALIBRATION_GLUCOMETER;
											log.debug("1");
											writeLocalCSV(auxRecord, context);
										} else {
											previousRecord.isCalibrating = calibrationSelectedAux == MedtronicConstants.CALIBRATION_GLUCOMETER;
											log.debug("2");
											writeLocalCSV(previousRecord,
													context);
										}
										SharedPreferences.Editor editor = settings
												.edit();
										
											editor.putBoolean("isCalibrating", calibrationSelectedAux == MedtronicConstants.CALIBRATION_GLUCOMETER);
											if (calibrationSelectedAux == MedtronicConstants.CALIBRATION_GLUCOMETER)
												sendMessageToUI("isCalibrating", false);
											sendMessageToUI("glucometer data received", false);
										
										editor.commit();
									}
								/*} else if (calibrationSelectedAux == MedtronicConstants.CALIBRATION_SENSOR) {
									if (lastGlucometerMessage == null
											|| lastGlucometerMessage.length == 0) {
										lastGlucometerMessage = Arrays
												.copyOfRange(readData, 0,
														readData.length);
										lastGlucometerMessageDate = System
												.currentTimeMillis();
										SharedPreferences.Editor editor = settings
												.edit();
										editor.putString(
												"lastGlucometerMessage",
												HexDump.toHexString(lastGlucometerMessage));

										editor.commit();
									} else {
										boolean isEqual = Arrays
												.equals(lastGlucometerMessage,
														readData);
										if (isEqual
												&& (System.currentTimeMillis()
														- lastGlucometerMessageDate < MedtronicConstants.TIME_15_MIN_IN_MS)) {
											continue;
										}
										lastGlucometerMessageDate = System
												.currentTimeMillis();
										lastGlucometerMessage = Arrays
												.copyOfRange(readData, 0,
														readData.length);
									}
									sResponse
									.append("Glucomenter Deteted!! \n")
									.append(processGlucometerDataMessage(
											readData, false));
								} else {
									isCalibrating = false;
									if (lastGlucometerMessage == null
											|| lastGlucometerMessage.length == 0) {
										lastGlucometerMessage = Arrays
												.copyOfRange(readData, 0,
														readData.length);
										lastGlucometerMessageDate = System
												.currentTimeMillis();
										SharedPreferences.Editor editor = settings
												.edit();
										editor.putString(
												"lastGlucometerMessage",
												HexDump.toHexString(lastGlucometerMessage));

										editor.commit();
									} else {
										boolean isEqual = Arrays
												.equals(lastGlucometerMessage,
														readData);
										if (isEqual
												&& (System.currentTimeMillis()
														- lastGlucometerMessageDate < MedtronicConstants.TIME_15_MIN_IN_MS)) {
											continue;
										}
										lastGlucometerMessageDate = System
												.currentTimeMillis();
										lastGlucometerMessage = Arrays
												.copyOfRange(readData, 0,
														readData.length);
									}
									sResponse
									.append("Glucomenter Deteted!! \n")
									.append(processGlucometerDataMessage(
											readData, false));
								}*/
								break;
							}
							case MedtronicConstants.MEDTRONIC_SENSOR1: {
								if (prefs.getString("glucSrcTypes", "1")
										.equals("2")) {
									log.debug("Sensor value received, but value is took only by pump logs");
									break;
								}
								log.debug("WARMING_UP");
								SharedPreferences.Editor editor = settings
										.edit();
								editor.remove("lastGlucometerMessage");
								editor.remove("previousValue");
								editor.remove("expectedSensorSortNumber");
								editor.remove("isCalibrating");
								calibrationStatus = MedtronicConstants.WITHOUT_ANY_CALIBRATION;
								editor.putInt(
										"calibrationStatus",
										MedtronicConstants.WITHOUT_ANY_CALIBRATION);
								editor.remove("calibrationFactor");
								log.debug("remove lastCalibrationDate");
								editor.remove("lastCalibrationDate");
								editor.remove("lastGlucometerValue");
								editor.remove("lastGlucometerDate");
								editor.remove("expectedSensorSortNumber");
								editor.remove("expectedSensorSortNumberForCalibration0");
								editor.remove("expectedSensorSortNumberForCalibration1");
								editor.remove("isCheckedWUP");
								if (!prefs.getBoolean("isWarmingUp", false)) {
									if (lastMedtronicPumpRecord == null) {
										lastMedtronicPumpRecord = new MedtronicPumpRecord();
										calculateDate(lastMedtronicPumpRecord,
												new Date(), 0);
										lastMedtronicPumpRecord.deviceId = prefs
												.getString("medtronic_cgm_id",
														"");
									}

									editor.putBoolean("isWarmingUp", true);

									lastMedtronicPumpRecord.isWarmingUp = true;
								}

								if (previousRecord == null) {
									MedtronicSensorRecord auxRecord = new MedtronicSensorRecord();
									calculateDate(auxRecord, new Date(), 0);
									log.debug("3");
									writeLocalCSV(auxRecord, context);
								} else {
									calculateDate(previousRecord, new Date(), 0);
									log.debug("4");
									writeLocalCSV(previousRecord, context);
								}
								sendMessageToUI("sensor data wUp.", false);
								editor.commit();
								break;
							}
							case MedtronicConstants.MEDTRONIC_SENSOR2:

								if (lastMedtronicPumpRecord != null)
									lastMedtronicPumpRecord.isWarmingUp = false;
								if (prefs.getString("glucSrcTypes", "1")
										.equals("2")) {
									if (prefs.getBoolean("isWarmingUp", false)) {
										if (lastMedtronicPumpRecord == null) {
											lastMedtronicPumpRecord = new MedtronicPumpRecord();
											calculateDate(
													lastMedtronicPumpRecord,
													new Date(), 0);
											lastMedtronicPumpRecord.deviceId = prefs
													.getString(
															"medtronic_cgm_id",
															"");
										}
										lastMedtronicPumpRecord.isWarmingUp = false;
										SharedPreferences.Editor editor1 = prefs
												.edit();
										editor1.putBoolean("isWarmingUp", false);
										editor1.commit();
									}
									log.debug("Sensor value received, but value is took only by pump logs");
									break;
								}
								Log.i("MEdtronic", "process sensor2");
								log.debug("SENSOR DATA RECEIVED");
								if (prefs.getBoolean("isWarmingUp", false)) {
									if (lastMedtronicPumpRecord == null) {
										lastMedtronicPumpRecord = new MedtronicPumpRecord();
										calculateDate(lastMedtronicPumpRecord,
												new Date(), 0);
										lastMedtronicPumpRecord.deviceId = prefs
												.getString("medtronic_cgm_id",
														"");
									}
									lastMedtronicPumpRecord.isWarmingUp = false;
									SharedPreferences.Editor editor = prefs
											.edit();
									editor.remove("isWarmingUp");
									editor.commit();
								}
								boolean calculateCalibration = false;
								if (isCalibrating) {
									calculateCalibration = true;
								}
								sResponse.append(
										processSensorDataMessage(readData))
										.append("\n");
								if (calculateCalibration && !isCalibrating) {
									SharedPreferences.Editor editor = settings
											.edit();
									editor.putBoolean("isCalibrating", false);
									editor.commit();
								}
								sendMessageToUI("sensor data value received",
										false);
								break;
							default:
								Log.i("MEdtronic", "No Match");
								log.debug("I can't understand this message");
								sResponse
								.append("I can't process this message, no device match.")
								.append("\n");
								break;
							}
						} else {
							Log.i("Medtronic",
									"I dont have to listen to this. This message comes from another source.");
							log.debug("I don't have to listen to this message. This message comes from another source.");
							sResponse
							.append("I don't have to listen to this. This message comes from another source.")
							.append("\n");
						}
						break;
					case MedtronicConstants.COMMAND_ANSWER:
						log.debug("ACK Received");
						synchronized (sendingCommandLock) {
							sendingCommand = false;
						}
						break;
					case MedtronicConstants.FILTER_COMMAND:
						if (readData[0] == (byte) 0x13)
							sResponse.append("FILTER DEACTIVATED").append("\n");
						else
							sResponse.append("FILTER ACTIVATED").append("\n");
						break;
					default: {
						log.debug("I don't understand this message "
								+ HexDump.toHexString(readData));
						sResponse.append(
								"I don't understand the received message ")
								.append("\n");
					}
					}
				} else {
					sResponse.append("CRC Error ").append("\n");
					log.debug("CRC ERROR!!! " + HexDump.dumpHexString(readData));
				}
			}
		} catch (Exception ex2) {
			StringBuffer sb1 = new StringBuffer("");
			sb1.append("EXCEPTION!!!!!! " + ex2.getMessage() + " "
					+ ex2.getCause());
			for (StackTraceElement st : ex2.getStackTrace()) {
				sb1.append(st.toString());
			}
			sendMessageToUI(sb1.toString(), false);
		}
		SharedPreferences.Editor editor = settings.edit();
		editor.remove("last_read");
		editor.commit();
		return sResponse.toString();
	}

	/**
	 * This function parses the raw bytes to correct messages or discards the
	 * wrong bytes.
	 * 
	 * @param readData
	 *            , array of bytes read
	 * @param read
	 *            , length of bytes read
	 * @return ArrayList of parsed messages.
	 */
	private ArrayList<byte[]> parseMessageData(byte[] readData, int read) {
		byte[] readBuffer = null;
		log.debug("PARSE MESSAGE");
		ArrayList<byte[]> messageList = new ArrayList<byte[]>();
		if (notFinishedRead == null || notFinishedRead.length <= 0) {
			readBuffer = Arrays.copyOf(readData, read);
		} else {
			readBuffer = Arrays.copyOf(notFinishedRead, notFinishedRead.length
					+ read);
			for (int i = 0; i < read; i++) {
				readBuffer[notFinishedRead.length + i] = readData[i];
			}
			notFinishedRead = null;
		}
		SharedPreferences.Editor editor = settings.edit();
		editor.putString("last_read", HexDump.toHexString(Arrays.copyOfRange(
				readBuffer, 0, readBuffer.length)));
		editor.commit();
		int i = 0;
		if (crcErrorBytesToDiscard > 0)
			i = crcErrorBytesToDiscard;
		crcErrorBytesToDiscard = 0;
		while (i < readBuffer.length) {
			int answer = getAnswerType(readBuffer[i]);
			if (answer == MedtronicConstants.COMMAND_ANSWER) {
				log.debug("COMMAND");
				if (readBuffer.length >= i + 3)
					messageList.add(Arrays.copyOfRange(readBuffer, i, i + 3));
				else {
					notFinishedRead = Arrays.copyOfRange(readBuffer, i,
							readBuffer.length);
					return messageList;
				}
				i += 3;
			} else if (answer == MedtronicConstants.FILTER_COMMAND) {
				log.debug("FILTERCOMMAND");
				messageList.add(Arrays.copyOfRange(readBuffer, i, i + 1));
				i++;
			} else if (answer == MedtronicConstants.CRC_ERROR) {
				log.debug("CRC ERROR");
				if (hGetter != null && hGetter.isWaitingNextLine) {
					if (hGetter.timeout >= 2) {
						hGetter.timeout = 0;
						log.debug("too much retries");
						sendMessageToUI(
								"historic log read aborted! too much crc errors, waiting to retry.",
								false);
					} else {
						sendMessageToUI(
								"CRC error reading historic log line, reinitializating read...",
								false);
						hGetter.timeout++;
						hGetter.commandList = Arrays.copyOf(
								hGetter.commandList,
								hGetter.commandList.length + 1);
						hGetter.commandList[hGetter.commandList.length - 1] = MedtronicConstants.MEDTRONIC_READ_PAGE_COMMAND;
						hGetter.firstReadPage = true;
						hGetter.isWaitingNextLine = true;
						hGetter.withoutConfirmation = 0;
						hGetter.currentLine = -1;
						hGetter.historicPage.clear();
						synchronized (waitingCommandLock) {
							waitingCommand = false;
							lastCommandSend = null;
						}
					}
				}
				if (readBuffer.length <= i + 1) {
					notFinishedRead = Arrays.copyOfRange(readBuffer, i,
							readBuffer.length);
					return messageList;
				}
				int length = HexDump.unsignedByte(readBuffer[i + 1]);
				if (length <= 0) {
					i++;
					continue;
				}

				if (readBuffer.length >= i + length + 2) {
					i = i + length + 2;
				} else {
					crcErrorBytesToDiscard = (i + length + 2)
							- readBuffer.length;
					return messageList;
				}
			} else if (answer == MedtronicConstants.DATA_ANSWER) {
				log.debug("DATA_ANSWER");
				if (readBuffer.length <= i + 1) {
					notFinishedRead = Arrays.copyOfRange(readBuffer, i,
							readBuffer.length);
					return messageList;
				}
				int length = HexDump.unsignedByte(readBuffer[i + 1]);
				if (length <= 0) {
					i++;
					continue;
				}
				if (readBuffer.length >= i + length + 2) {
					messageList.add(Arrays.copyOfRange(readBuffer, i, i
							+ length + 2));
					i = i + length + 2;// I have to add 2 bytes CTRL byte and
					// SIZE byte
				} else {
					notFinishedRead = Arrays.copyOfRange(readBuffer, i,
							readBuffer.length);
					return messageList;
				}
			} else {
				i++;
			}
		}
		return messageList;
	}

	/**
	 * This method process the pump answers
	 * 
	 * @param readData
	 * @param calibrationSelected
	 * @return String, for debug or notification purposes
	 */
	public String processPumpDataMessage(byte[] readData,
			int calibrationSelected) {
		int commandByte = firstByteAfterDeviceId(readData);
		String sResult = "I do nothing";
		if (commandByte < 0)
			return "Error, I can not identify the command byte";
		if (lastCommandSend == null)
			return "lastCommand == null";
		switch (readData[commandByte]) {
		case MedtronicConstants.MEDTRONIC_GET_LAST_PAGE: {
			if (lastCommandSend != null) {
				synchronized (waitingCommandLock) {
					waitingCommand = false;
					lastCommandSend = null;
				}
				byte[] modelArray = Arrays.copyOfRange(readData,
						commandByte + 2, (commandByte + 6));
				historicPageIndex = HexDump.byteArrayToInt(modelArray);
				hGetter.historicPageIndex = historicPageIndex;
				hGetter.lastHistoricPage = modelArray;
				String sModel = new String(HexDump.toHexString(modelArray));
				sResult = "Command " + commandByte + " Read Data "
						+ HexDump.toHexString(readData)
						+ " Pump last historic page......: " + sModel;
			}
			log.debug(sResult);
			return sResult;
		}
		case MedtronicConstants.MEDTRONIC_READ_PAGE_COMMAND: {
			log.debug("READ_PAGE");

			if (lastCommandSend != null) {
				try {
					log.debug("lcommand send != null");
					if (!hGetter.firstReadPage) {
						hGetter.isWaitingNextLine = false;
						int currentLineAux = HexDump
								.unsignedByte(readData[commandByte + 1]);
						log.debug("!first page ");
						if (!(currentLineAux == HexDump
								.unsignedByte((byte) 0x90)
								|| (currentLineAux == hGetter.currentLine + 1) || hGetter.currentLine < 1
								&& currentLineAux == 1)) {
							log.debug("Error");
							hGetter.commandList = Arrays.copyOf(
									hGetter.commandList,
									hGetter.commandList.length + 1);
							hGetter.commandList[hGetter.commandList.length - 1] = MedtronicConstants.MEDTRONIC_READ_PAGE_COMMAND;
							hGetter.wThread.isRequest = true;
							hGetter.firstReadPage = false;
							hGetter.withoutConfirmation = 0;
							hGetter.isWaitingNextLine = true;
							hGetter.currentLine = -1;
							synchronized (waitingCommandLock) {
								waitingCommand = false;
								lastCommandSend = null;
							}
							hGetter.historicPage.clear();
							return "Error currentLine is "
							+ hGetter.currentLine + " next line is "
							+ currentLineAux
							+ " is not the order expected";
						}
						hGetter.currentLine = currentLineAux;
						byte[] modelArray = Arrays.copyOfRange(readData,
								commandByte + 2, (commandByte + 2 + (4 * 16)));
						hGetter.historicPage.add(modelArray);
						if (hGetter.currentLine != HexDump
								.unsignedByte((byte) 0x90)) {
							log.debug("is correct line");
							hGetter.commandList = Arrays.copyOf(
									hGetter.commandList,
									hGetter.commandList.length + 1);
							hGetter.commandList[hGetter.commandList.length - 1] = MedtronicConstants.MEDTRONIC_ACK;
							hGetter.withoutConfirmation = 0;
							hGetter.wThread.isRequest = true;
							hGetter.wThread.postCommandBytes = null;
							// String sModel = new
							// String(HexDump.toHexString(modelArray));
							sResult = "Pump last historic page ("
									+ hGetter.currentLine + ")......: Ok.";// +
							// sModel;
							hGetter.isWaitingNextLine = true;
						} else {
							log.debug("All lines read.");
							processHistoricPage();
						}
					}
				} finally {
					synchronized (waitingCommandLock) {
						waitingCommand = false;
						lastCommandSend = null;
					}
				}
			}

			log.debug(sResult);
			return sResult;
		}
		case MedtronicConstants.MEDTRONIC_GET_PUMP_MODEL:
			log.debug("Pump Model Received");
			sendMessageToUI("Pump Model Received...", false);
			if (lastMedtronicPumpRecord == null) {
				lastMedtronicPumpRecord = new MedtronicPumpRecord();
				calculateDate(lastMedtronicPumpRecord, new Date(), 0);
				lastMedtronicPumpRecord.deviceId = prefs.getString(
						"medtronic_cgm_id", "");
			}
			if (lastCommandSend != null) {
				synchronized (waitingCommandLock) {
					waitingCommand = false;
					lastCommandSend = null;
				}
				byte[] modelArray = Arrays.copyOfRange(readData,
						commandByte + 2,
						(commandByte + 2 + (readData[commandByte + 1])));
				String sModel = new String(modelArray);
				sResult = "Pump model......: " + sModel;
				lastMedtronicPumpRecord.model = sModel;
			}
			return sResult;
		case MedtronicConstants.MEDTRONIC_GET_ALARM_MODE:
			log.debug("Pump Alarm Mode Received");
			if (lastMedtronicPumpRecord == null) {
				lastMedtronicPumpRecord = new MedtronicPumpRecord();
				calculateDate(lastMedtronicPumpRecord, new Date(), 0);
				lastMedtronicPumpRecord.deviceId = prefs.getString(
						"medtronic_cgm_id", "");
			}
			if (lastCommandSend != null) {
				synchronized (waitingCommandLock) {
					waitingCommand = false;
					lastCommandSend = null;
				}
				int status = readData[commandByte + 2];
				if (status == 0)
					sResult = "Ok";
				else
					sResult = "Unknown (by now)";
				lastMedtronicPumpRecord.alarm = sResult;
			}
			return sResult;
		case MedtronicConstants.MEDTRONIC_GET_PUMP_STATE:
			log.debug("Pump Status Received");
			sendMessageToUI("Pump Status Received...", false);
			if (lastMedtronicPumpRecord == null) {
				lastMedtronicPumpRecord = new MedtronicPumpRecord();
				calculateDate(lastMedtronicPumpRecord, new Date(), 0);
				lastMedtronicPumpRecord.deviceId = prefs.getString(
						"medtronic_cgm_id", "");
			}
			if (lastCommandSend != null) {
				synchronized (waitingCommandLock) {
					waitingCommand = false;
					lastCommandSend = null;
				}
				sResult = "Pump state...........: "
						+ HexDump.toHexString(readData[commandByte + 2]);
				lastMedtronicPumpRecord.status = HexDump
						.toHexString(readData[commandByte + 2]);
			}

			return sResult;
		case MedtronicConstants.MEDTRONIC_GET_TEMPORARY_BASAL:
			log.debug("Pump Temporary Basal Received");
			if (lastMedtronicPumpRecord == null) {
				lastMedtronicPumpRecord = new MedtronicPumpRecord();
				calculateDate(lastMedtronicPumpRecord, new Date(), 0);
				lastMedtronicPumpRecord.deviceId = prefs.getString(
						"medtronic_cgm_id", "");
			}
			if (lastCommandSend != null) {
				synchronized (waitingCommandLock) {
					waitingCommand = false;
					lastCommandSend = null;
				}
				byte[] tempBasalArray = Arrays.copyOfRange(readData,
						commandByte + 2,
						(commandByte + 2 + (readData[commandByte + 2])));
				String sTempBasalArray = HexDump.toHexString(tempBasalArray);

				sResult = "Temporary basal......: " + sTempBasalArray;
				lastMedtronicPumpRecord.status = sTempBasalArray;
			}
			return sResult;
		case MedtronicConstants.MEDTRONIC_GET_BATTERY_STATUS:
			log.debug("Pump Battery Status Received");
			sendMessageToUI("Pump Battery Status Received...", false);
			if (lastMedtronicPumpRecord == null) {
				lastMedtronicPumpRecord = new MedtronicPumpRecord();
				calculateDate(lastMedtronicPumpRecord, new Date(), 0);
				lastMedtronicPumpRecord.deviceId = prefs.getString(
						"medtronic_cgm_id", "");
			}
			if (lastCommandSend != null) {
				synchronized (waitingCommandLock) {
					waitingCommand = false;
					lastCommandSend = null;
				}
				int status = readData[commandByte + 3];
				float voltage = (((float) HexDump
						.unsignedByte(readData[commandByte + 3])) * 256f + (float) HexDump
						.unsignedByte(readData[commandByte + 4])) / 100;
				if (status == 0) {
					sResult = "Battery status.......: Normal\n";
					lastMedtronicPumpRecord.batteryStatus = "Normal";
				} else {
					sResult = "Battery status.......: Low\n";
					lastMedtronicPumpRecord.batteryStatus = "Low";
				}

				sResult += "Battery voltage......: " + voltage + " Volts";
				lastMedtronicPumpRecord.batteryVoltage = "" + voltage;
			}
			return sResult;
		case MedtronicConstants.MEDTRONIC_GET_REMAINING_INSULIN:
			log.debug("Pump Remaining Insulin Received");
			sendMessageToUI("Pump Remaining Insulin Received...", false);
			if (lastMedtronicPumpRecord == null) {
				lastMedtronicPumpRecord = new MedtronicPumpRecord();
				calculateDate(lastMedtronicPumpRecord, new Date(), 0);
				lastMedtronicPumpRecord.deviceId = prefs.getString(
						"medtronic_cgm_id", "");
			}
			if (lastCommandSend != null) {
				synchronized (waitingCommandLock) {
					waitingCommand = false;
					lastCommandSend = null;
				}
				float insulinLeft = (HexDump
						.unsignedByte(readData[commandByte + 4]) * 256f + (float) HexDump
						.unsignedByte(readData[commandByte + 5])) / 40f;
				sResult = "Remaining insulin....: " + insulinLeft + " Units";
				lastMedtronicPumpRecord.insulinLeft = insulinLeft;
			}
			return sResult;
		case MedtronicConstants.MEDTRONIC_GET_REMOTE_CONTROL_IDS:
			log.debug("Pump Remote Control Ids Received");
			sendMessageToUI("Pump Remote Control Ids Received...", false);
			if (lastCommandSend != null) {
				synchronized (waitingCommandLock) {
					waitingCommand = false;
					lastCommandSend = null;
				}

				byte[] remoteControlID1 = Arrays.copyOfRange(readData,
						commandByte + 2, commandByte + 8);
				String sRemoteControlID1 = new String(remoteControlID1);
				if (HexDump.isHexaNumber(sRemoteControlID1)) {
					if (!knownDevices.contains(sRemoteControlID1))
						knownDevices.add(sRemoteControlID1);
				}

				byte[] remoteControlID2 = Arrays.copyOfRange(readData,
						commandByte + 8, commandByte + 14);
				String sRemoteControlID2 = new String(remoteControlID2);
				if (HexDump.isHexaNumber(sRemoteControlID2)) {
					if (!knownDevices.contains(sRemoteControlID2))
						knownDevices.add(sRemoteControlID2);
				}

				byte[] remoteControlID3 = Arrays.copyOfRange(readData,
						commandByte + 14, commandByte + 20);
				String sRemoteControlID3 = new String(remoteControlID3);
				if (HexDump.isHexaNumber(sRemoteControlID3)) {
					if (!knownDevices.contains(sRemoteControlID3))
						knownDevices.add(sRemoteControlID3);
				}
				storeKnownDevices();
				sResult = "Remote Control IDs...: " + sRemoteControlID1 + "  "
						+ sRemoteControlID2 + "  " + sRemoteControlID3;

			}
			return sResult;
		case MedtronicConstants.MEDTRONIC_GET_PARADIGM_LINK_IDS:
			log.debug("Pump Paradigm Link Ids Received");
			sendMessageToUI("Pump Paradigm Link Ids Received...", false);
			if (lastCommandSend != null) {
				synchronized (waitingCommandLock) {
					waitingCommand = false;
					lastCommandSend = null;
				}

				byte[] glucID1 = Arrays.copyOfRange(readData, commandByte + 2,
						commandByte + 8);
				String sGlucID1 = new String(glucID1);
				if (HexDump.isHexaNumber(sGlucID1)) {
					if (!knownDevices.contains(sGlucID1))
						knownDevices.add(sGlucID1);
				}

				byte[] glucID2 = Arrays.copyOfRange(readData, commandByte + 8,
						commandByte + 14);
				String sGlucID2 = new String(glucID2);
				if (HexDump.isHexaNumber(sGlucID2)) {
					if (!knownDevices.contains(sGlucID2))
						knownDevices.add(sGlucID2);
				}

				byte[] glucID3 = Arrays.copyOfRange(readData, commandByte + 14,
						commandByte + 20);
				String sGlucID3 = new String(glucID3);
				if (HexDump.isHexaNumber(sGlucID3)) {
					if (!knownDevices.contains(sGlucID3))
						knownDevices.add(sGlucID3);
				}
				storeKnownDevices();
				sResult = "Paradigm Link IDs....: " + sGlucID1 + "  "
						+ sGlucID2 + "  " + sGlucID3;
			}
			return sResult;
		case MedtronicConstants.MEDTRONIC_GET_SENSORID:
			log.debug("Pump Sensor Id Received");
			sendMessageToUI("Pump Sensor Id Received...", false);
			if (lastCommandSend != null) {
				synchronized (waitingCommandLock) {
					waitingCommand = false;
					lastCommandSend = null;
				}
				if (readData.length >= HexDump.unsignedByte(readData[1])
						&& HexDump.unsignedByte(readData[1]) > 0) {
					byte[] sensorId = Arrays.copyOfRange(readData,
							commandByte + 58, commandByte + 61);
					String sSensorId = HexDump.toHexString(sensorId);
					sResult = "Sensor ID...: " + sSensorId;
					if (!knownDevices.contains(sSensorId))
						knownDevices.add(sSensorId);
					storeKnownDevices();
				} else
					sResult = readData.length + " Not enough length";
			}
			return sResult;
		case MedtronicConstants.MEDTRONIC_GET_CALIBRATION_FACTOR:
			log.debug("Pump Calibration Factor Received");
			sendMessageToUI("Pump Cal. Factor Received...", false);
			if (lastCommandSend != null) {
				synchronized (waitingCommandLock) {
					waitingCommand = false;
					lastCommandSend = null;
				}
				float factor = ((float) HexDump
						.unsignedByte(readData[commandByte + 2]) * 256f + (float) HexDump
						.unsignedByte(readData[commandByte + 3]));
				if (calibrationSelected == MedtronicConstants.CALIBRATION_SENSOR) {
					if (factor > 0) {
						calibrationStatus = MedtronicConstants.CALIBRATED;
						calibrationFactor = factor / 1126;
					} else {
						if (calibrationStatus != MedtronicConstants.WITHOUT_ANY_CALIBRATION) {
							calibrationStatus = MedtronicConstants.LAST_CALIBRATION_FAILED_USING_PREVIOUS;
						}
					}
					SharedPreferences.Editor editor = settings.edit();
					editor.putFloat("calibrationFactor",
							(float) calibrationFactor);
					editor.putInt("calibrationStatus", calibrationStatus);
					editor.commit();
					if (previousRecord == null) {
						MedtronicSensorRecord auxRecord = new MedtronicSensorRecord();
						auxRecord.calibrationStatus = calibrationStatus;
						auxRecord.calibrationFactor = calibrationFactor;
						log.debug("5");
						writeLocalCSV(auxRecord, context);
					} else {
						previousRecord.calibrationStatus = calibrationStatus;
						previousRecord.calibrationFactor = calibrationFactor;
						log.debug("6");
						writeLocalCSV(previousRecord, context);
					}
					if (lastMedtronicPumpRecord == null) {
						lastMedtronicPumpRecord = new MedtronicPumpRecord();
						calculateDate(lastMedtronicPumpRecord, new Date(), 0);
						lastMedtronicPumpRecord.deviceId = prefs.getString(
								"medtronic_cgm_id", "");
					}
					// sendMessageToUI("Is In RANGE CALIBRATION SUCCESS "+currentMeasure+
					// " Factor "+calibrationFactor, false);

				}
				sResult = "Calibration Factor...: " + factor;
			}
			return sResult;
		case MedtronicConstants.MEDTRONIC_ACK:
			log.debug("Pump Ack Received");
			if (lastCommandSend != null) {
				synchronized (sendingCommandLock) {
					sendingCommand = false;
				}
				synchronized (waitingCommandLock) {
					waitingCommand = false;
					lastCommandSend = null;
				}
			}
			return "ACK RECEIVED! ";
		default:
			log.error("Undecoded Command");
			return "I do not understand this command "
			+ HexDump.toHexString(readData[commandByte]);
		}
	}

	/**
	 * method to check the Medtronic log page received.
	 */
	private void processHistoricPage() {
		ArrayList<Byte> page = new ArrayList<Byte>();
		ArrayList<Byte> crcPage = new ArrayList<Byte>();
		int substract = 3;
		for (int i = 0; i < hGetter.historicPage.size(); i++) {
			byte[] hPage = hGetter.historicPage.get(i);
			for (int j = 0; j < hPage.length; j++) {
				crcPage.add(hPage[j]);
			}
		}
		byte crc2 = crcPage.get(crcPage.size() - 1);// remove crc
		crcPage.remove(crcPage.size() - 1);// remove crc
		byte crc1 = crcPage.get(crcPage.size() - 1);// remove crc
		crcPage.remove(crcPage.size() - 1);// remove crc
		// CALCULA EL CRC
		byte[] crcReceived = { crc1, crc2 };
		short sCrcReceived = HexDump.byteArrayToShort(crcReceived);
		Byte[] finalCrcPage = crcPage.toArray(new Byte[crcPage.size()]);
		// crc16Init();
		short sCrcCalculated = (short) crc16(finalCrcPage);
		if (sCrcCalculated != sCrcReceived) {
			log.debug("Error page crc --> crcReceived " + sCrcReceived
					+ " crcCalculated " + sCrcCalculated);
			if (hGetter.timeout >= 2) {
				hGetter.timeout = 0;
				log.debug("too much retries");
				sendMessageToUI(
						"historic log read aborted! too much crc errors, waiting to retry",
						false);
				return;
			}
			sendMessageToUI(
					"Crc error in page read, reinitializing page read...",
					false);
			hGetter.timeout++;
			hGetter.commandList = Arrays.copyOf(hGetter.commandList,
					hGetter.commandList.length + 1);
			hGetter.commandList[hGetter.commandList.length - 1] = MedtronicConstants.MEDTRONIC_READ_PAGE_COMMAND;
			hGetter.wThread.isRequest = true;
			hGetter.firstReadPage = false;
			hGetter.withoutConfirmation = 0;
			/*
			 * byte[] lastHistoricPage = HexDump.toByteArray(historicPageIndex -
			 * historicPageShift); hGetter.wThread.postCommandBytes = new
			 * byte[64]; Arrays.fill(hGetter.wThread.postCommandBytes,
			 * (byte)0x00); hGetter.wThread.postCommandBytes[0] = 0x04;
			 * hGetter.wThread.postCommandBytes[1] = lastHistoricPage[0];
			 * hGetter.wThread.postCommandBytes[2] = lastHistoricPage[1];
			 * hGetter.wThread.postCommandBytes[3] = lastHistoricPage[2];
			 * hGetter.wThread.postCommandBytes[4] = lastHistoricPage[3];
			 */
			hGetter.isWaitingNextLine = true;
			hGetter.currentLine = -1;
			hGetter.historicPage.clear();
			return;
		}
		log.debug("Success!! page crc --> crcReceived " + sCrcReceived
				+ " crcCalculated " + sCrcCalculated);
		// invertpage and remove 0
		StringBuffer buf = new StringBuffer();
		for (int i = hGetter.historicPage.size() - 1; i >= 0; i--) {
			byte[] hPage = hGetter.historicPage.get(i);
			for (int j = hPage.length - substract; j >= 0; j--) {
				if (hPage[j] == (byte) 0x00 && (substract == 3)) {
					continue;
				}
				substract = 1;
				page.add(hPage[j]);
				buf = buf.append(HexDump.toHexString(hPage[j]));
			}
			buf.append("\n");
		}
		Byte[] invertedPage = page.toArray(new Byte[page.size()]);
		// log.debug("InvertedPage   \n"+buf.toString());
		readHistoricPage(invertedPage);
	}

	/**
	 * Read and translate the Medtronic log page received.
	 * 
	 * @param invertedPage
	 */
	private void readHistoricPage(Byte[] invertedPage) {
		boolean glucoseDataRead = false;
		boolean sensorTimeStampRead = false;
		int glucoseVal = 0;
		StringBuffer sb = null;
		for (int i = 0; i < invertedPage.length; i++) {
			sb = new StringBuffer();
			byte opCommand = HexDump.bUnsignedByte(invertedPage[i]);
			if ((opCommand > 0) && (opCommand < 20)) {
				// Other data
				switch (opCommand) {
				case 0x0001:
					log.debug("    - Data End");
					i += 0;
					break;
				case 0x0002:
					log.debug("    - Sensor Weak Signal");
					i += 0;
					break;
				case 0x0003:
					sb.append("    - Sensor Calibration : ");
					datalog.numEntries++;
					if ((invertedPage[i + 1] & 0x00FF) == 0x00) {
						sb.append(" Not Calibrated");
						datalog.entryType[datalog.numEntries] = 0x03;
					} else {
						sb.append(" Calibrating");
						datalog.entryType[datalog.numEntries] = 0x04;
					}
					log.debug(sb.toString());
					i += 1;
					break;
				case 0x0008:
					int year = 2000 + (invertedPage[i + 4] & 0x00FF);
					int day = (invertedPage[i + 3] & 0x001F);
					int minute = (invertedPage[i + 2] & 0x003F);
					int hour = (invertedPage[i + 1] & 0x001F);
					int month = (((invertedPage[i + 1] >> 6) & 0x0003) << 2)
							+ ((invertedPage[i + 2] >> 6) & 0x0003);
					log.debug("    - Sensor Timestamp: " + day + "-" + month
							+ "-" + year + " " + hour + ":" + minute);
					if (glucoseDataRead)
						sensorTimeStampRead = true;
					i += 4;

					datalog.numEntries++;
					datalog.entryType[datalog.numEntries] = 0x08;
					Calendar cal = Calendar.getInstance();
					cal.set(year, month, day, hour, minute);
					datalog.dateField[datalog.numEntries] = cal.getTime();
					break;
				case 0x000B:
					sb.append("    - Sensor Status : ");
					year = 2000 + (invertedPage[i + 4] & 0x000F);
					day = (invertedPage[i + 3] & 0x001F);
					minute = (invertedPage[i + 2] & 0x003F);
					hour = (invertedPage[i + 1] & 0x001F);
					month = (((invertedPage[i + 1] >> 6) & 0x0003) << 2)
							+ ((invertedPage[i + 2] >> 6) & 0x0003);
					sb.append(day + "-" + month + "-" + year + " " + hour + ":"
							+ minute);
					log.debug(sb.toString());
					i += 4;
					break;
				case 0x000C:
					sb.append("    - Date Time Change : ");
					for (int j = 14; j > 0; j--)
						sb.append(HexDump
								.toHexString(invertedPage[i + j] & 0x00FF));
					log.debug(sb.toString());
					i += 14;
					break;
				case 0x000D:
					sb.append("    - Sensor Sync : ");
					year = 2000 + (invertedPage[i + 4] & 0x000F);
					day = (invertedPage[i + 3] & 0x001F);
					minute = (invertedPage[i + 2] & 0x003F);
					hour = (invertedPage[i + 1] & 0x001F);
					month = (((invertedPage[i + 1] >> 6) & 0x0003) << 2)
							+ ((invertedPage[i + 2] >> 6) & 0x0003);
					sb.append(day + "-" + month + "-" + year + " " + hour + ":"
							+ minute);
					log.debug(sb.toString());
					i += 4;
					break;
				case 0x000E:
					glucoseVal = HexDump.unsignedByte(invertedPage[i + 5]);
					year = 2000 + (invertedPage[i + 4] & 0x000F);
					day = (invertedPage[i + 3] & 0x001F);
					minute = (invertedPage[i + 2] & 0x003F);
					hour = (invertedPage[i + 1] & 0x001F);
					month = (((invertedPage[i + 1] >> 6) & 0x0003) << 2)
							+ ((invertedPage[i + 2] >> 6) & 0x0003);
					sb.append("    - Calibration BG:  " + glucoseVal
							+ " mg/dl - ");
					sb.append(day + "-" + month + "-" + year + " " + hour + ":"
							+ minute);
					log.debug(sb.toString());
					i += 5;
					break;
				case 0x000F:
					sb.append("    - Sensor Calibration Factor : ");
					byte[] value = new byte[2];
					value[0] = 0x00;
					value[1] = 0x00;
					int index = 1;
					for (int j = 6; j > 4; j--) {
						value[index] = invertedPage[i + j];
						index--;
					}
					sb.append("" + HexDump.byteArrayToShort(value));
					year = 2000 + (invertedPage[i + 4] & 0x000F);
					day = (invertedPage[i + 3] & 0x001F);
					minute = (invertedPage[i + 2] & 0x003F);
					hour = (invertedPage[i + 1] & 0x001F);
					month = (((invertedPage[i + 1] >> 6) & 0x0003) << 2)
							+ ((invertedPage[i + 2] >> 6) & 0x0003);
					sb.append("\n" + day + "-" + month + "-" + year + " "
							+ hour + ":" + minute);
					datalog.numEntries++;
					datalog.entryType[datalog.numEntries] = 0x0F;
					cal = Calendar.getInstance();
					cal.set(year, month, day, hour, minute);
					datalog.dateField[datalog.numEntries] = cal.getTime();
					datalog.calFactor[datalog.numEntries] = HexDump
							.byteArrayToShort(value);
					log.debug(sb.toString());
					i += 6;
					break;
				case 0x0010:
					sb.append("    - Bolus : ");
					// for (j=7;j>4;j--)
					// log.debug("%.2X",(invertedPage[i+j]&0x00FF));
					year = 2000 + (invertedPage[i + 4] & 0x00FF);
					day = (invertedPage[i + 3] & 0x001F);
					minute = (invertedPage[i + 2] & 0x003F);
					hour = (invertedPage[i + 1] & 0x001F);
					month = (((invertedPage[i + 1] >> 6) & 0x0003) << 2)
							+ ((invertedPage[i + 2] >> 6) & 0x0003);
					sb.append(day + "-" + month + "-" + year + " " + hour + ":"
							+ minute);
					log.debug(sb.toString());
					i += 7;
					break;
				case 0x0013:
					log.debug("    - Basal Profile Start\n");
					i += 0;
					break;
				default:
					log.debug("    - Unknown (" + HexDump.toHexString(opCommand)
							+ ")");
					i += 0;
					break;
				}
			} else {
				// Glucose data

				glucoseVal = (HexDump.unsignedByte(invertedPage[i])) * 2;
				log.debug("    - Glucose:" + glucoseVal + " mg/dl ("
						+ HexDump.toHexString(invertedPage[i]) + ")");
				glucoseDataRead = true;
				datalog.numEntries++;
				datalog.entryType[datalog.numEntries] = 0x00;
				datalog.glucose[datalog.numEntries] = glucoseVal;
			}
		}
		historicPageShift++;
		hGetter.shift = historicPageShift;
		Date d = new Date();

		long lastRecordTime = 0;
		if (previousRecord != null) {

			lastRecordTime = previousRecord.displayDateTime;
		} else if (settings.contains("lastSensorValueDate")) {
			lastRecordTime = settings.getLong("lastSensorValueDate", 0);
		}

		log.debug("EVAL sensorTimeStampRead " + sensorTimeStampRead
				+ " timeSinceLastRecord " + lastRecordTime + " historicshift "
				+ historicPageShift);
		if ((sensorTimeStampRead && lastRecordTime > 0)
				|| historicPageShift > 1) {
			log.debug("shift: " + historicPageShift);
			int i;
			long actualTime = 0;

			// First drop everything before the first timestamp
			for (i = datalog.numEntries; i > 0 && datalog.entryType[i] != 0x08; i--)
				;
			datalog.numEntries = i;
			log.debug("\n * Number of traceable entries: " + datalog.numEntries);
			MedtronicSensorRecord record = null;
			// Fill missing timestamps
			boolean otherPage = false;
			boolean first = true;

			for (i = datalog.numEntries; i > 0; i--) {
				switch (datalog.entryType[i]) {
				case 0x00:
				case 0x03:
				case 0x04: {
					actualTime += 5 * 60000;
					datalog.dateField[i] = new Date(actualTime);
					// Check times to kow if This record must be uploaded.
					if (lastRecordTime == 0
							|| ((actualTime > lastRecordTime) && (actualTime
									- lastRecordTime > 150000))) {
						log.debug("OK! Upload this record");

						if (first && historicPageShift <= 1) {
							otherPage = true;
						}

						record = new MedtronicSensorRecord();
						record.setBGValue(datalog.glucose[i] + "");
						calculateDate(record, datalog.dateField[i], 0);
						record.isCalibrating = false;
						record.calibrationStatus = MedtronicConstants.CALIBRATED;
						lastRecordsInMemory.add(record);
						calculateTrendAndArrow(record, lastRecordsInMemory);
					} else {
						log.debug("KO!, this record must not be uploaded");
						first = false;
					}
					break;
				}
				case 0x08:
					actualTime = datalog.dateField[i].getTime();
					break;
				default:
					break;
				}
			}

			SharedPreferences.Editor editor = settings.edit();
			editor.putFloat("previousValue", (float) previousValue);
			editor.putInt("calibrationStatus", calibrationStatus);
			lastSensorValueDate = d.getTime();
			editor.putLong("lastSensorValueDate", lastSensorValueDate);
			editor.commit();
			if (record != null) {
				previousRecord = record;
				log.debug("7");
				writeLocalCSV(previousRecord, context);
			}
			if (otherPage) {
				sendMessageToUI("The next page must be read", false);
				log.debug("The next page must be read");
				hGetter.commandList = Arrays.copyOf(hGetter.commandList,
						hGetter.commandList.length + 2);
				hGetter.commandList[hGetter.commandList.length - 2] = MedtronicConstants.MEDTRONIC_ACK;
				hGetter.commandList[hGetter.commandList.length - 1] = MedtronicConstants.MEDTRONIC_READ_PAGE_COMMAND;
				hGetter.wThread.isRequest = true;
				hGetter.firstReadPage = true;
				hGetter.withoutConfirmation = 1;
				/*
				 * byte[] lastHistoricPage =
				 * HexDump.toByteArray(historicPageIndex - historicPageShift);
				 * hGetter.wThread.postCommandBytes = new byte[64];
				 * Arrays.fill(hGetter.wThread.postCommandBytes, (byte)0x00);
				 * hGetter.wThread.postCommandBytes[0] = 0x04;
				 * hGetter.wThread.postCommandBytes[1] = lastHistoricPage[0];
				 * hGetter.wThread.postCommandBytes[2] = lastHistoricPage[1];
				 * hGetter.wThread.postCommandBytes[3] = lastHistoricPage[2];
				 * hGetter.wThread.postCommandBytes[4] = lastHistoricPage[3];
				 */
				hGetter.isWaitingNextLine = true;
				hGetter.currentLine = -1;
				hGetter.historicPage.clear();
				return;
			}
			hGetter.commandList = Arrays.copyOf(hGetter.commandList,
					hGetter.commandList.length + 2);
			hGetter.commandList[hGetter.commandList.length - 2] = MedtronicConstants.MEDTRONIC_ACK;
			hGetter.commandList[hGetter.commandList.length - 1] = MedtronicConstants.MEDTRONIC_INIT;
			hGetter.withoutConfirmation = 1;
			hGetter.wThread.isRequest = true;
			hGetter.wThread.postCommandBytes = null;

			// clear vars and EXIT!!!
			sendMessageToUI("historic log has been read", false);
			// hGetter.init();
			historicPageIndex = -1;
			historicPageShift = 0;
			datalog = new DataLog();
			SharedPreferences.Editor editor2 = prefs.edit();
			editor2.putLong("lastHistoricRead", System.currentTimeMillis());
			editor2.commit();
			if (mHandlerCheckLastRead != null && checkLastRead != null)
				mHandlerCheckLastRead.postDelayed(checkLastRead,
						MedtronicConstants.TIME_10_MIN_IN_MS);

		} else {
			sendMessageToUI("The next page must be read", false);
			log.debug("The next page must be read");
			hGetter.commandList = Arrays.copyOf(hGetter.commandList,
					hGetter.commandList.length + 2);
			hGetter.commandList[hGetter.commandList.length - 2] = MedtronicConstants.MEDTRONIC_ACK;
			hGetter.commandList[hGetter.commandList.length - 1] = MedtronicConstants.MEDTRONIC_READ_PAGE_COMMAND;
			hGetter.wThread.isRequest = true;
			hGetter.firstReadPage = true;
			hGetter.withoutConfirmation = 1;
			hGetter.isWaitingNextLine = true;
			hGetter.currentLine = -1;
			hGetter.historicPage.clear();
		}
	}

	/**
	 * This method process the glucometer messages
	 * 
	 * @param readData
	 * @return String, for debug or notification purposes
	 */
	public String processGlucometerDataMessage(byte[] readData,
			boolean calibrate) {
		int firstMeasureByte = firstByteAfterDeviceId(readData);
		if (firstMeasureByte < 0)
			return "Error, I can not identify the initial byte of the glucometer measure";
		int numBytes = ByteBuffer.wrap(
				new byte[] { (byte) 0x00, (byte) 0x00, (byte) 0x00,
						(byte) readData[1] }).getInt();
		if (firstMeasureByte > readData.length || numBytes > readData.length)
			return "Error, I have detected an error in glucometer message size";
		byte[] arr = Arrays.copyOfRange(readData, firstMeasureByte,
				numBytes + 1);
		byte[] res = new byte[4];
		if (arr.length < 4) {
			for (int j = 0; j < 4; j++) {
				res[j] = (byte) 0x00;
				if (j >= 4 - arr.length)
					res[j] = arr[Math.abs(4 - j - arr.length)];
			}
		} else
			res = arr;
		ByteBuffer wrapped = ByteBuffer.wrap(res);
		int num = wrapped.getInt(); // 1
		if (num < 0 || num > 1535)
			return "Glucometer value under 0 or over 0x5ff. Possible ACK or malfunction.";
		
		processManualCalibrationDataMessage(num, true, calibrate);
		//int calibrationSelectedAux = 0;
		//synchronized (calibrationSelectedLock) {
			//calibrationSelectedAux = calibrationSelected;
		//}
		//sendGlucMessageToUI(num, calibrate, calibrationSelectedAux == MedtronicConstants.CALIBRATION_SENSOR);
		return "Measure received " + num + " mg/dl";
	}

	
	
	public void approveGlucValueForCalibration(float num, boolean calibrate, boolean isSensorFactorFromPump){
		if (!isSensorFactorFromPump)
			processManualCalibrationDataMessage(num, false, calibrate);
		else{
			sendMessageToUI(
					"Glucometer Detected!!..Waiting 15 min. to retrieve calibration factor...",
					false);
			log.debug("Glucometer Detected!!..Waiting 15 min. to retrieve calibration factor...");
			if (mHandlerSensorCalibration != null
					&& getCalibrationFromSensor != null) {
				mHandlerSensorCalibration
				.removeCallbacks(getCalibrationFromSensor);
				mHandlerSensorCalibration
				.postDelayed(
						getCalibrationFromSensor,
						MedtronicConstants.TIME_15_MIN_IN_MS + 120000);
			} else
				log.debug("glucometer handler or glucometer runnable is null");
			lastGlucometerRecord = new GlucometerRecord();
			lastGlucometerRecord.numGlucometerValue = num;
			lastGlucometerValue = num;
			Date d = new Date();
			lastGlucometerRecord.lastDate = d.getTime();
			lastGlucometerDate = d.getTime();
			calculateDate(lastGlucometerRecord, d, 0);
			SharedPreferences.Editor editor = settings.edit();
			editor.putFloat("lastGlucometerValue", (float) lastGlucometerValue);
			editor.putLong("glucometerLastDate", d.getTime());
			editor.commit();
		}
	}
	/**
	 * This method process the Manual Calibration message
	 * 
	 * @param readData
	 * @return String, for debug or notification purposes
	 */
	public String processManualCalibrationDataMessage(float value,
			boolean instant, boolean doCalibration) {
		float mult = 1f;
		//if (prefs.getBoolean("mmolxl", false))
			//mult = 1f/18f;
		float num = value * mult;
		lastGlucometerRecord = new GlucometerRecord();
		lastGlucometerRecord.numGlucometerValue = num;
		lastGlucometerValue = num;
		Date d = new Date();
		lastGlucometerRecord.lastDate = d.getTime();
		lastGlucometerDate = d.getTime();
		calculateDate(lastGlucometerRecord, d, 0);
		if (!instant && doCalibration) {
			if (HexDump.unsignedByte(expectedSensorSortNumber) == HexDump
					.unsignedByte((byte) 0xff)) {
				expectedSensorSortNumberForCalibration[0] = (byte) 0x00;
				expectedSensorSortNumberForCalibration[1] = (byte) 0x71;
			} else {
				synchronized (expectedSensorSortNumberLock) {
					byte expectedAux = expectedSensorSortNumber;
					if (HexDump
							.unsignedByte((byte) (expectedSensorSortNumber & (byte) 0x01)) > 0)
						expectedAux = (byte) (expectedSensorSortNumber & (byte) 0xFE);
					expectedSensorSortNumberForCalibration[0] = calculateNextSensorSortNameFrom(
							6, expectedAux);
					expectedSensorSortNumberForCalibration[1] = calculateNextSensorSortNameFrom(
							10, expectedAux);
				}
			}
		}
		SharedPreferences.Editor editor = settings.edit();
		editor.putFloat("lastGlucometerValue", (float) num);
		editor.putLong("glucometerLastDate", d.getTime());
		if (!instant && doCalibration) {
			editor.putString("expectedSensorSortNumberForCalibration0", HexDump
					.toHexString(expectedSensorSortNumberForCalibration[0]));
			editor.putString("expectedSensorSortNumberForCalibration1", HexDump
					.toHexString(expectedSensorSortNumberForCalibration[1]));
		} else {
			editor.remove("expectedSensorSortNumberForCalibration0");
			editor.remove("expectedSensorSortNumberForCalibration1");
		}
		if (lastGlucometerValue > 0) {
			isCalibrating = !instant && doCalibration;
			if (previousRecord == null) {
				MedtronicSensorRecord auxRecord = new MedtronicSensorRecord();
				auxRecord.isCalibrating = !instant;
				log.debug("8");
				writeLocalCSV(auxRecord, context);
			} else {
				previousRecord.isCalibrating = !instant;
				log.debug("9");
				writeLocalCSV(previousRecord, context);
			}
			editor.putBoolean("isCalibrating", !instant);
			editor.commit();
		}
		editor.commit();
		return "Measure received " + num + " mg/dl";
	}

	/**
	 * Apply calibration factor to a value in "index" position of the sensor
	 * message
	 * 
	 * @param previousCalibrationFactor
	 * @param previousCalibrationStatus
	 * @param isig
	 * @param record
	 * @param added
	 * @param currentTime
	 */
	private void calibratingBackwards(float previousCalibrationFactor,
			int previousCalibrationStatus, float isig,
			MedtronicSensorRecord record, int added, Date currentTime) {
		List<Record> auxList = null;
		synchronized (lastRecordsListLock) {
			auxList = lastRecordsInMemory.getListFromTail(2);
		}
		if (previousCalibrationFactor > 0) {
			if (previousCalibrationStatus != MedtronicConstants.WITHOUT_ANY_CALIBRATION) {
				record.setUnfilteredGlucose(isig * previousCalibrationFactor);
				record.setBGValue((applyFilterToRecord(record, auxList)) + "");
				record.isCalibrating = false;
				record.calibrationFactor = previousCalibrationFactor;
				record.calibrationStatus = previousCalibrationStatus;
			} else {
				record.setUnfilteredGlucose(isig * previousCalibrationFactor);
				record.setBGValue((applyFilterToRecord(record, auxList)) + "");
				record.isCalibrating = false;
				record.calibrationFactor = previousCalibrationFactor;
				record.calibrationStatus = MedtronicConstants.LAST_CALIBRATION_FAILED_USING_PREVIOUS;
			}
		}
		calculateDate(record, currentTime, added);
	}

	/**
	 * Apply calibration to the "current" value of the sensor message
	 * 
	 * @param difference
	 * @param isig
	 * @param readData
	 * @param index
	 * @param record
	 * @param num
	 * @param currentTime
	 */
	private void calibratingCurrentElement(long difference, float isig,
			byte[] readData, int index, MedtronicSensorRecord record, int num,
			Date currentTime) {
		boolean calibrated = false;
		// currentMeasure = num;
		if (isCalibrating) {
			if (num > 0) {
				calculateCalibration(difference, isig, readData[index]);
				if (calibrationFactor > 0) {
					if (!isCalibrating) {
						if (calibrationStatus != MedtronicConstants.WITHOUT_ANY_CALIBRATION
								&& calibrationStatus != MedtronicConstants.LAST_CALIBRATION_FAILED_USING_PREVIOUS
								&& calibrationStatus != MedtronicConstants.CALIBRATION_MORE_THAN_12H_OLD) {
							record.setBGValue(((int) lastGlucometerValue) + "");
							record.setUnfilteredGlucose(lastGlucometerValue);
							record.calibrationFactor = calibrationFactor;
							record.isCalibrating = false;
							record.calibrationStatus = calibrationStatus;
							lastCalibrationDate = currentTime.getTime();
							SharedPreferences.Editor editor = settings.edit();
							log.debug("change lastCalibrationDate");
							editor.putLong("lastCalibrationDate",
									lastCalibrationDate);
							editor.commit();
							calibrated = true;
						}
					}
				}
			}
		}
		if (calibrationFactor > 0 && !calibrated) {
			List<Record> auxList = null;
			synchronized (lastRecordsListLock) {
				auxList = lastRecordsInMemory.getListFromTail(2);
			}
			if (calibrationStatus != MedtronicConstants.WITHOUT_ANY_CALIBRATION) {
				record.setUnfilteredGlucose(isig * calibrationFactor);
				record.setBGValue((applyFilterToRecord(record, auxList)) + "");
				record.isCalibrating = false;
				record.calibrationFactor = calibrationFactor;
				record.calibrationStatus = calibrationStatus;
			} else {
				record.setUnfilteredGlucose(isig * calibrationFactor);
				record.setBGValue((applyFilterToRecord(record, auxList)) + "");
				record.isCalibrating = false;
				record.calibrationFactor = calibrationFactor;
				record.calibrationStatus = MedtronicConstants.LAST_CALIBRATION_FAILED_USING_PREVIOUS;
			}
		}
		calculateDate(record, currentTime, 0);
		previousRecord = record;
	}

	public void calculateInstantCalibration(float currentMeasure) {
		log.debug("Instant Calibration");
		if (previousRecord != null && previousRecord.isig != 0) {
			log.debug("I  have isig " + previousRecord.isig);
			calibrationFactor = currentMeasure / previousRecord.isig;
			log.debug("Instant Calibration result " + calibrationFactor);
			if (calibrationFactor > 0) {
				previousRecord.bGValue = "" + ((int) currentMeasure);
				log.debug("Instant Calibration Success!! ");
				calibrationStatus = MedtronicConstants.CALIBRATED;
				lastCalibrationDate = System.currentTimeMillis();
				isCalibrating = false;
				previousRecord.isCalibrating = false;
				previousRecord.calibrationStatus = calibrationStatus;
				log.debug("10");
				writeLocalCSV(previousRecord, context);
				SharedPreferences.Editor editor = settings.edit();
				log.debug("change instant lastCalibrationDate");
				editor.putLong("lastCalibrationDate", lastCalibrationDate);
				editor.putBoolean("isCalibrating", false);
				editor.putFloat("calibrationFactor", (float) calibrationFactor);
				editor.putInt("calibrationStatus",
							calibrationStatus);
				editor.commit();
			}
			return;
		} else{
			sendErrorMessageToUI("I can't calibrate, I don't have any ISIG stored yet.");
			log.debug("I dont have isig");
		}
		if (previousRecord == null)
			previousRecord = new MedtronicSensorRecord();
		if (calibrationStatus != MedtronicConstants.WITHOUT_ANY_CALIBRATION
				&& calibrationFactor != -1f) {
			calibrationStatus = MedtronicConstants.LAST_CALIBRATION_FAILED_USING_PREVIOUS;
		} else {
			calibrationStatus = MedtronicConstants.WITHOUT_ANY_CALIBRATION;
		}
		previousRecord.calibrationStatus = calibrationStatus;
		log.debug("11");
		writeLocalCSV(previousRecord, context);
		log.debug("Instant Calibration Failure!! ");
	}

	/**
	 * This method process the sensor answers
	 * 
	 * @param readData
	 * @return String, for debug or notification purposes
	 */
	public String processSensorDataMessage(byte[] readData) {
		Date d = new Date();
		long difference = 0;
		if (isCalibrating && lastGlucometerDate > 0) {
			difference = d.getTime() - lastGlucometerDate;
		}

		int added = 8;
		int firstMeasureByte = firstByteAfterDeviceId(readData);
		int currentMeasure = -1;
		float isig = 0;
		StringBuffer sResult = new StringBuffer("");
		if (firstMeasureByte < 0)
			return "Error, I can not identify the initial byte of the sensor measure";
		int numBytes = HexDump.unsignedByte(readData[1]);
		if (firstMeasureByte > readData.length || numBytes > readData.length
				|| numBytes <= 0)
			return "Error, I have detected an error in sensor message size";
		int previousCalibrationStatus = calibrationStatus;
		float previousCalibrationFactor = calibrationFactor;
		short adjustement = (short) readData[firstMeasureByte + 2];
		long firstTimeOut = d.getTime() - lastSensorValueDate;
		if (HexDump.unsignedByte(expectedSensorSortNumber) == HexDump
				.unsignedByte((byte) 0xff)
				|| firstTimeOut == d.getTime()
				|| (firstTimeOut > MedtronicConstants.TIME_10_MIN_IN_MS
						+ MedtronicConstants.TIME_30_MIN_IN_MS)) {
			Log.i("Medtronic", "First");
			log.debug("SENSOR MEASURE, First Time, retrieving all previous measures");
			lastElementsAdded = 0;
			// I must read ALL THE MEASURES
			synchronized (expectedSensorSortNumberLock) {
				expectedSensorSortNumber = readData[firstMeasureByte + 3];
			}

			for (int i = 20; i >= 0; i -= 2) {
				if (i >= 4 && i < 8) {
					continue;
				}
				lastElementsAdded++;
				byte[] arr = Arrays.copyOfRange(readData, firstMeasureByte + 4
						+ i, firstMeasureByte + 6 + i);
				byte[] res = new byte[4];
				if (arr.length < 4) {
					for (int j = 0; j < 4; j++) {
						res[j] = (byte) 0x00;
						if (j >= 4 - arr.length)
							res[j] = arr[Math.abs(4 - j - arr.length)];
					}
				} else
					res = arr;
				ByteBuffer wrapped = ByteBuffer.wrap(res);
				int num = wrapped.getInt(); // 1
				MedtronicSensorRecord record = new MedtronicSensorRecord();
				record.isCalibrating = isCalibrating;
				isig = calculateISIG(num, adjustement);
				record.setIsig(isig);
				if (i == 0) {
					currentMeasure = num;
					calibratingCurrentElement(difference, isig, readData,
							firstMeasureByte + 3, record, num, d);
				} else {
					calibratingBackwards(previousCalibrationFactor,
							previousCalibrationStatus, isig, record, added, d);
				}
				added--;
				lastRecordsInMemory.add(record);
				calculateTrendAndArrow(record, lastRecordsInMemory);
				sResult.append("Measure(").append(((i + 2) / 2)).append("): ")
				.append(num);
			}

		} else {
			log.debug("Estoy Esperando "
					+ HexDump.toHexString(expectedSensorSortNumber)
					+ " He recibido "
					+ HexDump.toHexString(readData[firstMeasureByte + 3]));
			if (HexDump.unsignedByte(expectedSensorSortNumber) == HexDump
					.unsignedByte(readData[firstMeasureByte + 3])
					|| HexDump.unsignedByte(calculateNextSensorSortNameFrom(1,
							expectedSensorSortNumber)) == HexDump
							.unsignedByte(readData[firstMeasureByte + 3])) {
				Log.i("Medtronic", "Expected sensor number received!!");
				log.debug("SENSOR MEASURE, Expected sensor measure received!!");
				lastElementsAdded = 0;
				// I must read only the first value except if byte ends in "1"
				// then I skip this value
				if (!isSensorRepeatedMessage(readData[firstMeasureByte + 3])
						|| HexDump
						.unsignedByte((byte) ((byte) expectedSensorSortNumber & (byte) 0x01)) < 1
						&& HexDump
						.unsignedByte((byte) ((byte) readData[firstMeasureByte + 3] & (byte) 0x01)) == 1) {
					byte[] arr = Arrays.copyOfRange(readData,
							firstMeasureByte + 4, firstMeasureByte + 6);
					byte[] res = new byte[4];
					if (arr.length < 4) {
						for (int j = 0; j < 4; j++) {
							res[j] = (byte) 0x00;
							if (j >= 4 - arr.length)
								res[j] = arr[Math.abs(4 - j - arr.length)];
						}
					} else
						res = arr;
					ByteBuffer wrapped = ByteBuffer.wrap(res);
					int num = wrapped.getInt(); // 1
					MedtronicSensorRecord record = new MedtronicSensorRecord();
					isig = calculateISIG(num, adjustement);
					record.setIsig(isig);
					record.isCalibrating = isCalibrating;
					currentMeasure = num;
					calibratingCurrentElement(difference, isig, readData,
							firstMeasureByte + 3, record, num, d);
					lastRecordsInMemory.add(record);
					calculateTrendAndArrow(record, lastRecordsInMemory);
					sResult.append("last measure: ").append(num);
					lastElementsAdded++;
				} else {
					// sendMessageToUI("ES REPETIDO NO LO EVALUO ", false);
					synchronized (expectedSensorSortNumberLock) {
						expectedSensorSortNumber = calculateNextSensorSortNameFrom(
								1, expectedSensorSortNumber);
					}
					return sResult.toString();
				}
			} else {
				Log.i("Medtronic", "NOT Expected sensor number received!!");
				log.debug("SENSOR MEASURE, NOT Expected sensor measure received!!");
				int dataLost = -1;
				if (previousRecord != null || lastSensorValueDate > 0) {
					long timeDiff = 0;
					if (previousRecord != null)
						timeDiff = d.getTime() - previousRecord.displayDateTime;
					else
						timeDiff = d.getTime() - lastSensorValueDate;
					if (timeDiff > (MedtronicConstants.TIME_30_MIN_IN_MS + MedtronicConstants.TIME_10_MIN_IN_MS)) {
						dataLost = 10;
						added = 8;
					} else {
						int valPrev = transformSequenceToIndex(expectedSensorSortNumber);
						int currentVal = transformSequenceToIndex(readData[firstMeasureByte + 3]);
						if (valPrev > currentVal)
							currentVal = 8 + currentVal;
						dataLost = (currentVal - (valPrev)) % 9;
						if (dataLost < 0)
							dataLost *= -1;
						dataLost--;
						added = dataLost;
						Log.i("medtronic", " valPrev " + valPrev
								+ " currentVal " + currentVal + " dataLost "
								+ dataLost + " added " + added);
					}
				} else {
					dataLost = 10;
					added = 8;
				}
				Log.i("Medtronic", "Data Lost " + dataLost);
				if (dataLost >= 0) {
					if (dataLost >= 2)
						dataLost += 2;
					if (dataLost > 10) {
						dataLost = 10;
						added = 8;
					}
					log.debug("SENSOR MEASURE, I am going to retrieve "
							+ (dataLost) + " previous values");
					dataLost *= 2;
					lastElementsAdded = 0;
					// I must read ALL THE MEASURES
					if (dataLost == 20 || dataLost == 0) {
						synchronized (expectedSensorSortNumberLock) {
							expectedSensorSortNumber = readData[firstMeasureByte + 3];
						}
					}

					for (int i = dataLost; i >= 0; i -= 2) {
						if (i >= 4 && i < 8) {
							continue;
						}
						lastElementsAdded++;
						byte[] arr = Arrays.copyOfRange(readData,
								firstMeasureByte + 4 + i, firstMeasureByte + 6
								+ i);
						byte[] res = new byte[4];
						if (arr.length < 4) {
							for (int j = 0; j < 4; j++) {
								res[j] = (byte) 0x00;
								if (j >= 4 - arr.length)
									res[j] = arr[Math.abs(4 - j - arr.length)];
							}
						} else
							res = arr;
						ByteBuffer wrapped = ByteBuffer.wrap(res);
						int num = wrapped.getInt(); // 1
						MedtronicSensorRecord record = new MedtronicSensorRecord();
						record.isCalibrating = isCalibrating;
						isig = calculateISIG(num, adjustement);
						record.setIsig(isig);
						if (i == 0) {
							currentMeasure = num;
							calibratingCurrentElement(difference, isig,
									readData, firstMeasureByte + 3, record,
									num, d);
						} else {
							calibratingBackwards(previousCalibrationFactor,
									previousCalibrationStatus, isig, record,
									added, d);
						}
						added--;
						lastRecordsInMemory.add(record);
						calculateTrendAndArrow(record, lastRecordsInMemory);
						sResult.append("Measure(").append(((i + 2) / 2))
						.append("): ").append(num);
					}
				} else {
					byte[] arr = Arrays.copyOfRange(readData,
							firstMeasureByte + 4, firstMeasureByte + 6);
					byte[] res = new byte[4];
					if (arr.length < 4) {
						for (int j = 0; j < 4; j++) {
							res[j] = (byte) 0x00;
							if (j >= 4 - arr.length)
								res[j] = arr[Math.abs(4 - j - arr.length)];
						}
					} else
						res = arr;
					ByteBuffer wrapped = ByteBuffer.wrap(res);
					int num = wrapped.getInt(); // 1
					MedtronicSensorRecord record = new MedtronicSensorRecord();
					isig = calculateISIG(num, adjustement);
					record.setIsig(isig);
					record.isCalibrating = isCalibrating;
					currentMeasure = num;
					calibratingCurrentElement(difference, isig, readData,
							firstMeasureByte + 3, record, num, d);
					lastRecordsInMemory.add(record);
					calculateTrendAndArrow(record, lastRecordsInMemory);
					sResult.append("last measure: ").append(num);
					lastElementsAdded++;
				}
			}
			Log.i("Medtronic", "Fill next expected");
			expectedSensorSortNumber = readData[firstMeasureByte + 3];
		}
		previousValue = currentMeasure;
		// I must recalculate next message!!!!
		synchronized (expectedSensorSortNumberLock) {
			expectedSensorSortNumber = calculateNextSensorSortNameFrom(1,
					expectedSensorSortNumber);
		}

		SharedPreferences.Editor editor = settings.edit();
		editor.putFloat("previousValue", (float) previousValue);
		editor.putString("expectedSensorSortNumber",
				HexDump.toHexString(expectedSensorSortNumber));
		editor.putInt("calibrationStatus", calibrationStatus);
		lastSensorValueDate = d.getTime();
		editor.putLong("lastSensorValueDate", lastSensorValueDate);
		editor.commit();
		log.debug("12");
		writeLocalCSV(previousRecord, context);
		Log.i("Medtronic", "BYE!!!!");
		log.debug("sensorprocessed end expected "
				+ HexDump.toHexString(expectedSensorSortNumber));
		return sResult.toString();
	}

	/**
	 * This method saves a file with the last Record read from the device
	 * 
	 * @param mostRecentData
	 *            , Record to save.
	 * @param context
	 *            , Application context.
	 */
	private void writeLocalCSV(MedtronicSensorRecord mostRecentData,
			Context context) {

		// Write EGV Binary of last (most recent) data
		try {
			if (mostRecentData == null || mostRecentData.bGValue == null)
				log.debug("writeLocalCSV SAVING  EMPTY!!");
			else
				log.debug("writeLocalCSV SAVING --> " + mostRecentData.bGValue);
			ObjectOutputStream oos = new ObjectOutputStream(
					new FileOutputStream(new File(context.getFilesDir(),
							"save.bin"))); // Select where you wish to save the
			// file...
			oos.writeObject(mostRecentData); // write the class as an 'object'
			oos.flush(); // flush the stream to insure all of the information
			// was written to 'save.bin'
			oos.close();// close the stream
		} catch (Exception e) {
			Log.e(TAG, "write to OutputStream failed", e);
			log.error("write to OutputStream failed", e);
		}
	}

	/**
	 * Checks if the message received is the expected redundant message.
	 * 
	 * @param sortID
	 * @return true, if is the redundant message
	 */
	private boolean isSensorRepeatedMessage(byte sortID) {
		String sExpected = HexDump.toHexString(expectedSensorSortNumber);
		String sSortId = HexDump.toHexString(sortID);
		if (sExpected != null && sSortId != null
				&& sExpected.length() == sSortId.length()
				&& sExpected.length() >= 2) {
			return (sExpected.charAt(0) == sSortId.charAt(0))
					&& (sSortId.charAt(1) == '1');
		} else
			return false;
	}

	/**
	 * The messages emitted from the sensor are sorted following the pattern:
	 * order byte - content ================================= 00 - message 01 -
	 * same message as 00 10 - message2 11 - same message2 as 10 20 - message3
	 * 21 - same message3 as 20 ... ... 60 - message6 61 - same message6 as 60
	 * 70 - message7 71 - same message7 as 70 00 - message8 01 - same message8
	 * as 00 ... ...
	 * 
	 * @return next order to be expected
	 */
	private byte calculateNextSensorSortNameFrom(int shift,
			byte expectedSensorSortNumber) {
		// sendMessageToUI("calculating FROM "+HexDump.toHexString(expectedSensorSortNumber),
		// false);
		byte aux = expectedSensorSortNumber;
		String sExpected = HexDump.toHexString(aux);
		if (sExpected != null && sExpected.length() >= 2) {

			while (shift > 0) {
				sExpected = HexDump.toHexString(aux);
				char sort1 = sExpected.charAt(0);

				boolean repeated = sExpected.charAt(1) == '1';
				switch (sort1) {
				case '0':
					aux = (byte) 0x10;
					if (!repeated)
						aux = (byte) 0x01;
					break;
				case '1':
					aux = (byte) 0x20;
					if (!repeated)
						aux = (byte) 0x11;
					break;
				case '2':
					aux = (byte) 0x30;
					if (!repeated)
						aux = (byte) 0x21;
					break;
				case '3':
					aux = (byte) 0x40;
					if (!repeated)
						aux = (byte) 0x31;
					break;
				case '4':
					aux = (byte) 0x50;
					if (!repeated)
						aux = (byte) 0x41;
					break;
				case '5':
					aux = (byte) 0x60;
					if (!repeated)
						aux = (byte) 0x51;
					break;
				case '6':
					aux = (byte) 0x70;
					if (!repeated)
						aux = (byte) 0x61;
					break;
				case '7':
					aux = (byte) 0x00;
					if (!repeated)
						aux = (byte) 0x71;
					break;
				default:
					aux = (byte) 0xff;
				}
				shift--;
			}
			return aux;
		} else
			return (byte) 0xff;
	}

	private int transformSequenceToIndex(byte aux) {
		String sExpected = HexDump.toHexString(aux);
		char sort1 = sExpected.charAt(0);
		int result = Integer.parseInt("" + sort1);
		if (result == 0)
			result = 8;
		return result;
	}

	/**
	 * This function checks if the measure index is between a range of indexes
	 * previously stored.
	 * 
	 * @param measureIndex
	 *            , index to check
	 * @param range
	 *            ,
	 * @return true if the measure index is between a range of indexes
	 *         previously stored.
	 */
	private boolean isSensorMeasureInRange(byte measureIndex, byte[] range) {
		byte minRange = range[0];
		byte maxRange = range[1];
		if (HexDump.unsignedByte(maxRange) < HexDump.unsignedByte(minRange)) {
			return ((HexDump.unsignedByte(measureIndex) >= HexDump
					.unsignedByte(minRange)) && (HexDump
							.unsignedByte(measureIndex) <= HexDump
							.unsignedByte((byte) 0x71)))
							|| (HexDump.unsignedByte(measureIndex) <= HexDump
							.unsignedByte(maxRange))
							&& (HexDump.unsignedByte(measureIndex) >= HexDump
							.unsignedByte((byte) 0x00));
		} else {
			return (HexDump.unsignedByte(measureIndex) >= HexDump
					.unsignedByte(minRange))
					&& (HexDump.unsignedByte(measureIndex) <= HexDump
					.unsignedByte(maxRange));
		}
	}

	/**
	 * method to store the current status of my known devices. this info will be
	 * used to restore status.
	 */
	public void storeKnownDevices() {
		StringBuffer listKnownDevices = new StringBuffer();
		boolean first = true;
		for (String id : knownDevices) {
			if (id.length() > 0) {
				if (!first)
					listKnownDevices = listKnownDevices.append(",");
				else
					first = false;
				listKnownDevices = listKnownDevices.append(id);
			}
		}
		SharedPreferences.Editor editor = settings.edit();
		editor.putString("knownDevices", listKnownDevices.toString());
		editor.commit();
	}

	/**
	 * This method calculates the date of the sensor readings
	 * 
	 * @param record
	 *            , current sensor reading
	 * @param initTime
	 *            , time of the first (most actual) reading in this row
	 * @param substract
	 *            , index of this reading respectively the initTime reading.
	 *            Each increment subtracts 5 minutes to "initTime"
	 */
	public void calculateDate(Record record, Date initTime, int subtract) {
		Date d = initTime;

		long milliseconds = d.getTime();

		if (subtract > 0) {
			milliseconds -= subtract * MedtronicConstants.TIME_5_MIN_IN_MS;// record
			// was
			// read
			// (subtract
			// *
			// 5
			// minutes) before the initTime
		}

		long timeAdd = milliseconds;

		/*
		 * TimeZone tz = TimeZone.getDefault();
		 * 
		 * if (!tz.inDaylightTime(new Date())) timeAdd = timeAdd - 3600000L;
		 */
		Date display = new Date(timeAdd);
		String displayTime = new SimpleDateFormat("MM/dd/yyy hh:mm:ss aa",
				Locale.getDefault()).format(display);
		record.displayTime = displayTime;
		if (record instanceof MedtronicSensorRecord) {
			((MedtronicSensorRecord) record).displayDateTime = display
					.getTime();
		}
	}

	/**
	 * This method checks if a calibration is valid.
	 * 
	 * @param difference
	 * @param currentMeasure
	 * @param currentIndex
	 */
	private void calculateCalibration(long difference, float currentMeasure,
			byte currentIndex) {
		if (difference >= MedtronicConstants.TIME_15_MIN_IN_MS
				&& difference < MedtronicConstants.TIME_20_MIN_IN_MS) {
			if (isSensorMeasureInRange(currentIndex,
					expectedSensorSortNumberForCalibration)) {
				isCalibrating = false;
				calibrationStatus = MedtronicConstants.CALIBRATED;
				calibrationIsigValue = currentMeasure;
				SharedPreferences.Editor editor = settings.edit();
				calibrationFactor = lastGlucometerValue / calibrationIsigValue;
				editor.remove("expectedSensorSortNumberForCalibration0");
				editor.remove("expectedSensorSortNumberForCalibration1");
				editor.putFloat("calibrationFactor", (float) calibrationFactor);
				editor.putInt("calibrationStatus",
						calibrationStatus);
				editor.commit();
			} else {
				if (calibrationStatus != MedtronicConstants.WITHOUT_ANY_CALIBRATION
						&& currentIndex != expectedSensorSortNumber) {
					calibrationStatus = MedtronicConstants.LAST_CALIBRATION_FAILED_USING_PREVIOUS;
					isCalibrating = false;
				} else {
					calibrationStatus = MedtronicConstants.WITHOUT_ANY_CALIBRATION;
				}
				SharedPreferences.Editor editor = settings.edit();
				editor.remove("expectedSensorSortNumberForCalibration0");
				editor.remove("expectedSensorSortNumberForCalibration1");
				editor.commit();
			}
		} else if (difference >= MedtronicConstants.TIME_20_MIN_IN_MS) {
			if (isSensorMeasureInRange(currentIndex,
					expectedSensorSortNumberForCalibration)) {
				calibrationStatus = MedtronicConstants.CALIBRATED_IN_15MIN;
				calibrationIsigValue = currentMeasure;
				SharedPreferences.Editor editor = settings.edit();
				calibrationFactor = lastGlucometerValue / calibrationIsigValue;
				editor.remove("expectedSensorSortNumberForCalibration0");
				editor.remove("expectedSensorSortNumberForCalibration1");
				editor.putFloat("calibrationFactor", (float) calibrationFactor);
				editor.putInt("calibrationStatus",
						calibrationStatus);
				editor.commit();
			} else {
				if (calibrationStatus != MedtronicConstants.WITHOUT_ANY_CALIBRATION)
					calibrationStatus = MedtronicConstants.LAST_CALIBRATION_FAILED_USING_PREVIOUS;
				else {
					calibrationStatus = MedtronicConstants.WITHOUT_ANY_CALIBRATION;
				}
				SharedPreferences.Editor editor = settings.edit();
				editor.remove("expectedSensorSortNumberForCalibration0");
				editor.remove("expectedSensorSortNumberForCalibration1");
				editor.commit();
			}
			isCalibrating = false;
		} else {
			if (isCalibrating){
				if (difference < MedtronicConstants.TIME_5_MIN_IN_MS) {
					calibrationStatus = MedtronicConstants.CALIBRATING;
				} else if (difference >= MedtronicConstants.TIME_5_MIN_IN_MS
						&& difference <= MedtronicConstants.TIME_15_MIN_IN_MS)
					calibrationStatus = MedtronicConstants.CALIBRATING2;
				else
					calibrationStatus = MedtronicConstants.CALIBRATING;
			}else{
				if (calibrationStatus != MedtronicConstants.WITHOUT_ANY_CALIBRATION)
					calibrationStatus = MedtronicConstants.LAST_CALIBRATION_FAILED_USING_PREVIOUS;
				else {
					calibrationStatus = MedtronicConstants.WITHOUT_ANY_CALIBRATION;
				}
				SharedPreferences.Editor editor = settings.edit();
				editor.remove("expectedSensorSortNumberForCalibration0");
				editor.remove("expectedSensorSortNumberForCalibration1");
				editor.putInt("calibrationStatus",
						calibrationStatus);
				editor.commit();
			}
		}
	}

	/**
	 * Function which helps to calculate the difference of Glucose.
	 * 
	 * @param size
	 *            , amount of records to use (aprox. 5 min between records)
	 * @param list
	 *            , list of records.
	 * @return, Total Glucose variation.
	 */
	public Float getGlucoseDifferentialIn(int size, CircleList<Record> list) {
		List<Record> auxList = list.getListFromTail(size);
		SimpleDateFormat formatter = new SimpleDateFormat(
				"MM/dd/yyyy hh:mm:ss a", Locale.getDefault());
		if (auxList.size() == size) {
			log.debug("I Have the correct size");
			for (int i = 1; i < size; i++) {
				if (!(auxList.get(i) instanceof MedtronicSensorRecord)) {
					log.debug("but not the correct records");
					return null;
				}
			}
			float diff = 0;
			long dateDif = 0;
			for (int i = 1; i < size; i++) {
				log.debug("Start calculate diff");
				MedtronicSensorRecord prevRecord = (MedtronicSensorRecord) auxList
						.get(i - 1);
				MedtronicSensorRecord record = (MedtronicSensorRecord) auxList
						.get(i);
				Date prevDate = null;
				Date date = null;
				try {
					prevDate = formatter.parse(prevRecord.displayTime);
					date = formatter.parse(record.displayTime);
					dateDif += (prevDate.getTime() - date.getTime());
					log.debug("DATE_diff " + dateDif);
				} catch (ParseException e1) {
					e1.printStackTrace();
				}

				float prevRecordValue = 0;
				float recordValue = 0;
				try {
					prevRecordValue = Float.parseFloat(prevRecord.bGValue);
				} catch (Exception e) {

				}
				try {
					recordValue = Float.parseFloat(record.bGValue);
				} catch (Exception e) {

				}

				if (prevRecordValue > 0 && recordValue <= 0) {
					log.debug("AdjustRecordValue prev " + prevRecordValue
							+ " record " + recordValue);
					recordValue = prevRecordValue;
				}
				diff += prevRecordValue - recordValue;
				log.debug("VALUEDIFF " + diff);
			}
			if (dateDif > MedtronicConstants.TIME_20_MIN_IN_MS) {
				log.debug("EXIT BY TIME ");
				return null;
			} else {
				log.debug("CORRECT EXIT ");
				return diff;
			}
		} else {
			log.debug("I DO NOT Have the correct size " + auxList.size());
			return null;
		}
	}

	/**
	 * Function to calculate ISIG value
	 * 
	 * @param value
	 *            , Raw Value
	 * @param adjustment
	 *            ,
	 * @return ISIG value
	 */
	public float calculateISIG(int value, short adjustment) {
		float isig = (float) value
				/ (MedtronicConstants.SENSOR_CONVERSION_CONSTANT_VALUE - ((float) value * (float) MedtronicConstants.SENSOR_CONVERSION_CONSTANT_VALUE2));
		isig += ((float) adjustment * (float) value * (MedtronicConstants.SENSOR_CONVERSION_CONSTANT_VALUE3 + (MedtronicConstants.SENSOR_CONVERSION_CONSTANT_VALUE4
				* (float) value / (float) MedtronicConstants.SENSOR_CONVERSION_CONSTANT_VALUE5)));
		return isig;
	}

	/**
	 * This function calculates the SVG to upload applying a filter to the
	 * Unfiltered glucose data
	 * 
	 * @param prevRecord
	 * @param auxList
	 * @return
	 */
	public int applyFilterToRecord(MedtronicSensorRecord currentRecord,
			List<Record> auxList) {
		/*
		 * if (auxList.size() >= 2) {
		 * 
		 * if (!(auxList.get(0) instanceof MedtronicSensorRecord)) return -1;
		 * MedtronicSensorRecord record = (MedtronicSensorRecord) auxList
		 * .get(0); MedtronicSensorRecord record2 = (MedtronicSensorRecord)
		 * auxList .get(1); return (int) ((currentRecord.unfilteredGlucose *
		 * glucoseFilter[0]) + (record.unfilteredGlucose * glucoseFilter[1]) +
		 * (record2.unfilteredGlucose * glucoseFilter[2])); } else if
		 * (auxList.size() == 1) { MedtronicSensorRecord record =
		 * (MedtronicSensorRecord) auxList .get(0); return (int)
		 * ((currentRecord.unfilteredGlucose * glucoseFilter[0]) +
		 * (currentRecord.unfilteredGlucose * glucoseFilter[1]) +
		 * (record.unfilteredGlucose * glucoseFilter[2])); }else{ return (int)
		 * ((currentRecord.unfilteredGlucose * glucoseFilter[0]) +
		 * (currentRecord.unfilteredGlucose * glucoseFilter[1])+
		 * (currentRecord.unfilteredGlucose * glucoseFilter[2])); }
		 */
		return (int) currentRecord.unfilteredGlucose;

	}

	/**
	 * calculates crc16
	 * 
	 * @param bytes
	 * @return crc
	 */
	public int crc16(Byte[] bytes) {
		int crc = 0xFFFF; // initial value
		int polynomial = 0x1021; // 0001 0000 0010 0001 (0, 5, 12)
		// byte[] testBytes = "123456789".getBytes("ASCII");
		for (byte b : bytes) {
			for (int i = 0; i < 8; i++) {
				boolean bit = ((b >> (7 - i) & 1) == 1);
				boolean c15 = ((crc >> 15 & 1) == 1);
				crc <<= 1;
				if (c15 ^ bit)
					crc ^= polynomial;
			}
		}
		crc &= 0xffff;
		return crc;
	}

	/**
	 * This function tries to calculate the trend of the glucose values.
	 * 
	 * @param record
	 * @param list
	 */
	public void calculateTrendAndArrow(MedtronicSensorRecord record,
			CircleList<Record> list) {
		String trend = "Not Calculated";
		String trendA = "--X";
		Float diff = getGlucoseDifferentialIn(3, list);// most Recent first
		if (diff != null) {
			diff /= 5f;
			diff *= 0.0555f;// convierto a mmol/l
			int trendArrow = 0;
			if (diff >= -0.06f && diff <= 0.06f)
				trendArrow = 4;
			else if ((diff > 0.06f) && (diff <= 0.11f)) {
				trendArrow = 3;
			} else if ((diff < -0.06f) && (diff >= -0.11f)) {
				trendArrow = 5;
			} else if ((diff > 0.11f) && (diff <= 0.17f)) {
				trendArrow = 2;
			} else if ((diff < -0.11f) && (diff >= -0.17f)) {
				trendArrow = 6;
			} else if ((diff > 0.17f)) {
				trendArrow = 1;
			} else if ((diff < -0.17f)) {
				trendArrow = 7;
			} else {
				trendArrow = 0;
			}

			switch (trendArrow) {
			case (0):
				trendA = "\u2194";
			trend = "NONE";
			break;
			case (1):
				trendA = "\u21C8";
			trend = "DoubleUp";
			break;
			case (2):
				trendA = "\u2191";
			trend = "SingleUp";
			break;
			case (3):
				trendA = "\u2197";
			trend = "FortyFiveUp";
			break;
			case (4):
				trendA = "\u2192";
			trend = "Flat";
			break;
			case (5):
				trendA = "\u2198";
			trend = "FortyFiveDown";
			break;
			case (6):
				trendA = "\u2193";
			trend = "SingleDown";
			break;
			case (7):
				trendA = "\u21CA";
			trend = "DoubleDown";
			break;
			case (8):
				trendA = "\u2194";
			trend = "NOT COMPUTABLE";
			break;
			case (9):
				trendA = "\u2194";
			trend = "RATE OUT OF RANGE";
			break;
			}
		} else {
			trendA = "\u2194";
			trend = "RATE OUT OF RANGE";
		}

		record.trend = trend;
		record.trendArrow = trendA;
	}

	/**
     * Sends an error message to be printed in the display (DEBUG) if it is repeated, It is not printed again. If UI is not visible, It will launch a pop-up message.
     * @param valuetosend
     * @param clear, if true, the display is cleared before printing "valuetosend"
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
	
	
	/**
	 * Class to manage the circular aspect of the sensor readings
	 * 
	 * @author lmmarguenda
	 * 
	 * @param <E>
	 */
	class CircleList<E> {
		private int size;
		private int capacity;
		private int endOffset;
		private int startOffset;
		ArrayList<E> list = new ArrayList<E>();
		private Object listLock = new Object();

		/**
		 * Constructor
		 * 
		 * @param capacity
		 */
		public CircleList(int capacity) {
			size = 0;
			this.capacity = capacity;
			endOffset = 0;
			startOffset = 0;
			list = new ArrayList<E>();
		}

		/**
		 * add
		 * 
		 * @param item
		 */
		public void add(E item) {
			synchronized (listLock) {
				if (endOffset == capacity) {
					endOffset = 0;
					startOffset = 1;
				}
				if (endOffset >= list.size())
					list.add(endOffset, item);
				else
					list.set(endOffset, item);
				endOffset++;
				if (endOffset <= startOffset)
					startOffset++;
				if (startOffset == capacity)
					startOffset = 0;
				size = list.size();
			}
		}

		/**
		 * clear
		 */
		public void clear() {
			synchronized (listLock) {
				size = 0;
				endOffset = 0;
				startOffset = 0;
				list.clear();
			}
		}

		/**
		 * @param size
		 *            , maximum number of elements to get.
		 * @return a list sorted from the "startOffset" to the "endOffset".
		 */
		public List<E> getList(int size) {
			List<E> result = new ArrayList<E>();
			List<E> aux = null;
			int auxEndOffset = 0;
			int auxStartOffset = 0;
			synchronized (listLock) {
				auxEndOffset = endOffset;
				auxStartOffset = startOffset;
				aux = new ArrayList<E>();
				aux.addAll(list);

			}
			int auxSize = size;
			if (auxSize > aux.size())
				auxSize = aux.size();
			if (auxEndOffset > auxStartOffset) {
				for (int i = auxStartOffset; i < auxEndOffset && auxSize > 0; i++) {
					result.add(aux.get(i));
					auxSize--;
				}
			} else {
				for (int i = auxStartOffset; i < capacity && auxSize > 0; i++) {
					result.add(aux.get(i));
					auxSize--;
				}
				for (int i = 0; i < auxEndOffset && auxSize > 0; i++) {
					result.add(aux.get(i));
					auxSize--;
				}
			}
			return result;
		}

		/**
		 * @param size
		 *            , maximum number of elements to get.
		 * @return a list sorted from the "endOffset" to the "startOffset".
		 */
		public List<E> getListFromTail(int size) {
			List<E> result = new ArrayList<E>();
			List<E> aux = null;
			int auxEndOffset = 0;
			int auxStartOffset = 0;
			synchronized (listLock) {
				auxEndOffset = endOffset;
				auxStartOffset = startOffset;
				aux = new ArrayList<E>();
				aux.addAll(list);

			}
			int auxSize = size;
			if (auxSize > aux.size())
				auxSize = aux.size();

			if (auxEndOffset > auxStartOffset) {
				for (int i = auxEndOffset - 1; i >= auxStartOffset
						&& auxSize > 0; i--) {
					result.add(aux.get(i));
					auxSize--;
				}
			} else {
				for (int i = auxEndOffset - 1; i >= 0 && auxSize > 0; i--) {
					result.add(aux.get(i));
					auxSize--;
				}
				if (auxSize > 0) {
					for (int i = capacity - 1; i >= auxStartOffset
							&& auxSize > 0; i--) {
						result.add(aux.get(i));
						auxSize--;
					}
				}
			}
			return result;
		}

		/**
		 * 
		 * @return items allocated.
		 */
		public int size() {
			synchronized (listLock) {
				return size;
			}
		}
	}

	/**
	 * Runnable to check how old is the last calibration, and to manage the time
	 * out of the current calibration process
	 * 
	 * @author lmmarguenda
	 * 
	 */
	class CalibrationStatusChecker implements Runnable {
		public Handler mHandlerReviewParameters = null;

		public CalibrationStatusChecker(Handler mHandlerReviewParameters) {
			this.mHandlerReviewParameters = mHandlerReviewParameters;
		}

		public void run() {
			checkCalibrationOutOfTime();
			mHandlerReviewParameters.postDelayed(this,
					MedtronicConstants.TIME_5_MIN_IN_MS);
		}
	}

}

package com.nightscout.android.medtronic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import android.os.Handler;
import android.os.Messenger;

import com.nightscout.android.dexcom.USB.HexDump;
import com.physicaloid.lib.Physicaloid;

public class HistoricGetterThread extends CommandSenderThread {
	ArrayList<byte[]> historicPage = new ArrayList<byte[]>();
	HashMap<String,ArrayList<byte[]>> historic = new HashMap<String, ArrayList<byte[]>>();
	boolean firstReadPage = true;
	byte[] currentPage = new byte[4];
	byte[] lastHistoricPage = new byte[4];
	int historicPageIndex = -1;
	int currentLine = -1;
	int shift = 0;
	int timeout = 0;
	boolean isWaitingNextLine = false;
	
	public HistoricGetterThread(
			ArrayList<Messenger> mClients, MedtronicReader reader,
			byte[] idPump, Physicaloid mSerialDevice, Handler mHandler4) {

		super(new byte[]{MedtronicConstants.MEDTRONIC_WAKE_UP, MedtronicConstants.MEDTRONIC_GET_LAST_PAGE, MedtronicConstants.MEDTRONIC_READ_PAGE_COMMAND}, mClients, reader, idPump, mSerialDevice, mHandler4);
		// TODO Auto-generated constructor stub
		waitTime = 500;
		log.debug("HistoricGetterConstructor");
	}
	public void init() {
		log.debug("HistoricGetterInit");

		mHandler3.removeCallbacks(wThread);
		//mHandler4.removeCallbacks(this);
		if (reader != null){
			synchronized (reader.sendingCommandLock) {
				reader.sendingCommand = false;
			}
			synchronized (reader.waitingCommandLock) {
				reader.waitingCommand = false;
				reader.lastCommandSend = null;
			}
			synchronized (reader.processingCommandLock) {
				reader.processingCommand = false;
			}
			sendMessageToUI("Init historicGetter", false);
			synchronized (reader.processingCommandLock) {
				reader.processingCommand = false;
			}
			synchronized (reader.waitingCommandLock) {
				reader.waitingCommand = false;
				reader.lastCommandSend = null;
			}
		}

		byte[] cList = new byte[]{MedtronicConstants.MEDTRONIC_WAKE_UP, MedtronicConstants.MEDTRONIC_GET_LAST_PAGE, MedtronicConstants.MEDTRONIC_READ_PAGE_COMMAND};
		commandList = cList;
		historicPage.clear();
		historic.clear();
		firstReadPage = true;
		currentPage = new byte[4];
		lastHistoricPage = new byte[4];
		currentLine = -1;
		isWaitingNextLine = false;
		shift = 0;
		timeout = 0;
		index = 0;
		historicPageIndex = -1;
		wThread.retries = -1; //first retry does not count;
		wThread.sent = false;
		wThread.isRequest = true;
		wThread.postCommandBytes = null;
	}
	/**
	 * Runnable Method, It tries to send all the commands in "commanList", in order to do that, 
	 * It creates a second handler calling WriterThread to manage the command send.
	 */
	@Override
	public void run() {
		try
		{
			//sendMessageToUI("sending Command "+ HexDump.toHexString(command), false);
			if (index == 0){
				sendMessageToUI(" ", true);
				sendMessageToUI("Starting Historic log request...", false);
			}
			if (wThread.retries >= MedtronicConstants.NUMBER_OF_RETRIES){
				mHandler3.removeCallbacks(wThread);
				synchronized (reader.sendingCommandLock) {
					reader.sendingCommand = false;
				}
				synchronized (reader.waitingCommandLock) {
					reader.waitingCommand = false;
					reader.lastCommandSend = null;
				}
				synchronized (reader.processingCommandLock) {
					reader.processingCommand = false;
				}
				sendMessageToUI("Timeout expired executing command list", false);
				synchronized (reader.processingCommandLock) {
					reader.processingCommand = false;
				}
				synchronized (reader.waitingCommandLock) {
					reader.waitingCommand = false;
					reader.lastCommandSend = null;
				}
				return;
			}
			synchronized (reader.processingCommandLock) {//TODO: LAST
				if (!reader.processingCommand)
					return;
			}
			log.debug("HistoricGetter index + commandList "+ index + " "+ commandList.length);
			if (index >= commandList.length){
				log.debug("HG firstReadPage");
				if (firstReadPage){
					log.debug("HG firstReadPage TRUE");
					firstReadPage = false;
					commandList = Arrays.copyOf(commandList, commandList.length+1);
					commandList[commandList.length-1] = MedtronicConstants.MEDTRONIC_READ_PAGE_COMMAND;
					wThread.isRequest = false;
					byte[] lastHistoricPage = HexDump.toByteArray(historicPageIndex - shift);
					log.debug("LastPAGE "+HexDump.toHexString(lastHistoricPage)+" size "+lastHistoricPage.length);
					wThread.postCommandBytes = new byte[64];
					Arrays.fill(wThread.postCommandBytes, (byte)0x00);
					wThread.postCommandBytes[0] = 0x04;
					wThread.postCommandBytes[1] = lastHistoricPage[0];
					wThread.postCommandBytes[2] = lastHistoricPage[1];
					wThread.postCommandBytes[3] = lastHistoricPage[2];
					wThread.postCommandBytes[4] = lastHistoricPage[3];
					isWaitingNextLine = true;
				
					synchronized (reader.waitingCommandLock) {
						reader.waitingCommand = true;
						reader.lastCommandSend = null;
					}
				
					byte command = commandList[index];
					//sendMessageToUI("COMMAND SENDER: sending Command  "+ HexDump.toHexString(command), false);
					wThread.retries = -1;
					wThread.command = command;
					wThread.instance = this;
					mHandler3.post(wThread);
					index++;
					return;
				}
				synchronized (reader.processingCommandLock) {
					reader.processingCommand = false;
				}
				synchronized (reader.waitingCommandLock) {
					reader.waitingCommand = false;
					reader.lastCommandSend = null;
				}
				return;
			}
			byte command = commandList[index];
			if (withoutConfirmation <= 0 || command == MedtronicConstants.MEDTRONIC_INIT ){
				synchronized (reader.waitingCommandLock) {
					reader.waitingCommand = true;
					reader.lastCommandSend = null;
				}
			}else{
				synchronized (reader.waitingCommandLock) {
					reader.waitingCommand = false;
					reader.lastCommandSend = null;
				}
			}
			if (command == MedtronicConstants.MEDTRONIC_INIT){
				init();
				return;
			}
			wThread.retries = -1;
			wThread.command = command;
			wThread.instance = this;
			mHandler3.post(wThread);
			index++;
		}catch(Exception e){
			StringBuffer sb1 = new StringBuffer("");
			sb1.append("EXCEPTION!!!!!! "+ e.getMessage()+" "+e.getCause());
			for (StackTraceElement st : e.getStackTrace()){
				sb1.append(st.toString());
			}
			sendMessageToUI(sb1.toString(), false);
		}

	}
}

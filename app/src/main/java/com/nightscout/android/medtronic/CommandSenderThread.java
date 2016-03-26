package com.nightscout.android.medtronic;

import java.util.ArrayList;


import org.slf4j.LoggerFactory;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import ch.qos.logback.classic.Logger;

import com.nightscout.android.dexcom.USB.HexDump;
import com.physicaloid.lib.Physicaloid;


/**
 * Class: CommandSenderThread
 * This class manages the command send operations over the Medtronic's pump.
 * This class also access the shared variables located in MedtronicReader class to know when the application is commanding or it has finished a request.
 * @author lmmarguenda
 *
 */
public class CommandSenderThread implements Runnable{
	 protected Logger log = (Logger) LoggerFactory.getLogger(MedtronicReader.class.getName());
	 protected byte[] commandList;  
	 protected MedtronicReader reader;
	 protected byte[] idPump;
	 protected Physicaloid mSerialDevice;
	 protected Handler mHandler3 = new Handler();
	 protected Handler mHandler4 = null;
	 protected int index = 0;
	 protected WriterThread wThread = new WriterThread();
	 protected ArrayList<Messenger> mClients = new ArrayList<Messenger>();
	 long waitTime = MedtronicConstants.WAIT_ANSWER;
	 int withoutConfirmation = 0;
	 
	 
	 public CommandSenderThread(MedtronicReader reader,byte[]idPump,Physicaloid mSerialDevice, Handler mHandler4){
		 this.reader = reader;
		 this.idPump = idPump;
		 this.mSerialDevice = mSerialDevice;
		 this.mHandler4 = mHandler4;
	 }
	 
	 /**
	  * Constructor
	  * @param commandList, array of commands to send in order.
	  * @param mClient, communication with the UI.
	  * @param reader, MedtronicReader instance.
	  * @param idPump
	  * @param mSerialDevice
	  * @param mHandler4, Handler which has started this process.
	  */
	 public CommandSenderThread(byte[]commandList, ArrayList<Messenger> mClients, MedtronicReader reader,byte[]idPump,Physicaloid mSerialDevice, Handler mHandler4){
		 this.commandList = commandList;
		 this.mClients = mClients;
		 this.reader = reader;
		 this.idPump = idPump;
		 this.mSerialDevice = mSerialDevice;
		 this.mHandler4 = mHandler4;
	 }
	 

	 /**
	  * Runnable Method, It tries to send all the commands in "commanList", in order to do that, 
	  * It creates a second handler calling WriterThread to manage the command send.
	  */
	 public void run() {
		 try{
    		//sendMessageToUI("sending Command "+ HexDump.toHexString(command), false);
			 if (index == 0){
				 sendMessageToUI("Starting Pump info request...", true);
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
		 	
		 	if (index >= commandList.length){
        		synchronized (reader.processingCommandLock) {
	    			reader.processingCommand = false;
				}
        		synchronized (reader.waitingCommandLock) {
            		reader.waitingCommand = false;
            		reader.lastCommandSend = null;
    			}
        		return;
        	}
		 	if (withoutConfirmation <= 0){
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
        	byte command = commandList[index];
        	//sendMessageToUI("COMMAND SENDER: sending Command  "+ HexDump.toHexString(command), false);
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
	 /**
	  * Sends a message to the serial device
	  * @param command, Medtronic command from MedtronicConstants.class
	  * @param repeat, Number of times to send this message.
	  * @return bytes written
	  */
    protected int sendMedtronicPumpRequest(byte command, byte repeat) {
    	try{
    	if (idPump != null && idPump.length > 0){
	        byte[] readSystemTime = new byte[6+idPump.length];
	        int i = 0;
	        readSystemTime[0] = (byte)0x81;
	        readSystemTime[1] = (byte)0x06;
	        readSystemTime[2] = repeat;
	        readSystemTime[3] = (byte)MedtronicConstants.MEDTRONIC_PUMP;
	        for (i=0; i < idPump.length; i++)
	        	readSystemTime[i+4] = (byte)idPump[i];
	        readSystemTime[idPump.length + 4] = command;
	        readSystemTime[idPump.length + 5] = (byte)0x00;
	        log.debug("pump request sent ");//+ HexDump.toHexString(readSystemTime));
	        int resultWrite =  mSerialDevice.write(readSystemTime);
	        return resultWrite;
    	}
    	}catch(Exception e){
   		 StringBuffer sb1 = new StringBuffer("");
   		 sb1.append("EXCEPTION!!!!!! "+ e.getMessage()+" "+e.getCause());
   		 for (StackTraceElement st : e.getStackTrace()){
   			 sb1.append(st.toString());
   		 }
   		 sendMessageToUI(sb1.toString(), false);
    	}
   	
    	return -1;
    }
    /**
	  * Sends a message to the serial device
	  * @param command, Medtronic command from MedtronicConstants.class
	  * @param repeat, Number of times to send this message.
	  * @return bytes written
	  */
   protected int sendMedtronicPumpCommand(byte command, byte repeat, byte[]postCommand) {
   	try{
   	if (idPump != null && idPump.length > 0){
   			int pCLength = 0;
   			if (postCommand != null)
   				pCLength = postCommand.length;
	        byte[] readSystemTime = new byte[6+idPump.length+pCLength];
	        int i = 0;
	        int size= 2 +idPump.length+pCLength+1;
	        byte[] sizByte = HexDump.hexStringToByteArray(HexDump.toHexString(size));
	        log.debug("sizByte "+ HexDump.toHexString(sizByte));
	        readSystemTime[0] = (byte)0x81;
	        readSystemTime[1] = (byte)sizByte[sizByte.length-1];
	        readSystemTime[2] = repeat;
	        readSystemTime[3] = (byte)MedtronicConstants.MEDTRONIC_PUMP;
	        for (i=0; i < idPump.length; i++)
	        	readSystemTime[i+4] = (byte)idPump[i];
	        readSystemTime[idPump.length + 4] = command;
	        log.debug("postcommadnLength ");//+ postCommand.length);
	        for (i=0; i < postCommand.length; i++)
	        	readSystemTime[idPump.length + 5 + i] = (byte)postCommand[i];
	        log.debug("command sent "+ HexDump.toHexString(readSystemTime));
	        int resultWrite =  mSerialDevice.write(readSystemTime);
	        return resultWrite;
   	}
   	}catch(Exception e){
  		 StringBuffer sb1 = new StringBuffer("");
  		 sb1.append("EXCEPTION!!!!!! "+ e.getMessage()+" "+e.getCause());
  		 for (StackTraceElement st : e.getStackTrace()){
  			 sb1.append(st.toString());
  		 }
  		 sendMessageToUI(sb1.toString(), false);
   	}
  	
   	return -1;
   }
    /**
     * Runnable to manage the command send, retries included.
     * @author lmmarguenda
     *
     */
    class WriterThread implements Runnable{
    	byte command;
    	int retries = -1; //first retry does not count;
    	boolean sent = false;
    	boolean isRequest = true;
    	byte[] postCommandBytes = null;
    	CommandSenderThread instance = null;
    	int timeoutSending = 0;
    	public WriterThread(){
    		
    	}
    	public void setCommand(byte command){
    		this.command = command;
    	}
    	
    	public void run(){
    		try{
			synchronized (reader.processingCommandLock) {
				if (!reader.processingCommand)
					return;
			}
    		boolean isWakeUp = false;
    		byte repeat = (byte)0x01;
    		if (command == MedtronicConstants.MEDTRONIC_WAKE_UP){
    			repeat = (byte)0xff;
    			isWakeUp = true;
    		}
    		log.debug("sendcommand");
    		
    		synchronized (reader.sendingCommandLock) {
        		if (reader.sendingCommand){
        			if (timeoutSending < 11){
        				log.debug("timeoutSending++ "+timeoutSending);
	        			mHandler3.removeCallbacks(wThread);
	        			mHandler3.postDelayed(wThread, 3000);
	        			timeoutSending++;
	        			return;
        			}else{
        				reader.sendingCommand = false;
        				timeoutSending = 0;
        			}
        			
        		}
			}
    		log.debug("timeoutSending "+timeoutSending);
    		timeoutSending = 0;
    		if (sent){
    			log.debug("sent ");
    			//if I have sent it, I will wait once to give the "enlite" time to answer, next time I will retry.
    			sent = false;
    			synchronized (reader.waitingCommandLock) {
            		if (!reader.waitingCommand){
            			retries = 0;
            			mHandler4.removeCallbacks(instance);
            			mHandler4.post(instance);
            			return;//exit I have received the answer expected
            		}
    			}
    			long delay = waitTime;
    			if (isWakeUp)
					delay = 10000;
				mHandler3.postDelayed(wThread, delay);
    			return;
    		}
    		synchronized (reader.waitingCommandLock) {
        		if (!reader.waitingCommand){
        			if (withoutConfirmation > 0){
        				log.debug("sending command without expecting confirmation still send -->"+ (withoutConfirmation--));
            			if (isRequest || postCommandBytes == null || postCommandBytes.length == 0)
                			sendMedtronicPumpRequest(command,repeat); //command sent
                		else
                			sendMedtronicPumpCommand(command, repeat, postCommandBytes);
            			withoutConfirmation--;
            		}else
            			log.debug("answer received");
        			retries = 0;
        			mHandler4.removeCallbacks(instance);
        			mHandler4.post(instance);
        			return;//exit I have received the answer expected
        		}
			}
    		if (isRequest || postCommandBytes == null || postCommandBytes.length == 0)
    			sendMedtronicPumpRequest(command,repeat); //command sent
    		else
    			sendMedtronicPumpCommand(command, repeat, postCommandBytes);
 
        	synchronized (reader.waitingCommandLock) {
        			reader.lastCommandSend = command;// register the command
    		}
    		
			synchronized (reader.sendingCommandLock) {
				log.debug("send command "+retries);
				reader.sendingCommand = true;
				if (!sent){
					retries++;
					sent = true;
				}
				if (retries < MedtronicConstants.NUMBER_OF_RETRIES){
					// sendMessageToUI("ANOTHER RETRY", false);
					mHandler3.removeCallbacks(wThread);
					long delay = waitTime;
					if (isWakeUp)
						delay = 10000;
					mHandler3.postDelayed(wThread, delay);
				}else{
					//sendMessageToUI("I do not have more retries!!", false);
					mHandler4.removeCallbacks(instance);
					mHandler4.post(instance);
				}
				
			}
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
    /**
	  * Method to send a Message to the UI.
	  * @param valuetosend
	  * @param clear
	  */
    protected void sendMessageToUI(String valuetosend, boolean clear) { 
    	Log.i("medtronic.CommandSender", valuetosend);
    	if (mClients != null && mClients.size() > 0){
	        for (int i=mClients.size()-1; i>=0; i--) {
	            try {
	            	Message mSend = null;
	            	if (clear){
	            		mSend = Message.obtain(null, MedtronicConstants.MSG_MEDTRONIC_CGM_CLEAR_DISPLAY);
	            		mClients.get(i).send(mSend);
	            		continue;
	            	}
	            	mSend = Message.obtain(null, MedtronicConstants.MSG_MEDTRONIC_CGM_MESSAGE_RECEIVED); 
	            	Bundle b = new Bundle();
	                b.putString("data", valuetosend);
	            	mSend.setData(b);
	                mClients.get(i).send(mSend);
	
	            } catch (RemoteException e) {
	                // The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
	                mClients.remove(i);
	            }
	        }
    	}
    }	  

	public byte[] getCommandList() {
		return commandList;
	}

	public void setCommandList(byte[] commandList) {
		this.index = 0;
		this.commandList = commandList;
	}

	public ArrayList<Messenger> getmClients() {
		return mClients;
	}

	public void setmClients(ArrayList<Messenger> mClients) {
		this.mClients = mClients;
	}
}

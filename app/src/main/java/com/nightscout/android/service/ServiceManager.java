package com.nightscout.android.service;

/*
 * This example demonstrates a good way to communicate between Activity and Service.
 *
 * 1. Implement a service by inheriting from AbstractService
 * 2. Add a ServiceManager to your activity
 *   - Control the service with ServiceManager.start() and .stop()
 *   - Send messages to the service via ServiceManager.send()
 *   - Receive messages with by passing a Handler in the constructor
 * 3. Send and receive messages on the service-side using send() and onReceiveMessage()
 *
 * Author: Philipp C. Heckel; based on code by Lance Lefebure from
 *         http://stackoverflow.com/questions/4300291/example-communication-between-activity-and-service-using-messaging
 * Source: https://code.launchpad.net/~binwiederhier/+junk/android-service-example
 * Date:   6 Jun 2012
 */

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public class ServiceManager {
    private Class<? extends AbstractService> mServiceClass;
    private Context mActivity;
    private boolean mIsBound;
    private Messenger mService = null;
    private Handler mIncomingHandler = null;
    private final Messenger mMessenger = new Messenger(new IncomingHandler());

    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (mIncomingHandler != null) {
                Log.i("ServiceHandler", "Incoming message. Passing to handler: "+msg);
                mIncomingHandler.handleMessage(msg);
            }
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
            //textStatus.setText("Attached.");
            Log.i("ServiceHandler", "Attached.");
            try {
                Message msg = Message.obtain(null, AbstractService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even do anything with it
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been unexpectedly disconnected - process crashed.
            mService = null;
            //textStatus.setText("Disconnected.");
            Log.i("ServiceHandler", "Disconnected.");
        }
    };

    public ServiceManager(Context context, Class<? extends AbstractService> serviceClass, Handler incomingHandler) {
        this.mActivity = context;
        this.mServiceClass = serviceClass;
        this.mIncomingHandler = incomingHandler;

        if (isRunning()) {
            doBindService();
        }
    }

    public void start() {
        doStartService();
        doBindService();
    }

    public void stop() {
        doUnbindService();
        doStopService();
    }

    /**
     * Use with caution (only in Activity.onDestroy())!
     */
    public void unbind() {
        doUnbindService();
    }

    public boolean isRunning() {
        ActivityManager manager = (ActivityManager) mActivity.getSystemService(Context.ACTIVITY_SERVICE);

        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (mServiceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }

        return false;
    }

    public void send(Message msg) throws RemoteException {
        if (mIsBound) {
            if (mService != null) {
                mService.send(msg);
            }
        }
    }

    private void doStartService() {
        mActivity.startService(new Intent(mActivity, mServiceClass));
    }

    private void doStopService() {
        mActivity.stopService(new Intent(mActivity, mServiceClass));
    }

    private void doBindService() {
        mActivity.bindService(new Intent(mActivity, mServiceClass), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    private void doUnbindService() {
        if (mIsBound) {
            // If we have received the service, and hence registered with it, then now is the time to unregister.
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null, AbstractService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            }

            // Detach our existing connection.
            mActivity.unbindService(mConnection);
            mIsBound = false;
            //textStatus.setText("Unbinding.");
            Log.i("ServiceHandler", "Unbinding.");
        }
    }
}
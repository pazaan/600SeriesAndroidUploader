package info.nightscout.android.USB;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.io.IOException;

// simplified usb driver targeting CNL comms
// keeps overhead to a minimum allowing for high speed transfer of bulk packets from pump to cnl to uploader

public class UsbHidDriver {
    private final String TAG = UsbHidDriver.class.getSimpleName();

    private UsbInterface mInterface;

    private UsbEndpoint mReadEndpoint;
    private UsbEndpoint mWriteEndpoint;

    private UsbDevice mDevice;
    private UsbDeviceConnection mConnection;

    private boolean isConnectionOpen = false;

    public UsbHidDriver(UsbDevice device, UsbDeviceConnection connection) {
        mDevice = device;
        mConnection = connection;
    }

    public static UsbDevice getUsbDevice(UsbManager usbManager, int vendorId, int productId) {
        // Iterate all the available devices and find ours.
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (device.getProductId() == productId && device.getVendorId() == vendorId) {
                return device;
            }
        }

        return null;
    }

    public static UsbHidDriver acquire(UsbManager usbManager, UsbDevice device) {
        if (device != null) {
            final UsbDeviceConnection mConnection = usbManager.openDevice(device);

            return new UsbHidDriver(device, mConnection);
        }

        return null;
    }

    public void open() throws IOException {
        Log.d(TAG, "Claiming HID interface.");
        mInterface = mDevice.getInterface(0);

        if (!mConnection.claimInterface(mInterface, true)) {
            isConnectionOpen = false;
            throw new IOException("Could not claim data interface.");
        }

        mReadEndpoint = mInterface.getEndpoint(1);
        Log.d(TAG, "Read endpoint direction: " + mReadEndpoint.getDirection());
        mWriteEndpoint = mInterface.getEndpoint(0);
        Log.d(TAG, "Write endpoint direction: " + mWriteEndpoint.getDirection());
        isConnectionOpen = true;
    }

    public void close() {
        synchronized (UsbHidDriver.class) {
            if (mConnection != null && isConnectionOpen) {
                Log.d(TAG, "Releasing HID interface.");
                if (!mConnection.releaseInterface(mInterface))
                    Log.w(TAG, "releaseInterface returned false");
                mConnection.close();
            }
            isConnectionOpen = false;
        }
    }

    public int read(byte[] dest, int timeoutMillis) {
        return mConnection.bulkTransfer(mReadEndpoint, dest, dest.length,
                timeoutMillis);
    }

    public void write(byte[] src, int timeoutMillis) throws IOException {
        if (mConnection.bulkTransfer(mWriteEndpoint, src, src.length,
                timeoutMillis) <= 0) {
            throw new IOException("Error writing to usb endpoint");
        }
    }

    public boolean isConnectionOpen() {
        return isConnectionOpen;
    }

}

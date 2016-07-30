package info.nightscout.android.USB;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.io.IOException;

/**
 * USB HID Driver implementation.
 *
 * @author mike wakerly (opensource@hoho.com), Lennart Goedhart (lennart@omnibase.com.au)
 * @see <a
 * href="http://www.usb.org/developers/devclass_docs/usbcdc11.pdf">Universal
 * Serial Bus Class Definitions for Communication Devices, v1.1</a>
 */
public class UsbHidDriver extends CommonUsbDriver {

    private final String TAG = UsbHidDriver.class.getSimpleName();

    private UsbInterface mInterface;

    private UsbEndpoint mReadEndpoint;
    private UsbEndpoint mWriteEndpoint;

    private boolean isConnectionOpen = false;

    public UsbHidDriver(UsbDevice device, UsbDeviceConnection connection) {
        super(device, connection);
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

    @Override
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
        mConnection.releaseInterface(mInterface);
    }

    @Override
    public void close() {
        if (mConnection != null) {
            mConnection.close();
        }
        isConnectionOpen = false;
    }

    @Override
    public int read(byte[] dest, int timeoutMillis) throws IOException {
        final int numBytesRead;
        synchronized (mReadBufferLock) {
            mConnection.claimInterface(mInterface, true);
            int readAmt = Math.min(dest.length, mReadBuffer.length);
            numBytesRead = mConnection.bulkTransfer(mReadEndpoint, mReadBuffer, readAmt,
                    timeoutMillis);
            if (numBytesRead > 0) {
                System.arraycopy(mReadBuffer, 0, dest, 0, numBytesRead);
            }
            
            mConnection.releaseInterface(mInterface);
        }
        return numBytesRead;
    }

    @Override
    public int write(byte[] src, int timeoutMillis) throws IOException {
        int offset = 0;

        while (offset < src.length) {
            final int writeLength;
            final int amtWritten;

            synchronized (mWriteBufferLock) {
                final byte[] writeBuffer;

                writeLength = Math.min(src.length - offset, mWriteBuffer.length);
                if (offset == 0) {
                    writeBuffer = src;
                } else {
                    // bulkTransfer does not support offsets, make a copy.
                    System.arraycopy(src, offset, mWriteBuffer, 0, writeLength);
                    writeBuffer = mWriteBuffer;
                }

                mConnection.claimInterface(mInterface, true);
                amtWritten = mConnection.bulkTransfer(mWriteEndpoint, writeBuffer, writeLength,
                        timeoutMillis);
                mConnection.releaseInterface(mInterface);
            }
            if (amtWritten <= 0) {
                throw new IOException("Error writing " + writeLength
                        + " bytes at offset " + offset + " length=" + src.length);
            }

            Log.d(TAG, "Wrote amt=" + amtWritten + " attempted=" + writeLength);
            offset += amtWritten;
        }
        return offset;
    }

    @Override
    public boolean isConnectionOpen() {
        return isConnectionOpen;
    }
}

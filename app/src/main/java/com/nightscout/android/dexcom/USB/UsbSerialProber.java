/* Copyright 2011 Google Inc.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * Project home page: http://code.google.com/p/usb-serial-for-android/
 */

package com.nightscout.android.dexcom.USB;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.Map;

//import com.hoho.android.usbserial.driver.Cp2102SerialDriver;
//import com.hoho.android.usbserial.driver.FtdiSerialDriver;

/**
 * Helper class to assist in detecting and building {@link UsbSerialDriver}
 * instances from available hardware.
 *
 * @author mike wakerly (opensource@hoho.com)
 */
public enum UsbSerialProber {

    // TODO(mikey): Too much boilerplate.

    /**
     * Prober for {@link FtdiSerialDriver}.
     *
     * @see FtdiSerialDriver
     */
/*    FTDI_SERIAL {
        @Override
        public UsbSerialDriver getDevice(final UsbManager manager, final UsbDevice usbDevice) {
            if (!testIfSupported(usbDevice, FtdiSerialDriver.getSupportedDevices())) {
                return null;
            }
            final UsbDeviceConnection connection = manager.openDevice(usbDevice);
            if (connection == null) {
                return null;
            }
            return new FtdiSerialDriver(usbDevice, connection);
        }
    },*/

    CDC_ACM_SERIAL {
        @Override
        public UsbSerialDriver getDevice(UsbManager manager, UsbDevice usbDevice) {
//            if (!testIfSupported(usbDevice, CdcAcmSerialDriver.getSupportedDevices())) {
//               return null;
//            }
            final UsbDeviceConnection connection = manager.openDevice(usbDevice);
            if (connection == null) {
                return null;
            }
            return new CdcAcmSerialDriver(usbDevice, connection);
        }
    }; //,
    
/*    SILAB_SERIAL {
        @Override
        public UsbSerialDriver getDevice(final UsbManager manager, final UsbDevice usbDevice) {
            if (!testIfSupported(usbDevice, Cp2102SerialDriver.getSupportedDevices())) {
                return null;
            }
            final UsbDeviceConnection connection = manager.openDevice(usbDevice);
            if (connection == null) {
                return null;
            }
            return new Cp2102SerialDriver(usbDevice, connection);
        }
    };*/

    private static final String TAG = UsbSerialProber.class.getSimpleName();

    /**
     * Builds a new {@link UsbSerialDriver} instance from the raw device, or
     * returns <code>null</code> if it could not be built (for example, if the
     * probe failed).
     *
     * @param manager the {@link android.hardware.usb.UsbManager} to use
     * @param usbDevice the raw {@link android.hardware.usb.UsbDevice} to use
     * @return the first available {@link UsbSerialDriver}, or {@code null} if
     *         no devices could be acquired
     */
    public abstract UsbSerialDriver getDevice(final UsbManager manager, final UsbDevice usbDevice);

    /**
     * Acquires and returns the first available serial device among all
     * available {@link android.hardware.usb.UsbDevice}s, or returns {@code null} if no device could
     * be acquired.
     *
     * @param usbManager the {@link android.hardware.usb.UsbManager} to use
     * @return the first available {@link UsbSerialDriver}, or {@code null} if
     *         no devices could be acquired
     */
    public static UsbSerialDriver acquire(final UsbManager usbManager) {
        for (final UsbDevice usbDevice : usbManager.getDeviceList().values()) {
            final UsbSerialDriver probedDevice = acquire(usbManager, usbDevice);
            if (probedDevice != null) {
                return probedDevice;
            }
        }
        return null;
    }

    /**
     * Builds and returns a new {@link UsbSerialDriver} from the given
     * {@link android.hardware.usb.UsbDevice}, or returns {@code null} if no drivers supported this
     * device.
     *
     * @param usbManager the {@link android.hardware.usb.UsbManager} to use
     * @param usbDevice the {@link android.hardware.usb.UsbDevice} to use
     * @return a new {@link UsbSerialDriver}, or {@code null} if no devices
     *         could be acquired
     */
    public static UsbSerialDriver acquire(final UsbManager usbManager, final UsbDevice usbDevice) {
        if (!usbManager.hasPermission(usbDevice)) {
            Log.i(TAG, "No permission for " + usbDevice.getVendorId() + " " + usbDevice.getProductId());
            return null;
        }
        for (final UsbSerialProber prober : values()) {
            final UsbSerialDriver probedDevice = prober.getDevice(usbManager, usbDevice);
            if (probedDevice != null) {
                return probedDevice;
            }
        }
        return null;
    }

    /**
     * Returns {@code true} if the given device is found in the vendor/product map.
     *
     * @param usbDevice the device to test
     * @param supportedDevices map of vendor ids to product id(s)
     * @return {@code true} if supported
     */
    private static boolean testIfSupported(final UsbDevice usbDevice,
            final Map<Integer, int[]> supportedDevices) {
        final int[] supportedProducts = supportedDevices.get(
                Integer.valueOf(usbDevice.getVendorId()));
        if (supportedProducts == null) {
            return false;
        }

        final int productId = usbDevice.getProductId();
        for (int supportedProductId : supportedProducts) {
            if (productId == supportedProductId) {
                return true;
            }
        }
        return false;
    }

}

package com.nightscout.android.upload.MedtronicNG;

import com.nightscout.android.upload.DeviceRecord;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Created by lgoedhart on 27/03/2016.
 */
public class PumpStatusRecord extends DeviceRecord implements Serializable {

    public String alarm = "---"; // Not sure where this is yet
    public String temporaryBasal = "---"; // Not sure where this is yet
    public String model = "---"; // Available?

    public int batteryPercentage;
    public Date pumpDate = new Date();
    public BigDecimal activeInsulin = new BigDecimal(0);
    public BigDecimal reservoirAmount = new BigDecimal(0);
    public boolean recentBolusWizard = false; // Whether a bolus wizard has been run recently
    public int bolusWizardBGL = 0; // in mg/dL. 0 means no recent bolus wizard reading.
}

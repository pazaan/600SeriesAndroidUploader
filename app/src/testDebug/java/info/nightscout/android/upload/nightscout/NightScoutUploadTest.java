package info.nightscout.android.upload.nightscout;

import android.util.Log;

import info.nightscout.android.model.medtronicNg.PumpStatusEvent;
import info.nightscout.android.model.medtronicNg.StatusEvent;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Log.class})
public class NightScoutUploadTest {

    NightScoutUpload mUploader;
    List<StatusEvent> mEvents;

    @Before
    public void doSetup() {

        mUploader = new NightScoutUpload();

        mEvents = new ArrayList<StatusEvent>();

        StatusEvent event1 = new StatusEvent();
        Date date = new Date();
        event1.setEventDate(date);
        event1.setPumpDate(date);
        event1.setDeviceName("MyDevice");
        event1.setSgv(100);
        event1.setCgmTrend(StatusEvent.CGM_TREND.FLAT);
        event1.setActiveInsulin(5.0f);
        event1.setBatteryPercentage((short)100);
        event1.setReservoirAmount(50.0f);
        event1.setRecentBolusWizard(false);
        event1.setBolusWizardBGL(100);
        event1.setUploaded(false);
        event1.setSuspended(false);
        event1.setDeliveringInsulin(false);
        event1.setTempBasalActive(false);
        event1.setCgmActive(false);
        event1.setActiveBasalPattern((byte)42);
        event1.setBasalRate(1.0f);
        event1.setTempBasalPercentage((byte)80);
        event1.setTempBasalMinutesRemaining((short)30);
        event1.setBasalUnitsDeliveredToday(30.0f);
        event1.setMinutesOfInsulinRemaining((short)45);
        event1.setSgvDate(date);
        event1.setLowSuspendActive(false);
        event1.setPumpTimeOffset(100000);

        mEvents.add(event1);

    }

    @Test
    public void doTest() {
        PowerMockito.mockStatic(Log.class);
        String url =  "https://my.azurewebsites.net";
        String secret = "SECRET";
        int uploaderBatteryLevel = 50;

        Boolean success = mUploader.doRESTUpload(url, secret, uploaderBatteryLevel, mEvents);
        assertEquals(success, true);
    }

}
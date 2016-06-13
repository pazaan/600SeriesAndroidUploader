package info.nightscout.android.model.medtronicNg;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.Index;

/**
 * Created by lgoedhart on 4/06/2016.
 */
public class PumpStatusEvent extends RealmObject {
    @Index
    private Date eventDate; // The actual time of the event (assume the capture device eventDate/time is accurate)
    private Date pumpDate; // The eventDate/time on the pump at the time of the event
    private float activeInsulin;
    private float reservoirAmount;
    private boolean recentBolusWizard; // Whether a bolus wizard has been run recently
    private int bolusWizardBGL; // in mg/dL. 0 means no recent bolus wizard reading.
}

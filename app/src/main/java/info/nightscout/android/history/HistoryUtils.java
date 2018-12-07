package info.nightscout.android.history;

import android.util.Log;

import java.util.Date;
import java.util.List;

import info.nightscout.android.medtronic.exception.IntegrityException;
import info.nightscout.android.model.medtronicNg.PumpHistoryInterface;
import info.nightscout.android.upload.nightscout.EntriesEndpoints;
import info.nightscout.android.upload.nightscout.TreatmentsEndpoints;

public class HistoryUtils {
    private static final String TAG = HistoryUtils.class.getSimpleName();

    public static boolean nightscoutTTL(List<NightscoutItem> nightscoutItems, PumpHistoryInterface record, String senderID) {

        if (record.getSenderDEL().contains(senderID)) {
            Log.d(TAG, "TTL delete record");

            NightscoutItem nightscoutItem = new NightscoutItem();
            nightscoutItem.setTimestamp(record.getEventDate().getTime());
            nightscoutItem.setMode(NightscoutItem.MODE.DELETE);

            TreatmentsEndpoints.Treatment treatment = nightscoutItem.treatment();
            treatment.setKey600(record.getKey());
            treatment.setPumpMAC600(String.valueOf(record.getPumpMAC()));
            treatment.setCreated_at(record.getEventDate());

            nightscoutItems.add(nightscoutItem);

            return true;
        }

        return false;
    }

    public static TreatmentsEndpoints.Treatment nightscoutTreatment(List<NightscoutItem> nightscoutItems, PumpHistoryInterface record, String senderID) {
        return nightscoutTreatment(nightscoutItems, record, senderID, record.getEventDate());
    }

    public static TreatmentsEndpoints.Treatment nightscoutTreatment(List<NightscoutItem> nightscoutItems, PumpHistoryInterface record, String senderID, Date eventDate) {

        NightscoutItem nightscoutItem = new NightscoutItem();
        nightscoutItem.setTimestamp(eventDate.getTime());
        nightscoutItem.setMode(record.getSenderACK().contains(senderID) ? NightscoutItem.MODE.UPDATE : NightscoutItem.MODE.CHECK );

        TreatmentsEndpoints.Treatment treatment = nightscoutItem.treatment();
        treatment.setKey600(record.getKey());
        treatment.setPumpMAC600(pumpMAC(record.getPumpMAC()));
        treatment.setCreated_at(eventDate);

        nightscoutItems.add(nightscoutItem);

        return treatment;
    }

    public static EntriesEndpoints.Entry nightscoutEntry(List<NightscoutItem> nightscoutItems, PumpHistoryInterface record, String senderID) {
        return nightscoutEntry(nightscoutItems, record, senderID, record.getEventDate());
    }

    public static EntriesEndpoints.Entry nightscoutEntry(List<NightscoutItem> nightscoutItems, PumpHistoryInterface record, String senderID, Date eventDate) {

        NightscoutItem nightscoutItem = new NightscoutItem();
        nightscoutItem.setTimestamp(eventDate.getTime());
        nightscoutItem.setMode(record.getSenderACK().contains(senderID) ? NightscoutItem.MODE.UPDATE : NightscoutItem.MODE.CHECK );

        EntriesEndpoints.Entry entry = nightscoutItem.entry();
        entry.setKey600(record.getKey());
        entry.setPumpMAC600(pumpMAC(record.getPumpMAC()));
        entry.setDate(eventDate.getTime());
        entry.setDateString(eventDate);

        nightscoutItems.add(nightscoutItem);

        return entry;
    }

    public static boolean integrity(PumpHistoryInterface record, Date eventDate) throws IntegrityException {

        if (Math.abs(record.getEventDate().getTime() - eventDate.getTime()) > 5 * 60000L) {
            throw new IntegrityException("Integrity check failed");
        }

        return true;
    }

    public static String key(String id, int value) {
        return String.format("%s%08X", id, value);
    }

    public static String key(String id, short value1, int value2) {
        return String.format("%s%04X%08X", id, value1, value2);
    }

    public static String pumpMAC(long value) {
        return String.format("%016X", value);
    }

}

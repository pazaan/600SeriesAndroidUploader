package info.nightscout.android.history;

import android.util.Log;

import java.util.Date;
import java.util.List;

import info.nightscout.android.medtronic.exception.IntegrityException;
import info.nightscout.android.medtronic.service.MedtronicCnlService;
import info.nightscout.android.model.medtronicNg.PumpHistoryInterface;
import info.nightscout.android.upload.nightscout.EntriesEndpoints;
import info.nightscout.android.upload.nightscout.TreatmentsEndpoints;
import io.realm.RealmObject;
import io.realm.RealmResults;

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

    public static TreatmentsEndpoints.Treatment nightscoutDeleteTreatment(List<NightscoutItem> nightscoutItems, PumpHistoryInterface record, String senderID) {

        Log.d(TAG, "Treatment: delete record request");

        NightscoutItem nightscoutItem = new NightscoutItem();
        nightscoutItem.setTimestamp(record.getEventDate().getTime());
        nightscoutItem.setMode(NightscoutItem.MODE.DELETE);

        TreatmentsEndpoints.Treatment treatment = nightscoutItem.treatment();
        treatment.setKey600(record.getKey());
        treatment.setPumpMAC600(String.valueOf(record.getPumpMAC()));
        treatment.setCreated_at(record.getEventDate());

        nightscoutItems.add(nightscoutItem);

        return treatment;
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

    public static EntriesEndpoints.Entry nightscoutDeleteEntry(List<NightscoutItem> nightscoutItems, PumpHistoryInterface record, String senderID) {

        Log.d(TAG, "Entry: delete record request");

        NightscoutItem nightscoutItem = new NightscoutItem();
        nightscoutItem.setTimestamp(record.getEventDate().getTime());
        nightscoutItem.setMode(NightscoutItem.MODE.DELETE);

        EntriesEndpoints.Entry entry = nightscoutItem.entry();
        entry.setKey600(record.getKey());
        entry.setPumpMAC600(String.valueOf(record.getPumpMAC()));
        entry.setDate(record.getEventDate().getTime());
        entry.setDateString(record.getEventDate());

        nightscoutItems.add(nightscoutItem);

        return entry;
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

        if (Math.abs(record.getEventDate().getTime() - eventDate.getTime()) >= MedtronicCnlService.INTEGRITY_FAIL_MS) {
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

    // nightscout will delete events with the same type & date, workaround: offset dates
    public static void checkTreatmentDateDuplicated(Class clazz, PumpHistoryInterface record, TreatmentsEndpoints.Treatment treatment) {
        long timestamp = record.getEventDate().getTime();
        String key = record.getKey();

        RealmResults results = RealmObject.getRealm(record).where(clazz)
                .greaterThan("eventDate", new Date(timestamp - 2000L))
                .lessThan("eventDate", new Date(timestamp + 2000L))
                .findAll();

        if (results.size() > 1) {
            Log.w(TAG, "found " + results.size() + " events with the same date");
            int n = 0;
            while (n < results.size() &&
                    !((PumpHistoryInterface) results.get(n)).getKey().equals(key)) {n++;}
            if (n > 0) {
                Log.w(TAG, String.format("adjusted eventDate for nightscout +%s seconds", n * 2));
                treatment.setCreated_at(new Date(timestamp + n * 2000));
            }
        }
    }

    // keep pump RTC value within bounds 0x80000000 to 0xFFFFFFFF
    public static int offsetRTC(int rtc, int offset) {
        long i = (long) rtc + (long) offset;
        if (i > 0) i = -1;
        if (i < -0x7FFFFFFFL) i = -0x7FFFFFFFL;
        return (int) i;
    }
}

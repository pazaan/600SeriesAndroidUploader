package info.nightscout.android.history;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;

import info.nightscout.android.medtronic.message.MessageUtils;

import static info.nightscout.android.utils.ToolKit.read32BEtoInt;
import static info.nightscout.android.utils.ToolKit.read8toUInt;

/**
 * Created by Pogman on 17.1.18.
 */

public class HistoryDebug {
    private static final String TAG = HistoryDebug.class.getSimpleName();

    private Context mContext;
    private PumpHistoryHandler pumpHistoryHandler;

    private int pumpRTC;
    private int pumpOFFSET;

    private byte[] eventData;

    public HistoryDebug(Context context, PumpHistoryHandler pumpHistoryHandler) {
        Log.d(TAG, "initialise HistoryDebug");

        this.mContext = context;
        this.pumpHistoryHandler = pumpHistoryHandler;
    }

    public void run() {

        //logcatCGM("20180104-caty-670G-2-weeks.json");
        //logcatPUMP("20180104-caty-670G-2-weeks.json");

        //logcatCGM("20180101-caty-670G-3-months.json");
        //logcatPUMP("20180101-caty-670G-3-months.json");

        history("20180101-caty-670G-3-months.json");

        //history("20180104-caty-670G-2-weeks.json");
    }

    public void history(String file) {
        try {
            read(file, "cbg_pages");
            calcRTCandOFFSET(new Date(System.currentTimeMillis()));
            new PumpHistoryParser(eventData).process(pumpHistoryHandler.getPumpHistorySender(), 0, pumpRTC, pumpOFFSET, 0, 0, 0, 0, 0);
            read(file, "pages");
            //new PumpHistoryParser(eventData).logcat();
            new PumpHistoryParser(eventData).process(pumpHistoryHandler.getPumpHistorySender(), 0, pumpRTC, pumpOFFSET, 0, 0, 0, 0, 0);
        } catch (Throwable ignored) {}
    }

    // file = json file stored in assets
    // type = "pages" for pump history
    // type = "cbg_pages" for cgm history

    public void read(String file, String type) {
        ByteArrayOutputStream events = new ByteArrayOutputStream();

        try {
            JSONObject jsonObject = new JSONObject(loadJSONFromAsset(file));

            Iterator<?> iterator = jsonObject.keys();
            while (iterator.hasNext()) {
                String obj = iterator.next().toString();
                Log.d(TAG, "" + obj);
            }

            JSONArray pages = jsonObject.getJSONArray(type);
            Log.d(TAG, "type: " + type + " count: " + pages.length());

            for (int i = 0; i < pages.length(); i++) {
                String hex = pages.getString(i);
                events.write(hexToByteArray(hex));
            }

            eventData = events.toByteArray();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void calcRTCandOFFSET(Date date) {

        // get RTC and OFFSET from last event
        int index = 0;
        int indexLast = 0;
        while (index < eventData.length) {
            indexLast = index;
            index += read8toUInt(eventData, index + 0x02);
        }

        pumpRTC = read32BEtoInt(eventData, indexLast + 0x03);
        int lastOFFSET = read32BEtoInt(eventData, indexLast + 0x07);
        pumpOFFSET = (int) MessageUtils.offsetFromTime(date.getTime(), pumpRTC & 0xFFFFFFFFL);
    }

    public void logcatCGM(String file) {
        read(file, "cbg_pages");
        new PumpHistoryParser(eventData).logcat();
    }

    public void logcatPUMP(String file) {
        read(file, "pages");
        new PumpHistoryParser(eventData).logcat();
    }

    private byte[] hexToByteArray(String hex) {
        hex = hex.length()%2 != 0?"0"+hex:hex;

        byte[] b = new byte[hex.length() / 2];

        for (int i = 0; i < b.length; i++) {
            int index = i * 2;
            int v = Integer.parseInt(hex.substring(index, index + 2), 16);
            b[i] = (byte) v;
        }
        return b;
    }

    private String loadJSONFromAsset(String file) {
        String json = null;

        try {
            InputStream is = mContext.getAssets().open(file);

            int size = is.available();

            byte[] buffer = new byte[size];

            is.read(buffer);
            is.close();

            json = new String(buffer, "UTF-8");

        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }

        return json;
    }

}

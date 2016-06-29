package info.nightscout.android.upload.nightscout.serializer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

import info.nightscout.android.model.CgmStatusEvent;

/**
 * Created by lgoedhart on 26/06/2016.
 */
public class EntriesSerializer implements JsonSerializer<CgmStatusEvent> {
    public static String getDirectionString(CgmStatusEvent.TREND trend) {
        switch( trend ) {
            case NONE:
                return "NONE";
            case DOUBLE_UP:
                return "DoubleUp";
            case SINGLE_UP:
                return "SingleUp";
            case FOURTY_FIVE_UP:
                return "FortyFiveUp";
            case FLAT:
                return "Flat";
            case FOURTY_FIVE_DOWN:
                return "FortyFiveDown";
            case SINGLE_DOWN:
                return "SingleDown";
            case DOUBLE_DOWN:
                return "DoubleDown";
            case NOT_COMPUTABLE:
                return "NOT COMPUTABLE";
            case RATE_OUT_OF_RANGE:
                return "RATE OUT OF RANGE";
            case NOT_SET:
                return "NONE";
            default:
                return "NOT COMPUTABLE"; // TODO - should this be something else?
        }
    }

    @Override
    public JsonElement serialize(CgmStatusEvent src, Type typeOfSrc, JsonSerializationContext context) {
        final JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("sgv", src.getSgv());
        jsonObject.addProperty("direction", getDirectionString(src.getTrend()));
        jsonObject.addProperty("device", src.getDeviceName());
        jsonObject.addProperty("type", "sgv");
        jsonObject.addProperty("date", src.getEventDate().getTime());
        jsonObject.addProperty("dateString", String.valueOf(src.getEventDate()));

        return jsonObject;
    }
}

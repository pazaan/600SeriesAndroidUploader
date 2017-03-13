package info.nightscout.api;



import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface GlucoseEndpoints {

    class GlucoseEntry {

        String type;
        String dateString;
        float date;
        float sgv;
        String direction;
        String device;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDateString() {
            return dateString;
        }

        public void setDateString(String dateString) {
            this.dateString = dateString;
        }

        public float getDate() {
            return date;
        }

        public void setDate(float date) {
            this.date = date;
        }

        public float getSgv() {
            return sgv;
        }

        public void setSgv(float sgv) {
            this.sgv = sgv;
        }

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }

        public String getDevice() {
            return device;
        }

        public void setDevice(String device) {
            this.device = device;
        }

        public GlucoseEntry() {  }
    }

    @Headers({
            "Accept: application/json",
            "Content-type: application/json"
    })
    @POST("/api/v1/entries")
    Call<ResponseBody> sendEntries(@Body List<GlucoseEntry> entries);

}




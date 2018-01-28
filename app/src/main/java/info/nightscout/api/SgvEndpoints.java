package info.nightscout.api;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface SgvEndpoints {

    class SgvEntry {
        String type;
        String dateString;
        long date;
        int sgv;
        String direction;
        String device;

        public void setType(String type) {
            this.type = type;
        }

        public void setDateString(String dateString) {
            this.dateString = dateString;
        }

        public void setDate(long date) {
            this.date = date;
        }

        public void setSgv(int sgv) {
            this.sgv = sgv;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }

        public void setDevice(String device) {
            this.device = device;
        }

        public SgvEntry() {  }
    }

    @Headers({
            "Accept: application/json",
            "Content-type: application/json"
    })
    @POST("/api/v1/entries")
    Call<ResponseBody> sendEntries(@Body List<SgvEntry> entries);
}




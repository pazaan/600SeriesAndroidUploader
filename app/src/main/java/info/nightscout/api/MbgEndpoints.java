package info.nightscout.api;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface MbgEndpoints {

    class MbgEntry {
        String type;
        String dateString;
        long date;
        int mbg;
        int sgv;
        String device;

        public MbgEntry() {  }

        public void setType(String type) {
            this.type = type;
        }

        public void setDateString(String dateString) {
            this.dateString = dateString;
        }

        public void setDate(long date) {
            this.date = date;
        }

        public void setMbg(int mbg) {
            this.mbg = mbg;
        }

        public void setSgv(int sgv) {
            this.sgv = sgv;
        }

        public void setDevice(String device) {
            this.device = device;
        }
    }

    @Headers({
            "Accept: application/json",
            "Content-type: application/json"
    })
    @POST("/api/v1/entries")
    Call<ResponseBody> sendEntries(@Body List<MbgEntry> entries);
}




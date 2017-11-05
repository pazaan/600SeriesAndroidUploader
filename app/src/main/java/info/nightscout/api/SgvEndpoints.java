package info.nightscout.api;

import com.google.gson.annotations.SerializedName;

import java.util.Date;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface SgvEndpoints {

    class SgvEntry {

        @SerializedName("_id")
        String _id;

        @SerializedName("type")
        String type;

        @SerializedName("dateString")
        String dateString;

        @SerializedName("date")
        long date;

        @SerializedName("sgv")
        int sgv;

        @SerializedName("direction")
        String direction;

        @SerializedName("device")
        String device;

        @SerializedName("key600")
        String key600;

        public void getId(String id) {
            this._id = id;
        }

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

        public void setKey600(String key600) {
            this.key600 = key600;
        }

        public SgvEntry() {  }
    }

    @GET("/api/v1/entries.json")
    Call<List<SgvEntry>> checkKey(@Query("find[key600]") String key);

    @DELETE("/api/v1/entries.json")
    Call<ResponseBody> deleteKey(@Query("find[key600]") String key);

    @Headers({
            "Accept: application/json",
            "Content-type: application/json"
    })
    @POST("/api/v1/entries")
    Call<ResponseBody> sendEntries(@Body List<SgvEntry> entries);
}




/*
public interface SgvEndpoints {

    class SgvEntry {
        String type;
        String dateString;
        long date;
        int sgv;
        String direction;
        String device;
        String key600;

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

        public void setKey600(String key600) {
            this.key600 = key600;
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

 */


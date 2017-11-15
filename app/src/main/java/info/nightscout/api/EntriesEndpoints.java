package info.nightscout.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Created by John on 3.11.17.
 */

public interface EntriesEndpoints {

    class Entry {

        @SerializedName("_id")
        String _id;

        @SerializedName("type")
        String type;

        @SerializedName("dateString")
        String dateString;

        @SerializedName("date")
        Long date;

        @SerializedName("sgv")
        Integer sgv;

        @SerializedName("mbg")
        Integer mbg;

        @SerializedName("direction")
        String direction;

        @SerializedName("device")
        String device;

        @SerializedName("key600")
        String key600;

        public String get_id() {
            return _id;
        }

        public void set_id(String _id) {
            this._id = _id;
        }

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

        public Long getDate() {
            return date;
        }

        public void setDate(Long date) {
            this.date = date;
        }

        public Integer getSgv() {
            return sgv;
        }

        public void setSgv(Integer sgv) {
            this.sgv = sgv;
        }

        public Integer getMbg() {
            return mbg;
        }

        public void setMbg(Integer mbg) {
            this.mbg = mbg;
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

        public String getKey600() {
            return key600;
        }

        public void setKey600(String key600) {
            this.key600 = key600;
        }

        public Entry() {  }
    }

    @GET("/api/v1/entries/sgv.json")
    Call<List<Entry>> checkKey(@Query("find[dateString][$gte]") String date, @Query("find[key600]") String key);

    @DELETE("/api/v1/entries/sgv.json")
    Call<ResponseBody> deleteKey(@Query("find[dateString][$gte]") String date, @Query("find[key600]") String key);

    // bulk delete
    @DELETE("/api/v1/entries/sgv.json")
    Call<ResponseBody> deleteDateRange(@Query("find[date][$gte]") String from, @Query("find[date][$lte]") String to);

//    https://nsnemo.herokuapp.com/api/v1/entries/sgv.json?find[date][$gte]=1510110000000&find[date][$lte]=1510120000000&count=100

//    @DELETE("/api/v1/entries/{id}")
//    Call<ResponseBody> deleteID(@Path("id") String id);

    @Headers({
            "Accept: application/json",
            "Content-type: application/json"
    })

    @POST("/api/v1/entries")
    Call<ResponseBody> sendEntry(@Body Entry entry);

    @POST("/api/v1/entries")
    Call<ResponseBody> sendEntries(@Body List<Entry> entries);
}

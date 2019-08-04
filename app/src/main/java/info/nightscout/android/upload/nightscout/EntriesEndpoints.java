package info.nightscout.android.upload.nightscout;

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

/**
 * Created by Pogman on 3.11.17.
 */

public interface EntriesEndpoints {

    class Entry {

        @SerializedName("_id")
        private String _id;

        @SerializedName("type")
        private String type;

        @SerializedName("dateString")
        private String dateString;

        @SerializedName("date")
        private Long date;

        @SerializedName("sgv")
        private Integer sgv;

        @SerializedName("mbg")
        private Integer mbg;

        @SerializedName("direction")
        private String direction;

        @SerializedName("device")
        private String device;

        @SerializedName("key600")
        private String key600;

        @SerializedName("pumpMAC600")
        private String pumpMAC600;

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

        public void setDateString(Date date) {
            this.dateString = NightscoutUploadProcess.formatDateForNS(date);
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

        public String getPumpMAC600() {
            return pumpMAC600;
        }

        public void setPumpMAC600(String pumpMAC600) {
            this.pumpMAC600 = pumpMAC600;
        }

        public Entry() {  }
    }

    // find entry using key
    @GET("/api/v1/entries/sgv.json")
    Call<List<Entry>> findKey(@Query("find[date][$gte]") String date,
                              @Query("find[key600]") String key);

    // find entry using key within date range
    @GET("/api/v1/entries/sgv.json")
    Call<List<Entry>> findKey(@Query("find[date][$gte]") String from,
                              @Query("find[date][$lte]") String to,
                              @Query("find[key600]") String key);

    // delete entry using id
    @DELETE("/api/v1/entries/sgv.json")
    Call<ResponseBody> deleteID(@Query("find[date]") String date,
                                @Query("find[_id]") String id);

    // delete entry using key
    @DELETE("/api/v1/entries/sgv.json")
    Call<ResponseBody> deleteKey(@Query("find[date]") String date,
                                 @Query("find[key600]") String key);

    // delete entry using key and mac
    @DELETE("/api/v1/entries/sgv.json")
    Call<ResponseBody> deleteKeyMac(@Query("find[date]") String date,
                                    @Query("find[key600]") String key,
                                    @Query("find[pumpMAC600]") String mac);

    // delete entry using key and mac not exist
    @DELETE("/api/v1/entries/sgv.json")
    Call<ResponseBody> deleteKeyNoMac(@Query("find[date]") String date,
                                      @Query("find[key600]") String key,
                                      @Query("find[pumpMAC600][$not][$exists]") String empty);

    // delete entry using key within date range
    @DELETE("/api/v1/entries/sgv.json")
    Call<ResponseBody> deleteKey(@Query("find[date][$gte]") String from,
                                 @Query("find[date][$lte]") String to,
                                 @Query("find[key600]") String key);

    // bulk delete
    @DELETE("/api/v1/entries/sgv.json")
    Call<ResponseBody> deleteDateRange(@Query("find[date][$gte]") String from,
                                       @Query("find[date][$lte]") String to);

    // bulk delete non-keyed entries, ignore key600 field
    @DELETE("/api/v1/entries/sgv.json")
    Call<ResponseBody> deleteCleanupItemsNonKeyed(@Query("find[date][$gte]") String from,
                                                  @Query("find[date][$lte]") String to,
                                                  @Query("find[key600][$not][$exists]") String empty);

    // bulk delete non-keyed entries
    @DELETE("/api/v1/entries/sgv.json")
    Call<ResponseBody> deleteCleanupItems(@Query("find[date][$gte]") String from,
                                          @Query("find[date][$lte]") String to);

    @Headers({
            "Accept: application/json",
            "Content-type: application/json"
    })

    // post single entry
    @POST("/api/v1/entries")
    Call<ResponseBody> sendEntry(@Body Entry entry);

    // post bulk entries
    @POST("/api/v1/entries")
    Call<ResponseBody> sendEntries(@Body List<Entry> entries);
}

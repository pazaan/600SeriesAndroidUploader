package info.nightscout.android.upload.nightscout;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.util.Date;
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
 * Created by Pogman on 3.11.17.
 */

public interface TreatmentsEndpoints {

    class Treatment {

        @SerializedName("_id")
        private String _id;

        @SerializedName("key600")
        private String key600;

        @SerializedName("pumpMAC600")
        private String pumpMAC600;

        @SerializedName("eventType")
        private String eventType;

        @SerializedName("created_at")
        private String created_at;

        @SerializedName("device")
        private String device;

        @SerializedName("notes")
        private String notes;

        @SerializedName("enteredBy")
        private String enteredBy;

        @SerializedName("reason")
        private String reason;

        @SerializedName("profile")
        private String profile;

        @SerializedName("enteredinsulin")
        private String enteredinsulin;

        @SerializedName("splitNow")
        private String splitNow;

        @SerializedName("splitExt")
        private String splitExt;

        @SerializedName("units")
        private String units;

        @SerializedName("glucoseType")
        private String glucoseType;

        @SerializedName("insulin")
        private Float insulin;

        @SerializedName("duration")
        private Float duration;

        @SerializedName("absolute")
        private Float absolute;

        @SerializedName("percent")
        private Float percent;

        @SerializedName("relative")
        private Float relative;

        @SerializedName("preBolus")
        private Float preBolus;

        @SerializedName("carbs")
        private Float carbs;

        @SerializedName("glucose")
        private BigDecimal glucose;

        @SerializedName("isAnnouncement")
        private Boolean isAnnouncement;

        public String get_id() {
            return _id;
        }

        public void set_id(String _id) {
            this._id = _id;
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

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getCreated_at() {
            return created_at;
        }

        public String getDevice() {
            return device;
        }

        public void setDevice(String device) {
            this.device = device;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }

        public String getEnteredBy() {
            return enteredBy;
        }

        public void setEnteredBy(String enteredBy) {
            this.enteredBy = enteredBy;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public String getProfile() {
            return profile;
        }

        public void setProfile(String profile) {
            this.profile = profile;
        }

        public String getEnteredinsulin() {
            return enteredinsulin;
        }

        public void setEnteredinsulin(String enteredinsulin) {
            this.enteredinsulin = enteredinsulin;
        }

        public String getSplitNow() {
            return splitNow;
        }

        public void setSplitNow(String splitNow) {
            this.splitNow = splitNow;
        }

        public String getSplitExt() {
            return splitExt;
        }

        public void setSplitExt(String splitExt) {
            this.splitExt = splitExt;
        }

        public String getUnits() {
            return units;
        }

        public void setUnits(String units) {
            this.units = units;
        }

        public String getGlucoseType() {
            return glucoseType;
        }

        public void setGlucoseType(String glucoseType) {
            this.glucoseType = glucoseType;
        }

        public Float getInsulin() {
            return insulin;
        }

        public void setInsulin(Float insulin) {
            this.insulin = insulin;
        }

        public Float getDuration() {
            return duration;
        }

        public void setDuration(Float duration) {
            this.duration = duration;
        }

        public Float getAbsolute() {
            return absolute;
        }

        public void setAbsolute(Float absolute) {
            this.absolute = absolute;
        }

        public Float getPercent() {
            return percent;
        }

        public void setPercent(Float percent) {
            this.percent = percent;
        }

        public Float getRelative() {
            return relative;
        }

        public void setRelative(Float relative) {
            this.relative = relative;
        }

        public Float getPreBolus() {
            return preBolus;
        }

        public void setPreBolus(Float preBolus) {
            this.preBolus = preBolus;
        }

        public Float getCarbs() {
            return carbs;
        }

        public void setCarbs(Float carbs) {
            this.carbs = carbs;
        }

        public BigDecimal getGlucose() {
            return glucose;
        }

        public void setGlucose(BigDecimal glucose) {
            this.glucose = glucose;
        }

        public Boolean getAnnouncement() {
            return isAnnouncement;
        }

        public void setAnnouncement(Boolean announcement) {
            isAnnouncement = announcement;
        }

        public void setCreated_at(Date created_at) {
            this.created_at = NightscoutUploadProcess.formatDateForNS(created_at);
        }
    }

    // https://docs.mongodb.com/v3.6/reference/operator/query/

    // find treatments using key
    @GET("/api/v1/treatments.json")
    Call<List<Treatment>> findKey(@Query("find[created_at][$gte]") String from,
                                  @Query("find[key600]") String key);

    // find treatments using key within date range
    @GET("/api/v1/treatments.json")
    Call<List<Treatment>> findKey(@Query("find[created_at][$gte]") String from,
                                  @Query("find[created_at][$lte]") String to,
                                  @Query("find[key600]") String key);

    // find treatments using date range
    @GET("/api/v1/treatments.json")
    Call<List<Treatment>> findDateRangeCount(@Query("find[created_at][$gte]") String from,
                                             @Query("find[created_at][$lte]") String to,
                                             @Query("count") String count);

    // find non-keyed treatments exclude by eventType
    @GET("/api/v1/treatments.json")
    Call<List<Treatment>> findCleanupItems(@Query("find[created_at][$gte]") String from,
                                           @Query("find[created_at][$lte]") String to,
                                           @Query("find[eventType][$ne]") String type,
                                           @Query("find[key600][$not][$exists]") String empty,
                                           @Query("count") String count);

    // find treatment using partial key within date range
    @GET("/api/v1/treatments.json")
    Call<List<Treatment>> findKeyRegex(@Query("find[created_at][$gte]") String from,
                                       @Query("find[created_at][$lte]") String to,
                                       @Query("find[key600][$regex]") String key,
                                       @Query("count") String count);

    @GET("/api/v1/treatments.json")
    Call<List<Treatment>> findNotesRegex(@Query("find[created_at][$gte]") String from,
                                         @Query("find[created_at][$lte]") String to,
                                         @Query("find[notes][$regex]") String notes,
                                         @Query("count") String count);

    @GET("/api/v1/treatments.json")
    Call<List<Treatment>> findKeyRegexNoPumpMAC(@Query("find[created_at][$gte]") String from,
                                                @Query("find[created_at][$lte]") String to,
                                                @Query("find[key600][$regex]") String key,
                                                @Query("find[pumpMAC600][$not][$exists]") String pumpMAC600,
                                                @Query("count") String count);

    @GET("/api/v1/treatments.json")
    Call<List<Treatment>> findNotesRegexNoPumpMAC(@Query("find[created_at][$gte]") String from,
                                                  @Query("find[created_at][$lte]") String to,
                                                  @Query("find[notes][$regex]") String notes,
                                                  @Query("find[key600][$exists]") String key600,
                                                  @Query("find[pumpMAC600][$not][$exists]") String pumpMAC600,
                                                  @Query("count") String count);

    // NS v0.11.1 delete handling changed to work as query based
    // regression caused the id based method to be limited to 4 days max causing deletes older then this to not complete

    // delete using id
    @DELETE("/api/v1/treatments/{id}")
    Call<ResponseBody> deleteID(@Path("id") String id);

    // query based delete
    @DELETE("/api/v1/treatments.json")
    Call<ResponseBody> deleteID(@Query("find[created_at]") String date,
                                @Query("find[_id]") String id);

    @Headers({
            "Accept: application/json",
            "Content-type: application/json"
    })

    // post single treatment
    @POST("/api/v1/treatments")
    Call<ResponseBody> sendTreatment(@Body Treatment treatment);

    // post bulk treatments
    @POST("/api/v1/treatments")
    Call<ResponseBody> sendTreatments(@Body List<Treatment> treatments);
}

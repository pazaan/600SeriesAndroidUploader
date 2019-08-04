package info.nightscout.android.upload.nightscout;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.Date;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Created by Pogman on 7.11.17.
 */

public interface ProfileEndpoints {

    class Profile {

        @SerializedName("_id")
        private String _id;

        @SerializedName("key600")
        private String key600;

        @SerializedName("pumpMAC600")
        private String pumpMAC600;

        @SerializedName("defaultProfile")
        private String defaultProfile;

        @SerializedName("startDate")
        private String startDate;

        @SerializedName("mills")
        private String mills;

        @SerializedName("units")
        private String units;

        @SerializedName("created_at")
        private String created_at;

        @SerializedName("store")
        @Expose
        private Map<String, BasalProfile> basalProfileMap;

        public Map<String, BasalProfile> getBasalProfileMap() {
            return basalProfileMap;
        }

        public void setBasalProfileMap(Map<String, BasalProfile> basalProfileMap) {
            this.basalProfileMap = basalProfileMap;
        }

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

        public String getDefaultProfile() {
            return defaultProfile;
        }

        public void setDefaultProfile(String defaultProfile) {
            this.defaultProfile = defaultProfile;
        }

        public String getStartDate() {
            return startDate;
        }

        public void setStartDate(String startDate) {
            this.startDate = startDate;
        }

        public String getMills() {
            return mills;
        }

        public void setMills(String mills) {
            this.mills = mills;
        }

        public String getUnits() {
            return units;
        }

        public void setUnits(String units) {
            this.units = units;
        }

        public String getCreated_at() {
            return created_at;
        }

        public void setCreated_at(String created_at) {
            this.created_at = created_at;
        }

        public void setCreated_at(Date created_at) {
            this.created_at = NightscoutUploadProcess.formatDateForNS(created_at);
        }

        public void setStartDate(Date startDate) {
            this.startDate = NightscoutUploadProcess.formatDateForNS(startDate);
        }
    }

    class BasalProfile {

        @SerializedName("startDate")
        private String startDate;

        @SerializedName("timezone")
        private String timezone;

        @SerializedName("units")
        private String units;

        @SerializedName("carbs_hr")
        private String carbs_hr;

        @SerializedName("delay")
        private String delay;

        @SerializedName("dia")
        private String dia;

        @SerializedName("basal")
        private List<TimePeriod> basal;

        @SerializedName("carbratio")
        List<TimePeriod> carbratio;

        @SerializedName("sens")
        private List<TimePeriod> sens;

        @SerializedName("target_low")
        private List<TimePeriod> target_low;

        @SerializedName("target_high")
        private List<TimePeriod> target_high;

        public String getStartDate() {
            return startDate;
        }

        public void setStartDate(String startDate) {
            this.startDate = startDate;
        }

        public String getTimezone() {
            return timezone;
        }

        public void setTimezone(String timezone) {
            this.timezone = timezone;
        }

        public String getUnits() {
            return units;
        }

        public void setUnits(String units) {
            this.units = units;
        }

        public String getCarbs_hr() {
            return carbs_hr;
        }

        public void setCarbs_hr(String carbs_hr) {
            this.carbs_hr = carbs_hr;
        }

        public String getDelay() {
            return delay;
        }

        public void setDelay(String delay) {
            this.delay = delay;
        }

        public String getDia() {
            return dia;
        }

        public void setDia(String dia) {
            this.dia = dia;
        }

        public List getBasal() {
            return basal;
        }

        public void setBasal(List<TimePeriod> basal) {
            this.basal = basal;
        }

        public List getCarbratio() {
            return carbratio;
        }

        public void setCarbratio(List<TimePeriod> carbratio) {
            this.carbratio = carbratio;
        }

        public List getSens() {
            return sens;
        }

        public void setSens(List<TimePeriod> sens) {
            this.sens = sens;
        }

        public List getTarget_low() {
            return target_low;
        }

        public void setTarget_low(List<TimePeriod> target_low) {
            this.target_low = target_low;
        }

        public List getTarget_high() {
            return target_high;
        }

        public void setTarget_high(List<TimePeriod> target_high) {
            this.target_high = target_high;
        }

        public void setStartDate(Date startDate) {
            this.startDate = NightscoutUploadProcess.formatDateForNS(startDate);
        }
    }

    class TimePeriod {

        @SerializedName("time")
        private String time;

        @SerializedName("value")
        private String value;

        @SerializedName("timeAsSeconds")
        private String timeAsSeconds;

        public String getTime() {
            return time;
        }

        public void setTime(String time) {
            this.time = time;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getTimeAsSeconds() {
            return timeAsSeconds;
        }

        public void setTimeAsSeconds(String timeAsSeconds) {
            this.timeAsSeconds = timeAsSeconds;
        }
    }

    @GET("/api/v1/profile.json")
    Call<List<Profile>> getProfiles();

    @DELETE("/api/v1/profile/{id}")
    Call<ResponseBody> deleteID(@Path("id") String id);

    @Headers({
            "Accept: application/json",
            "Content-type: application/json"
    })

    @POST("/api/v1/profile")
    Call<ResponseBody> sendProfile(@Body Profile profile);

    @POST("/api/v1/profile")
    Call<ResponseBody> sendProfiles(@Body List<Profile> profiles);
}
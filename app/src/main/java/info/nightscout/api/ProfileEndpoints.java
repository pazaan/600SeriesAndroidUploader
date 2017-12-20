package info.nightscout.api;

        import com.google.gson.annotations.SerializedName;

        import java.text.SimpleDateFormat;
        import java.util.Date;
        import java.util.List;
        import java.util.Locale;

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
        String _id;
        @SerializedName("key600")
        String key600;
        @SerializedName("defaultProfile")
        String defaultProfile;
        @SerializedName("startDate")
        String startDate;
        @SerializedName("mills")
        String mills;
        @SerializedName("units")
        String units;
        @SerializedName("created_at")
        String created_at;
        @SerializedName("store")
        Store store;

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

        public Store getStore() {
            return store;
        }

        public void setStore(Store store) {
            this.store = store;
        }

        public void setCreated_at(Date created_at) {
            this.created_at = ISO8601_DATE_FORMAT.format(created_at);
        }

        public void setStartDate(Date startDate) {
            this.startDate = ISO8601_DATE_FORMAT.format(startDate);
        }

        private static final SimpleDateFormat ISO8601_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());
    }

    class Store {
        @SerializedName("Basal 1")
        BasalProfile basal1;
        @SerializedName("Basal 2")
        BasalProfile basal2;
        @SerializedName("Basal 3")
        BasalProfile basal3;
        @SerializedName("Basal 4")
        BasalProfile basal4;
        @SerializedName("Basal 5")
        BasalProfile basal5;
        @SerializedName("Workday")
        BasalProfile workday;
        @SerializedName("Day Off")
        BasalProfile dayoff;
        @SerializedName("Sick Day")
        BasalProfile sickday;

        public BasalProfile getBasal1() {
            return basal1;
        }

        public void setBasal1(BasalProfile basal1) {
            this.basal1 = basal1;
        }

        public BasalProfile getBasal2() {
            return basal2;
        }

        public void setBasal2(BasalProfile basal2) {
            this.basal2 = basal2;
        }

        public BasalProfile getBasal3() {
            return basal3;
        }

        public void setBasal3(BasalProfile basal3) {
            this.basal3 = basal3;
        }

        public BasalProfile getBasal4() {
            return basal4;
        }

        public void setBasal4(BasalProfile basal4) {
            this.basal4 = basal4;
        }

        public BasalProfile getBasal5() {
            return basal5;
        }

        public void setBasal5(BasalProfile basal5) {
            this.basal5 = basal5;
        }

        public BasalProfile getWorkday() {
            return workday;
        }

        public void setWorkday(BasalProfile workday) {
            this.workday = workday;
        }

        public BasalProfile getDayoff() {
            return dayoff;
        }

        public void setDayoff(BasalProfile dayoff) {
            this.dayoff = dayoff;
        }

        public BasalProfile getSickday() {
            return sickday;
        }

        public void setSickday(BasalProfile sickday) {
            this.sickday = sickday;
        }
    }

    class BasalProfile {
        @SerializedName("startDate")
        String startDate;
        @SerializedName("timezone")
        String timezone;
        @SerializedName("units")
        String units;
        @SerializedName("carbs_hr")
        String carbs_hr;
        @SerializedName("delay")
        String delay;
        @SerializedName("dia")
        String dia;
        @SerializedName("basal")
        List<TimePeriod> basal;
        @SerializedName("carbratio")
        List<TimePeriod> carbratio;
        @SerializedName("sens")
        List<TimePeriod> sens;
        @SerializedName("target_low")
        List<TimePeriod> target_low;
        @SerializedName("target_high")
        List<TimePeriod> target_high;

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
            this.startDate = ISO8601_DATE_FORMAT.format(startDate);
        }

        private static final SimpleDateFormat ISO8601_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());
    }

    class TimePeriod {
        @SerializedName("time")
        String time;
        @SerializedName("value")
        String value;
        @SerializedName("timeAsSeconds")
        String timeAsSeconds;

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
package info.nightscout.api;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;

/**
 * Created by Pogman on 21.1.18.
 */

public interface StatusEndpoints {

    class Status {

        @SerializedName("status")
        @Expose
        private String status;
        @SerializedName("name")
        @Expose
        private String name;
        @SerializedName("version")
        @Expose
        private String version;
        @SerializedName("serverTime")
        @Expose
        private String serverTime;
        @SerializedName("serverTimeEpoch")
        @Expose
        private Long serverTimeEpoch;
        @SerializedName("apiEnabled")
        @Expose
        private Boolean apiEnabled;
        @SerializedName("authorized")
        @Expose
        private String authorized;
        @SerializedName("careportalEnabled")
        @Expose
        private Boolean careportalEnabled;
        @SerializedName("boluscalcEnabled")
        @Expose
        private Boolean boluscalcEnabled;
        @SerializedName("head")
        @Expose
        private String head;
        @SerializedName("settings")
        @Expose
        private Settings settings;
        @SerializedName("extendedSettings")
        @Expose
        private ExtendedSettings extendedSettings;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getServerTime() {
            return serverTime;
        }

        public void setServerTime(String serverTime) {
            this.serverTime = serverTime;
        }

        public Long getServerTimeEpoch() {
            return serverTimeEpoch;
        }

        public void setServerTimeEpoch(Long serverTimeEpoch) {
            this.serverTimeEpoch = serverTimeEpoch;
        }

        public Boolean isApiEnabled() {
            return apiEnabled;
        }

        public void setApiEnabled(Boolean apiEnabled) {
            this.apiEnabled = apiEnabled;
        }

        public String getAuthorized() {
            return authorized;
        }

        public void setAuthorized(String authorized) {
            this.authorized = authorized;
        }

        public Boolean isCareportalEnabled() {
            return careportalEnabled;
        }

        public void setCareportalEnabled(Boolean careportalEnabled) {
            this.careportalEnabled = careportalEnabled;
        }

        public Boolean isBoluscalcEnabled() {
            return boluscalcEnabled;
        }

        public void setBoluscalcEnabled(Boolean boluscalcEnabled) {
            this.boluscalcEnabled = boluscalcEnabled;
        }

        public String getHead() {
            return head;
        }

        public void setHead(String head) {
            this.head = head;
        }

        public Settings getSettings() {
            return settings;
        }

        public void setSettings(Settings settings) {
            this.settings = settings;
        }

        public ExtendedSettings getExtendedSettings() {
            return extendedSettings;
        }

        public void setExtendedSettings(ExtendedSettings extendedSettings) {
            this.extendedSettings = extendedSettings;
        }

    }

    class Settings {

        @SerializedName("units")
        @Expose
        private String units;
        @SerializedName("timeFormat")
        @Expose
        private Long timeFormat;
        @SerializedName("nightMode")
        @Expose
        private Boolean nightMode;
        @SerializedName("editMode")
        @Expose
        private Boolean editMode;
        @SerializedName("showRawbg")
        @Expose
        private String showRawbg;
        @SerializedName("customTitle")
        @Expose
        private String customTitle;
        @SerializedName("theme")
        @Expose
        private String theme;
        @SerializedName("alarmUrgentHigh")
        @Expose
        private Boolean alarmUrgentHigh;
        @SerializedName("alarmUrgentHighMins")
        @Expose
        private List<Long> alarmUrgentHighMins = null;
        @SerializedName("alarmHigh")
        @Expose
        private Boolean alarmHigh;
        @SerializedName("alarmHighMins")
        @Expose
        private List<Long> alarmHighMins = null;
        @SerializedName("alarmLow")
        @Expose
        private Boolean alarmLow;
        @SerializedName("alarmLowMins")
        @Expose
        private List<Long> alarmLowMins = null;
        @SerializedName("alarmUrgentLow")
        @Expose
        private Boolean alarmUrgentLow;
        @SerializedName("alarmUrgentLowMins")
        @Expose
        private List<Long> alarmUrgentLowMins = null;
        @SerializedName("alarmUrgentMins")
        @Expose
        private List<Long> alarmUrgentMins = null;
        @SerializedName("alarmWarnMins")
        @Expose
        private List<Long> alarmWarnMins = null;
        @SerializedName("alarmTimeagoWarn")
        @Expose
        private Boolean alarmTimeagoWarn;
        @SerializedName("alarmTimeagoWarnMins")
        @Expose
        private Long alarmTimeagoWarnMins;
        @SerializedName("alarmTimeagoUrgent")
        @Expose
        private Boolean alarmTimeagoUrgent;
        @SerializedName("alarmTimeagoUrgentMins")
        @Expose
        private Long alarmTimeagoUrgentMins;
        @SerializedName("language")
        @Expose
        private String language;
        @SerializedName("scaleY")
        @Expose
        private String scaleY;
        @SerializedName("showPlugins")
        @Expose
        private String showPlugins;
        @SerializedName("showForecast")
        @Expose
        private String showForecast;
        @SerializedName("focusHours")
        @Expose
        private Long focusHours;
        @SerializedName("heartbeat")
        @Expose
        private Long heartbeat;
        @SerializedName("baseURL")
        @Expose
        private String baseURL;
        @SerializedName("authDefaultRoles")
        @Expose
        private String authDefaultRoles;
        @SerializedName("thresholds")
        @Expose
        private Thresholds thresholds;
        @SerializedName("DEFAULT_FEATURES")
        @Expose
        private List<String> dEFAULTFEATURES = null;
        @SerializedName("alarmTypes")
        @Expose
        private List<String> alarmTypes = null;
        @SerializedName("enable")
        @Expose
        private List<String> enable = null;

        public String getUnits() {
            return units;
        }

        public void setUnits(String units) {
            this.units = units;
        }

        public Long getTimeFormat() {
            return timeFormat;
        }

        public void setTimeFormat(Long timeFormat) {
            this.timeFormat = timeFormat;
        }

        public Boolean isNightMode() {
            return nightMode;
        }

        public void setNightMode(Boolean nightMode) {
            this.nightMode = nightMode;
        }

        public Boolean isEditMode() {
            return editMode;
        }

        public void setEditMode(Boolean editMode) {
            this.editMode = editMode;
        }

        public String getShowRawbg() {
            return showRawbg;
        }

        public void setShowRawbg(String showRawbg) {
            this.showRawbg = showRawbg;
        }

        public String getCustomTitle() {
            return customTitle;
        }

        public void setCustomTitle(String customTitle) {
            this.customTitle = customTitle;
        }

        public String getTheme() {
            return theme;
        }

        public void setTheme(String theme) {
            this.theme = theme;
        }

        public Boolean isAlarmUrgentHigh() {
            return alarmUrgentHigh;
        }

        public void setAlarmUrgentHigh(Boolean alarmUrgentHigh) {
            this.alarmUrgentHigh = alarmUrgentHigh;
        }

        public List<Long> getAlarmUrgentHighMins() {
            return alarmUrgentHighMins;
        }

        public void setAlarmUrgentHighMins(List<Long> alarmUrgentHighMins) {
            this.alarmUrgentHighMins = alarmUrgentHighMins;
        }

        public Boolean isAlarmHigh() {
            return alarmHigh;
        }

        public void setAlarmHigh(Boolean alarmHigh) {
            this.alarmHigh = alarmHigh;
        }

        public List<Long> getAlarmHighMins() {
            return alarmHighMins;
        }

        public void setAlarmHighMins(List<Long> alarmHighMins) {
            this.alarmHighMins = alarmHighMins;
        }

        public Boolean isAlarmLow() {
            return alarmLow;
        }

        public void setAlarmLow(Boolean alarmLow) {
            this.alarmLow = alarmLow;
        }

        public List<Long> getAlarmLowMins() {
            return alarmLowMins;
        }

        public void setAlarmLowMins(List<Long> alarmLowMins) {
            this.alarmLowMins = alarmLowMins;
        }

        public Boolean isAlarmUrgentLow() {
            return alarmUrgentLow;
        }

        public void setAlarmUrgentLow(Boolean alarmUrgentLow) {
            this.alarmUrgentLow = alarmUrgentLow;
        }

        public List<Long> getAlarmUrgentLowMins() {
            return alarmUrgentLowMins;
        }

        public void setAlarmUrgentLowMins(List<Long> alarmUrgentLowMins) {
            this.alarmUrgentLowMins = alarmUrgentLowMins;
        }

        public List<Long> getAlarmUrgentMins() {
            return alarmUrgentMins;
        }

        public void setAlarmUrgentMins(List<Long> alarmUrgentMins) {
            this.alarmUrgentMins = alarmUrgentMins;
        }

        public List<Long> getAlarmWarnMins() {
            return alarmWarnMins;
        }

        public void setAlarmWarnMins(List<Long> alarmWarnMins) {
            this.alarmWarnMins = alarmWarnMins;
        }

        public Boolean isAlarmTimeagoWarn() {
            return alarmTimeagoWarn;
        }

        public void setAlarmTimeagoWarn(Boolean alarmTimeagoWarn) {
            this.alarmTimeagoWarn = alarmTimeagoWarn;
        }

        public Long getAlarmTimeagoWarnMins() {
            return alarmTimeagoWarnMins;
        }

        public void setAlarmTimeagoWarnMins(Long alarmTimeagoWarnMins) {
            this.alarmTimeagoWarnMins = alarmTimeagoWarnMins;
        }

        public Boolean isAlarmTimeagoUrgent() {
            return alarmTimeagoUrgent;
        }

        public void setAlarmTimeagoUrgent(Boolean alarmTimeagoUrgent) {
            this.alarmTimeagoUrgent = alarmTimeagoUrgent;
        }

        public Long getAlarmTimeagoUrgentMins() {
            return alarmTimeagoUrgentMins;
        }

        public void setAlarmTimeagoUrgentMins(Long alarmTimeagoUrgentMins) {
            this.alarmTimeagoUrgentMins = alarmTimeagoUrgentMins;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getScaleY() {
            return scaleY;
        }

        public void setScaleY(String scaleY) {
            this.scaleY = scaleY;
        }

        public String getShowPlugins() {
            return showPlugins;
        }

        public void setShowPlugins(String showPlugins) {
            this.showPlugins = showPlugins;
        }

        public String getShowForecast() {
            return showForecast;
        }

        public void setShowForecast(String showForecast) {
            this.showForecast = showForecast;
        }

        public Long getFocusHours() {
            return focusHours;
        }

        public void setFocusHours(Long focusHours) {
            this.focusHours = focusHours;
        }

        public Long getHeartbeat() {
            return heartbeat;
        }

        public void setHeartbeat(Long heartbeat) {
            this.heartbeat = heartbeat;
        }

        public String getBaseURL() {
            return baseURL;
        }

        public void setBaseURL(String baseURL) {
            this.baseURL = baseURL;
        }

        public String getAuthDefaultRoles() {
            return authDefaultRoles;
        }

        public void setAuthDefaultRoles(String authDefaultRoles) {
            this.authDefaultRoles = authDefaultRoles;
        }

        public Thresholds getThresholds() {
            return thresholds;
        }

        public void setThresholds(Thresholds thresholds) {
            this.thresholds = thresholds;
        }

        public List<String> getDEFAULTFEATURES() {
            return dEFAULTFEATURES;
        }

        public void setDEFAULTFEATURES(List<String> dEFAULTFEATURES) {
            this.dEFAULTFEATURES = dEFAULTFEATURES;
        }

        public List<String> getAlarmTypes() {
            return alarmTypes;
        }

        public void setAlarmTypes(List<String> alarmTypes) {
            this.alarmTypes = alarmTypes;
        }

        public List<String> getEnable() {
            return enable;
        }

        public void setEnable(List<String> enable) {
            this.enable = enable;
        }

    }

    class Thresholds {

        @SerializedName("bgHigh")
        @Expose
        private Long bgHigh;
        @SerializedName("bgTargetTop")
        @Expose
        private Long bgTargetTop;
        @SerializedName("bgTargetBottom")
        @Expose
        private Long bgTargetBottom;
        @SerializedName("bgLow")
        @Expose
        private Long bgLow;

        public Long getBgHigh() {
            return bgHigh;
        }

        public void setBgHigh(Long bgHigh) {
            this.bgHigh = bgHigh;
        }

        public Long getBgTargetTop() {
            return bgTargetTop;
        }

        public void setBgTargetTop(Long bgTargetTop) {
            this.bgTargetTop = bgTargetTop;
        }

        public Long getBgTargetBottom() {
            return bgTargetBottom;
        }

        public void setBgTargetBottom(Long bgTargetBottom) {
            this.bgTargetBottom = bgTargetBottom;
        }

        public Long getBgLow() {
            return bgLow;
        }

        public void setBgLow(Long bgLow) {
            this.bgLow = bgLow;
        }

    }

    class ExtendedSettings {

        @SerializedName("pump")
        @Expose
        private Pump pump;
        @SerializedName("basal")
        @Expose
        private Basal basal;
        @SerializedName("profile")
        @Expose
        private Profile profile;
        @SerializedName("devicestatus")
        @Expose
        private Devicestatus devicestatus;

        public Pump getPump() {
            return pump;
        }

        public void setPump(Pump pump) {
            this.pump = pump;
        }

        public Basal getBasal() {
            return basal;
        }

        public void setBasal(Basal basal) {
            this.basal = basal;
        }

        public Profile getProfile() {
            return profile;
        }

        public void setProfile(Profile profile) {
            this.profile = profile;
        }

        public Devicestatus getDevicestatus() {
            return devicestatus;
        }

        public void setDevicestatus(Devicestatus devicestatus) {
            this.devicestatus = devicestatus;
        }

    }

    class Pump {

        @SerializedName("fields")
        @Expose
        private String fields;

        public String getFields() {
            return fields;
        }

        public void setFields(String fields) {
            this.fields = fields;
        }

    }

    class Basal {

        @SerializedName("render")
        @Expose
        private String render;

        public String getRender() {
            return render;
        }

        public void setRender(String render) {
            this.render = render;
        }

    }

    class Profile {

        @SerializedName("history")
        @Expose
        private Boolean history;
        @SerializedName("multiple")
        @Expose
        private Boolean multiple;

        public Boolean isHistory() {
            return history;
        }

        public void setHistory(Boolean history) {
            this.history = history;
        }

        public Boolean isMultiple() {
            return multiple;
        }

        public void setMultiple(Boolean multiple) {
            this.multiple = multiple;
        }

    }

    class Devicestatus {

        @SerializedName("advanced")
        @Expose
        private Boolean advanced;

        public Boolean isAdvanced() {
            return advanced;
        }

        public void setAdvanced(Boolean advanced) {
            this.advanced = advanced;
        }

    }

    @Headers({
            "Accept: application/json",
            "Content-type: application/json"
    })

    @GET("/api/v1/status.json")
    Call<Status> getStatus();
}

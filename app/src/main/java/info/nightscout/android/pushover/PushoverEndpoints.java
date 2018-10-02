package info.nightscout.android.pushover;

import com.google.gson.annotations.SerializedName;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

/**
 * Created by Pogman on 14.4.18.
 */

public interface PushoverEndpoints {

    class Message {

        @SerializedName("token")
        private String token;

        @SerializedName("user")
        private String user;

        @SerializedName("message")
        private String message;

        @SerializedName("device")
        private String device;

        @SerializedName("title")
        private String title;

        @SerializedName("url")
        private String url;

        @SerializedName("url_title")
        private String url_title;

        @SerializedName("priority")
        private String priority;

        @SerializedName("retry")
        private String retry;

        @SerializedName("expire")
        private String expire;

        @SerializedName("sound")
        private String sound;

        @SerializedName("timestamp")
        private String timestamp;

        @SerializedName("attachment")
        private String attachment;

        @SerializedName("limit")
        private String  limit;

        @SerializedName("remaining")
        private String remaining;

        @SerializedName("reset")
        private String reset;

        @SerializedName("status")
        private String status;

        @SerializedName("errors")
        private String[] errors;

        @SerializedName("devices")
        private String[] devices;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getDevice() {
            return device;
        }

        public void setDevice(String device) {
            this.device = device;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUrl_title() {
            return url_title;
        }

        public void setUrl_title(String url_title) {
            this.url_title = url_title;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public String getRetry() {
            return retry;
        }

        public void setRetry(String retry) {
            this.retry = retry;
        }

        public String getExpire() {
            return expire;
        }

        public void setExpire(String expire) {
            this.expire = expire;
        }

        public String getSound() {
            return sound;
        }

        public void setSound(String sound) {
            this.sound = sound;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public String getAttachment() {
            return attachment;
        }

        public void setAttachment(String attachment) {
            this.attachment = attachment;
        }

        public String getLimit() {
            return limit;
        }

        public void setLimit(String limit) {
            this.limit = limit;
        }

        public String getRemaining() {
            return remaining;
        }

        public void setRemaining(String remaining) {
            this.remaining = remaining;
        }

        public String getReset() {
            return reset;
        }

        public void setReset(String reset) {
            this.reset = reset;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String[] getErrors() {
            return errors;
        }

        public void setErrors(String[] errors) {
            this.errors = errors;
        }

        public String[] getDevices() {
            return devices;
        }

        public void setDevices(String[] devices) {
            this.devices = devices;
        }
    }

    /*
    File file = // initialize file here
    MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", file.getName(), RequestBody.create(MediaType.parse("image/*"), file));
    Call<MyResponse> call = api.uploadAttachment(filePart);
     */

    @Headers({
            "Accept: application/json",
            "Content-type: application/json"
    })

    @POST("/1/messages.json")
    Call<Message> postMessage(@Body Message message);

    @Multipart
    @POST("/1/messages.json")
    Call<Message> postAttachment(@Part Message message, @Part MultipartBody.Part filePart);

    @POST("/1/users/validate.json")
    Call<Message> validate(@Body Message message);

    @GET("/1/apps/limits.json")
    Call<Message> getLimits(@Query("token") String token);
}

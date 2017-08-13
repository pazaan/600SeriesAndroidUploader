package info.nightscout.api;

import android.support.annotation.Nullable;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface TreatmentEndpoints {

    class TreatmentEntry {
        String eventType;
        String created_at;
        String enteredinsulin;
        String splitNow;
        String splitExt;
        String notes;
        String device;
        float insulin;
        float duration;
        float relative;

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getCreatedAt() {
            return created_at;
        }

        public void setCreatedAt(String created_at) {
            this.created_at = created_at;
        }

        public String getDevice() {
            return device;
        }

        public void setDevice(String device) {
            this.device = device;
        }

        public float getInsulin() {
            return insulin;
        }

        public void setInsulin(float insulin) {
            this.insulin = insulin;
        }

        public float getDuration() {
            return duration;
        }

        public void setDuration(float duration) {
            this.duration = duration;
        }

        public float getRelative() {
            return relative;
        }

        public void setRelative(float relative) {
            this.relative = relative;
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

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }

    }

    @Headers({
            "Accept: application/json",
            "Content-type: application/json"
    })
    @POST("/api/v1/treatments")
    Call<ResponseBody> sendEntries(@Body List <TreatmentEntry> entries);
}

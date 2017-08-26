package info.nightscout.api;

import java.math.BigDecimal;
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
        String units;
        String glucoseType;
        String notes;
        String device;
        float insulin;
        float duration;
        float relative;
        BigDecimal glucose;

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public void setCreatedAt(String created_at) {
            this.created_at = created_at;
        }

        public void setDevice(String device) {
            this.device = device;
        }

        public void setInsulin(float insulin) {
            this.insulin = insulin;
        }

        public void setGlucose(BigDecimal glucose) {
            this.glucose = glucose;
        }

        public void setDuration(float duration) {
            this.duration = duration;
        }

        public void setRelative(float relative) {
            this.relative = relative;
        }

        public void setEnteredinsulin(String enteredinsulin) {
            this.enteredinsulin = enteredinsulin;
        }

        public void setSplitNow(String splitNow) {
            this.splitNow = splitNow;
        }

        public void setSplitExt(String splitExt) {
            this.splitExt = splitExt;
        }

        public void setUnits(String units) {
            this.units = units;
        }

        public void setGlucoseType(String glucoseType) {
            this.glucoseType = glucoseType;
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

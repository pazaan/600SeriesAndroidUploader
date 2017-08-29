package info.nightscout.api;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface TempBasalRateEndpoints {

    class TempBasalRateEntry {
        String eventType = "Temp Basal";
        String created_at;
        String notes;
        float duration;
        float absolute;

        public TempBasalRateEntry(String created_at, String notes, float duration, float absolute) {
            this.created_at = created_at;
            this.notes = notes;
            this.duration = duration;
            this.absolute = absolute;
        }
    }

    @Headers({
            "Accept: application/json",
            "Content-type: application/json"
    })
    @POST("/api/v1/treatments")
    Call<ResponseBody> sendEntries(@Body List<TempBasalRateEntry> entries);
}

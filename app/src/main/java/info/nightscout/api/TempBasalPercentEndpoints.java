package info.nightscout.api;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface TempBasalPercentEndpoints {

    class TempBasalPercentEntry {
        String eventType = "Temp Basal";
        String created_at;
        String notes;
        float duration;
        float percent;

        public TempBasalPercentEntry(String created_at, String notes, float duration, float percent) {
            this.created_at = created_at;
            this.notes = notes;
            this.duration = duration;
            this.percent = percent;
        }
    }

    @Headers({
            "Accept: application/json",
            "Content-type: application/json"
    })
    @POST("/api/v1/treatments")
    Call<ResponseBody> sendEntries(@Body List<TempBasalPercentEntry> entries);
}

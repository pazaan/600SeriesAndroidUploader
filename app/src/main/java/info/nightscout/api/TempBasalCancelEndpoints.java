package info.nightscout.api;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface TempBasalCancelEndpoints {

    class TempBasalCancelEntry {
        String eventType = "Temp Basal";
        String created_at;
        String notes;
        float duration = 0;

        public TempBasalCancelEntry(String created_at, String notes) {
            this.created_at = created_at;
            this.notes = notes;
        }
    }

    @Headers({
            "Accept: application/json",
            "Content-type: application/json"
    })
    @POST("/api/v1/treatments")
    Call<ResponseBody> sendEntries(@Body List<TempBasalCancelEntry> entries);
}

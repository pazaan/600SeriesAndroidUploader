package info.nightscout.api;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface NoteEndpoints {

    class NoteEntry {
        String eventType;
        String created_at;
        String notes;

        public NoteEntry(String eventType, String created_at, String notes) {
            this.eventType = eventType;
            this.created_at = created_at;
            this.notes = notes;
        }
    }

    @Headers({
            "Accept: application/json",
            "Content-type: application/json"
    })
    @POST("/api/v1/treatments")
    Call<ResponseBody> sendEntries(@Body List<NoteEntry> noteEntries);
}

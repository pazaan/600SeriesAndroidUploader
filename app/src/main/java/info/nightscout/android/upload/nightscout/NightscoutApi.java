package info.nightscout.android.upload.nightscout;

import retrofit2.Call;
import retrofit2.http.GET;
/**
 * Created by lgoedhart on 26/06/2016.
 */
public interface NightscoutApi {
    class LoginStatus {
        public final String status;

        public LoginStatus(String status) {
            this.status = status;
        }
    }

    class SiteStatus {
        public final String status;
        public final String name;

        public SiteStatus(String status, String name) {
            this.status = status;
            this.name = name;
        }
    }

    @GET("/api/v1/status.json")
    Call<SiteStatus> getStatus();

    @GET("/api/v1/experiments/update")
    Call<LoginStatus> testLogin();
}

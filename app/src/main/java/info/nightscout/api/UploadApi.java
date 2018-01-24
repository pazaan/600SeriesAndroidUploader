package info.nightscout.api;


import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class UploadApi {
    private Retrofit retrofit;

    private StatusEndpoints statusEndpoints;
    private DeviceEndpoints deviceEndpoints;
    private ProfileEndpoints profileEndpoints;
    private EntriesEndpoints entriesEndpoints;
    private TreatmentsEndpoints treatmentsEndpoints;

    public StatusEndpoints getStatusEndpoints() {
        return statusEndpoints;
    }
    public DeviceEndpoints getDeviceEndpoints() {
        return deviceEndpoints;
    }
    public ProfileEndpoints getProfileEndpoints() {
        return profileEndpoints;
    }
    public EntriesEndpoints getEntriesEndpoints() {
        return entriesEndpoints;
    }
    public TreatmentsEndpoints getTreatmentsEndpoints() {
        return treatmentsEndpoints;
    }

    public UploadApi(String baseURL, String token) {

        class AddAuthHeader implements Interceptor {

            private String token;

            public AddAuthHeader(String token) {
                this.token = token;
            }

            @Override
            public Response intercept(Interceptor.Chain chain) throws IOException {
                Request original = chain.request();

                Request.Builder requestBuilder = original.newBuilder()
                        .header("api-secret", token)
                        .method( original.method(), original.body());

                Request request = requestBuilder.build();
                return chain.proceed(request);
            }
        }

        OkHttpClient.Builder okHttpClient = new OkHttpClient().newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS);

        if (token != null)
            okHttpClient.addInterceptor(new AddAuthHeader(token));

        // dev debug logging only
        if (false) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            okHttpClient.addInterceptor(logging);
        }

        retrofit = new Retrofit.Builder()
                .baseUrl(baseURL)
                .client(okHttpClient.build())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        statusEndpoints = retrofit.create(StatusEndpoints.class);
        deviceEndpoints = retrofit.create(DeviceEndpoints.class);
        profileEndpoints = retrofit.create(ProfileEndpoints.class);
        entriesEndpoints = retrofit.create(EntriesEndpoints.class);
        treatmentsEndpoints = retrofit.create(TreatmentsEndpoints.class);
    }
}

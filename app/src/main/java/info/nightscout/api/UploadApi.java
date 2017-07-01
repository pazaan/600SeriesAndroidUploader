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
    private GlucoseEndpoints glucoseEndpoints;
    private BolusEndpoints bolusEndpoints;
    private DeviceEndpoints deviceEndpoints;

    public GlucoseEndpoints getGlucoseEndpoints() {
        return glucoseEndpoints;
    }

    public BolusEndpoints getBolusEndpoints() {
        return bolusEndpoints;
    }

    public DeviceEndpoints getDeviceEndpoints() {
        return deviceEndpoints;
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

        okHttpClient.addInterceptor(new AddAuthHeader(token));

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        okHttpClient.addInterceptor(logging);

        retrofit = new Retrofit.Builder()
                .baseUrl(baseURL)
                .client(okHttpClient.build())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        glucoseEndpoints = retrofit.create(GlucoseEndpoints.class);
        bolusEndpoints = retrofit.create(BolusEndpoints.class);
        deviceEndpoints = retrofit.create(DeviceEndpoints.class);
    }
}

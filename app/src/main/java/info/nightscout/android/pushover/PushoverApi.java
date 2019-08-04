package info.nightscout.android.pushover;

import java.util.concurrent.TimeUnit;

import info.nightscout.android.BuildConfig;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by Pogman on 14.4.18.
 */

@SuppressWarnings("PointlessBooleanExpression")
public class PushoverApi {
    private Retrofit retrofit;

    private PushoverEndpoints pushoverEndpoints;

    public PushoverEndpoints getPushoverEndpoints() {
        return pushoverEndpoints;
    }

    public PushoverApi(String baseURL) {

        OkHttpClient.Builder okHttpClient = new OkHttpClient().newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS);

        // dev debug logging only
        if (false & BuildConfig.DEBUG) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            okHttpClient.addInterceptor(logging);
        }

        retrofit = new Retrofit.Builder()
                .baseUrl(baseURL)
                .client(okHttpClient.build())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        pushoverEndpoints = retrofit.create(PushoverEndpoints.class);
    }
}

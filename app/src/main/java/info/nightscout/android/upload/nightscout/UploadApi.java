package info.nightscout.android.upload.nightscout;


import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import info.nightscout.android.BuildConfig;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@SuppressWarnings("PointlessBooleanExpression")
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

    public UploadApi(String baseURL, String secret)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {

        class AddAuthHeader implements Interceptor {

            private String token;

            public AddAuthHeader(String token) {
                this.token = token;
            }

            @Override
            public Response intercept(@NonNull Interceptor.Chain chain) throws IOException {
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

        if (secret != null)
            okHttpClient.addInterceptor(new AddAuthHeader(formToken(secret)));

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

        statusEndpoints = retrofit.create(StatusEndpoints.class);
        deviceEndpoints = retrofit.create(DeviceEndpoints.class);
        profileEndpoints = retrofit.create(ProfileEndpoints.class);
        entriesEndpoints = retrofit.create(EntriesEndpoints.class);
        treatmentsEndpoints = retrofit.create(TreatmentsEndpoints.class);
    }

    @NonNull
    private String formToken(String secret) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] bytes = secret.getBytes("UTF-8");
        digest.update(bytes, 0, bytes.length);
        bytes = digest.digest();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

}

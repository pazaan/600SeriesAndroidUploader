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
    private SgvEndpoints sgvEndpoints;
    private MbgEndpoints mbgEndpoints;
    private DeviceEndpoints deviceEndpoints;
    private TreatmentEndpoints treatmentEndpoints;
    private TempBasalRateEndpoints tempBasalRateEndpoints;
    private TempBasalPercentEndpoints tempBasalPercentEndpoints;
    private TempBasalCancelEndpoints tempBasalCancelEndpoints;
    private NoteEndpoints noteEndpoints;

    public SgvEndpoints getSgvEndpoints() {
        return sgvEndpoints;
    }
    public MbgEndpoints getMbgEndpoints() {
        return mbgEndpoints;
    }
    public DeviceEndpoints getDeviceEndpoints() {
        return deviceEndpoints;
    }
    public TreatmentEndpoints getTreatmentEndpoints() {
        return treatmentEndpoints;
    }
    public TempBasalRateEndpoints getTempBasalRateEndpoints() {
        return tempBasalRateEndpoints;
    }
    public TempBasalPercentEndpoints getTempBasalPercentEndpoints() {
        return tempBasalPercentEndpoints;
    }
    public TempBasalCancelEndpoints getTempBasalCancelEndpoints() {
        return tempBasalCancelEndpoints;
    }
    public NoteEndpoints getNoteEndpoints() {
        return noteEndpoints;
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

        sgvEndpoints = retrofit.create(SgvEndpoints.class);
        mbgEndpoints = retrofit.create(MbgEndpoints.class);
        deviceEndpoints = retrofit.create(DeviceEndpoints.class);
        treatmentEndpoints = retrofit.create(TreatmentEndpoints.class);
        tempBasalRateEndpoints = retrofit.create(TempBasalRateEndpoints.class);
        tempBasalPercentEndpoints = retrofit.create(TempBasalPercentEndpoints.class);
        tempBasalCancelEndpoints = retrofit.create(TempBasalCancelEndpoints.class);
        noteEndpoints = retrofit.create(NoteEndpoints.class);
    }
}

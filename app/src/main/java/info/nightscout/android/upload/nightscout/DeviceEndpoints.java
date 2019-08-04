package info.nightscout.android.upload.nightscout;

import java.math.BigDecimal;
import java.util.Date;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface DeviceEndpoints {

    class Iob {
        final Date timestamp;
        final Float bolusiob;

        public Iob(Date timestamp,
                   Float bolusiob) {
            this.timestamp = timestamp;
            this.bolusiob = bolusiob;
        }
    }

    class Battery {
        final Short percent;

        public Battery(short percent) {
            this.percent = percent;
        }
    }

    class PumpStatus {
        final Boolean bolusing;
        final Boolean suspended;
        final String status;

        public PumpStatus(
                Boolean bolusing,
                Boolean suspended,
                String status) {
            this.bolusing = bolusing;
            this.suspended = suspended;
            this.status = status;

        }
    }

    class PumpInfo {
        final String clock;
        final BigDecimal reservoir;
        final Iob iob;
        final Battery battery;
        final PumpStatus status;

        public PumpInfo(String clock,
                        BigDecimal reservoir,
                        Iob iob,
                        Battery battery,
                        PumpStatus status) {
            this.clock = clock;
            this.reservoir = reservoir;
            this.iob = iob;
            this.battery = battery;
            this.status = status;
        }
    }

    class DeviceStatus {
        Integer uploaderBattery;
        String device;
        String created_at;
        PumpInfo pump;

        public void setUploaderBattery(Integer uploaderBattery) {
            this.uploaderBattery = uploaderBattery;
        }

        public void setDevice(String device) {
            this.device = device;
        }

        public void setCreatedAt(String created_at) {
            this.created_at = created_at;
        }

        public void setPump(PumpInfo pump) {
            this.pump = pump;
        }

    }

    @Headers({
            "Accept: application/json",
            "Content-type: application/json"
    })
    @POST("/api/v1/devicestatus")
    Call<ResponseBody> sendDeviceStatus(@Body DeviceStatus deviceStatus);

}

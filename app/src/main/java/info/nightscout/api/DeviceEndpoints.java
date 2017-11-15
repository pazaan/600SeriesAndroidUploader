package info.nightscout.api;

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
        public Iob (Date timestamp,
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
                String status

        ) {
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
        final Integer uploaderBattery;
        final String device;
        final String created_at;
        final PumpInfo pump;

        public DeviceStatus(Integer uploaderBattery,
                     String device,
                     String created_at,
                     PumpInfo pump) {
            this.uploaderBattery = uploaderBattery;
            this.device = device;
            this.created_at = created_at;
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




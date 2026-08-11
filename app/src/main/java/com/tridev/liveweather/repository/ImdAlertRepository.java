package com.tridev.liveweather.repository;

import androidx.annotation.NonNull;

import com.tridev.liveweather.data.remote.api.ImdAlertApiClient;
import com.tridev.liveweather.data.remote.api.ImdAlertApiService;
import com.tridev.liveweather.data.remote.dto.ImdDistrictWarning;
import com.tridev.liveweather.data.remote.dto.ImdNowcastWarning;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import retrofit2.Response;

public final class ImdAlertRepository {

    private final ImdAlertApiService api = ImdAlertApiClient.getApiService();

    @NonNull
    public Result loadBlocking() throws IOException {
        Response<List<ImdDistrictWarning>> districtResponse = api.getDistrictWarnings().execute();
        Response<List<ImdNowcastWarning>> nowcastResponse = api.getDistrictNowcast().execute();

        if (!districtResponse.isSuccessful() && !nowcastResponse.isSuccessful()) {
            throw new IOException(
                    "IMD alert requests failed with HTTP "
                            + districtResponse.code() + "/" + nowcastResponse.code()
            );
        }

        List<ImdDistrictWarning> districtWarnings = districtResponse.isSuccessful()
                && districtResponse.body() != null
                ? districtResponse.body()
                : Collections.emptyList();
        List<ImdNowcastWarning> nowcasts = nowcastResponse.isSuccessful()
                && nowcastResponse.body() != null
                ? nowcastResponse.body()
                : Collections.emptyList();
        return new Result(districtWarnings, nowcasts);
    }

    public static final class Result {
        private final List<ImdDistrictWarning> districtWarnings;
        private final List<ImdNowcastWarning> nowcasts;

        Result(
                @NonNull List<ImdDistrictWarning> districtWarnings,
                @NonNull List<ImdNowcastWarning> nowcasts
        ) {
            this.districtWarnings = districtWarnings;
            this.nowcasts = nowcasts;
        }

        @NonNull public List<ImdDistrictWarning> getDistrictWarnings() { return districtWarnings; }
        @NonNull public List<ImdNowcastWarning> getNowcasts() { return nowcasts; }
    }
}

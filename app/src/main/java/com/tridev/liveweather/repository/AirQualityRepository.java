package com.tridev.liveweather.repository;

import androidx.annotation.NonNull;

import com.tridev.liveweather.data.remote.api.AirQualityApiClient;
import com.tridev.liveweather.data.remote.api.AirQualityApiService;
import com.tridev.liveweather.data.remote.dto.AirQualityResponse;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class AirQualityRepository {

    private static final String CURRENT_VARIABLES =
            "european_aqi,us_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide," +
                    "sulphur_dioxide,ozone,aerosol_optical_depth,dust,uv_index,uv_index_clear_sky";

    private static final String HOURLY_VARIABLES = CURRENT_VARIABLES;
    private static final String TIMEZONE_AUTO = "auto";
    private static final int FORECAST_DAYS = 3;

    private final AirQualityApiService apiService;

    public AirQualityRepository() {
        apiService = AirQualityApiClient.getApiService();
    }

    public Call<AirQualityResponse> loadAirQuality(
            double latitude,
            double longitude,
            @NonNull CallbackResult callback
    ) {
        Call<AirQualityResponse> call = apiService.getAirQuality(
                latitude,
                longitude,
                CURRENT_VARIABLES,
                HOURLY_VARIABLES,
                TIMEZONE_AUTO,
                FORECAST_DAYS
        );
        call.enqueue(new Callback<AirQualityResponse>() {
            @Override
            public void onResponse(
                    @NonNull Call<AirQualityResponse> call,
                    @NonNull Response<AirQualityResponse> response
            ) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Air-quality request failed with HTTP " + response.code(), null);
                }
            }

            @Override
            public void onFailure(
                    @NonNull Call<AirQualityResponse> call,
                    @NonNull Throwable throwable
            ) {
                if (!call.isCanceled()) {
                    callback.onError("Unable to load air-quality data.", throwable);
                }
            }
        });
        return call;
    }

    @NonNull
    public AirQualityResponse loadAirQualityBlocking(double latitude, double longitude) throws IOException {
        Response<AirQualityResponse> response = apiService.getAirQuality(
                latitude,
                longitude,
                CURRENT_VARIABLES,
                HOURLY_VARIABLES,
                TIMEZONE_AUTO,
                FORECAST_DAYS
        ).execute();
        if (!response.isSuccessful() || response.body() == null) {
            throw new IOException("Air-quality request failed with HTTP " + response.code());
        }
        return response.body();
    }

    public interface CallbackResult {
        void onSuccess(@NonNull AirQualityResponse response);
        void onError(@NonNull String message, Throwable throwable);
    }
}

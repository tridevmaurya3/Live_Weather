package com.tridev.liveweather.repository;

import androidx.annotation.NonNull;

import com.tridev.liveweather.data.remote.api.OpenMeteoApiService;
import com.tridev.liveweather.data.remote.api.NetworkFailureMessage;
import com.tridev.liveweather.data.remote.api.WeatherApiClient;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WeatherRepository {

    private static final String CURRENT_VARIABLES =
            "temperature_2m,relative_humidity_2m,apparent_temperature,dew_point_2m,is_day," +
                    "precipitation,rain,showers,snowfall,weather_code,cloud_cover," +
                    "cloud_cover_low,cloud_cover_mid,cloud_cover_high,visibility," +
                    "pressure_msl,surface_pressure,wind_speed_10m,wind_direction_10m," +
                    "wind_gusts_10m";

    private static final String MINUTELY_15_VARIABLES =
            "precipitation,rain,showers,snowfall,weather_code,cloud_cover,visibility";

    private static final String HOURLY_VARIABLES =
            "temperature_2m,relative_humidity_2m,apparent_temperature,dew_point_2m,is_day," +
                    "precipitation_probability,precipitation,rain,showers,snowfall," +
                    "weather_code,cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high," +
                    "visibility,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m";

    private static final String DAILY_VARIABLES =
            "weather_code,temperature_2m_max,temperature_2m_min," +
                    "apparent_temperature_max,apparent_temperature_min,sunrise,sunset," +
                    "daylight_duration,sunshine_duration,uv_index_max,precipitation_sum," +
                    "rain_sum,showers_sum,snowfall_sum,precipitation_hours," +
                    "precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max," +
                    "wind_direction_10m_dominant";

    private static final String TIMEZONE_AUTO = "auto";
    private static final int FORECAST_DAYS = 10;
    private static final int FORECAST_MINUTELY_15 = 8;
    private static final int PAST_MINUTELY_15 = 4;

    private final OpenMeteoApiService apiService;

    public WeatherRepository() {
        this(WeatherApiClient.getApiService());
    }

    WeatherRepository(OpenMeteoApiService apiService) {
        this.apiService = apiService;
    }

    public Call<WeatherResponse> loadWeather(
            double latitude,
            double longitude,
            @NonNull WeatherCallback callback
    ) {
        Call<WeatherResponse> call = createCall(latitude, longitude);

        call.enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(
                    @NonNull Call<WeatherResponse> call,
                    @NonNull Response<WeatherResponse> response
            ) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                    return;
                }

                callback.onError(buildHttpError(response), null);
            }

            @Override
            public void onFailure(
                    @NonNull Call<WeatherResponse> call,
                    @NonNull Throwable throwable
            ) {
                if (call.isCanceled()) {
                    return;
                }

                callback.onError(NetworkFailureMessage.forService("Weather data", throwable), throwable);
            }
        });

        return call;
    }

    /**
     * Synchronous network path intended for WorkManager background threads.
     * Never call this method from the Android main thread.
     */
    @NonNull
    public WeatherResponse loadWeatherBlocking(
            double latitude,
            double longitude
    ) throws IOException {
        Response<WeatherResponse> response = createCall(latitude, longitude).execute();
        if (response.isSuccessful() && response.body() != null) {
            return response.body();
        }
        throw new IOException(buildHttpError(response));
    }

    private Call<WeatherResponse> createCall(double latitude, double longitude) {
        return apiService.getWeather(
                latitude,
                longitude,
                CURRENT_VARIABLES,
                MINUTELY_15_VARIABLES,
                HOURLY_VARIABLES,
                DAILY_VARIABLES,
                TIMEZONE_AUTO,
                FORECAST_DAYS,
                FORECAST_MINUTELY_15,
                PAST_MINUTELY_15
        );
    }

    @NonNull
    private String buildHttpError(@NonNull Response<WeatherResponse> response) {
        String message = "Weather request failed with HTTP " + response.code();
        if (response.errorBody() != null) {
            try {
                String errorText = response.errorBody().string();
                if (!errorText.trim().isEmpty()) {
                    message = message + ": " + errorText;
                }
            } catch (IOException ignored) {
                // Keep HTTP status when the optional error body cannot be read.
            }
        }
        return message;
    }

    public interface WeatherCallback {
        void onSuccess(@NonNull WeatherResponse weatherResponse);

        void onError(@NonNull String message, Throwable throwable);
    }
}

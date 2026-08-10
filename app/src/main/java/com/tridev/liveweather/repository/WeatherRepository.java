package com.tridev.liveweather.repository;

import androidx.annotation.NonNull;

import com.tridev.liveweather.data.remote.api.OpenMeteoApiService;
import com.tridev.liveweather.data.remote.api.WeatherApiClient;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WeatherRepository {

    private static final String CURRENT_VARIABLES =
            "temperature_2m,relative_humidity_2m,apparent_temperature,dew_point_2m,is_day," +
                    "precipitation,rain,showers,snowfall,weather_code,cloud_cover,visibility," +
                    "pressure_msl,surface_pressure,wind_speed_10m,wind_direction_10m," +
                    "wind_gusts_10m";

    private static final String MINUTELY_15_VARIABLES =
            "precipitation,rain,showers,snowfall,weather_code,cloud_cover,visibility";

    private static final String HOURLY_VARIABLES =
            "temperature_2m,relative_humidity_2m,apparent_temperature,dew_point_2m,is_day," +
                    "precipitation_probability,precipitation,rain,showers,snowfall," +
                    "weather_code,cloud_cover,visibility,pressure_msl,wind_speed_10m," +
                    "wind_direction_10m,wind_gusts_10m";

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
        Call<WeatherResponse> call = apiService.getWeather(
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

                String message = "Weather request failed with HTTP " + response.code();
                if (response.errorBody() != null) {
                    try {
                        String errorText = response.errorBody().string();
                        if (!errorText.trim().isEmpty()) {
                            message = message + ": " + errorText;
                        }
                    } catch (IOException ignored) {
                        // Keep the HTTP status message if the error body cannot be read.
                    }
                }

                callback.onError(message, null);
            }

            @Override
            public void onFailure(
                    @NonNull Call<WeatherResponse> call,
                    @NonNull Throwable throwable
            ) {
                if (call.isCanceled()) {
                    return;
                }

                callback.onError("Unable to load weather data.", throwable);
            }
        });

        return call;
    }

    public interface WeatherCallback {
        void onSuccess(@NonNull WeatherResponse weatherResponse);

        void onError(@NonNull String message, Throwable throwable);
    }
}

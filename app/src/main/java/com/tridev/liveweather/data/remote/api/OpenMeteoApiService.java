package com.tridev.liveweather.data.remote.api;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OpenMeteoApiService {

    @GET("v1/forecast")
    Call<WeatherResponse> getWeather(
            @Query("latitude") double latitude,
            @Query("longitude") double longitude,
            @Query("current") String currentVariables,
            @Query("minutely_15") String minutely15Variables,
            @Query("hourly") String hourlyVariables,
            @Query("daily") String dailyVariables,
            @Query("timezone") String timezone,
            @Query("forecast_days") int forecastDays,
            @Query("forecast_minutely_15") int forecastMinutely15,
            @Query("past_minutely_15") int pastMinutely15
    );
}

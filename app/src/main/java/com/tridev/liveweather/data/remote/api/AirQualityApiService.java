package com.tridev.liveweather.data.remote.api;

import com.tridev.liveweather.data.remote.dto.AirQualityResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface AirQualityApiService {

    @GET("v1/air-quality")
    Call<AirQualityResponse> getAirQuality(
            @Query("latitude") double latitude,
            @Query("longitude") double longitude,
            @Query("current") String currentVariables,
            @Query("hourly") String hourlyVariables,
            @Query("timezone") String timezone,
            @Query("forecast_days") int forecastDays
    );
}

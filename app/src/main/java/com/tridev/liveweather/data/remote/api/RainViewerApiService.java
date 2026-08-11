package com.tridev.liveweather.data.remote.api;

import com.tridev.liveweather.data.remote.dto.RainViewerResponse;

import retrofit2.Call;
import retrofit2.http.GET;

public interface RainViewerApiService {

    @GET("public/weather-maps.json")
    Call<RainViewerResponse> getWeatherMaps();
}

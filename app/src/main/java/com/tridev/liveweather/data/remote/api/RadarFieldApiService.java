package com.tridev.liveweather.data.remote.api;

import com.tridev.liveweather.data.remote.dto.RadarFieldPointResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface RadarFieldApiService {

    @GET("v1/forecast")
    Call<List<RadarFieldPointResponse>> getCurrentField(
            @Query("latitude") String latitudes,
            @Query("longitude") String longitudes,
            @Query("current") String currentVariables,
            @Query("timezone") String timezone
    );
}

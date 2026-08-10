package com.tridev.liveweather.data.remote.api;

import com.tridev.liveweather.data.remote.dto.GeocodingResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OpenMeteoGeocodingApiService {

    @GET("v1/search")
    Call<GeocodingResponse> searchLocations(
            @Query("name") String name,
            @Query("count") int count,
            @Query("language") String language,
            @Query("format") String format
    );
}

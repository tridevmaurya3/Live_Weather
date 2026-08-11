package com.tridev.liveweather.data.remote.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RadarFieldApiClient {

    private static final String BASE_URL = "https://api.open-meteo.com/";
    private static volatile RadarFieldApiService apiService;

    private RadarFieldApiClient() {
    }

    public static RadarFieldApiService getApiService() {
        if (apiService == null) {
            synchronized (RadarFieldApiClient.class) {
                if (apiService == null) {
                    Gson gson = new GsonBuilder().create();
                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .addConverterFactory(GsonConverterFactory.create(gson))
                            .build();
                    apiService = retrofit.create(RadarFieldApiService.class);
                }
            }
        }
        return apiService;
    }
}

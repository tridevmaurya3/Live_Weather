package com.tridev.liveweather.data.remote.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class AirQualityApiClient {

    private static final String BASE_URL = "https://air-quality-api.open-meteo.com/";
    private static volatile AirQualityApiService apiService;

    private AirQualityApiClient() {
    }

    public static AirQualityApiService getApiService() {
        if (apiService == null) {
            synchronized (AirQualityApiClient.class) {
                if (apiService == null) {
                    Gson gson = new GsonBuilder().create();
                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(NetworkClientFactory.get())
                            .addConverterFactory(GsonConverterFactory.create(gson))
                            .build();
                    apiService = retrofit.create(AirQualityApiService.class);
                }
            }
        }
        return apiService;
    }
}

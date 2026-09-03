package com.tridev.liveweather.data.remote.api;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class GeocodingApiClient {

    private static final String BASE_URL = "https://geocoding-api.open-meteo.com/";

    private static volatile OpenMeteoGeocodingApiService apiService;

    private GeocodingApiClient() {
    }

    public static OpenMeteoGeocodingApiService getApiService() {
        if (apiService == null) {
            synchronized (GeocodingApiClient.class) {
                if (apiService == null) {
                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(NetworkClientFactory.get())
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                    apiService = retrofit.create(OpenMeteoGeocodingApiService.class);
                }
            }
        }
        return apiService;
    }
}

package com.tridev.liveweather.data.remote.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class WeatherApiClient {

    private static final String BASE_URL = "https://api.open-meteo.com/";

    private static volatile OpenMeteoApiService apiService;

    private WeatherApiClient() {
    }

    public static OpenMeteoApiService getApiService() {
        if (apiService == null) {
            synchronized (WeatherApiClient.class) {
                if (apiService == null) {
                    Gson gson = new GsonBuilder().create();

                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(NetworkClientFactory.get())
                            .addConverterFactory(GsonConverterFactory.create(gson))
                            .build();

                    apiService = retrofit.create(OpenMeteoApiService.class);
                }
            }
        }

        return apiService;
    }
}

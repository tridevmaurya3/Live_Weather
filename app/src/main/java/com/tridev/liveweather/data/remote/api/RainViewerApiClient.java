package com.tridev.liveweather.data.remote.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RainViewerApiClient {

    private static final String BASE_URL = "https://api.rainviewer.com/";
    private static volatile RainViewerApiService apiService;

    private RainViewerApiClient() {
    }

    public static RainViewerApiService getApiService() {
        if (apiService == null) {
            synchronized (RainViewerApiClient.class) {
                if (apiService == null) {
                    Gson gson = new GsonBuilder().create();
                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .addConverterFactory(GsonConverterFactory.create(gson))
                            .build();
                    apiService = retrofit.create(RainViewerApiService.class);
                }
            }
        }
        return apiService;
    }
}

package com.tridev.liveweather.data.remote.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ImdAlertApiClient {

    private static final String BASE_URL = "https://api.imd.gov.in/";
    private static volatile ImdAlertApiService apiService;

    private ImdAlertApiClient() {
    }

    public static ImdAlertApiService getApiService() {
        if (apiService == null) {
            synchronized (ImdAlertApiClient.class) {
                if (apiService == null) {
                    Gson gson = new GsonBuilder().create();
                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .addConverterFactory(GsonConverterFactory.create(gson))
                            .build();
                    apiService = retrofit.create(ImdAlertApiService.class);
                }
            }
        }
        return apiService;
    }
}

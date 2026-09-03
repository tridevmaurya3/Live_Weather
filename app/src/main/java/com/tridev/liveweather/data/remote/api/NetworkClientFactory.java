package com.tridev.liveweather.data.remote.api;

import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/** Shared bounded network policy for every external weather provider. */
public final class NetworkClientFactory {

    public static final long CONNECT_TIMEOUT_SECONDS = 10L;
    public static final long READ_TIMEOUT_SECONDS = 20L;
    public static final long WRITE_TIMEOUT_SECONDS = 15L;
    public static final long CALL_TIMEOUT_SECONDS = 25L;

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private NetworkClientFactory() { }

    @NonNull
    public static OkHttpClient get() {
        return CLIENT;
    }
}

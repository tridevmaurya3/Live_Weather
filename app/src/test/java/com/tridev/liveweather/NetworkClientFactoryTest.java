package com.tridev.liveweather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.tridev.liveweather.data.remote.api.NetworkClientFactory;

import org.junit.Test;

import okhttp3.OkHttpClient;

public final class NetworkClientFactoryTest {

    @Test
    public void allProvidersShareOneClient() {
        assertSame(NetworkClientFactory.get(), NetworkClientFactory.get());
    }

    @Test
    public void networkCallsHaveBoundedTimeouts() {
        OkHttpClient client = NetworkClientFactory.get();
        assertEquals(10_000, client.connectTimeoutMillis());
        assertEquals(20_000, client.readTimeoutMillis());
        assertEquals(15_000, client.writeTimeoutMillis());
        assertEquals(25_000, client.callTimeoutMillis());
    }

    @Test
    public void safeConnectionRetryRemainsEnabled() {
        assertTrue(NetworkClientFactory.get().retryOnConnectionFailure());
    }
}

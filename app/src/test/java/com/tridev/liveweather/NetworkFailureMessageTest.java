package com.tridev.liveweather;

import static org.junit.Assert.assertTrue;

import com.tridev.liveweather.data.remote.api.NetworkFailureMessage;

import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public final class NetworkFailureMessageTest {

    @Test public void timeoutIsIdentified() {
        assertTrue(NetworkFailureMessage.forService(
                "Weather data", new SocketTimeoutException()
        ).contains("timed out"));
    }

    @Test public void offlineDnsFailureIsIdentifiedThroughWrapper() {
        assertTrue(NetworkFailureMessage.forService(
                "Weather data", new IOException(new UnknownHostException())
        ).contains("offline"));
    }

    @Test public void refusedConnectionGetsRetryGuidance() {
        assertTrue(NetworkFailureMessage.forService(
                "Weather data", new ConnectException()
        ).contains("Try again"));
    }

    @Test public void unknownFailureStaysServiceSpecific() {
        assertTrue(NetworkFailureMessage.forService(
                "Weather data", new IOException()
        ).contains("weather data"));
    }
}

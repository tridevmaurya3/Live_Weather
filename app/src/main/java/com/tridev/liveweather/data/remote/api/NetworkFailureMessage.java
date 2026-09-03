package com.tridev.liveweather.data.remote.api;

import androidx.annotation.NonNull;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

/** Converts transport failures into short, truthful user-facing messages. */
public final class NetworkFailureMessage {

    private NetworkFailureMessage() { }

    @NonNull
    public static String forService(@NonNull String service, Throwable throwable) {
        Throwable cause = rootCause(throwable);
        if (cause instanceof SocketTimeoutException) {
            return service + " timed out. Check the connection and try again.";
        }
        if (cause instanceof UnknownHostException) {
            return service + " is offline. Check the internet connection.";
        }
        if (cause instanceof ConnectException) {
            return service + " could not connect. Try again shortly.";
        }
        return "Unable to load " + service.toLowerCase(Locale.US) + ".";
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}

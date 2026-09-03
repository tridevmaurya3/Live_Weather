package com.tridev.liveweather.core;

import androidx.annotation.NonNull;

/** Pure generation/identity gate that prevents out-of-order refresh responses. */
public final class ActiveWeatherRequestPolicy {

    private ActiveWeatherRequestPolicy() {
    }

    public static boolean isCurrent(
            long requestGeneration,
            @NonNull String requestIdentity,
            long activeGeneration,
            @NonNull String activeIdentity
    ) {
        return requestGeneration > 0L
                && requestGeneration == activeGeneration
                && requestIdentity.equals(activeIdentity);
    }
}

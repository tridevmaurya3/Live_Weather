package com.tridev.liveweather.core.location;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Best-effort reverse geocoder for a friendly current-place label.
 * Weather never depends on this result; coordinates remain the fallback.
 */
public final class PlaceNameResolver {

    private final Geocoder geocoder;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public PlaceNameResolver(@NonNull Context context) {
        geocoder = new Geocoder(context.getApplicationContext(), Locale.getDefault());
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void resolve(
            double latitude,
            double longitude,
            @NonNull Callback callback
    ) {
        if (!Geocoder.isPresent()) {
            callback.onResolved(null);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                geocoder.getFromLocation(
                        latitude,
                        longitude,
                        3,
                        new Geocoder.GeocodeListener() {
                            @Override
                            public void onGeocode(@NonNull List<Address> addresses) {
                                callback.onResolved(buildLabel(addresses));
                            }

                            @Override
                            public void onError(@Nullable String errorMessage) {
                                callback.onResolved(null);
                            }
                        }
                );
            } catch (IllegalArgumentException exception) {
                callback.onResolved(null);
            }
            return;
        }

        executor.execute(() -> {
            String label = null;
            try {
                @SuppressWarnings("deprecation")
                List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 3);
                label = buildLabel(addresses);
            } catch (IOException | IllegalArgumentException ignored) {
                // Coordinates remain the safe fallback.
            }

            String resolvedLabel = label;
            mainHandler.post(() -> callback.onResolved(resolvedLabel));
        });
    }

    @Nullable
    private String buildLabel(@Nullable List<Address> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }

        Address address = addresses.get(0);
        if (address == null) {
            return null;
        }

        String primary = firstNonEmpty(
                address.getLocality(),
                address.getSubAdminArea(),
                address.getAdminArea(),
                address.getFeatureName()
        );
        String admin = address.getAdminArea();
        String country = address.getCountryName();

        if (primary == null) {
            return country;
        }

        StringBuilder builder = new StringBuilder(primary);
        if (admin != null
                && !admin.trim().isEmpty()
                && !admin.equalsIgnoreCase(primary)) {
            builder.append(", ").append(admin.trim());
        }
        if (country != null
                && !country.trim().isEmpty()
                && !country.equalsIgnoreCase(primary)
                && (admin == null || !country.equalsIgnoreCase(admin))) {
            builder.append(", ").append(country.trim());
        }
        return builder.toString();
    }

    @Nullable
    private String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    public interface Callback {
        void onResolved(@Nullable String label);
    }
}

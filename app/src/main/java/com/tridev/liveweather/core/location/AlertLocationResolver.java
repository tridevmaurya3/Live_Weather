package com.tridev.liveweather.core.location;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.domain.alert.AlertLocation;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AlertLocationResolver {

    private final Geocoder geocoder;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AlertLocationResolver(@NonNull Context context) {
        geocoder = new Geocoder(context.getApplicationContext(), Locale.getDefault());
    }

    public void resolve(double latitude, double longitude, @NonNull Callback callback) {
        if (!Geocoder.isPresent()) {
            callback.onResolved(new AlertLocation(latitude, longitude, null, null, null));
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(latitude, longitude, 1, new Geocoder.GeocodeListener() {
                @Override
                public void onGeocode(@NonNull List<Address> addresses) {
                    callback.onResolved(fromAddresses(latitude, longitude, addresses));
                }

                @Override
                public void onError(@Nullable String errorMessage) {
                    callback.onResolved(new AlertLocation(latitude, longitude, null, null, null));
                }
            });
            return;
        }

        executor.execute(() -> {
            AlertLocation result;
            try {
                @SuppressWarnings("deprecation")
                List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                result = fromAddresses(latitude, longitude, addresses);
            } catch (IOException | RuntimeException exception) {
                result = new AlertLocation(latitude, longitude, null, null, null);
            }
            AlertLocation finalResult = result;
            mainHandler.post(() -> callback.onResolved(finalResult));
        });
    }

    @NonNull
    private AlertLocation fromAddresses(
            double latitude,
            double longitude,
            @Nullable List<Address> addresses
    ) {
        if (addresses == null || addresses.isEmpty()) {
            return new AlertLocation(latitude, longitude, null, null, null);
        }
        Address address = addresses.get(0);
        String district = firstNonBlank(
                address.getSubAdminArea(),
                address.getLocality(),
                address.getSubLocality()
        );
        return new AlertLocation(
                latitude,
                longitude,
                district,
                address.getAdminArea(),
                address.getCountryCode()
        );
    }

    @Nullable
    private String firstNonBlank(@Nullable String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    public interface Callback {
        void onResolved(@NonNull AlertLocation location);
    }
}

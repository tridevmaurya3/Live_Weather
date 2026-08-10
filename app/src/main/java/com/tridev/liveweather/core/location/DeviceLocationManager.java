package com.tridev.liveweather.core.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

/**
 * Provides one foreground location fix for weather lookup.
 * Fine-location permission prefers a fresh high-accuracy fix; approximate
 * permission remains supported with balanced power accuracy.
 */
public final class DeviceLocationManager {

    private static final long FINE_MAX_UPDATE_AGE_MILLIS = 60_000L;
    private static final long COARSE_MAX_UPDATE_AGE_MILLIS = 2 * 60 * 1000L;
    private static final long REQUEST_DURATION_MILLIS = 15_000L;

    private final Context appContext;
    private final FusedLocationProviderClient fusedLocationClient;

    public DeviceLocationManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext);
    }

    public boolean hasLocationPermission() {
        return hasFineLocationPermission() || hasCoarseLocationPermission();
    }

    public boolean hasFineLocationPermission() {
        return ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasCoarseLocationPermission() {
        return ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    public void requestCurrentLocation(@NonNull LocationCallback callback) {
        if (!hasLocationPermission()) {
            callback.onError(
                    LocationError.PERMISSION_REQUIRED,
                    "Location permission is required for local weather.",
                    null
            );
            return;
        }

        int playServicesStatus = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(appContext);
        if (playServicesStatus != ConnectionResult.SUCCESS) {
            callback.onError(
                    LocationError.PLAY_SERVICES_UNAVAILABLE,
                    "Google Play services location is unavailable on this device.",
                    null
            );
            return;
        }

        boolean fine = hasFineLocationPermission();
        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(fine
                        ? Priority.PRIORITY_HIGH_ACCURACY
                        : Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(fine
                        ? FINE_MAX_UPDATE_AGE_MILLIS
                        : COARSE_MAX_UPDATE_AGE_MILLIS)
                .setDurationMillis(REQUEST_DURATION_MILLIS)
                .build();

        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();

        try {
            fusedLocationClient
                    .getCurrentLocation(request, cancellationTokenSource.getToken())
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            callback.onLocation(location);
                        } else {
                            callback.onError(
                                    LocationError.LOCATION_UNAVAILABLE,
                                    "Current location is unavailable. Check device location services.",
                                    null
                            );
                        }
                    })
                    .addOnFailureListener(throwable -> callback.onError(
                            LocationError.REQUEST_FAILED,
                            "Unable to read the current location.",
                            throwable
                    ));
        } catch (SecurityException securityException) {
            callback.onError(
                    LocationError.PERMISSION_REQUIRED,
                    "Location permission is required for local weather.",
                    securityException
            );
        }
    }

    public enum LocationError {
        PERMISSION_REQUIRED,
        PLAY_SERVICES_UNAVAILABLE,
        LOCATION_UNAVAILABLE,
        REQUEST_FAILED
    }

    public interface LocationCallback {
        void onLocation(@NonNull Location location);

        void onError(
                @NonNull LocationError error,
                @NonNull String message,
                Throwable throwable
        );
    }
}

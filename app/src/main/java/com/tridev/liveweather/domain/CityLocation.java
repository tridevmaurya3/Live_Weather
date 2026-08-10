package com.tridev.liveweather.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * Stable city/place model used by search, saved locations and active weather switching.
 */
public class CityLocation {

    private String id;
    private String name;
    private String adminArea;
    private String country;
    private String timezone;
    private double latitude;
    private double longitude;

    public CityLocation() {
        // Required for Gson.
    }

    public CityLocation(
            @NonNull String id,
            @NonNull String name,
            @Nullable String adminArea,
            @Nullable String country,
            @Nullable String timezone,
            double latitude,
            double longitude
    ) {
        this.id = id;
        this.name = name;
        this.adminArea = adminArea;
        this.country = country;
        this.timezone = timezone;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @NonNull
    public String getId() {
        return id == null ? stableCoordinateKey(latitude, longitude) : id;
    }

    @NonNull
    public String getName() {
        return name == null || name.trim().isEmpty() ? "Saved location" : name;
    }

    @Nullable
    public String getAdminArea() {
        return adminArea;
    }

    @Nullable
    public String getCountry() {
        return country;
    }

    @Nullable
    public String getTimezone() {
        return timezone;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    @NonNull
    public String getDisplayName() {
        StringBuilder builder = new StringBuilder(getName());

        if (adminArea != null
                && !adminArea.trim().isEmpty()
                && !adminArea.equalsIgnoreCase(getName())) {
            builder.append(", ").append(adminArea.trim());
        }

        if (country != null
                && !country.trim().isEmpty()
                && !country.equalsIgnoreCase(getName())
                && (adminArea == null || !country.equalsIgnoreCase(adminArea))) {
            builder.append(", ").append(country.trim());
        }

        return builder.toString();
    }

    @NonNull
    public String getCoordinateLabel() {
        return String.format(
                Locale.getDefault(),
                "%.3f, %.3f",
                latitude,
                longitude
        );
    }

    @NonNull
    public static String stableCoordinateKey(double latitude, double longitude) {
        return String.format(Locale.US, "%.4f_%.4f", latitude, longitude);
    }

    public boolean sameIdentity(@Nullable CityLocation other) {
        if (other == null) {
            return false;
        }
        return Objects.equals(getId(), other.getId())
                || (Math.abs(latitude - other.latitude) <= 0.0001d
                && Math.abs(longitude - other.longitude) <= 0.0001d);
    }
}

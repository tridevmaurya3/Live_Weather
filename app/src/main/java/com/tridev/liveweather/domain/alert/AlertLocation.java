package com.tridev.liveweather.domain.alert;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class AlertLocation {

    private final double latitude;
    private final double longitude;
    private final String district;
    private final String state;
    private final String countryCode;

    public AlertLocation(
            double latitude,
            double longitude,
            @Nullable String district,
            @Nullable String state,
            @Nullable String countryCode
    ) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.district = clean(district);
        this.state = clean(state);
        this.countryCode = clean(countryCode);
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    @Nullable public String getDistrict() { return district; }
    @Nullable public String getState() { return state; }
    @Nullable public String getCountryCode() { return countryCode; }

    public boolean isIndia() {
        return countryCode != null && "IN".equalsIgnoreCase(countryCode);
    }

    @NonNull
    public String displayLabel() {
        if (district != null && state != null && !district.equalsIgnoreCase(state)) {
            return district + ", " + state;
        }
        if (district != null) return district;
        if (state != null) return state;
        return String.format(java.util.Locale.getDefault(), "%.3f, %.3f", latitude, longitude);
    }

    private static String clean(@Nullable String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

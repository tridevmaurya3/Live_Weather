package com.tridev.liveweather.domain.alert;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class AlertUiState {

    private final boolean loading;
    private final List<WeatherAlert> alerts;
    private final AlertLocation location;
    private final String message;
    private final long updatedAt;
    private final boolean officialAvailable;

    public AlertUiState(
            boolean loading,
            @NonNull List<WeatherAlert> alerts,
            @Nullable AlertLocation location,
            @Nullable String message,
            long updatedAt,
            boolean officialAvailable
    ) {
        this.loading = loading;
        this.alerts = new ArrayList<>(alerts);
        this.location = location;
        this.message = message;
        this.updatedAt = updatedAt;
        this.officialAvailable = officialAvailable;
    }

    public boolean isLoading() { return loading; }
    @NonNull public List<WeatherAlert> getAlerts() { return new ArrayList<>(alerts); }
    @Nullable public AlertLocation getLocation() { return location; }
    @Nullable public String getMessage() { return message; }
    public long getUpdatedAt() { return updatedAt; }
    public boolean isOfficialAvailable() { return officialAvailable; }

    public boolean hasAlerts() { return !alerts.isEmpty(); }

    @Nullable
    public WeatherAlert highestAlert() {
        WeatherAlert best = null;
        for (WeatherAlert alert : alerts) {
            if (best == null || rank(alert.getSeverity()) > rank(best.getSeverity())) {
                best = alert;
            }
        }
        return best;
    }

    private int rank(WeatherAlert.Severity severity) {
        switch (severity) {
            case RED: return 4;
            case ORANGE: return 3;
            case YELLOW: return 2;
            default: return 1;
        }
    }
}

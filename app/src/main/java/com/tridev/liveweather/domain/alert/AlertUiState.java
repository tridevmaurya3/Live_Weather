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
    private final long checkedAt;
    private final long officialSavedAt;
    private final AlertTruthPolicy.OfficialDelivery officialDelivery;

    public AlertUiState(
            boolean loading,
            @NonNull List<WeatherAlert> alerts,
            @Nullable AlertLocation location,
            @Nullable String message,
            long checkedAt,
            long officialSavedAt,
            @NonNull AlertTruthPolicy.OfficialDelivery officialDelivery
    ) {
        this.loading = loading;
        this.alerts = new ArrayList<>(alerts);
        this.location = location;
        this.message = message;
        this.checkedAt = checkedAt;
        this.officialSavedAt = officialSavedAt;
        this.officialDelivery = officialDelivery;
    }

    /**
     * Backwards-compatible constructor for older call sites while Phase 21 is
     * rolled out. New code should use the explicit delivery constructor above.
     */
    public AlertUiState(
            boolean loading,
            @NonNull List<WeatherAlert> alerts,
            @Nullable AlertLocation location,
            @Nullable String message,
            long updatedAt,
            boolean officialAvailable
    ) {
        this(
                loading,
                alerts,
                location,
                message,
                updatedAt,
                officialAvailable ? updatedAt : 0L,
                officialAvailable
                        ? AlertTruthPolicy.OfficialDelivery.CACHE
                        : AlertTruthPolicy.OfficialDelivery.UNAVAILABLE
        );
    }

    public boolean isLoading() { return loading; }
    @NonNull public List<WeatherAlert> getAlerts() { return new ArrayList<>(alerts); }
    @Nullable public AlertLocation getLocation() { return location; }
    @Nullable public String getMessage() { return message; }

    /** Last attempt/check time, not the age of saved official warning data. */
    public long getUpdatedAt() { return checkedAt; }
    public long getCheckedAt() { return checkedAt; }

    /** Time when official warning data was last network-confirmed or saved. */
    public long getOfficialSavedAt() { return officialSavedAt; }

    @NonNull
    public AlertTruthPolicy.OfficialDelivery getOfficialDelivery() {
        return officialDelivery;
    }

    public boolean isOfficialAvailable() {
        return AlertTruthPolicy.isOfficialSourceAvailable(officialDelivery, officialSavedAt);
    }

    public boolean isOfficialFresh(long nowMillis) {
        return AlertTruthPolicy.isOfficialSourceFresh(
                officialDelivery,
                officialSavedAt,
                nowMillis
        );
    }

    public boolean isOfficialStale(long nowMillis) {
        return AlertTruthPolicy.isOfficialSourceStale(
                officialDelivery,
                officialSavedAt,
                nowMillis
        );
    }

    public boolean canConfirmNoOfficialMatch(long nowMillis) {
        return AlertTruthPolicy.canConfirmNoOfficialMatch(
                officialDelivery,
                officialSavedAt,
                nowMillis,
                alerts
        );
    }

    @NonNull
    public String officialDeliveryLabel(long nowMillis) {
        return AlertTruthPolicy.deliveryLabel(
                officialDelivery,
                officialSavedAt,
                nowMillis
        );
    }

    public boolean hasAlerts() { return !alerts.isEmpty(); }

    public boolean hasOfficialAlerts() {
        for (WeatherAlert alert : alerts) {
            if (alert != null && alert.isOfficial()) return true;
        }
        return false;
    }

    public boolean hasSmartRisk() {
        for (WeatherAlert alert : alerts) {
            if (alert != null && !alert.isOfficial()) return true;
        }
        return false;
    }

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

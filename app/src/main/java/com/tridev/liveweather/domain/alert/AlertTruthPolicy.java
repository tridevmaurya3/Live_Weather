package com.tridev.liveweather.domain.alert;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 21 truth boundary for the Alerts Pro pipeline.
 *
 * Official warning data and app-derived Smart Risk signals are intentionally
 * treated as different evidence classes. Cached official data may remain useful,
 * but stale/unavailable delivery must never be presented as a live all-clear.
 */
public final class AlertTruthPolicy {

    public static final long OFFICIAL_REUSE_MILLIS = 10L * 60L * 1000L;

    public enum OfficialDelivery {
        NOT_APPLICABLE,
        NETWORK,
        NETWORK_EMPTY,
        CACHE,
        UNAVAILABLE
    }

    private AlertTruthPolicy() {
    }

    public static boolean isOfficialCacheFresh(long savedAtMillis, long nowMillis) {
        if (savedAtMillis <= 0L) return false;
        long age = Math.max(0L, nowMillis - savedAtMillis);
        return age <= OFFICIAL_REUSE_MILLIS;
    }

    public static boolean isOfficialSourceAvailable(
            @NonNull OfficialDelivery delivery,
            long savedAtMillis
    ) {
        switch (delivery) {
            case NETWORK:
            case NETWORK_EMPTY:
                return true;
            case CACHE:
                return savedAtMillis > 0L;
            default:
                return false;
        }
    }

    public static boolean isOfficialSourceFresh(
            @NonNull OfficialDelivery delivery,
            long savedAtMillis,
            long nowMillis
    ) {
        switch (delivery) {
            case NETWORK:
            case NETWORK_EMPTY:
                return true;
            case CACHE:
                return isOfficialCacheFresh(savedAtMillis, nowMillis);
            default:
                return false;
        }
    }

    public static boolean isOfficialSourceStale(
            @NonNull OfficialDelivery delivery,
            long savedAtMillis,
            long nowMillis
    ) {
        return delivery == OfficialDelivery.CACHE
                && savedAtMillis > 0L
                && !isOfficialCacheFresh(savedAtMillis, nowMillis);
    }

    public static boolean canConfirmNoOfficialMatch(
            @NonNull OfficialDelivery delivery,
            long savedAtMillis,
            long nowMillis,
            @NonNull List<WeatherAlert> alerts
    ) {
        if (!isOfficialSourceFresh(delivery, savedAtMillis, nowMillis)) return false;
        for (WeatherAlert alert : alerts) {
            if (alert != null && alert.isOfficial() && !alert.isExpired(nowMillis)) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    public static List<WeatherAlert> notificationCandidates(
            @NonNull List<WeatherAlert> alerts,
            @NonNull OfficialDelivery delivery,
            long officialSavedAtMillis,
            long nowMillis
    ) {
        boolean allowOfficial = isOfficialSourceFresh(
                delivery,
                officialSavedAtMillis,
                nowMillis
        );
        List<WeatherAlert> result = new ArrayList<>();
        for (WeatherAlert alert : alerts) {
            if (alert == null || alert.isExpired(nowMillis)) continue;
            if (alert.isOfficial() && !allowOfficial) continue;
            result.add(alert);
        }
        return result;
    }

    @NonNull
    public static String deliveryLabel(
            @NonNull OfficialDelivery delivery,
            long savedAtMillis,
            long nowMillis
    ) {
        switch (delivery) {
            case NETWORK:
                return "Official warning source checked live";
            case NETWORK_EMPTY:
                return "Official warning source checked live · no active match";
            case CACHE:
                if (isOfficialCacheFresh(savedAtMillis, nowMillis)) {
                    return "Official warning data from recent saved check";
                }
                return savedAtMillis > 0L
                        ? "Official warning data is saved but stale"
                        : "Official warning data unavailable";
            case NOT_APPLICABLE:
                return "Official IMD warning scope not applicable to this location";
            default:
                return "Official warning source unavailable";
        }
    }
}

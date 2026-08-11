package com.tridev.liveweather.domain.alert;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AlertMerger {

    private AlertMerger() {
    }

    @NonNull
    public static List<WeatherAlert> merge(
            @NonNull List<WeatherAlert> official,
            @NonNull List<WeatherAlert> smart,
            long nowMillis
    ) {
        Map<String, WeatherAlert> unique = new LinkedHashMap<>();
        add(unique, official, nowMillis);
        add(unique, smart, nowMillis);
        List<WeatherAlert> result = new ArrayList<>(unique.values());
        result.sort(
                Comparator.<WeatherAlert>comparingInt(alert -> rank(alert.getSeverity()))
                        .reversed()
                        .thenComparing(alert -> alert.isOfficial() ? 0 : 1)
                        .thenComparing(
                                Comparator.comparingLong(WeatherAlert::getIssuedAt).reversed()
                        )
        );
        return result;
    }

    private static void add(
            Map<String, WeatherAlert> unique,
            List<WeatherAlert> alerts,
            long nowMillis
    ) {
        for (WeatherAlert alert : alerts) {
            if (alert == null || alert.isExpired(nowMillis)) continue;
            String key = alert.fingerprint();
            WeatherAlert existing = unique.get(key);
            if (existing == null || rank(alert.getSeverity()) > rank(existing.getSeverity())) {
                unique.put(key, alert);
            }
        }
    }

    private static int rank(WeatherAlert.Severity severity) {
        switch (severity) {
            case RED: return 4;
            case ORANGE: return 3;
            case YELLOW: return 2;
            default: return 1;
        }
    }
}

package com.tridev.liveweather.domain.alert;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public final class WeatherAlert {

    public enum Severity {
        INFO,
        YELLOW,
        ORANGE,
        RED
    }

    public enum Source {
        IMD_NOWCAST,
        IMD_DISTRICT_WARNING,
        SMART_FORECAST
    }

    private String id;
    private String title;
    private String message;
    private String locationLabel;
    private String validLabel;
    private Severity severity;
    private Source source;
    private long issuedAt;
    private long expiresAt;

    public WeatherAlert() {
        // Gson constructor.
    }

    public WeatherAlert(
            @NonNull String id,
            @NonNull String title,
            @NonNull String message,
            @Nullable String locationLabel,
            @Nullable String validLabel,
            @NonNull Severity severity,
            @NonNull Source source,
            long issuedAt,
            long expiresAt
    ) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.locationLabel = locationLabel;
        this.validLabel = validLabel;
        this.severity = severity;
        this.source = source;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    @NonNull
    public String getId() {
        return id == null ? fingerprint() : id;
    }

    @NonNull
    public String getTitle() {
        return title == null ? "Weather alert" : title;
    }

    @NonNull
    public String getMessage() {
        return message == null ? "Weather conditions may need attention." : message;
    }

    @Nullable
    public String getLocationLabel() {
        return locationLabel;
    }

    @Nullable
    public String getValidLabel() {
        return validLabel;
    }

    @NonNull
    public Severity getSeverity() {
        return severity == null ? Severity.INFO : severity;
    }

    @NonNull
    public Source getSource() {
        return source == null ? Source.SMART_FORECAST : source;
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired(long nowMillis) {
        return expiresAt > 0L && expiresAt < nowMillis;
    }

    @NonNull
    public String sourceLabel() {
        switch (getSource()) {
            case IMD_NOWCAST:
                return "IMD NOWCAST";
            case IMD_DISTRICT_WARNING:
                return "IMD OFFICIAL";
            default:
                return "SMART RISK";
        }
    }

    public boolean isOfficial() {
        return getSource() == Source.IMD_NOWCAST
                || getSource() == Source.IMD_DISTRICT_WARNING;
    }

    @NonNull
    public String fingerprint() {
        return String.format(
                Locale.ROOT,
                "%s|%s|%s|%s",
                getSource().name(),
                normalize(getTitle()),
                normalize(locationLabel),
                normalize(validLabel)
        );
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

package com.tridev.liveweather.widget;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.domain.CityLocation;

/** Persistent configuration owned independently by every placed widget. */
public final class WidgetPreferences {

    private static final String PREFS = "live_weather_widget_config";

    public enum SourceMode {
        ACTIVE,
        FIXED_CITY
    }

    public enum Appearance {
        GLASS,
        TRANSPARENT
    }

    public static final class Config {
        @NonNull private final SourceMode sourceMode;
        @NonNull private final Appearance appearance;
        @Nullable private final String cityName;
        private final double latitude;
        private final double longitude;

        public Config(
                @NonNull SourceMode sourceMode,
                @NonNull Appearance appearance,
                @Nullable String cityName,
                double latitude,
                double longitude
        ) {
            this.sourceMode = sourceMode;
            this.appearance = appearance;
            this.cityName = cityName;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        @NonNull public SourceMode getSourceMode() { return sourceMode; }
        @NonNull public Appearance getAppearance() { return appearance; }
        @Nullable public String getCityName() { return cityName; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public boolean hasFixedCoordinates() {
            return sourceMode == SourceMode.FIXED_CITY
                    && Double.isFinite(latitude)
                    && Double.isFinite(longitude);
        }
    }

    private final SharedPreferences preferences;

    public WidgetPreferences(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    public Config load(int appWidgetId) {
        String prefix = prefix(appWidgetId);
        SourceMode sourceMode = readEnum(
                prefix + "source",
                SourceMode.ACTIVE,
                SourceMode.class
        );
        Appearance appearance = readEnum(
                prefix + "appearance",
                Appearance.GLASS,
                Appearance.class
        );
        String cityName = preferences.getString(prefix + "city_name", null);
        long latBits = preferences.getLong(prefix + "lat_bits", Double.doubleToLongBits(Double.NaN));
        long lonBits = preferences.getLong(prefix + "lon_bits", Double.doubleToLongBits(Double.NaN));
        return new Config(
                sourceMode,
                appearance,
                cityName,
                Double.longBitsToDouble(latBits),
                Double.longBitsToDouble(lonBits)
        );
    }

    public void save(int appWidgetId, @NonNull Config config) {
        String prefix = prefix(appWidgetId);
        preferences.edit()
                .putString(prefix + "source", config.getSourceMode().name())
                .putString(prefix + "appearance", config.getAppearance().name())
                .putString(prefix + "city_name", config.getCityName())
                .putLong(prefix + "lat_bits", Double.doubleToRawLongBits(config.getLatitude()))
                .putLong(prefix + "lon_bits", Double.doubleToRawLongBits(config.getLongitude()))
                .apply();
    }

    public void saveFixedCity(
            int appWidgetId,
            @NonNull CityLocation city,
            @NonNull Appearance appearance
    ) {
        save(appWidgetId, new Config(
                SourceMode.FIXED_CITY,
                appearance,
                city.getDisplayName(),
                city.getLatitude(),
                city.getLongitude()
        ));
    }

    public void saveActive(int appWidgetId, @NonNull Appearance appearance) {
        save(appWidgetId, new Config(
                SourceMode.ACTIVE,
                appearance,
                null,
                Double.NaN,
                Double.NaN
        ));
    }

    public void delete(int appWidgetId) {
        String prefix = prefix(appWidgetId);
        preferences.edit()
                .remove(prefix + "source")
                .remove(prefix + "appearance")
                .remove(prefix + "city_name")
                .remove(prefix + "lat_bits")
                .remove(prefix + "lon_bits")
                .apply();
    }

    @NonNull
    private String prefix(int appWidgetId) {
        return "widget_" + appWidgetId + "_";
    }

    @NonNull
    private <T extends Enum<T>> T readEnum(
            @NonNull String key,
            @NonNull T fallback,
            @NonNull Class<T> enumClass
    ) {
        String stored = preferences.getString(key, fallback.name());
        if (stored == null) return fallback;
        try {
            return Enum.valueOf(enumClass, stored);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}

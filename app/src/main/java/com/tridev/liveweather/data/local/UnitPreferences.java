package com.tridev.liveweather.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Persistent display-unit preferences shared by the app and home-screen widgets.
 * Provider/cache values remain metric; conversion is presentation-only.
 */
public final class UnitPreferences {

    private static final String PREFS = "live_weather_units";
    private static final String KEY_TEMPERATURE = "temperature";
    private static final String KEY_WIND = "wind";
    private static final String KEY_PRESSURE = "pressure";
    private static final String KEY_PRECIPITATION = "precipitation";
    private static final String KEY_DISTANCE = "distance";

    public enum TemperatureUnit {
        CELSIUS,
        FAHRENHEIT
    }

    public enum WindUnit {
        KMH,
        MPH,
        MPS,
        KNOT
    }

    public enum PressureUnit {
        HPA,
        MBAR,
        INHG
    }

    public enum PrecipitationUnit {
        MM,
        INCH
    }

    public enum DistanceUnit {
        KM,
        MILE
    }

    public static final class Units {
        @NonNull private final TemperatureUnit temperature;
        @NonNull private final WindUnit wind;
        @NonNull private final PressureUnit pressure;
        @NonNull private final PrecipitationUnit precipitation;
        @NonNull private final DistanceUnit distance;

        public Units(
                @NonNull TemperatureUnit temperature,
                @NonNull WindUnit wind,
                @NonNull PressureUnit pressure,
                @NonNull PrecipitationUnit precipitation,
                @NonNull DistanceUnit distance
        ) {
            this.temperature = temperature;
            this.wind = wind;
            this.pressure = pressure;
            this.precipitation = precipitation;
            this.distance = distance;
        }

        @NonNull public TemperatureUnit getTemperature() { return temperature; }
        @NonNull public WindUnit getWind() { return wind; }
        @NonNull public PressureUnit getPressure() { return pressure; }
        @NonNull public PrecipitationUnit getPrecipitation() { return precipitation; }
        @NonNull public DistanceUnit getDistance() { return distance; }

        @NonNull
        public String summary() {
            return temperature == TemperatureUnit.CELSIUS
                    && wind == WindUnit.KMH
                    && pressure == PressureUnit.HPA
                    && precipitation == PrecipitationUnit.MM
                    && distance == DistanceUnit.KM
                    ? "Metric · °C · km/h · hPa · mm · km"
                    : temperature == TemperatureUnit.FAHRENHEIT
                    && wind == WindUnit.MPH
                    && pressure == PressureUnit.INHG
                    && precipitation == PrecipitationUnit.INCH
                    && distance == DistanceUnit.MILE
                    ? "Imperial · °F · mph · inHg · in · mi"
                    : customSummary();
        }

        @NonNull
        private String customSummary() {
            return "Custom · "
                    + temperatureLabel() + " · "
                    + windLabel() + " · "
                    + pressureLabel() + " · "
                    + precipitationLabel() + " · "
                    + distanceLabel();
        }

        @NonNull public String temperatureLabel() {
            return temperature == TemperatureUnit.CELSIUS ? "°C" : "°F";
        }

        @NonNull public String windLabel() {
            switch (wind) {
                case MPH: return "mph";
                case MPS: return "m/s";
                case KNOT: return "kn";
                case KMH:
                default: return "km/h";
            }
        }

        @NonNull public String pressureLabel() {
            switch (pressure) {
                case MBAR: return "mbar";
                case INHG: return "inHg";
                case HPA:
                default: return "hPa";
            }
        }

        @NonNull public String precipitationLabel() {
            return precipitation == PrecipitationUnit.MM ? "mm" : "in";
        }

        @NonNull public String distanceLabel() {
            return distance == DistanceUnit.KM ? "km" : "mi";
        }
    }

    private final SharedPreferences preferences;

    public UnitPreferences(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    public Units load() {
        return new Units(
                readEnum(KEY_TEMPERATURE, TemperatureUnit.CELSIUS, TemperatureUnit.class),
                readEnum(KEY_WIND, WindUnit.KMH, WindUnit.class),
                readEnum(KEY_PRESSURE, PressureUnit.HPA, PressureUnit.class),
                readEnum(KEY_PRECIPITATION, PrecipitationUnit.MM, PrecipitationUnit.class),
                readEnum(KEY_DISTANCE, DistanceUnit.KM, DistanceUnit.class)
        );
    }

    public void save(@NonNull Units units) {
        preferences.edit()
                .putString(KEY_TEMPERATURE, units.getTemperature().name())
                .putString(KEY_WIND, units.getWind().name())
                .putString(KEY_PRESSURE, units.getPressure().name())
                .putString(KEY_PRECIPITATION, units.getPrecipitation().name())
                .putString(KEY_DISTANCE, units.getDistance().name())
                .apply();
    }

    public void saveMetric() {
        save(metric());
    }

    public void saveImperial() {
        save(imperial());
    }

    @NonNull
    public static Units metric() {
        return new Units(
                TemperatureUnit.CELSIUS,
                WindUnit.KMH,
                PressureUnit.HPA,
                PrecipitationUnit.MM,
                DistanceUnit.KM
        );
    }

    @NonNull
    public static Units imperial() {
        return new Units(
                TemperatureUnit.FAHRENHEIT,
                WindUnit.MPH,
                PressureUnit.INHG,
                PrecipitationUnit.INCH,
                DistanceUnit.MILE
        );
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

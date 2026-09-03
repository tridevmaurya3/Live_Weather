package com.tridev.liveweather.ui.gl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;

/**
 * Stage 11 presentation-only vegetation environment policy.
 *
 * <p>Atmospheric humidity/dew-point context is combined later with retained soil moisture,
 * current liquid precipitation, snow and the shared thermal material signal. The policy never
 * changes weather truth, scenery identity or forecast data.</p>
 *
 * <p>Production uses {@link #resolveInto(Sample, float, float, float, float, float, float, float)}
 * with a reusable {@link Sample}; no object is allocated from the render hot path.</p>
 */
public final class VegetationMaterialPolicy {

    public static final class Sample {
        public float vitality;
        public float dryStress;
        public float coldStress;
        public float effectiveMoisture;
    }

    private VegetationMaterialPolicy() {
    }

    /** Returns bounded current-air moisture context. Missing observations remain neutral. */
    public static float resolveAtmosphericMoisture(
            @Nullable WeatherResponse.CurrentWeather current
    ) {
        if (current == null) return 0.50f;
        return resolveAtmosphericMoisture(
                current.getTemperature2m(),
                current.getRelativeHumidity2m(),
                current.getDewPoint2m()
        );
    }

    static float resolveAtmosphericMoisture(
            @Nullable Double temperatureC,
            @Nullable Double relativeHumidityPercent,
            @Nullable Double dewPointC
    ) {
        if (relativeHumidityPercent == null && dewPointC == null) {
            return 0.50f;
        }

        float humidity = relativeHumidityPercent == null
                ? 0.50f
                : clamp01((float) (relativeHumidityPercent / 100d));

        float dewCloseness;
        if (temperatureC == null || dewPointC == null) {
            dewCloseness = humidity;
        } else {
            double depressionC = Math.max(0d, temperatureC - dewPointC);
            dewCloseness = 1f - clamp01((float) (depressionC / 18d));
        }

        return clamp01(humidity * 0.72f + dewCloseness * 0.28f);
    }

    /**
     * Resolves reusable vegetation material signals from current truth and retained ground state.
     * Dry stress requires both low moisture evidence and genuine heat; a freshly created renderer
     * therefore cannot declare vegetation dry just because its retained soil reservoir starts low.
     */
    public static void resolveInto(
            @NonNull Sample out,
            float atmosphericMoisture,
            float soilSaturation,
            float groundWetness,
            float rainIntensity,
            float drizzleIntensity,
            float snowIntensity,
            float thermalBias
    ) {
        float air = clamp01(atmosphericMoisture);
        float soil = clamp01(soilSaturation);
        float wet = clamp01(groundWetness);
        float rain = clamp01(rainIntensity);
        float drizzle = clamp01(drizzleIntensity);
        float snow = clamp01(snowIntensity);
        float thermal = clamp(thermalBias, -1f, 1f);

        float liquidPrecipitation = Math.max(rain, drizzle * 0.58f);
        float precipitationMoisture = smoothstep(0.03f, 0.52f, liquidPrecipitation);
        float retainedRootMoisture = Math.max(soil, wet * 0.78f);

        float effectiveMoisture = Math.max(
                retainedRootMoisture,
                Math.max(air * 0.72f, precipitationMoisture * 0.86f)
        );
        effectiveMoisture = clamp01(effectiveMoisture);

        float warm = Math.max(0f, thermal);
        float cold = Math.max(0f, -thermal);
        float drynessEvidence = smoothstep(0.40f, 0.88f, 1f - effectiveMoisture);
        float heatStress = smoothstep(0.28f, 0.88f, warm);
        float dryStress = clamp01(drynessEvidence * heatStress);
        float coldStress = clamp01(smoothstep(0.36f, 0.92f, cold));

        float vitality = 0.70f
                + effectiveMoisture * 0.30f
                + precipitationMoisture * 0.08f
                - dryStress * 0.44f
                - coldStress * 0.22f
                - snow * 0.16f;

        out.effectiveMoisture = effectiveMoisture;
        out.dryStress = dryStress;
        out.coldStress = coldStress;
        out.vitality = clamp(vitality, 0.28f, 1f);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float x = clamp((value - edge0) / Math.max(0.0001f, edge1 - edge0), 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    private static float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

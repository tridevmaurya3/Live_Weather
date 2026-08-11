package com.tridev.liveweather.ui.scene;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.LiveConditionResolver;

/**
 * Continuous Hero rain renderer.
 *
 * HRS-1B guarantees that active rain never "runs out" after a short animation:
 * every far/mid/near drop is clock-driven and deterministically recycled. A
 * short rain-signal hold prevents a single transient model sample from abruptly
 * turning an obviously wet scene into a dry screen.
 */
public final class HeroRainRenderer {

    private static final long STATE_REFRESH_MILLIS = 4_000L;
    private static final long RAIN_SIGNAL_HOLD_MILLIS = 90_000L;

    private final Paint streakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint veilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final WetGlassOverlay wetGlass = new WetGlassOverlay();

    private WeatherResponse weather;
    private boolean enabled = true;
    private long lastStateRefresh;
    private long lastFrameMillis;
    private long lastPositiveRainSignal;

    private float targetRain;
    private float targetDrizzle;
    private float targetStorm;
    private float targetWindSpeed;
    private float targetWindDirection;

    private float rainIntensity;
    private float drizzleIntensity;
    private float stormIntensity;
    private float windSpeedKmh;
    private float windDirectionDegrees;

    public HeroRainRenderer() {
        streakPaint.setStyle(Paint.Style.STROKE);
        streakPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setWeatherData(@Nullable WeatherResponse weather) {
        this.weather = weather;
        lastStateRefresh = 0L;
        if (weather == null) {
            clearWeatherData();
        }
    }

    public void clearWeatherData() {
        weather = null;
        lastStateRefresh = 0L;
        lastFrameMillis = 0L;
        lastPositiveRainSignal = 0L;
        targetRain = 0f;
        targetDrizzle = 0f;
        targetStorm = 0f;
        targetWindSpeed = 0f;
        targetWindDirection = 0f;
        rainIntensity = 0f;
        drizzleIntensity = 0f;
        stormIntensity = 0f;
        windSpeedKmh = 0f;
        windDirectionDegrees = 0f;
    }

    public void draw(@NonNull Canvas canvas, int width, int height, long nowMillis) {
        draw(canvas, width, height, nowMillis, 0f);
    }

    public void draw(
            @NonNull Canvas canvas,
            int width,
            int height,
            long nowMillis,
            float lightningFlash
    ) {
        if (!enabled || weather == null || width <= 0 || height <= 0) return;

        refreshState(nowMillis);
        smoothState(nowMillis);

        float rain = clamp01(rainIntensity);
        float drizzle = clamp01(drizzleIntensity);
        float effective = Math.max(rain, drizzle * 0.72f);
        if (effective < 0.015f) return;

        drawRainCurtain(canvas, width, height, effective, nowMillis);

        if (drizzle > rain) {
            drawDepthLayer(canvas, width, height, drizzle, nowMillis, 0.20f, true, 0);
            drawDepthLayer(canvas, width, height, drizzle, nowMillis, 0.52f, true, 1);
            drawDepthLayer(canvas, width, height, drizzle, nowMillis, 0.86f, true, 2);
        } else {
            drawDepthLayer(canvas, width, height, rain, nowMillis, 0.18f, false, 0);
            drawDepthLayer(canvas, width, height, rain, nowMillis, 0.50f, false, 1);
            drawDepthLayer(canvas, width, height, rain, nowMillis, 0.92f, false, 2);
        }

        float wetness = clamp01((Math.max(effective, stormIntensity * 0.78f) - 0.10f) / 0.90f);
        wetGlass.draw(canvas, width, height, wetness, clamp01(lightningFlash), nowMillis);
    }

    private void refreshState(long nowMillis) {
        if (nowMillis - lastStateRefresh < STATE_REFRESH_MILLIS) return;
        lastStateRefresh = nowMillis;

        LiveConditionResolver.ResolvedCondition condition = LiveConditionResolver.resolve(weather);
        WeatherResponse.CurrentWeather current = weather.getCurrent();

        int code = condition.getWeatherCode() == null ? 0 : condition.getWeatherCode();
        double precipitationSignal = Math.max(0d, condition.getPrecipitationSignalMm());
        double currentRain = value(current == null ? null : current.getRain());
        double showers = value(current == null ? null : current.getShowers());

        boolean drizzleCode = code >= 51 && code <= 57;
        boolean rainCode = (code >= 61 && code <= 67)
                || (code >= 80 && code <= 82)
                || code >= 95;
        boolean stormCode = code >= 95;

        double drizzle = drizzleCode
                ? clamp(0.24d + precipitationSignal * 0.52d, 0.18d, 0.78d)
                : 0d;
        double rain = rainCode || currentRain > 0d || showers > 0d || precipitationSignal > 0.045d
                ? clamp(
                        0.24d
                                + precipitationSignal * 0.72d
                                + currentRain * 0.46d
                                + showers * 0.56d,
                        0.18d,
                        1d
                )
                : 0d;

        if (drizzleCode && rain < 0.43d) rain = 0d;

        double effectiveSignal = Math.max(rain, drizzle * 0.72d);
        if (effectiveSignal > 0.025d) {
            lastPositiveRainSignal = nowMillis;
        } else if (lastPositiveRainSignal > 0L
                && nowMillis - lastPositiveRainSignal < RAIN_SIGNAL_HOLD_MILLIS) {
            // Do not let one temporary model dip stop an otherwise continuous
            // rainy wallpaper. Hold the last visual target briefly, then normal
            // smoothing will decay once the dry state persists.
            rain = Math.max(rain, Math.max(targetRain, rainIntensity) * 0.94f);
            drizzle = Math.max(drizzle, Math.max(targetDrizzle, drizzleIntensity) * 0.94f);
        }

        targetRain = (float) clamp(rain, 0d, 1d);
        targetDrizzle = (float) clamp(drizzle, 0d, 1d);
        targetStorm = stormCode
                ? (float) clamp(0.62d + Math.max(rain, drizzle) * 0.38d, 0.62d, 1d)
                : 0f;
        targetWindSpeed = (float) Math.max(0d, value(current == null ? null : current.getWindSpeed10m()));
        targetWindDirection = (float) value(current == null ? null : current.getWindDirection10m());
    }

    private void smoothState(long nowMillis) {
        if (lastFrameMillis <= 0L) {
            lastFrameMillis = nowMillis;
            rainIntensity = targetRain;
            drizzleIntensity = targetDrizzle;
            stormIntensity = targetStorm;
            windSpeedKmh = targetWindSpeed;
            windDirectionDegrees = targetWindDirection;
            return;
        }

        float dt = clamp((nowMillis - lastFrameMillis) / 1000f, 0f, 0.20f);
        lastFrameMillis = nowMillis;

        // Fast enough to respond to real weather, slow enough to avoid visible
        // popping when Open-Meteo current/minutely samples disagree for one tick.
        float weatherRate = 1f - (float) Math.exp(-dt * 1.55f);
        float windRate = 1f - (float) Math.exp(-dt * 0.85f);

        rainIntensity = lerp(rainIntensity, targetRain, weatherRate);
        drizzleIntensity = lerp(drizzleIntensity, targetDrizzle, weatherRate);
        stormIntensity = lerp(stormIntensity, targetStorm, weatherRate);
        windSpeedKmh = lerp(windSpeedKmh, targetWindSpeed, windRate);
        windDirectionDegrees = circularLerp(windDirectionDegrees, targetWindDirection, windRate);
    }

    private void drawRainCurtain(
            Canvas canvas,
            int width,
            int height,
            float intensity,
            long nowMillis
    ) {
        float heavy = clamp01((intensity - 0.22f) / 0.78f);
        if (heavy <= 0.01f) return;

        // Atmospheric rain veil: one static full-screen gradient, so it cannot
        // create moving rectangular blocks.
        int topAlpha = clampInt(Math.round(4f + heavy * 15f), 0, 22);
        int midAlpha = clampInt(Math.round(8f + heavy * 28f), 0, 40);
        int lowerAlpha = clampInt(Math.round(12f + heavy * 38f), 0, 54);
        veilPaint.setShader(new LinearGradient(
                0f,
                0f,
                0f,
                height,
                new int[]{
                        Color.argb(topAlpha, 94, 119, 139),
                        Color.argb(midAlpha, 113, 137, 153),
                        Color.argb(lowerAlpha, 133, 149, 156)
                },
                new float[]{0f, 0.58f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, width, height, veilPaint);
        veilPaint.setShader(null);

        // Dense far rain. These streaks recycle forever through positiveMod.
        float seconds = nowMillis / 1000f;
        float direction = (float) Math.toRadians(windDirectionDegrees + 180f);
        float windNorm = clamp01(windSpeedKmh / 75f);
        int count = 90 + Math.round(heavy * 150f);
        float margin = 100f;

        streakPaint.setStrokeCap(Paint.Cap.ROUND);
        streakPaint.setStrokeWidth(0.55f + heavy * 0.38f);
        streakPaint.setColor(Color.argb(
                clampInt(Math.round(28f + heavy * 38f), 20, 78),
                181,
                207,
                224
        ));

        for (int i = 0; i < count; i++) {
            int seed = 701 + i * 151;
            float length = 8f + hash01(seed * 17 + 3) * (14f + heavy * 20f);
            float speed = 210f + heavy * 330f + hash01(seed * 29 + 9) * 150f;
            float cycle = height + length * 3f;
            float y0 = hash01(seed * 37 + 11) * cycle;
            float y = positiveMod(y0 + seconds * speed, cycle) - length * 1.5f;
            float x0 = hash01(seed * 43 + 19) * (width + margin) - margin * 0.5f;
            float drift = seconds * (float) Math.sin(direction) * windSpeedKmh * 0.28f;
            float x = positiveMod(x0 + drift, width + margin) - margin * 0.5f;
            float slant = (float) Math.sin(direction) * length * (0.10f + windNorm * 0.78f);
            canvas.drawLine(x, y, x + slant, y + length, streakPaint);
        }
    }

    private void drawDepthLayer(
            Canvas canvas,
            int width,
            int height,
            float intensity,
            long nowMillis,
            float depth,
            boolean drizzle,
            int layerIndex
    ) {
        float seconds = nowMillis / 1000f;
        float direction = (float) Math.toRadians(windDirectionDegrees + 180f);
        float windNorm = clamp01(windSpeedKmh / 70f);

        int count;
        if (drizzle) {
            count = Math.round(72f + intensity * 92f + depth * 30f);
        } else {
            count = Math.round(82f + intensity * 125f + depth * 42f);
        }

        float baseLength = drizzle
                ? lerp(7f, 22f, depth)
                : lerp(12f, 78f, depth);
        float speed = drizzle
                ? lerp(150f, 390f, depth) * (0.82f + intensity * 0.45f)
                : lerp(300f, 1040f, depth) * (0.80f + intensity * 0.65f);
        float thickness = drizzle
                ? lerp(0.60f, 1.30f, depth)
                : lerp(0.68f, 2.55f, depth);
        int alpha = drizzle
                ? clampInt(Math.round(42f + depth * 58f + intensity * 46f), 30, 150)
                : clampInt(Math.round(48f + depth * 112f + intensity * 62f), 38, 226);

        float margin = 170f;
        for (int i = 0; i < count; i++) {
            int seed = layerIndex * 10_007 + i * 131 + (drizzle ? 73 : 19);
            float length = baseLength * (0.68f + hash01(seed * 17 + 5) * 0.72f);
            float localSpeed = speed * (0.74f + hash01(seed * 29 + 11) * 0.52f);
            float cycle = height + length * 3f;
            float y0 = hash01(seed * 37 + 7) * cycle;
            float y = positiveMod(y0 + seconds * localSpeed, cycle) - length * 1.4f;

            float x0 = hash01(seed * 43 + 17) * (width + margin) - margin * 0.5f;
            float horizontalTravel = seconds
                    * (float) Math.sin(direction)
                    * windSpeedKmh
                    * (0.20f + depth * 0.52f);
            float x = positiveMod(x0 + horizontalTravel, width + margin) - margin * 0.5f;

            float slant = (float) Math.sin(direction)
                    * length
                    * (0.08f + windNorm * (0.50f + depth * 0.78f));
            float vertical = length * (0.95f + Math.abs((float) Math.cos(direction)) * 0.05f);

            if (depth > 0.78f) {
                // Near drop motion blur.
                streakPaint.setStrokeWidth(thickness * 2.6f);
                streakPaint.setColor(Color.argb(
                        clampInt(Math.round(alpha * 0.20f), 10, 56),
                        151,
                        184,
                        207
                ));
                canvas.drawLine(x, y, x + slant, y + vertical, streakPaint);

                // Bright wet core.
                streakPaint.setStrokeWidth(thickness);
                streakPaint.setColor(Color.argb(alpha, 215, 236, 249));
                canvas.drawLine(x, y, x + slant, y + vertical, streakPaint);

                streakPaint.setStrokeWidth(Math.max(0.55f, thickness * 0.34f));
                streakPaint.setColor(Color.argb(
                        clampInt(Math.round(alpha * 0.60f), 20, 152),
                        250,
                        253,
                        255
                ));
                canvas.drawLine(
                        x,
                        y,
                        x + slant * 0.42f,
                        y + vertical * 0.42f,
                        streakPaint
                );
            } else {
                streakPaint.setStrokeWidth(thickness);
                streakPaint.setColor(Color.argb(alpha, 187, 214, 233));
                canvas.drawLine(x, y, x + slant, y + vertical, streakPaint);
            }
        }
    }

    private static double value(Double value) {
        return value == null ? 0d : value;
    }

    private static float hash01(int seed) {
        int n = seed;
        n = (n << 13) ^ n;
        int nn = n * (n * n * 15731 + 789221) + 1376312589;
        return (nn & 0x7fffffff) / 2147483647f;
    }

    private static float positiveMod(float value, float modulo) {
        if (modulo <= 0f) return 0f;
        float result = value % modulo;
        return result < 0f ? result + modulo : result;
    }

    private static float circularLerp(float from, float to, float amount) {
        float delta = ((to - from + 540f) % 360f) - 180f;
        float result = from + delta * amount;
        result %= 360f;
        return result < 0f ? result + 360f : result;
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

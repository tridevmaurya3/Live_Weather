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
 * HRS-1B contract:
 * - rain never runs out while the wallpaper/view remains visible and rain is active;
 * - far/mid/near layers have different scale, speed, alpha and blur character;
 * - drop brightness and length are intentionally non-uniform;
 * - wind and slow gust modulation change slant/motion continuously;
 * - heavy rain adds atmospheric curtain + low spray;
 * - wet-glass droplets remain a separate foreground plane.
 */
public final class HeroRainRenderer {

    private static final long STATE_REFRESH_MILLIS = 4_000L;
    private static final long RAIN_SIGNAL_HOLD_MILLIS = 90_000L;

    private final Paint streakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint veilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dropPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final WetGlassOverlay wetGlass = new WetGlassOverlay();

    private WeatherResponse weather;
    private boolean enabled = true;
    private long lastStateRefresh;
    private long lastFrameMillis;
    private long lastPositiveRainSignal;
    private long animationOriginMillis;

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
        dropPaint.setStyle(Paint.Style.FILL);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setWeatherData(@Nullable WeatherResponse weather) {
        this.weather = weather;
        lastStateRefresh = 0L;
        if (weather == null) clearWeatherData();
    }

    public void clearWeatherData() {
        weather = null;
        lastStateRefresh = 0L;
        lastFrameMillis = 0L;
        lastPositiveRainSignal = 0L;
        animationOriginMillis = 0L;
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

        if (animationOriginMillis == 0L) animationOriginMillis = nowMillis;
        float seconds = Math.max(0L, nowMillis - animationOriginMillis) / 1000f;

        refreshState(nowMillis);
        smoothState(nowMillis);

        float rain = clamp01(rainIntensity);
        float drizzle = clamp01(drizzleIntensity);
        float effective = Math.max(rain, drizzle * 0.72f);
        if (effective < 0.015f) return;

        drawRainCurtain(canvas, width, height, effective, seconds);

        if (drizzle > rain) {
            drawDepthLayer(canvas, width, height, drizzle, seconds, 0.18f, true, 0);
            drawDepthLayer(canvas, width, height, drizzle, seconds, 0.48f, true, 1);
            drawDepthLayer(canvas, width, height, drizzle, seconds, 0.82f, true, 2);
        } else {
            drawDepthLayer(canvas, width, height, rain, seconds, 0.16f, false, 0);
            drawDepthLayer(canvas, width, height, rain, seconds, 0.48f, false, 1);
            drawDepthLayer(canvas, width, height, rain, seconds, 0.90f, false, 2);
        }

        if (effective > 0.48f) {
            drawLowerSpray(canvas, width, height, effective, seconds);
        }

        float wetness = clamp01((Math.max(effective, stormIntensity * 0.80f) - 0.08f) / 0.92f);
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
            float seconds
    ) {
        float heavy = clamp01((intensity - 0.20f) / 0.80f);
        if (heavy <= 0.01f) return;

        int topAlpha = clampInt(Math.round(3f + heavy * 13f), 0, 19);
        int midAlpha = clampInt(Math.round(7f + heavy * 24f), 0, 35);
        int lowerAlpha = clampInt(Math.round(10f + heavy * 34f), 0, 48);
        veilPaint.setShader(new LinearGradient(
                0f,
                0f,
                0f,
                height,
                new int[]{
                        Color.argb(topAlpha, 86, 111, 132),
                        Color.argb(midAlpha, 104, 130, 149),
                        Color.argb(lowerAlpha, 126, 145, 155)
                },
                new float[]{0f, 0.58f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, width, height, veilPaint);
        veilPaint.setShader(null);

        float direction = (float) Math.toRadians(windDirectionDegrees + 180f);
        float windNorm = clamp01(windSpeedKmh / 75f);
        float gust = 0.84f + 0.16f * (float) Math.sin(seconds * 0.52f + 0.9f);
        int count = 110 + Math.round(heavy * 170f);
        float margin = 110f;

        for (int i = 0; i < count; i++) {
            int seed = 701 + i * 151;
            float visibility = 0.35f + hash01(seed * 13 + 5) * 0.65f;
            float length = 5f + hash01(seed * 17 + 3) * (12f + heavy * 18f);
            float speed = (190f + heavy * 300f + hash01(seed * 29 + 9) * 180f) * gust;
            float cycle = height + length * 3f;
            float y0 = hash01(seed * 37 + 11) * cycle;
            float y = positiveMod(y0 + seconds * speed, cycle) - length * 1.5f;
            float x0 = hash01(seed * 43 + 19) * (width + margin) - margin * 0.5f;
            float drift = seconds * (float) Math.sin(direction) * windSpeedKmh * 0.27f;
            float x = positiveMod(x0 + drift, width + margin) - margin * 0.5f;
            float slant = (float) Math.sin(direction) * length * (0.08f + windNorm * 0.72f);

            streakPaint.setStrokeWidth(0.40f + visibility * 0.42f + heavy * 0.14f);
            streakPaint.setColor(Color.argb(
                    clampInt(Math.round((18f + heavy * 35f) * visibility), 8, 66),
                    179,
                    207,
                    226
            ));
            canvas.drawLine(x, y, x + slant, y + length, streakPaint);
        }
    }

    private void drawDepthLayer(
            Canvas canvas,
            int width,
            int height,
            float intensity,
            float seconds,
            float depth,
            boolean drizzle,
            int layerIndex
    ) {
        float direction = (float) Math.toRadians(windDirectionDegrees + 180f);
        float windNorm = clamp01(windSpeedKmh / 70f);
        float gust = 0.86f + 0.14f * (float) Math.sin(seconds * (0.44f + layerIndex * 0.06f) + layerIndex * 1.9f);

        int count;
        if (drizzle) {
            count = Math.round(58f + intensity * 80f + depth * 22f);
        } else {
            count = Math.round(62f + intensity * 108f + depth * 34f);
        }

        float baseLength = drizzle
                ? lerp(5f, 18f, depth)
                : lerp(8f, 64f, depth);
        float speed = drizzle
                ? lerp(145f, 360f, depth) * (0.82f + intensity * 0.45f)
                : lerp(290f, 980f, depth) * (0.80f + intensity * 0.65f);
        float baseThickness = drizzle
                ? lerp(0.48f, 1.12f, depth)
                : lerp(0.55f, 2.15f, depth);

        float margin = 180f;
        for (int i = 0; i < count; i++) {
            int seed = layerIndex * 10_007 + i * 131 + (drizzle ? 73 : 19);
            float visibility = 0.34f + hash01(seed * 11 + 3) * 0.66f;
            float length = baseLength * (0.48f + hash01(seed * 17 + 5) * 1.05f);
            float localSpeed = speed * gust * (0.68f + hash01(seed * 29 + 11) * 0.64f);
            float cycle = height + length * 3f;
            float y0 = hash01(seed * 37 + 7) * cycle;
            float y = positiveMod(y0 + seconds * localSpeed, cycle) - length * 1.4f;

            float x0 = hash01(seed * 43 + 17) * (width + margin) - margin * 0.5f;
            float horizontalTravel = seconds
                    * (float) Math.sin(direction)
                    * windSpeedKmh
                    * (0.18f + depth * 0.48f);
            float x = positiveMod(x0 + horizontalTravel, width + margin) - margin * 0.5f;

            float slant = (float) Math.sin(direction)
                    * length
                    * (0.07f + windNorm * (0.48f + depth * 0.74f));
            float vertical = length * (0.95f + Math.abs((float) Math.cos(direction)) * 0.05f);
            float thickness = baseThickness * (0.72f + visibility * 0.50f);

            int alpha = drizzle
                    ? clampInt(Math.round((32f + depth * 58f + intensity * 42f) * visibility), 18, 132)
                    : clampInt(Math.round((38f + depth * 105f + intensity * 58f) * visibility), 20, 214);

            if (depth > 0.78f) {
                // Soft motion body.
                streakPaint.setStrokeWidth(thickness * 2.5f);
                streakPaint.setColor(Color.argb(
                        clampInt(Math.round(alpha * 0.18f), 7, 44),
                        150,
                        183,
                        207
                ));
                canvas.drawLine(x, y, x + slant, y + vertical, streakPaint);

                // Main drop streak.
                streakPaint.setStrokeWidth(thickness);
                streakPaint.setColor(Color.argb(alpha, 211, 234, 248));
                canvas.drawLine(x, y, x + slant, y + vertical, streakPaint);

                // Small bright head on only some near drops, avoiding the old
                // "all identical white lines" appearance.
                if (hash01(seed * 79 + 23) > 0.54f) {
                    dropPaint.setColor(Color.argb(
                            clampInt(Math.round(alpha * 0.56f), 18, 132),
                            244,
                            251,
                            255
                    ));
                    float headRadius = Math.max(0.7f, thickness * 0.70f);
                    canvas.drawCircle(x, y, headRadius, dropPaint);
                }

                streakPaint.setStrokeWidth(Math.max(0.45f, thickness * 0.31f));
                streakPaint.setColor(Color.argb(
                        clampInt(Math.round(alpha * 0.52f), 14, 138),
                        249,
                        253,
                        255
                ));
                canvas.drawLine(
                        x,
                        y,
                        x + slant * 0.34f,
                        y + vertical * 0.34f,
                        streakPaint
                );
            } else {
                streakPaint.setStrokeWidth(thickness);
                streakPaint.setColor(Color.argb(alpha, 183, 211, 231));
                canvas.drawLine(x, y, x + slant, y + vertical, streakPaint);
            }
        }
    }

    private void drawLowerSpray(
            Canvas canvas,
            int width,
            int height,
            float intensity,
            float seconds
    ) {
        float heavy = clamp01((intensity - 0.48f) / 0.52f);
        if (heavy <= 0f) return;

        int alpha = clampInt(Math.round(14f + heavy * 32f), 0, 48);
        veilPaint.setShader(new LinearGradient(
                0f,
                height * 0.62f,
                0f,
                height,
                new int[]{
                        Color.TRANSPARENT,
                        Color.argb(alpha / 2, 184, 202, 213),
                        Color.argb(alpha, 197, 211, 218)
                },
                new float[]{0f, 0.58f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, height * 0.62f, width, height, veilPaint);
        veilPaint.setShader(null);

        int streaks = 12 + Math.round(heavy * 18f);
        for (int i = 0; i < streaks; i++) {
            int seed = 9001 + i * 101;
            float x = hash01(seed * 17) * width;
            float y = height * (0.72f + hash01(seed * 31) * 0.26f);
            float sway = (float) Math.sin(seconds * 0.9f + i * 1.7f) * (2f + heavy * 5f);
            float len = 3f + hash01(seed * 43) * (5f + heavy * 8f);
            streakPaint.setStrokeWidth(0.7f + heavy * 0.7f);
            streakPaint.setColor(Color.argb(
                    clampInt(Math.round(24f + heavy * 42f), 18, 70),
                    218,
                    232,
                    239
            ));
            canvas.drawLine(x, y, x + sway, y - len, streakPaint);
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

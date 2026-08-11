package com.tridev.liveweather.ui.scene;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.LiveConditionResolver;

/**
 * Hero rain renderer used above the shared nature scene.
 *
 * HRS-1A contract:
 * - legacy dot/short-line rain is replaced by three depth layers;
 * - wind changes both streak angle and horizontal travel;
 * - heavy rain adds a dense atmospheric curtain;
 * - foreground droplets live on the virtual glass surface and slowly slide;
 * - no network or bitmap synthesis happens from the frame loop.
 */
public final class HeroRainRenderer {

    private static final long STATE_REFRESH_MILLIS = 4_000L;

    private final Paint streakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private WeatherResponse weather;
    private boolean enabled = true;
    private long lastStateRefresh;

    private float rainIntensity;
    private float drizzleIntensity;
    private float stormIntensity;
    private float windSpeedKmh;
    private float windDirectionDegrees;

    public HeroRainRenderer() {
        streakPaint.setStyle(Paint.Style.STROKE);
        streakPaint.setStrokeCap(Paint.Cap.ROUND);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setWeatherData(@Nullable WeatherResponse weather) {
        this.weather = weather;
        lastStateRefresh = 0L;
    }

    public void clearWeatherData() {
        weather = null;
        lastStateRefresh = 0L;
        rainIntensity = 0f;
        drizzleIntensity = 0f;
        stormIntensity = 0f;
        windSpeedKmh = 0f;
        windDirectionDegrees = 0f;
    }

    public void draw(@NonNull Canvas canvas, int width, int height, long nowMillis) {
        if (!enabled || weather == null || width <= 0 || height <= 0) {
            return;
        }

        refreshState(nowMillis);

        float rain = clamp01(rainIntensity);
        float drizzle = clamp01(drizzleIntensity);
        float effective = Math.max(rain, drizzle * 0.68f);
        if (effective < 0.015f) {
            return;
        }

        // Far rain first: this creates the visual sense of a wet atmosphere,
        // rather than isolated dots floating over a clear background.
        drawRainCurtain(canvas, width, height, effective, nowMillis);

        if (drizzle > rain) {
            drawDepthLayer(canvas, width, height, drizzle, nowMillis, 0.26f, true, 0);
            drawDepthLayer(canvas, width, height, drizzle, nowMillis, 0.58f, true, 1);
            drawDepthLayer(canvas, width, height, drizzle, nowMillis, 0.88f, true, 2);
        } else {
            drawDepthLayer(canvas, width, height, rain, nowMillis, 0.22f, false, 0);
            drawDepthLayer(canvas, width, height, rain, nowMillis, 0.55f, false, 1);
            drawDepthLayer(canvas, width, height, rain, nowMillis, 0.92f, false, 2);
        }

        // Foreground glass is intentionally last so the droplets feel as if
        // they are on the phone glass, not in the distant weather volume.
        drawWetGlass(canvas, width, height, Math.max(effective, stormIntensity * 0.75f), nowMillis);
    }

    private void refreshState(long nowMillis) {
        if (nowMillis - lastStateRefresh < STATE_REFRESH_MILLIS) {
            return;
        }
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
                ? clamp(0.24d + precipitationSignal * 0.48d, 0.18d, 0.76d)
                : 0d;
        double rain = rainCode || currentRain > 0d || showers > 0d || precipitationSignal > 0.045d
                ? clamp(
                        0.22d
                                + precipitationSignal * 0.68d
                                + currentRain * 0.44d
                                + showers * 0.52d,
                        0.16d,
                        1d
                )
                : 0d;

        if (drizzleCode && rain < 0.42d) {
            rain = 0d;
        }

        rainIntensity = (float) rain;
        drizzleIntensity = (float) drizzle;
        stormIntensity = stormCode
                ? (float) clamp(0.58d + Math.max(rain, drizzle) * 0.42d, 0.58d, 1d)
                : 0f;
        windSpeedKmh = (float) Math.max(0d, value(current == null ? null : current.getWindSpeed10m()));
        windDirectionDegrees = (float) value(current == null ? null : current.getWindDirection10m());
    }

    private void drawRainCurtain(
            Canvas canvas,
            int width,
            int height,
            float intensity,
            long nowMillis
    ) {
        float heavy = clamp01((intensity - 0.28f) / 0.72f);
        if (heavy <= 0.01f) {
            return;
        }

        // Whole-screen wet atmospheric veil. It has no hard bitmap boundary,
        // therefore it cannot create moving rectangular artifacts.
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.argb(
                clampInt(Math.round(10f + heavy * 24f), 0, 38),
                116,
                139,
                156
        ));
        canvas.drawRect(0f, 0f, width, height, fillPaint);

        float seconds = nowMillis / 1000f;
        float direction = (float) Math.toRadians(windDirectionDegrees + 180f);
        float wind = (float) Math.sin(direction) * (3f + windSpeedKmh * 0.12f);
        int bands = 18 + Math.round(heavy * 26f);

        streakPaint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < bands; i++) {
            int seed = i * 337 + 41;
            float x0 = hash01(seed * 17 + 3) * (width + 180f) - 90f;
            float x = positiveMod(x0 + seconds * wind * (0.55f + hash01(seed) * 0.8f), width + 180f) - 90f;
            float length = height * (0.20f + hash01(seed * 31 + 7) * 0.36f);
            float y0 = hash01(seed * 43 + 13) * (height + length);
            float y = positiveMod(y0 + seconds * (90f + heavy * 180f), height + length) - length;
            float slant = (float) Math.sin(direction) * length * (0.05f + clamp01(windSpeedKmh / 75f) * 0.14f);

            streakPaint.setStrokeWidth(0.7f + heavy * 0.55f);
            streakPaint.setColor(Color.argb(
                    clampInt(Math.round(10f + heavy * 21f), 6, 36),
                    196,
                    218,
                    231
            ));
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

        int countBase;
        int countExtra;
        if (drizzle) {
            countBase = depth < 0.4f ? 78 : depth < 0.8f ? 56 : 30;
            countExtra = depth < 0.4f ? 80 : 54;
        } else {
            countBase = depth < 0.4f ? 96 : depth < 0.8f ? 68 : 38;
            countExtra = depth < 0.4f ? 110 : depth < 0.8f ? 88 : 56;
        }
        int count = countBase + Math.round(intensity * countExtra);

        float baseLength = drizzle
                ? lerp(8f, 22f, depth)
                : lerp(13f, 62f, depth);
        float speed = drizzle
                ? lerp(150f, 360f, depth) * (0.82f + intensity * 0.42f)
                : lerp(300f, 920f, depth) * (0.78f + intensity * 0.62f);
        float thickness = drizzle
                ? lerp(0.65f, 1.35f, depth)
                : lerp(0.75f, 2.35f, depth);
        int alpha = drizzle
                ? clampInt(Math.round(46f + depth * 64f + intensity * 38f), 34, 148)
                : clampInt(Math.round(52f + depth * 105f + intensity * 56f), 42, 220);

        float margin = 140f;
        for (int i = 0; i < count; i++) {
            int seed = layerIndex * 10007 + i * 131 + (drizzle ? 73 : 19);
            float variation = 0.72f + hash01(seed * 17 + 5) * 0.58f;
            float length = baseLength * variation;
            float localSpeed = speed * (0.76f + hash01(seed * 29 + 11) * 0.48f);
            float cycle = height + length * 3f;

            float y0 = hash01(seed * 37 + 7) * cycle;
            float y = positiveMod(y0 + seconds * localSpeed, cycle) - length * 1.4f;

            float x0 = hash01(seed * 43 + 17) * (width + margin) - margin * 0.5f;
            float horizontalTravel = seconds
                    * (float) Math.sin(direction)
                    * (windSpeedKmh * (0.22f + depth * 0.44f));
            float x = positiveMod(x0 + horizontalTravel, width + margin) - margin * 0.5f;

            float slant = (float) Math.sin(direction)
                    * length
                    * (0.10f + windNorm * (0.42f + depth * 0.72f));
            float vertical = length * (0.94f + Math.abs((float) Math.cos(direction)) * 0.06f);

            // Near drops get a soft motion-blur body plus a crisp bright core.
            if (depth > 0.78f) {
                streakPaint.setStrokeWidth(thickness * 2.25f);
                streakPaint.setColor(Color.argb(
                        clampInt(Math.round(alpha * 0.22f), 10, 56),
                        166,
                        196,
                        216
                ));
                canvas.drawLine(x, y, x + slant, y + vertical, streakPaint);

                streakPaint.setStrokeWidth(thickness);
                streakPaint.setColor(Color.argb(alpha, 211, 232, 245));
                canvas.drawLine(x, y, x + slant, y + vertical, streakPaint);

                float highlightScale = 0.38f;
                streakPaint.setStrokeWidth(Math.max(0.55f, thickness * 0.38f));
                streakPaint.setColor(Color.argb(
                        clampInt(Math.round(alpha * 0.62f), 18, 148),
                        242,
                        250,
                        255
                ));
                canvas.drawLine(
                        x,
                        y,
                        x + slant * highlightScale,
                        y + vertical * highlightScale,
                        streakPaint
                );
            } else {
                streakPaint.setStrokeWidth(thickness);
                streakPaint.setColor(Color.argb(alpha, 190, 217, 235));
                canvas.drawLine(x, y, x + slant, y + vertical, streakPaint);
            }
        }
    }

    private void drawWetGlass(
            Canvas canvas,
            int width,
            int height,
            float intensity,
            long nowMillis
    ) {
        float wetness = clamp01((intensity - 0.18f) / 0.82f);
        if (wetness <= 0.01f) {
            return;
        }

        float seconds = nowMillis / 1000f;
        int count = 7 + Math.round(wetness * 19f);

        for (int i = 0; i < count; i++) {
            int seed = i * 911 + 101;
            float radius = 3.8f + hash01(seed * 13 + 7) * (5.5f + wetness * 8.0f);
            float horizontalDrift = (hash01(seed * 19 + 3) - 0.5f) * radius * 0.7f;
            float x = hash01(seed * 31 + 9) * width + horizontalDrift;

            float slideSpeed = (5f + hash01(seed * 47 + 21) * 18f)
                    * (0.34f + wetness * 0.88f);
            float cycle = height + radius * 18f;
            float y0 = hash01(seed * 59 + 17) * cycle;
            float y = positiveMod(y0 + seconds * slideSpeed, cycle) - radius * 8f;

            float stretch = 1.0f + wetness * 0.45f + hash01(seed * 67 + 5) * 0.38f;
            rect.set(
                    x - radius,
                    y - radius * stretch,
                    x + radius,
                    y + radius * stretch
            );

            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Color.argb(
                    clampInt(Math.round(10f + wetness * 18f), 8, 30),
                    214,
                    235,
                    246
            ));
            canvas.drawOval(rect, fillPaint);

            // Dark refractive lower edge.
            rimPaint.setStrokeWidth(Math.max(0.65f, radius * 0.12f));
            rimPaint.setColor(Color.argb(
                    clampInt(Math.round(20f + wetness * 25f), 16, 48),
                    74,
                    102,
                    120
            ));
            canvas.drawArc(rect, 12f, 155f, false, rimPaint);

            // Bright upper rim catches sky/lightning highlights.
            rimPaint.setStrokeWidth(Math.max(0.55f, radius * 0.10f));
            rimPaint.setColor(Color.argb(
                    clampInt(Math.round(42f + wetness * 58f), 36, 112),
                    244,
                    251,
                    255
            ));
            canvas.drawArc(rect, 190f, 120f, false, rimPaint);

            fillPaint.setColor(Color.argb(
                    clampInt(Math.round(48f + wetness * 62f), 36, 120),
                    250,
                    254,
                    255
            ));
            canvas.drawCircle(
                    x - radius * 0.33f,
                    y - radius * stretch * 0.38f,
                    Math.max(0.8f, radius * 0.17f),
                    fillPaint
            );

            // A subset of larger drops leaves a sliding water trail.
            if (wetness > 0.32f && radius > 7.0f && hash01(seed * 79 + 11) > 0.48f) {
                float trail = radius * (2.6f + wetness * 4.8f);
                rimPaint.setStrokeWidth(Math.max(0.65f, radius * 0.11f));
                rimPaint.setColor(Color.argb(
                        clampInt(Math.round(15f + wetness * 25f), 12, 45),
                        207,
                        229,
                        241
                ));
                canvas.drawLine(x, y - radius * stretch, x, y - radius * stretch - trail, rimPaint);
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
        return ((nn & 0x7fffffff) / 2147483647f);
    }

    private static float positiveMod(float value, float modulo) {
        float result = value % modulo;
        return result < 0f ? result + modulo : result;
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

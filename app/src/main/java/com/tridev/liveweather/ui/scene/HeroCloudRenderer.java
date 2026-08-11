package com.tridev.liveweather.ui.scene;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.LiveConditionResolver;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Hero cloud renderer.
 *
 * HRS-3 contract:
 * - no bitmap bounds and no rectangular moving artifacts;
 * - broad smooth cloud banks instead of repeated semicircle/scallop shapes;
 * - current + nearest 15-minute cloud cover + WMO condition decide visibility;
 * - rain/storm states produce fewer, larger, darker masses;
 * - wind moves cloud banks continuously while vertical motion remains restrained.
 */
public final class HeroCloudRenderer {

    private static final long STATE_REFRESH_MILLIS = 5_000L;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hazePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cloudPath = new Path();

    private WeatherResponse weather;
    private boolean enabled = true;
    private long lastStateRefresh;
    private long animationOriginMillis;

    private float cloudCover;
    private float rainIntensity;
    private float stormIntensity;
    private float windSpeedKmh;
    private float windDirectionDegrees;
    private boolean daylight = true;

    public HeroCloudRenderer() {
        fillPaint.setStyle(Paint.Style.FILL);
        hazePaint.setStyle(Paint.Style.FILL);
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
        animationOriginMillis = 0L;
        cloudCover = 0f;
        rainIntensity = 0f;
        stormIntensity = 0f;
        windSpeedKmh = 0f;
        windDirectionDegrees = 0f;
        daylight = true;
    }

    public void draw(@NonNull Canvas canvas, int width, int height, long nowMillis) {
        if (!enabled || weather == null || width <= 0 || height <= 0) return;
        refreshState(nowMillis);

        float cover = clamp01(cloudCover);
        if (cover < 0.025f) return;

        if (animationOriginMillis == 0L) animationOriginMillis = nowMillis;
        float seconds = Math.max(0L, nowMillis - animationOriginMillis) / 1000f;

        // Fewer, broader layers read as atmospheric cloud banks instead of rows
        // of repeated cartoon puffs.
        int layerCount = cover >= 0.88f ? 3 : cover >= 0.34f ? 2 : 1;
        float direction = (float) Math.toRadians(windDirectionDegrees + 180f);
        float flowX = (float) Math.sin(direction);
        float flowY = -(float) Math.cos(direction);

        // Very high cover gets one non-rectangular upper veil to connect masses.
        if (cover > 0.76f) {
            drawUpperVeil(canvas, width, height, cover, stormIntensity, seconds, flowX);
        }

        for (int layer = 0; layer < layerCount; layer++) {
            float depth = layerCount == 1
                    ? 0.62f
                    : 0.24f + layer * (0.70f / Math.max(1, layerCount - 1));
            CloudStyle style = chooseStyle(cover, layer, layerCount);
            int count = cloudCount(style, cover, layer);
            float baseSpeed = (0.85f + windSpeedKmh * 0.090f) * (0.52f + depth * 0.72f);

            for (int i = 0; i < count; i++) {
                int seed = style.ordinal() * 100_003 + layer * 5_009 + i * 977;
                float scale = cloudScale(style, depth, seed);
                float cloudWidth = width * scale;
                float cloudHeight = cloudWidth * cloudAspect(style);
                float track = width + cloudWidth * 2.2f;
                float origin = hash01(seed * 17 + 3) * track - cloudWidth * 1.10f;
                float travel = seconds * baseSpeed * (0.76f + hash01(seed * 29 + 11) * 0.48f);
                float x = positiveMod(origin + flowX * travel, track) - cloudWidth * 1.10f;

                float verticalWave = (float) Math.sin(
                        seconds * (0.012f + depth * 0.010f) + seed * 0.013f
                );
                float yDrift = verticalWave * height * (0.004f + depth * 0.008f)
                        + flowY * height * 0.0035f * depth;
                float y = cloudY(height, style, depth, seed) + yDrift;

                float alpha = cloudAlpha(style, cover, depth);
                drawCloudMass(
                        canvas,
                        x,
                        y,
                        cloudWidth,
                        cloudHeight,
                        seed,
                        style,
                        alpha
                );
            }
        }
    }

    private void refreshState(long nowMillis) {
        if (nowMillis - lastStateRefresh < STATE_REFRESH_MILLIS) return;
        lastStateRefresh = nowMillis;

        WeatherResponse.CurrentWeather current = weather.getCurrent();
        LiveConditionResolver.ResolvedCondition resolved = LiveConditionResolver.resolve(weather);
        int code = resolved.getWeatherCode() == null ? 0 : resolved.getWeatherCode();

        double currentCover = value(current == null ? null : current.getCloudCover()) / 100d;
        double nearest15 = nearestMinutelyCloudCover(weather, current) / 100d;
        double blended;
        if (nearest15 >= 0d) {
            blended = currentCover * 0.58d + nearest15 * 0.42d;
            blended = Math.max(blended, Math.min(currentCover, nearest15) * 0.92d);
        } else {
            blended = currentCover;
        }

        double precip = Math.max(0d, resolved.getPrecipitationSignalMm());
        double currentRain = value(current == null ? null : current.getRain());
        double showers = value(current == null ? null : current.getShowers());
        double rainSignal = clamp(
                precip * 0.55d + currentRain * 0.44d + showers * 0.52d,
                0d,
                1d
        );

        double floor = 0d;
        if (code == 1) floor = 0.10d;
        if (code == 2) floor = 0.38d;
        if (code == 3) floor = 0.80d;
        if (code == 45 || code == 48) floor = 0.68d;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) floor = 0.70d;
        if (code >= 95) floor = 0.94d;
        if (rainSignal > 0.10d) floor = Math.max(floor, 0.66d + rainSignal * 0.22d);

        cloudCover = (float) clamp(Math.max(blended, floor), 0d, 1d);
        rainIntensity = (float) rainSignal;
        stormIntensity = code >= 95
                ? (float) clamp(0.68d + rainSignal * 0.32d, 0.68d, 1d)
                : 0f;
        windSpeedKmh = (float) Math.max(0d, value(current == null ? null : current.getWindSpeed10m()));
        windDirectionDegrees = (float) value(current == null ? null : current.getWindDirection10m());
        daylight = current == null || current.getIsDay() == null || current.getIsDay() == 1;
    }

    private CloudStyle chooseStyle(float cover, int layer, int layerCount) {
        if (stormIntensity > 0.10f) {
            return layer == layerCount - 1 ? CloudStyle.STORM : CloudStyle.STRATUS;
        }
        if (rainIntensity > 0.18f) {
            return layer == 0 ? CloudStyle.STRATUS : CloudStyle.RAIN_CUMULUS;
        }
        if (cover > 0.80f) {
            return layer == 0 ? CloudStyle.THIN : CloudStyle.STRATUS;
        }
        if (cover > 0.48f) {
            return layer == 0 ? CloudStyle.THIN : CloudStyle.CUMULUS;
        }
        return CloudStyle.CUMULUS;
    }

    private int cloudCount(CloudStyle style, float cover, int layer) {
        switch (style) {
            case STRATUS:
                return 2 + (cover > 0.86f ? 1 : 0);
            case STORM:
                return 2 + Math.min(1, layer);
            case RAIN_CUMULUS:
                return 2 + (cover > 0.80f ? 1 : 0);
            case THIN:
                return 1 + (cover > 0.62f ? 1 : 0);
            case CUMULUS:
            default:
                return 2 + (cover > 0.54f ? 1 : 0);
        }
    }

    private float cloudScale(CloudStyle style, float depth, int seed) {
        float jitter = 0.84f + hash01(seed * 37 + 13) * 0.32f;
        float base;
        switch (style) {
            case STRATUS:
                base = 0.72f;
                break;
            case STORM:
                base = 0.68f;
                break;
            case RAIN_CUMULUS:
                base = 0.58f;
                break;
            case THIN:
                base = 0.62f;
                break;
            case CUMULUS:
            default:
                base = 0.50f;
                break;
        }
        return base * (0.76f + depth * 0.46f) * jitter;
    }

    private float cloudAspect(CloudStyle style) {
        switch (style) {
            case STRATUS:
                return 0.22f;
            case STORM:
                return 0.40f;
            case RAIN_CUMULUS:
                return 0.34f;
            case THIN:
                return 0.16f;
            case CUMULUS:
            default:
                return 0.32f;
        }
    }

    private float cloudY(int height, CloudStyle style, float depth, int seed) {
        float jitter = (hash01(seed * 47 + 19) - 0.5f) * height * 0.18f;
        float base;
        switch (style) {
            case STRATUS:
                base = height * (0.12f + depth * 0.14f);
                break;
            case STORM:
                base = height * (0.06f + depth * 0.15f);
                break;
            case RAIN_CUMULUS:
                base = height * (0.09f + depth * 0.17f);
                break;
            case THIN:
                base = height * (0.08f + depth * 0.08f);
                break;
            case CUMULUS:
            default:
                base = height * (0.08f + depth * 0.17f);
                break;
        }
        return base + jitter;
    }

    private float cloudAlpha(CloudStyle style, float cover, float depth) {
        float base;
        switch (style) {
            case STRATUS:
                base = 0.44f;
                break;
            case STORM:
                base = 0.72f;
                break;
            case RAIN_CUMULUS:
                base = 0.58f;
                break;
            case THIN:
                base = 0.24f;
                break;
            case CUMULUS:
            default:
                base = 0.42f;
                break;
        }
        return clamp(base + cover * 0.20f + depth * 0.05f, 0.22f, 0.92f);
    }

    private void drawUpperVeil(
            Canvas canvas,
            int width,
            int height,
            float cover,
            float storm,
            float seconds,
            float flowX
    ) {
        int alpha = clampInt(Math.round((cover - 0.76f) / 0.24f * (storm > 0.1f ? 54f : 30f)), 0, 58);
        if (alpha <= 0) return;

        float drift = (float) Math.sin(seconds * 0.012f) * width * 0.025f + flowX * width * 0.015f;
        float top = height * 0.02f;
        float bottom = height * (storm > 0.1f ? 0.34f : 0.26f);
        int tone = storm > 0.1f ? Color.rgb(57, 66, 79) : Color.rgb(202, 212, 220);
        hazePaint.setShader(new LinearGradient(
                drift,
                top,
                width + drift,
                bottom,
                new int[]{
                        withAlpha(tone, alpha),
                        withAlpha(tone, Math.max(0, alpha - 8)),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.58f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, top, width, bottom, hazePaint);
        hazePaint.setShader(null);
    }

    private void drawCloudMass(
            Canvas canvas,
            float x,
            float y,
            float width,
            float height,
            int seed,
            CloudStyle style,
            float alpha
    ) {
        buildCloudPath(cloudPath, x, y, width, height, seed, style);

        int[] tones = cloudTones(style);
        int top = withAlpha(tones[0], Math.round(alpha * 255f));
        int middle = withAlpha(tones[1], Math.round(alpha * 246f));
        int bottom = withAlpha(tones[2], Math.round(alpha * 255f));

        // One very restrained atmospheric shadow. The previous multiple offset
        // passes made clouds look like stacked horizontal bands.
        canvas.save();
        canvas.translate(width * 0.003f, height * 0.025f);
        fillPaint.setShader(null);
        fillPaint.setColor(withAlpha(tones[2], Math.round(alpha * 34f)));
        canvas.drawPath(cloudPath, fillPaint);
        canvas.restore();

        fillPaint.setShader(new LinearGradient(
                0f,
                y,
                0f,
                y + height,
                new int[]{top, middle, bottom},
                new float[]{0f, 0.52f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawPath(cloudPath, fillPaint);
        fillPaint.setShader(null);

        // Tiny diffuse top-light only; no decorative interior stripe.
        if (daylight && style != CloudStyle.STORM && style != CloudStyle.STRATUS) {
            canvas.save();
            canvas.translate(0f, -height * 0.012f);
            fillPaint.setColor(Color.argb(Math.round(alpha * 18f), 255, 255, 255));
            canvas.drawPath(cloudPath, fillPaint);
            canvas.restore();
        }
    }

    /**
     * Creates one smooth atmospheric silhouette using broad anchor points.
     * There are no repeated semicircle lobes and the underside is nearly flat,
     * which removes the cartoon scallop pattern visible in the previous build.
     */
    private void buildCloudPath(
            Path path,
            float x,
            float y,
            float width,
            float height,
            int seed,
            CloudStyle style
    ) {
        path.reset();

        float topBase;
        float dome;
        float bottomBase;
        float topNoise;
        int topSegments;

        switch (style) {
            case THIN:
                topBase = 0.56f;
                dome = 0.13f;
                bottomBase = 0.72f;
                topNoise = 0.055f;
                topSegments = 6;
                break;
            case STRATUS:
                topBase = 0.50f;
                dome = 0.10f;
                bottomBase = 0.76f;
                topNoise = 0.065f;
                topSegments = 6;
                break;
            case STORM:
                topBase = 0.54f;
                dome = 0.29f;
                bottomBase = 0.88f;
                topNoise = 0.11f;
                topSegments = 7;
                break;
            case RAIN_CUMULUS:
                topBase = 0.56f;
                dome = 0.24f;
                bottomBase = 0.84f;
                topNoise = 0.095f;
                topSegments = 7;
                break;
            case CUMULUS:
            default:
                topBase = 0.58f;
                dome = 0.27f;
                bottomBase = 0.80f;
                topNoise = 0.095f;
                topSegments = 7;
                break;
        }

        float leftBottomY = y + height * (bottomBase - 0.015f);
        float firstX = x + width * 0.055f;
        float firstY = y + height * (topBase - dome * 0.20f);
        path.moveTo(x, leftBottomY);
        path.cubicTo(
                x + width * 0.012f,
                leftBottomY - height * 0.08f,
                firstX - width * 0.018f,
                firstY + height * 0.035f,
                firstX,
                firstY
        );

        float previousX = firstX;
        float previousY = firstY;
        for (int i = 1; i <= topSegments; i++) {
            float t = i / (float) topSegments;
            float anchorX = x + width * (0.055f + 0.89f * t);
            float envelope = (float) Math.sin(Math.PI * t);
            float broadShape = dome * envelope * (0.68f + hash01(seed + i * 131) * 0.38f);
            float irregular = (hash01(seed * 17 + i * 173) - 0.5f) * topNoise;
            float anchorY = y + height * (topBase - broadShape + irregular);

            float midX = (previousX + anchorX) * 0.5f;
            float midY = (previousY + anchorY) * 0.5f;
            path.quadTo(previousX, previousY, midX, midY);
            previousX = anchorX;
            previousY = anchorY;
        }

        float rightTopX = x + width * 0.955f;
        float rightTopY = y + height * (topBase - dome * 0.18f);
        path.quadTo(previousX, previousY, rightTopX, rightTopY);

        float rightBottomY = y + height * (bottomBase + 0.010f);
        path.cubicTo(
                x + width * 0.985f,
                rightTopY + height * 0.04f,
                x + width,
                rightBottomY - height * 0.07f,
                x + width,
                rightBottomY
        );

        // Broad low-frequency underside, intentionally not scalloped.
        float prevBottomX = x + width;
        float prevBottomY = rightBottomY;
        int bottomSegments = 5;
        for (int i = 1; i <= bottomSegments; i++) {
            float t = i / (float) bottomSegments;
            float anchorX = x + width * (1f - t);
            float middleSag = (float) Math.sin(Math.PI * t) * 0.018f;
            float irregular = (hash01(seed * 211 + i * 59) - 0.5f)
                    * (style == CloudStyle.STORM ? 0.050f : 0.034f);
            float anchorY = y + height * (bottomBase + middleSag + irregular);
            float midX = (prevBottomX + anchorX) * 0.5f;
            float midY = (prevBottomY + anchorY) * 0.5f;
            path.quadTo(prevBottomX, prevBottomY, midX, midY);
            prevBottomX = anchorX;
            prevBottomY = anchorY;
        }
        path.quadTo(prevBottomX, prevBottomY, x, leftBottomY);
        path.close();
    }

    private int[] cloudTones(CloudStyle style) {
        if (!daylight) {
            if (style == CloudStyle.STORM || style == CloudStyle.STRATUS) {
                return new int[]{
                        Color.rgb(88, 100, 116),
                        Color.rgb(57, 69, 86),
                        Color.rgb(32, 42, 57)
                };
            }
            return new int[]{
                    Color.rgb(124, 138, 156),
                    Color.rgb(84, 99, 118),
                    Color.rgb(51, 63, 80)
            };
        }

        switch (style) {
            case STORM:
                return new int[]{
                        Color.rgb(125, 133, 145),
                        Color.rgb(79, 88, 102),
                        Color.rgb(45, 54, 67)
                };
            case STRATUS:
                return new int[]{
                        Color.rgb(211, 217, 222),
                        Color.rgb(174, 184, 193),
                        Color.rgb(129, 142, 154)
                };
            case RAIN_CUMULUS:
                return new int[]{
                        Color.rgb(221, 227, 231),
                        Color.rgb(174, 186, 196),
                        Color.rgb(115, 129, 143)
                };
            case THIN:
                return new int[]{
                        Color.rgb(252, 253, 254),
                        Color.rgb(236, 241, 245),
                        Color.rgb(209, 218, 226)
                };
            case CUMULUS:
            default:
                return new int[]{
                        Color.rgb(254, 254, 254),
                        Color.rgb(236, 241, 244),
                        Color.rgb(187, 198, 207)
                };
        }
    }

    private static double nearestMinutelyCloudCover(
            WeatherResponse weather,
            WeatherResponse.CurrentWeather current
    ) {
        WeatherResponse.Minutely15Weather minutely = weather.getMinutely15();
        if (minutely == null || minutely.getCloudCover() == null || minutely.getCloudCover().isEmpty()) {
            return -1d;
        }

        List<Double> covers = minutely.getCloudCover();
        List<String> times = minutely.getTime();
        if (times == null || times.isEmpty() || current == null || current.getTime() == null) {
            return firstUseful(covers);
        }

        try {
            LocalDateTime currentTime = LocalDateTime.parse(current.getTime());
            long best = Long.MAX_VALUE;
            Double bestValue = null;
            int count = Math.min(times.size(), covers.size());
            for (int i = 0; i < count; i++) {
                String time = times.get(i);
                Double cover = covers.get(i);
                if (time == null || cover == null) continue;
                LocalDateTime candidate = LocalDateTime.parse(time);
                long distance = Math.abs(Duration.between(currentTime, candidate).toMinutes());
                if (distance < best) {
                    best = distance;
                    bestValue = cover;
                }
            }
            return bestValue == null ? firstUseful(covers) : bestValue;
        } catch (DateTimeParseException ignored) {
            return firstUseful(covers);
        }
    }

    private static double firstUseful(List<Double> values) {
        for (Double value : values) {
            if (value != null) return value;
        }
        return -1d;
    }

    private static double value(Double value) {
        return value == null ? 0d : value;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(
                clampInt(alpha, 0, 255),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum CloudStyle {
        THIN,
        CUMULUS,
        RAIN_CUMULUS,
        STRATUS,
        STORM
    }
}

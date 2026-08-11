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
 * Artifact-free Hero cloud renderer.
 *
 * Unlike the earlier sprite implementation this renderer never scales a cloud
 * bitmap. Every cloud is a closed irregular Bezier mass with transparent space
 * outside the Path, therefore moving rectangular texture bounds cannot appear.
 * Current + nearest 15-minute cloud cover and the resolved WMO condition decide
 * how much cloud is visible.
 */
public final class HeroCloudRenderer {

    private static final long STATE_REFRESH_MILLIS = 5_000L;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cloudPath = new Path();
    private final Path detailPath = new Path();

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
        detailPaint.setStyle(Paint.Style.STROKE);
        detailPaint.setStrokeCap(Paint.Cap.ROUND);
        detailPaint.setStrokeJoin(Paint.Join.ROUND);
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

        int layerCount = cover >= 0.82f ? 4 : cover >= 0.58f ? 3 : cover >= 0.24f ? 2 : 1;
        float direction = (float) Math.toRadians(windDirectionDegrees + 180f);
        float flowX = (float) Math.sin(direction);
        float flowY = -(float) Math.cos(direction);

        for (int layer = 0; layer < layerCount; layer++) {
            float depth = layerCount == 1
                    ? 0.68f
                    : 0.26f + layer * (0.68f / Math.max(1, layerCount - 1));
            CloudStyle style = chooseStyle(cover, layer, layerCount);
            int count = cloudCount(style, cover, layer);
            float baseSpeed = (1.1f + windSpeedKmh * 0.105f) * (0.52f + depth * 0.74f);

            for (int i = 0; i < count; i++) {
                int seed = style.ordinal() * 100_003 + layer * 5_009 + i * 977;
                float scale = cloudScale(style, depth, seed);
                float cloudWidth = width * scale;
                float cloudHeight = cloudWidth * cloudAspect(style);
                float track = width + cloudWidth * 2.4f;
                float origin = hash01(seed * 17 + 3) * track - cloudWidth * 1.15f;
                float travel = seconds * baseSpeed * (0.78f + hash01(seed * 29 + 11) * 0.44f);
                float x = positiveMod(origin + flowX * travel, track) - cloudWidth * 1.15f;

                // Vertical wind response must remain bounded. The previous
                // epoch-time multiplication could push clouds off-screen after
                // long runtimes. A slow oscillation keeps atmospheric motion alive.
                float verticalWave = (float) Math.sin(
                        seconds * (0.018f + depth * 0.011f) + seed * 0.013f
                );
                float yDrift = verticalWave * height * (0.006f + depth * 0.010f)
                        + flowY * height * 0.004f * depth;
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
            return layer <= 1 ? CloudStyle.STRATUS : CloudStyle.STORM;
        }
        if (rainIntensity > 0.18f) {
            return layer == 0 ? CloudStyle.STRATUS : CloudStyle.RAIN_CUMULUS;
        }
        if (cover > 0.82f) {
            return layer == layerCount - 1 ? CloudStyle.STRATUS : CloudStyle.CUMULUS;
        }
        if (cover > 0.48f) {
            return layer == 0 ? CloudStyle.THIN : CloudStyle.CUMULUS;
        }
        return CloudStyle.CUMULUS;
    }

    private int cloudCount(CloudStyle style, float cover, int layer) {
        switch (style) {
            case STRATUS:
                return 3 + Math.round(cover * 2f) + layer;
            case STORM:
                return 3 + layer;
            case RAIN_CUMULUS:
                return 3 + Math.round(cover * 3f);
            case THIN:
                return 2 + Math.round(cover * 2f);
            case CUMULUS:
            default:
                return 2 + Math.round(cover * 3f) + Math.min(1, layer);
        }
    }

    private float cloudScale(CloudStyle style, float depth, int seed) {
        float jitter = 0.82f + hash01(seed * 37 + 13) * 0.36f;
        float base;
        switch (style) {
            case STRATUS:
                base = 0.58f;
                break;
            case STORM:
                base = 0.52f;
                break;
            case RAIN_CUMULUS:
                base = 0.43f;
                break;
            case THIN:
                base = 0.46f;
                break;
            case CUMULUS:
            default:
                base = 0.36f;
                break;
        }
        return base * (0.70f + depth * 0.56f) * jitter;
    }

    private float cloudAspect(CloudStyle style) {
        switch (style) {
            case STRATUS:
                return 0.30f;
            case STORM:
                return 0.52f;
            case RAIN_CUMULUS:
                return 0.48f;
            case THIN:
                return 0.22f;
            case CUMULUS:
            default:
                return 0.44f;
        }
    }

    private float cloudY(int height, CloudStyle style, float depth, int seed) {
        float jitter = (hash01(seed * 47 + 19) - 0.5f) * height * 0.14f;
        float base;
        switch (style) {
            case STRATUS:
                base = height * (0.13f + depth * 0.15f);
                break;
            case STORM:
                base = height * (0.08f + depth * 0.19f);
                break;
            case RAIN_CUMULUS:
                base = height * (0.11f + depth * 0.19f);
                break;
            case THIN:
                base = height * (0.10f + depth * 0.09f);
                break;
            case CUMULUS:
            default:
                base = height * (0.10f + depth * 0.18f);
                break;
        }
        return base + jitter;
    }

    private float cloudAlpha(CloudStyle style, float cover, float depth) {
        float base;
        switch (style) {
            case STRATUS:
                base = 0.52f;
                break;
            case STORM:
                base = 0.78f;
                break;
            case RAIN_CUMULUS:
                base = 0.66f;
                break;
            case THIN:
                base = 0.35f;
                break;
            case CUMULUS:
            default:
                base = 0.50f;
                break;
        }
        return clamp(base + cover * 0.24f + depth * 0.08f, 0.28f, 0.96f);
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
        int middle = withAlpha(tones[1], Math.round(alpha * 242f));
        int bottom = withAlpha(tones[2], Math.round(alpha * 255f));

        canvas.save();
        canvas.translate(0f, height * 0.055f);
        fillPaint.setShader(null);
        fillPaint.setColor(withAlpha(tones[2], Math.round(alpha * 82f)));
        canvas.drawPath(cloudPath, fillPaint);
        canvas.restore();

        fillPaint.setShader(new LinearGradient(
                0f,
                y,
                0f,
                y + height,
                new int[]{top, middle, bottom},
                new float[]{0f, 0.48f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawPath(cloudPath, fillPaint);
        fillPaint.setShader(null);

        detailPath.reset();
        float bandY = y + height * (style == CloudStyle.STORM ? 0.62f : 0.58f);
        detailPath.moveTo(x + width * 0.12f, bandY);
        detailPath.cubicTo(
                x + width * 0.32f,
                bandY + height * (hash01(seed * 71 + 3) - 0.5f) * 0.11f,
                x + width * 0.60f,
                bandY - height * 0.07f,
                x + width * 0.88f,
                bandY + height * 0.02f
        );
        detailPaint.setStrokeWidth(Math.max(1.2f, height * 0.055f));
        detailPaint.setColor(withAlpha(tones[2], Math.round(alpha * 44f)));
        canvas.drawPath(detailPath, detailPaint);

        if (daylight && style != CloudStyle.STORM && style != CloudStyle.STRATUS) {
            detailPath.reset();
            float highlightY = y + height * 0.28f;
            detailPath.moveTo(x + width * 0.18f, highlightY);
            detailPath.cubicTo(
                    x + width * 0.36f,
                    highlightY - height * 0.08f,
                    x + width * 0.56f,
                    highlightY - height * 0.05f,
                    x + width * 0.72f,
                    highlightY
            );
            detailPaint.setStrokeWidth(Math.max(1.0f, height * 0.035f));
            detailPaint.setColor(Color.argb(Math.round(alpha * 40f), 255, 255, 255));
            canvas.drawPath(detailPath, detailPaint);
        }
    }

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

        float topBaseline;
        float bottomBaseline;
        float peakMin;
        float peakRange;
        int lobes;

        switch (style) {
            case THIN:
                topBaseline = 0.50f;
                bottomBaseline = 0.72f;
                peakMin = 0.05f;
                peakRange = 0.08f;
                lobes = 7;
                break;
            case STRATUS:
                topBaseline = 0.44f;
                bottomBaseline = 0.76f;
                peakMin = 0.04f;
                peakRange = 0.10f;
                lobes = 8;
                break;
            case STORM:
                topBaseline = 0.46f;
                bottomBaseline = 0.88f;
                peakMin = 0.14f;
                peakRange = 0.18f;
                lobes = 9;
                break;
            case RAIN_CUMULUS:
                topBaseline = 0.50f;
                bottomBaseline = 0.84f;
                peakMin = 0.12f;
                peakRange = 0.18f;
                lobes = 8;
                break;
            case CUMULUS:
            default:
                topBaseline = 0.54f;
                bottomBaseline = 0.80f;
                peakMin = 0.12f;
                peakRange = 0.22f;
                lobes = 8;
                break;
        }

        float leftY = y + height * bottomBaseline;
        path.moveTo(x, leftY);

        float segment = width / lobes;
        float currentY = y + height * (topBaseline + 0.03f);
        path.cubicTo(
                x + segment * 0.20f,
                leftY - height * 0.10f,
                x + segment * 0.42f,
                currentY,
                x + segment * 0.58f,
                currentY
        );

        float currentX = x + segment * 0.58f;
        for (int i = 0; i < lobes; i++) {
            float endX = x + Math.min(width, segment * (i + 1.12f));
            float edgeWeight = (float) Math.sin(Math.PI * (i + 0.5f) / lobes);
            float peak = peakMin + peakRange * edgeWeight
                    * (0.65f + hash01(seed + i * 113) * 0.55f);
            float peakY = y + height * (topBaseline - peak);
            float endY = y + height * (
                    topBaseline
                            + (hash01(seed + i * 139 + 7) - 0.5f) * 0.12f
            );
            float dx = Math.max(segment * 0.45f, endX - currentX);
            path.cubicTo(
                    currentX + dx * 0.24f,
                    peakY,
                    currentX + dx * 0.72f,
                    peakY,
                    endX,
                    endY
            );
            currentX = endX;
            currentY = endY;
        }

        float rightX = x + width;
        float rightBottom = y + height * (bottomBaseline + 0.01f);
        path.cubicTo(
                rightX - width * 0.06f,
                currentY + height * 0.08f,
                rightX,
                rightBottom - height * 0.08f,
                rightX,
                rightBottom
        );

        int bottomSegments = 5;
        for (int i = bottomSegments - 1; i >= 0; i--) {
            float t = i / (float) bottomSegments;
            float px = x + width * t;
            float py = y + height * (
                    bottomBaseline
                            + (hash01(seed * 211 + i * 53) - 0.5f)
                            * (style == CloudStyle.STORM ? 0.12f : 0.075f)
            );
            float cx = (rightX + px) * 0.5f;
            float cy = y + height * (bottomBaseline + 0.035f);
            path.quadTo(cx, cy, px, py);
            rightX = px;
        }
        path.close();
    }

    private int[] cloudTones(CloudStyle style) {
        if (!daylight) {
            if (style == CloudStyle.STORM || style == CloudStyle.STRATUS) {
                return new int[]{
                        Color.rgb(84, 96, 112),
                        Color.rgb(55, 67, 84),
                        Color.rgb(31, 41, 56)
                };
            }
            return new int[]{
                    Color.rgb(119, 133, 151),
                    Color.rgb(81, 96, 115),
                    Color.rgb(49, 61, 78)
            };
        }

        switch (style) {
            case STORM:
                return new int[]{
                        Color.rgb(119, 127, 139),
                        Color.rgb(75, 84, 98),
                        Color.rgb(43, 52, 65)
                };
            case STRATUS:
                return new int[]{
                        Color.rgb(205, 211, 216),
                        Color.rgb(166, 176, 185),
                        Color.rgb(123, 136, 148)
                };
            case RAIN_CUMULUS:
                return new int[]{
                        Color.rgb(213, 220, 224),
                        Color.rgb(164, 176, 186),
                        Color.rgb(108, 122, 136)
                };
            case THIN:
                return new int[]{
                        Color.rgb(250, 252, 253),
                        Color.rgb(232, 238, 242),
                        Color.rgb(202, 212, 220)
                };
            case CUMULUS:
            default:
                return new int[]{
                        Color.rgb(252, 253, 253),
                        Color.rgb(231, 237, 240),
                        Color.rgb(181, 192, 201)
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

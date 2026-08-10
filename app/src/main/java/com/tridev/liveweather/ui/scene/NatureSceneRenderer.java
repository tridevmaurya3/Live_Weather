package com.tridev.liveweather.ui.scene;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.local.WallpaperPreferences;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.SkyRealityState;
import com.tridev.liveweather.domain.scene.DynamicRealityComposer;
import com.tridev.liveweather.domain.scene.SceneState;

/**
 * Shared procedural renderer for the app preview and Android live wallpaper.
 * It draws nature layers instead of substituting weather icons for animation.
 */
public final class NatureSceneRenderer {

    private static final long REALITY_REFRESH_MILLIS = 15_000L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private WeatherResponse weather;
    private double latitude = Double.NaN;
    private double longitude = Double.NaN;
    private SceneState scene;
    private long lastRealityUpdate;
    private float parallaxOffset = 0.5f;

    private WallpaperPreferences.Options options = new WallpaperPreferences.Options(
            true, true, true, true, true, true, true
    );

    public NatureSceneRenderer() {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setWeatherData(
            @Nullable WeatherResponse weather,
            double latitude,
            double longitude
    ) {
        this.weather = weather;
        this.latitude = latitude;
        this.longitude = longitude;
        lastRealityUpdate = 0L;
    }

    public void clearWeatherData() {
        weather = null;
        latitude = Double.NaN;
        longitude = Double.NaN;
        scene = null;
        lastRealityUpdate = 0L;
    }

    public void setOptions(@NonNull WallpaperPreferences.Options options) {
        this.options = options;
    }

    public void setParallaxOffset(float offset) {
        parallaxOffset = clamp(offset, 0f, 1f);
    }

    @Nullable
    public SkyRealityState getLastSkyRealityState() {
        return scene == null ? null : scene.getSky();
    }

    public void draw(
            @NonNull Canvas canvas,
            int width,
            int height,
            long nowMillis
    ) {
        if (width <= 0 || height <= 0) {
            return;
        }

        if (weather == null || Double.isNaN(latitude) || Double.isNaN(longitude)) {
            drawWaitingSky(canvas, width, height, nowMillis);
            return;
        }

        if (scene == null || nowMillis - lastRealityUpdate >= REALITY_REFRESH_MILLIS) {
            try {
                scene = DynamicRealityComposer.compose(
                        weather,
                        latitude,
                        longitude,
                        nowMillis
                );
                lastRealityUpdate = nowMillis;
            } catch (RuntimeException ignored) {
                scene = null;
            }
        }

        if (scene == null) {
            drawWaitingSky(canvas, width, height, nowMillis);
            return;
        }

        drawAtmosphere(canvas, width, height, scene);
        if (options.isStars()) {
            drawStars(canvas, width, height, scene, nowMillis);
        }
        drawSun(canvas, width, height, scene, nowMillis);
        drawMoon(canvas, width, height, scene, nowMillis);
        if (options.isClouds()) {
            drawCloudLayers(canvas, width, height, scene, nowMillis);
        }
        if (options.isRain()) {
            drawRainAndDrizzle(canvas, width, height, scene, nowMillis);
        }
        if (options.isSnow()) {
            drawSnow(canvas, width, height, scene, nowMillis);
        }
        if (options.isFog()) {
            drawFog(canvas, width, height, scene, nowMillis);
        }
        if (options.isLightning()) {
            drawLightning(canvas, width, height, scene, nowMillis);
        }
        drawGroundAtmosphere(canvas, width, height, scene);
    }

    private void drawWaitingSky(Canvas canvas, int width, int height, long nowMillis) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(nowMillis / 2400d);
        int top = mix(Color.rgb(8, 18, 43), Color.rgb(32, 61, 92), 0.16f * pulse);
        int bottom = Color.rgb(17, 32, 58);
        paint.setShader(new LinearGradient(0, 0, 0, height, top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
    }

    private void drawAtmosphere(Canvas canvas, int width, int height, SceneState state) {
        String stage = state.getSky().getSkyStage();
        int top;
        int middle;
        int bottom;

        if (stage.contains("Daylight")) {
            top = Color.rgb(42, 119, 190);
            middle = Color.rgb(96, 170, 222);
            bottom = Color.rgb(181, 213, 229);
        } else if (stage.contains("Golden")) {
            top = Color.rgb(50, 78, 143);
            middle = Color.rgb(161, 105, 118);
            bottom = Color.rgb(244, 158, 91);
        } else if (stage.contains("Civil")) {
            top = Color.rgb(37, 52, 109);
            middle = Color.rgb(94, 72, 132);
            bottom = Color.rgb(185, 102, 120);
        } else if (stage.contains("Nautical")) {
            top = Color.rgb(18, 33, 77);
            middle = Color.rgb(38, 48, 92);
            bottom = Color.rgb(70, 67, 110);
        } else if (stage.contains("Astronomical twilight")) {
            top = Color.rgb(9, 20, 52);
            middle = Color.rgb(22, 31, 65);
            bottom = Color.rgb(43, 45, 78);
        } else {
            top = Color.rgb(3, 10, 28);
            middle = Color.rgb(8, 18, 42);
            bottom = Color.rgb(16, 28, 55);
        }

        double weatherDarkening = state.getCloudCover() * 0.35d
                + state.getStormIntensity() * 0.35d
                + state.getFogIntensity() * 0.13d;
        float factor = (float) clamp(1d - weatherDarkening, 0.38d, 1d);
        top = scale(top, factor);
        middle = scale(middle, factor);
        bottom = scale(bottom, Math.min(1f, factor + 0.08f));

        paint.setShader(new LinearGradient(
                0,
                0,
                0,
                height,
                new int[]{top, middle, bottom},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
    }

    private void drawStars(
            Canvas canvas,
            int width,
            int height,
            SceneState state,
            long nowMillis
    ) {
        double visibility = state.getStarVisibility();
        if (visibility < 0.015d) {
            return;
        }

        int count = 150;
        float usableHeight = height * 0.72f;
        float parallax = (parallaxOffset - 0.5f) * width * 0.05f;
        for (int i = 0; i < count; i++) {
            float visibilityThreshold = hash01(i * 31 + 7);
            if (visibilityThreshold > visibility) {
                continue;
            }
            float x = wrap(hash01(i * 89 + 13) * width + parallax * (0.4f + hash01(i + 4)), width);
            float y = 8f + hash01(i * 113 + 29) * usableHeight;
            float twinkle = 0.55f + 0.45f * (float) Math.sin(
                    nowMillis / (650d + (i % 7) * 90d) + i * 1.73d
            );
            int alpha = (int) (255d * visibility * (0.35f + 0.65f * twinkle));
            int warm = i % 11 == 0 ? Color.rgb(255, 238, 209) : Color.rgb(235, 244, 255);
            paint.setColor(withAlpha(warm, clampInt(alpha, 8, 245)));
            float radius = dp(width, i % 17 == 0 ? 1.8f : i % 7 == 0 ? 1.25f : 0.72f);
            canvas.drawCircle(x, y, radius, paint);
            if (i % 23 == 0 && alpha > 100) {
                strokePaint.setColor(withAlpha(warm, alpha / 2));
                strokePaint.setStrokeWidth(dp(width, 0.55f));
                canvas.drawLine(x - radius * 2.2f, y, x + radius * 2.2f, y, strokePaint);
                canvas.drawLine(x, y - radius * 2.2f, x, y + radius * 2.2f, strokePaint);
            }
        }
    }

    private void drawSun(
            Canvas canvas,
            int width,
            int height,
            SceneState state,
            long nowMillis
    ) {
        if (state.getSunVisibility() < 0.02d || state.getSky().getSunAltitude() < -5d) {
            return;
        }

        float x = celestialX(width, state.getSky().getSunAzimuth());
        float y = celestialY(height, state.getSky().getSunAltitude());
        float radius = Math.max(20f, Math.min(width, height) * 0.048f);
        float visibility = (float) state.getSunVisibility();

        paint.setShader(new RadialGradient(
                x,
                y,
                radius * 3.2f,
                new int[]{
                        withAlpha(Color.rgb(255, 246, 180), (int) (150 * visibility)),
                        withAlpha(Color.rgb(255, 190, 77), (int) (80 * visibility)),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.32f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(x, y, radius * 3.2f, paint);
        paint.setShader(null);

        canvas.save();
        canvas.rotate((nowMillis / 220d) % 360f, x, y);
        strokePaint.setStrokeWidth(Math.max(1.3f, radius * 0.06f));
        strokePaint.setColor(withAlpha(Color.rgb(255, 226, 132), (int) (115 * visibility)));
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30d);
            float sx = x + (float) Math.cos(angle) * radius * 1.45f;
            float sy = y + (float) Math.sin(angle) * radius * 1.45f;
            float ex = x + (float) Math.cos(angle) * radius * 2.05f;
            float ey = y + (float) Math.sin(angle) * radius * 2.05f;
            canvas.drawLine(sx, sy, ex, ey, strokePaint);
        }
        canvas.restore();

        paint.setShader(new RadialGradient(
                x - radius * 0.22f,
                y - radius * 0.28f,
                radius * 1.15f,
                new int[]{Color.rgb(255, 255, 232), Color.rgb(255, 218, 93)},
                null,
                Shader.TileMode.CLAMP
        ));
        paint.setAlpha((int) (255 * visibility));
        canvas.drawCircle(x, y, radius, paint);
        paint.setAlpha(255);
        paint.setShader(null);
    }

    private void drawMoon(
            Canvas canvas,
            int width,
            int height,
            SceneState state,
            long nowMillis
    ) {
        if (state.getMoonVisibility() < 0.02d || state.getSky().getMoonAltitude() < -5d) {
            return;
        }

        float x = celestialX(width, state.getSky().getMoonAzimuth());
        float y = celestialY(height, state.getSky().getMoonAltitude());
        float radius = Math.max(16f, Math.min(width, height) * 0.041f);
        float visibility = (float) state.getMoonVisibility();
        double illumination = clamp(state.getSky().getMoonIlluminationPercent() / 100d, 0d, 1d);
        String phaseName = state.getSky().getMoonPhaseName();
        boolean waxing = phaseName.startsWith("Waxing") || phaseName.contains("First Quarter");

        paint.setShader(new RadialGradient(
                x,
                y,
                radius * 2.6f,
                new int[]{
                        withAlpha(Color.rgb(220, 232, 255), (int) (75 * visibility)),
                        Color.TRANSPARENT
                },
                null,
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(x, y, radius * 2.6f, paint);
        paint.setShader(null);

        paint.setColor(withAlpha(Color.rgb(229, 235, 241), (int) (245 * visibility)));
        canvas.drawCircle(x, y, radius, paint);

        paint.setColor(withAlpha(Color.rgb(157, 165, 176), (int) (60 * visibility)));
        canvas.drawCircle(x - radius * 0.25f, y - radius * 0.16f, radius * 0.14f, paint);
        canvas.drawCircle(x + radius * 0.31f, y + radius * 0.22f, radius * 0.10f, paint);
        canvas.drawCircle(x + radius * 0.10f, y - radius * 0.38f, radius * 0.07f, paint);

        if (illumination < 0.995d) {
            canvas.save();
            path.reset();
            path.addCircle(x, y, radius, Path.Direction.CW);
            canvas.clipPath(path);
            float phaseShift = (float) ((illumination * 2d - 1d) * radius);
            float shadowCenter = waxing
                    ? x - radius + phaseShift
                    : x + radius - phaseShift;
            paint.setColor(withAlpha(Color.rgb(5, 12, 28), (int) (238 * visibility)));
            canvas.drawCircle(shadowCenter, y, radius * 1.03f, paint);
            canvas.restore();
        }
    }

    private void drawCloudLayers(
            Canvas canvas,
            int width,
            int height,
            SceneState state,
            long nowMillis
    ) {
        double cover = state.getCloudCover();
        if (cover < 0.05d) {
            return;
        }

        int layers = cover > 0.78d ? 3 : cover > 0.35d ? 2 : 1;
        for (int layer = 0; layer < layers; layer++) {
            int count = (int) Math.ceil(2d + cover * (3 + layer));
            float depth = 0.55f + layer * 0.22f;
            float scale = 0.72f + layer * 0.30f;
            float alphaFactor = (float) clamp(0.32d + cover * 0.55d + state.getStormIntensity() * 0.18d, 0.25d, 0.95d);
            float motion = (float) ((nowMillis / 1000d)
                    * (2.8d + state.getWindSpeedKmh() * 0.18d)
                    * depth);
            double windRadians = Math.toRadians(state.getWindDirectionDegrees() + 180d);
            float dx = (float) Math.sin(windRadians) * motion;
            float dy = (float) -Math.cos(windRadians) * motion * 0.08f;
            float parallax = (parallaxOffset - 0.5f) * width * 0.09f * depth;

            for (int i = 0; i < count; i++) {
                float baseX = hash01(i * 41 + layer * 101 + 17) * width;
                float baseY = height * (0.10f + hash01(i * 67 + layer * 131) * 0.46f);
                float cloudWidth = width * (0.22f + hash01(i * 23 + layer * 47) * 0.22f) * scale;
                float cloudHeight = cloudWidth * (0.24f + hash01(i * 17 + 3) * 0.08f);
                float x = wrap(baseX + dx + parallax, width + cloudWidth) - cloudWidth * 0.5f;
                float y = baseY + dy;
                drawCloudCluster(canvas, x, y, cloudWidth, cloudHeight, alphaFactor, state);
            }
        }
    }

    private void drawCloudCluster(
            Canvas canvas,
            float x,
            float y,
            float width,
            float height,
            float alphaFactor,
            SceneState state
    ) {
        int base = state.getStormIntensity() > 0.2d
                ? Color.rgb(55, 63, 78)
                : Color.rgb(169, 180, 192);
        int highlight = state.getSceneLight() > 0.35d
                ? Color.rgb(218, 226, 232)
                : Color.rgb(124, 137, 153);
        int alpha = clampInt((int) (230 * alphaFactor), 40, 235);

        paint.setShader(new LinearGradient(
                x,
                y - height,
                x,
                y + height,
                withAlpha(highlight, alpha),
                withAlpha(base, alpha),
                Shader.TileMode.CLAMP
        ));
        RectF body = new RectF(x, y - height * 0.18f, x + width, y + height * 0.55f);
        canvas.drawOval(body, paint);
        canvas.drawCircle(x + width * 0.23f, y - height * 0.12f, height * 0.60f, paint);
        canvas.drawCircle(x + width * 0.48f, y - height * 0.36f, height * 0.82f, paint);
        canvas.drawCircle(x + width * 0.72f, y - height * 0.14f, height * 0.65f, paint);
        paint.setShader(null);
    }

    private void drawRainAndDrizzle(
            Canvas canvas,
            int width,
            int height,
            SceneState state,
            long nowMillis
    ) {
        double intensity = Math.max(state.getRainIntensity(), state.getDrizzleIntensity());
        if (intensity <= 0.01d) {
            return;
        }

        boolean drizzle = state.getDrizzleIntensity() > state.getRainIntensity();
        int count = (int) (drizzle ? 65 + intensity * 80 : 85 + intensity * 175);
        float speed = (float) (drizzle ? 180d + intensity * 170d : 410d + intensity * 520d);
        float windShift = (float) (state.getWindStrength() * width * 0.23d);
        double windRadians = Math.toRadians(state.getWindDirectionDegrees() + 180d);
        float windX = (float) Math.sin(windRadians) * windShift;

        strokePaint.setStrokeWidth(drizzle ? Math.max(1f, width / 900f) : Math.max(1.25f, width / 620f));
        strokePaint.setColor(withAlpha(
                Color.rgb(199, 224, 244),
                (int) (drizzle ? 95 + intensity * 70 : 120 + intensity * 95)
        ));

        float t = nowMillis / 1000f;
        for (int i = 0; i < count; i++) {
            float seedX = hash01(i * 79 + 23);
            float seedY = hash01(i * 131 + 11);
            float cycle = height + 80f;
            float y = positiveMod(seedY * cycle + t * speed * (0.72f + hash01(i * 19) * 0.55f), cycle) - 40f;
            float x = positiveMod(seedX * width + windX * (y / Math.max(1f, height)) + i * 2.3f, width + 30f) - 15f;
            float length = drizzle ? 5f + (float) intensity * 8f : 15f + (float) intensity * 24f;
            float slant = windX == 0f ? 0f : Math.signum(windX) * Math.min(length * 0.8f, Math.abs(windX) * 0.08f);
            canvas.drawLine(x, y, x + slant, y + length, strokePaint);
        }
    }

    private void drawSnow(
            Canvas canvas,
            int width,
            int height,
            SceneState state,
            long nowMillis
    ) {
        double intensity = state.getSnowIntensity();
        if (intensity <= 0.01d) {
            return;
        }

        int count = (int) (50 + intensity * 125);
        float t = nowMillis / 1000f;
        for (int i = 0; i < count; i++) {
            float seedX = hash01(i * 103 + 17);
            float seedY = hash01(i * 61 + 9);
            float fallSpeed = 28f + hash01(i * 31) * 62f + (float) intensity * 40f;
            float y = positiveMod(seedY * height + t * fallSpeed, height + 30f) - 15f;
            float drift = (float) Math.sin(t * (0.5f + hash01(i) * 0.8f) + i) * (8f + 20f * (float) state.getWindStrength());
            float x = positiveMod(seedX * width + drift + (float) state.getWindStrength() * t * 10f, width + 20f) - 10f;
            float radius = 1.2f + hash01(i * 47) * 2.8f;
            paint.setColor(withAlpha(Color.WHITE, 125 + (int) (intensity * 115)));
            canvas.drawCircle(x, y, radius, paint);
        }
    }

    private void drawFog(
            Canvas canvas,
            int width,
            int height,
            SceneState state,
            long nowMillis
    ) {
        double intensity = state.getFogIntensity();
        if (intensity <= 0.035d) {
            return;
        }

        float t = nowMillis / 1000f;
        int bands = 5;
        for (int i = 0; i < bands; i++) {
            float bandHeight = height * (0.13f + i * 0.018f);
            float y = height * (0.20f + i * 0.14f);
            float drift = positiveMod(t * (5f + i * 1.8f) + i * width * 0.17f, width * 1.4f) - width * 0.2f;
            int alpha = (int) (35 + intensity * (38 + i * 7));
            paint.setShader(new LinearGradient(
                    drift - width * 0.35f,
                    y,
                    drift + width * 0.75f,
                    y,
                    new int[]{Color.TRANSPARENT, withAlpha(Color.rgb(210, 220, 225), alpha), Color.TRANSPARENT},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawOval(new RectF(-width * 0.2f, y - bandHeight / 2f, width * 1.2f, y + bandHeight / 2f), paint);
            paint.setShader(null);
        }
    }

    private void drawLightning(
            Canvas canvas,
            int width,
            int height,
            SceneState state,
            long nowMillis
    ) {
        double intensity = state.getStormIntensity();
        if (intensity < 0.12d) {
            return;
        }

        long cycle = nowMillis / 5200L;
        long phase = nowMillis % 5200L;
        float chance = hash01((int) (cycle * 37L + 19L));
        if (chance > 0.18f + intensity * 0.62f || phase > 220L) {
            return;
        }

        float flash = 1f - phase / 220f;
        paint.setColor(withAlpha(Color.rgb(224, 232, 255), (int) (110 * flash * intensity)));
        canvas.drawRect(0, 0, width, height, paint);

        float startX = width * (0.22f + hash01((int) cycle * 13 + 7) * 0.56f);
        float y = height * 0.12f;
        path.reset();
        path.moveTo(startX, y);
        float x = startX;
        for (int segment = 0; segment < 7; segment++) {
            x += (hash01((int) cycle * 29 + segment * 17) - 0.5f) * width * 0.09f;
            y += height * (0.065f + hash01(segment * 23 + (int) cycle) * 0.045f);
            path.lineTo(x, y);
        }
        strokePaint.setStrokeWidth(Math.max(2f, width / 260f));
        strokePaint.setColor(withAlpha(Color.rgb(240, 244, 255), (int) (235 * flash)));
        canvas.drawPath(path, strokePaint);
        strokePaint.setStrokeWidth(Math.max(5f, width / 110f));
        strokePaint.setColor(withAlpha(Color.rgb(159, 181, 255), (int) (65 * flash)));
        canvas.drawPath(path, strokePaint);
    }

    private void drawGroundAtmosphere(Canvas canvas, int width, int height, SceneState state) {
        float horizon = height * 0.86f;
        int base = state.getSceneLight() > 0.45d
                ? Color.rgb(44, 70, 70)
                : Color.rgb(8, 19, 28);
        int alpha = (int) (115 + state.getCloudCover() * 55d + state.getFogIntensity() * 35d);
        paint.setShader(new LinearGradient(
                0,
                horizon,
                0,
                height,
                Color.TRANSPARENT,
                withAlpha(base, clampInt(alpha, 90, 210)),
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, horizon - height * 0.05f, width, height, paint);
        paint.setShader(null);
    }

    private float celestialX(int width, double azimuth) {
        double normalized = ((azimuth % 360d) + 360d) % 360d;
        float x = (float) (normalized / 360d * width);
        float parallax = (parallaxOffset - 0.5f) * width * 0.035f;
        return wrap(x + parallax, width);
    }

    private float celestialY(int height, double altitude) {
        float horizon = height * 0.86f;
        double normalized = clamp(altitude, -7d, 90d);
        return (float) (horizon - ((normalized + 7d) / 97d) * height * 0.77d);
    }

    private float dp(int width, float value) {
        return value * Math.max(0.85f, width / 1080f);
    }

    private static int scale(int color, float factor) {
        return Color.rgb(
                clampInt(Math.round(Color.red(color) * factor), 0, 255),
                clampInt(Math.round(Color.green(color) * factor), 0, 255),
                clampInt(Math.round(Color.blue(color) * factor), 0, 255)
        );
    }

    private static int mix(int a, int b, float amount) {
        float t = clamp(amount, 0f, 1f);
        return Color.rgb(
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t)
        );
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
        int x = seed;
        x = (x << 13) ^ x;
        int n = (x * (x * x * 15731 + 789221) + 1376312589) & 0x7fffffff;
        return n / 2147483647f;
    }

    private static float wrap(float value, float max) {
        if (max <= 0f) {
            return 0f;
        }
        float result = value % max;
        return result < 0f ? result + max : result;
    }

    private static float positiveMod(float value, float modulus) {
        if (modulus <= 0f) {
            return 0f;
        }
        float result = value % modulus;
        return result < 0f ? result + modulus : result;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

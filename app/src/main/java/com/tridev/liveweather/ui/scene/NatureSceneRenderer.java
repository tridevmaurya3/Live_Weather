package com.tridev.liveweather.ui.scene;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
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
 * Cinematic procedural renderer shared by the app and Android Live Wallpaper.
 *
 * The renderer is intentionally icon-free for the main atmosphere. Weather and
 * astronomy drive cached fractal textures, volumetric shading, depth particles,
 * real lunar illumination and time-of-day lighting.
 */
public final class NatureSceneRenderer {

    private static final long REALITY_REFRESH_MILLIS = 5_000L;
    private static final long WEATHER_TRANSITION_MILLIS = 7_500L;
    private static final long SIDEREAL_DAY_MILLIS = 86_164_090L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final ProceduralTextureFactory textures = new ProceduralTextureFactory();

    private WeatherResponse weather;
    private double latitude = Double.NaN;
    private double longitude = Double.NaN;
    private SceneState scene;
    private SceneState previousScene;
    private long lastRealityUpdate;
    private long transitionStartedAt;
    private float parallaxOffset = 0.5f;

    private WallpaperPreferences.Options options = new WallpaperPreferences.Options(
            true, true, true, true, true, true, true
    );

    public NatureSceneRenderer() {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        bitmapPaint.setFilterBitmap(true);
        bitmapPaint.setDither(true);
    }

    public void setWeatherData(
            @Nullable WeatherResponse weather,
            double latitude,
            double longitude
    ) {
        if (scene != null) {
            previousScene = scene;
            transitionStartedAt = System.currentTimeMillis();
        }
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
        previousScene = null;
        lastRealityUpdate = 0L;
        transitionStartedAt = 0L;
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

        refreshReality(nowMillis);
        if (scene == null) {
            drawWaitingSky(canvas, width, height, nowMillis);
            return;
        }

        SceneState renderState = transitionScene(nowMillis);

        drawAtmosphere(canvas, width, height, renderState, nowMillis);
        if (options.isStars()) {
            drawStars(canvas, width, height, renderState, nowMillis);
        }
        drawSun(canvas, width, height, renderState, nowMillis);
        drawMoon(canvas, width, height, renderState, nowMillis);

        if (options.isClouds()) {
            drawCloudLayers(canvas, width, height, renderState, nowMillis);
        }

        drawPrecipitationVeil(canvas, width, height, renderState);
        if (options.isRain()) {
            drawRainAndDrizzle(canvas, width, height, renderState, nowMillis);
        }
        if (options.isSnow()) {
            drawSnow(canvas, width, height, renderState, nowMillis);
        }
        if (options.isFog()) {
            drawFog(canvas, width, height, renderState, nowMillis);
        }
        if (options.isLightning()) {
            drawLightning(canvas, width, height, renderState, nowMillis);
        }
        drawGroundAtmosphere(canvas, width, height, renderState);
    }

    private void refreshReality(long nowMillis) {
        if (scene != null && nowMillis - lastRealityUpdate < REALITY_REFRESH_MILLIS) {
            return;
        }
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

    @NonNull
    private SceneState transitionScene(long nowMillis) {
        if (previousScene == null || transitionStartedAt <= 0L) {
            return scene;
        }
        double progress = clamp(
                (nowMillis - transitionStartedAt) / (double) WEATHER_TRANSITION_MILLIS,
                0.0d,
                1.0d
        );
        double eased = progress * progress * (3.0d - 2.0d * progress);
        if (progress >= 1.0d) {
            previousScene = null;
            return scene;
        }
        return blend(previousScene, scene, eased);
    }

    @NonNull
    private SceneState blend(SceneState from, SceneState to, double t) {
        return new SceneState(
                to.getSky(),
                to.getCondition(),
                lerp(from.getCloudCover(), to.getCloudCover(), t),
                lerp(from.getRainIntensity(), to.getRainIntensity(), t),
                lerp(from.getDrizzleIntensity(), to.getDrizzleIntensity(), t),
                lerp(from.getSnowIntensity(), to.getSnowIntensity(), t),
                lerp(from.getFogIntensity(), to.getFogIntensity(), t),
                lerp(from.getStormIntensity(), to.getStormIntensity(), t),
                lerp(from.getAirHazeIntensity(), to.getAirHazeIntensity(), t),
                lerp(from.getWindSpeedKmh(), to.getWindSpeedKmh(), t),
                circularLerp(from.getWindDirectionDegrees(), to.getWindDirectionDegrees(), t),
                lerp(from.getWindStrength(), to.getWindStrength(), t),
                lerp(from.getVisibilityFactor(), to.getVisibilityFactor(), t),
                lerp(from.getSunVisibility(), to.getSunVisibility(), t),
                lerp(from.getMoonVisibility(), to.getMoonVisibility(), t),
                lerp(from.getStarVisibility(), to.getStarVisibility(), t),
                lerp(from.getSceneLight(), to.getSceneLight(), t)
        );
    }

    private void drawWaitingSky(Canvas canvas, int width, int height, long nowMillis) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(nowMillis / 4200d);
        int top = mix(Color.rgb(7, 17, 38), Color.rgb(29, 58, 86), 0.10f * pulse);
        int bottom = Color.rgb(18, 33, 55);
        paint.setShader(new LinearGradient(0f, 0f, 0f, height, top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setShader(null);
    }

    /** RU-4: cinematic sky scattering and weather-aware lighting. */
    private void drawAtmosphere(
            Canvas canvas,
            int width,
            int height,
            SceneState state,
            long nowMillis
    ) {
        String stage = state.getSky().getSkyStage();
        int top;
        int middle;
        int horizon;

        if (stage.contains("Daylight")) {
            top = Color.rgb(45, 115, 183);
            middle = Color.rgb(91, 157, 207);
            horizon = Color.rgb(174, 204, 219);
        } else if (stage.contains("Golden")) {
            top = Color.rgb(47, 73, 127);
            middle = Color.rgb(150, 100, 105);
            horizon = Color.rgb(238, 151, 82);
        } else if (stage.contains("Civil")) {
            top = Color.rgb(35, 48, 100);
            middle = Color.rgb(91, 66, 121);
            horizon = Color.rgb(175, 92, 110);
        } else if (stage.contains("Nautical")) {
            top = Color.rgb(17, 31, 70);
            middle = Color.rgb(36, 46, 82);
            horizon = Color.rgb(69, 64, 100);
        } else if (stage.contains("Astronomical twilight")) {
            top = Color.rgb(8, 18, 45);
            middle = Color.rgb(19, 29, 59);
            horizon = Color.rgb(42, 43, 72);
        } else {
            top = Color.rgb(3, 9, 24);
            middle = Color.rgb(8, 17, 37);
            horizon = Color.rgb(15, 27, 50);
        }

        double weatherDarkening = state.getCloudCover() * 0.33d
                + state.getStormIntensity() * 0.42d
                + state.getFogIntensity() * 0.10d
                + state.getRainIntensity() * 0.08d;
        float lightFactor = (float) clamp(1.0d - weatherDarkening, 0.34d, 1.0d);
        top = scale(top, lightFactor);
        middle = scale(middle, Math.min(1f, lightFactor + 0.04f));
        horizon = scale(horizon, Math.min(1f, lightFactor + 0.12f));

        paint.setShader(new LinearGradient(
                0f,
                0f,
                0f,
                height,
                new int[]{top, middle, horizon},
                new float[]{0f, 0.56f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setShader(null);

        drawSolarHorizonScattering(canvas, width, height, state);
        drawNightLuminance(canvas, width, height, state);

        if (state.getStormIntensity() > 0.04d) {
            int stormAlpha = clampInt((int) Math.round(state.getStormIntensity() * 86d), 0, 96);
            paint.setShader(new LinearGradient(
                    0f, 0f, 0f, height,
                    Color.argb(stormAlpha, 20, 27, 39),
                    Color.argb(stormAlpha / 2, 38, 43, 49),
                    Shader.TileMode.CLAMP
            ));
            canvas.drawRect(0f, 0f, width, height, paint);
            paint.setShader(null);
        }
    }

    private void drawSolarHorizonScattering(Canvas canvas, int width, int height, SceneState state) {
        double altitude = state.getSky().getSunAltitude();
        if (altitude < -11d || altitude > 24d) {
            return;
        }
        float x = celestialX(width, state.getSky().getSunAzimuth());
        float y = height * 0.78f;
        double horizonStrength = 1.0d - clamp(Math.abs(altitude - 1.0d) / 24.0d, 0.0d, 1.0d);
        horizonStrength *= clamp(1.0d - state.getCloudCover() * 0.50d, 0.15d, 1.0d);
        int warmAlpha = clampInt((int) Math.round(120d * horizonStrength), 0, 120);
        float radius = Math.max(width, height) * 0.78f;
        paint.setShader(new RadialGradient(
                x,
                y,
                radius,
                new int[]{
                        Color.argb(warmAlpha, 255, 159, 84),
                        Color.argb(warmAlpha / 2, 224, 120, 104),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.35f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(x, y, radius, paint);
        paint.setShader(null);
    }

    private void drawNightLuminance(Canvas canvas, int width, int height, SceneState state) {
        if (!state.getSky().getSkyStage().contains("night") || state.getMoonVisibility() <= 0.04d) {
            return;
        }
        float x = celestialX(width, state.getSky().getMoonAzimuth());
        float y = celestialY(height, state.getSky().getMoonAltitude());
        float radius = Math.max(width, height) * 0.48f;
        int alpha = clampInt((int) Math.round(
                state.getMoonVisibility()
                        * state.getSky().getMoonIlluminationPercent() / 100d
                        * 32d
        ), 0, 34);
        paint.setShader(new RadialGradient(
                x, y, radius,
                Color.argb(alpha, 125, 158, 198),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(x, y, radius, paint);
        paint.setShader(null);
    }

    /** RU-3: deep, subtly rotating star field with brightness/color classes. */
    private void drawStars(
            Canvas canvas,
            int width,
            int height,
            SceneState state,
            long nowMillis
    ) {
        double visibility = state.getStarVisibility();
        if (visibility < 0.012d) {
            return;
        }

        int count = 270;
        float usableHeight = height * 0.77f;
        float siderealShift = (float) ((nowMillis % SIDEREAL_DAY_MILLIS)
                / (double) SIDEREAL_DAY_MILLIS * width);
        float parallax = (parallaxOffset - 0.5f) * width * 0.018f;

        for (int i = 0; i < count; i++) {
            float threshold = hash01(i * 31 + 7);
            if (threshold > visibility * 1.08d) {
                continue;
            }

            float depth = 0.45f + hash01(i * 43 + 5) * 0.55f;
            float baseX = hash01(i * 89 + 13) * width;
            float x = wrap(baseX + siderealShift * (0.78f + depth * 0.22f) + parallax * depth, width);
            float y = 5f + hash01(i * 113 + 29) * usableHeight;
            float magnitude = hash01(i * 71 + 17);
            float twinkle = 0.82f + 0.18f * (float) Math.sin(
                    nowMillis / (1100d + (i % 9) * 170d) + i * 1.29d
            );
            float radius = dp(width,
                    magnitude > 0.985f ? 1.65f
                            : magnitude > 0.94f ? 1.05f
                            : 0.56f + magnitude * 0.28f);
            int starColor;
            float temperature = hash01(i * 157 + 3);
            if (temperature > 0.86f) {
                starColor = Color.rgb(255, 231, 201);
            } else if (temperature < 0.16f) {
                starColor = Color.rgb(207, 226, 255);
            } else {
                starColor = Color.rgb(238, 243, 248);
            }
            int alpha = clampInt((int) Math.round(
                    235d * visibility * twinkle * (0.48d + magnitude * 0.52d)
            ), 5, 238);

            if (radius > dp(width, 1.15f)) {
                paint.setShader(new RadialGradient(
                        x, y, radius * 4.4f,
                        Color.argb(alpha / 3, Color.red(starColor), Color.green(starColor), Color.blue(starColor)),
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                ));
                canvas.drawCircle(x, y, radius * 4.4f, paint);
                paint.setShader(null);
            }
            paint.setColor(Color.argb(alpha, Color.red(starColor), Color.green(starColor), Color.blue(starColor)));
            canvas.drawCircle(x, y, radius, paint);
        }
    }

    /** RU-3: luminous Sun without cartoon rotating spokes. */
    private void drawSun(
            Canvas canvas,
            int width,
            int height,
            SceneState state,
            long nowMillis
    ) {
        if (state.getSunVisibility() < 0.015d || state.getSky().getSunAltitude() < -4.5d) {
            return;
        }

        float x = celestialX(width, state.getSky().getSunAzimuth());
        float y = celestialY(height, state.getSky().getSunAltitude());
        float radius = Math.max(12f, Math.min(width, height) * 0.034f);
        float visibility = (float) state.getSunVisibility();
        float shimmer = 0.985f + 0.015f * (float) Math.sin(nowMillis / 1650d);

        paint.setShader(new RadialGradient(
                x,
                y,
                radius * 7.5f,
                new int[]{
                        Color.argb((int) (92f * visibility), 255, 238, 178),
                        Color.argb((int) (42f * visibility), 255, 189, 98),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.32f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(x, y, radius * 7.5f, paint);
        paint.setShader(null);

        paint.setShader(new RadialGradient(
                x,
                y,
                radius * 2.3f,
                new int[]{
                        Color.argb((int) (190f * visibility), 255, 249, 204),
                        Color.argb((int) (102f * visibility), 255, 204, 96),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.44f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(x, y, radius * 2.3f, paint);
        paint.setShader(null);

        float discRadius = radius * shimmer;
        paint.setShader(new RadialGradient(
                x - discRadius * 0.18f,
                y - discRadius * 0.20f,
                discRadius * 1.18f,
                new int[]{Color.rgb(255, 255, 236), Color.rgb(255, 229, 133), Color.rgb(249, 183, 74)},
                new float[]{0f, 0.64f, 1f},
                Shader.TileMode.CLAMP
        ));
        paint.setAlpha(clampInt((int) (255f * visibility), 0, 255));
        canvas.drawCircle(x, y, discRadius, paint);
        paint.setAlpha(255);
        paint.setShader(null);
    }

    /** RU-3: crater texture + physically shaped lunar phase illumination. */
    private void drawMoon(
            Canvas canvas,
            int width,
            int height,
            SceneState state,
            long nowMillis
    ) {
        if (state.getMoonVisibility() < 0.012d || state.getSky().getMoonAltitude() < -4.5d) {
            return;
        }

        float x = celestialX(width, state.getSky().getMoonAzimuth());
        float y = celestialY(height, state.getSky().getMoonAltitude());
        float radius = Math.max(13f, Math.min(width, height) * 0.032f);
        float visibility = (float) state.getMoonVisibility();
        double illumination = clamp(state.getSky().getMoonIlluminationPercent() / 100d, 0d, 1d);

        float haloRadius = radius * (3.2f + (float) illumination * 2.0f);
        int haloAlpha = clampInt((int) Math.round(66d * visibility * (0.25d + illumination * 0.75d)), 0, 72);
        paint.setShader(new RadialGradient(
                x, y, haloRadius,
                Color.argb(haloAlpha, 193, 215, 242),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(x, y, haloRadius, paint);
        paint.setShader(null);

        Bitmap moon = textures.moonPhase(state.getSky().getMoonPhaseAngleDegrees());
        rect.set(x - radius, y - radius, x + radius, y + radius);
        bitmapPaint.setAlpha(clampInt((int) (255f * visibility), 0, 255));
        bitmapPaint.setColorFilter(null);
        canvas.drawBitmap(moon, null, rect, bitmapPaint);
        bitmapPaint.setAlpha(255);
    }

    /** RU-1: cached fractal cloud textures, real depth, shading and wind motion. */
    private void drawCloudLayers(
            Canvas canvas,
            int width,
            int height,
            SceneState state,
            long nowMillis
    ) {
        double cover = state.getCloudCover();
        if (cover < 0.025d) {
            return;
        }

        int layerCount = cover > 0.76d ? 3 : cover > 0.28d ? 2 : 1;
        for (int layer = 0; layer < layerCount; layer++) {
            float depth = layerCount == 1 ? 0.62f : 0.38f + layer * 0.31f;
            ProceduralTextureFactory.CloudKind kind = cloudKind(state, cover, layer, layerCount);
            int baseCount;
            if (kind == ProceduralTextureFactory.CloudKind.STRATUS) {
                baseCount = cover > 0.82d ? 6 + layer * 2 : 4 + layer;
            } else if (kind == ProceduralTextureFactory.CloudKind.CIRRUS) {
                baseCount = 3 + (int) Math.round(cover * 3d);
            } else {
                baseCount = 2 + layer + (int) Math.ceil(cover * (3d + layer));
            }

            double travelDirection = Math.toRadians(state.getWindDirectionDegrees() + 180d);
            float speed = (float) (1.3d + state.getWindSpeedKmh() * 0.095d) * (0.52f + depth * 0.68f);
            float seconds = nowMillis / 1000f;
            float travel = seconds * speed;
            float dx = (float) Math.sin(travelDirection) * travel;
            float dy = (float) -Math.cos(travelDirection) * travel * 0.10f;
            float parallax = (parallaxOffset - 0.5f) * width * 0.11f * depth;

            for (int i = 0; i < baseCount; i++) {
                int seed = layer * 1013 + i * 193 + kind.ordinal() * 3571;
                Bitmap sprite = textures.cloud(kind, seed);
                float scale = cloudScale(kind, depth, seed);
                float cloudWidth = width * scale;
                float cloudHeight = cloudWidth * cloudAspect(kind);
                float track = width + cloudWidth * 1.8f;
                float originX = hash01(seed * 17 + 7) * track - cloudWidth * 0.9f;
                float x = wrap(originX + dx + parallax, track) - cloudWidth * 0.9f;
                float y = cloudBaseY(height, kind, depth, seed) + dy;
                float alpha = (float) clamp(
                        (0.32d + cover * 0.58d + state.getStormIntensity() * 0.14d)
                                * (0.68d + depth * 0.34d),
                        0.16d,
                        0.98d
                );
                drawCloudSprite(canvas, sprite, x, y, cloudWidth, cloudHeight, alpha, depth, state);
            }
        }
    }

    private ProceduralTextureFactory.CloudKind cloudKind(
            SceneState state,
            double cover,
            int layer,
            int layerCount
    ) {
        if (state.getStormIntensity() > 0.16d
                || state.getRainIntensity() > 0.45d
                || state.getCondition().getWeatherCode() != null
                && state.getCondition().getWeatherCode() >= 95) {
            return layer == 0
                    ? ProceduralTextureFactory.CloudKind.STRATUS
                    : ProceduralTextureFactory.CloudKind.STORM;
        }
        if (cover > 0.80d) {
            return layer == layerCount - 1
                    ? ProceduralTextureFactory.CloudKind.STRATUS
                    : layer == 0
                    ? ProceduralTextureFactory.CloudKind.CIRRUS
                    : ProceduralTextureFactory.CloudKind.STRATUS;
        }
        if (cover > 0.42d) {
            return layer == 0
                    ? ProceduralTextureFactory.CloudKind.CIRRUS
                    : ProceduralTextureFactory.CloudKind.CUMULUS;
        }
        return layer == 0 && cover < 0.20d
                ? ProceduralTextureFactory.CloudKind.CIRRUS
                : ProceduralTextureFactory.CloudKind.CUMULUS;
    }

    private float cloudScale(
            ProceduralTextureFactory.CloudKind kind,
            float depth,
            int seed
    ) {
        float random = 0.82f + hash01(seed * 43 + 11) * 0.36f;
        float base;
        switch (kind) {
            case CIRRUS:
                base = 0.34f;
                break;
            case STRATUS:
                base = 0.48f;
                break;
            case STORM:
                base = 0.40f;
                break;
            case CUMULUS:
            default:
                base = 0.30f;
                break;
        }
        return base * (0.72f + depth * 0.68f) * random;
    }

    private float cloudAspect(ProceduralTextureFactory.CloudKind kind) {
        switch (kind) {
            case CIRRUS:
                return 0.27f;
            case STRATUS:
                return 0.33f;
            case STORM:
                return 0.50f;
            case CUMULUS:
            default:
                return 0.46f;
        }
    }

    private float cloudBaseY(
            int height,
            ProceduralTextureFactory.CloudKind kind,
            float depth,
            int seed
    ) {
        float jitter = (hash01(seed * 61 + 29) - 0.5f) * height * 0.16f;
        float base;
        switch (kind) {
            case CIRRUS:
                base = height * (0.10f + depth * 0.10f);
                break;
            case STRATUS:
                base = height * (0.16f + depth * 0.19f);
                break;
            case STORM:
                base = height * (0.12f + depth * 0.24f);
                break;
            case CUMULUS:
            default:
                base = height * (0.12f + depth * 0.20f);
                break;
        }
        return base + jitter;
    }

    private void drawCloudSprite(
            Canvas canvas,
            Bitmap sprite,
            float x,
            float y,
            float width,
            float height,
            float alpha,
            float depth,
            SceneState state
    ) {
        boolean dark = state.getSky().getSkyStage().contains("night")
                || state.getSky().getSkyStage().contains("Astronomical");
        double storm = state.getStormIntensity();
        double light = state.getSceneLight();

        int shadow;
        int base;
        int highlight;
        if (storm > 0.18d) {
            shadow = Color.rgb(36, 43, 55);
            base = Color.rgb(69, 76, 88);
            highlight = Color.rgb(104, 110, 120);
        } else if (dark) {
            shadow = Color.rgb(28, 37, 52);
            base = Color.rgb(62, 74, 90);
            highlight = Color.rgb(99, 113, 132);
        } else {
            int daylight = clampInt((int) Math.round(196d + light * 48d), 190, 244);
            shadow = Color.rgb(117, 128, 141);
            base = Color.rgb(daylight - 22, daylight - 16, daylight - 8);
            highlight = Color.rgb(daylight, daylight + 2 > 255 ? 255 : daylight + 2, 250);
        }

        float yShadow = y + height * (0.055f + depth * 0.012f);
        rect.set(x, yShadow, x + width, yShadow + height);
        drawTintedBitmap(canvas, sprite, rect, shadow, alpha * 0.48f);

        rect.set(x, y, x + width, y + height);
        drawTintedBitmap(canvas, sprite, rect, base, alpha * 0.90f);

        float highlightOffset = height * 0.022f;
        rect.set(x, y - highlightOffset, x + width, y + height - highlightOffset);
        drawTintedBitmap(canvas, sprite, rect, highlight,
                alpha * (float) clamp(0.14d + light * 0.24d, 0.10d, 0.34d));
    }

    private void drawTintedBitmap(
            Canvas canvas,
            Bitmap bitmap,
            RectF destination,
            int tint,
            float alpha
    ) {
        bitmapPaint.setAlpha(clampInt((int) Math.round(clamp(alpha, 0f, 1f) * 255f), 0, 255));
        bitmapPaint.setColorFilter(new PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, null, destination, bitmapPaint);
        bitmapPaint.setColorFilter(null);
        bitmapPaint.setAlpha(255);
    }

    /** RU-2: atmospheric rain veil before individual droplets. */
    private void drawPrecipitationVeil(Canvas canvas, int width, int height, SceneState state) {
        double intensity = Math.max(state.getRainIntensity(), state.getSnowIntensity());
        intensity = Math.max(intensity, state.getStormIntensity() * 0.82d);
        if (intensity < 0.08d) {
            return;
        }
        int topAlpha = clampInt((int) Math.round(intensity * 26d), 0, 34);
        int horizonAlpha = clampInt((int) Math.round(intensity * 66d), 0, 78);
        paint.setShader(new LinearGradient(
                0f, 0f, 0f, height,
                new int[]{
                        Color.argb(topAlpha, 55, 67, 78),
                        Color.argb(horizonAlpha, 94, 103, 108),
                        Color.argb(horizonAlpha / 2, 52, 62, 70)
                },
                new float[]{0f, 0.72f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setShader(null);
    }

    /** RU-2: depth-layered drizzle and rain with real wind vector response. */
    private void drawRainAndDrizzle(
            Canvas canvas,
            int width,
            int height,
            SceneState state,
            long nowMillis
    ) {
        double rain = state.getRainIntensity();
        double drizzle = state.getDrizzleIntensity();
        if (rain <= 0.012d && drizzle <= 0.012d) {
            return;
        }

        if (drizzle > rain) {
            drawRainLayer(canvas, width, height, drizzle, nowMillis, state, 0, true);
            drawRainLayer(canvas, width, height, drizzle, nowMillis, state, 1, true);
        } else {
            drawRainLayer(canvas, width, height, rain, nowMillis, state, 0, false);
            drawRainLayer(canvas, width, height, rain, nowMillis, state, 1, false);
            drawRainLayer(canvas, width, height, rain, nowMillis, state, 2, false);
            if (rain > 0.58d) {
                drawRainSplashes(canvas, width, height, rain, nowMillis, state);
            }
        }
    }

    private void drawRainLayer(
            Canvas canvas,
            int width,
            int height,
            double intensity,
            long nowMillis,
            SceneState state,
            int layer,
            boolean drizzle
    ) {
        float depth = drizzle
                ? (layer == 0 ? 0.46f : 0.82f)
                : (layer == 0 ? 0.34f : layer == 1 ? 0.68f : 1.0f);
        int count = (int) Math.round(
                (drizzle ? 34d : 42d)
                        + intensity * (drizzle ? 78d : 98d)
                        * (0.72d + depth * 0.58d)
        );
        float speed = (float) ((drizzle ? 135d : 330d)
                + intensity * (drizzle ? 190d : 500d))
                * (0.60f + depth * 0.64f);
        float length = (drizzle ? 4.5f : 11f)
                + (float) intensity * (drizzle ? 8f : 31f)
                * (0.58f + depth * 0.72f);
        float thickness = Math.max(0.7f,
                dp(width, drizzle ? 0.42f + depth * 0.35f : 0.56f + depth * 0.74f));
        int alpha = clampInt((int) Math.round(
                (drizzle ? 72d : 86d)
                        + intensity * (drizzle ? 58d : 98d)
                        * depth
        ), 25, 205);

        double direction = Math.toRadians(state.getWindDirectionDegrees() + 180d);
        float windRatio = (float) clamp(state.getWindSpeedKmh() / 70d, 0d, 1d);
        float slantX = (float) Math.sin(direction) * length * (0.12f + windRatio * 1.05f);
        float verticalFactor = 0.92f + (float) Math.abs(Math.cos(direction)) * 0.08f;

        strokePaint.setStrokeWidth(thickness);
        strokePaint.setColor(Color.argb(alpha, 188, 215, 235));
        float seconds = nowMillis / 1000f;
        float cycle = height + length * 3f;

        for (int i = 0; i < count; i++) {
            int seed = layer * 5003 + i * 97 + (drizzle ? 71 : 19);
            float x0 = hash01(seed * 13 + 5) * (width + 80f) - 40f;
            float y0 = hash01(seed * 29 + 17) * cycle;
            float localSpeed = speed * (0.74f + hash01(seed * 43 + 3) * 0.42f);
            float y = positiveMod(y0 + seconds * localSpeed, cycle) - length * 1.5f;
            float windTravel = seconds * state.getWindSpeedKmh() * 0.24f * (0.55f + depth * 0.55f);
            float x = positiveMod(
                    x0 + (float) Math.sin(direction) * windTravel,
                    width + 80f
            ) - 40f;
            canvas.drawLine(x, y, x + slantX, y + length * verticalFactor, strokePaint);
        }
    }

    private void drawRainSplashes(
            Canvas canvas,
            int width,
            int height,
            double intensity,
            long nowMillis,
            SceneState state
    ) {
        int count = 10 + (int) Math.round(intensity * 18d);
        float horizon = height * 0.92f;
        long tick = nowMillis / 95L;
        strokePaint.setStrokeWidth(Math.max(0.8f, dp(width, 0.7f)));
        strokePaint.setColor(Color.argb(
                clampInt((int) Math.round(70d + intensity * 65d), 40, 145),
                190, 214, 228
        ));
        for (int i = 0; i < count; i++) {
            int seed = (int) (tick + i * 131L);
            float x = hash01(seed * 17 + 3) * width;
            float y = horizon + hash01(seed * 31 + 9) * height * 0.065f;
            float size = 3f + hash01(seed * 47 + 11) * 9f;
            rect.set(x - size, y - size * 0.22f, x + size, y + size * 0.22f);
            canvas.drawOval(rect, strokePaint);
        }
    }

    /** RU-2: multi-depth fluttering snow. */
    private void drawSnow(
            Canvas canvas,
            int width,
            int height,
            SceneState state,
            long nowMillis
    ) {
        double intensity = state.getSnowIntensity();
        if (intensity <= 0.012d) {
            return;
        }

        int count = 58 + (int) Math.round(intensity * 145d);
        float seconds = nowMillis / 1000f;
        double windDirection = Math.toRadians(state.getWindDirectionDegrees() + 180d);
        float wind = (float) Math.sin(windDirection)
                * (float) clamp(state.getWindSpeedKmh() / 45d, 0d, 1.4d);

        for (int i = 0; i < count; i++) {
            float depth = 0.30f + hash01(i * 53 + 17) * 0.70f;
            float seedX = hash01(i * 103 + 17);
            float seedY = hash01(i * 61 + 9);
            float fallSpeed = (18f + hash01(i * 31) * 42f + (float) intensity * 34f)
                    * (0.58f + depth * 0.68f);
            float y = positiveMod(seedY * height + seconds * fallSpeed, height + 36f) - 18f;
            float flutter = (float) Math.sin(seconds * (0.38f + hash01(i) * 0.74f) + i * 0.83f)
                    * (5f + 16f * depth);
            float x = positiveMod(
                    seedX * width + flutter + wind * seconds * (7f + depth * 15f),
                    width + 28f
            ) - 14f;
            float radius = dp(width, 0.72f + depth * 2.15f);
            int alpha = clampInt((int) Math.round(105d + intensity * 105d * depth), 70, 225);
            paint.setColor(Color.argb(alpha, 239, 245, 250));
            canvas.drawCircle(x, y, radius, paint);

            if (depth > 0.82f && radius > 1.8f) {
                strokePaint.setColor(Color.argb(alpha / 2, 245, 250, 255));
                strokePaint.setStrokeWidth(Math.max(0.55f, radius * 0.22f));
                canvas.drawLine(x - radius * 1.7f, y, x + radius * 1.7f, y, strokePaint);
                canvas.drawLine(x, y - radius * 1.7f, x, y + radius * 1.7f, strokePaint);
            }
        }
    }

    /** RU-2: noise-textured mist bands instead of flat translucent ovals. */
    private void drawFog(
            Canvas canvas,
            int width,
            int height,
            SceneState state,
            long nowMillis
    ) {
        double intensity = state.getFogIntensity();
        if (intensity <= 0.025d) {
            return;
        }

        float seconds = nowMillis / 1000f;
        double direction = Math.toRadians(state.getWindDirectionDegrees() + 180d);
        float windX = (float) Math.sin(direction)
                * (3.0f + (float) state.getWindSpeedKmh() * 0.055f);
        int bands = 5;

        for (int layer = 0; layer < bands; layer++) {
            Bitmap fog = textures.fog(layer);
            float depth = 0.36f + layer * 0.16f;
            float bandWidth = width * (1.05f + depth * 0.52f);
            float bandHeight = height * (0.15f + layer * 0.018f);
            float y = height * (0.28f + layer * 0.125f);
            float track = width + bandWidth;
            float x = wrap(
                    layer * width * 0.23f + seconds * windX * (0.55f + depth),
                    track
            ) - bandWidth * 0.62f;
            int alpha = clampInt((int) Math.round(
                    intensity * (78d + layer * 10d)
            ), 12, 142);
            bitmapPaint.setAlpha(alpha);
            bitmapPaint.setColorFilter(null);
            rect.set(x, y - bandHeight / 2f, x + bandWidth, y + bandHeight / 2f);
            canvas.drawBitmap(fog, null, rect, bitmapPaint);
            bitmapPaint.setAlpha(255);
        }
    }

    /** RU-2: irregular storm flash and branched lightning. */
    private void drawLightning(
            Canvas canvas,
            int width,
            int height,
            SceneState state,
            long nowMillis
    ) {
        double intensity = state.getStormIntensity();
        if (intensity < 0.11d) {
            return;
        }

        long window = 6_700L;
        long cycle = nowMillis / window;
        long phase = nowMillis % window;
        float eventChance = hash01((int) (cycle * 47L + 19L));
        double threshold = 0.16d + intensity * 0.58d;
        if (eventChance > threshold || phase > 310L) {
            return;
        }

        float doublePulse;
        if (phase < 80L) {
            doublePulse = 1f - phase / 80f;
        } else if (phase > 145L && phase < 235L) {
            doublePulse = 0.58f * (1f - (phase - 145L) / 90f);
        } else {
            doublePulse = 0.08f;
        }

        int flashAlpha = clampInt((int) Math.round(118d * doublePulse * intensity), 0, 132);
        paint.setColor(Color.argb(flashAlpha, 211, 222, 245));
        canvas.drawRect(0f, 0f, width, height, paint);

        if (phase > 190L) {
            return;
        }

        float startX = width * (0.18f + hash01((int) cycle * 17 + 7) * 0.64f);
        float startY = height * (0.08f + hash01((int) cycle * 13 + 5) * 0.08f);
        drawLightningBranch(canvas, width, height, startX, startY,
                (int) (cycle * 101L + 31L), 0, doublePulse);
    }

    private void drawLightningBranch(
            Canvas canvas,
            int width,
            int height,
            float startX,
            float startY,
            int seed,
            int depth,
            float pulse
    ) {
        int segments = depth == 0 ? 8 : 4;
        float x = startX;
        float y = startY;
        path.reset();
        path.moveTo(x, y);

        for (int i = 0; i < segments; i++) {
            float horizontal = (hash01(seed + i * 37) - 0.5f)
                    * width * (depth == 0 ? 0.075f : 0.045f);
            x += horizontal;
            y += height * (depth == 0 ? 0.066f : 0.046f)
                    * (0.78f + hash01(seed + i * 53) * 0.50f);
            path.lineTo(x, y);

            if (depth == 0 && i > 1 && i < 6 && hash01(seed + i * 79) > 0.62f) {
                drawLightningBranch(
                        canvas,
                        width,
                        height,
                        x,
                        y,
                        seed + i * 997,
                        1,
                        pulse * 0.72f
                );
            }
        }

        strokePaint.setStrokeWidth(Math.max(5f, width / (depth == 0 ? 118f : 185f)));
        strokePaint.setColor(Color.argb(
                clampInt((int) (72f * pulse), 0, 85),
                157, 183, 255
        ));
        canvas.drawPath(path, strokePaint);

        strokePaint.setStrokeWidth(Math.max(1.4f, width / (depth == 0 ? 410f : 540f)));
        strokePaint.setColor(Color.argb(
                clampInt((int) (245f * pulse), 0, 250),
                241, 247, 255
        ));
        canvas.drawPath(path, strokePaint);
    }

    private void drawGroundAtmosphere(Canvas canvas, int width, int height, SceneState state) {
        float horizon = height * 0.84f;
        int base = state.getSceneLight() > 0.45d
                ? Color.rgb(43, 64, 62)
                : Color.rgb(7, 17, 25);
        int alpha = clampInt((int) Math.round(
                102d
                        + state.getCloudCover() * 48d
                        + state.getFogIntensity() * 52d
                        + state.getRainIntensity() * 24d
        ), 86, 212);
        paint.setShader(new LinearGradient(
                0f,
                horizon,
                0f,
                height,
                Color.TRANSPARENT,
                Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base)),
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, horizon - height * 0.06f, width, height, paint);
        paint.setShader(null);
    }

    private float celestialX(int width, double azimuth) {
        double normalized = ((azimuth % 360d) + 360d) % 360d;
        float x = (float) (normalized / 360d * width);
        float parallax = (parallaxOffset - 0.5f) * width * 0.026f;
        return wrap(x + parallax, width);
    }

    private float celestialY(int height, double altitude) {
        float horizon = height * 0.86f;
        double normalized = clamp(altitude, -7d, 90d);
        return (float) (horizon - ((normalized + 7d) / 97d) * height * 0.77d);
    }

    private float dp(int width, float value) {
        return value * Math.max(0.78f, width / 1080f);
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

    private static double circularLerp(double from, double to, double amount) {
        double delta = ((to - from + 540d) % 360d) - 180d;
        double value = from + delta * amount;
        value %= 360d;
        return value < 0d ? value + 360d : value;
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
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

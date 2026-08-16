package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.local.WallpaperPreferences;

import java.util.Locale;

/**
 * Read-only diagnostics for the shared OpenGL Hero/Live Wallpaper pipeline.
 *
 * Diagnostics remain off the frame hot path. Renderer faults are recorded only
 * when a real exception occurs, so graceful recovery does not add per-frame
 * logging or GPU synchronization work.
 */
public final class HeroGlDiagnostics {

    private static final String TAG = "LiveWeatherGL";
    private static final String QUALITY_MODE = "FULL_SHARED_GL";

    @Nullable
    private volatile GlSceneSnapshot snapshot;

    @NonNull
    private volatile WallpaperPreferences.Options options = new WallpaperPreferences.Options(
            true, true, true, true, true, true, true
    );

    @NonNull private volatile String glVendor = "unknown";
    @NonNull private volatile String glRenderer = "unknown";
    @NonNull private volatile String glVersion = "unknown";
    @NonNull private volatile String rendererFaults = "none";

    @Nullable
    private String lastLoggedEvidence;

    private volatile int surfaceWidth = 1;
    private volatile int surfaceHeight = 1;

    public void onSurfaceCreated() {
        glVendor = safeGlString(GLES20.GL_VENDOR);
        glRenderer = safeGlString(GLES20.GL_RENDERER);
        glVersion = safeGlString(GLES20.GL_VERSION);
        logCurrent("surface-created");
    }

    public void onSurfaceChanged(int width, int height) {
        surfaceWidth = Math.max(1, width);
        surfaceHeight = Math.max(1, height);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot value) {
        snapshot = value;
        String evidence = value == null ? "NO_WEATHER_SNAPSHOT" : resolveEvidence(value);
        if (!evidence.equals(lastLoggedEvidence)) {
            lastLoggedEvidence = evidence;
            logCurrent("weather-evidence-changed");
        }
    }

    public void setOptions(@NonNull WallpaperPreferences.Options value) {
        options = value;
    }

    public synchronized void resetRendererFaults() {
        rendererFaults = "none";
    }

    public synchronized void recordRendererFault(
            @NonNull String renderer,
            @NonNull String stage,
            @NonNull Throwable error
    ) {
        String key = renderer + "@" + stage;
        if ("none".equals(rendererFaults)) {
            rendererFaults = key;
        } else if (!rendererFaults.contains(key)) {
            rendererFaults = rendererFaults + "," + key;
        }
        Log.e(TAG, "renderer-isolated " + key + " " + error.getClass().getSimpleName()
                + ": " + safeMessage(error), error);
    }

    @NonNull
    public String getRendererFaultSummary() {
        return rendererFaults;
    }

    @NonNull
    public Snapshot capture() {
        GlSceneSnapshot state = snapshot;
        WallpaperPreferences.Options currentOptions = options;

        if (state == null) {
            return new Snapshot(
                    QUALITY_MODE,
                    "NO_WEATHER_SNAPSHOT",
                    "none",
                    glVendor,
                    glRenderer,
                    glVersion,
                    surfaceWidth,
                    surfaceHeight,
                    0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 0f, 0f,
                    false, false, false, false, false, false, false
            );
        }

        return new Snapshot(
                QUALITY_MODE,
                resolveEvidence(state),
                resolveActiveEffects(state, currentOptions),
                glVendor,
                glRenderer,
                glVersion,
                surfaceWidth,
                surfaceHeight,
                state.cloudCover,
                state.cloudDensity,
                state.cloudFarLayer,
                state.cloudMidLayer,
                state.cloudNearLayer,
                state.rainIntensity,
                state.drizzleIntensity,
                state.snowIntensity,
                state.fogIntensity,
                state.stormIntensity,
                state.airHazeIntensity,
                state.windStrength,
                state.windDirectionRadians,
                state.visibilityFactor,
                state.sceneLight,
                state.starVisibility,
                currentOptions.isClouds(),
                currentOptions.isRain(),
                currentOptions.isLightning(),
                currentOptions.isSnow(),
                currentOptions.isFog(),
                currentOptions.isStars(),
                true
        );
    }

    @NonNull
    public String buildReport() {
        return capture().toMultilineString() + "\nrendererFaults=" + rendererFaults;
    }

    private void logCurrent(@NonNull String reason) {
        if (!Log.isLoggable(TAG, Log.INFO)) return;
        Log.i(TAG, reason + "\n" + buildReport());
    }

    @NonNull
    private static String resolveEvidence(@NonNull GlSceneSnapshot state) {
        if (state.stormIntensity > 0.02f) return "STORM_CURRENT";
        if (state.snowIntensity > 0.004f) return "SNOW_CURRENT";
        if (state.rainIntensity > 0.004f) return "RAIN_CURRENT";
        if (state.drizzleIntensity > 0.004f) return "DRIZZLE_CURRENT";
        if (state.fogIntensity > 0.10f) return "FOG_OR_LOW_VISIBILITY_CURRENT";
        if (state.cloudCover >= 0.78f) return "OVERCAST_CURRENT";
        if (state.cloudCover >= 0.52f) return "BROKEN_CLOUD_CURRENT";
        if (state.cloudCover >= 0.25f) return "SCATTERED_CLOUD_CURRENT";
        if (state.cloudCover >= 0.06f) return "LIGHT_CLOUD_CURRENT";
        return "CLEAR_CURRENT";
    }

    @NonNull
    private static String resolveActiveEffects(
            @NonNull GlSceneSnapshot state,
            @NonNull WallpaperPreferences.Options options
    ) {
        StringBuilder value = new StringBuilder("sky");
        if (options.isStars() && state.starVisibility > 0.002f) value.append(",stars");
        if (options.isClouds() && state.cloudCover > 0.015f) value.append(",clouds");
        if (options.isFog() && (state.fogIntensity > 0.01f || state.airHazeIntensity > 0.01f)) {
            value.append(",atmosphere");
        }
        if (options.isLightning() && state.stormIntensity > 0.02f) value.append(",lightning");
        if (options.isRain() && (state.rainIntensity > 0.003f || state.drizzleIntensity > 0.003f)) {
            value.append(",rain");
        }
        if (options.isSnow() && state.snowIntensity > 0.003f) value.append(",snow");
        return value.toString();
    }

    @NonNull
    private static String safeGlString(int name) {
        String value = GLES20.glGetString(name);
        return value == null || value.trim().isEmpty() ? "unknown" : value.trim();
    }

    @NonNull
    private static String safeMessage(@NonNull Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? "no-message" : message.trim();
    }

    /** Immutable diagnostic snapshot safe to read outside the render loop. */
    public static final class Snapshot {
        @NonNull public final String qualityMode;
        @NonNull public final String resolvedEvidence;
        @NonNull public final String activeEffects;
        @NonNull public final String glVendor;
        @NonNull public final String glRenderer;
        @NonNull public final String glVersion;
        public final int surfaceWidth;
        public final int surfaceHeight;
        public final float cloudCover;
        public final float cloudDensity;
        public final float cloudFarLayer;
        public final float cloudMidLayer;
        public final float cloudNearLayer;
        public final float rainIntensity;
        public final float drizzleIntensity;
        public final float snowIntensity;
        public final float fogIntensity;
        public final float stormIntensity;
        public final float hazeIntensity;
        public final float windStrength;
        public final float windDirectionRadians;
        public final float visibilityFactor;
        public final float sceneLight;
        public final float starVisibility;
        public final boolean cloudsEnabled;
        public final boolean rainEnabled;
        public final boolean lightningEnabled;
        public final boolean snowEnabled;
        public final boolean fogEnabled;
        public final boolean starsEnabled;
        public final boolean currentEvidenceOnly;

        private Snapshot(
                @NonNull String qualityMode,
                @NonNull String resolvedEvidence,
                @NonNull String activeEffects,
                @NonNull String glVendor,
                @NonNull String glRenderer,
                @NonNull String glVersion,
                int surfaceWidth,
                int surfaceHeight,
                float cloudCover,
                float cloudDensity,
                float cloudFarLayer,
                float cloudMidLayer,
                float cloudNearLayer,
                float rainIntensity,
                float drizzleIntensity,
                float snowIntensity,
                float fogIntensity,
                float stormIntensity,
                float hazeIntensity,
                float windStrength,
                float windDirectionRadians,
                float visibilityFactor,
                float sceneLight,
                float starVisibility,
                boolean cloudsEnabled,
                boolean rainEnabled,
                boolean lightningEnabled,
                boolean snowEnabled,
                boolean fogEnabled,
                boolean starsEnabled,
                boolean currentEvidenceOnly
        ) {
            this.qualityMode = qualityMode;
            this.resolvedEvidence = resolvedEvidence;
            this.activeEffects = activeEffects;
            this.glVendor = glVendor;
            this.glRenderer = glRenderer;
            this.glVersion = glVersion;
            this.surfaceWidth = surfaceWidth;
            this.surfaceHeight = surfaceHeight;
            this.cloudCover = cloudCover;
            this.cloudDensity = cloudDensity;
            this.cloudFarLayer = cloudFarLayer;
            this.cloudMidLayer = cloudMidLayer;
            this.cloudNearLayer = cloudNearLayer;
            this.rainIntensity = rainIntensity;
            this.drizzleIntensity = drizzleIntensity;
            this.snowIntensity = snowIntensity;
            this.fogIntensity = fogIntensity;
            this.stormIntensity = stormIntensity;
            this.hazeIntensity = hazeIntensity;
            this.windStrength = windStrength;
            this.windDirectionRadians = windDirectionRadians;
            this.visibilityFactor = visibilityFactor;
            this.sceneLight = sceneLight;
            this.starVisibility = starVisibility;
            this.cloudsEnabled = cloudsEnabled;
            this.rainEnabled = rainEnabled;
            this.lightningEnabled = lightningEnabled;
            this.snowEnabled = snowEnabled;
            this.fogEnabled = fogEnabled;
            this.starsEnabled = starsEnabled;
            this.currentEvidenceOnly = currentEvidenceOnly;
        }

        @NonNull
        public String toMultilineString() {
            return String.format(
                    Locale.US,
                    "quality=%s evidence=%s effects=%s\n"
                            + "surface=%dx%d gpu=%s / %s gl=%s\n"
                            + "cloud=%.2f density=%.2f layers=%.2f/%.2f/%.2f\n"
                            + "rain=%.2f drizzle=%.2f snow=%.2f fog=%.2f storm=%.2f haze=%.2f\n"
                            + "wind=%.2f dirRad=%.2f visibility=%.2f light=%.2f stars=%.2f\n"
                            + "options clouds=%s rain=%s lightning=%s snow=%s fog=%s stars=%s currentEvidenceOnly=%s",
                    qualityMode,
                    resolvedEvidence,
                    activeEffects,
                    surfaceWidth,
                    surfaceHeight,
                    glVendor,
                    glRenderer,
                    glVersion,
                    cloudCover,
                    cloudDensity,
                    cloudFarLayer,
                    cloudMidLayer,
                    cloudNearLayer,
                    rainIntensity,
                    drizzleIntensity,
                    snowIntensity,
                    fogIntensity,
                    stormIntensity,
                    hazeIntensity,
                    windStrength,
                    windDirectionRadians,
                    visibilityFactor,
                    sceneLight,
                    starVisibility,
                    cloudsEnabled,
                    rainEnabled,
                    lightningEnabled,
                    snowEnabled,
                    fogEnabled,
                    starsEnabled,
                    currentEvidenceOnly
            );
        }
    }
}

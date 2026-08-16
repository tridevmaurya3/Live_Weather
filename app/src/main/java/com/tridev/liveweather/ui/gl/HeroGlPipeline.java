package com.tridev.liveweather.ui.gl;

import android.opengl.GLES20;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.local.WallpaperPreferences;

/**
 * Shared GPU composition pipeline for the app Hero and Android Live Wallpaper.
 *
 * Phase 20A.14 keeps resolved weather truth immediate while renderer-facing
 * values move through an allocation-free temporal transition controller. New
 * weather therefore changes the sky naturally instead of looking like a page or
 * scene refresh.
 */
public final class HeroGlPipeline {

    private final HeroGlSkyCelestialRenderer sceneRenderer = new HeroGlSkyCelestialRenderer();
    private final HeroGlFixedStarRenderer starRenderer = new HeroGlFixedStarRenderer();
    private final HeroGlTextureCloudRenderer cloudRenderer = new HeroGlTextureCloudRenderer();
    private final HeroGlAnalyticWorldRenderer worldRenderer = new HeroGlAnalyticWorldRenderer();
    private final HeroGlAtmosphereOverlayRenderer atmosphereRenderer = new HeroGlAtmosphereOverlayRenderer();
    private final HeroGlPortableStormRenderer stormRenderer = new HeroGlPortableStormRenderer();
    private final HeroGlDepthRainRenderer rainRenderer = new HeroGlDepthRainRenderer();
    private final HeroGlSnowRenderer snowRenderer = new HeroGlSnowRenderer();
    private final HeroGlDiagnostics diagnostics = new HeroGlDiagnostics();
    private final GlSceneTransitionController transitionController = new GlSceneTransitionController();

    /** Latest resolved current-weather truth. Diagnostics use this immediately. */
    @Nullable
    private GlSceneSnapshot fullSnapshot;

    /** Reusable renderer-specific views; allocated once, then mutated in place. */
    @Nullable private GlSceneSnapshot sceneView;
    @Nullable private GlSceneSnapshot starView;
    @Nullable private GlSceneSnapshot cloudView;
    @Nullable private GlSceneSnapshot worldView;
    @Nullable private GlSceneSnapshot atmosphereView;
    @Nullable private GlSceneSnapshot stormView;
    @Nullable private GlSceneSnapshot rainView;
    @Nullable private GlSceneSnapshot snowView;

    private volatile float performanceDetailScale = 1f;

    private boolean sceneHealthy = true;
    private boolean starsHealthy = true;
    private boolean cloudsHealthy = true;
    private boolean worldHealthy = true;
    private boolean atmosphereHealthy = true;
    private boolean stormHealthy = true;
    private boolean rainHealthy = true;
    private boolean snowHealthy = true;

    @NonNull
    private WallpaperPreferences.Options options = new WallpaperPreferences.Options(
            true, true, true, true, true, true, true
    );

    public void onSurfaceCreated() {
        resetRendererHealth();
        diagnostics.resetRendererFaults();
        diagnostics.onSurfaceCreated();

        try {
            sceneRenderer.onSurfaceCreated();
        } catch (RuntimeException error) {
            sceneHealthy = false;
            diagnostics.recordRendererFault("sky", "surface-create", error);
        }
        try {
            starRenderer.onSurfaceCreated();
        } catch (RuntimeException error) {
            starsHealthy = false;
            diagnostics.recordRendererFault("stars", "surface-create", error);
        }
        try {
            cloudRenderer.onSurfaceCreated();
        } catch (RuntimeException error) {
            cloudsHealthy = false;
            diagnostics.recordRendererFault("clouds", "surface-create", error);
        }
        try {
            worldRenderer.onSurfaceCreated();
        } catch (RuntimeException error) {
            worldHealthy = false;
            diagnostics.recordRendererFault("world", "surface-create", error);
        }
        try {
            atmosphereRenderer.onSurfaceCreated();
        } catch (RuntimeException error) {
            atmosphereHealthy = false;
            diagnostics.recordRendererFault("atmosphere", "surface-create", error);
        }
        try {
            stormRenderer.onSurfaceCreated();
        } catch (RuntimeException error) {
            stormHealthy = false;
            diagnostics.recordRendererFault("storm", "surface-create", error);
        }
        try {
            rainRenderer.onSurfaceCreated();
        } catch (RuntimeException error) {
            rainHealthy = false;
            diagnostics.recordRendererFault("rain", "surface-create", error);
        }
        try {
            snowRenderer.onSurfaceCreated();
        } catch (RuntimeException error) {
            snowHealthy = false;
            diagnostics.recordRendererFault("snow", "surface-create", error);
        }

        applyPerformanceDetail();
        GlSceneSnapshot visual = transitionController.current();
        if (visual != null) {
            applyVisualSnapshot(visual);
        } else {
            clearRendererSnapshots();
        }
    }

    public void onSurfaceChanged(int width, int height) {
        diagnostics.onSurfaceChanged(width, height);

        if (sceneHealthy) {
            try {
                sceneRenderer.onSurfaceChanged(width, height);
            } catch (RuntimeException error) {
                sceneHealthy = false;
                diagnostics.recordRendererFault("sky", "surface-change", error);
            }
        }
        if (starsHealthy) {
            try {
                starRenderer.onSurfaceChanged(width, height);
            } catch (RuntimeException error) {
                starsHealthy = false;
                diagnostics.recordRendererFault("stars", "surface-change", error);
            }
        }
        if (cloudsHealthy) {
            try {
                cloudRenderer.onSurfaceChanged(width, height);
            } catch (RuntimeException error) {
                cloudsHealthy = false;
                diagnostics.recordRendererFault("clouds", "surface-change", error);
            }
        }
        if (worldHealthy) {
            try {
                worldRenderer.onSurfaceChanged(width, height);
            } catch (RuntimeException error) {
                worldHealthy = false;
                diagnostics.recordRendererFault("world", "surface-change", error);
            }
        }
        if (atmosphereHealthy) {
            try {
                atmosphereRenderer.onSurfaceChanged(width, height);
            } catch (RuntimeException error) {
                atmosphereHealthy = false;
                diagnostics.recordRendererFault("atmosphere", "surface-change", error);
            }
        }
        if (stormHealthy) {
            try {
                stormRenderer.onSurfaceChanged(width, height);
            } catch (RuntimeException error) {
                stormHealthy = false;
                diagnostics.recordRendererFault("storm", "surface-change", error);
            }
        }
        if (rainHealthy) {
            try {
                rainRenderer.onSurfaceChanged(width, height);
            } catch (RuntimeException error) {
                rainHealthy = false;
                diagnostics.recordRendererFault("rain", "surface-change", error);
            }
        }
        if (snowHealthy) {
            try {
                snowRenderer.onSurfaceChanged(width, height);
            } catch (RuntimeException error) {
                snowHealthy = false;
                diagnostics.recordRendererFault("snow", "surface-change", error);
            }
        }
    }

    public void setSnapshot(@Nullable GlSceneSnapshot snapshot) {
        fullSnapshot = snapshot;

        // Diagnostics reflect current resolved truth immediately, before visual easing.
        diagnostics.setSnapshot(snapshot);
        transitionController.setTarget(snapshot);

        GlSceneSnapshot visual = transitionController.current();
        if (visual == null) {
            clearRendererSnapshots();
        } else {
            applyVisualSnapshot(visual);
        }
    }

    public void setOptions(@NonNull WallpaperPreferences.Options options) {
        this.options = options;
        diagnostics.setOptions(options);

        GlSceneSnapshot visual = transitionController.current();
        if (visual == null) {
            clearRendererSnapshots();
        } else {
            applyVisualSnapshot(visual);
        }
    }

    /** Changes only secondary visual sampling cost. Weather state/intensity is untouched. */
    public void setPerformanceDetailScale(float detailScale) {
        performanceDetailScale = Math.max(0.5f, Math.min(1f, detailScale));
        applyPerformanceDetail();
    }

    @NonNull
    public HeroGlDiagnostics.Snapshot captureDiagnostics() {
        return diagnostics.capture();
    }

    @NonNull
    public String buildDiagnosticsReport() {
        return diagnostics.buildReport();
    }

    @NonNull
    public String getRendererFaultSummary() {
        return diagnostics.getRendererFaultSummary();
    }

    public void drawFrame() {
        // No allocations here: the controller and renderer views are reused.
        if (transitionController.advance()) {
            GlSceneSnapshot visual = transitionController.current();
            if (visual != null) applyVisualSnapshot(visual);
        }

        if (sceneHealthy) {
            try {
                sceneRenderer.drawFrame();
            } catch (RuntimeException error) {
                sceneHealthy = false;
                diagnostics.recordRendererFault("sky", "draw", error);
                drawFallbackSky();
            }
        } else {
            drawFallbackSky();
        }

        if (starsHealthy) {
            try {
                starRenderer.drawFrame();
            } catch (RuntimeException error) {
                starsHealthy = false;
                diagnostics.recordRendererFault("stars", "draw", error);
            }
        }
        if (cloudsHealthy) {
            try {
                cloudRenderer.drawFrame();
            } catch (RuntimeException error) {
                cloudsHealthy = false;
                diagnostics.recordRendererFault("clouds", "draw", error);
            }
        }
        if (worldHealthy) {
            try {
                worldRenderer.drawFrame();
            } catch (RuntimeException error) {
                worldHealthy = false;
                diagnostics.recordRendererFault("world", "draw", error);
            }
        }
        if (atmosphereHealthy) {
            try {
                atmosphereRenderer.drawFrame();
            } catch (RuntimeException error) {
                atmosphereHealthy = false;
                diagnostics.recordRendererFault("atmosphere", "draw", error);
            }
        }
        if (stormHealthy) {
            try {
                stormRenderer.drawFrame();
            } catch (RuntimeException error) {
                stormHealthy = false;
                diagnostics.recordRendererFault("storm", "draw", error);
            }
        }
        if (rainHealthy) {
            try {
                rainRenderer.drawFrame();
            } catch (RuntimeException error) {
                rainHealthy = false;
                diagnostics.recordRendererFault("rain", "draw", error);
            }
        }
        if (snowHealthy) {
            try {
                snowRenderer.drawFrame();
            } catch (RuntimeException error) {
                snowHealthy = false;
                diagnostics.recordRendererFault("snow", "draw", error);
            }
        }
    }

    public void release() {
        safeReleaseSky();
        safeReleaseStars();
        safeReleaseClouds();
        safeReleaseWorld();
        safeReleaseAtmosphere();
        safeReleaseStorm();
        safeReleaseRain();
        safeReleaseSnow();
    }

    private void applyPerformanceDetail() {
        float detail = performanceDetailScale;
        cloudRenderer.setDetailScale(detail);
        stormRenderer.setDetailScale(detail);
        rainRenderer.setDetailScale(detail);
        snowRenderer.setDetailScale(detail);
    }

    /**
     * Copies the smoothed master scene into renderer-specific reusable views.
     * This replaces the old per-update withVisualOptions object fan-out and keeps
     * the per-frame transition path allocation-free.
     */
    private void applyVisualSnapshot(@NonNull GlSceneSnapshot state) {
        ensureRendererViews(state);

        sceneView.copyVisualOptionsFrom(
                state, false, false, true, options.isSnow(), options.isFog(), false
        );
        starView.copyVisualOptionsFrom(
                state, options.isClouds(), true, true, true, options.isFog(), options.isStars()
        );
        cloudView.copyVisualOptionsFrom(
                state, options.isClouds(), false, true, options.isSnow(), options.isFog(), false
        );
        worldView.copyVisualOptionsFrom(
                state, options.isClouds(), options.isRain(), true,
                options.isSnow(), options.isFog(), options.isStars()
        );
        atmosphereView.copyVisualOptionsFrom(
                state, options.isClouds(), true, true,
                options.isSnow(), options.isFog(), options.isStars()
        );
        stormView.copyVisualOptionsFrom(
                state, options.isClouds(), false, true,
                options.isSnow(), options.isFog(), options.isStars()
        );
        rainView.copyVisualOptionsFrom(
                state, true, options.isRain(), options.isLightning(), true, true, true
        );
        snowView.copyVisualOptionsFrom(
                state, true, false, true, options.isSnow(), true, true
        );

        // Rebinding is only reference assignment; renderer views themselves are reused.
        sceneRenderer.setSnapshot(sceneView);
        starRenderer.setSnapshot(starView);
        cloudRenderer.setSnapshot(cloudView);
        worldRenderer.setSnapshot(worldView);
        atmosphereRenderer.setSnapshot(atmosphereView);
        stormRenderer.setSnapshot(stormView);
        stormRenderer.setElectricalEnabled(options.isLightning());
        rainRenderer.setSnapshot(rainView);
        snowRenderer.setSnapshot(snowView);
    }

    private void ensureRendererViews(@NonNull GlSceneSnapshot state) {
        if (sceneView == null) sceneView = GlSceneSnapshot.reusableCopyOf(state);
        if (starView == null) starView = GlSceneSnapshot.reusableCopyOf(state);
        if (cloudView == null) cloudView = GlSceneSnapshot.reusableCopyOf(state);
        if (worldView == null) worldView = GlSceneSnapshot.reusableCopyOf(state);
        if (atmosphereView == null) atmosphereView = GlSceneSnapshot.reusableCopyOf(state);
        if (stormView == null) stormView = GlSceneSnapshot.reusableCopyOf(state);
        if (rainView == null) rainView = GlSceneSnapshot.reusableCopyOf(state);
        if (snowView == null) snowView = GlSceneSnapshot.reusableCopyOf(state);
    }

    private void clearRendererSnapshots() {
        sceneRenderer.setSnapshot(null);
        starRenderer.setSnapshot(null);
        cloudRenderer.setSnapshot(null);
        worldRenderer.setSnapshot(null);
        atmosphereRenderer.setSnapshot(null);
        stormRenderer.setSnapshot(null);
        rainRenderer.setSnapshot(null);
        snowRenderer.setSnapshot(null);
    }

    private void resetRendererHealth() {
        sceneHealthy = true;
        starsHealthy = true;
        cloudsHealthy = true;
        worldHealthy = true;
        atmosphereHealthy = true;
        stormHealthy = true;
        rainHealthy = true;
        snowHealthy = true;
    }

    private void drawFallbackSky() {
        GlSceneSnapshot state = transitionController.current();
        if (state == null) state = fullSnapshot;
        float light = state == null ? 0.45f : Math.max(0f, Math.min(1f, state.sceneLight));
        float red = 0.025f + light * 0.075f;
        float green = 0.045f + light * 0.115f;
        float blue = 0.075f + light * 0.175f;
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glClearColor(red, green, blue, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    private void safeReleaseSky() {
        try { sceneRenderer.release(); } catch (RuntimeException error) {
            diagnostics.recordRendererFault("sky", "release", error);
        }
    }

    private void safeReleaseStars() {
        try { starRenderer.release(); } catch (RuntimeException error) {
            diagnostics.recordRendererFault("stars", "release", error);
        }
    }

    private void safeReleaseClouds() {
        try { cloudRenderer.release(); } catch (RuntimeException error) {
            diagnostics.recordRendererFault("clouds", "release", error);
        }
    }

    private void safeReleaseWorld() {
        try { worldRenderer.release(); } catch (RuntimeException error) {
            diagnostics.recordRendererFault("world", "release", error);
        }
    }

    private void safeReleaseAtmosphere() {
        try { atmosphereRenderer.release(); } catch (RuntimeException error) {
            diagnostics.recordRendererFault("atmosphere", "release", error);
        }
    }

    private void safeReleaseStorm() {
        try { stormRenderer.release(); } catch (RuntimeException error) {
            diagnostics.recordRendererFault("storm", "release", error);
        }
    }

    private void safeReleaseRain() {
        try { rainRenderer.release(); } catch (RuntimeException error) {
            diagnostics.recordRendererFault("rain", "release", error);
        }
    }

    private void safeReleaseSnow() {
        try { snowRenderer.release(); } catch (RuntimeException error) {
            diagnostics.recordRendererFault("snow", "release", error);
        }
    }
}

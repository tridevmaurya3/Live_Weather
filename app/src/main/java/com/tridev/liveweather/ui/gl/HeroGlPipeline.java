package com.tridev.liveweather.ui.gl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.local.WallpaperPreferences;

/**
 * ODM-5 shared GPU composition pipeline used by both the in-app live scene and
 * Android system Live Wallpaper.
 *
 * Render ownership/order is intentionally centralized here so app and wallpaper
 * cannot drift into different visual engines again.
 */
public final class HeroGlPipeline {

    private final HeroGlCloudSceneRenderer sceneRenderer = new HeroGlCloudSceneRenderer();
    private final HeroGlAtmosphereOverlayRenderer atmosphereRenderer = new HeroGlAtmosphereOverlayRenderer();
    private final HeroGlStormOverlayRenderer stormRenderer = new HeroGlStormOverlayRenderer();
    private final HeroGlRainOverlayRenderer rainRenderer = new HeroGlRainOverlayRenderer();

    @Nullable
    private GlSceneSnapshot fullSnapshot;

    @NonNull
    private WallpaperPreferences.Options options = new WallpaperPreferences.Options(
            true, true, true, true, true, true, true
    );

    public void onSurfaceCreated() {
        sceneRenderer.onSurfaceCreated();
        atmosphereRenderer.onSurfaceCreated();
        stormRenderer.onSurfaceCreated();
        rainRenderer.onSurfaceCreated();
        applySnapshot();
    }

    public void onSurfaceChanged(int width, int height) {
        sceneRenderer.onSurfaceChanged(width, height);
        atmosphereRenderer.onSurfaceChanged(width, height);
        stormRenderer.onSurfaceChanged(width, height);
        rainRenderer.onSurfaceChanged(width, height);
    }

    public void setSnapshot(@Nullable GlSceneSnapshot snapshot) {
        fullSnapshot = snapshot;
        applySnapshot();
    }

    public void setOptions(@NonNull WallpaperPreferences.Options options) {
        this.options = options;
        applySnapshot();
    }

    public void drawFrame() {
        sceneRenderer.drawFrame();
        atmosphereRenderer.drawFrame();
        stormRenderer.drawFrame();
        rainRenderer.drawFrame();
    }

    public void release() {
        sceneRenderer.release();
        atmosphereRenderer.release();
        stormRenderer.release();
        rainRenderer.release();
    }

    private void applySnapshot() {
        GlSceneSnapshot state = fullSnapshot;
        if (state == null) {
            sceneRenderer.setSnapshot(null);
            atmosphereRenderer.setSnapshot(null);
            stormRenderer.setSnapshot(null);
            rainRenderer.setSnapshot(null);
            return;
        }

        /*
         * Base scene now contains no legacy lightning/rain shader blocks, so it
         * may keep the real storm intensity for correct storm cloud coloration.
         */
        GlSceneSnapshot sceneSnapshot = state.withVisualOptions(
                options.isClouds(),
                false,
                true,
                options.isSnow(),
                options.isFog(),
                options.isStars()
        );

        /*
         * Cinematic atmosphere is weather-aware even when rain particles are
         * disabled. Fog respects the user's visual Fog option.
         */
        GlSceneSnapshot atmosphereSnapshot = state.withVisualOptions(
                options.isClouds(),
                true,
                true,
                options.isSnow(),
                options.isFog(),
                options.isStars()
        );

        /*
         * Storm darkness remains reality-driven. Electrical visibility is a
         * separate preference and is gated directly on the storm renderer.
         */
        GlSceneSnapshot stormSnapshot = state.withVisualOptions(
                options.isClouds(),
                false,
                true,
                options.isSnow(),
                options.isFog(),
                options.isStars()
        );

        /*
         * Rain particles/wet glass follow the Rain preference. Storm intensity
         * is zeroed here only when Lightning is disabled so foreground water does
         * not receive electrical exposure flashes while lightning is off.
         */
        GlSceneSnapshot rainSnapshot = state.withVisualOptions(
                true,
                options.isRain(),
                options.isLightning(),
                true,
                true,
                true
        );

        sceneRenderer.setSnapshot(sceneSnapshot);
        atmosphereRenderer.setSnapshot(atmosphereSnapshot);
        stormRenderer.setSnapshot(stormSnapshot);
        stormRenderer.setElectricalEnabled(options.isLightning());
        rainRenderer.setSnapshot(rainSnapshot);
    }
}

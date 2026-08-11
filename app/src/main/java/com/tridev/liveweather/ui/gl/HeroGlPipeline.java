package com.tridev.liveweather.ui.gl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.local.WallpaperPreferences;

/**
 * Shared GPU composition pipeline used by both the in-app live scene and
 * Android system Live Wallpaper.
 *
 * Render ownership/order is centralized here so app and wallpaper cannot drift
 * into different visual engines.
 */
public final class HeroGlPipeline {

    private final HeroGlCloudSceneRenderer sceneRenderer = new HeroGlCloudSceneRenderer();
    private final HeroGlWorldLayerRendererV2 worldRenderer = new HeroGlWorldLayerRendererV2();
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
        worldRenderer.onSurfaceCreated();
        atmosphereRenderer.onSurfaceCreated();
        stormRenderer.onSurfaceCreated();
        rainRenderer.onSurfaceCreated();
        applySnapshot();
    }

    public void onSurfaceChanged(int width, int height) {
        sceneRenderer.onSurfaceChanged(width, height);
        worldRenderer.onSurfaceChanged(width, height);
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
        worldRenderer.drawFrame();
        atmosphereRenderer.drawFrame();
        stormRenderer.drawFrame();
        rainRenderer.drawFrame();
    }

    public void release() {
        sceneRenderer.release();
        worldRenderer.release();
        atmosphereRenderer.release();
        stormRenderer.release();
        rainRenderer.release();
    }

    private void applySnapshot() {
        GlSceneSnapshot state = fullSnapshot;
        if (state == null) {
            sceneRenderer.setSnapshot(null);
            worldRenderer.setSnapshot(null);
            atmosphereRenderer.setSnapshot(null);
            stormRenderer.setSnapshot(null);
            rainRenderer.setSnapshot(null);
            return;
        }

        GlSceneSnapshot sceneSnapshot = state.withVisualOptions(
                options.isClouds(),
                false,
                true,
                options.isSnow(),
                options.isFog(),
                options.isStars()
        );

        /*
         * The world layer uses real scene intensities only to choose how its
         * artistic environment is lit and whether rain/storm should reveal the
         * restrained urban/wet-ground treatment. It never changes weather data.
         */
        GlSceneSnapshot worldSnapshot = state.withVisualOptions(
                options.isClouds(),
                options.isRain(),
                true,
                options.isSnow(),
                options.isFog(),
                options.isStars()
        );

        GlSceneSnapshot atmosphereSnapshot = state.withVisualOptions(
                options.isClouds(),
                true,
                true,
                options.isSnow(),
                options.isFog(),
                options.isStars()
        );

        GlSceneSnapshot stormSnapshot = state.withVisualOptions(
                options.isClouds(),
                false,
                true,
                options.isSnow(),
                options.isFog(),
                options.isStars()
        );

        GlSceneSnapshot rainSnapshot = state.withVisualOptions(
                true,
                options.isRain(),
                options.isLightning(),
                true,
                true,
                true
        );

        sceneRenderer.setSnapshot(sceneSnapshot);
        worldRenderer.setSnapshot(worldSnapshot);
        atmosphereRenderer.setSnapshot(atmosphereSnapshot);
        stormRenderer.setSnapshot(stormSnapshot);
        stormRenderer.setElectricalEnabled(options.isLightning());
        rainRenderer.setSnapshot(rainSnapshot);
    }
}

package com.tridev.liveweather.ui.gl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.local.WallpaperPreferences;

/**
 * Shared GPU composition pipeline used by both the in-app live scene and
 * Android system Live Wallpaper.
 *
 * Device Consistency Pass:
 * Sky/Sun/Moon are analytic and hash-free. Stars, clouds and world silhouettes
 * are texture-driven deterministic passes so emulator, Adreno and Mali receive
 * the same source patterns.
 */
public final class HeroGlPipeline {

    private final HeroGlSkyCelestialRenderer sceneRenderer = new HeroGlSkyCelestialRenderer();
    private final HeroGlPortableStarRenderer starRenderer = new HeroGlPortableStarRenderer();
    private final HeroGlPortableCloudRenderer cloudRenderer = new HeroGlPortableCloudRenderer();
    private final HeroGlWorldLayerRendererV4 worldRenderer = new HeroGlWorldLayerRendererV4();
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
        starRenderer.onSurfaceCreated();
        cloudRenderer.onSurfaceCreated();
        worldRenderer.onSurfaceCreated();
        atmosphereRenderer.onSurfaceCreated();
        stormRenderer.onSurfaceCreated();
        rainRenderer.onSurfaceCreated();
        applySnapshot();
    }

    public void onSurfaceChanged(int width, int height) {
        sceneRenderer.onSurfaceChanged(width, height);
        starRenderer.onSurfaceChanged(width, height);
        cloudRenderer.onSurfaceChanged(width, height);
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
        starRenderer.drawFrame();
        cloudRenderer.drawFrame();
        worldRenderer.drawFrame();
        atmosphereRenderer.drawFrame();
        stormRenderer.drawFrame();
        rainRenderer.drawFrame();
    }

    public void release() {
        sceneRenderer.release();
        starRenderer.release();
        cloudRenderer.release();
        worldRenderer.release();
        atmosphereRenderer.release();
        stormRenderer.release();
        rainRenderer.release();
    }

    private void applySnapshot() {
        GlSceneSnapshot state = fullSnapshot;
        if (state == null) {
            sceneRenderer.setSnapshot(null);
            starRenderer.setSnapshot(null);
            cloudRenderer.setSnapshot(null);
            worldRenderer.setSnapshot(null);
            atmosphereRenderer.setSnapshot(null);
            stormRenderer.setSnapshot(null);
            rainRenderer.setSnapshot(null);
            return;
        }

        GlSceneSnapshot sceneSnapshot = state.withVisualOptions(
                false,
                false,
                true,
                options.isSnow(),
                options.isFog(),
                false
        );

        GlSceneSnapshot starSnapshot = state.withVisualOptions(
                options.isClouds(),
                true,
                true,
                true,
                options.isFog(),
                options.isStars()
        );

        GlSceneSnapshot cloudSnapshot = state.withVisualOptions(
                options.isClouds(),
                false,
                true,
                options.isSnow(),
                options.isFog(),
                false
        );

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
        starRenderer.setSnapshot(starSnapshot);
        cloudRenderer.setSnapshot(cloudSnapshot);
        worldRenderer.setSnapshot(worldSnapshot);
        atmosphereRenderer.setSnapshot(atmosphereSnapshot);
        stormRenderer.setSnapshot(stormSnapshot);
        stormRenderer.setElectricalEnabled(options.isLightning());
        rainRenderer.setSnapshot(rainSnapshot);
    }
}

package com.tridev.liveweather.ui.sky;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.local.WallpaperPreferences;
import com.tridev.liveweather.data.remote.dto.AirQualityResponse;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.SkyRealityState;
import com.tridev.liveweather.ui.scene.AirHazeOverlayRenderer;
import com.tridev.liveweather.ui.scene.HeroCloudRenderer;
import com.tridev.liveweather.ui.scene.HeroRainRenderer;
import com.tridev.liveweather.ui.scene.HeroStormRenderer;
import com.tridev.liveweather.ui.scene.NatureSceneRenderer;

public final class LiveSkyView extends View {

    private static final long FRAME_MILLIS = 33L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final NatureSceneRenderer renderer = new NatureSceneRenderer();
    private final HeroCloudRenderer heroCloudRenderer = new HeroCloudRenderer();
    private final HeroRainRenderer heroRainRenderer = new HeroRainRenderer();
    private final HeroStormRenderer heroStormRenderer = new HeroStormRenderer();
    private final AirHazeOverlayRenderer airHazeRenderer = new AirHazeOverlayRenderer();

    private WallpaperPreferences.Options options;
    private boolean attached;

    private final Runnable frameTicker = new Runnable() {
        @Override
        public void run() {
            if (!shouldAnimate()) return;
            postInvalidateOnAnimation();
            handler.postDelayed(this, FRAME_MILLIS);
        }
    };

    public LiveSkyView(Context context) {
        super(context);
        init(context);
    }

    public LiveSkyView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public LiveSkyView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        options = new WallpaperPreferences(context).load();
        applyOptions(options);
        setWillNotDraw(false);
    }

    public void setWeatherData(@Nullable WeatherResponse weather, double latitude, double longitude) {
        renderer.setWeatherData(weather, latitude, longitude);
        heroCloudRenderer.setWeatherData(weather);
        heroRainRenderer.setWeatherData(weather);
        heroStormRenderer.setWeatherData(weather);
        invalidate();
        restartTicker();
    }

    public void setAirQualityData(@Nullable AirQualityResponse airQuality) {
        airHazeRenderer.setAirQuality(airQuality);
        invalidate();
    }

    public void clearWeatherData() {
        renderer.clearWeatherData();
        heroCloudRenderer.clearWeatherData();
        heroRainRenderer.clearWeatherData();
        heroStormRenderer.clearWeatherData();
        invalidate();
    }

    public void clearAirQualityData() {
        airHazeRenderer.setAirQuality(null);
        invalidate();
    }

    public void setRenderOptions(@NonNull WallpaperPreferences.Options options) {
        this.options = options;
        applyOptions(options);
        invalidate();
        restartTicker();
    }

    @Nullable
    public SkyRealityState getLastState() {
        return renderer.getLastSkyRealityState();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        restartTicker();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        handler.removeCallbacks(frameTicker);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            restartTicker();
        } else {
            handler.removeCallbacks(frameTicker);
        }
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            restartTicker();
        } else if (!shouldAnimate()) {
            handler.removeCallbacks(frameTicker);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        long now = System.currentTimeMillis();

        // Base sky/celestial layer. Legacy cloud/rain/lightning are disabled in
        // applyOptions() so only one Hero implementation is ever visible.
        renderer.draw(canvas, getWidth(), getHeight(), now);

        // Path-based clouds naturally pass in front of Sun/Moon.
        heroCloudRenderer.draw(canvas, getWidth(), getHeight(), now);

        // Storm flash first illuminates the cloud/sky volume.
        heroStormRenderer.drawAtmosphere(canvas, getWidth(), getHeight(), now);

        airHazeRenderer.draw(canvas, getWidth(), getHeight());

        // Rain + wet glass receive current lightning strength so foreground water
        // catches the electrical flash.
        float flash = heroStormRenderer.flashStrength(now);
        heroRainRenderer.draw(canvas, getWidth(), getHeight(), now, flash);

        // Visible electric branches stay on top of rain and wet-glass effects.
        heroStormRenderer.drawForeground(canvas, getWidth(), getHeight(), now);
    }

    private void applyOptions(@NonNull WallpaperPreferences.Options options) {
        // HRS-1B/HRS-2/HRS-3: NatureSceneRenderer remains responsible for sky,
        // Sun/Moon/stars/snow/fog only. Hero renderers exclusively own cloud,
        // rain and lightning so legacy effects cannot duplicate or leak artifacts.
        renderer.setOptions(new WallpaperPreferences.Options(
                false,
                false,
                false,
                options.isSnow(),
                options.isFog(),
                options.isStars(),
                options.isBatteryAdaptive()
        ));
        heroCloudRenderer.setEnabled(options.isClouds());
        heroRainRenderer.setEnabled(options.isRain());
        heroStormRenderer.setEnabled(options.isLightning());
    }

    private void restartTicker() {
        handler.removeCallbacks(frameTicker);
        if (shouldAnimate()) handler.post(frameTicker);
    }

    private boolean shouldAnimate() {
        return attached
                && getWindowVisibility() == VISIBLE
                && getVisibility() == VISIBLE
                && isShown();
    }
}

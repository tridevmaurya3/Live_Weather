package com.tridev.liveweather.wallpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;

import com.tridev.liveweather.data.local.AirQualityCache;
import com.tridev.liveweather.data.local.WallpaperPreferences;
import com.tridev.liveweather.data.local.WeatherCache;
import com.tridev.liveweather.ui.scene.AirHazeOverlayRenderer;
import com.tridev.liveweather.ui.scene.HeroCloudRenderer;
import com.tridev.liveweather.ui.scene.HeroRainRenderer;
import com.tridev.liveweather.ui.scene.HeroStormRenderer;
import com.tridev.liveweather.ui.scene.NatureSceneRenderer;
import com.tridev.liveweather.worker.WallpaperWeatherScheduler;

public final class LiveWeatherWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new LiveWeatherEngine();
    }

    private final class LiveWeatherEngine extends Engine {

        private static final long CACHE_RELOAD_MILLIS = 45_000L;
        private static final long NORMAL_FRAME_MILLIS = 33L;
        private static final long ADAPTIVE_FRAME_MILLIS = 50L;
        private static final long POWER_SAVE_FRAME_MILLIS = 66L;

        private final Handler handler = new Handler(Looper.getMainLooper());
        private final NatureSceneRenderer renderer = new NatureSceneRenderer();
        private final HeroCloudRenderer heroCloudRenderer = new HeroCloudRenderer();
        private final HeroRainRenderer heroRainRenderer = new HeroRainRenderer();
        private final HeroStormRenderer heroStormRenderer = new HeroStormRenderer();
        private final AirHazeOverlayRenderer airHazeRenderer = new AirHazeOverlayRenderer();

        private final WeatherCache weatherCache = new WeatherCache(LiveWeatherWallpaperService.this);
        private final AirQualityCache airQualityCache = new AirQualityCache(LiveWeatherWallpaperService.this);
        private final WallpaperPreferences preferences = new WallpaperPreferences(LiveWeatherWallpaperService.this);

        private boolean visible;
        private boolean surfaceReady;
        private long lastCacheReload;
        private long loadedWeatherSavedAt = Long.MIN_VALUE;
        private long loadedAirSavedAt = Long.MIN_VALUE;
        private WallpaperPreferences.Options options = preferences.load();

        private final Runnable drawRunnable = new Runnable() {
            @Override
            public void run() {
                if (!visible || !surfaceReady) return;
                drawFrame();
                // Rain/cloud/storm animations are clock-driven and recycle forever.
                // The loop only pauses when Android reports the wallpaper hidden.
                handler.postDelayed(this, frameIntervalMillis());
            }
        };

        @Override
        public void onCreate(@NonNull SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(false);
            applyOptions(options);
            reloadCache(true);
            WallpaperWeatherScheduler.schedule(LiveWeatherWallpaperService.this);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            handler.removeCallbacks(drawRunnable);
            if (visible) {
                options = preferences.load();
                applyOptions(options);
                reloadCache(true);
                handler.post(drawRunnable);
            }
        }

        @Override
        public void onSurfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            surfaceReady = true;
            if (visible) {
                handler.removeCallbacks(drawRunnable);
                handler.post(drawRunnable);
            }
        }

        @Override
        public void onSurfaceDestroyed(@NonNull SurfaceHolder holder) {
            surfaceReady = false;
            handler.removeCallbacks(drawRunnable);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onOffsetsChanged(
                float xOffset,
                float yOffset,
                float xOffsetStep,
                float yOffsetStep,
                int xPixelOffset,
                int yPixelOffset
        ) {
            renderer.setParallaxOffset(xOffset);
            if (visible) drawFrame();
        }

        @Override
        public void onDestroy() {
            handler.removeCallbacks(drawRunnable);
            super.onDestroy();
        }

        private void drawFrame() {
            long now = System.currentTimeMillis();
            reloadCache(false);

            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    int width = canvas.getWidth();
                    int height = canvas.getHeight();

                    renderer.draw(canvas, width, height, now);
                    heroCloudRenderer.draw(canvas, width, height, now);
                    heroStormRenderer.drawAtmosphere(canvas, width, height, now);
                    airHazeRenderer.draw(canvas, width, height);

                    float flash = heroStormRenderer.flashStrength(now);
                    heroRainRenderer.draw(canvas, width, height, now, flash);
                    heroStormRenderer.drawForeground(canvas, width, height, now);
                }
            } catch (RuntimeException ignored) {
                // Keep the wallpaper engine alive; the next frame will retry.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }

        private void reloadCache(boolean force) {
            long now = System.currentTimeMillis();
            if (!force && now - lastCacheReload < CACHE_RELOAD_MILLIS) return;
            lastCacheReload = now;

            options = preferences.load();
            applyOptions(options);

            WeatherCache.CachedWeather weather = weatherCache.load();
            if (weather == null) {
                renderer.clearWeatherData();
                heroCloudRenderer.clearWeatherData();
                heroRainRenderer.clearWeatherData();
                heroStormRenderer.clearWeatherData();
                airHazeRenderer.setAirQuality(null);
                loadedWeatherSavedAt = Long.MIN_VALUE;
                loadedAirSavedAt = Long.MIN_VALUE;
                return;
            }

            if (force || weather.getSavedAt() != loadedWeatherSavedAt) {
                loadedWeatherSavedAt = weather.getSavedAt();
                renderer.setWeatherData(
                        weather.getWeather(),
                        weather.getLatitude(),
                        weather.getLongitude()
                );
                heroCloudRenderer.setWeatherData(weather.getWeather());
                heroRainRenderer.setWeatherData(weather.getWeather());
                heroStormRenderer.setWeatherData(weather.getWeather());
            }

            AirQualityCache.CachedAirQuality air = airQualityCache.load(
                    weather.getLatitude(),
                    weather.getLongitude()
            );
            if (air == null) {
                airHazeRenderer.setAirQuality(null);
                loadedAirSavedAt = Long.MIN_VALUE;
            } else if (force || air.getSavedAt() != loadedAirSavedAt) {
                loadedAirSavedAt = air.getSavedAt();
                airHazeRenderer.setAirQuality(air.getResponse());
            }
        }

        private void applyOptions(@NonNull WallpaperPreferences.Options options) {
            // Hero pipeline owns cloud/rain/lightning. NatureSceneRenderer keeps
            // sky, celestial bodies, stars, snow and fog only.
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

        private long frameIntervalMillis() {
            if (!options.isBatteryAdaptive()) return NORMAL_FRAME_MILLIS;

            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null && powerManager.isPowerSaveMode()) {
                return POWER_SAVE_FRAME_MILLIS;
            }

            BatteryManager batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            if (batteryManager != null) {
                int capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (capacity >= 0 && capacity <= 20) return ADAPTIVE_FRAME_MILLIS;
            }
            return NORMAL_FRAME_MILLIS;
        }
    }
}

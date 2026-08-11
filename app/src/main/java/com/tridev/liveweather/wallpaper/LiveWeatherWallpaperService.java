package com.tridev.liveweather.wallpaper;

import android.content.Context;
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
import com.tridev.liveweather.data.remote.dto.AirQualityResponse;
import com.tridev.liveweather.worker.WallpaperWeatherScheduler;

/**
 * Android system Live Wallpaper backed by the Hero OpenGL weather engine.
 *
 * The WallpaperService main thread owns lifecycle/cache refresh only. EGL and
 * all animation frames run on GlWallpaperRenderThread.
 */
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
        private final GlWallpaperRenderThread glRenderer = new GlWallpaperRenderThread();
        private final WeatherCache weatherCache = new WeatherCache(LiveWeatherWallpaperService.this);
        private final AirQualityCache airQualityCache = new AirQualityCache(LiveWeatherWallpaperService.this);
        private final WallpaperPreferences preferences = new WallpaperPreferences(LiveWeatherWallpaperService.this);

        private boolean visible;
        private WallpaperPreferences.Options options = preferences.load();

        private final Runnable cacheRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (!visible) return;
                reloadCache();
                handler.postDelayed(this, CACHE_RELOAD_MILLIS);
            }
        };

        @Override
        public void onCreate(@NonNull SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(false);
            applyOptions(preferences.load());
            reloadCache();
            WallpaperWeatherScheduler.schedule(LiveWeatherWallpaperService.this);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            handler.removeCallbacks(cacheRefreshRunnable);
            glRenderer.setVisible(visible);

            if (visible) {
                applyOptions(preferences.load());
                reloadCache();
                handler.postDelayed(cacheRefreshRunnable, CACHE_RELOAD_MILLIS);
            }
        }

        @Override
        public void onSurfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            glRenderer.attachSurface(holder.getSurface(), width, height);
            glRenderer.setVisible(visible);
        }

        @Override
        public void onSurfaceDestroyed(@NonNull SurfaceHolder holder) {
            glRenderer.detachSurface();
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
            glRenderer.setParallax(xOffset);
        }

        @Override
        public void onDestroy() {
            visible = false;
            handler.removeCallbacksAndMessages(null);
            glRenderer.setVisible(false);
            glRenderer.release();
            super.onDestroy();
        }

        private void reloadCache() {
            options = preferences.load();
            applyOptions(options);

            WeatherCache.CachedWeather weather = weatherCache.load();
            if (weather == null) {
                glRenderer.clearWeatherData();
                return;
            }

            AirQualityCache.CachedAirQuality air = airQualityCache.load(
                    weather.getLatitude(),
                    weather.getLongitude()
            );
            AirQualityResponse airResponse = air == null ? null : air.getResponse();

            glRenderer.setWeatherData(
                    weather.getWeather(),
                    airResponse,
                    weather.getLatitude(),
                    weather.getLongitude()
            );
        }

        private void applyOptions(@NonNull WallpaperPreferences.Options newOptions) {
            options = newOptions;
            glRenderer.setVisualOptions(newOptions);
            glRenderer.setFrameIntervalMillis(frameIntervalMillis());
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

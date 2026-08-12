package com.tridev.liveweather.wallpaper;

import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;

import com.tridev.liveweather.core.performance.PerformancePolicy;
import com.tridev.liveweather.data.local.AirQualityCache;
import com.tridev.liveweather.data.local.PerformancePreferences;
import com.tridev.liveweather.data.local.WallpaperPreferences;
import com.tridev.liveweather.data.local.WeatherCache;
import com.tridev.liveweather.data.remote.dto.AirQualityResponse;
import com.tridev.liveweather.worker.WallpaperWeatherScheduler;

/**
 * Android system Live Wallpaper backed by the shared Hero OpenGL weather engine.
 *
 * Phase 14 uses the same adaptive frame policy as in-app live scenes. Rendering
 * still runs only while the wallpaper is visible; network/cache refresh remains
 * outside the frame loop.
 */
public final class LiveWeatherWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new LiveWeatherEngine();
    }

    private final class LiveWeatherEngine extends Engine {

        private static final long CACHE_RELOAD_MILLIS = 45_000L;

        private final Handler handler = new Handler(Looper.getMainLooper());
        private final GlWallpaperRenderThread glRenderer = new GlWallpaperRenderThread();
        private final WeatherCache weatherCache = new WeatherCache(LiveWeatherWallpaperService.this);
        private final AirQualityCache airQualityCache = new AirQualityCache(LiveWeatherWallpaperService.this);
        private final WallpaperPreferences preferences = new WallpaperPreferences(LiveWeatherWallpaperService.this);
        private final PerformancePreferences performancePreferences =
                new PerformancePreferences(LiveWeatherWallpaperService.this);

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
            glRenderer.setFrameIntervalMillis(
                    PerformancePolicy.frameIntervalMillis(
                            LiveWeatherWallpaperService.this,
                            performancePreferences.loadMode(),
                            newOptions.isBatteryAdaptive()
                    )
            );
        }
    }
}

package com.tridev.liveweather.wallpaper;

import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
 * Rendering runs only while the wallpaper is visible. Network refresh remains
 * fully outside the frame loop. The lightweight cache poll is change-detected,
 * so an unchanged cached snapshot is never pushed back through the GL pipeline.
 */
public final class LiveWeatherWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new LiveWeatherEngine();
    }

    private final class LiveWeatherEngine extends Engine {

        private static final long CACHE_RELOAD_MILLIS = 45_000L;
        private static final long NO_CACHE_VERSION = Long.MIN_VALUE;

        private final Handler handler = new Handler(Looper.getMainLooper());
        private final GlWallpaperRenderThread glRenderer = new GlWallpaperRenderThread();
        private final WeatherCache weatherCache = new WeatherCache(LiveWeatherWallpaperService.this);
        private final AirQualityCache airQualityCache = new AirQualityCache(LiveWeatherWallpaperService.this);
        private final WallpaperPreferences preferences = new WallpaperPreferences(LiveWeatherWallpaperService.this);
        private final PerformancePreferences performancePreferences =
                new PerformancePreferences(LiveWeatherWallpaperService.this);

        private boolean visible;

        @Nullable
        private WallpaperPreferences.Options appliedOptions;

        private long appliedWeatherSavedAt = NO_CACHE_VERSION;
        private long appliedAirSavedAt = NO_CACHE_VERSION;
        private double appliedLatitude = Double.NaN;
        private double appliedLongitude = Double.NaN;

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
            reloadCache();
            WallpaperWeatherScheduler.schedule(LiveWeatherWallpaperService.this);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            handler.removeCallbacks(cacheRefreshRunnable);
            glRenderer.setVisible(visible);

            if (visible) {
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
            WallpaperPreferences.Options latestOptions = preferences.load();
            applyOptions(latestOptions);

            WeatherCache.CachedWeather weather = weatherCache.load();
            if (weather == null) {
                if (appliedWeatherSavedAt != NO_CACHE_VERSION) {
                    glRenderer.clearWeatherData();
                    resetAppliedWeatherIdentity();
                }
                return;
            }

            AirQualityCache.CachedAirQuality air = airQualityCache.load(
                    weather.getLatitude(),
                    weather.getLongitude()
            );

            long weatherSavedAt = weather.getSavedAt();
            long airSavedAt = air == null ? NO_CACHE_VERSION : air.getSavedAt();
            boolean locationChanged = !sameCoordinate(appliedLatitude, weather.getLatitude())
                    || !sameCoordinate(appliedLongitude, weather.getLongitude());
            boolean weatherChanged = weatherSavedAt != appliedWeatherSavedAt;
            boolean airChanged = airSavedAt != appliedAirSavedAt;

            if (!locationChanged && !weatherChanged && !airChanged) {
                return;
            }

            AirQualityResponse airResponse = air == null ? null : air.getResponse();
            glRenderer.setWeatherData(
                    weather.getWeather(),
                    airResponse,
                    weather.getLatitude(),
                    weather.getLongitude()
            );

            appliedWeatherSavedAt = weatherSavedAt;
            appliedAirSavedAt = airSavedAt;
            appliedLatitude = weather.getLatitude();
            appliedLongitude = weather.getLongitude();
        }

        private void applyOptions(@NonNull WallpaperPreferences.Options newOptions) {
            if (appliedOptions == null || !sameOptions(appliedOptions, newOptions)) {
                glRenderer.setVisualOptions(newOptions);
                appliedOptions = newOptions;
            }

            // Keep battery / performance policy adaptive without re-sending the
            // visual state or rebuilding the weather snapshot.
            glRenderer.setFrameIntervalMillis(
                    PerformancePolicy.frameIntervalMillis(
                            LiveWeatherWallpaperService.this,
                            performancePreferences.loadMode(),
                            newOptions.isBatteryAdaptive()
                    )
            );
        }

        private void resetAppliedWeatherIdentity() {
            appliedWeatherSavedAt = NO_CACHE_VERSION;
            appliedAirSavedAt = NO_CACHE_VERSION;
            appliedLatitude = Double.NaN;
            appliedLongitude = Double.NaN;
        }

        private boolean sameOptions(
                @NonNull WallpaperPreferences.Options first,
                @NonNull WallpaperPreferences.Options second
        ) {
            return first.isRain() == second.isRain()
                    && first.isClouds() == second.isClouds()
                    && first.isLightning() == second.isLightning()
                    && first.isSnow() == second.isSnow()
                    && first.isFog() == second.isFog()
                    && first.isStars() == second.isStars()
                    && first.isBatteryAdaptive() == second.isBatteryAdaptive();
        }

        private boolean sameCoordinate(double first, double second) {
            if (Double.isNaN(first) || Double.isNaN(second)) return false;
            return Math.abs(first - second) < 0.000001d;
        }
    }
}

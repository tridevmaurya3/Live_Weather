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

import com.tridev.liveweather.data.local.WallpaperPreferences;
import com.tridev.liveweather.data.local.WeatherCache;
import com.tridev.liveweather.ui.scene.NatureSceneRenderer;
import com.tridev.liveweather.worker.WallpaperWeatherScheduler;

/**
 * Real Android live wallpaper that shares the exact procedural nature renderer
 * used by the in-app preview.
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
        private final NatureSceneRenderer renderer = new NatureSceneRenderer();
        private final WeatherCache weatherCache = new WeatherCache(LiveWeatherWallpaperService.this);
        private final WallpaperPreferences preferences = new WallpaperPreferences(LiveWeatherWallpaperService.this);

        private boolean visible;
        private boolean surfaceReady;
        private long lastCacheReload;
        private long loadedSavedAt = Long.MIN_VALUE;
        private WallpaperPreferences.Options options = preferences.load();

        private final Runnable drawRunnable = new Runnable() {
            @Override
            public void run() {
                if (!visible || !surfaceReady) {
                    return;
                }
                drawFrame();
                handler.postDelayed(this, frameIntervalMillis());
            }
        };

        @Override
        public void onCreate(@NonNull SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(false);
            renderer.setOptions(options);
            reloadCache(true);
            WallpaperWeatherScheduler.schedule(LiveWeatherWallpaperService.this);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            handler.removeCallbacks(drawRunnable);
            if (visible) {
                options = preferences.load();
                renderer.setOptions(options);
                reloadCache(true);
                handler.post(drawRunnable);
            }
        }

        @Override
        public void onSurfaceChanged(
                @NonNull SurfaceHolder holder,
                int format,
                int width,
                int height
        ) {
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
            if (visible) {
                drawFrame();
            }
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
                    renderer.draw(
                            canvas,
                            canvas.getWidth(),
                            canvas.getHeight(),
                            now
                    );
                }
            } catch (RuntimeException ignored) {
                // A transient surface loss should not terminate the wallpaper service.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (RuntimeException ignored) {
                        // Surface may have been destroyed between lock and post.
                    }
                }
            }
        }

        private void reloadCache(boolean force) {
            long now = System.currentTimeMillis();
            if (!force && now - lastCacheReload < CACHE_RELOAD_MILLIS) {
                return;
            }
            lastCacheReload = now;

            options = preferences.load();
            renderer.setOptions(options);

            WeatherCache.CachedWeather cached = weatherCache.load();
            if (cached == null) {
                renderer.clearWeatherData();
                loadedSavedAt = Long.MIN_VALUE;
                return;
            }
            if (!force && cached.getSavedAt() == loadedSavedAt) {
                return;
            }
            loadedSavedAt = cached.getSavedAt();
            renderer.setWeatherData(
                    cached.getWeather(),
                    cached.getLatitude(),
                    cached.getLongitude()
            );
        }

        private long frameIntervalMillis() {
            if (!options.isBatteryAdaptive()) {
                return NORMAL_FRAME_MILLIS;
            }

            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null && powerManager.isPowerSaveMode()) {
                return POWER_SAVE_FRAME_MILLIS;
            }

            BatteryManager batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            if (batteryManager != null) {
                int capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (capacity >= 0 && capacity <= 20) {
                    return ADAPTIVE_FRAME_MILLIS;
                }
            }
            return NORMAL_FRAME_MILLIS;
        }
    }
}

package com.tridev.liveweather.wallpaper;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.core.performance.AdaptiveFrameTimeGuard;
import com.tridev.liveweather.data.local.WallpaperPreferences;
import com.tridev.liveweather.data.remote.dto.AirQualityResponse;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.ui.gl.GlRealityAdapter;
import com.tridev.liveweather.ui.gl.GlSceneSnapshot;
import com.tridev.liveweather.ui.gl.HeroGlPipeline;

/**
 * Dedicated EGL14 render thread for Android system Live Wallpaper.
 *
 * Renderer faults are isolated inside HeroGlPipeline. Transient EGL/context
 * failures use bounded recovery, while the frame-time guard adapts only
 * secondary detail after sustained GPU pressure. Weather truth is untouched.
 */
public final class GlWallpaperRenderThread {

    private static final String TAG = "LiveWeatherGL";
    private static final long REALITY_REFRESH_MILLIS = 30_000L;
    private static final long PARALLAX_COMPOSE_MILLIS = 140L;
    private static final long EGL_RECOVERY_DELAY_MILLIS = 220L;
    private static final int MAX_EGL_RECOVERY_ATTEMPTS = 2;

    private final HandlerThread thread;
    private final Handler handler;
    private final HeroGlPipeline pipeline = new HeroGlPipeline();
    private final AdaptiveFrameTimeGuard frameTimeGuard = new AdaptiveFrameTimeGuard();

    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext context = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private EGLConfig config;

    @Nullable private Surface windowSurface;
    @Nullable private WeatherResponse weather;
    @Nullable private AirQualityResponse airQuality;

    @NonNull
    private WallpaperPreferences.Options options = new WallpaperPreferences.Options(
            true, true, true, true, true, true, true
    );

    private double latitude = Double.NaN;
    private double longitude = Double.NaN;
    private float parallax = 0.5f;
    private int width = 1;
    private int height = 1;
    private long frameIntervalMillis = 33L;
    private float performanceDetailScale = 0.82f;
    private long lastRealityRefresh;
    private long lastParallaxCompose;
    private boolean parallaxDirty;
    private boolean visible;
    private boolean released;
    private boolean pipelineCreated;
    private int eglRecoveryAttempts;

    private final Runnable renderRunnable = new Runnable() {
        @Override
        public void run() {
            if (released || !visible || eglSurface == EGL14.EGL_NO_SURFACE) return;

            long loopStartNanos = System.nanoTime();
            try {
                refreshRealityIfNeeded();

                long renderStartNanos = System.nanoTime();
                pipeline.drawFrame();
                if (!EGL14.eglSwapBuffers(display, eglSurface)) {
                    int eglError = EGL14.eglGetError();
                    scheduleEglRecovery(
                            "wallpaper-swap-0x" + Integer.toHexString(eglError),
                            null
                    );
                    return;
                }

                float adaptedDetail = frameTimeGuard.observeFrameNanos(
                        System.nanoTime() - renderStartNanos
                );
                if (!Float.isNaN(adaptedDetail)) {
                    performanceDetailScale = adaptedDetail;
                    pipeline.setPerformanceDetailScale(adaptedDetail);
                }
                eglRecoveryAttempts = 0;
            } catch (RuntimeException error) {
                scheduleEglRecovery("wallpaper-render-runtime", error);
                return;
            }

            if (!released && visible && eglSurface != EGL14.EGL_NO_SURFACE) {
                long loopElapsedMillis = nanosToCeilMillis(System.nanoTime() - loopStartNanos);
                long delayMillis = Math.max(0L, frameIntervalMillis - loopElapsedMillis);
                handler.postDelayed(this, delayMillis);
            }
        }
    };

    public GlWallpaperRenderThread() {
        thread = new HandlerThread("LiveWeather-OpenGL", Process.THREAD_PRIORITY_DISPLAY);
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    public void attachSurface(@NonNull Surface surface, int width, int height) {
        handler.post(() -> {
            if (released) return;
            windowSurface = surface;
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            eglRecoveryAttempts = 0;
            frameTimeGuard.resetMeasurements();
            try {
                createEglSurface();
                restartLoop();
            } catch (RuntimeException error) {
                scheduleEglRecovery("wallpaper-surface-create", error);
            }
        });
    }

    public void detachSurface() {
        handler.post(() -> {
            windowSurface = null;
            eglRecoveryAttempts = 0;
            frameTimeGuard.resetMeasurements();
            handler.removeCallbacks(renderRunnable);
            detachEglSurface();
        });
    }

    public void setVisible(boolean value) {
        handler.post(() -> {
            visible = value;
            restartLoop();
        });
    }

    public void setFrameIntervalMillis(long value) {
        handler.post(() -> frameIntervalMillis = Math.max(16L, value));
    }

    public void setPerformanceProfile(long frameMillis, float detailScale) {
        handler.post(() -> {
            frameIntervalMillis = Math.max(16L, frameMillis);
            performanceDetailScale = frameTimeGuard.setBaseProfile(
                    frameIntervalMillis,
                    clamp(detailScale, 0.5f, 1f)
            );
            if (pipelineCreated) {
                pipeline.setPerformanceDetailScale(performanceDetailScale);
            }
        });
    }

    public void setVisualOptions(@NonNull WallpaperPreferences.Options value) {
        handler.post(() -> {
            options = value;
            if (pipelineCreated) pipeline.setOptions(value);
        });
    }

    public void setWeatherData(
            @NonNull WeatherResponse value,
            @Nullable AirQualityResponse air,
            double lat,
            double lon
    ) {
        handler.post(() -> {
            weather = value;
            airQuality = air;
            latitude = lat;
            longitude = lon;
            lastRealityRefresh = 0L;
        });
    }

    public void clearWeatherData() {
        handler.post(() -> {
            weather = null;
            airQuality = null;
            latitude = Double.NaN;
            longitude = Double.NaN;
            lastRealityRefresh = 0L;
            parallaxDirty = false;
            if (pipelineCreated) pipeline.setSnapshot(null);
        });
    }

    public void setParallax(float offset) {
        handler.post(() -> {
            float bounded = clamp(offset, 0f, 1f);
            if (Math.abs(parallax - bounded) < 0.0005f) return;
            parallax = bounded;
            parallaxDirty = true;
        });
    }

    public void release() {
        handler.post(() -> {
            if (released) return;
            released = true;
            visible = false;
            handler.removeCallbacksAndMessages(null);
            destroyEglContext();
            thread.quitSafely();
        });
    }

    private void scheduleEglRecovery(@NonNull String reason, @Nullable RuntimeException error) {
        handler.removeCallbacks(renderRunnable);
        if (released || !visible || windowSurface == null || !windowSurface.isValid()) return;

        if (eglRecoveryAttempts >= MAX_EGL_RECOVERY_ATTEMPTS) {
            Log.e(TAG, "wallpaper-egl-recovery-exhausted reason=" + reason
                    + " rendererFaults=" + pipeline.getRendererFaultSummary(), error);
            detachEglSurface();
            return;
        }

        eglRecoveryAttempts++;
        Log.e(TAG, "wallpaper-egl-recovery attempt=" + eglRecoveryAttempts
                + " reason=" + reason, error);
        frameTimeGuard.resetMeasurements();
        destroyEglContext();

        long delay = EGL_RECOVERY_DELAY_MILLIS * eglRecoveryAttempts;
        handler.postDelayed(() -> {
            if (released || !visible || windowSurface == null || !windowSurface.isValid()) return;
            try {
                createEglSurface();
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    lastRealityRefresh = 0L;
                    restartLoop();
                } else {
                    scheduleEglRecovery("wallpaper-recreate-no-surface", null);
                }
            } catch (RuntimeException recoveryError) {
                scheduleEglRecovery("wallpaper-recreate-runtime", recoveryError);
            }
        }, delay);
    }

    private void initializeEgl() {
        if (released || display != EGL14.EGL_NO_DISPLAY) return;

        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) {
            throw new IllegalStateException("Unable to obtain EGL display");
        }

        int[] versions = new int[2];
        if (!EGL14.eglInitialize(display, versions, 0, versions, 1)) {
            throw new IllegalStateException("Unable to initialize EGL");
        }

        int[] attrs = {
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 0,
                EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        if (!EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0)
                || count[0] <= 0) {
            throw new IllegalStateException("No compatible OpenGL ES 2 EGL config");
        }

        config = configs[0];
        int[] contextAttrs = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
        context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT, contextAttrs, 0
        );
        if (context == null || context == EGL14.EGL_NO_CONTEXT) {
            throw new IllegalStateException("Unable to create OpenGL ES 2 context");
        }
    }

    private void createEglSurface() {
        if (released || windowSurface == null || !windowSurface.isValid()) return;
        if (display == EGL14.EGL_NO_DISPLAY
                || context == EGL14.EGL_NO_CONTEXT
                || config == null) {
            initializeEgl();
        }

        detachEglSurface();
        int[] attrs = {EGL14.EGL_NONE};
        eglSurface = EGL14.eglCreateWindowSurface(display, config, windowSurface, attrs, 0);
        if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
            eglSurface = EGL14.EGL_NO_SURFACE;
            return;
        }
        if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
            detachEglSurface();
            return;
        }

        if (!pipelineCreated) {
            pipeline.onSurfaceCreated();
            pipelineCreated = true;
        }
        performanceDetailScale = frameTimeGuard.getEffectiveDetailScale();
        pipeline.setPerformanceDetailScale(performanceDetailScale);
        pipeline.onSurfaceChanged(width, height);
        pipeline.setOptions(options);
        frameTimeGuard.resetMeasurements();
        lastRealityRefresh = 0L;
        lastParallaxCompose = 0L;
        parallaxDirty = true;
    }

    private void detachEglSurface() {
        if (display == EGL14.EGL_NO_DISPLAY) return;
        EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
        );
        if (eglSurface != null && eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(display, eglSurface);
        }
        eglSurface = EGL14.EGL_NO_SURFACE;
    }

    private void destroyEglContext() {
        if (display != EGL14.EGL_NO_DISPLAY
                && eglSurface != EGL14.EGL_NO_SURFACE
                && context != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context);
        }
        if (pipelineCreated) {
            pipeline.release();
            pipelineCreated = false;
        }
        detachEglSurface();
        if (display != EGL14.EGL_NO_DISPLAY && context != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(display, context);
        }
        if (display != EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(display);
        config = null;
        context = EGL14.EGL_NO_CONTEXT;
        display = EGL14.EGL_NO_DISPLAY;
    }

    private void refreshRealityIfNeeded() {
        long now = System.currentTimeMillis();
        boolean regularDue = lastRealityRefresh <= 0L
                || now - lastRealityRefresh >= REALITY_REFRESH_MILLIS;
        boolean parallaxDue = parallaxDirty
                && (lastParallaxCompose <= 0L
                || now - lastParallaxCompose >= PARALLAX_COMPOSE_MILLIS);
        if (!regularDue && !parallaxDue) return;

        WeatherResponse current = weather;
        if (current == null || Double.isNaN(latitude) || Double.isNaN(longitude)) {
            if (pipelineCreated) pipeline.setSnapshot(null);
            parallaxDirty = false;
            if (regularDue) lastRealityRefresh = now;
            if (parallaxDue) lastParallaxCompose = now;
            return;
        }

        GlSceneSnapshot snapshot = GlRealityAdapter.compose(
                current, airQuality, latitude, longitude, now, parallax
        );
        if (pipelineCreated) pipeline.setSnapshot(snapshot);
        if (regularDue) lastRealityRefresh = now;
        if (parallaxDue) {
            lastParallaxCompose = now;
            parallaxDirty = false;
        }
    }

    private void restartLoop() {
        handler.removeCallbacks(renderRunnable);
        if (!released && visible && eglSurface != EGL14.EGL_NO_SURFACE) {
            handler.post(renderRunnable);
        } else {
            frameTimeGuard.resetMeasurements();
        }
    }

    private static long nanosToCeilMillis(long nanos) {
        if (nanos <= 0L) return 0L;
        return (nanos + 999_999L) / 1_000_000L;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

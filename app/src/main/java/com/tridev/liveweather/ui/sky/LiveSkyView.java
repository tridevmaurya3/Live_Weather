package com.tridev.liveweather.ui.sky;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.core.performance.AdaptiveFrameTimeGuard;
import com.tridev.liveweather.core.performance.CinematicPerformanceGovernor;
import com.tridev.liveweather.data.local.PerformancePreferences;
import com.tridev.liveweather.data.local.WallpaperPreferences;
import com.tridev.liveweather.data.remote.dto.AirQualityResponse;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.SkyRealityEngine;
import com.tridev.liveweather.domain.SkyRealityState;
import com.tridev.liveweather.ui.gl.GlRealityAdapter;
import com.tridev.liveweather.ui.gl.GlSceneSnapshot;
import com.tridev.liveweather.ui.gl.HeroGlPipeline;

/**
 * In-app live weather surface backed by the same OpenGL pipeline as the system
 * Live Wallpaper. Hidden views draw zero frames. Renderer faults are isolated,
 * transient EGL failures recover in a bounded path, and sustained frame pressure
 * trims only secondary detail while the actual weather state remains untouched.
 *
 * Visual-option updates are versioned separately from weather/AQI/location truth,
 * so toggling clouds/rain/lightning/snow/fog/stars never forces astronomy/weather
 * recomposition or looks like a scene refresh.
 */
public final class LiveSkyView extends FrameLayout implements TextureView.SurfaceTextureListener {

    private static final String TAG = "LiveWeatherGL";
    private static final long REALITY_REFRESH_MILLIS = 30_000L;
    private static final long PERFORMANCE_REFRESH_MILLIS = 15_000L;
    private static final long EGL_RECOVERY_DELAY_MILLIS = 180L;
    private static final int MAX_EGL_RECOVERY_ATTEMPTS = 2;

    private static final Object SHARED_LOCK = new Object();
    @Nullable private static WeatherResponse sharedWeather;
    @Nullable private static AirQualityResponse sharedAirQuality;
    @Nullable private static WallpaperPreferences.Options sharedOptions;
    private static double sharedLatitude = Double.NaN;
    private static double sharedLongitude = Double.NaN;
    private static long sharedVersion = 1L;
    private static long sharedOptionsVersion = 1L;

    private final Context appContext;
    private final TextureView textureView;
    private final HeroGlPipeline pipeline = new HeroGlPipeline();
    private final AdaptiveFrameTimeGuard frameTimeGuard = new AdaptiveFrameTimeGuard();
    private final HandlerThread renderThread;
    private final Handler renderHandler;
    private final PerformancePreferences performancePreferences;

    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private EGLConfig eglConfig;

    private int surfaceWidth = 1;
    private int surfaceHeight = 1;
    private boolean attached;
    private boolean visible;
    private volatile boolean released;
    private boolean pipelineCreated;
    private int eglRecoveryAttempts;
    private long lastRealityRefresh;
    private long lastPerformanceRefresh;
    private long frameIntervalMillis = 33L;
    private long seenSharedVersion = -1L;
    private long seenSharedOptionsVersion = -1L;

    @Nullable private volatile SkyRealityState lastSkyState;

    private final Runnable renderRunnable = new Runnable() {
        @Override
        public void run() {
            if (released || !visible || eglSurface == EGL14.EGL_NO_SURFACE) return;

            long loopStartNanos = System.nanoTime();
            try {
                refreshPerformanceIfNeeded();
                refreshRealityIfNeeded();

                long renderStartNanos = System.nanoTime();
                pipeline.drawFrame();
                if (!EGL14.eglSwapBuffers(display, eglSurface)) {
                    int eglError = EGL14.eglGetError();
                    scheduleEglRecovery("app-swap-0x" + Integer.toHexString(eglError), null);
                    return;
                }

                float adaptedDetail = frameTimeGuard.observeFrameNanos(
                        System.nanoTime() - renderStartNanos
                );
                if (!Float.isNaN(adaptedDetail)) {
                    pipeline.setPerformanceDetailScale(adaptedDetail);
                }
                eglRecoveryAttempts = 0;
            } catch (RuntimeException error) {
                scheduleEglRecovery("app-render-runtime", error);
                return;
            }

            if (!released && visible && eglSurface != EGL14.EGL_NO_SURFACE) {
                long loopElapsedMillis = nanosToCeilMillis(System.nanoTime() - loopStartNanos);
                long delayMillis = Math.max(0L, frameIntervalMillis - loopElapsedMillis);
                renderHandler.postDelayed(this, delayMillis);
            }
        }
    };

    public LiveSkyView(Context context) {
        this(context, null);
    }

    public LiveSkyView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LiveSkyView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        appContext = context.getApplicationContext();
        performancePreferences = new PerformancePreferences(appContext);

        synchronized (SHARED_LOCK) {
            if (sharedOptions == null) {
                sharedOptions = new WallpaperPreferences(context).load();
                sharedOptionsVersion++;
            }
        }

        WallpaperPreferences.Options initialOptions;
        synchronized (SHARED_LOCK) {
            initialOptions = sharedOptions;
        }
        CinematicPerformanceGovernor.Profile initialProfile = CinematicPerformanceGovernor.resolve(
                appContext,
                performancePreferences.loadMode(),
                initialOptions == null || initialOptions.isBatteryAdaptive(),
                CinematicPerformanceGovernor.Surface.APP_HERO
        );
        frameIntervalMillis = initialProfile.frameIntervalMillis;
        float initialDetail = frameTimeGuard.setBaseProfile(
                initialProfile.frameIntervalMillis,
                initialProfile.detailScale
        );
        pipeline.setPerformanceDetailScale(initialDetail);

        setClipToOutline(true);
        setClipChildren(true);

        textureView = new TextureView(context);
        textureView.setOpaque(true);
        textureView.setSurfaceTextureListener(this);
        textureView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(textureView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        renderThread = new HandlerThread("LiveWeather-AppGL", Process.THREAD_PRIORITY_DISPLAY);
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());
    }

    public void setWeatherData(@Nullable WeatherResponse weather, double latitude, double longitude) {
        synchronized (SHARED_LOCK) {
            sharedWeather = weather;
            sharedLatitude = weather == null ? Double.NaN : latitude;
            sharedLongitude = weather == null ? Double.NaN : longitude;
            sharedVersion++;
        }
        requestRealityRefresh();
    }

    public void setAirQualityData(@Nullable AirQualityResponse airQuality) {
        synchronized (SHARED_LOCK) {
            sharedAirQuality = airQuality;
            sharedVersion++;
        }
        requestRealityRefresh();
    }

    public void clearWeatherData() {
        synchronized (SHARED_LOCK) {
            sharedWeather = null;
            sharedLatitude = Double.NaN;
            sharedLongitude = Double.NaN;
            sharedVersion++;
        }
        requestRealityRefresh();
    }

    public void clearAirQualityData() {
        synchronized (SHARED_LOCK) {
            sharedAirQuality = null;
            sharedVersion++;
        }
        requestRealityRefresh();
    }

    public void setRenderOptions(@NonNull WallpaperPreferences.Options options) {
        synchronized (SHARED_LOCK) {
            sharedOptions = options;
            sharedOptionsVersion++;
        }
        requestVisualOptionsRefresh();
    }

    @Nullable
    public SkyRealityState getLastState() {
        return lastSkyState;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        updateVisibilityAndLoop();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        visible = false;
        renderHandler.removeCallbacks(renderRunnable);
        releaseRendererThread();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateVisibilityAndLoop();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateVisibilityAndLoop();
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        if (released) return;
        surfaceWidth = Math.max(1, width);
        surfaceHeight = Math.max(1, height);
        eglRecoveryAttempts = 0;
        renderHandler.post(() -> {
            if (released) return;
            try {
                frameTimeGuard.resetMeasurements();
                createEglSurface(surface, surfaceWidth, surfaceHeight);
                seenSharedVersion = -1L;
                seenSharedOptionsVersion = -1L;
                lastPerformanceRefresh = 0L;
                restartLoopOnRenderThread();
            } catch (RuntimeException error) {
                scheduleEglRecovery("app-surface-create", error);
            }
        });
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        surfaceWidth = Math.max(1, width);
        surfaceHeight = Math.max(1, height);
        renderHandler.post(() -> {
            if (released || eglSurface == EGL14.EGL_NO_SURFACE) return;
            if (EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)) {
                pipeline.onSurfaceChanged(surfaceWidth, surfaceHeight);
                frameTimeGuard.resetMeasurements();
            }
        });
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        if (!released) {
            renderHandler.post(() -> {
                renderHandler.removeCallbacks(renderRunnable);
                frameTimeGuard.resetMeasurements();
                detachEglSurface();
            });
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
        // Frames are produced by the dedicated EGL thread.
    }

    private void updateVisibilityAndLoop() {
        if (released) return;
        visible = attached
                && getWindowVisibility() == VISIBLE
                && getVisibility() == VISIBLE
                && isShown();
        renderHandler.post(this::restartLoopOnRenderThread);
    }

    private void requestRealityRefresh() {
        if (released) return;
        renderHandler.post(() -> {
            seenSharedVersion = -1L;
            lastRealityRefresh = 0L;
            lastPerformanceRefresh = 0L;
            restartLoopOnRenderThread();
        });
    }

    /** Visual toggles wake the renderer without invalidating weather/astronomy truth. */
    private void requestVisualOptionsRefresh() {
        if (released) return;
        renderHandler.post(() -> {
            seenSharedOptionsVersion = -1L;
            lastPerformanceRefresh = 0L;
            restartLoopOnRenderThread();
        });
    }

    private void restartLoopOnRenderThread() {
        renderHandler.removeCallbacks(renderRunnable);
        if (!released && visible && eglSurface != EGL14.EGL_NO_SURFACE) {
            renderHandler.post(renderRunnable);
        } else {
            frameTimeGuard.resetMeasurements();
        }
    }

    private void scheduleEglRecovery(@NonNull String reason, @Nullable RuntimeException error) {
        renderHandler.removeCallbacks(renderRunnable);
        if (released || !visible) return;

        if (eglRecoveryAttempts >= MAX_EGL_RECOVERY_ATTEMPTS) {
            Log.e(TAG, "app-egl-recovery-exhausted reason=" + reason
                    + " rendererFaults=" + pipeline.getRendererFaultSummary(), error);
            detachEglSurface();
            return;
        }

        eglRecoveryAttempts++;
        Log.e(TAG, "app-egl-recovery attempt=" + eglRecoveryAttempts + " reason=" + reason, error);
        frameTimeGuard.resetMeasurements();
        resetEglContextForRecovery();

        long delay = EGL_RECOVERY_DELAY_MILLIS * eglRecoveryAttempts;
        renderHandler.postDelayed(() -> {
            if (released || !visible) return;
            SurfaceTexture surface = textureView.getSurfaceTexture();
            if (surface == null) return;
            try {
                createEglSurface(surface, surfaceWidth, surfaceHeight);
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    seenSharedVersion = -1L;
                    seenSharedOptionsVersion = -1L;
                    lastRealityRefresh = 0L;
                    restartLoopOnRenderThread();
                } else {
                    scheduleEglRecovery("app-recreate-no-surface", null);
                }
            } catch (RuntimeException recoveryError) {
                scheduleEglRecovery("app-recreate-runtime", recoveryError);
            }
        }, delay);
    }

    private void initializeEgl() {
        if (released || display != EGL14.EGL_NO_DISPLAY) return;
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) {
            throw new IllegalStateException("Unable to obtain app EGL display");
        }
        int[] versions = new int[2];
        if (!EGL14.eglInitialize(display, versions, 0, versions, 1)) {
            throw new IllegalStateException("Unable to initialize app EGL");
        }

        int[] configAttributes = {
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
        if (!EGL14.eglChooseConfig(display, configAttributes, 0, configs, 0, 1, count, 0)
                || count[0] <= 0) {
            throw new IllegalStateException("No compatible app OpenGL ES 2 EGL config");
        }
        eglConfig = configs[0];
        int[] contextAttributes = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
        eglContext = EGL14.eglCreateContext(
                display, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttributes, 0
        );
        if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) {
            throw new IllegalStateException("Unable to create app OpenGL ES 2 context");
        }
    }

    private void createEglSurface(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
        if (released) return;
        if (display == EGL14.EGL_NO_DISPLAY
                || eglContext == EGL14.EGL_NO_CONTEXT
                || eglConfig == null) {
            initializeEgl();
        }
        detachEglSurface();
        int[] surfaceAttributes = {EGL14.EGL_NONE};
        eglSurface = EGL14.eglCreateWindowSurface(
                display, eglConfig, surfaceTexture, surfaceAttributes, 0
        );
        if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
            eglSurface = EGL14.EGL_NO_SURFACE;
            return;
        }
        if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)) {
            detachEglSurface();
            return;
        }
        if (!pipelineCreated) {
            pipeline.onSurfaceCreated();
            pipelineCreated = true;
        }
        pipeline.setPerformanceDetailScale(frameTimeGuard.getEffectiveDetailScale());
        pipeline.onSurfaceChanged(width, height);
        seenSharedVersion = -1L;
        seenSharedOptionsVersion = -1L;
        lastRealityRefresh = 0L;
        lastPerformanceRefresh = 0L;
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

    private void resetEglContextForRecovery() {
        if (display != EGL14.EGL_NO_DISPLAY
                && eglSurface != EGL14.EGL_NO_SURFACE
                && eglContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext);
        }
        if (pipelineCreated) {
            pipeline.release();
            pipelineCreated = false;
        }
        detachEglSurface();
        if (display != EGL14.EGL_NO_DISPLAY && eglContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(display, eglContext);
        }
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglTerminate(display);
        }
        eglConfig = null;
        eglContext = EGL14.EGL_NO_CONTEXT;
        display = EGL14.EGL_NO_DISPLAY;
    }

    private void refreshPerformanceIfNeeded() {
        long now = System.currentTimeMillis();
        if (lastPerformanceRefresh > 0L
                && now - lastPerformanceRefresh < PERFORMANCE_REFRESH_MILLIS) {
            return;
        }
        lastPerformanceRefresh = now;

        WallpaperPreferences.Options options;
        synchronized (SHARED_LOCK) {
            options = sharedOptions;
        }
        CinematicPerformanceGovernor.Profile profile = CinematicPerformanceGovernor.resolve(
                appContext,
                performancePreferences.loadMode(),
                options == null || options.isBatteryAdaptive(),
                CinematicPerformanceGovernor.Surface.APP_HERO
        );
        frameIntervalMillis = profile.frameIntervalMillis;
        float effectiveDetail = frameTimeGuard.setBaseProfile(
                profile.frameIntervalMillis,
                profile.detailScale
        );
        pipeline.setPerformanceDetailScale(effectiveDetail);
    }

    private void refreshRealityIfNeeded() {
        long now = System.currentTimeMillis();
        WeatherResponse weather;
        AirQualityResponse airQuality;
        WallpaperPreferences.Options options;
        double latitude;
        double longitude;
        long version;
        long optionsVersion;
        synchronized (SHARED_LOCK) {
            weather = sharedWeather;
            airQuality = sharedAirQuality;
            options = sharedOptions;
            latitude = sharedLatitude;
            longitude = sharedLongitude;
            version = sharedVersion;
            optionsVersion = sharedOptionsVersion;
        }

        // Visual options are cheap state updates and never invalidate reality composition.
        if (optionsVersion != seenSharedOptionsVersion) {
            if (options != null) pipeline.setOptions(options);
            seenSharedOptionsVersion = optionsVersion;
        }

        if (version == seenSharedVersion
                && lastRealityRefresh > 0L
                && now - lastRealityRefresh < REALITY_REFRESH_MILLIS) {
            return;
        }
        seenSharedVersion = version;
        lastRealityRefresh = now;

        if (weather == null || Double.isNaN(latitude) || Double.isNaN(longitude)) {
            pipeline.setSnapshot(null);
            lastSkyState = null;
            return;
        }
        GlSceneSnapshot snapshot = GlRealityAdapter.compose(
                weather, airQuality, latitude, longitude, now, 0.5f
        );
        pipeline.setSnapshot(snapshot);
        lastSkyState = SkyRealityEngine.calculate(weather, latitude, longitude, now);
    }

    private void releaseRendererThread() {
        if (released) return;
        released = true;
        renderHandler.post(() -> {
            renderHandler.removeCallbacksAndMessages(null);
            if (display != EGL14.EGL_NO_DISPLAY
                    && eglSurface != EGL14.EGL_NO_SURFACE
                    && eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext);
            }
            if (pipelineCreated) {
                pipeline.release();
                pipelineCreated = false;
            }
            detachEglSurface();
            if (display != EGL14.EGL_NO_DISPLAY && eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, eglContext);
            }
            if (display != EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(display);
            eglConfig = null;
            eglContext = EGL14.EGL_NO_CONTEXT;
            display = EGL14.EGL_NO_DISPLAY;
            renderThread.quitSafely();
        });
    }

    private static long nanosToCeilMillis(long nanos) {
        if (nanos <= 0L) return 0L;
        return (nanos + 999_999L) / 1_000_000L;
    }
}

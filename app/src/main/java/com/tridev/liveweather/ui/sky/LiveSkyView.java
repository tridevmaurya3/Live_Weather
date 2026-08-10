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
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.SkyRealityState;
import com.tridev.liveweather.ui.scene.NatureSceneRenderer;

/**
 * App-side animated sky/weather surface.
 *
 * The rendering implementation is shared with WallpaperService. Weather comes
 * from the shared cached pipeline while Sun/Moon/particle motion is local.
 */
public final class LiveSkyView extends View {

    private static final long FRAME_MILLIS = 33L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final NatureSceneRenderer renderer = new NatureSceneRenderer();
    private WallpaperPreferences.Options options;
    private boolean attached;

    private final Runnable frameTicker = new Runnable() {
        @Override
        public void run() {
            if (!shouldAnimate()) {
                return;
            }
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
        renderer.setOptions(options);
        setWillNotDraw(false);
    }

    public void setWeatherData(
            @Nullable WeatherResponse weather,
            double latitude,
            double longitude
    ) {
        renderer.setWeatherData(weather, latitude, longitude);
        invalidate();
        restartTicker();
    }

    public void clearWeatherData() {
        renderer.clearWeatherData();
        invalidate();
    }

    public void setRenderOptions(@NonNull WallpaperPreferences.Options options) {
        this.options = options;
        renderer.setOptions(options);
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
        renderer.draw(
                canvas,
                getWidth(),
                getHeight(),
                System.currentTimeMillis()
        );
    }

    private void restartTicker() {
        handler.removeCallbacks(frameTicker);
        if (shouldAnimate()) {
            handler.post(frameTicker);
        }
    }

    private boolean shouldAnimate() {
        return attached
                && getWindowVisibility() == VISIBLE
                && getVisibility() == VISIBLE
                && isShown();
    }
}

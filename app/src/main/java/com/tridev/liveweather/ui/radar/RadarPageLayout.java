package com.tridev.liveweather.ui.radar;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.tridev.liveweather.R;
import com.tridev.liveweather.data.local.WeatherCache;

/**
 * Self-contained Phase 9 coordinator.
 *
 * It follows the same latest successful WeatherCache location used by the app
 * and Live Wallpaper, so current GPS and selected-city changes automatically
 * propagate to Radar without introducing another location permission flow.
 */
public final class RadarPageLayout extends LinearLayout {

    private static final long CACHE_WATCH_MILLIS = 5_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WeatherCache weatherCache;
    private RadarViewModel viewModel;
    private Phase9Renderer renderer;
    private boolean initialized;
    private long lastWeatherSavedAt = Long.MIN_VALUE;
    private double lastLatitude = Double.NaN;
    private double lastLongitude = Double.NaN;

    private final Runnable cacheWatcher = new Runnable() {
        @Override
        public void run() {
            if (!initialized) return;
            if (isShown()) refreshFromWeatherCache(false);
            handler.postDelayed(this, CACHE_WATCH_MILLIS);
        }
    };

    public RadarPageLayout(Context context) {
        super(context);
    }

    public RadarPageLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public RadarPageLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (initialized) return;

        AppCompatActivity activity = findActivity(getContext());
        if (activity == null) return;

        initialized = true;
        weatherCache = new WeatherCache(activity);
        viewModel = new ViewModelProvider(activity).get(RadarViewModel.class);
        renderer = new Phase9Renderer(activity);
        renderer.setRefreshAction(() -> refreshFromWeatherCache(true));
        viewModel.getState().observe(activity, state -> {
            if (state != null && renderer != null) renderer.render(state);
        });

        refreshFromWeatherCache(false);
        handler.removeCallbacks(cacheWatcher);
        handler.postDelayed(cacheWatcher, CACHE_WATCH_MILLIS);
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (initialized && visibility == VISIBLE) {
            refreshFromWeatherCache(false);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(cacheWatcher);
        if (renderer != null) renderer.onDestroy();
        renderer = null;
        initialized = false;
        super.onDetachedFromWindow();
    }

    private void refreshFromWeatherCache(boolean force) {
        if (weatherCache == null || viewModel == null) return;
        WeatherCache.CachedWeather cached = weatherCache.load();
        if (cached == null) {
            TextView status = findViewById(R.id.radarStatusValue);
            if (status != null) {
                status.setText("Waiting for the first successful weather/location sync…");
            }
            return;
        }

        boolean changed = cached.getSavedAt() != lastWeatherSavedAt
                || Double.isNaN(lastLatitude)
                || Math.abs(lastLatitude - cached.getLatitude()) > 0.02d
                || Math.abs(lastLongitude - cached.getLongitude()) > 0.02d;

        if (!force && !changed) return;

        lastWeatherSavedAt = cached.getSavedAt();
        lastLatitude = cached.getLatitude();
        lastLongitude = cached.getLongitude();
        viewModel.refresh(lastLatitude, lastLongitude, force);
    }

    @Nullable
    private AppCompatActivity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof AppCompatActivity) {
                return (AppCompatActivity) current;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        if (current instanceof Activity && current instanceof AppCompatActivity) {
            return (AppCompatActivity) current;
        }
        return null;
    }
}

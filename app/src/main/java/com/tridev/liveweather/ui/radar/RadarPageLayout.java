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
import com.tridev.liveweather.data.local.SavedCityStore;
import com.tridev.liveweather.data.local.WeatherCache;
import com.tridev.liveweather.domain.CityLocation;

/**
 * Self-contained Phase 9 coordinator.
 *
 * Selected cities are read directly from SavedCityStore so Radar switches even
 * before a fresh weather network response arrives. Current-device mode follows
 * the latest successful foreground GPS weather location from WeatherCache.
 */
public final class RadarPageLayout extends LinearLayout {

    private static final long LOCATION_WATCH_MILLIS = 5_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WeatherCache weatherCache;
    private SavedCityStore savedCityStore;
    private RadarViewModel viewModel;
    private Phase9Renderer renderer;
    private boolean initialized;
    private long lastWeatherSavedAt = Long.MIN_VALUE;
    private double lastLatitude = Double.NaN;
    private double lastLongitude = Double.NaN;
    private boolean lastSourceWasSelectedCity;

    private final Runnable locationWatcher = new Runnable() {
        @Override
        public void run() {
            if (!initialized) return;
            if (isShown()) refreshActiveLocation(false);
            handler.postDelayed(this, LOCATION_WATCH_MILLIS);
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
        savedCityStore = new SavedCityStore(activity);
        viewModel = new ViewModelProvider(activity).get(RadarViewModel.class);
        renderer = new Phase9Renderer(activity);
        renderer.setRefreshAction(() -> refreshActiveLocation(true));
        viewModel.getState().observe(activity, state -> {
            if (state != null && renderer != null) renderer.render(state);
        });

        refreshActiveLocation(false);
        handler.removeCallbacks(locationWatcher);
        handler.postDelayed(locationWatcher, LOCATION_WATCH_MILLIS);
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (initialized && visibility == VISIBLE) {
            refreshActiveLocation(false);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(locationWatcher);
        if (renderer != null) renderer.onDestroy();
        renderer = null;
        initialized = false;
        super.onDetachedFromWindow();
    }

    private void refreshActiveLocation(boolean force) {
        if (weatherCache == null || savedCityStore == null || viewModel == null) return;

        CityLocation selectedCity = savedCityStore.getSelectedCity();
        if (selectedCity != null) {
            double latitude = selectedCity.getLatitude();
            double longitude = selectedCity.getLongitude();
            boolean changed = !lastSourceWasSelectedCity
                    || Double.isNaN(lastLatitude)
                    || Math.abs(lastLatitude - latitude) > 0.02d
                    || Math.abs(lastLongitude - longitude) > 0.02d;
            if (!force && !changed) return;

            lastSourceWasSelectedCity = true;
            lastWeatherSavedAt = Long.MIN_VALUE;
            lastLatitude = latitude;
            lastLongitude = longitude;
            viewModel.refresh(latitude, longitude, force);
            return;
        }

        WeatherCache.CachedWeather cached = weatherCache.load();
        if (cached == null) {
            TextView status = findViewById(R.id.radarStatusValue);
            if (status != null) {
                status.setText("Waiting for the first successful current-location weather sync…");
            }
            return;
        }

        boolean changed = lastSourceWasSelectedCity
                || cached.getSavedAt() != lastWeatherSavedAt
                || Double.isNaN(lastLatitude)
                || Math.abs(lastLatitude - cached.getLatitude()) > 0.02d
                || Math.abs(lastLongitude - cached.getLongitude()) > 0.02d;
        if (!force && !changed) return;

        lastSourceWasSelectedCity = false;
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

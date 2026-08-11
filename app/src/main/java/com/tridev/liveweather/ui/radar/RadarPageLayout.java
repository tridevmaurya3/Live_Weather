package com.tridev.liveweather.ui.radar;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.tridev.liveweather.R;
import com.tridev.liveweather.domain.WeatherUiState;
import com.tridev.liveweather.ui.weather.WeatherViewModel;

/**
 * Phase 9 Radar coordinator.
 *
 * Radar follows the activity-scoped WeatherViewModel directly. WeatherViewModel
 * publishes the requested coordinates immediately when current GPS or a saved
 * city becomes active, before the weather network response completes. This
 * prevents Radar from temporarily following an unrelated last-successful cache
 * location.
 */
public final class RadarPageLayout extends LinearLayout {

    private WeatherViewModel weatherViewModel;
    private RadarViewModel radarViewModel;
    private Phase9Renderer renderer;
    private AppCompatActivity activity;
    private boolean initialized;

    private double lastLatitude = Double.NaN;
    private double lastLongitude = Double.NaN;

    private final Observer<WeatherUiState> weatherObserver = state -> {
        if (state == null) return;
        double latitude = state.getLatitude();
        double longitude = state.getLongitude();
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) return;
        syncRadarLocation(latitude, longitude, false);
    };

    private final Observer<RadarUiState> radarObserver = state -> {
        if (state != null && renderer != null) {
            renderer.render(state);
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

        activity = findActivity(getContext());
        if (activity == null) return;

        initialized = true;
        weatherViewModel = new ViewModelProvider(activity).get(WeatherViewModel.class);
        radarViewModel = new ViewModelProvider(activity).get(RadarViewModel.class);
        renderer = new Phase9Renderer(activity);
        renderer.setRefreshAction(() -> refreshRadar(true));

        weatherViewModel.getWeatherState().observe(activity, weatherObserver);
        radarViewModel.getState().observe(activity, radarObserver);

        refreshRadar(false);
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (initialized && visibility == VISIBLE) {
            refreshRadar(false);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (weatherViewModel != null) {
            weatherViewModel.getWeatherState().removeObserver(weatherObserver);
        }
        if (radarViewModel != null) {
            radarViewModel.getState().removeObserver(radarObserver);
        }
        if (renderer != null) {
            renderer.onDestroy();
        }

        renderer = null;
        radarViewModel = null;
        weatherViewModel = null;
        activity = null;
        initialized = false;
        lastLatitude = Double.NaN;
        lastLongitude = Double.NaN;
        super.onDetachedFromWindow();
    }

    private void refreshRadar(boolean force) {
        if (weatherViewModel == null || radarViewModel == null) return;

        WeatherUiState state = weatherViewModel.getWeatherState().getValue();
        if (state == null
                || Double.isNaN(state.getLatitude())
                || Double.isNaN(state.getLongitude())) {
            TextView status = findViewById(R.id.radarStatusValue);
            if (status != null) {
                status.setText("Waiting for the active GPS / city location…");
            }
            return;
        }

        syncRadarLocation(state.getLatitude(), state.getLongitude(), force);
    }

    private void syncRadarLocation(double latitude, double longitude, boolean force) {
        if (radarViewModel == null) return;

        boolean changed = Double.isNaN(lastLatitude)
                || Double.isNaN(lastLongitude)
                || Math.abs(lastLatitude - latitude) > 0.005d
                || Math.abs(lastLongitude - longitude) > 0.005d;

        if (!force && !changed) return;

        lastLatitude = latitude;
        lastLongitude = longitude;
        radarViewModel.refresh(latitude, longitude, force);
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

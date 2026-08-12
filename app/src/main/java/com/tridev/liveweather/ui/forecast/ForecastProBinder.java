package com.tridev.liveweather.ui.forecast;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import com.tridev.liveweather.MainActivity;
import com.tridev.liveweather.ui.weather.WeatherViewModel;

import java.util.WeakHashMap;

/** Binds the Phase 19 renderer once per MainActivity instance without retaining it. */
public final class ForecastProBinder {

    private static final WeakHashMap<MainActivity, Boolean> BOUND = new WeakHashMap<>();

    private ForecastProBinder() {
    }

    public static synchronized void bind(@NonNull MainActivity activity) {
        if (BOUND.containsKey(activity)) return;

        ForecastProRenderer renderer = new ForecastProRenderer(activity);
        WeatherViewModel weatherViewModel = new ViewModelProvider(activity).get(WeatherViewModel.class);
        weatherViewModel.getWeatherState().observe(activity, renderer::render);
        BOUND.put(activity, Boolean.TRUE);
    }
}

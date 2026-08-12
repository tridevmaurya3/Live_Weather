package com.tridev.liveweather;

import android.Manifest;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.tridev.liveweather.core.location.DeviceLocationManager;
import com.tridev.liveweather.core.location.PlaceNameResolver;
import com.tridev.liveweather.data.local.AlertPreferences;
import com.tridev.liveweather.data.local.WallpaperPreferences;
import com.tridev.liveweather.domain.CityLocation;
import com.tridev.liveweather.domain.WeatherUiState;
import com.tridev.liveweather.notification.AlertNotificationManager;
import com.tridev.liveweather.ui.air.AirQualityViewModel;
import com.tridev.liveweather.ui.alert.AlertViewModel;
import com.tridev.liveweather.ui.alert.Phase8Renderer;
import com.tridev.liveweather.ui.city.CityScreenRenderer;
import com.tridev.liveweather.ui.city.CityViewModel;
import com.tridev.liveweather.ui.phase7.Phase7Renderer;
import com.tridev.liveweather.ui.sky.LiveSkyView;
import com.tridev.liveweather.ui.weather.Phase6Renderer;
import com.tridev.liveweather.ui.weather.WeatherFormatter;
import com.tridev.liveweather.ui.weather.WeatherScreenRenderer;
import com.tridev.liveweather.ui.weather.WeatherViewModel;
import com.tridev.liveweather.wallpaper.LiveWeatherWallpaperService;
import com.tridev.liveweather.widget.CompactWeatherWidgetProvider;
import com.tridev.liveweather.widget.WeatherWidgetUpdater;
import com.tridev.liveweather.widget.WideWeatherWidgetProvider;
import com.tridev.liveweather.worker.WallpaperWeatherScheduler;
import com.tridev.liveweather.worker.WeatherAlertScheduler;

import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String STATE_SELECTED_DESTINATION = "selected_destination";
    private static final String PREF_LOCATION_PERMISSION_REQUESTED = "location_permission_requested";

    private View pageContainer;
    private View pageHome;
    private View pageForecast;
    private View pageRadar;
    private View pageWallpaper;
    private View pageMore;
    private BottomNavigationView bottomNavigation;
    private TextView homeLocationValue;
    private TextView homeSyncStatus;
    private TextView forecastStatus;
    private LiveSkyView appLiveNatureBackground;
    private LiveSkyView forecastLiveSkyView;
    private LiveSkyView wallpaperLiveSkyView;

    private DeviceLocationManager deviceLocationManager;
    private PlaceNameResolver placeNameResolver;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private WeatherViewModel weatherViewModel;
    private AirQualityViewModel airQualityViewModel;
    private AlertViewModel alertViewModel;
    private WeatherScreenRenderer weatherScreenRenderer;
    private Phase6Renderer phase6Renderer;
    private Phase7Renderer phase7Renderer;
    private Phase8Renderer phase8Renderer;
    private CityViewModel cityViewModel;
    private CityScreenRenderer cityScreenRenderer;
    private WallpaperPreferences wallpaperPreferences;
    private AlertPreferences alertPreferences;
    private AlertNotificationManager alertNotificationManager;

    private double latestLatitude = Double.NaN;
    private double latestLongitude = Double.NaN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        registerLocationPermissionLauncher();
        registerNotificationPermissionLauncher();
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        deviceLocationManager = new DeviceLocationManager(this);
        placeNameResolver = new PlaceNameResolver(this);
        wallpaperPreferences = new WallpaperPreferences(this);
        alertPreferences = new AlertPreferences(this);
        alertNotificationManager = new AlertNotificationManager(this);

        bindViews();
        weatherScreenRenderer = new WeatherScreenRenderer(this);
        phase6Renderer = new Phase6Renderer(this);
        phase7Renderer = new Phase7Renderer(this);
        phase8Renderer = new Phase8Renderer(this);
        cityScreenRenderer = new CityScreenRenderer(this);

        applySystemInsets();
        setupBottomNavigation(savedInstanceState);
        setupQuickActions();
        setupWidgetEngine();
        setupWallpaperEngine();
        setupAirQualityEngine();
        setupAlertEngine();
        setupWeatherEngine();
        setupCityEngine();
        setupLocationEngine();

        WallpaperWeatherScheduler.schedule(this);
        WeatherAlertScheduler.schedule(this);
        handleLaunchIntent(getIntent());
    }

    private void registerLocationPermissionLauncher() {
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::handleLocationPermissionResult
        );
    }

    private void registerNotificationPermissionLauncher() {
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    alertPreferences.setNotificationsEnabled(granted);
                    updateAlertNotificationUi();
                }
        );
    }

    private void handleLocationPermissionResult(Map<String, Boolean> result) {
        boolean fineGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
        boolean coarseGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
        if (fineGranted || coarseGranted) requestCurrentLocation(false);
        else showLocationPermissionNeeded();
    }

    private void bindViews() {
        pageContainer = findViewById(R.id.pageContainer);
        pageHome = findViewById(R.id.pageHome);
        pageForecast = findViewById(R.id.pageForecast);
        pageRadar = findViewById(R.id.pageRadar);
        pageWallpaper = findViewById(R.id.pageWallpaper);
        pageMore = findViewById(R.id.pageMore);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        homeLocationValue = findViewById(R.id.homeLocationValue);
        homeSyncStatus = findViewById(R.id.homeSyncStatus);
        forecastStatus = findViewById(R.id.forecastStatus);
        appLiveNatureBackground = findViewById(R.id.appLiveNatureBackground);
        forecastLiveSkyView = findViewById(R.id.forecastLiveSkyView);
        wallpaperLiveSkyView = findViewById(R.id.wallpaperLiveSkyView);
    }

    private void applySystemInsets() {
        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            pageContainer.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            bottomNavigation.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setupBottomNavigation(Bundle savedInstanceState) {
        bottomNavigation.setOnItemSelectedListener(item -> {
            renderDestination(item.getItemId());
            return true;
        });
        int destinationId = R.id.nav_home;
        if (savedInstanceState != null) {
            destinationId = savedInstanceState.getInt(STATE_SELECTED_DESTINATION, R.id.nav_home);
        }
        bottomNavigation.setSelectedItemId(destinationId);
        renderDestination(destinationId);
    }

    private void setupQuickActions() {
        bindNavigationAction(R.id.homeForecastAction, R.id.nav_forecast);
        bindNavigationAction(R.id.homeRadarAction, R.id.nav_radar);
        bindNavigationAction(R.id.homeWallpaperAction, R.id.nav_wallpaper);

        View airAction = findViewById(R.id.homeAirAction);
        if (airAction != null) {
            airAction.setOnClickListener(view -> {
                performLightHaptic(view);
                bottomNavigation.setSelectedItemId(R.id.nav_more);
                phase7Renderer.scrollToAirQuality();
            });
        }

        View refreshAction = findViewById(R.id.homeRefreshAction);
        if (refreshAction != null) {
            refreshAction.setOnClickListener(view -> {
                performLightHaptic(view);
                refreshWeatherManually();
                refreshAirQualityManually();
                refreshAlertsManually();
            });
        }
    }

    private void setupWidgetEngine() {
        View widgetsAction = findViewById(R.id.moreWidgetsAction);
        if (widgetsAction == null) return;
        widgetsAction.setOnClickListener(view -> {
            performLightHaptic(view);
            String[] options = {
                    getString(R.string.widget_compact_name),
                    getString(R.string.widget_wide_name)
            };
            new AlertDialog.Builder(this)
                    .setTitle(R.string.widget_choose_title)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) requestPinWeatherWidget(CompactWeatherWidgetProvider.class);
                        else requestPinWeatherWidget(WideWeatherWidgetProvider.class);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    private void requestPinWeatherWidget(Class<?> providerClass) {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && manager.isRequestPinAppWidgetSupported()) {
            boolean requested = manager.requestPinAppWidget(
                    new ComponentName(this, providerClass),
                    null,
                    null
            );
            if (!requested) {
                Toast.makeText(this, R.string.widget_pin_request_failed, Toast.LENGTH_LONG).show();
            }
            return;
        }
        Toast.makeText(this, R.string.widget_pin_not_supported, Toast.LENGTH_LONG).show();
    }

    private void setupWallpaperEngine() {
        SwitchCompat rain = findViewById(R.id.switchRainParticles);
        SwitchCompat clouds = findViewById(R.id.switchCloudMovement);
        SwitchCompat lightning = findViewById(R.id.switchLightning);
        SwitchCompat snow = findViewById(R.id.switchSnow);
        SwitchCompat fog = findViewById(R.id.switchFog);
        SwitchCompat stars = findViewById(R.id.switchStars);
        SwitchCompat batteryAdaptive = findViewById(R.id.switchBatteryAdaptive);
        LiveSkyView preview = wallpaperLiveSkyView;

        WallpaperPreferences.Options saved = wallpaperPreferences.load();
        rain.setChecked(saved.isRain());
        clouds.setChecked(saved.isClouds());
        lightning.setChecked(saved.isLightning());
        snow.setChecked(saved.isSnow());
        fog.setChecked(saved.isFog());
        stars.setChecked(saved.isStars());
        batteryAdaptive.setChecked(saved.isBatteryAdaptive());
        preview.setRenderOptions(saved);
        appLiveNatureBackground.setRenderOptions(saved);

        android.widget.CompoundButton.OnCheckedChangeListener listener = (button, checked) -> {
            WallpaperPreferences.Options updated = new WallpaperPreferences.Options(
                    rain.isChecked(), clouds.isChecked(), lightning.isChecked(),
                    snow.isChecked(), fog.isChecked(), stars.isChecked(), batteryAdaptive.isChecked()
            );
            wallpaperPreferences.save(updated);
            preview.setRenderOptions(updated);
            appLiveNatureBackground.setRenderOptions(updated);
        };

        rain.setOnCheckedChangeListener(listener);
        clouds.setOnCheckedChangeListener(listener);
        lightning.setOnCheckedChangeListener(listener);
        snow.setOnCheckedChangeListener(listener);
        fog.setOnCheckedChangeListener(listener);
        stars.setOnCheckedChangeListener(listener);
        batteryAdaptive.setOnCheckedChangeListener(listener);

        View applyButton = findViewById(R.id.applyWallpaperButton);
        applyButton.setOnClickListener(view -> {
            performLightHaptic(view);
            openLiveWallpaperPreview();
        });
    }

    private void setupAirQualityEngine() {
        airQualityViewModel = new ViewModelProvider(this).get(AirQualityViewModel.class);
        phase7Renderer.setRefreshAirQualityAction(this::refreshAirQualityManually);
        airQualityViewModel.getState().observe(this, state -> {
            if (state == null) return;
            phase7Renderer.renderAirQuality(state);
            if (state.hasData() && state.getData() != null) {
                appLiveNatureBackground.setAirQualityData(state.getData());
                forecastLiveSkyView.setAirQualityData(state.getData());
                wallpaperLiveSkyView.setAirQualityData(state.getData());
            } else {
                appLiveNatureBackground.clearAirQualityData();
                forecastLiveSkyView.clearAirQualityData();
                wallpaperLiveSkyView.clearAirQualityData();
            }
        });
    }

    private void setupAlertEngine() {
        alertViewModel = new ViewModelProvider(this).get(AlertViewModel.class);
        phase8Renderer.setCallbacks(
                this::openAlertsCenter,
                this::refreshAlertsManually,
                this::toggleAlertNotifications
        );
        alertViewModel.getState().observe(this, state -> {
            if (state != null) phase8Renderer.render(state);
        });
        updateAlertNotificationUi();
    }

    private void toggleAlertNotifications() {
        if (alertPreferences.isNotificationsEnabled()
                && alertNotificationManager.canPostNotifications()) {
            alertPreferences.setNotificationsEnabled(false);
            updateAlertNotificationUi();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }

        alertPreferences.setNotificationsEnabled(true);
        updateAlertNotificationUi();
    }

    private void updateAlertNotificationUi() {
        if (phase8Renderer == null || alertPreferences == null || alertNotificationManager == null) return;
        phase8Renderer.setNotificationsEnabled(
                alertPreferences.isNotificationsEnabled(),
                alertNotificationManager.canPostNotifications()
        );
    }

    private void openAlertsCenter() {
        bottomNavigation.setSelectedItemId(R.id.nav_more);
        phase8Renderer.scrollToAlerts();
    }

    private void openLiveWallpaperPreview() {
        ComponentName component = new ComponentName(this, LiveWeatherWallpaperService.class);
        Intent previewIntent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        previewIntent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component);
        try {
            startActivity(previewIntent);
        } catch (ActivityNotFoundException exception) {
            Intent chooserIntent = new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER);
            try {
                startActivity(chooserIntent);
            } catch (ActivityNotFoundException ignored) {
            }
        }
    }

    private void bindNavigationAction(int viewId, int destinationId) {
        View action = findViewById(viewId);
        if (action == null) return;
        action.setOnClickListener(view -> {
            performLightHaptic(view);
            bottomNavigation.setSelectedItemId(destinationId);
        });
    }

    private void performLightHaptic(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void setupWeatherEngine() {
        weatherViewModel = new ViewModelProvider(this).get(WeatherViewModel.class);
        weatherViewModel.getWeatherState().observe(this, state -> {
            if (state == null) return;

            weatherScreenRenderer.render(state);
            phase6Renderer.render(state);
            phase7Renderer.renderCelestial(state);
            alertViewModel.refresh(state, false);

            if (state.hasWeather() && state.getWeather() != null
                    && !Double.isNaN(state.getLatitude()) && !Double.isNaN(state.getLongitude())) {
                appLiveNatureBackground.setWeatherData(
                        state.getWeather(), state.getLatitude(), state.getLongitude()
                );
                airQualityViewModel.refresh(state.getLatitude(), state.getLongitude(), false);
                WeatherWidgetUpdater.updateAll(this);
            } else {
                appLiveNatureBackground.clearWeatherData();
            }

            if (Double.isNaN(latestLatitude)
                    && state.hasWeather()
                    && cityViewModel == null
                    && !Double.isNaN(state.getLatitude())
                    && !Double.isNaN(state.getLongitude())) {
                homeLocationValue.setText(
                        WeatherFormatter.savedCoordinates(state.getLatitude(), state.getLongitude())
                );
            }
        });

        homeSyncStatus.setOnClickListener(view -> {
            performLightHaptic(view);
            refreshWeatherManually();
            refreshAirQualityManually();
            refreshAlertsManually();
        });
        forecastStatus.setOnClickListener(view -> {
            performLightHaptic(view);
            refreshWeatherManually();
            refreshAirQualityManually();
            refreshAlertsManually();
        });
    }

    private void setupCityEngine() {
        cityViewModel = new ViewModelProvider(this).get(CityViewModel.class);
        cityScreenRenderer.setCallbacks(new CityScreenRenderer.Callbacks() {
            @Override public void onSearch(String query) { cityViewModel.searchCities(query); }
            @Override public void onUseCity(CityLocation city) {
                cityViewModel.selectCity(city);
                activateCity(city, true, true);
            }
            @Override public void onSaveCity(CityLocation city) { cityViewModel.saveCity(city); }
            @Override public void onRemoveCity(CityLocation city) {
                boolean wasSelected = cityViewModel.isSelected(city);
                cityViewModel.removeCity(city);
                if (wasSelected) {
                    cityViewModel.useCurrentLocation();
                    activateCurrentLocation();
                }
            }
            @Override public void onUseCurrentLocation() {
                cityViewModel.useCurrentLocation();
                activateCurrentLocation();
            }
        });
        cityViewModel.getCityState().observe(this, state -> {
            if (state != null) cityScreenRenderer.render(state);
        });
    }

    private void setupLocationEngine() {
        homeLocationValue.setOnClickListener(view -> {
            performLightHaptic(view);
            CityLocation selectedCity = cityViewModel.getSelectedCity();
            if (selectedCity != null) bottomNavigation.setSelectedItemId(R.id.nav_more);
            else requestLocationAccess();
        });

        CityLocation selectedCity = cityViewModel.getSelectedCity();
        if (selectedCity != null) {
            activateCity(selectedCity, false, false);
            return;
        }
        if (deviceLocationManager.hasLocationPermission()) {
            requestCurrentLocation(false);
            return;
        }

        boolean permissionRequestedBefore = getPreferences(Context.MODE_PRIVATE)
                .getBoolean(PREF_LOCATION_PERMISSION_REQUESTED, false);
        if (!permissionRequestedBefore) {
            getPreferences(Context.MODE_PRIVATE).edit()
                    .putBoolean(PREF_LOCATION_PERMISSION_REQUESTED, true)
                    .apply();
            requestLocationPermission();
        } else {
            showLocationPermissionNeeded();
        }
    }

    private void activateCity(CityLocation city, boolean force, boolean navigateHome) {
        latestLatitude = city.getLatitude();
        latestLongitude = city.getLongitude();
        phase6Renderer.clearLocationAccuracy();
        homeLocationValue.setText(city.getDisplayName());
        weatherViewModel.refreshWeather(latestLatitude, latestLongitude, force);
        airQualityViewModel.refresh(latestLatitude, latestLongitude, force);
        if (navigateHome) bottomNavigation.setSelectedItemId(R.id.nav_home);
    }

    private void activateCurrentLocation() {
        latestLatitude = Double.NaN;
        latestLongitude = Double.NaN;
        requestLocationAccess();
        bottomNavigation.setSelectedItemId(R.id.nav_home);
    }

    private void requestLocationAccess() {
        if (deviceLocationManager.hasLocationPermission()) requestCurrentLocation(false);
        else requestLocationPermission();
    }

    private void requestLocationPermission() {
        locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void requestCurrentLocation(boolean forceWeatherRefresh) {
        if (cityViewModel.getSelectedCity() != null) return;

        homeLocationValue.setText(R.string.home_location_requesting);
        if (!hasWeatherData()) homeSyncStatus.setText(R.string.home_sync_location_requesting);

        deviceLocationManager.requestCurrentLocation(new DeviceLocationManager.LocationCallback() {
            @Override
            public void onLocation(Location location) {
                latestLatitude = location.getLatitude();
                latestLongitude = location.getLongitude();
                double resolvedLatitude = latestLatitude;
                double resolvedLongitude = latestLongitude;
                float accuracy = location.hasAccuracy() ? location.getAccuracy() : Float.NaN;
                boolean precise = deviceLocationManager.hasFineLocationPermission();

                runOnUiThread(() -> {
                    if (cityViewModel.getSelectedCity() != null) return;
                    phase6Renderer.setLocationAccuracy(accuracy, precise);
                    homeLocationValue.setText(
                            WeatherFormatter.coordinates(resolvedLatitude, resolvedLongitude)
                    );
                    homeSyncStatus.setText(R.string.home_sync_location_ready);
                    weatherViewModel.refreshWeather(
                            resolvedLatitude, resolvedLongitude, forceWeatherRefresh
                    );
                    airQualityViewModel.refresh(
                            resolvedLatitude, resolvedLongitude, forceWeatherRefresh
                    );
                });

                placeNameResolver.resolve(
                        resolvedLatitude,
                        resolvedLongitude,
                        label -> runOnUiThread(() -> {
                            if (label == null || label.trim().isEmpty()) return;
                            if (cityViewModel.getSelectedCity() != null) return;
                            if (Math.abs(latestLatitude - resolvedLatitude) > 0.001d
                                    || Math.abs(latestLongitude - resolvedLongitude) > 0.001d) return;
                            homeLocationValue.setText(label);
                        })
                );
            }

            @Override
            public void onError(
                    DeviceLocationManager.LocationError error,
                    String message,
                    Throwable throwable
            ) {
                runOnUiThread(() -> renderLocationError(error));
            }
        });
    }

    private void refreshWeatherManually() {
        CityLocation selectedCity = cityViewModel.getSelectedCity();
        if (selectedCity != null) {
            latestLatitude = selectedCity.getLatitude();
            latestLongitude = selectedCity.getLongitude();
            weatherViewModel.refreshWeather(latestLatitude, latestLongitude, true);
            return;
        }
        if (deviceLocationManager.hasLocationPermission()) {
            requestCurrentLocation(true);
            return;
        }
        requestLocationPermission();
    }

    private void refreshAirQualityManually() {
        CityLocation selectedCity = cityViewModel == null ? null : cityViewModel.getSelectedCity();
        if (selectedCity != null) {
            airQualityViewModel.refresh(selectedCity.getLatitude(), selectedCity.getLongitude(), true);
            return;
        }
        if (!Double.isNaN(latestLatitude) && !Double.isNaN(latestLongitude)) {
            airQualityViewModel.refresh(latestLatitude, latestLongitude, true);
            return;
        }
        if (weatherViewModel != null) {
            WeatherUiState state = weatherViewModel.getWeatherState().getValue();
            if (state != null && !Double.isNaN(state.getLatitude()) && !Double.isNaN(state.getLongitude())) {
                airQualityViewModel.refresh(state.getLatitude(), state.getLongitude(), true);
            }
        }
    }

    private void refreshAlertsManually() {
        if (weatherViewModel == null || alertViewModel == null) return;
        WeatherUiState state = weatherViewModel.getWeatherState().getValue();
        if (state != null && state.hasWeather()) {
            alertViewModel.refresh(state, true);
        }
    }

    private void renderLocationError(DeviceLocationManager.LocationError error) {
        if (cityViewModel.getSelectedCity() != null) return;
        if (error == DeviceLocationManager.LocationError.PERMISSION_REQUIRED) {
            showLocationPermissionNeeded();
            return;
        }
        if (error == DeviceLocationManager.LocationError.PLAY_SERVICES_UNAVAILABLE) {
            homeLocationValue.setText(R.string.home_location_play_services_unavailable);
            if (!hasWeatherData()) homeSyncStatus.setText(R.string.home_sync_location_service_unavailable);
            return;
        }
        homeLocationValue.setText(R.string.home_location_unavailable);
        if (!hasWeatherData()) homeSyncStatus.setText(R.string.home_sync_location_unavailable);
    }

    private void showLocationPermissionNeeded() {
        if (cityViewModel.getSelectedCity() != null) return;
        homeLocationValue.setText(R.string.home_location_permission_needed);
        if (!hasWeatherData()) homeSyncStatus.setText(R.string.home_sync_location_permission_denied);
    }

    private boolean hasWeatherData() {
        if (weatherViewModel == null) return false;
        WeatherUiState state = weatherViewModel.getWeatherState().getValue();
        return state != null && state.hasWeather();
    }

    private void handleLaunchIntent(Intent intent) {
        if (intent == null) return;
        if (intent.getBooleanExtra("open_weather_alerts", false)) {
            bottomNavigation.post(this::openAlertsCenter);
            return;
        }

        String destination = intent.getStringExtra(WeatherWidgetUpdater.EXTRA_OPEN_DESTINATION);
        if (WeatherWidgetUpdater.DESTINATION_FORECAST.equals(destination)) {
            bottomNavigation.post(() -> bottomNavigation.setSelectedItemId(R.id.nav_forecast));
        } else if (WeatherWidgetUpdater.DESTINATION_HOME.equals(destination)) {
            bottomNavigation.post(() -> bottomNavigation.setSelectedItemId(R.id.nav_home));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
    }

    private void renderDestination(int itemId) {
        pageHome.setVisibility(itemId == R.id.nav_home ? View.VISIBLE : View.GONE);
        pageForecast.setVisibility(itemId == R.id.nav_forecast ? View.VISIBLE : View.GONE);
        pageRadar.setVisibility(itemId == R.id.nav_radar ? View.VISIBLE : View.GONE);
        pageWallpaper.setVisibility(itemId == R.id.nav_wallpaper ? View.VISIBLE : View.GONE);
        pageMore.setVisibility(itemId == R.id.nav_more ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_SELECTED_DESTINATION, bottomNavigation.getSelectedItemId());
        super.onSaveInstanceState(outState);
    }
}

package com.tridev.liveweather;

import android.Manifest;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.tridev.liveweather.core.location.DeviceLocationManager;
import com.tridev.liveweather.domain.WeatherUiState;
import com.tridev.liveweather.ui.weather.WeatherFormatter;
import com.tridev.liveweather.ui.weather.WeatherScreenRenderer;
import com.tridev.liveweather.ui.weather.WeatherViewModel;

import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String STATE_SELECTED_DESTINATION = "selected_destination";
    private static final String PREF_LOCATION_PERMISSION_REQUESTED =
            "location_permission_requested";

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

    private DeviceLocationManager deviceLocationManager;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private WeatherViewModel weatherViewModel;
    private WeatherScreenRenderer weatherScreenRenderer;

    private double latestLatitude = Double.NaN;
    private double latestLongitude = Double.NaN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        registerLocationPermissionLauncher();
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        deviceLocationManager = new DeviceLocationManager(this);

        bindViews();
        weatherScreenRenderer = new WeatherScreenRenderer(this);
        applySystemInsets();
        setupBottomNavigation(savedInstanceState);
        setupQuickActions();
        setupWeatherEngine();
        setupLocationEngine();
    }

    private void registerLocationPermissionLauncher() {
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::handleLocationPermissionResult
        );
    }

    private void handleLocationPermissionResult(Map<String, Boolean> result) {
        boolean fineGranted = Boolean.TRUE.equals(
                result.get(Manifest.permission.ACCESS_FINE_LOCATION)
        );
        boolean coarseGranted = Boolean.TRUE.equals(
                result.get(Manifest.permission.ACCESS_COARSE_LOCATION)
        );

        if (fineGranted || coarseGranted) {
            requestCurrentLocation();
        } else {
            showLocationPermissionNeeded();
        }
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
    }

    private void applySystemInsets() {
        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            pageContainer.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    0
            );

            bottomNavigation.setPadding(
                    systemBars.left,
                    0,
                    systemBars.right,
                    systemBars.bottom
            );

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
            destinationId = savedInstanceState.getInt(
                    STATE_SELECTED_DESTINATION,
                    R.id.nav_home
            );
        }

        bottomNavigation.setSelectedItemId(destinationId);
        renderDestination(destinationId);
    }

    private void setupQuickActions() {
        View wallpaperAction = findViewById(R.id.homeWallpaperAction);
        if (wallpaperAction != null) {
            wallpaperAction.setOnClickListener(view ->
                    bottomNavigation.setSelectedItemId(R.id.nav_wallpaper)
            );
        }
    }

    private void setupWeatherEngine() {
        weatherViewModel = new ViewModelProvider(this).get(WeatherViewModel.class);
        weatherViewModel.getWeatherState().observe(this, state -> {
            if (state == null) {
                return;
            }

            weatherScreenRenderer.render(state);

            if (Double.isNaN(latestLatitude)
                    && state.hasWeather()
                    && !Double.isNaN(state.getLatitude())
                    && !Double.isNaN(state.getLongitude())) {
                homeLocationValue.setText(
                        WeatherFormatter.savedCoordinates(
                                state.getLatitude(),
                                state.getLongitude()
                        )
                );
            }
        });

        homeSyncStatus.setOnClickListener(view -> refreshWeatherManually());
        forecastStatus.setOnClickListener(view -> refreshWeatherManually());
    }

    private void setupLocationEngine() {
        homeLocationValue.setOnClickListener(view -> requestLocationAccess());

        if (deviceLocationManager.hasLocationPermission()) {
            requestCurrentLocation();
            return;
        }

        boolean permissionRequestedBefore = getPreferences(Context.MODE_PRIVATE)
                .getBoolean(PREF_LOCATION_PERMISSION_REQUESTED, false);

        if (!permissionRequestedBefore) {
            getPreferences(Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(PREF_LOCATION_PERMISSION_REQUESTED, true)
                    .apply();
            requestLocationPermission();
        } else {
            showLocationPermissionNeeded();
        }
    }

    private void requestLocationAccess() {
        if (deviceLocationManager.hasLocationPermission()) {
            requestCurrentLocation();
        } else {
            requestLocationPermission();
        }
    }

    private void requestLocationPermission() {
        locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void requestCurrentLocation() {
        homeLocationValue.setText(R.string.home_location_requesting);
        if (!hasWeatherData()) {
            homeSyncStatus.setText(R.string.home_sync_location_requesting);
        }

        deviceLocationManager.requestCurrentLocation(
                new DeviceLocationManager.LocationCallback() {
                    @Override
                    public void onLocation(Location location) {
                        latestLatitude = location.getLatitude();
                        latestLongitude = location.getLongitude();

                        runOnUiThread(() -> {
                            homeLocationValue.setText(
                                    WeatherFormatter.coordinates(
                                            latestLatitude,
                                            latestLongitude
                                    )
                            );
                            homeSyncStatus.setText(R.string.home_sync_location_ready);
                            weatherViewModel.refreshWeather(
                                    latestLatitude,
                                    latestLongitude,
                                    false
                            );
                        });
                    }

                    @Override
                    public void onError(
                            DeviceLocationManager.LocationError error,
                            String message,
                            Throwable throwable
                    ) {
                        runOnUiThread(() -> renderLocationError(error));
                    }
                }
        );
    }

    private void refreshWeatherManually() {
        if (!Double.isNaN(latestLatitude) && !Double.isNaN(latestLongitude)) {
            weatherViewModel.refreshWeather(latestLatitude, latestLongitude, true);
            return;
        }

        WeatherUiState state = weatherViewModel.getWeatherState().getValue();
        if (state != null
                && state.hasWeather()
                && !Double.isNaN(state.getLatitude())
                && !Double.isNaN(state.getLongitude())) {
            weatherViewModel.refreshWeather(
                    state.getLatitude(),
                    state.getLongitude(),
                    true
            );
            return;
        }

        requestLocationAccess();
    }

    private void renderLocationError(DeviceLocationManager.LocationError error) {
        if (error == DeviceLocationManager.LocationError.PERMISSION_REQUIRED) {
            showLocationPermissionNeeded();
            return;
        }

        if (error == DeviceLocationManager.LocationError.PLAY_SERVICES_UNAVAILABLE) {
            homeLocationValue.setText(
                    R.string.home_location_play_services_unavailable
            );
            if (!hasWeatherData()) {
                homeSyncStatus.setText(
                        R.string.home_sync_location_service_unavailable
                );
            }
            return;
        }

        homeLocationValue.setText(R.string.home_location_unavailable);
        if (!hasWeatherData()) {
            homeSyncStatus.setText(R.string.home_sync_location_unavailable);
        }
    }

    private void showLocationPermissionNeeded() {
        homeLocationValue.setText(R.string.home_location_permission_needed);
        if (!hasWeatherData()) {
            homeSyncStatus.setText(R.string.home_sync_location_permission_denied);
        }
    }

    private boolean hasWeatherData() {
        if (weatherViewModel == null) {
            return false;
        }
        WeatherUiState state = weatherViewModel.getWeatherState().getValue();
        return state != null && state.hasWeather();
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
        outState.putInt(
                STATE_SELECTED_DESTINATION,
                bottomNavigation.getSelectedItemId()
        );
        super.onSaveInstanceState(outState);
    }
}

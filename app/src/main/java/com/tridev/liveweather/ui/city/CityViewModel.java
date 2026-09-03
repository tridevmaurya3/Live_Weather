package com.tridev.liveweather.ui.city;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tridev.liveweather.data.local.ActiveWeatherSnapshotStore;
import com.tridev.liveweather.data.local.SavedCityStore;
import com.tridev.liveweather.data.remote.dto.GeocodingResponse;
import com.tridev.liveweather.domain.CityLocation;
import com.tridev.liveweather.domain.CityUiState;
import com.tridev.liveweather.repository.CitySearchRepository;
import com.tridev.liveweather.widget.WeatherWidgetUpdater;

import java.util.Collections;
import java.util.List;

import retrofit2.Call;

public final class CityViewModel extends AndroidViewModel {

    private final MutableLiveData<CityUiState> cityState = new MutableLiveData<>();
    private final CitySearchRepository citySearchRepository;
    private final SavedCityStore savedCityStore;
    private final ActiveWeatherSnapshotStore activeSnapshotStore;

    private Call<GeocodingResponse> activeSearchCall;

    public CityViewModel(@NonNull Application application) {
        super(application);
        citySearchRepository = new CitySearchRepository();
        savedCityStore = new SavedCityStore(application);
        activeSnapshotStore = new ActiveWeatherSnapshotStore(application);
        publish(
                false,
                Collections.emptyList(),
                savedCityStore.getSelectedCity(),
                null
        );
    }

    @NonNull
    public LiveData<CityUiState> getCityState() {
        return cityState;
    }

    @Nullable
    public CityLocation getSelectedCity() {
        return savedCityStore.getSelectedCity();
    }

    public boolean isSelected(@NonNull CityLocation city) {
        CityLocation selected = savedCityStore.getSelectedCity();
        return selected != null && selected.sameIdentity(city);
    }

    public void searchCities(@NonNull String query) {
        String cleanQuery = query.trim();
        if (cleanQuery.length() < 2) {
            publish(
                    false,
                    Collections.emptyList(),
                    savedCityStore.getSelectedCity(),
                    "Type at least 2 characters to search."
            );
            return;
        }

        if (activeSearchCall != null) {
            activeSearchCall.cancel();
            activeSearchCall = null;
        }

        CityUiState previous = cityState.getValue();
        List<CityLocation> previousResults = previous == null
                ? Collections.emptyList()
                : previous.getSearchResults();

        publish(true, previousResults, savedCityStore.getSelectedCity(), "Searching cities…");

        activeSearchCall = citySearchRepository.searchCities(
                cleanQuery,
                new CitySearchRepository.SearchCallback() {
                    @Override
                    public void onSuccess(@NonNull List<CityLocation> cities) {
                        activeSearchCall = null;
                        publish(
                                false,
                                cities,
                                savedCityStore.getSelectedCity(),
                                cities.isEmpty() ? "No matching cities found." : null
                        );
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        activeSearchCall = null;
                        publish(
                                false,
                                Collections.emptyList(),
                                savedCityStore.getSelectedCity(),
                                message
                        );
                    }
                }
        );
    }

    public void saveCity(@NonNull CityLocation city) {
        savedCityStore.saveCity(city);
        publishCurrent("Saved " + city.getDisplayName() + ".");
    }

    public void removeCity(@NonNull CityLocation city) {
        savedCityStore.removeCity(city);
        publishCurrent("Removed " + city.getDisplayName() + ".");
    }

    public void selectCity(@NonNull CityLocation city) {
        savedCityStore.selectCity(city);
        activeSnapshotStore.ensureActiveTarget(
                city.getLatitude(),
                city.getLongitude(),
                city.getDisplayName()
        );
        WeatherWidgetUpdater.updateAll(getApplication());
        publishCurrent("Showing weather for " + city.getDisplayName() + ".");
    }

    public void useCurrentLocation() {
        savedCityStore.selectCity(null);
        activeSnapshotStore.clearActiveTarget();
        WeatherWidgetUpdater.updateAll(getApplication());
        publishCurrent("Using device current location.");
    }

    private void publishCurrent(@Nullable String message) {
        CityUiState previous = cityState.getValue();
        List<CityLocation> results = previous == null
                ? Collections.emptyList()
                : previous.getSearchResults();
        publish(false, results, savedCityStore.getSelectedCity(), message);
    }

    private void publish(
            boolean loading,
            @NonNull List<CityLocation> results,
            @Nullable CityLocation selectedCity,
            @Nullable String message
    ) {
        cityState.setValue(new CityUiState(
                loading,
                results,
                savedCityStore.loadSavedCities(),
                selectedCity,
                message
        ));
    }

    @Override
    protected void onCleared() {
        if (activeSearchCall != null) {
            activeSearchCall.cancel();
            activeSearchCall = null;
        }
        super.onCleared();
    }
}

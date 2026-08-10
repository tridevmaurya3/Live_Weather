package com.tridev.liveweather.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CityUiState {

    private final boolean loading;
    private final List<CityLocation> searchResults;
    private final List<CityLocation> savedCities;
    private final CityLocation selectedCity;
    private final String message;

    public CityUiState(
            boolean loading,
            @Nullable List<CityLocation> searchResults,
            @Nullable List<CityLocation> savedCities,
            @Nullable CityLocation selectedCity,
            @Nullable String message
    ) {
        this.loading = loading;
        this.searchResults = searchResults == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(searchResults));
        this.savedCities = savedCities == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(savedCities));
        this.selectedCity = selectedCity;
        this.message = message;
    }

    public boolean isLoading() {
        return loading;
    }

    @NonNull
    public List<CityLocation> getSearchResults() {
        return searchResults;
    }

    @NonNull
    public List<CityLocation> getSavedCities() {
        return savedCities;
    }

    @Nullable
    public CityLocation getSelectedCity() {
        return selectedCity;
    }

    @Nullable
    public String getMessage() {
        return message;
    }
}

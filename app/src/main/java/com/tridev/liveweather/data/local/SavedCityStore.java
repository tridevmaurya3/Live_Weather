package com.tridev.liveweather.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.tridev.liveweather.domain.CityLocation;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class SavedCityStore {

    private static final String PREFS_NAME = "live_weather_cities";
    private static final String KEY_SAVED_CITIES = "saved_cities";
    private static final String KEY_SELECTED_CITY = "selected_city";
    private static final int MAX_SAVED_CITIES = 20;

    private final SharedPreferences preferences;
    private final Gson gson;
    private final Type cityListType;

    public SavedCityStore(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        cityListType = new TypeToken<ArrayList<CityLocation>>() { }.getType();
    }

    @NonNull
    public List<CityLocation> loadSavedCities() {
        String json = preferences.getString(KEY_SAVED_CITIES, null);
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            List<CityLocation> cities = gson.fromJson(json, cityListType);
            return cities == null ? new ArrayList<>() : new ArrayList<>(cities);
        } catch (JsonSyntaxException exception) {
            preferences.edit().remove(KEY_SAVED_CITIES).apply();
            return new ArrayList<>();
        }
    }

    public void saveCity(@NonNull CityLocation city) {
        List<CityLocation> cities = loadSavedCities();
        boolean found = false;

        for (int index = 0; index < cities.size(); index++) {
            CityLocation existing = cities.get(index);
            if (existing != null && existing.sameIdentity(city)) {
                cities.set(index, city);
                found = true;
                break;
            }
        }

        if (!found) {
            cities.add(0, city);
        }

        while (cities.size() > MAX_SAVED_CITIES) {
            cities.remove(cities.size() - 1);
        }

        preferences.edit()
                .putString(KEY_SAVED_CITIES, gson.toJson(cities))
                .apply();
    }

    public void removeCity(@NonNull CityLocation city) {
        List<CityLocation> cities = loadSavedCities();
        List<CityLocation> updated = new ArrayList<>();

        for (CityLocation existing : cities) {
            if (existing == null || !existing.sameIdentity(city)) {
                updated.add(existing);
            }
        }

        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_SAVED_CITIES, gson.toJson(updated));

        CityLocation selectedCity = getSelectedCity();
        if (selectedCity != null && selectedCity.sameIdentity(city)) {
            editor.remove(KEY_SELECTED_CITY);
        }
        editor.apply();
    }

    public boolean isSaved(@NonNull CityLocation city) {
        for (CityLocation existing : loadSavedCities()) {
            if (existing != null && existing.sameIdentity(city)) {
                return true;
            }
        }
        return false;
    }

    public void selectCity(@Nullable CityLocation city) {
        SharedPreferences.Editor editor = preferences.edit();
        if (city == null) {
            editor.remove(KEY_SELECTED_CITY);
        } else {
            editor.putString(KEY_SELECTED_CITY, gson.toJson(city));
        }
        editor.apply();
    }

    @Nullable
    public CityLocation getSelectedCity() {
        String json = preferences.getString(KEY_SELECTED_CITY, null);
        if (json == null || json.trim().isEmpty()) {
            return null;
        }

        try {
            return gson.fromJson(json, CityLocation.class);
        } catch (JsonSyntaxException exception) {
            preferences.edit().remove(KEY_SELECTED_CITY).apply();
            return null;
        }
    }
}

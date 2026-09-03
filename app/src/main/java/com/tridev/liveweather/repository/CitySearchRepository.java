package com.tridev.liveweather.repository;

import androidx.annotation.NonNull;

import com.tridev.liveweather.data.remote.api.GeocodingApiClient;
import com.tridev.liveweather.data.remote.api.OpenMeteoGeocodingApiService;
import com.tridev.liveweather.data.remote.api.NetworkFailureMessage;
import com.tridev.liveweather.data.remote.dto.GeocodingResponse;
import com.tridev.liveweather.domain.CityLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class CitySearchRepository {

    private static final int RESULT_COUNT = 8;
    private static final String FORMAT_JSON = "json";

    private final OpenMeteoGeocodingApiService apiService;

    public CitySearchRepository() {
        apiService = GeocodingApiClient.getApiService();
    }

    public Call<GeocodingResponse> searchCities(
            @NonNull String query,
            @NonNull SearchCallback callback
    ) {
        String language = Locale.getDefault().getLanguage();
        if (language == null || language.trim().isEmpty()) {
            language = "en";
        }

        Call<GeocodingResponse> call = apiService.searchLocations(
                query.trim(),
                RESULT_COUNT,
                language,
                FORMAT_JSON
        );

        call.enqueue(new Callback<GeocodingResponse>() {
            @Override
            public void onResponse(
                    @NonNull Call<GeocodingResponse> call,
                    @NonNull Response<GeocodingResponse> response
            ) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError("City search failed with HTTP " + response.code());
                    return;
                }

                List<CityLocation> cities = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                List<GeocodingResponse.Result> results = response.body().getResults();

                if (results != null) {
                    for (GeocodingResponse.Result result : results) {
                        if (result == null
                                || result.getName() == null
                                || result.getLatitude() == null
                                || result.getLongitude() == null) {
                            continue;
                        }

                        String id = result.getId() != null
                                ? "openmeteo_" + result.getId()
                                : CityLocation.stableCoordinateKey(
                                        result.getLatitude(),
                                        result.getLongitude()
                                );

                        CityLocation city = new CityLocation(
                                id,
                                result.getName(),
                                firstNonEmpty(result.getAdmin1(), result.getAdmin2()),
                                result.getCountry(),
                                result.getTimezone(),
                                result.getLatitude(),
                                result.getLongitude()
                        );

                        String stableKey = city.getId();
                        if (seen.add(stableKey)) {
                            cities.add(city);
                        }
                    }
                }

                callback.onSuccess(cities);
            }

            @Override
            public void onFailure(
                    @NonNull Call<GeocodingResponse> call,
                    @NonNull Throwable throwable
            ) {
                if (call.isCanceled()) {
                    return;
                }
                callback.onError(NetworkFailureMessage.forService("City search", throwable));
            }
        });

        return call;
    }

    private String firstNonEmpty(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        return second;
    }

    public interface SearchCallback {
        void onSuccess(@NonNull List<CityLocation> cities);

        void onError(@NonNull String message);
    }
}

package com.tridev.liveweather.ui.radar;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.tridev.liveweather.data.remote.dto.RadarFieldPointResponse;
import com.tridev.liveweather.data.remote.dto.RainViewerResponse;
import com.tridev.liveweather.data.repository.RadarRepository;

import java.util.Collections;
import java.util.List;

public final class RadarViewModel extends ViewModel {

    private final MutableLiveData<RadarUiState> state = new MutableLiveData<>();
    private final RadarRepository repository = new RadarRepository();

    private long requestGeneration;
    private double latitude = Double.NaN;
    private double longitude = Double.NaN;
    private boolean loadingRadar;
    private boolean loadingField;
    private RainViewerResponse radar;
    private List<RadarFieldPointResponse> field = Collections.emptyList();
    private String radarError;
    private String fieldError;
    private boolean radarFromCache;
    private boolean fieldFromCache;

    public LiveData<RadarUiState> getState() {
        return state;
    }

    public void refresh(double latitude, double longitude, boolean force) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) return;

        boolean sameLocation = !Double.isNaN(this.latitude)
                && Math.abs(this.latitude - latitude) < 0.02d
                && Math.abs(this.longitude - longitude) < 0.02d;

        long generation = ++requestGeneration;
        this.latitude = latitude;
        this.longitude = longitude;
        loadingRadar = true;
        loadingField = true;
        radarError = null;
        fieldError = null;

        if (force || !sameLocation) {
            radar = null;
            field = Collections.emptyList();
            radarFromCache = false;
            fieldFromCache = false;
        }
        publish();

        repository.loadRadar(force, new RadarRepository.ResultCallback<RainViewerResponse>() {
            @Override
            public void onSuccess(@NonNull RainViewerResponse value, boolean fromCache) {
                if (generation != requestGeneration) return;
                radar = value;
                radarFromCache = fromCache;
                radarError = null;
                loadingRadar = false;
                publish();
            }

            @Override
            public void onError(@NonNull String message, Throwable throwable) {
                if (generation != requestGeneration) return;
                radarError = message;
                loadingRadar = false;
                publish();
            }
        });

        repository.loadField(latitude, longitude, force,
                new RadarRepository.ResultCallback<List<RadarFieldPointResponse>>() {
                    @Override
                    public void onSuccess(
                            @NonNull List<RadarFieldPointResponse> value,
                            boolean fromCache
                    ) {
                        if (generation != requestGeneration) return;
                        field = value;
                        fieldFromCache = fromCache;
                        fieldError = null;
                        loadingField = false;
                        publish();
                    }

                    @Override
                    public void onError(@NonNull String message, Throwable throwable) {
                        if (generation != requestGeneration) return;
                        fieldError = message;
                        loadingField = false;
                        publish();
                    }
                });
    }

    private void publish() {
        RadarUiState snapshot = new RadarUiState(
                latitude,
                longitude,
                loadingRadar,
                loadingField,
                radar,
                field,
                radarError,
                fieldError,
                radarFromCache,
                fieldFromCache
        );
        state.postValue(snapshot);
    }
}

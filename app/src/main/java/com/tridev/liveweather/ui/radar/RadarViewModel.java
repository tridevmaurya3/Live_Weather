package com.tridev.liveweather.ui.radar;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.tridev.liveweather.data.remote.dto.RadarFieldPointResponse;
import com.tridev.liveweather.data.remote.dto.RainViewerResponse;
import com.tridev.liveweather.data.repository.RadarRepository;

import java.util.List;

public final class RadarViewModel extends ViewModel {

    private final MutableLiveData<RadarUiState> state = new MutableLiveData<>();
    private final RadarRepository repository = new RadarRepository();
    private long requestGeneration;

    public LiveData<RadarUiState> getState() {
        return state;
    }

    public void refresh(double latitude, double longitude, boolean force) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) return;

        long generation = ++requestGeneration;
        RadarUiState previous = state.getValue();
        RadarUiState loading = RadarUiState.loading(latitude, longitude);
        if (!force && previous != null
                && Math.abs(previous.getLatitude() - latitude) < 0.02d
                && Math.abs(previous.getLongitude() - longitude) < 0.02d) {
            loading = new RadarUiState(
                    latitude,
                    longitude,
                    true,
                    true,
                    previous.getRadar(),
                    previous.getField(),
                    null,
                    null,
                    previous.isRadarFromCache(),
                    previous.isFieldFromCache()
            );
        }
        state.setValue(loading);

        repository.loadRadar(force, new RadarRepository.ResultCallback<RainViewerResponse>() {
            @Override
            public void onSuccess(@NonNull RainViewerResponse value, boolean fromCache) {
                if (generation != requestGeneration) return;
                RadarUiState current = state.getValue();
                if (current == null) current = RadarUiState.loading(latitude, longitude);
                state.postValue(current.withRadar(value, null, fromCache));
            }

            @Override
            public void onError(@NonNull String message, Throwable throwable) {
                if (generation != requestGeneration) return;
                RadarUiState current = state.getValue();
                if (current == null) current = RadarUiState.loading(latitude, longitude);
                state.postValue(current.withRadar(current.getRadar(), message, current.isRadarFromCache()));
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
                        RadarUiState current = state.getValue();
                        if (current == null) current = RadarUiState.loading(latitude, longitude);
                        state.postValue(current.withField(value, null, fromCache));
                    }

                    @Override
                    public void onError(@NonNull String message, Throwable throwable) {
                        if (generation != requestGeneration) return;
                        RadarUiState current = state.getValue();
                        if (current == null) current = RadarUiState.loading(latitude, longitude);
                        state.postValue(current.withField(
                                current.getField(),
                                message,
                                current.isFieldFromCache()
                        ));
                    }
                });
    }
}

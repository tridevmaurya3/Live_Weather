package com.tridev.liveweather.ui.radar;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.tridev.liveweather.R;
import com.tridev.liveweather.data.remote.dto.RadarFieldPointResponse;
import com.tridev.liveweather.data.remote.dto.RainViewerResponse;
import com.tridev.liveweather.ui.weather.WeatherFormatter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Phase9Renderer {

    private static final long PLAY_INTERVAL_MILLIS = 1_100L;
    private static final DateTimeFormatter FRAME_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm 'UTC'", Locale.US).withZone(ZoneOffset.UTC);

    private final Activity activity;
    private final Gson gson = new Gson();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final FrameLayout mapHost;
    private final WebView mapWebView;
    private final TextView locationValue;
    private final TextView statusValue;
    private final TextView frameTimeValue;
    private final TextView sourceValue;
    private final TextView rainLayer;
    private final TextView cloudsLayer;
    private final TextView windLayer;
    private final TextView tempLayer;
    private final TextView playButton;
    private final TextView refreshButton;
    private final TextView recenterButton;
    private final SeekBar timelineSeek;

    private RadarUiState latestState;
    private boolean webReady;
    private boolean playing;
    private boolean destroyed;
    private int frameIndex = -1;
    private String activeLayer = "rain";
    private Runnable refreshAction;

    private final Runnable playTicker = new Runnable() {
        @Override
        public void run() {
            if (destroyed || !playing || latestState == null || !latestState.hasRadarFrames()) return;
            int count = latestState.getRadar().getPastFrames().size();
            if (count <= 1 || frameIndex >= count - 1) {
                stopPlayback();
                return;
            }
            frameIndex++;
            timelineSeek.setProgress(frameIndex);
            applyFrame();
            if (frameIndex >= count - 1) {
                stopPlayback();
            } else {
                handler.postDelayed(this, PLAY_INTERVAL_MILLIS);
            }
        }
    };

    public Phase9Renderer(@NonNull Activity activity) {
        this.activity = activity;
        mapHost = activity.findViewById(R.id.radarMapHost);
        locationValue = activity.findViewById(R.id.radarLocationValue);
        statusValue = activity.findViewById(R.id.radarStatusValue);
        frameTimeValue = activity.findViewById(R.id.radarFrameTimeValue);
        sourceValue = activity.findViewById(R.id.radarSourceValue);
        rainLayer = activity.findViewById(R.id.radarLayerRain);
        cloudsLayer = activity.findViewById(R.id.radarLayerClouds);
        windLayer = activity.findViewById(R.id.radarLayerWind);
        tempLayer = activity.findViewById(R.id.radarLayerTemp);
        playButton = activity.findViewById(R.id.radarPlayButton);
        refreshButton = activity.findViewById(R.id.radarRefreshButton);
        recenterButton = activity.findViewById(R.id.radarRecenterButton);
        timelineSeek = activity.findViewById(R.id.radarTimelineSeek);

        mapWebView = new WebView(activity);
        FrameLayout.LayoutParams mapParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        mapHost.addView(mapWebView, 0, mapParams);

        setupWebView();
        setupControls();
        selectLayer("rain");
    }

    public void setRefreshAction(@Nullable Runnable refreshAction) {
        this.refreshAction = refreshAction;
    }

    public void render(@NonNull RadarUiState state) {
        if (destroyed) return;
        boolean locationChanged = latestState == null
                || Math.abs(latestState.getLatitude() - state.getLatitude()) > 0.02d
                || Math.abs(latestState.getLongitude() - state.getLongitude()) > 0.02d;
        latestState = state;

        locationValue.setText(String.format(
                Locale.US,
                "%.3f°, %.3f°",
                state.getLatitude(),
                state.getLongitude()
        ));

        renderStatus(state);
        renderSource(state);
        renderTimeline(state, locationChanged);
        pushStateToMap();
    }

    public void onVisible() {
        if (destroyed) return;
        mapWebView.onResume();
        pushStateToMap();
    }

    public void onHidden() {
        if (destroyed) return;
        stopPlayback();
        mapWebView.onPause();
    }

    public void onDestroy() {
        if (destroyed) return;
        destroyed = true;
        stopPlayback();
        refreshAction = null;
        webReady = false;
        try {
            mapWebView.stopLoading();
            mapWebView.setWebViewClient(null);
            mapWebView.loadUrl("about:blank");
            mapHost.removeView(mapWebView);
            mapWebView.destroy();
        } catch (RuntimeException ignored) {
        }
    }

    private void setupWebView() {
        WebSettings settings = mapWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setLoadsImagesAutomatically(true);
        String userAgent = settings.getUserAgentString();
        if (userAgent != null && !userAgent.contains("LiveWeather/1.0")) {
            settings.setUserAgentString(userAgent + " LiveWeather/1.0");
        }

        mapWebView.setBackgroundColor(0xFF0B1F36);
        mapWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mapWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (destroyed) return;
                webReady = true;
                pushStateToMap();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String url = uri == null ? "" : uri.toString();
                if (url.startsWith("file:///android_asset/radar/")) {
                    return false;
                }

                String host = uri == null ? null : uri.getHost();
                if (isAttributionHost(host)) {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (RuntimeException ignored) {
                    }
                }
                return true;
            }
        });
        mapWebView.loadUrl("file:///android_asset/radar/radar_map.html");
    }

    private boolean isAttributionHost(@Nullable String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.US);
        return normalized.equals("rainviewer.com")
                || normalized.equals("www.rainviewer.com")
                || normalized.equals("open-meteo.com")
                || normalized.equals("www.open-meteo.com")
                || normalized.equals("openstreetmap.org")
                || normalized.equals("www.openstreetmap.org");
    }

    private void setupControls() {
        rainLayer.setOnClickListener(view -> {
            haptic(view);
            selectLayer("rain");
        });
        cloudsLayer.setOnClickListener(view -> {
            haptic(view);
            selectLayer("clouds");
        });
        windLayer.setOnClickListener(view -> {
            haptic(view);
            selectLayer("wind");
        });
        tempLayer.setOnClickListener(view -> {
            haptic(view);
            selectLayer("temp");
        });

        playButton.setOnClickListener(view -> {
            haptic(view);
            if (playing) stopPlayback();
            else startPlayback();
        });

        refreshButton.setOnClickListener(view -> {
            haptic(view);
            if (refreshAction != null) refreshAction.run();
        });

        recenterButton.setOnClickListener(view -> {
            haptic(view);
            evaluate("RadarApp.recenter();");
        });

        timelineSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                stopPlayback();
                frameIndex = progress;
                selectLayer("rain");
                applyFrame();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    private void renderStatus(RadarUiState state) {
        if (state.isLoadingRadar() || state.isLoadingField()) {
            statusValue.setText("Syncing radar and atmospheric layers…");
            return;
        }
        if (state.hasRadarFrames() && state.hasField()) {
            statusValue.setText("Radar + cloud/wind field ready");
            return;
        }
        if (state.hasRadarFrames()) {
            statusValue.setText("Radar ready • model overlays unavailable");
            return;
        }
        if (state.hasField()) {
            statusValue.setText("Model overlays ready • radar unavailable here");
            return;
        }
        String radarError = state.getRadarError();
        String fieldError = state.getFieldError();
        if (radarError != null && fieldError != null) {
            statusValue.setText(radarError + " • " + fieldError);
        } else if (radarError != null) {
            statusValue.setText(radarError);
        } else if (fieldError != null) {
            statusValue.setText(fieldError);
        } else {
            statusValue.setText("Radar data unavailable");
        }
    }

    private void renderSource(RadarUiState state) {
        StringBuilder text = new StringBuilder(
                "RainViewer radar • Open-Meteo cloud/wind/temp • OpenStreetMap base"
        );
        if (state.isRadarFromCache() || state.isFieldFromCache()) {
            text.append(" • cached fallback");
        }
        text.append("\nRadar timeline is observed past data; no future nowcast is fabricated.");
        sourceValue.setText(text.toString());
    }

    private void renderTimeline(RadarUiState state, boolean locationChanged) {
        if (!state.hasRadarFrames()) {
            timelineSeek.setEnabled(false);
            timelineSeek.setMax(0);
            timelineSeek.setProgress(0);
            frameIndex = -1;
            frameTimeValue.setText("No radar frames");
            playButton.setEnabled(false);
            stopPlayback();
            return;
        }

        int count = state.getRadar().getPastFrames().size();
        timelineSeek.setEnabled(true);
        playButton.setEnabled(true);
        timelineSeek.setMax(Math.max(0, count - 1));
        if (locationChanged || frameIndex < 0 || frameIndex >= count) {
            frameIndex = count - 1;
        }
        timelineSeek.setProgress(frameIndex);
        updateFrameTime();
    }

    private void startPlayback() {
        if (latestState == null || !latestState.hasRadarFrames()) return;
        int count = latestState.getRadar().getPastFrames().size();
        if (count <= 1) return;

        selectLayer("rain");
        if (frameIndex < 0 || frameIndex >= count - 1) {
            frameIndex = 0;
            timelineSeek.setProgress(frameIndex);
            applyFrame();
        }
        playing = true;
        playButton.setText("Pause");
        handler.removeCallbacks(playTicker);
        handler.postDelayed(playTicker, PLAY_INTERVAL_MILLIS);
    }

    private void stopPlayback() {
        playing = false;
        playButton.setText("Play");
        handler.removeCallbacks(playTicker);
    }

    private void applyFrame() {
        updateFrameTime();
        if (frameIndex >= 0) {
            evaluate("RadarApp.setFrame(" + frameIndex + ");");
        }
    }

    private void updateFrameTime() {
        if (latestState == null || !latestState.hasRadarFrames() || frameIndex < 0) return;
        List<RainViewerResponse.Frame> frames = latestState.getRadar().getPastFrames();
        if (frameIndex >= frames.size()) return;
        Long time = frames.get(frameIndex).getTime();
        if (time == null) {
            frameTimeValue.setText("Radar frame");
        } else {
            frameTimeValue.setText(FRAME_TIME_FORMATTER.format(Instant.ofEpochSecond(time)));
        }
    }

    private void selectLayer(@NonNull String layer) {
        activeLayer = layer;
        setChipSelected(rainLayer, "rain".equals(layer));
        setChipSelected(cloudsLayer, "clouds".equals(layer));
        setChipSelected(windLayer, "wind".equals(layer));
        setChipSelected(tempLayer, "temp".equals(layer));
        evaluate("RadarApp.setLayer('" + layer + "');");
    }

    private void setChipSelected(TextView view, boolean selected) {
        view.setAlpha(selected ? 1.0f : 0.56f);
        view.setScaleX(selected ? 1.03f : 1.0f);
        view.setScaleY(selected ? 1.03f : 1.0f);
    }

    private void pushStateToMap() {
        if (destroyed || !webReady || latestState == null) return;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("latitude", latestState.getLatitude());
        payload.put("longitude", latestState.getLongitude());
        payload.put("zoom", 6);

        RainViewerResponse radar = latestState.getRadar();
        payload.put("radarHost", radar == null ? null : radar.getHost());

        List<Map<String, Object>> frames = new ArrayList<>();
        if (radar != null) {
            for (RainViewerResponse.Frame frame : radar.getPastFrames()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("time", frame.getTime());
                item.put("path", frame.getPath());
                frames.add(item);
            }
        }
        payload.put("frames", frames);

        List<Map<String, Object>> field = new ArrayList<>();
        for (RadarFieldPointResponse point : latestState.getField()) {
            if (point.getLatitude() == null || point.getLongitude() == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("lat", point.getLatitude());
            item.put("lon", point.getLongitude());
            RadarFieldPointResponse.Current current = point.getCurrent();
            Double temperature = current == null ? null : current.getTemperature2m();
            Double windSpeed = current == null ? null : current.getWindSpeed10m();
            item.put("temperature", temperature);
            item.put("temperatureLabel", temperature == null ? "--" : WeatherFormatter.temperature(temperature));
            item.put("cloud", current == null ? null : current.getCloudCover());
            item.put("windSpeed", windSpeed);
            item.put("windLabel", windSpeed == null ? "--" : WeatherFormatter.wind(windSpeed));
            item.put("windDirection", current == null ? null : current.getWindDirection10m());
            field.add(item);
        }
        payload.put("field", field);

        String json = gson.toJson(payload);
        mapWebView.post(() -> {
            if (destroyed) return;
            evaluate("RadarApp.setData(" + json + ");");
            evaluate("RadarApp.setLayer('" + activeLayer + "');");
            if (frameIndex >= 0) evaluate("RadarApp.setFrame(" + frameIndex + ");");
        });
    }

    private void evaluate(@NonNull String script) {
        if (destroyed || !webReady) return;
        mapWebView.evaluateJavascript(
                "if (window.RadarApp) { " + script + " }",
                null
        );
    }

    private void haptic(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }
}

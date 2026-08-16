package com.tridev.liveweather.ui.radar;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
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
import com.tridev.liveweather.data.repository.RadarRepository;
import com.tridev.liveweather.ui.weather.WeatherFormatter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Radar page renderer.
 *
 * Phase 20B keeps observed RainViewer radar frames separate from Open-Meteo
 * model-field context. Only the sanitized observed timeline and safe tile host
 * from RadarUiState are allowed into the WebView payload.
 *
 * Phase 20B.4 makes the observed timeline timestamp-aware, gives latest/history/
 * playback states explicit UI, and keeps replay behavior deterministic without
 * inventing any future radar frame.
 *
 * Phase 20B.5 exposes freshness and delivery provenance while preserving usable
 * cached layers during refresh.
 *
 * Phase 20B.6 hardens the WebView/Chromium lifecycle. Unchanged map payloads are
 * not resent on every show/hide cycle, the page gets explicit suspend/resume
 * signals, and a crashed WebView renderer receives bounded recreation instead of
 * leaving the Radar destination permanently black.
 */
public final class Phase9Renderer {

    private static final String TAG = "LiveWeatherRadar";
    private static final String RADAR_ASSET_URL = "file:///android_asset/radar/radar_map.html";
    private static final long PLAY_INTERVAL_MILLIS = 1_100L;
    private static final long FRESHNESS_TICK_MILLIS = 60_000L;
    private static final long WEB_RECOVERY_DELAY_MILLIS = 650L;
    private static final long WEB_RECOVERY_WINDOW_MILLIS = 30_000L;
    private static final int MAX_WEB_RECOVERY_ATTEMPTS = 2;

    private static final DateTimeFormatter FRAME_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm 'UTC'", Locale.US).withZone(ZoneOffset.UTC);

    private final Activity activity;
    private final Gson gson = new Gson();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final FrameLayout mapHost;
    private final TextView locationValue;
    private final TextView statusValue;
    private final TextView freshnessValue;
    private final TextView frameTimeValue;
    private final TextView timelineSummary;
    private final TextView sourceValue;
    private final TextView legendTitle;
    private final TextView legendBody;
    private final TextView rainLayer;
    private final TextView cloudsLayer;
    private final TextView windLayer;
    private final TextView tempLayer;
    private final TextView playButton;
    private final TextView refreshButton;
    private final TextView recenterButton;
    private final SeekBar timelineSeek;

    @Nullable private WebView mapWebView;
    @Nullable private RadarUiState latestState;
    @Nullable private String preparedPayloadJson;
    @Nullable private String lastDeliveredPayloadJson;
    @Nullable private String lastDeliveredLayer;
    @Nullable private Long selectedFrameTime;

    private boolean webReady;
    private boolean playing;
    private boolean destroyed;
    private boolean pageVisible;
    private boolean followLatest = true;
    private boolean webRecoveryScheduled;
    private boolean webRecoveryExhausted;
    private int webRecoveryAttempts;
    private long webRecoveryWindowStartedAtMillis;
    private int frameIndex = -1;
    private int lastDeliveredFrame = Integer.MIN_VALUE;
    private String activeLayer = "rain";
    private Runnable refreshAction;

    private final Runnable playTicker = new Runnable() {
        @Override
        public void run() {
            if (destroyed || !playing || latestState == null || !latestState.hasRadarFrames()) return;

            int count = latestState.getObservedFrames().size();
            if (count <= 1 || frameIndex >= count - 1) {
                followLatest = true;
                stopPlayback();
                return;
            }

            frameIndex++;
            followLatest = frameIndex >= count - 1;
            timelineSeek.setProgress(frameIndex);
            applyFrame();

            if (frameIndex >= count - 1) {
                followLatest = true;
                stopPlayback();
            } else {
                handler.postDelayed(this, PLAY_INTERVAL_MILLIS);
            }
        }
    };

    private final Runnable freshnessTicker = new Runnable() {
        @Override
        public void run() {
            if (destroyed || !pageVisible) return;
            if (latestState != null) {
                renderFreshness(latestState);
                renderLegend();
                updatePlaybackPresentation();
            }
            handler.postDelayed(this, FRESHNESS_TICK_MILLIS);
        }
    };

    public Phase9Renderer(@NonNull Activity activity) {
        this.activity = activity;
        mapHost = activity.findViewById(R.id.radarMapHost);
        locationValue = activity.findViewById(R.id.radarLocationValue);
        statusValue = activity.findViewById(R.id.radarStatusValue);
        freshnessValue = activity.findViewById(R.id.radarFreshnessValue);
        frameTimeValue = activity.findViewById(R.id.radarFrameTimeValue);
        timelineSummary = activity.findViewById(R.id.radarTimelineSummary);
        sourceValue = activity.findViewById(R.id.radarSourceValue);
        legendTitle = activity.findViewById(R.id.radarLegendTitle);
        legendBody = activity.findViewById(R.id.radarLegendBody);
        rainLayer = activity.findViewById(R.id.radarLayerRain);
        cloudsLayer = activity.findViewById(R.id.radarLayerClouds);
        windLayer = activity.findViewById(R.id.radarLayerWind);
        tempLayer = activity.findViewById(R.id.radarLayerTemp);
        playButton = activity.findViewById(R.id.radarPlayButton);
        refreshButton = activity.findViewById(R.id.radarRefreshButton);
        recenterButton = activity.findViewById(R.id.radarRecenterButton);
        timelineSeek = activity.findViewById(R.id.radarTimelineSeek);

        createWebView();
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
        preparedPayloadJson = buildMapPayloadJson(state);

        locationValue.setText(String.format(
                Locale.US,
                "%.3f°, %.3f°",
                state.getLatitude(),
                state.getLongitude()
        ));

        renderStatus(state);
        renderFreshness(state);
        renderSource(state);
        renderTimeline(state, locationChanged);
        renderLegend();
        pushStateToMap();
    }

    public void onVisible() {
        if (destroyed) return;
        pageVisible = true;

        if (mapWebView == null && !webRecoveryExhausted) {
            createWebView();
        }

        WebView view = mapWebView;
        if (view != null) {
            try {
                view.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true);
                view.onResume();
            } catch (RuntimeException exception) {
                requestWebRecovery(view, "resume", exception);
            }
        }

        evaluate("RadarApp.resume();");
        pushStateToMap();
        if (latestState != null) renderFreshness(latestState);
        handler.removeCallbacks(freshnessTicker);
        handler.postDelayed(freshnessTicker, FRESHNESS_TICK_MILLIS);
    }

    public void onHidden() {
        if (destroyed) return;
        pageVisible = false;
        handler.removeCallbacks(freshnessTicker);
        stopPlayback();
        evaluate("RadarApp.suspend();");

        WebView view = mapWebView;
        if (view != null) {
            try {
                view.onPause();
            } catch (RuntimeException exception) {
                requestWebRecovery(view, "pause", exception);
            }
        }
    }

    public void onDestroy() {
        if (destroyed) return;
        destroyed = true;
        pageVisible = false;
        playing = false;
        refreshAction = null;
        preparedPayloadJson = null;
        latestState = null;
        handler.removeCallbacksAndMessages(null);

        WebView view = mapWebView;
        mapWebView = null;
        webReady = false;
        destroyWebView(view);
        resetDeliveredBridgeState();
    }

    private void createWebView() {
        if (destroyed || mapWebView != null) return;

        WebView view;
        try {
            view = new WebView(activity);
        } catch (RuntimeException exception) {
            webRecoveryExhausted = true;
            Log.e(TAG, "Unable to create Radar WebView", exception);
            renderMapEngineFailureStatus();
            return;
        }

        mapWebView = view;
        webReady = false;
        resetDeliveredBridgeState();

        FrameLayout.LayoutParams mapParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        mapHost.addView(view, 0, mapParams);
        setupWebView(view);
        view.loadUrl(RADAR_ASSET_URL);
    }

    private void setupWebView(@NonNull WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setDomStorageEnabled(false);
        settings.setGeolocationEnabled(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);

        String userAgent = settings.getUserAgentString();
        if (userAgent != null && !userAgent.contains("LiveWeather/1.0")) {
            settings.setUserAgentString(userAgent + " LiveWeather/1.0");
        }

        view.setSaveEnabled(false);
        view.setBackgroundColor(0xFF0B1F36);
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true);
        view.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView finishedView, String url) {
                if (destroyed || finishedView != mapWebView || !isRadarAssetUrl(url)) return;

                webReady = true;
                webRecoveryScheduled = false;
                webRecoveryExhausted = false;
                resetDeliveredBridgeState();
                evaluate("RadarApp.resume();");
                pushStateToMap();
            }

            @Override
            public void onReceivedError(
                    WebView failedView,
                    WebResourceRequest request,
                    WebResourceError error
            ) {
                if (destroyed || failedView != mapWebView || request == null) return;
                Uri uri = request.getUrl();
                if (request.isForMainFrame() && uri != null && isRadarAssetUrl(uri.toString())) {
                    requestWebRecovery(
                            failedView,
                            "main-frame-load",
                            new IllegalStateException("Radar local page load failed: " + error)
                    );
                }
            }

            @Override
            public boolean onRenderProcessGone(
                    WebView failedView,
                    RenderProcessGoneDetail detail
            ) {
                if (destroyed || failedView != mapWebView) return true;
                String stage = detail != null && detail.didCrash()
                        ? "renderer-crash"
                        : "renderer-terminated";
                requestWebRecovery(
                        failedView,
                        stage,
                        new IllegalStateException("Radar WebView render process unavailable")
                );
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView sourceView, WebResourceRequest request) {
                Uri uri = request == null ? null : request.getUrl();
                String url = uri == null ? "" : uri.toString();
                if (isRadarAssetUrl(url)) return false;

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
    }

    private boolean isRadarAssetUrl(@Nullable String url) {
        return url != null && url.startsWith("file:///android_asset/radar/");
    }

    private boolean isAttributionHost(@Nullable String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.US);
        return normalized.equals("rainviewer.com")
                || normalized.equals("www.rainviewer.com")
                || normalized.equals("open-meteo.com")
                || normalized.equals("www.open-meteo.com")
                || normalized.equals("openstreetmap.org")
                || normalized.equals("www.openstreetmap.org")
                || normalized.equals("leafletjs.com")
                || normalized.equals("www.leafletjs.com");
    }

    private void requestWebRecovery(
            @NonNull WebView failedView,
            @NonNull String stage,
            @NonNull Throwable throwable
    ) {
        if (destroyed || failedView != mapWebView) return;

        Log.e(TAG, "Radar WebView fault at " + stage, throwable);
        webReady = false;
        resetDeliveredBridgeState();

        mapWebView = null;
        destroyWebView(failedView);

        long now = SystemClock.elapsedRealtime();
        if (webRecoveryWindowStartedAtMillis <= 0L
                || now - webRecoveryWindowStartedAtMillis > WEB_RECOVERY_WINDOW_MILLIS) {
            webRecoveryWindowStartedAtMillis = now;
            webRecoveryAttempts = 0;
        }

        if (webRecoveryAttempts >= MAX_WEB_RECOVERY_ATTEMPTS) {
            webRecoveryExhausted = true;
            webRecoveryScheduled = false;
            renderMapEngineFailureStatus();
            return;
        }

        webRecoveryAttempts++;
        if (webRecoveryScheduled) return;
        webRecoveryScheduled = true;

        handler.postDelayed(() -> {
            webRecoveryScheduled = false;
            if (destroyed || mapWebView != null) return;
            if (pageVisible) {
                createWebView();
            }
        }, WEB_RECOVERY_DELAY_MILLIS);
    }

    private void destroyWebView(@Nullable WebView view) {
        if (view == null) return;
        try {
            view.stopLoading();
        } catch (RuntimeException ignored) {
        }
        try {
            view.setWebViewClient(null);
        } catch (RuntimeException ignored) {
        }
        try {
            view.clearHistory();
            view.removeAllViews();
        } catch (RuntimeException ignored) {
        }
        try {
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null) parent.removeView(view);
        } catch (RuntimeException ignored) {
        }
        try {
            view.destroy();
        } catch (RuntimeException ignored) {
        }
    }

    private void retryMapEngine() {
        if (destroyed) return;

        if (mapWebView == null) {
            webRecoveryAttempts = 0;
            webRecoveryWindowStartedAtMillis = 0L;
            webRecoveryExhausted = false;
            createWebView();
            return;
        }

        evaluate("RadarApp.retryEngine();");
    }

    private void renderMapEngineFailureStatus() {
        if (destroyed) return;
        statusValue.setText("Interactive map engine unavailable • weather data retained");
        freshnessValue.setTextColor(activity.getColor(R.color.weather_warning));
    }

    private void resetDeliveredBridgeState() {
        lastDeliveredPayloadJson = null;
        lastDeliveredLayer = null;
        lastDeliveredFrame = Integer.MIN_VALUE;
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
            retryMapEngine();
            if (refreshAction != null) refreshAction.run();
        });

        recenterButton.setOnClickListener(view -> {
            haptic(view);
            if (!evaluate("RadarApp.recenter();")) retryMapEngine();
        });

        timelineSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || latestState == null || !latestState.hasRadarFrames()) return;

                stopPlayback();
                int last = latestState.getObservedFrames().size() - 1;
                frameIndex = Math.max(0, Math.min(progress, last));
                followLatest = frameIndex == last;
                selectLayer("rain");
                applyFrame();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    private void renderStatus(@NonNull RadarUiState state) {
        boolean refreshing = state.isLoadingRadar() || state.isLoadingField();
        boolean hasUsableData = state.hasRadarFrames() || state.hasField();

        refreshButton.setText(refreshing ? "Refreshing" : "Refresh");
        refreshButton.setEnabled(!refreshing);
        refreshButton.setAlpha(refreshing ? 0.64f : 1.0f);
        refreshButton.setContentDescription(refreshing
                ? "Refreshing radar and model field"
                : "Refresh radar and model field");

        if (webRecoveryExhausted) {
            statusValue.setText(hasUsableData
                    ? "Interactive map engine unavailable • weather data retained"
                    : "Interactive map engine unavailable • retry Refresh when ready");
            return;
        }

        if (refreshing) {
            statusValue.setText(hasUsableData
                    ? "Refreshing… showing existing usable data"
                    : "Loading observed radar and atmospheric model field…");
            return;
        }

        if ((state.isRadarNetworkFallback() && state.hasRadarFrames())
                || (state.isFieldNetworkFallback() && state.hasField())) {
            statusValue.setText("Network unavailable • cached fallback active");
            return;
        }

        if ((state.isRadarServerFallback() && state.hasRadarFrames())
                || (state.isFieldServerFallback() && state.hasField())) {
            statusValue.setText("Provider refresh unavailable • cached fallback active");
            return;
        }

        if ((state.getRadarError() != null || state.getFieldError() != null) && hasUsableData) {
            statusValue.setText("Refresh issue • continuing with existing usable data");
            return;
        }

        if (state.hasRadarFrames() && state.hasField()) {
            statusValue.setText("Observed radar + model field ready");
            return;
        }
        if (state.hasRadarFrames()) {
            statusValue.setText("Observed radar ready • model field unavailable");
            return;
        }
        if (state.hasField()) {
            statusValue.setText("Model field ready • observed radar unavailable here");
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

    private void renderFreshness(@NonNull RadarUiState state) {
        long now = System.currentTimeMillis();
        StringBuilder text = new StringBuilder();

        if (state.hasRadarFrames()) {
            text.append("Radar observation ")
                    .append(ageLabel(state.getRadarObservationAgeMillis(now)));
            if (state.isRadarObservationDelayed(now)) text.append(" · delayed");
            text.append(" · ").append(sourceLabel(state.getRadarSource()));
        } else if (state.isLoadingRadar()) {
            text.append("Radar loading");
        } else {
            text.append("Radar unavailable");
        }

        text.append("  •  ");

        if (state.hasField()) {
            text.append("Model field ")
                    .append(ageLabel(state.getFieldAgeMillis(now)));
            if (state.isFieldDelayed(now)) text.append(" · delayed");
            text.append(" · ").append(sourceLabel(state.getFieldSource()));
        } else if (state.isLoadingField()) {
            text.append("Model loading");
        } else {
            text.append("Model unavailable");
        }

        freshnessValue.setText(text.toString());
        boolean warning = webRecoveryExhausted
                || state.isRadarNetworkFallback()
                || state.isFieldNetworkFallback()
                || state.isRadarServerFallback()
                || state.isFieldServerFallback()
                || state.isRadarObservationDelayed(now)
                || state.isFieldDelayed(now);
        boolean unavailable = !state.hasRadarFrames()
                && !state.hasField()
                && !state.isLoadingRadar()
                && !state.isLoadingField();
        freshnessValue.setTextColor(activity.getColor(
                unavailable
                        ? R.color.weather_danger
                        : warning ? R.color.weather_warning : R.color.weather_text_secondary
        ));
        freshnessValue.setContentDescription(text.toString());
    }

    @NonNull
    private String ageLabel(long ageMillis) {
        if (ageMillis < 0L) return "age --";
        long minutes = ageMillis / 60_000L;
        if (minutes < 1L) return "<1m ago";
        if (minutes < 60L) return minutes + "m ago";
        long hours = minutes / 60L;
        if (hours < 48L) return hours + "h ago";
        return (hours / 24L) + "d ago";
    }

    @NonNull
    private String sourceLabel(@Nullable RadarRepository.DeliverySource source) {
        if (source == null) return "source --";
        switch (source) {
            case NETWORK:
                return "network";
            case MEMORY_CACHE:
                return "recent cache";
            case NETWORK_FALLBACK_CACHE:
                return "network fallback";
            case SERVER_FALLBACK_CACHE:
                return "provider fallback";
            default:
                return "saved data";
        }
    }

    private void renderSource(@NonNull RadarUiState state) {
        StringBuilder text = new StringBuilder(
                "RainViewer observed radar • Open-Meteo model field (cloud/wind/temp) • OpenStreetMap base"
        );
        if (state.isRadarNetworkFallback() || state.isFieldNetworkFallback()) {
            text.append(" • network fallback cache");
        } else if (state.isRadarServerFallback() || state.isFieldServerFallback()) {
            text.append(" • provider fallback cache");
        } else if (state.isRadarFromCache() || state.isFieldFromCache()) {
            text.append(" • recent memory cache");
        }
        if (state.hasRadarFrames() && state.isRadarObservationDelayed(System.currentTimeMillis())) {
            text.append(" • radar delayed");
        }
        text.append("\nCloud/wind/temp remain model context, not radar observations. No future radar nowcast is fabricated.");
        sourceValue.setText(text.toString());
    }

    private void renderTimeline(@NonNull RadarUiState state, boolean locationChanged) {
        if (!state.hasRadarFrames()) {
            playing = false;
            handler.removeCallbacks(playTicker);
            timelineSeek.setEnabled(false);
            timelineSeek.setMax(0);
            timelineSeek.setProgress(0);
            frameIndex = -1;
            selectedFrameTime = null;
            followLatest = true;
            frameTimeValue.setText("--:-- UTC");
            timelineSummary.setText(state.isLoadingRadar()
                    ? "Loading observed radar history…"
                    : "Observed radar history unavailable");
            playButton.setText("Play");
            playButton.setEnabled(false);
            updateTimelineAccessibility();
            return;
        }

        List<RainViewerResponse.Frame> frames = state.getObservedFrames();
        int count = frames.size();
        int last = count - 1;

        timelineSeek.setEnabled(true);
        timelineSeek.setMax(last);
        playButton.setEnabled(count > 1);

        if (locationChanged || frameIndex < 0) {
            frameIndex = last;
            followLatest = true;
        } else if (followLatest) {
            frameIndex = last;
        } else if (selectedFrameTime != null) {
            frameIndex = findNearestFrameIndex(frames, selectedFrameTime);
        } else {
            frameIndex = Math.max(0, Math.min(frameIndex, last));
        }

        timelineSeek.setProgress(frameIndex);
        updateFramePresentation();
    }

    private int findNearestFrameIndex(
            @NonNull List<RainViewerResponse.Frame> frames,
            long targetTime
    ) {
        int bestIndex = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < frames.size(); i++) {
            Long time = frames.get(i).getTime();
            if (time == null) continue;
            long distance = Math.abs(time - targetTime);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private void startPlayback() {
        if (latestState == null || !latestState.hasRadarFrames()) return;
        int count = latestState.getObservedFrames().size();
        if (count <= 1) return;

        selectLayer("rain");
        if (frameIndex < 0 || frameIndex >= count - 1) {
            frameIndex = 0;
            followLatest = false;
            timelineSeek.setProgress(frameIndex);
            applyFrame();
        } else {
            followLatest = false;
        }

        playing = true;
        updatePlaybackPresentation();
        handler.removeCallbacks(playTicker);
        handler.postDelayed(playTicker, PLAY_INTERVAL_MILLIS);
    }

    private void stopPlayback() {
        playing = false;
        handler.removeCallbacks(playTicker);
        updatePlaybackPresentation();
    }

    private void applyFrame() {
        updateFramePresentation();
        if (frameIndex >= 0 && evaluate("RadarApp.setFrame(" + frameIndex + ");")) {
            lastDeliveredFrame = frameIndex;
        }
    }

    private void updateFramePresentation() {
        RadarUiState state = latestState;
        if (state == null || !state.hasRadarFrames() || frameIndex < 0) {
            updatePlaybackPresentation();
            return;
        }

        List<RainViewerResponse.Frame> frames = state.getObservedFrames();
        if (frameIndex >= frames.size()) return;

        Long time = frames.get(frameIndex).getTime();
        selectedFrameTime = time;
        frameTimeValue.setText(time == null
                ? "Observed frame"
                : FRAME_TIME_FORMATTER.format(Instant.ofEpochSecond(time)));
        updatePlaybackPresentation();
    }

    private void updatePlaybackPresentation() {
        RadarUiState state = latestState;
        if (state == null || !state.hasRadarFrames() || frameIndex < 0) {
            playButton.setText("Play");
            updateTimelineAccessibility();
            return;
        }

        int count = state.getObservedFrames().size();
        int boundedIndex = Math.max(0, Math.min(frameIndex, count - 1));
        boolean atLatest = boundedIndex == count - 1;
        String position = "frame " + (boundedIndex + 1) + "/" + count;

        if (playing) {
            playButton.setText("Pause");
            timelineSummary.setText("Playing observed history · " + position);
        } else if (atLatest) {
            playButton.setText(count > 1 ? "Replay" : "Play");
            if (state.isRadarObservationDelayed(System.currentTimeMillis())) {
                timelineSummary.setText("Latest available · delayed observation · " + position);
            } else if (state.isRadarNetworkFallback()) {
                timelineSummary.setText("Latest cached observation · network fallback · " + position);
            } else if (state.isRadarServerFallback()) {
                timelineSummary.setText("Latest cached observation · provider fallback · " + position);
            } else if (state.isRadarFromCache()) {
                timelineSummary.setText("Latest cached observation · " + position);
            } else {
                timelineSummary.setText("Latest observed frame · " + position);
            }
        } else {
            playButton.setText("Play");
            timelineSummary.setText(
                    (state.isRadarFromCache() ? "Cached historical frame · " : "Historical observed frame · ")
                            + position
            );
        }

        updateTimelineAccessibility();
    }

    private void updateTimelineAccessibility() {
        String summary = String.valueOf(timelineSummary.getText());
        String time = String.valueOf(frameTimeValue.getText());
        timelineSeek.setContentDescription(summary + " · " + time + ". Swipe to choose an observed past frame.");
        playButton.setContentDescription(String.valueOf(playButton.getText()) + " observed radar history");
    }

    private void selectLayer(@NonNull String layer) {
        activeLayer = layer;
        setChipSelected(rainLayer, "rain".equals(layer));
        setChipSelected(cloudsLayer, "clouds".equals(layer));
        setChipSelected(windLayer, "wind".equals(layer));
        setChipSelected(tempLayer, "temp".equals(layer));
        renderLegend();
        if (!layer.equals(lastDeliveredLayer) && evaluate("RadarApp.setLayer('" + layer + "');")) {
            lastDeliveredLayer = layer;
        }
    }

    private void setChipSelected(@NonNull TextView view, boolean selected) {
        view.setSelected(selected);
        view.setAlpha(selected ? 1.0f : 0.78f);
        view.setScaleX(1.0f);
        view.setScaleY(1.0f);
        view.setBackgroundResource(
                selected ? R.drawable.bg_weather_chip_selected : R.drawable.bg_weather_chip
        );
        view.setTextColor(activity.getColor(
                selected ? R.color.weather_aqua : R.color.weather_text_primary
        ));
        view.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        String label = String.valueOf(view.getText());
        view.setContentDescription(label + (selected ? ", selected" : ", not selected"));
    }

    private void renderLegend() {
        RadarUiState state = latestState;
        switch (activeLayer) {
            case "clouds":
                legendTitle.setText("MODEL CLOUDS · OPEN-METEO");
                if (state != null && !state.hasField()) {
                    legendBody.setText("Current model cloud field unavailable.");
                } else if (state != null && state.isFieldDelayed(System.currentTimeMillis())) {
                    legendBody.setText("Cloud cover 0–100% · delayed saved model field");
                } else {
                    legendBody.setText("Cloud cover 0–100% · continuous interpolated model field");
                }
                break;

            case "wind":
                legendTitle.setText("MODEL WIND · OPEN-METEO");
                if (state != null && !state.hasField()) {
                    legendBody.setText("Current model wind field unavailable.");
                } else if (state != null && state.isFieldDelayed(System.currentTimeMillis())) {
                    legendBody.setText("Saved model field is delayed · arrow = flow direction");
                } else {
                    legendBody.setText("Arrow = flow direction · label = speed in selected wind unit");
                }
                break;

            case "temp":
                legendTitle.setText("MODEL TEMPERATURE · OPEN-METEO");
                if (state != null && !state.hasField()) {
                    legendBody.setText("Current model temperature field unavailable.");
                } else if (state != null && state.isFieldDelayed(System.currentTimeMillis())) {
                    legendBody.setText("Saved model field is delayed · labels use selected unit");
                } else {
                    legendBody.setText("Color scale: cold → cool → mild → warm → hot · labels use selected unit");
                }
                break;

            case "rain":
            default:
                legendTitle.setText("OBSERVED RAIN RADAR · RAINVIEWER");
                if (state != null && !state.hasRadarFrames()) {
                    legendBody.setText("Observed radar frames unavailable for this area/network.");
                } else if (state != null && state.isRadarObservationDelayed(System.currentTimeMillis())) {
                    legendBody.setText("Echo intensity: light → moderate → heavy → intense · delayed observation");
                } else {
                    legendBody.setText("Echo intensity: light → moderate → heavy → intense · observed past frames");
                }
                break;
        }
    }

    @NonNull
    private String buildMapPayloadJson(@NonNull RadarUiState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("latitude", state.getLatitude());
        payload.put("longitude", state.getLongitude());
        payload.put("zoom", 6);
        payload.put("radarHost", state.getSafeRadarHost());
        payload.put("radarKind", "observed_past");
        payload.put("fieldKind", "model_current");

        List<Map<String, Object>> frames = new ArrayList<>();
        for (RainViewerResponse.Frame frame : state.getObservedFrames()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", frame.getTime());
            item.put("path", frame.getPath());
            frames.add(item);
        }
        payload.put("frames", frames);

        List<Map<String, Object>> field = new ArrayList<>();
        for (RadarFieldPointResponse point : state.getField()) {
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

        return gson.toJson(payload);
    }

    private void pushStateToMap() {
        WebView view = mapWebView;
        String json = preparedPayloadJson;
        if (destroyed || !webReady || view == null || json == null) return;

        boolean payloadChanged = !json.equals(lastDeliveredPayloadJson);
        boolean layerChanged = !activeLayer.equals(lastDeliveredLayer);
        boolean frameChanged = frameIndex >= 0 && frameIndex != lastDeliveredFrame;
        if (!payloadChanged && !layerChanged && !frameChanged) return;

        final int targetFrame = frameIndex;
        final String targetLayer = activeLayer;
        view.post(() -> {
            if (destroyed || !webReady || view != mapWebView) return;

            if (payloadChanged) {
                if (!evaluateOnView(view, "RadarApp.setData(" + json + ");")) return;
                lastDeliveredPayloadJson = json;
                lastDeliveredLayer = null;
                lastDeliveredFrame = Integer.MIN_VALUE;
            }

            if (payloadChanged || !targetLayer.equals(lastDeliveredLayer)) {
                if (evaluateOnView(view, "RadarApp.setLayer('" + targetLayer + "');")) {
                    lastDeliveredLayer = targetLayer;
                }
            }

            if (targetFrame >= 0 && (payloadChanged || targetFrame != lastDeliveredFrame)) {
                if (evaluateOnView(view, "RadarApp.setFrame(" + targetFrame + ");")) {
                    lastDeliveredFrame = targetFrame;
                }
            }
        });
    }

    private boolean evaluate(@NonNull String script) {
        WebView view = mapWebView;
        return view != null && evaluateOnView(view, script);
    }

    private boolean evaluateOnView(@NonNull WebView view, @NonNull String script) {
        if (destroyed || !webReady || view != mapWebView) return false;
        try {
            view.evaluateJavascript(
                    "if (window.RadarApp) { " + script + " }",
                    null
            );
            return true;
        } catch (RuntimeException exception) {
            requestWebRecovery(view, "javascript-bridge", exception);
            return false;
        }
    }

    private void haptic(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }
}

package com.tridev.liveweather.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.RainViewerResponse;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 20B observed-radar truth boundary.
 *
 * RainViewer metadata represents observed past radar imagery. This policy keeps
 * that observed timeline separate from Open-Meteo model fields and rejects
 * malformed/unsafe metadata before it reaches the WebView map.
 *
 * It never fabricates future frames or converts forecast/model cloud values into
 * radar observations.
 */
public final class RadarObservedDataPolicy {

    private static final long FUTURE_TOLERANCE_SECONDS = 2L * 60L;
    private static final long STALE_METADATA_SECONDS = 30L * 60L;

    private RadarObservedDataPolicy() {
    }

    /** Returns an HTTPS RainViewer tile host or null when metadata is unsafe. */
    @Nullable
    public static String safeHost(@Nullable RainViewerResponse response) {
        if (response == null) return null;
        String raw = response.getHost();
        if (raw == null || raw.trim().isEmpty()) return null;

        try {
            URI uri = URI.create(raw.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || !"https".equalsIgnoreCase(scheme) || host == null) {
                return null;
            }

            String normalized = host.toLowerCase(Locale.US);
            if (!normalized.equals("rainviewer.com")
                    && !normalized.endsWith(".rainviewer.com")) {
                return null;
            }

            // Host metadata is used as an origin only; path/query/fragment are not allowed.
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) return null;
            String path = uri.getPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) return null;

            String value = "https://" + normalized;
            int port = uri.getPort();
            if (port > 0 && port != 443) return null;
            return value;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Returns only usable observed past frames, sorted oldest -> newest and
     * deduplicated by timestamp. Provider metadata slightly ahead of the local
     * clock is tolerated for clock skew, but real future/nowcast frames are not.
     */
    @NonNull
    public static List<RainViewerResponse.Frame> sanitizePastFrames(
            @Nullable RainViewerResponse response,
            long nowMillis
    ) {
        if (response == null || safeHost(response) == null) return Collections.emptyList();

        long nowSeconds = Math.max(0L, nowMillis / 1000L);
        long latestAllowed = nowSeconds + FUTURE_TOLERANCE_SECONDS;
        Map<Long, RainViewerResponse.Frame> byTime = new LinkedHashMap<>();

        for (RainViewerResponse.Frame frame : response.getPastFrames()) {
            if (frame == null) continue;
            Long time = frame.getTime();
            String path = frame.getPath();
            if (time == null || time <= 0L || time > latestAllowed) continue;
            if (!isSafeRadarPath(path)) continue;
            byTime.put(time, frame);
        }

        if (byTime.isEmpty()) return Collections.emptyList();

        List<RainViewerResponse.Frame> frames = new ArrayList<>(byTime.values());
        frames.sort(Comparator.comparingLong(frame -> frame.getTime() == null ? 0L : frame.getTime()));
        return Collections.unmodifiableList(frames);
    }

    public static boolean hasUsableObservedTimeline(
            @Nullable RainViewerResponse response,
            long nowMillis
    ) {
        return safeHost(response) != null && !sanitizePastFrames(response, nowMillis).isEmpty();
    }

    /** Metadata age is diagnostic only; stale cached observations may still be shown with a label. */
    public static boolean isMetadataStale(@Nullable RainViewerResponse response, long nowMillis) {
        if (response == null || response.getGenerated() == null || response.getGenerated() <= 0L) {
            return true;
        }
        long nowSeconds = Math.max(0L, nowMillis / 1000L);
        long age = nowSeconds - response.getGenerated();
        return age > STALE_METADATA_SECONDS;
    }

    private static boolean isSafeRadarPath(@Nullable String path) {
        if (path == null || path.isEmpty()) return false;
        if (!path.startsWith("/v") || !path.contains("/radar/")) return false;
        if (path.contains("..") || path.contains("?") || path.contains("#")) return false;

        // RainViewer currently publishes /v2/radar/<epoch>. Keep the version
        // component forward-compatible while requiring a radar-only path.
        String[] parts = path.split("/");
        if (parts.length < 4) return false;
        if (!parts[1].matches("v\\d+")) return false;
        if (!"radar".equals(parts[2])) return false;
        try {
            return Long.parseLong(parts[3]) > 0L;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}

package com.tridev.liveweather.ui.radar;

import android.net.Uri;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;

/**
 * Serves the packaged Leaflet WebJar to Radar's WebView through a private local
 * HTTPS origin. The Leaflet engine therefore does not depend on a runtime CDN.
 *
 * Map imagery remains provider-backed: OpenStreetMap/RainViewer tile requests
 * are intentionally not intercepted here.
 */
public final class RadarLeafletRuntime {

    public static final String LOCAL_HOST = "appassets.androidplatform.net";
    public static final String LOCAL_ORIGIN = "https://" + LOCAL_HOST;
    public static final String LEAFLET_JS_URL = LOCAL_ORIGIN + "/radar-runtime/leaflet.js";
    public static final String LEAFLET_CSS_URL = LOCAL_ORIGIN + "/radar-runtime/leaflet.css";

    private static final String WEBJAR_ROOT =
            "META-INF/resources/webjars/leaflet/1.9.4/dist/";

    private RadarLeafletRuntime() {
    }

    /**
     * Returns a local WebResourceResponse only for the private Radar runtime
     * origin. All real network/provider requests return null and continue
     * through WebView normally.
     */
    @Nullable
    public static WebResourceResponse intercept(@Nullable Uri uri) {
        if (uri == null) return null;
        if (!"https".equalsIgnoreCase(uri.getScheme())) return null;
        if (!LOCAL_HOST.equalsIgnoreCase(uri.getHost())) return null;

        String path = uri.getPath();
        if (path == null) return notFound();

        switch (path) {
            case "/radar-runtime/leaflet.js":
                return classpathResource(WEBJAR_ROOT + "leaflet.js", "text/javascript", "UTF-8");
            case "/radar-runtime/leaflet.css":
                return classpathResource(WEBJAR_ROOT + "leaflet.css", "text/css", "UTF-8");
            case "/radar-runtime/images/layers.png":
                return classpathResource(WEBJAR_ROOT + "images/layers.png", "image/png", null);
            case "/radar-runtime/images/layers-2x.png":
                return classpathResource(WEBJAR_ROOT + "images/layers-2x.png", "image/png", null);
            case "/radar-runtime/images/marker-icon.png":
                return classpathResource(WEBJAR_ROOT + "images/marker-icon.png", "image/png", null);
            case "/radar-runtime/images/marker-icon-2x.png":
                return classpathResource(WEBJAR_ROOT + "images/marker-icon-2x.png", "image/png", null);
            case "/radar-runtime/images/marker-shadow.png":
                return classpathResource(WEBJAR_ROOT + "images/marker-shadow.png", "image/png", null);
            default:
                return notFound();
        }
    }

    @NonNull
    private static WebResourceResponse classpathResource(
            @NonNull String resourcePath,
            @NonNull String mimeType,
            @Nullable String encoding
    ) {
        ClassLoader classLoader = RadarLeafletRuntime.class.getClassLoader();
        InputStream stream = classLoader == null ? null : classLoader.getResourceAsStream(resourcePath);
        if (stream == null) return notFound();

        return new WebResourceResponse(
                mimeType,
                encoding,
                200,
                "OK",
                Collections.singletonMap("Cache-Control", "public, max-age=31536000, immutable"),
                stream
        );
    }

    @NonNull
    private static WebResourceResponse notFound() {
        byte[] body = "Local Radar runtime resource not found"
                .getBytes(StandardCharsets.UTF_8);
        return new WebResourceResponse(
                "text/plain",
                "UTF-8",
                404,
                "Not Found",
                Collections.singletonMap("Cache-Control", "no-store"),
                new ByteArrayInputStream(body)
        );
    }

    public static boolean isLocalRuntimeUrl(@Nullable Uri uri) {
        if (uri == null || uri.getHost() == null) return false;
        return "https".equalsIgnoreCase(uri.getScheme())
                && LOCAL_HOST.equals(uri.getHost().toLowerCase(Locale.US));
    }
}

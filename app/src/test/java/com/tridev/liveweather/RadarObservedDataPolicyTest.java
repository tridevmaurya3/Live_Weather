package com.tridev.liveweather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.tridev.liveweather.data.remote.dto.RainViewerResponse;
import com.tridev.liveweather.data.repository.RadarObservedDataPolicy;

import org.junit.Test;

import java.util.List;

public final class RadarObservedDataPolicyTest {

    private final Gson gson = new Gson();

    @Test
    public void opaqueFrameId_isAccepted() {
        long now = System.currentTimeMillis();
        long frameTime = (now / 1000L) - 60L;
        RainViewerResponse response = response(
                frameTime,
                "/v2/radar/25a5de4c13a6",
                "https://tilecache.rainviewer.com"
        );

        List<RainViewerResponse.Frame> frames =
                RadarObservedDataPolicy.sanitizePastFrames(response, now);

        assertEquals(1, frames.size());
        assertEquals("/v2/radar/25a5de4c13a6", frames.get(0).getPath());
    }

    @Test
    public void legacyNumericFrameId_remainsAccepted() {
        long now = System.currentTimeMillis();
        long frameTime = (now / 1000L) - 60L;
        RainViewerResponse response = response(
                frameTime,
                "/v2/radar/1786355400",
                "https://tilecache.rainviewer.com"
        );

        assertEquals(1, RadarObservedDataPolicy.sanitizePastFrames(response, now).size());
    }

    @Test
    public void unsafeFramePaths_areRejected() {
        long now = System.currentTimeMillis();
        long frameTime = (now / 1000L) - 60L;
        String[] unsafePaths = {
                "/v2/radar/../secret",
                "/v2/radar/frame?x=1",
                "/v2/radar/frame#fragment",
                "/v2/radar/frame/extra",
                "/v2/radar/frame%2Fextra",
                "/v2/cloud/frame",
                "/radar/frame"
        };

        for (String path : unsafePaths) {
            RainViewerResponse response = response(
                    frameTime,
                    path,
                    "https://tilecache.rainviewer.com"
            );
            assertTrue(
                    "Expected unsafe radar path to be rejected: " + path,
                    RadarObservedDataPolicy.sanitizePastFrames(response, now).isEmpty()
            );
        }
    }

    @Test
    public void unsafeHost_isRejectedEvenWithValidOpaqueFrame() {
        long now = System.currentTimeMillis();
        long frameTime = (now / 1000L) - 60L;
        RainViewerResponse response = response(
                frameTime,
                "/v2/radar/25a5de4c13a6",
                "https://example.com"
        );

        assertTrue(RadarObservedDataPolicy.sanitizePastFrames(response, now).isEmpty());
    }

    private RainViewerResponse response(long frameTime, String path, String host) {
        long generated = Math.max(1L, frameTime);
        String json = "{"
                + "\"version\":\"2.0\","
                + "\"generated\":" + generated + ","
                + "\"host\":\"" + host + "\","
                + "\"radar\":{\"past\":[{"
                + "\"time\":" + frameTime + ","
                + "\"path\":\"" + path + "\""
                + "}]}"
                + "}";
        return gson.fromJson(json, RainViewerResponse.class);
    }
}

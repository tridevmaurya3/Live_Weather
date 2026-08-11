package com.tridev.liveweather.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

public final class RainViewerResponse {

    @SerializedName("version")
    private String version;

    @SerializedName("generated")
    private Long generated;

    @SerializedName("host")
    private String host;

    @SerializedName("radar")
    private Radar radar;

    public String getVersion() {
        return version;
    }

    public Long getGenerated() {
        return generated;
    }

    public String getHost() {
        return host;
    }

    public List<Frame> getPastFrames() {
        if (radar == null || radar.past == null) return Collections.emptyList();
        return radar.past;
    }

    public static final class Radar {
        @SerializedName("past")
        private List<Frame> past;
    }

    public static final class Frame {
        @SerializedName("time")
        private Long time;

        @SerializedName("path")
        private String path;

        public Long getTime() {
            return time;
        }

        public String getPath() {
            return path;
        }
    }
}

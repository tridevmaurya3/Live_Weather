package com.tridev.liveweather.data.remote.dto;

import com.google.gson.annotations.SerializedName;

public final class RadarFieldPointResponse {

    @SerializedName("latitude")
    private Double latitude;

    @SerializedName("longitude")
    private Double longitude;

    @SerializedName("current")
    private Current current;

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Current getCurrent() {
        return current;
    }

    public static final class Current {
        @SerializedName("time")
        private String time;

        @SerializedName("temperature_2m")
        private Double temperature2m;

        @SerializedName("cloud_cover")
        private Double cloudCover;

        @SerializedName("wind_speed_10m")
        private Double windSpeed10m;

        @SerializedName("wind_direction_10m")
        private Double windDirection10m;

        public String getTime() {
            return time;
        }

        public Double getTemperature2m() {
            return temperature2m;
        }

        public Double getCloudCover() {
            return cloudCover;
        }

        public Double getWindSpeed10m() {
            return windSpeed10m;
        }

        public Double getWindDirection10m() {
            return windDirection10m;
        }
    }
}

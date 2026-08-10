package com.tridev.liveweather.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class GeocodingResponse {

    @SerializedName("results")
    private List<Result> results;

    @SerializedName("generationtime_ms")
    private Double generationTimeMs;

    public List<Result> getResults() {
        return results;
    }

    public Double getGenerationTimeMs() {
        return generationTimeMs;
    }

    public static class Result {

        @SerializedName("id")
        private Long id;

        @SerializedName("name")
        private String name;

        @SerializedName("latitude")
        private Double latitude;

        @SerializedName("longitude")
        private Double longitude;

        @SerializedName("timezone")
        private String timezone;

        @SerializedName("country")
        private String country;

        @SerializedName("country_code")
        private String countryCode;

        @SerializedName("admin1")
        private String admin1;

        @SerializedName("admin2")
        private String admin2;

        @SerializedName("population")
        private Long population;

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Double getLatitude() {
            return latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public String getTimezone() {
            return timezone;
        }

        public String getCountry() {
            return country;
        }

        public String getCountryCode() {
            return countryCode;
        }

        public String getAdmin1() {
            return admin1;
        }

        public String getAdmin2() {
            return admin2;
        }

        public Long getPopulation() {
            return population;
        }
    }
}

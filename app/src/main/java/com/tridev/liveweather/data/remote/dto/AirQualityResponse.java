package com.tridev.liveweather.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class AirQualityResponse {

    @SerializedName("latitude")
    private Double latitude;
    @SerializedName("longitude")
    private Double longitude;
    @SerializedName("timezone")
    private String timezone;
    @SerializedName("timezone_abbreviation")
    private String timezoneAbbreviation;
    @SerializedName("utc_offset_seconds")
    private Integer utcOffsetSeconds;
    @SerializedName("current")
    private CurrentAirQuality current;
    @SerializedName("hourly")
    private HourlyAirQuality hourly;

    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getTimezone() { return timezone; }
    public String getTimezoneAbbreviation() { return timezoneAbbreviation; }
    public Integer getUtcOffsetSeconds() { return utcOffsetSeconds; }
    public CurrentAirQuality getCurrent() { return current; }
    public HourlyAirQuality getHourly() { return hourly; }

    public static final class CurrentAirQuality {
        @SerializedName("time") private String time;
        @SerializedName("interval") private Integer interval;
        @SerializedName("european_aqi") private Double europeanAqi;
        @SerializedName("us_aqi") private Double usAqi;
        @SerializedName("pm10") private Double pm10;
        @SerializedName("pm2_5") private Double pm25;
        @SerializedName("carbon_monoxide") private Double carbonMonoxide;
        @SerializedName("nitrogen_dioxide") private Double nitrogenDioxide;
        @SerializedName("sulphur_dioxide") private Double sulphurDioxide;
        @SerializedName("ozone") private Double ozone;
        @SerializedName("aerosol_optical_depth") private Double aerosolOpticalDepth;
        @SerializedName("dust") private Double dust;
        @SerializedName("uv_index") private Double uvIndex;
        @SerializedName("uv_index_clear_sky") private Double uvIndexClearSky;

        public String getTime() { return time; }
        public Integer getInterval() { return interval; }
        public Double getEuropeanAqi() { return europeanAqi; }
        public Double getUsAqi() { return usAqi; }
        public Double getPm10() { return pm10; }
        public Double getPm25() { return pm25; }
        public Double getCarbonMonoxide() { return carbonMonoxide; }
        public Double getNitrogenDioxide() { return nitrogenDioxide; }
        public Double getSulphurDioxide() { return sulphurDioxide; }
        public Double getOzone() { return ozone; }
        public Double getAerosolOpticalDepth() { return aerosolOpticalDepth; }
        public Double getDust() { return dust; }
        public Double getUvIndex() { return uvIndex; }
        public Double getUvIndexClearSky() { return uvIndexClearSky; }
    }

    public static final class HourlyAirQuality {
        @SerializedName("time") private List<String> time;
        @SerializedName("european_aqi") private List<Double> europeanAqi;
        @SerializedName("us_aqi") private List<Double> usAqi;
        @SerializedName("pm10") private List<Double> pm10;
        @SerializedName("pm2_5") private List<Double> pm25;
        @SerializedName("carbon_monoxide") private List<Double> carbonMonoxide;
        @SerializedName("nitrogen_dioxide") private List<Double> nitrogenDioxide;
        @SerializedName("sulphur_dioxide") private List<Double> sulphurDioxide;
        @SerializedName("ozone") private List<Double> ozone;
        @SerializedName("aerosol_optical_depth") private List<Double> aerosolOpticalDepth;
        @SerializedName("dust") private List<Double> dust;
        @SerializedName("uv_index") private List<Double> uvIndex;
        @SerializedName("uv_index_clear_sky") private List<Double> uvIndexClearSky;

        public List<String> getTime() { return time; }
        public List<Double> getEuropeanAqi() { return europeanAqi; }
        public List<Double> getUsAqi() { return usAqi; }
        public List<Double> getPm10() { return pm10; }
        public List<Double> getPm25() { return pm25; }
        public List<Double> getCarbonMonoxide() { return carbonMonoxide; }
        public List<Double> getNitrogenDioxide() { return nitrogenDioxide; }
        public List<Double> getSulphurDioxide() { return sulphurDioxide; }
        public List<Double> getOzone() { return ozone; }
        public List<Double> getAerosolOpticalDepth() { return aerosolOpticalDepth; }
        public List<Double> getDust() { return dust; }
        public List<Double> getUvIndex() { return uvIndex; }
        public List<Double> getUvIndexClearSky() { return uvIndexClearSky; }
    }
}

package com.tridev.liveweather.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class WeatherResponse {

    @SerializedName("latitude")
    private Double latitude;

    @SerializedName("longitude")
    private Double longitude;

    @SerializedName("generationtime_ms")
    private Double generationTimeMs;

    @SerializedName("utc_offset_seconds")
    private Integer utcOffsetSeconds;

    @SerializedName("timezone")
    private String timezone;

    @SerializedName("timezone_abbreviation")
    private String timezoneAbbreviation;

    @SerializedName("elevation")
    private Double elevation;

    @SerializedName("current")
    private CurrentWeather current;

    @SerializedName("minutely_15")
    private Minutely15Weather minutely15;

    @SerializedName("hourly")
    private HourlyWeather hourly;

    @SerializedName("daily")
    private DailyWeather daily;

    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getGenerationTimeMs() { return generationTimeMs; }
    public Integer getUtcOffsetSeconds() { return utcOffsetSeconds; }
    public String getTimezone() { return timezone; }
    public String getTimezoneAbbreviation() { return timezoneAbbreviation; }
    public Double getElevation() { return elevation; }
    public CurrentWeather getCurrent() { return current; }
    public Minutely15Weather getMinutely15() { return minutely15; }
    public HourlyWeather getHourly() { return hourly; }
    public DailyWeather getDaily() { return daily; }

    public static class CurrentWeather {

        @SerializedName("time") private String time;
        @SerializedName("interval") private Integer interval;
        @SerializedName("temperature_2m") private Double temperature2m;
        @SerializedName("relative_humidity_2m") private Double relativeHumidity2m;
        @SerializedName("apparent_temperature") private Double apparentTemperature;
        @SerializedName("dew_point_2m") private Double dewPoint2m;
        @SerializedName("is_day") private Integer isDay;
        @SerializedName("precipitation") private Double precipitation;
        @SerializedName("rain") private Double rain;
        @SerializedName("showers") private Double showers;
        @SerializedName("snowfall") private Double snowfall;
        @SerializedName("snow_depth") private Double snowDepth;
        @SerializedName("soil_temperature_0cm") private Double soilTemperature0cm;
        @SerializedName("weather_code") private Integer weatherCode;
        @SerializedName("cloud_cover") private Double cloudCover;
        @SerializedName("cloud_cover_low") private Double cloudCoverLow;
        @SerializedName("cloud_cover_mid") private Double cloudCoverMid;
        @SerializedName("cloud_cover_high") private Double cloudCoverHigh;
        @SerializedName("visibility") private Double visibility;
        @SerializedName("pressure_msl") private Double pressureMsl;
        @SerializedName("surface_pressure") private Double surfacePressure;
        @SerializedName("wind_speed_10m") private Double windSpeed10m;
        @SerializedName("wind_direction_10m") private Double windDirection10m;
        @SerializedName("wind_gusts_10m") private Double windGusts10m;

        public String getTime() { return time; }
        public Integer getInterval() { return interval; }
        public Double getTemperature2m() { return temperature2m; }
        public Double getRelativeHumidity2m() { return relativeHumidity2m; }
        public Double getApparentTemperature() { return apparentTemperature; }
        public Double getDewPoint2m() { return dewPoint2m; }
        public Integer getIsDay() { return isDay; }
        public Double getPrecipitation() { return precipitation; }
        public Double getRain() { return rain; }
        public Double getShowers() { return showers; }
        public Double getSnowfall() { return snowfall; }
        public Double getSnowDepth() { return snowDepth; }
        public Double getSoilTemperature0cm() { return soilTemperature0cm; }
        public Integer getWeatherCode() { return weatherCode; }
        public Double getCloudCover() { return cloudCover; }
        public Double getCloudCoverLow() { return cloudCoverLow; }
        public Double getCloudCoverMid() { return cloudCoverMid; }
        public Double getCloudCoverHigh() { return cloudCoverHigh; }
        public Double getVisibility() { return visibility; }
        public Double getPressureMsl() { return pressureMsl; }
        public Double getSurfacePressure() { return surfacePressure; }
        public Double getWindSpeed10m() { return windSpeed10m; }
        public Double getWindDirection10m() { return windDirection10m; }
        public Double getWindGusts10m() { return windGusts10m; }
    }

    public static class Minutely15Weather {

        @SerializedName("time") private List<String> time;
        @SerializedName("precipitation") private List<Double> precipitation;
        @SerializedName("rain") private List<Double> rain;
        @SerializedName("showers") private List<Double> showers;
        @SerializedName("snowfall") private List<Double> snowfall;
        @SerializedName("weather_code") private List<Integer> weatherCode;
        @SerializedName("cloud_cover") private List<Double> cloudCover;
        @SerializedName("visibility") private List<Double> visibility;

        public List<String> getTime() { return time; }
        public List<Double> getPrecipitation() { return precipitation; }
        public List<Double> getRain() { return rain; }
        public List<Double> getShowers() { return showers; }
        public List<Double> getSnowfall() { return snowfall; }
        public List<Integer> getWeatherCode() { return weatherCode; }
        public List<Double> getCloudCover() { return cloudCover; }
        public List<Double> getVisibility() { return visibility; }
    }

    public static class HourlyWeather {

        @SerializedName("time") private List<String> time;
        @SerializedName("temperature_2m") private List<Double> temperature2m;
        @SerializedName("relative_humidity_2m") private List<Double> relativeHumidity2m;
        @SerializedName("apparent_temperature") private List<Double> apparentTemperature;
        @SerializedName("dew_point_2m") private List<Double> dewPoint2m;
        @SerializedName("is_day") private List<Integer> isDay;
        @SerializedName("precipitation_probability") private List<Double> precipitationProbability;
        @SerializedName("precipitation") private List<Double> precipitation;
        @SerializedName("rain") private List<Double> rain;
        @SerializedName("showers") private List<Double> showers;
        @SerializedName("snowfall") private List<Double> snowfall;
        @SerializedName("snow_depth") private List<Double> snowDepth;
        @SerializedName("soil_temperature_0cm") private List<Double> soilTemperature0cm;
        @SerializedName("weather_code") private List<Integer> weatherCode;
        @SerializedName("cloud_cover") private List<Double> cloudCover;
        @SerializedName("cloud_cover_low") private List<Double> cloudCoverLow;
        @SerializedName("cloud_cover_mid") private List<Double> cloudCoverMid;
        @SerializedName("cloud_cover_high") private List<Double> cloudCoverHigh;
        @SerializedName("visibility") private List<Double> visibility;
        @SerializedName("pressure_msl") private List<Double> pressureMsl;
        @SerializedName("wind_speed_10m") private List<Double> windSpeed10m;
        @SerializedName("wind_direction_10m") private List<Double> windDirection10m;
        @SerializedName("wind_gusts_10m") private List<Double> windGusts10m;

        public List<String> getTime() { return time; }
        public List<Double> getTemperature2m() { return temperature2m; }
        public List<Double> getRelativeHumidity2m() { return relativeHumidity2m; }
        public List<Double> getApparentTemperature() { return apparentTemperature; }
        public List<Double> getDewPoint2m() { return dewPoint2m; }
        public List<Integer> getIsDay() { return isDay; }
        public List<Double> getPrecipitationProbability() { return precipitationProbability; }
        public List<Double> getPrecipitation() { return precipitation; }
        public List<Double> getRain() { return rain; }
        public List<Double> getShowers() { return showers; }
        public List<Double> getSnowfall() { return snowfall; }
        public List<Double> getSnowDepth() { return snowDepth; }
        public List<Double> getSoilTemperature0cm() { return soilTemperature0cm; }
        public List<Integer> getWeatherCode() { return weatherCode; }
        public List<Double> getCloudCover() { return cloudCover; }
        public List<Double> getCloudCoverLow() { return cloudCoverLow; }
        public List<Double> getCloudCoverMid() { return cloudCoverMid; }
        public List<Double> getCloudCoverHigh() { return cloudCoverHigh; }
        public List<Double> getVisibility() { return visibility; }
        public List<Double> getPressureMsl() { return pressureMsl; }
        public List<Double> getWindSpeed10m() { return windSpeed10m; }
        public List<Double> getWindDirection10m() { return windDirection10m; }
        public List<Double> getWindGusts10m() { return windGusts10m; }
    }

    public static class DailyWeather {

        @SerializedName("time") private List<String> time;
        @SerializedName("weather_code") private List<Integer> weatherCode;
        @SerializedName("temperature_2m_max") private List<Double> temperature2mMax;
        @SerializedName("temperature_2m_min") private List<Double> temperature2mMin;
        @SerializedName("apparent_temperature_max") private List<Double> apparentTemperatureMax;
        @SerializedName("apparent_temperature_min") private List<Double> apparentTemperatureMin;
        @SerializedName("sunrise") private List<String> sunrise;
        @SerializedName("sunset") private List<String> sunset;
        @SerializedName("daylight_duration") private List<Double> daylightDuration;
        @SerializedName("sunshine_duration") private List<Double> sunshineDuration;
        @SerializedName("uv_index_max") private List<Double> uvIndexMax;
        @SerializedName("precipitation_sum") private List<Double> precipitationSum;
        @SerializedName("rain_sum") private List<Double> rainSum;
        @SerializedName("showers_sum") private List<Double> showersSum;
        @SerializedName("snowfall_sum") private List<Double> snowfallSum;
        @SerializedName("precipitation_hours") private List<Double> precipitationHours;
        @SerializedName("precipitation_probability_max") private List<Double> precipitationProbabilityMax;
        @SerializedName("wind_speed_10m_max") private List<Double> windSpeed10mMax;
        @SerializedName("wind_gusts_10m_max") private List<Double> windGusts10mMax;
        @SerializedName("wind_direction_10m_dominant") private List<Double> windDirection10mDominant;

        public List<String> getTime() { return time; }
        public List<Integer> getWeatherCode() { return weatherCode; }
        public List<Double> getTemperature2mMax() { return temperature2mMax; }
        public List<Double> getTemperature2mMin() { return temperature2mMin; }
        public List<Double> getApparentTemperatureMax() { return apparentTemperatureMax; }
        public List<Double> getApparentTemperatureMin() { return apparentTemperatureMin; }
        public List<String> getSunrise() { return sunrise; }
        public List<String> getSunset() { return sunset; }
        public List<Double> getDaylightDuration() { return daylightDuration; }
        public List<Double> getSunshineDuration() { return sunshineDuration; }
        public List<Double> getUvIndexMax() { return uvIndexMax; }
        public List<Double> getPrecipitationSum() { return precipitationSum; }
        public List<Double> getRainSum() { return rainSum; }
        public List<Double> getShowersSum() { return showersSum; }
        public List<Double> getSnowfallSum() { return snowfallSum; }
        public List<Double> getPrecipitationHours() { return precipitationHours; }
        public List<Double> getPrecipitationProbabilityMax() { return precipitationProbabilityMax; }
        public List<Double> getWindSpeed10mMax() { return windSpeed10mMax; }
        public List<Double> getWindGusts10mMax() { return windGusts10mMax; }
        public List<Double> getWindDirection10mDominant() { return windDirection10mDominant; }
    }
}

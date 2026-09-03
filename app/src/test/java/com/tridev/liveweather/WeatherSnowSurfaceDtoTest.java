package com.tridev.liveweather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.Gson;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import org.junit.Test;

public final class WeatherSnowSurfaceDtoTest {

    @Test
    public void parsesCurrentSnowDepthAndSurfaceTemperature() {
        WeatherResponse response = new Gson().fromJson(
                "{\"current\":{\"snow_depth\":0.12,\"soil_temperature_0cm\":-1.7}}",
                WeatherResponse.class
        );

        assertNotNull(response.getCurrent());
        assertEquals(0.12, response.getCurrent().getSnowDepth(), 0.000001);
        assertEquals(-1.7, response.getCurrent().getSoilTemperature0cm(), 0.000001);
    }

    @Test
    public void parsesHourlySnowDepthAndSurfaceTemperatureSeries() {
        WeatherResponse response = new Gson().fromJson(
                "{\"hourly\":{\"snow_depth\":[0.0,0.03,0.07],\"soil_temperature_0cm\":[1.2,0.1,-0.8]}}",
                WeatherResponse.class
        );

        assertNotNull(response.getHourly());
        assertEquals(3, response.getHourly().getSnowDepth().size());
        assertEquals(0.07, response.getHourly().getSnowDepth().get(2), 0.000001);
        assertEquals(-0.8, response.getHourly().getSoilTemperature0cm().get(2), 0.000001);
    }
}

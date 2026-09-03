package com.tridev.liveweather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.Gson;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import org.junit.Test;

public final class WeatherSolarRadiationDtoTest {

    @Test
    public void parsesCurrentSolarRadiationChannels() {
        WeatherResponse response = new Gson().fromJson(
                "{\"current\":{\"shortwave_radiation\":712.0,\"direct_radiation\":534.0,\"diffuse_radiation\":178.0,\"direct_normal_irradiance\":846.0}}",
                WeatherResponse.class
        );

        assertNotNull(response.getCurrent());
        assertEquals(712.0, response.getCurrent().getShortwaveRadiation(), 0.000001);
        assertEquals(534.0, response.getCurrent().getDirectRadiation(), 0.000001);
        assertEquals(178.0, response.getCurrent().getDiffuseRadiation(), 0.000001);
        assertEquals(846.0, response.getCurrent().getDirectNormalIrradiance(), 0.000001);
    }

    @Test
    public void parsesHourlySolarRadiationSeries() {
        WeatherResponse response = new Gson().fromJson(
                "{\"hourly\":{\"shortwave_radiation\":[0.0,240.0,690.0],\"direct_radiation\":[0.0,90.0,510.0],\"diffuse_radiation\":[0.0,150.0,180.0],\"direct_normal_irradiance\":[0.0,220.0,820.0]}}",
                WeatherResponse.class
        );

        assertNotNull(response.getHourly());
        assertEquals(3, response.getHourly().getShortwaveRadiation().size());
        assertEquals(510.0, response.getHourly().getDirectRadiation().get(2), 0.000001);
        assertEquals(180.0, response.getHourly().getDiffuseRadiation().get(2), 0.000001);
        assertEquals(820.0, response.getHourly().getDirectNormalIrradiance().get(2), 0.000001);
    }
}

package com.tridev.liveweather;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import org.junit.Test;

public final class WeatherCloudLayerDtoTest {

    @Test
    public void parsesCurrentAndHourlyVerticalCloudFields() {
        WeatherResponse response = new Gson().fromJson(
                "{\"current\":{\"cloud_cover_low\":71,\"cloud_cover_mid\":44,\"cloud_cover_high\":19}," +
                        "\"hourly\":{\"cloud_cover_low\":[71,62],\"cloud_cover_mid\":[44,38]," +
                        "\"cloud_cover_high\":[19,25]}}",
                WeatherResponse.class
        );

        assertEquals(71d, response.getCurrent().getCloudCoverLow(), 0d);
        assertEquals(44d, response.getCurrent().getCloudCoverMid(), 0d);
        assertEquals(19d, response.getCurrent().getCloudCoverHigh(), 0d);
        assertEquals(62d, response.getHourly().getCloudCoverLow().get(1), 0d);
        assertEquals(38d, response.getHourly().getCloudCoverMid().get(1), 0d);
        assertEquals(25d, response.getHourly().getCloudCoverHigh().get(1), 0d);
    }
}

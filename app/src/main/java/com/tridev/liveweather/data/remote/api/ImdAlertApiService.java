package com.tridev.liveweather.data.remote.api;

import com.tridev.liveweather.data.remote.dto.ImdDistrictWarning;
import com.tridev.liveweather.data.remote.dto.ImdNowcastWarning;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;

public interface ImdAlertApiService {

    @GET("api/v1/districtwarning")
    Call<List<ImdDistrictWarning>> getDistrictWarnings();

    @GET("api/v1/districtnowcast")
    Call<List<ImdNowcastWarning>> getDistrictNowcast();
}

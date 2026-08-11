package com.tridev.liveweather.data.remote.dto;

import com.google.gson.annotations.SerializedName;

public final class ImdDistrictWarning {

    @SerializedName(value = "Obj_id", alternate = {"OBJ_ID", "obj_id"})
    private String objectId;
    @SerializedName(value = "Date", alternate = {"date"})
    private String date;
    @SerializedName(value = "UTC", alternate = {"utc"})
    private String utc;
    @SerializedName(value = "District", alternate = {"district"})
    private String district;
    @SerializedName(value = "Day_1", alternate = {"day_1"})
    private String day1;
    @SerializedName(value = "Day_2", alternate = {"day_2"})
    private String day2;
    @SerializedName(value = "Day_3", alternate = {"day_3"})
    private String day3;
    @SerializedName(value = "Day_4", alternate = {"day_4"})
    private String day4;
    @SerializedName(value = "Day_5", alternate = {"day_5"})
    private String day5;
    @SerializedName(value = "Day1_Color", alternate = {"day1_color", "Day_1_Color"})
    private String day1Color;
    @SerializedName(value = "Day2_Color", alternate = {"day2_color", "Day_2_Color"})
    private String day2Color;
    @SerializedName(value = "Day3_Color", alternate = {"day3_color", "Day_3_Color"})
    private String day3Color;
    @SerializedName(value = "Day4_Color", alternate = {"day4_color", "Day_4_Color"})
    private String day4Color;
    @SerializedName(value = "Day5_Color", alternate = {"day5_color", "Day_5_Color"})
    private String day5Color;

    public String getObjectId() { return objectId; }
    public String getDate() { return date; }
    public String getUtc() { return utc; }
    public String getDistrict() { return district; }

    public String dayCodes(int dayIndex) {
        switch (dayIndex) {
            case 0: return day1;
            case 1: return day2;
            case 2: return day3;
            case 3: return day4;
            case 4: return day5;
            default: return null;
        }
    }

    public String dayColor(int dayIndex) {
        switch (dayIndex) {
            case 0: return day1Color;
            case 1: return day2Color;
            case 2: return day3Color;
            case 3: return day4Color;
            case 4: return day5Color;
            default: return null;
        }
    }
}

package com.tridev.liveweather.data.remote.dto;

import com.google.gson.annotations.SerializedName;

public final class ImdNowcastWarning {

    @SerializedName(value = "Station", alternate = {"station", "District", "district"})
    private String station;
    @SerializedName(value = "Date", alternate = {"date"})
    private String date;
    @SerializedName(value = "message", alternate = {"Message", "warning"})
    private String message;
    @SerializedName(value = "toi", alternate = {"TOI", "time_of_issue"})
    private String timeOfIssue;
    @SerializedName(value = "Vupto", alternate = {"vupto", "valid_upto"})
    private String validUpto;
    @SerializedName(value = "color", alternate = {"Color"})
    private String color;

    @SerializedName(value = "Cat1", alternate = {"cat1"}) private String cat1;
    @SerializedName(value = "Cat2", alternate = {"cat2"}) private String cat2;
    @SerializedName(value = "Cat3", alternate = {"cat3"}) private String cat3;
    @SerializedName(value = "Cat4", alternate = {"cat4"}) private String cat4;
    @SerializedName(value = "Cat5", alternate = {"cat5"}) private String cat5;
    @SerializedName(value = "Cat6", alternate = {"cat6"}) private String cat6;
    @SerializedName(value = "Cat7", alternate = {"cat7"}) private String cat7;
    @SerializedName(value = "Cat8", alternate = {"cat8"}) private String cat8;
    @SerializedName(value = "Cat9", alternate = {"cat9"}) private String cat9;
    @SerializedName(value = "Cat10", alternate = {"cat10"}) private String cat10;
    @SerializedName(value = "Cat11", alternate = {"cat11"}) private String cat11;
    @SerializedName(value = "Cat12", alternate = {"cat12"}) private String cat12;
    @SerializedName(value = "Cat13", alternate = {"cat13"}) private String cat13;
    @SerializedName(value = "Cat14", alternate = {"cat14"}) private String cat14;
    @SerializedName(value = "Cat15", alternate = {"cat15"}) private String cat15;
    @SerializedName(value = "Cat16", alternate = {"cat16"}) private String cat16;
    @SerializedName(value = "Cat17", alternate = {"cat17"}) private String cat17;
    @SerializedName(value = "Cat18", alternate = {"cat18"}) private String cat18;
    @SerializedName(value = "Cat19", alternate = {"cat19"}) private String cat19;

    public String getStation() { return station; }
    public String getDate() { return date; }
    public String getMessage() { return message; }
    public String getTimeOfIssue() { return timeOfIssue; }
    public String getValidUpto() { return validUpto; }
    public String getColor() { return color; }

    public String[] categories() {
        return new String[]{
                cat1, cat2, cat3, cat4, cat5, cat6, cat7, cat8, cat9, cat10,
                cat11, cat12, cat13, cat14, cat15, cat16, cat17, cat18, cat19
        };
    }
}

package com.jamespizzurro.metrorailserver.domain.marshallers;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class ElevatorIncident {

    private static final String DATETIME_STRING_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    @SerializedName("DateOutOfServ")
    private String outOfServiceDateString;

    @SerializedName("DateUpdated")
    private String updatedDateString;

    @SerializedName("EstimatedReturnToService")
    private String estimatedReturnToServiceDateString;

    @SerializedName("LocationDescription")
    private String locationDescription;

    @SerializedName("StationCode")
    private String stationCode;

    @SerializedName("StationName")
    private String stationName;

    @SerializedName("SymptomDescription")
    private String symptomDescription;

    @SerializedName("UnitName")
    private String unitName;

    @SerializedName("UnitType")
    private String unitType;

    public ElevatorIncident() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ElevatorIncident that = (ElevatorIncident) o;
        return Objects.equals(outOfServiceDateString, that.outOfServiceDateString) &&
                Objects.equals(updatedDateString, that.updatedDateString) &&
                Objects.equals(estimatedReturnToServiceDateString, that.estimatedReturnToServiceDateString) &&
                Objects.equals(locationDescription, that.locationDescription) &&
                Objects.equals(stationCode, that.stationCode) &&
                Objects.equals(stationName, that.stationName) &&
                Objects.equals(symptomDescription, that.symptomDescription) &&
                Objects.equals(unitName, that.unitName) &&
                Objects.equals(unitType, that.unitType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(outOfServiceDateString, updatedDateString, estimatedReturnToServiceDateString, locationDescription, stationCode, stationName, symptomDescription, unitName, unitType);
    }

    public String getOutOfServiceDateString() {
        return outOfServiceDateString;
    }

    public String getUpdatedDateString() {
        return updatedDateString;
    }

    public String getEstimatedReturnToServiceDateString() {
        return estimatedReturnToServiceDateString;
    }

    public String getLocationDescription() {
        return locationDescription;
    }

    public String getStationCode() {
        return stationCode;
    }

    public String getStationName() {
        return stationName;
    }

    public String getSymptomDescription() {
        return symptomDescription;
    }

    public String getUnitName() {
        return unitName;
    }

    public String getUnitType() {
        return unitType;
    }
}

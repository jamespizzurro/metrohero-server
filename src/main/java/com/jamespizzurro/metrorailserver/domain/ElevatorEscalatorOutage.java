package com.jamespizzurro.metrorailserver.domain;

import com.jamespizzurro.metrorailserver.domain.marshallers.ElevatorIncident;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;

public class ElevatorEscalatorOutage {

    private static final String DATETIME_STRING_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    private Calendar outOfServiceDate;
    private Calendar updatedDate;
    private Calendar estimatedReturnToServiceDate;
    private String locationDescription;
    private String stationCode;
    private String stationName;
    private String symptomDescription;
    private String unitName;
    private String unitType;

    @Deprecated
    private String outOfServiceDateString;
    @Deprecated
    private String updatedDateString;

    public ElevatorEscalatorOutage() {
    }

    public ElevatorEscalatorOutage(ElevatorIncident elevatorIncident) {
        this.outOfServiceDate = parseOutOfServiceDate(elevatorIncident.getOutOfServiceDateString());
        this.updatedDate = parseUpdatedDate(elevatorIncident.getUpdatedDateString());
        this.estimatedReturnToServiceDate = parseEstimatedReturnToServiceDate(elevatorIncident.getEstimatedReturnToServiceDateString());

        this.outOfServiceDateString = elevatorIncident.getOutOfServiceDateString();
        this.updatedDateString = elevatorIncident.getUpdatedDateString();
        this.locationDescription = elevatorIncident.getLocationDescription();
        this.stationCode = elevatorIncident.getStationCode();
        this.stationName = elevatorIncident.getStationName();
        this.symptomDescription = elevatorIncident.getSymptomDescription();
        this.unitName = elevatorIncident.getUnitName();
        this.unitType = elevatorIncident.getUnitType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ElevatorEscalatorOutage that = (ElevatorEscalatorOutage) o;
        return Objects.equals(outOfServiceDate, that.outOfServiceDate) &&
                Objects.equals(updatedDate, that.updatedDate) &&
                Objects.equals(estimatedReturnToServiceDate, that.estimatedReturnToServiceDate) &&
                Objects.equals(locationDescription, that.locationDescription) &&
                Objects.equals(stationCode, that.stationCode) &&
                Objects.equals(stationName, that.stationName) &&
                Objects.equals(symptomDescription, that.symptomDescription) &&
                Objects.equals(unitName, that.unitName) &&
                Objects.equals(unitType, that.unitType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(outOfServiceDate, updatedDate, estimatedReturnToServiceDate, locationDescription, stationCode, stationName, symptomDescription, unitName, unitType);
    }

    private Calendar parseOutOfServiceDate(String outOfServiceDateString) {
        if (outOfServiceDateString == null) {
            return null;
        }

        Calendar outOfServiceDate = Calendar.getInstance();

        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DATETIME_STRING_FORMAT);
            sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
            outOfServiceDate.setTime(sdf.parse(outOfServiceDateString));
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }

        return outOfServiceDate;
    }

    private Calendar parseUpdatedDate(String updatedDateString) {
        if (updatedDateString == null) {
            return null;
        }

        Calendar updatedDate = Calendar.getInstance();

        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DATETIME_STRING_FORMAT);
            sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
            updatedDate.setTime(sdf.parse(updatedDateString));
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }

        return updatedDate;
    }

    private Calendar parseEstimatedReturnToServiceDate(String estimatedReturnToServiceDateString) {
        if (estimatedReturnToServiceDateString == null) {
            return null;
        }

        Calendar estimatedReturnToServiceDate = Calendar.getInstance();

        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DATETIME_STRING_FORMAT);
            sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
            estimatedReturnToServiceDate.setTime(sdf.parse(estimatedReturnToServiceDateString));
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }

        return estimatedReturnToServiceDate;
    }

    public Calendar getOutOfServiceDate() {
        return outOfServiceDate;
    }

    public void setOutOfServiceDate(Calendar outOfServiceDate) {
        this.outOfServiceDate = outOfServiceDate;
    }

    public Calendar getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(Calendar updatedDate) {
        this.updatedDate = updatedDate;
    }

    public Calendar getEstimatedReturnToServiceDate() {
        return estimatedReturnToServiceDate;
    }

    public void setEstimatedReturnToServiceDate(Calendar estimatedReturnToServiceDate) {
        this.estimatedReturnToServiceDate = estimatedReturnToServiceDate;
    }

    public String getLocationDescription() {
        return locationDescription;
    }

    public void setLocationDescription(String locationDescription) {
        this.locationDescription = locationDescription;
    }

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public String getSymptomDescription() {
        return symptomDescription;
    }

    public void setSymptomDescription(String symptomDescription) {
        this.symptomDescription = symptomDescription;
    }

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public String getUnitType() {
        return unitType;
    }

    public void setUnitType(String unitType) {
        this.unitType = unitType;
    }
}

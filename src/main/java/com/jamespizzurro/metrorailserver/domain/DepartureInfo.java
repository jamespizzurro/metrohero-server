package com.jamespizzurro.metrorailserver.domain;

import java.util.Calendar;

public class DepartureInfo {

    private Calendar departureTime;
    private Double timeSinceLastDeparture;    // in minutes
    private String trainId;
    private Integer directionNumber;

    public DepartureInfo(Calendar departureTime, String trainId, Integer directionNumber) {
        this.departureTime = departureTime;
        this.trainId = trainId;
        this.directionNumber = directionNumber;
    }

    public Calendar getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(Calendar departureTime) {
        this.departureTime = departureTime;
    }

    public Double getTimeSinceLastDeparture() {
        return timeSinceLastDeparture;
    }

    public void setTimeSinceLastDeparture(Double timeSinceLastDeparture) {
        this.timeSinceLastDeparture = timeSinceLastDeparture;
    }

    public String getTrainId() {
        return trainId;
    }

    public void setTrainId(String trainId) {
        this.trainId = trainId;
    }

    public Integer getDirectionNumber() {
        return directionNumber;
    }

    public void setDirectionNumber(Integer directionNumber) {
        this.directionNumber = directionNumber;
    }
}

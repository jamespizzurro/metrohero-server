package com.jamespizzurro.metrorailserver.domain;

import java.util.Calendar;
import java.util.Objects;

public class ArrivalInfo {

    private Calendar arrivalTime;
    private Double timeSinceLastArrival;    // in minutes
    private String trainId;
    private Integer directionNumber;

    public ArrivalInfo(Calendar arrivalTime, String trainId, Integer directionNumber) {
        this.arrivalTime = arrivalTime;
        this.trainId = trainId;
        this.directionNumber = directionNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrivalInfo that = (ArrivalInfo) o;
        return Objects.equals(arrivalTime, that.arrivalTime) &&
                Objects.equals(timeSinceLastArrival, that.timeSinceLastArrival) &&
                Objects.equals(trainId, that.trainId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(arrivalTime, timeSinceLastArrival, trainId);
    }

    public Calendar getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(Calendar arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public Double getTimeSinceLastArrival() {
        return timeSinceLastArrival;
    }

    public void setTimeSinceLastArrival(Double timeSinceLastArrival) {
        this.timeSinceLastArrival = timeSinceLastArrival;
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

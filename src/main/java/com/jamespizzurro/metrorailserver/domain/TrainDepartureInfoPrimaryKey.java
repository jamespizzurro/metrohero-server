package com.jamespizzurro.metrorailserver.domain;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Objects;

public class TrainDepartureInfoPrimaryKey implements Serializable {

    private String lineCode;
    private Integer directionNumber;
    private String departureStationCode;
    private Calendar observedDepartureTime;
    private Calendar scheduledDepartureTime;

    public TrainDepartureInfoPrimaryKey() {
    }

    public TrainDepartureInfoPrimaryKey(String lineCode, Integer directionNumber, String departureStationCode, Calendar observedDepartureTime, Calendar scheduledDepartureTime) {
        this.lineCode = lineCode;
        this.directionNumber = directionNumber;
        this.departureStationCode = departureStationCode;
        this.observedDepartureTime = observedDepartureTime;
        this.scheduledDepartureTime = scheduledDepartureTime;
    }

    public TrainDepartureInfoPrimaryKey(TrainDepartureInfo trainDepartureInfo) {
        this.lineCode = trainDepartureInfo.getLineCode();
        this.directionNumber = trainDepartureInfo.getDirectionNumber();
        this.departureStationCode = trainDepartureInfo.getDepartureStationCode();
        this.observedDepartureTime = trainDepartureInfo.getObservedDepartureTime();
        this.scheduledDepartureTime = trainDepartureInfo.getScheduledDepartureTime();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrainDepartureInfoPrimaryKey that = (TrainDepartureInfoPrimaryKey) o;
        return Objects.equals(lineCode, that.lineCode) &&
                Objects.equals(directionNumber, that.directionNumber) &&
                Objects.equals(departureStationCode, that.departureStationCode) &&
                Objects.equals(observedDepartureTime, that.observedDepartureTime) &&
                Objects.equals(scheduledDepartureTime, that.scheduledDepartureTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineCode, directionNumber, departureStationCode, observedDepartureTime, scheduledDepartureTime);
    }
}

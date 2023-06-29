package com.jamespizzurro.metrorailserver.domain;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Objects;

public class TrainDeparturePrimaryKey implements Serializable {

    private String lineCode;
    private Integer directionNumber;
    private String departureStationCode;
    private Calendar departureTime;

    public TrainDeparturePrimaryKey() {
    }

    public TrainDeparturePrimaryKey(String lineCode, Integer directionNumber, String departureStationCode, Calendar departureTime) {
        this.lineCode = lineCode;
        this.directionNumber = directionNumber;
        this.departureStationCode = departureStationCode;
        this.departureTime = departureTime;
    }

    public TrainDeparturePrimaryKey(TrainDeparture trainDeparture) {
        this.lineCode = trainDeparture.getLineCode();
        this.directionNumber = trainDeparture.getDirectionNumber();
        this.departureStationCode = trainDeparture.getDepartureStationCode();
        this.departureTime = trainDeparture.getDepartureTime();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrainDeparturePrimaryKey that = (TrainDeparturePrimaryKey) o;
        return Objects.equals(lineCode, that.lineCode) &&
                Objects.equals(directionNumber, that.directionNumber) &&
                Objects.equals(departureStationCode, that.departureStationCode) &&
                Objects.equals(departureTime, that.departureTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineCode, directionNumber, departureStationCode, departureTime);
    }
}

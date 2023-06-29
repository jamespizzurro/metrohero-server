package com.jamespizzurro.metrorailserver.domain;

import java.util.Calendar;
import java.util.Objects;

public class ServiceGap {

    private String lineCode;
    private Integer directionNumber;
    private String direction;
    private String fromStationCode;
    private String fromStationName;
    private String toStationCode;
    private String toStationName;
    private String fromTrainId;
    private String toTrainId;
    private Double timeBetweenTrains;
    private Double scheduledTimeBetweenTrains;
    private Calendar observedDate;

    public ServiceGap(String lineCode, Integer directionNumber, String direction, String fromStationCode, String fromStationName, String toStationCode, String toStationName, String fromTrainId, String toTrainId, Double timeBetweenTrains, Double scheduledTimeBetweenTrains, Calendar observedDate) {
        this.lineCode = lineCode;
        this.directionNumber = directionNumber;
        this.direction = direction;
        this.fromStationCode = fromStationCode;
        this.fromStationName = fromStationName;
        this.toStationCode = toStationCode;
        this.toStationName = toStationName;
        this.fromTrainId = fromTrainId;
        this.toTrainId = toTrainId;
        this.timeBetweenTrains = timeBetweenTrains;
        this.scheduledTimeBetweenTrains = scheduledTimeBetweenTrains;
        this.observedDate = observedDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceGap that = (ServiceGap) o;
        return Objects.equals(fromTrainId, that.fromTrainId) &&
                Objects.equals(toTrainId, that.toTrainId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromTrainId, toTrainId);
    }

    public String getLineCode() {
        return lineCode;
    }

    public void setLineCode(String lineCode) {
        this.lineCode = lineCode;
    }

    public Integer getDirectionNumber() {
        return directionNumber;
    }

    public void setDirectionNumber(Integer directionNumber) {
        this.directionNumber = directionNumber;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getFromStationCode() {
        return fromStationCode;
    }

    public void setFromStationCode(String fromStationCode) {
        this.fromStationCode = fromStationCode;
    }

    public String getFromStationName() {
        return fromStationName;
    }

    public void setFromStationName(String fromStationName) {
        this.fromStationName = fromStationName;
    }

    public String getToStationCode() {
        return toStationCode;
    }

    public void setToStationCode(String toStationCode) {
        this.toStationCode = toStationCode;
    }

    public String getToStationName() {
        return toStationName;
    }

    public void setToStationName(String toStationName) {
        this.toStationName = toStationName;
    }

    public String getFromTrainId() {
        return fromTrainId;
    }

    public void setFromTrainId(String fromTrainId) {
        this.fromTrainId = fromTrainId;
    }

    public String getToTrainId() {
        return toTrainId;
    }

    public void setToTrainId(String toTrainId) {
        this.toTrainId = toTrainId;
    }

    public Double getTimeBetweenTrains() {
        return timeBetweenTrains;
    }

    public void setTimeBetweenTrains(Double timeBetweenTrains) {
        this.timeBetweenTrains = timeBetweenTrains;
    }

    public Double getScheduledTimeBetweenTrains() {
        return scheduledTimeBetweenTrains;
    }

    public void setScheduledTimeBetweenTrains(Double scheduledTimeBetweenTrains) {
        this.scheduledTimeBetweenTrains = scheduledTimeBetweenTrains;
    }

    public Calendar getObservedDate() {
        return observedDate;
    }

    public void setObservedDate(Calendar observedDate) {
        this.observedDate = observedDate;
    }
}

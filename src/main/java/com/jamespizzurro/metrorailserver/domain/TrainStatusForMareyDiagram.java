package com.jamespizzurro.metrorailserver.domain;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

// derived from TrainStatus
public class TrainStatusForMareyDiagram {

    private String realTrainId;
    private String destinationStationName;
    private String lineCode;
    private String locationStationCode;
    private String locationStationName;
    private String eta;
    private Integer directionNumber;
    private String previousStationCode;
    private Integer distanceFromNextStation;
    private UUID tripId;
    private Date observedDate;

    public TrainStatusForMareyDiagram() {}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrainStatusForMareyDiagram that = (TrainStatusForMareyDiagram) o;
        return Objects.equals(realTrainId, that.realTrainId) &&
                Objects.equals(destinationStationName, that.destinationStationName) &&
                Objects.equals(lineCode, that.lineCode) &&
                Objects.equals(locationStationCode, that.locationStationCode) &&
                Objects.equals(locationStationName, that.locationStationName) &&
                Objects.equals(eta, that.eta) &&
                Objects.equals(directionNumber, that.directionNumber) &&
                Objects.equals(previousStationCode, that.previousStationCode) &&
                Objects.equals(distanceFromNextStation, that.distanceFromNextStation) &&
                Objects.equals(tripId, that.tripId) &&
                Objects.equals(observedDate, that.observedDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(realTrainId, destinationStationName, lineCode, locationStationCode, locationStationName, eta, directionNumber, previousStationCode, distanceFromNextStation, tripId, observedDate);
    }

    public String getRealTrainId() {
        return realTrainId;
    }

    public void setRealTrainId(String realTrainId) {
        this.realTrainId = realTrainId;
    }

    public String getDestinationStationName() {
        return destinationStationName;
    }

    public void setDestinationStationName(String destinationStationName) {
        this.destinationStationName = destinationStationName;
    }

    public String getLineCode() {
        return lineCode;
    }

    public void setLineCode(String lineCode) {
        this.lineCode = lineCode;
    }

    public String getLocationStationCode() {
        return locationStationCode;
    }

    public void setLocationStationCode(String locationStationCode) {
        this.locationStationCode = locationStationCode;
    }

    public String getLocationStationName() {
        return locationStationName;
    }

    public void setLocationStationName(String locationStationName) {
        this.locationStationName = locationStationName;
    }

    public String getEta() {
        return eta;
    }

    public void setEta(String eta) {
        this.eta = eta;
    }

    public Integer getDirectionNumber() {
        return directionNumber;
    }

    public void setDirectionNumber(Integer directionNumber) {
        this.directionNumber = directionNumber;
    }

    public String getPreviousStationCode() {
        return previousStationCode;
    }

    public void setPreviousStationCode(String previousStationCode) {
        this.previousStationCode = previousStationCode;
    }

    public Integer getDistanceFromNextStation() {
        return distanceFromNextStation;
    }

    public void setDistanceFromNextStation(Integer distanceFromNextStation) {
        this.distanceFromNextStation = distanceFromNextStation;
    }

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }

    public Date getObservedDate() {
        return observedDate;
    }

    public void setObservedDate(Date observedDate) {
        this.observedDate = observedDate;
    }
}

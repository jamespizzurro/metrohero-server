package com.jamespizzurro.metrorailserver.domain;

import com.jamespizzurro.metrorailserver.StationUtil;

import javax.persistence.*;
import java.util.Calendar;
import java.util.Objects;

@Entity
@Table(name = "train_departure",
        indexes = {
                @Index(name = "train_departure_departure_time_type_index", columnList = "departureTime,type"),
                @Index(name = "train_departure_reconciliation_index", columnList = "departureStationCode,lineCode,directionNumber,departureTime,type")
        }
)
@IdClass(TrainDeparturePrimaryKey.class)
public class TrainDeparture {

    public enum Type {
        SCHEDULED,
        OBSERVED
    }

    @Id
    private String lineCode;

    private String lineName;

    @Id
    private Integer directionNumber;

    private String directionName;

    @Id
    private String departureStationCode;

    private String departureStationName;

    private String destinationStationCode;

    private String destinationStationName;

    @Id
    private Calendar departureTime;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Type type;

    // scheduled departures only
    private String tripId;

    // observed departures only
    private String trainId;
    private String realTrainId;
    private Integer numCars;

    public TrainDeparture() {}

    // scheduled departures only
    public TrainDeparture(String lineCode, Integer directionNumber, String departureStationCode, String departureStationName, String destinationStationCode, String destinationStationName, Calendar departureTime, String tripId) {
        this.lineCode = lineCode;
        this.lineName = getLineName(lineCode);
        this.directionNumber = directionNumber;
        this.directionName = StationUtil.getDirectionName(lineCode, directionNumber);
        this.departureStationCode = departureStationCode;
        this.departureStationName = departureStationName;
        this.destinationStationCode = destinationStationCode;
        this.destinationStationName = destinationStationName;
        this.departureTime = departureTime;

        this.tripId = tripId;

        this.type = Type.SCHEDULED;
    }

    // observed departures only
    public TrainDeparture(String lineCode, Integer directionNumber, String departureStationCode, String departureStationName, String destinationStationCode, String destinationStationName, Calendar departureTime, String trainId, String realTrainId, Integer numCars) {
        this.lineCode = lineCode;
        this.lineName = getLineName(lineCode);
        this.directionNumber = directionNumber;
        this.directionName = StationUtil.getDirectionName(lineCode, directionNumber);
        this.departureStationCode = departureStationCode;
        this.departureStationName = departureStationName;
        this.destinationStationCode = destinationStationCode;
        this.destinationStationName = destinationStationName;
        this.departureTime = departureTime;

        this.trainId = trainId;
        this.realTrainId = realTrainId;
        this.numCars = numCars;

        this.type = Type.OBSERVED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrainDeparture that = (TrainDeparture) o;
        return Objects.equals(lineCode, that.lineCode) &&
                Objects.equals(lineName, that.lineName) &&
                Objects.equals(directionNumber, that.directionNumber) &&
                Objects.equals(directionName, that.directionName) &&
                Objects.equals(departureStationCode, that.departureStationCode) &&
                Objects.equals(departureStationName, that.departureStationName) &&
                Objects.equals(destinationStationCode, that.destinationStationCode) &&
                Objects.equals(destinationStationName, that.destinationStationName) &&
                Objects.equals(departureTime, that.departureTime) &&
                type == that.type &&
                Objects.equals(tripId, that.tripId) &&
                Objects.equals(trainId, that.trainId) &&
                Objects.equals(realTrainId, that.realTrainId) &&
                Objects.equals(numCars, that.numCars);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineCode, lineName, directionNumber, directionName, departureStationCode, departureStationName, destinationStationCode, destinationStationName, departureTime, type, tripId, trainId, realTrainId, numCars);
    }

    private String getLineName(String lineCode) {
        return StationUtil.getLineName(lineCode);
    }

    public String getLineCode() {
        return lineCode;
    }

    public void setLineCode(String lineCode) {
        this.lineCode = lineCode;
    }

    public String getLineName() {
        return lineName;
    }

    public void setLineName(String lineName) {
        this.lineName = lineName;
    }

    public Integer getDirectionNumber() {
        return directionNumber;
    }

    public void setDirectionNumber(Integer directionNumber) {
        this.directionNumber = directionNumber;
    }

    public String getDirectionName() {
        return directionName;
    }

    public void setDirectionName(String directionName) {
        this.directionName = directionName;
    }

    public String getDepartureStationCode() {
        return departureStationCode;
    }

    public void setDepartureStationCode(String departureStationCode) {
        this.departureStationCode = departureStationCode;
    }

    public String getDepartureStationName() {
        return departureStationName;
    }

    public void setDepartureStationName(String departureStationName) {
        this.departureStationName = departureStationName;
    }

    public String getDestinationStationCode() {
        return destinationStationCode;
    }

    public void setDestinationStationCode(String destinationStationCode) {
        this.destinationStationCode = destinationStationCode;
    }

    public String getDestinationStationName() {
        return destinationStationName;
    }

    public void setDestinationStationName(String destinationStationName) {
        this.destinationStationName = destinationStationName;
    }

    public Calendar getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(Calendar departureTime) {
        this.departureTime = departureTime;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getTrainId() {
        return trainId;
    }

    public void setTrainId(String trainId) {
        this.trainId = trainId;
    }

    public String getRealTrainId() {
        return realTrainId;
    }

    public void setRealTrainId(String realTrainId) {
        this.realTrainId = realTrainId;
    }

    public Integer getNumCars() {
        return numCars;
    }

    public void setNumCars(Integer numCars) {
        this.numCars = numCars;
    }
}

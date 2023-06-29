package com.jamespizzurro.metrorailserver.domain;

import com.jamespizzurro.metrorailserver.Exclude;

import javax.persistence.*;
import java.util.Calendar;
import java.util.UUID;

@Entity
@Table(name = "station_to_station_trip",
        indexes = {
                @Index(name = "station_to_station_trip_departing_time_index", columnList = "departingTime", unique = false),
                @Index(name = "station_to_station_trip_arriving_time_index", columnList = "arrivingTime", unique = false)
        }
)
public class StationToStationTrip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false,
            columnDefinition = "bigserial not null")
    @Exclude
    private Long id;

    private String trainId;
    private String realTrainId;
    private String lineCode;
    private String destinationStationCode;
    private Integer numCars;
    private String departingStationCode;
    private Calendar departingTime;
    private String arrivingStationCode;
    private Integer arrivingTrackNumber;
    private Calendar arrivingTime;
    private Double tripDuration;    // in minutes
    private Integer secondsAtDepartingStation;
    private Integer arrivingDirectionNumber;
    private UUID tripId;

    public StationToStationTrip() {
    }

    public Long getId() {
        return id;
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

    public String getLineCode() {
        return lineCode;
    }

    public void setLineCode(String lineCode) {
        this.lineCode = lineCode;
    }

    public String getDestinationStationCode() {
        return destinationStationCode;
    }

    public void setDestinationStationCode(String destinationStationCode) {
        this.destinationStationCode = destinationStationCode;
    }

    public Integer getNumCars() {
        return numCars;
    }

    public void setNumCars(Integer numCars) {
        this.numCars = numCars;
    }

    public String getDepartingStationCode() {
        return departingStationCode;
    }

    public void setDepartingStationCode(String departingStationCode) {
        this.departingStationCode = departingStationCode;
    }

    public Calendar getDepartingTime() {
        return departingTime;
    }

    public void setDepartingTime(Calendar departingTime) {
        this.departingTime = departingTime;
    }

    public String getArrivingStationCode() {
        return arrivingStationCode;
    }

    public void setArrivingStationCode(String arrivingStationCode) {
        this.arrivingStationCode = arrivingStationCode;
    }

    public Integer getArrivingTrackNumber() {
        return arrivingTrackNumber;
    }

    public void setArrivingTrackNumber(Integer arrivingTrackNumber) {
        this.arrivingTrackNumber = arrivingTrackNumber;
    }

    public Calendar getArrivingTime() {
        return arrivingTime;
    }

    public void setArrivingTime(Calendar arrivingTime) {
        this.arrivingTime = arrivingTime;
    }

    public Double getTripDuration() {
        return tripDuration;
    }

    public void setTripDuration(Double tripDuration) {
        this.tripDuration = tripDuration;
    }

    public Integer getSecondsAtDepartingStation() {
        return secondsAtDepartingStation;
    }

    public void setSecondsAtDepartingStation(Integer secondsAtDepartingStation) {
        this.secondsAtDepartingStation = secondsAtDepartingStation;
    }

    public Integer getArrivingDirectionNumber() {
        return arrivingDirectionNumber;
    }

    public void setArrivingDirectionNumber(Integer arrivingDirectionNumber) {
        this.arrivingDirectionNumber = arrivingDirectionNumber;
    }

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }
}

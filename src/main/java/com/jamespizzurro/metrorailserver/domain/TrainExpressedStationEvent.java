package com.jamespizzurro.metrorailserver.domain;

import com.jamespizzurro.metrorailserver.Exclude;

import javax.persistence.*;
import java.util.Calendar;

@Entity
@Table(name = "train_expressed_station_event",
        indexes = {
                @Index(name = "train_expressed_station_event_date_index", columnList = "date")
        }
)
public class TrainExpressedStationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false,
            columnDefinition = "bigserial not null")
    @Exclude
    private Long id;

    @Column(nullable = false)
    private Calendar date;

    private String trainId;
    private String realTrainId;
    private String lineCode;
    private Integer directionNumber;
    private Integer trackNumber;
    private String destinationStationCode;
    private String stationCode;
    private Integer numSecondsAtStation;
    private String numCars;

    public TrainExpressedStationEvent() {
    }

    public TrainExpressedStationEvent(Calendar date, String trainId, String realTrainId, String lineCode, Integer directionNumber, Integer trackNumber, String destinationStationCode, String stationCode, Integer numSecondsAtStation, String numCars) {
        this.date = date;
        this.trainId = trainId;
        this.realTrainId = realTrainId;
        this.lineCode = lineCode;
        this.directionNumber = directionNumber;
        this.trackNumber = trackNumber;
        this.destinationStationCode = destinationStationCode;
        this.stationCode = stationCode;
        this.numSecondsAtStation = numSecondsAtStation;
        this.numCars = numCars;
    }

    public Long getId() {
        return id;
    }

    public Calendar getDate() {
        return date;
    }

    public String getTrainId() {
        return trainId;
    }

    public String getRealTrainId() {
        return realTrainId;
    }

    public String getLineCode() {
        return lineCode;
    }

    public Integer getDirectionNumber() {
        return directionNumber;
    }

    public Integer getTrackNumber() {
        return trackNumber;
    }

    public String getDestinationStationCode() {
        return destinationStationCode;
    }

    public String getStationCode() {
        return stationCode;
    }

    public Integer getNumSecondsAtStation() {
        return numSecondsAtStation;
    }

    public String getNumCars() {
        return numCars;
    }
}

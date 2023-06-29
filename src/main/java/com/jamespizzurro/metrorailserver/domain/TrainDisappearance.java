package com.jamespizzurro.metrorailserver.domain;

import com.jamespizzurro.metrorailserver.Exclude;

import javax.persistence.*;
import java.util.Calendar;

@Entity
@Table(name = "train_disappearance",
        indexes = {
                @Index(name = "train_disappearance_date_index", columnList = "date")
        }
)
public class TrainDisappearance {

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
    private String destinationStationCode;
    private String stationCode;
    private Integer trackCircuitId;

    public TrainDisappearance() {
    }

    public TrainDisappearance(Calendar date, String trainId, String realTrainId, String lineCode, Integer directionNumber, String destinationStationCode, String stationCode, Integer trackCircuitId) {
        this.date = date;
        this.trainId = trainId;
        this.realTrainId = realTrainId;
        this.lineCode = lineCode;
        this.directionNumber = directionNumber;
        this.destinationStationCode = destinationStationCode;
        this.stationCode = stationCode;
        this.trackCircuitId = trackCircuitId;
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

    public String getDestinationStationCode() {
        return destinationStationCode;
    }

    public String getStationCode() {
        return stationCode;
    }

    public Integer getTrackCircuitId() {
        return trackCircuitId;
    }
}

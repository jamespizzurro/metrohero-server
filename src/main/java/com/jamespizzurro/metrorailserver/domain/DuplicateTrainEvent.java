package com.jamespizzurro.metrorailserver.domain;

import com.jamespizzurro.metrorailserver.Exclude;

import javax.persistence.*;
import java.util.Calendar;

@Entity
@Table(name = "duplicate_train_event",
        indexes = {
                @Index(name = "duplicate_train_event_date_index", columnList = "date")
        }
)
public class DuplicateTrainEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false,
            columnDefinition = "bigserial not null")
    @Exclude
    private Long id;

    @Column(nullable = false)
    private Calendar date;

    private String realTrainId;
    private String keptTrainId;
    private String removedTrainId;
    private String lineCode;
    private Integer keptTrainDirectionNumber;
    private Integer removedTrainDirectionNumber;
    private String destinationStationCode;
    private String stationCode;

    public DuplicateTrainEvent() {
    }

    public DuplicateTrainEvent(Calendar date, String realTrainId, String keptTrainId, String removedTrainId, String lineCode, Integer keptTrainDirectionNumber, Integer removedTrainDirectionNumber, String destinationStationCode, String stationCode) {
        this.date = date;
        this.realTrainId = realTrainId;
        this.keptTrainId = keptTrainId;
        this.removedTrainId = removedTrainId;
        this.lineCode = lineCode;
        this.keptTrainDirectionNumber = keptTrainDirectionNumber;
        this.removedTrainDirectionNumber = removedTrainDirectionNumber;
        this.destinationStationCode = destinationStationCode;
        this.stationCode = stationCode;
    }

    public Long getId() {
        return id;
    }

    public Calendar getDate() {
        return date;
    }

    public String getRealTrainId() {
        return realTrainId;
    }

    public String getKeptTrainId() {
        return keptTrainId;
    }

    public String getRemovedTrainId() {
        return removedTrainId;
    }

    public String getLineCode() {
        return lineCode;
    }

    public Integer getKeptTrainDirectionNumber() {
        return keptTrainDirectionNumber;
    }

    public Integer getRemovedTrainDirectionNumber() {
        return removedTrainDirectionNumber;
    }

    public String getDestinationStationCode() {
        return destinationStationCode;
    }

    public String getStationCode() {
        return stationCode;
    }
}

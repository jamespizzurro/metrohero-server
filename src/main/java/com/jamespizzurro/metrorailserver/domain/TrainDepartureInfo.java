package com.jamespizzurro.metrorailserver.domain;

import com.jamespizzurro.metrorailserver.Exclude;

import javax.persistence.*;
import java.util.Calendar;

@Entity
@Table(name = "train_departure_info",
        indexes = {
                @Index(name = "train_departure_info_observed_departure_index", columnList = "departureStationCode,lineCode,directionNumber,observedDepartureTime", unique = true),
                @Index(name = "train_departure_info_scheduled_departure_index", columnList = "departureStationCode,lineCode,directionNumber,scheduledDepartureTime", unique = true),
                @Index(name = "train_departure_info_report_metrics_index", columnList = "observedDepartureTime,departureStationCode,lineCode,directionNumber")
                // CREATE INDEX CONCURRENTLY train_departure_info_departure_time_index ON train_departure_info (coalesce(observed_departure_time, scheduled_departure_time));
                // CREATE INDEX CONCURRENTLY train_departure_info_report_index ON train_departure_info (coalesce(observed_departure_time, scheduled_departure_time), departure_station_code, line_code, direction_number);
                // CREATE INDEX CONCURRENTLY train_departure_info_departure_index ON train_departure_info (departure_station_code, line_code, direction_number, observed_departure_time, scheduled_departure_time);
        }
)
public class TrainDepartureInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false,
            columnDefinition = "bigserial not null")
    @Exclude
    private Long id;

    private String trainId;
    private String realTrainId;
    private String departureStationName;
    private String departureStationCode;
    private String lineName;
    private String lineCode;
    private String directionName;
    private Integer directionNumber;
    private String scheduledDestinationStationName;
    private String scheduledDestinationStationCode;
    private String observedDestinationStationName;
    private String observedDestinationStationCode;
    private Integer observedNumCars;
    private Calendar observedDepartureTime;
    private Calendar scheduledDepartureTime;
    private Double observedTimeSinceLastDeparture;  // in minutes
    private Double scheduledTimeSinceLastDeparture; // in minutes
    private Double headwayDeviation;    // in minutes
    private Double scheduleDeviation;   // in minutes

    public TrainDepartureInfo() {
    }

    public TrainDepartureInfo(String trainId, String realTrainId, String departureStationName, String departureStationCode, String lineName, String lineCode, String directionName, Integer directionNumber, String scheduledDestinationStationName, String scheduledDestinationStationCode, String observedDestinationStationName, String observedDestinationStationCode, Integer observedNumCars, Calendar observedDepartureTime, Calendar scheduledDepartureTime, Double observedTimeSinceLastDeparture, Double scheduledTimeSinceLastDeparture, Double headwayDeviation, Double scheduleDeviation) {
        this.trainId = trainId;
        this.realTrainId = realTrainId;
        this.departureStationName = departureStationName;
        this.departureStationCode = departureStationCode;
        this.lineName = lineName;
        this.lineCode = lineCode;
        this.directionName = directionName;
        this.directionNumber = directionNumber;
        this.scheduledDestinationStationName = scheduledDestinationStationName;
        this.scheduledDestinationStationCode = scheduledDestinationStationCode;
        this.observedDestinationStationName = observedDestinationStationName;
        this.observedDestinationStationCode = observedDestinationStationCode;
        this.observedNumCars = observedNumCars;
        this.observedDepartureTime = observedDepartureTime;
        this.scheduledDepartureTime = scheduledDepartureTime;
        this.observedTimeSinceLastDeparture = observedTimeSinceLastDeparture;
        this.scheduledTimeSinceLastDeparture = scheduledTimeSinceLastDeparture;
        this.headwayDeviation = headwayDeviation;
        this.scheduleDeviation = scheduleDeviation;
    }

    public String getLineNameNonNull() {
        return (lineName != null) ? lineName : "N/A";
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

    public String getDepartureStationName() {
        return departureStationName;
    }

    public void setDepartureStationName(String departureStationName) {
        this.departureStationName = departureStationName;
    }

    public String getDepartureStationCode() {
        return departureStationCode;
    }

    public void setDepartureStationCode(String departureStationCode) {
        this.departureStationCode = departureStationCode;
    }

    public String getLineName() {
        return lineName;
    }

    public void setLineName(String lineName) {
        this.lineName = lineName;
    }

    public String getLineCode() {
        return lineCode;
    }

    public void setLineCode(String lineCode) {
        this.lineCode = lineCode;
    }

    public String getDirectionName() {
        return directionName;
    }

    public void setDirectionName(String directionName) {
        this.directionName = directionName;
    }

    public Integer getDirectionNumber() {
        return directionNumber;
    }

    public void setDirectionNumber(Integer directionNumber) {
        this.directionNumber = directionNumber;
    }

    public String getScheduledDestinationStationName() {
        return scheduledDestinationStationName;
    }

    public void setScheduledDestinationStationName(String scheduledDestinationStationName) {
        this.scheduledDestinationStationName = scheduledDestinationStationName;
    }

    public String getScheduledDestinationStationCode() {
        return scheduledDestinationStationCode;
    }

    public void setScheduledDestinationStationCode(String scheduledDestinationStationCode) {
        this.scheduledDestinationStationCode = scheduledDestinationStationCode;
    }

    public String getObservedDestinationStationName() {
        return observedDestinationStationName;
    }

    public void setObservedDestinationStationName(String observedDestinationStationName) {
        this.observedDestinationStationName = observedDestinationStationName;
    }

    public String getObservedDestinationStationCode() {
        return observedDestinationStationCode;
    }

    public void setObservedDestinationStationCode(String observedDestinationStationCode) {
        this.observedDestinationStationCode = observedDestinationStationCode;
    }

    public Integer getObservedNumCars() {
        return observedNumCars;
    }

    public void setObservedNumCars(Integer observedNumCars) {
        this.observedNumCars = observedNumCars;
    }

    public Calendar getObservedDepartureTime() {
        return observedDepartureTime;
    }

    public void setObservedDepartureTime(Calendar observedDepartureTime) {
        this.observedDepartureTime = observedDepartureTime;
    }

    public Calendar getScheduledDepartureTime() {
        return scheduledDepartureTime;
    }

    public void setScheduledDepartureTime(Calendar scheduledDepartureTime) {
        this.scheduledDepartureTime = scheduledDepartureTime;
    }

    public Double getObservedTimeSinceLastDeparture() {
        return observedTimeSinceLastDeparture;
    }

    public void setObservedTimeSinceLastDeparture(Double observedTimeSinceLastDeparture) {
        this.observedTimeSinceLastDeparture = observedTimeSinceLastDeparture;
    }

    public Double getScheduledTimeSinceLastDeparture() {
        return scheduledTimeSinceLastDeparture;
    }

    public void setScheduledTimeSinceLastDeparture(Double scheduledTimeSinceLastDeparture) {
        this.scheduledTimeSinceLastDeparture = scheduledTimeSinceLastDeparture;
    }

    public Double getHeadwayDeviation() {
        return headwayDeviation;
    }

    public void setHeadwayDeviation(Double headwayDeviation) {
        this.headwayDeviation = headwayDeviation;
    }

    public Double getScheduleDeviation() {
        return scheduleDeviation;
    }

    public void setScheduleDeviation(Double scheduleDeviation) {
        this.scheduleDeviation = scheduleDeviation;
    }
}

package com.jamespizzurro.metrorailserver.domain;

import com.jamespizzurro.metrorailserver.Exclude;

import javax.persistence.*;
import java.util.Calendar;

@Entity
public class TrainPredictionAccuracyMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false,
            columnDefinition = "bigserial not null")
    @Exclude
    private Long id;

    @Column(nullable = false)
    private String predictionSource;

    @Column(nullable = false)
    private Calendar measurementStartTime;

    @Column(nullable = false)
    private Calendar measurementEndTime;

    @Column(nullable = false)
    private String predictionOriginStation;

    @Column(nullable = false)
    private String lineCode;

    @Column(nullable = false)
    private String destinationStationCode;

    @Column(nullable = false)
    private String measurementStartPrediction;

    @Column(nullable = false)
    private Double averagePredictionError;  // in minutes

    @Column(nullable = false)
    private Long numPredictionSamples;

    private boolean hasCorrespondingWmataMeasurement;

    public TrainPredictionAccuracyMeasurement() {
    }

    public TrainPredictionAccuracyMeasurement(String predictionSource, Calendar measurementStartTime, Calendar measurementEndTime, String predictionOriginStation, String lineCode, String destinationStationCode, String measurementStartPrediction, Double averagePredictionError, Long numPredictionSamples, boolean hasCorrespondingWmataMeasurement) {
        this.predictionSource = predictionSource;
        this.measurementStartTime = measurementStartTime;
        this.measurementEndTime = measurementEndTime;
        this.predictionOriginStation = predictionOriginStation;
        this.lineCode = lineCode;
        this.destinationStationCode = destinationStationCode;
        this.measurementStartPrediction = measurementStartPrediction;
        this.averagePredictionError = averagePredictionError;
        this.numPredictionSamples = numPredictionSamples;
        this.hasCorrespondingWmataMeasurement = hasCorrespondingWmataMeasurement;
    }

    public Long getId() {
        return id;
    }

    public String getPredictionSource() {
        return predictionSource;
    }

    public void setPredictionSource(String predictionSource) {
        this.predictionSource = predictionSource;
    }

    public Calendar getMeasurementStartTime() {
        return measurementStartTime;
    }

    public void setMeasurementStartTime(Calendar measurementStartTime) {
        this.measurementStartTime = measurementStartTime;
    }

    public Calendar getMeasurementEndTime() {
        return measurementEndTime;
    }

    public void setMeasurementEndTime(Calendar measurementEndTime) {
        this.measurementEndTime = measurementEndTime;
    }

    public String getPredictionOriginStation() {
        return predictionOriginStation;
    }

    public void setPredictionOriginStation(String predictionOriginStation) {
        this.predictionOriginStation = predictionOriginStation;
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

    public String getMeasurementStartPrediction() {
        return measurementStartPrediction;
    }

    public void setMeasurementStartPrediction(String measurementStartPrediction) {
        this.measurementStartPrediction = measurementStartPrediction;
    }

    public Double getAveragePredictionError() {
        return averagePredictionError;
    }

    public void setAveragePredictionError(Double averagePredictionError) {
        this.averagePredictionError = averagePredictionError;
    }

    public Long getNumPredictionSamples() {
        return numPredictionSamples;
    }

    public void setNumPredictionSamples(Long numPredictionSamples) {
        this.numPredictionSamples = numPredictionSamples;
    }

    public boolean isHasCorrespondingWmataMeasurement() {
        return hasCorrespondingWmataMeasurement;
    }

    public void setHasCorrespondingWmataMeasurement(boolean hasCorrespondingWmataMeasurement) {
        this.hasCorrespondingWmataMeasurement = hasCorrespondingWmataMeasurement;
    }
}

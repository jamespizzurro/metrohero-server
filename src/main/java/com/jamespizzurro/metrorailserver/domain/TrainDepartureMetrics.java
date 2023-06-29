package com.jamespizzurro.metrorailserver.domain;

import java.util.Objects;

public class TrainDepartureMetrics {

    private Integer numObservedDepartures;
    private Integer numScheduledDepartures;
    private Integer numMissedDepartures;
    private Double pctMissedDepartures;
    private Integer numUnscheduledDepartures;
    private Double pctUnscheduledDepartures;
    private Double avgObservedTrainFrequency;   // in minutes
    private Double avgScheduledTrainFrequency;  // in minutes
    private Double avgTrainFrequencyPercentVariance;
    private Double observedTrainFrequencyConsistency;   // in minutes
    private Double scheduledTrainFrequencyConsistency;  // in minutes
    private Double trainFrequencyConsistencyPercentVariance;
    private Double avgObservedPlatformWaitTime; // in minutes
    private Double avgScheduledPlatformWaitTime;    // in minutes
    private Double avgPlatformWaitTimePercentVariance;
    private Double pctHeadwayAdherence;
    private Double avgHeadwayDeviation; // in minutes
    private Double numOnTimeOrEarlyDeparturesByHeadwayAdherence;
    private Double pctOnTimeOrEarlyDeparturesByHeadwayAdherence;
    private Double numLateDeparturesByHeadwayAdherence;
    private Double pctLateDeparturesByHeadwayAdherence;
    private Double numVeryLateDeparturesByHeadwayAdherence;
    private Double pctVeryLateDeparturesByHeadwayAdherence;
    private Double pctScheduleAdherence;
    private Double avgScheduleDeviation;    // in minutes
    private Double numOnTimeDeparturesByScheduleAdherence;
    private Double pctOnTimeDeparturesByScheduleAdherence;
    private Double numOffScheduleDeparturesByScheduleAdherence;
    private Double pctOffScheduleDeparturesByScheduleAdherence;
    private Double numVeryOffScheduleDeparturesByScheduleAdherence;
    private Double pctVeryOffScheduleDeparturesByScheduleAdherence;

    public TrainDepartureMetrics() {
    }

    public TrainDepartureMetrics(Integer numObservedDepartures, Integer numScheduledDepartures, Integer numMissedDepartures, Double pctMissedDepartures, Integer numUnscheduledDepartures, Double pctUnscheduledDepartures, Double avgObservedTrainFrequency, Double avgScheduledTrainFrequency, Double avgTrainFrequencyPercentVariance, Double observedTrainFrequencyConsistency, Double scheduledTrainFrequencyConsistency, Double trainFrequencyConsistencyPercentVariance, Double avgObservedPlatformWaitTime, Double avgScheduledPlatformWaitTime, Double avgPlatformWaitTimePercentVariance, Double pctHeadwayAdherence, Double avgHeadwayDeviation, Double numOnTimeOrEarlyDeparturesByHeadwayAdherence, Double pctOnTimeOrEarlyDeparturesByHeadwayAdherence, Double numLateDeparturesByHeadwayAdherence, Double pctLateDeparturesByHeadwayAdherence, Double numVeryLateDeparturesByHeadwayAdherence, Double pctVeryLateDeparturesByHeadwayAdherence, Double pctScheduleAdherence, Double avgScheduleDeviation, Double numOnTimeDeparturesByScheduleAdherence, Double pctOnTimeDeparturesByScheduleAdherence, Double numOffScheduleDeparturesByScheduleAdherence, Double pctOffScheduleDeparturesByScheduleAdherence, Double numVeryOffScheduleDeparturesByScheduleAdherence, Double pctVeryOffScheduleDeparturesByScheduleAdherence) {
        this.numObservedDepartures = numObservedDepartures;
        this.numScheduledDepartures = numScheduledDepartures;
        this.numMissedDepartures = numMissedDepartures;
        this.pctMissedDepartures = pctMissedDepartures;
        this.numUnscheduledDepartures = numUnscheduledDepartures;
        this.pctUnscheduledDepartures = pctUnscheduledDepartures;
        this.avgObservedTrainFrequency = avgObservedTrainFrequency;
        this.avgScheduledTrainFrequency = avgScheduledTrainFrequency;
        this.avgTrainFrequencyPercentVariance = avgTrainFrequencyPercentVariance;
        this.observedTrainFrequencyConsistency = observedTrainFrequencyConsistency;
        this.scheduledTrainFrequencyConsistency = scheduledTrainFrequencyConsistency;
        this.trainFrequencyConsistencyPercentVariance = trainFrequencyConsistencyPercentVariance;
        this.avgObservedPlatformWaitTime = avgObservedPlatformWaitTime;
        this.avgScheduledPlatformWaitTime = avgScheduledPlatformWaitTime;
        this.avgPlatformWaitTimePercentVariance = avgPlatformWaitTimePercentVariance;
        this.pctHeadwayAdherence = pctHeadwayAdherence;
        this.avgHeadwayDeviation = avgHeadwayDeviation;
        this.numOnTimeOrEarlyDeparturesByHeadwayAdherence = numOnTimeOrEarlyDeparturesByHeadwayAdherence;
        this.pctOnTimeOrEarlyDeparturesByHeadwayAdherence = pctOnTimeOrEarlyDeparturesByHeadwayAdherence;
        this.numLateDeparturesByHeadwayAdherence = numLateDeparturesByHeadwayAdherence;
        this.pctLateDeparturesByHeadwayAdherence = pctLateDeparturesByHeadwayAdherence;
        this.numVeryLateDeparturesByHeadwayAdherence = numVeryLateDeparturesByHeadwayAdherence;
        this.pctVeryLateDeparturesByHeadwayAdherence = pctVeryLateDeparturesByHeadwayAdherence;
        this.pctScheduleAdherence = pctScheduleAdherence;
        this.avgScheduleDeviation = avgScheduleDeviation;
        this.numOnTimeDeparturesByScheduleAdherence = numOnTimeDeparturesByScheduleAdherence;
        this.pctOnTimeDeparturesByScheduleAdherence = pctOnTimeDeparturesByScheduleAdherence;
        this.numOffScheduleDeparturesByScheduleAdherence = numOffScheduleDeparturesByScheduleAdherence;
        this.pctOffScheduleDeparturesByScheduleAdherence = pctOffScheduleDeparturesByScheduleAdherence;
        this.numVeryOffScheduleDeparturesByScheduleAdherence = numVeryOffScheduleDeparturesByScheduleAdherence;
        this.pctVeryOffScheduleDeparturesByScheduleAdherence = pctVeryOffScheduleDeparturesByScheduleAdherence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrainDepartureMetrics that = (TrainDepartureMetrics) o;
        return Objects.equals(numObservedDepartures, that.numObservedDepartures) &&
                Objects.equals(numScheduledDepartures, that.numScheduledDepartures) &&
                Objects.equals(numMissedDepartures, that.numMissedDepartures) &&
                Objects.equals(pctMissedDepartures, that.pctMissedDepartures) &&
                Objects.equals(numUnscheduledDepartures, that.numUnscheduledDepartures) &&
                Objects.equals(pctUnscheduledDepartures, that.pctUnscheduledDepartures) &&
                Objects.equals(avgObservedTrainFrequency, that.avgObservedTrainFrequency) &&
                Objects.equals(avgScheduledTrainFrequency, that.avgScheduledTrainFrequency) &&
                Objects.equals(avgTrainFrequencyPercentVariance, that.avgTrainFrequencyPercentVariance) &&
                Objects.equals(observedTrainFrequencyConsistency, that.observedTrainFrequencyConsistency) &&
                Objects.equals(scheduledTrainFrequencyConsistency, that.scheduledTrainFrequencyConsistency) &&
                Objects.equals(trainFrequencyConsistencyPercentVariance, that.trainFrequencyConsistencyPercentVariance) &&
                Objects.equals(avgObservedPlatformWaitTime, that.avgObservedPlatformWaitTime) &&
                Objects.equals(avgScheduledPlatformWaitTime, that.avgScheduledPlatformWaitTime) &&
                Objects.equals(avgPlatformWaitTimePercentVariance, that.avgPlatformWaitTimePercentVariance) &&
                Objects.equals(pctHeadwayAdherence, that.pctHeadwayAdherence) &&
                Objects.equals(avgHeadwayDeviation, that.avgHeadwayDeviation) &&
                Objects.equals(numOnTimeOrEarlyDeparturesByHeadwayAdherence, that.numOnTimeOrEarlyDeparturesByHeadwayAdherence) &&
                Objects.equals(pctOnTimeOrEarlyDeparturesByHeadwayAdherence, that.pctOnTimeOrEarlyDeparturesByHeadwayAdherence) &&
                Objects.equals(numLateDeparturesByHeadwayAdherence, that.numLateDeparturesByHeadwayAdherence) &&
                Objects.equals(pctLateDeparturesByHeadwayAdherence, that.pctLateDeparturesByHeadwayAdherence) &&
                Objects.equals(numVeryLateDeparturesByHeadwayAdherence, that.numVeryLateDeparturesByHeadwayAdherence) &&
                Objects.equals(pctVeryLateDeparturesByHeadwayAdherence, that.pctVeryLateDeparturesByHeadwayAdherence) &&
                Objects.equals(pctScheduleAdherence, that.pctScheduleAdherence) &&
                Objects.equals(avgScheduleDeviation, that.avgScheduleDeviation) &&
                Objects.equals(numOnTimeDeparturesByScheduleAdherence, that.numOnTimeDeparturesByScheduleAdherence) &&
                Objects.equals(pctOnTimeDeparturesByScheduleAdherence, that.pctOnTimeDeparturesByScheduleAdherence) &&
                Objects.equals(numOffScheduleDeparturesByScheduleAdherence, that.numOffScheduleDeparturesByScheduleAdherence) &&
                Objects.equals(pctOffScheduleDeparturesByScheduleAdherence, that.pctOffScheduleDeparturesByScheduleAdherence) &&
                Objects.equals(numVeryOffScheduleDeparturesByScheduleAdherence, that.numVeryOffScheduleDeparturesByScheduleAdherence) &&
                Objects.equals(pctVeryOffScheduleDeparturesByScheduleAdherence, that.pctVeryOffScheduleDeparturesByScheduleAdherence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numObservedDepartures, numScheduledDepartures, numMissedDepartures, pctMissedDepartures, numUnscheduledDepartures, pctUnscheduledDepartures, avgObservedTrainFrequency, avgScheduledTrainFrequency, avgTrainFrequencyPercentVariance, observedTrainFrequencyConsistency, scheduledTrainFrequencyConsistency, trainFrequencyConsistencyPercentVariance, avgObservedPlatformWaitTime, avgScheduledPlatformWaitTime, avgPlatformWaitTimePercentVariance, pctHeadwayAdherence, avgHeadwayDeviation, numOnTimeOrEarlyDeparturesByHeadwayAdherence, pctOnTimeOrEarlyDeparturesByHeadwayAdherence, numLateDeparturesByHeadwayAdherence, pctLateDeparturesByHeadwayAdherence, numVeryLateDeparturesByHeadwayAdherence, pctVeryLateDeparturesByHeadwayAdherence, pctScheduleAdherence, avgScheduleDeviation, numOnTimeDeparturesByScheduleAdherence, pctOnTimeDeparturesByScheduleAdherence, numOffScheduleDeparturesByScheduleAdherence, pctOffScheduleDeparturesByScheduleAdherence, numVeryOffScheduleDeparturesByScheduleAdherence, pctVeryOffScheduleDeparturesByScheduleAdherence);
    }

    public Integer getNumObservedDepartures() {
        return numObservedDepartures;
    }

    public void setNumObservedDepartures(Integer numObservedDepartures) {
        this.numObservedDepartures = numObservedDepartures;
    }

    public Integer getNumScheduledDepartures() {
        return numScheduledDepartures;
    }

    public void setNumScheduledDepartures(Integer numScheduledDepartures) {
        this.numScheduledDepartures = numScheduledDepartures;
    }

    public Integer getNumMissedDepartures() {
        return numMissedDepartures;
    }

    public void setNumMissedDepartures(Integer numMissedDepartures) {
        this.numMissedDepartures = numMissedDepartures;
    }

    public Double getPctMissedDepartures() {
        return pctMissedDepartures;
    }

    public void setPctMissedDepartures(Double pctMissedDepartures) {
        this.pctMissedDepartures = pctMissedDepartures;
    }

    public Integer getNumUnscheduledDepartures() {
        return numUnscheduledDepartures;
    }

    public void setNumUnscheduledDepartures(Integer numUnscheduledDepartures) {
        this.numUnscheduledDepartures = numUnscheduledDepartures;
    }

    public Double getPctUnscheduledDepartures() {
        return pctUnscheduledDepartures;
    }

    public void setPctUnscheduledDepartures(Double pctUnscheduledDepartures) {
        this.pctUnscheduledDepartures = pctUnscheduledDepartures;
    }

    public Double getAvgObservedTrainFrequency() {
        return avgObservedTrainFrequency;
    }

    public void setAvgObservedTrainFrequency(Double avgObservedTrainFrequency) {
        this.avgObservedTrainFrequency = avgObservedTrainFrequency;
    }

    public Double getAvgScheduledTrainFrequency() {
        return avgScheduledTrainFrequency;
    }

    public void setAvgScheduledTrainFrequency(Double avgScheduledTrainFrequency) {
        this.avgScheduledTrainFrequency = avgScheduledTrainFrequency;
    }

    public Double getAvgTrainFrequencyPercentVariance() {
        return avgTrainFrequencyPercentVariance;
    }

    public void setAvgTrainFrequencyPercentVariance(Double avgTrainFrequencyPercentVariance) {
        this.avgTrainFrequencyPercentVariance = avgTrainFrequencyPercentVariance;
    }

    public Double getObservedTrainFrequencyConsistency() {
        return observedTrainFrequencyConsistency;
    }

    public void setObservedTrainFrequencyConsistency(Double observedTrainFrequencyConsistency) {
        this.observedTrainFrequencyConsistency = observedTrainFrequencyConsistency;
    }

    public Double getScheduledTrainFrequencyConsistency() {
        return scheduledTrainFrequencyConsistency;
    }

    public void setScheduledTrainFrequencyConsistency(Double scheduledTrainFrequencyConsistency) {
        this.scheduledTrainFrequencyConsistency = scheduledTrainFrequencyConsistency;
    }

    public Double getTrainFrequencyConsistencyPercentVariance() {
        return trainFrequencyConsistencyPercentVariance;
    }

    public void setTrainFrequencyConsistencyPercentVariance(Double trainFrequencyConsistencyPercentVariance) {
        this.trainFrequencyConsistencyPercentVariance = trainFrequencyConsistencyPercentVariance;
    }

    public Double getAvgObservedPlatformWaitTime() {
        return avgObservedPlatformWaitTime;
    }

    public void setAvgObservedPlatformWaitTime(Double avgObservedPlatformWaitTime) {
        this.avgObservedPlatformWaitTime = avgObservedPlatformWaitTime;
    }

    public Double getAvgScheduledPlatformWaitTime() {
        return avgScheduledPlatformWaitTime;
    }

    public void setAvgScheduledPlatformWaitTime(Double avgScheduledPlatformWaitTime) {
        this.avgScheduledPlatformWaitTime = avgScheduledPlatformWaitTime;
    }

    public Double getAvgPlatformWaitTimePercentVariance() {
        return avgPlatformWaitTimePercentVariance;
    }

    public void setAvgPlatformWaitTimePercentVariance(Double avgPlatformWaitTimePercentVariance) {
        this.avgPlatformWaitTimePercentVariance = avgPlatformWaitTimePercentVariance;
    }

    public Double getPctHeadwayAdherence() {
        return pctHeadwayAdherence;
    }

    public void setPctHeadwayAdherence(Double pctHeadwayAdherence) {
        this.pctHeadwayAdherence = pctHeadwayAdherence;
    }

    public Double getAvgHeadwayDeviation() {
        return avgHeadwayDeviation;
    }

    public void setAvgHeadwayDeviation(Double avgHeadwayDeviation) {
        this.avgHeadwayDeviation = avgHeadwayDeviation;
    }

    public Double getNumOnTimeOrEarlyDeparturesByHeadwayAdherence() {
        return numOnTimeOrEarlyDeparturesByHeadwayAdherence;
    }

    public void setNumOnTimeOrEarlyDeparturesByHeadwayAdherence(Double numOnTimeOrEarlyDeparturesByHeadwayAdherence) {
        this.numOnTimeOrEarlyDeparturesByHeadwayAdherence = numOnTimeOrEarlyDeparturesByHeadwayAdherence;
    }

    public Double getPctOnTimeOrEarlyDeparturesByHeadwayAdherence() {
        return pctOnTimeOrEarlyDeparturesByHeadwayAdherence;
    }

    public void setPctOnTimeOrEarlyDeparturesByHeadwayAdherence(Double pctOnTimeOrEarlyDeparturesByHeadwayAdherence) {
        this.pctOnTimeOrEarlyDeparturesByHeadwayAdherence = pctOnTimeOrEarlyDeparturesByHeadwayAdherence;
    }

    public Double getNumLateDeparturesByHeadwayAdherence() {
        return numLateDeparturesByHeadwayAdherence;
    }

    public void setNumLateDeparturesByHeadwayAdherence(Double numLateDeparturesByHeadwayAdherence) {
        this.numLateDeparturesByHeadwayAdherence = numLateDeparturesByHeadwayAdherence;
    }

    public Double getPctLateDeparturesByHeadwayAdherence() {
        return pctLateDeparturesByHeadwayAdherence;
    }

    public void setPctLateDeparturesByHeadwayAdherence(Double pctLateDeparturesByHeadwayAdherence) {
        this.pctLateDeparturesByHeadwayAdherence = pctLateDeparturesByHeadwayAdherence;
    }

    public Double getNumVeryLateDeparturesByHeadwayAdherence() {
        return numVeryLateDeparturesByHeadwayAdherence;
    }

    public void setNumVeryLateDeparturesByHeadwayAdherence(Double numVeryLateDeparturesByHeadwayAdherence) {
        this.numVeryLateDeparturesByHeadwayAdherence = numVeryLateDeparturesByHeadwayAdherence;
    }

    public Double getPctVeryLateDeparturesByHeadwayAdherence() {
        return pctVeryLateDeparturesByHeadwayAdherence;
    }

    public void setPctVeryLateDeparturesByHeadwayAdherence(Double pctVeryLateDeparturesByHeadwayAdherence) {
        this.pctVeryLateDeparturesByHeadwayAdherence = pctVeryLateDeparturesByHeadwayAdherence;
    }

    public Double getPctScheduleAdherence() {
        return pctScheduleAdherence;
    }

    public void setPctScheduleAdherence(Double pctScheduleAdherence) {
        this.pctScheduleAdherence = pctScheduleAdherence;
    }

    public Double getAvgScheduleDeviation() {
        return avgScheduleDeviation;
    }

    public void setAvgScheduleDeviation(Double avgScheduleDeviation) {
        this.avgScheduleDeviation = avgScheduleDeviation;
    }

    public Double getNumOnTimeDeparturesByScheduleAdherence() {
        return numOnTimeDeparturesByScheduleAdherence;
    }

    public void setNumOnTimeDeparturesByScheduleAdherence(Double numOnTimeDeparturesByScheduleAdherence) {
        this.numOnTimeDeparturesByScheduleAdherence = numOnTimeDeparturesByScheduleAdherence;
    }

    public Double getPctOnTimeDeparturesByScheduleAdherence() {
        return pctOnTimeDeparturesByScheduleAdherence;
    }

    public void setPctOnTimeDeparturesByScheduleAdherence(Double pctOnTimeDeparturesByScheduleAdherence) {
        this.pctOnTimeDeparturesByScheduleAdherence = pctOnTimeDeparturesByScheduleAdherence;
    }

    public Double getNumOffScheduleDeparturesByScheduleAdherence() {
        return numOffScheduleDeparturesByScheduleAdherence;
    }

    public void setNumOffScheduleDeparturesByScheduleAdherence(Double numOffScheduleDeparturesByScheduleAdherence) {
        this.numOffScheduleDeparturesByScheduleAdherence = numOffScheduleDeparturesByScheduleAdherence;
    }

    public Double getPctOffScheduleDeparturesByScheduleAdherence() {
        return pctOffScheduleDeparturesByScheduleAdherence;
    }

    public void setPctOffScheduleDeparturesByScheduleAdherence(Double pctOffScheduleDeparturesByScheduleAdherence) {
        this.pctOffScheduleDeparturesByScheduleAdherence = pctOffScheduleDeparturesByScheduleAdherence;
    }

    public Double getNumVeryOffScheduleDeparturesByScheduleAdherence() {
        return numVeryOffScheduleDeparturesByScheduleAdherence;
    }

    public void setNumVeryOffScheduleDeparturesByScheduleAdherence(Double numVeryOffScheduleDeparturesByScheduleAdherence) {
        this.numVeryOffScheduleDeparturesByScheduleAdherence = numVeryOffScheduleDeparturesByScheduleAdherence;
    }

    public Double getPctVeryOffScheduleDeparturesByScheduleAdherence() {
        return pctVeryOffScheduleDeparturesByScheduleAdherence;
    }

    public void setPctVeryOffScheduleDeparturesByScheduleAdherence(Double pctVeryOffScheduleDeparturesByScheduleAdherence) {
        this.pctVeryOffScheduleDeparturesByScheduleAdherence = pctVeryOffScheduleDeparturesByScheduleAdherence;
    }
}

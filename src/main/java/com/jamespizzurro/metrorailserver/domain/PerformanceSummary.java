package com.jamespizzurro.metrorailserver.domain;

import java.math.BigDecimal;
import java.math.BigInteger;

public class PerformanceSummary {

    private String date;
    private String timeOfDay;
    private String lineCode;

    private Integer averageCalculatedHeadwayAdherence;

    private Integer averageCalculatedScheduleAdherence;

    private Double averageObservedTrainFrequency;
    private Double averageExpectedTrainFrequency;
    private Integer percentAverageObservedTrainFrequencyOfExpected;

    private Double standardDeviationObservedTrainFrequency;
    private Double standardDeviationExpectedTrainFrequency;
    private Integer percentStandardDeviationObservedTrainFrequencyOfExpected;

    private Double averageCalculatedPlatformWaitTime;
    private Double averageExpectedPlatformWaitTime;
    private Integer percentAverageCalculatedPlatformWaitTimeOfExpected;

    private Double averageObservedTrains;
    private Double averageExpectedTrains;
    private Integer percentAverageObservedTrainsOfExpected;

    private Double averageObservedEightCarTrains;
    private Double averageExpectedEightCarTrains;
    private Integer percentAverageObservedEightCarTrainsOfExpected;

    private Double averageObservedTrainCars;
    private Double averageExpectedTrainCars;
    private Integer percentAverageObservedTrainCarsOfExpected;

    private Double maximumObservedDelayedTrains;
    private Double averageObservedTrainDelays;
    private Double medianObservedTrainDelays;
    private Double minimumObservedTrainDelays;
    private Double maximumObservedTrainDelays;

    private Integer numOffloads;

    private Integer numIncidents;

    private Integer numNegativeTrainTags;

    private Integer numTimesTrainsExpressedStations;

    public PerformanceSummary(Object[] performanceSummaryData) {
        this.date = (String) performanceSummaryData[0];
        this.timeOfDay = (String) performanceSummaryData[1];
        this.lineCode = (String) performanceSummaryData[2];
        this.averageCalculatedHeadwayAdherence = (performanceSummaryData[3] != null) ? ((BigDecimal) performanceSummaryData[3]).intValue() : null;
        this.averageCalculatedScheduleAdherence = (performanceSummaryData[4] != null) ? ((BigDecimal) performanceSummaryData[4]).intValue() : null;
        this.averageObservedTrainFrequency = (performanceSummaryData[5] != null) ? ((BigDecimal) performanceSummaryData[5]).doubleValue() : null;
        this.averageExpectedTrainFrequency = (performanceSummaryData[6] != null) ? ((BigDecimal) performanceSummaryData[6]).doubleValue() : null;
        this.percentAverageObservedTrainFrequencyOfExpected = (performanceSummaryData[7] != null) ? Math.toIntExact(Math.round((Double) performanceSummaryData[7])) : null;
        this.standardDeviationObservedTrainFrequency = (performanceSummaryData[8] != null) ? ((BigDecimal) performanceSummaryData[8]).doubleValue() : null;
        this.standardDeviationExpectedTrainFrequency = (performanceSummaryData[9] != null) ? ((BigDecimal) performanceSummaryData[9]).doubleValue() : null;
        this.percentStandardDeviationObservedTrainFrequencyOfExpected = (performanceSummaryData[10] != null) ? Math.toIntExact(Math.round((Double) performanceSummaryData[10])) : null;
        this.averageCalculatedPlatformWaitTime = (performanceSummaryData[11] != null) ? ((BigDecimal) performanceSummaryData[11]).doubleValue() : null;
        this.averageExpectedPlatformWaitTime = (performanceSummaryData[12] != null) ? ((BigDecimal) performanceSummaryData[12]).doubleValue() : null;
        this.percentAverageCalculatedPlatformWaitTimeOfExpected = (performanceSummaryData[13] != null) ? Math.toIntExact(Math.round((Double) performanceSummaryData[13])) : null;
        this.averageObservedTrains = (performanceSummaryData[14] != null) ? ((BigDecimal) performanceSummaryData[14]).doubleValue() : null;
        this.averageExpectedTrains = (performanceSummaryData[15] != null) ? ((BigDecimal) performanceSummaryData[15]).doubleValue() : null;
        this.percentAverageObservedTrainsOfExpected = (performanceSummaryData[16] != null) ? ((BigDecimal) performanceSummaryData[16]).intValue() : null;
        this.averageObservedEightCarTrains = (performanceSummaryData[17] != null) ? ((BigDecimal) performanceSummaryData[17]).doubleValue() : null;
        this.averageExpectedEightCarTrains = null;  // maybe someday
        this.percentAverageObservedEightCarTrainsOfExpected = null; // maybe someday
        this.averageObservedTrainCars = (performanceSummaryData[18] != null) ? ((BigDecimal) performanceSummaryData[18]).doubleValue() : null;
        this.averageExpectedTrainCars = null;   // maybe someday
        this.percentAverageObservedTrainCarsOfExpected = null;  // maybe someday
        this.maximumObservedDelayedTrains = (performanceSummaryData[19] != null) ? ((BigDecimal) performanceSummaryData[19]).doubleValue() : null;
        this.averageObservedTrainDelays = (performanceSummaryData[20] != null) ? ((BigDecimal) performanceSummaryData[20]).doubleValue() : null;
        this.medianObservedTrainDelays = (performanceSummaryData[21] != null) ? ((BigDecimal) performanceSummaryData[21]).doubleValue() : null;
        this.minimumObservedTrainDelays = (performanceSummaryData[22] != null) ? ((BigDecimal) performanceSummaryData[22]).doubleValue() : null;
        this.maximumObservedTrainDelays = (performanceSummaryData[23] != null) ? ((BigDecimal) performanceSummaryData[23]).doubleValue() : null;
        this.numOffloads = (performanceSummaryData[24] != null) ? ((BigInteger) performanceSummaryData[24]).intValue() : null;
        this.numIncidents = (performanceSummaryData[25] != null) ? ((BigInteger) performanceSummaryData[25]).intValue() : null;
        this.numNegativeTrainTags = (performanceSummaryData[26] != null) ? ((BigInteger) performanceSummaryData[26]).intValue() : null;
        this.numTimesTrainsExpressedStations = (performanceSummaryData[27] != null) ? ((BigInteger) performanceSummaryData[27]).intValue() : null;
    }

    public String getDate() {
        return date;
    }

    public String getTimeOfDay() {
        return timeOfDay;
    }

    public String getLineCode() {
        return lineCode;
    }

    public Integer getAverageCalculatedHeadwayAdherence() {
        return averageCalculatedHeadwayAdherence;
    }

    public Integer getAverageCalculatedScheduleAdherence() {
        return averageCalculatedScheduleAdherence;
    }

    public Double getAverageObservedTrainFrequency() {
        return averageObservedTrainFrequency;
    }

    public Double getAverageExpectedTrainFrequency() {
        return averageExpectedTrainFrequency;
    }

    public Integer getPercentAverageObservedTrainFrequencyOfExpected() {
        return percentAverageObservedTrainFrequencyOfExpected;
    }

    public Double getStandardDeviationObservedTrainFrequency() {
        return standardDeviationObservedTrainFrequency;
    }

    public Double getStandardDeviationExpectedTrainFrequency() {
        return standardDeviationExpectedTrainFrequency;
    }

    public Integer getPercentStandardDeviationObservedTrainFrequencyOfExpected() {
        return percentStandardDeviationObservedTrainFrequencyOfExpected;
    }

    public Double getAverageCalculatedPlatformWaitTime() {
        return averageCalculatedPlatformWaitTime;
    }

    public Double getAverageExpectedPlatformWaitTime() {
        return averageExpectedPlatformWaitTime;
    }

    public Integer getPercentAverageCalculatedPlatformWaitTimeOfExpected() {
        return percentAverageCalculatedPlatformWaitTimeOfExpected;
    }

    public Double getAverageObservedTrains() {
        return averageObservedTrains;
    }

    public Double getAverageExpectedTrains() {
        return averageExpectedTrains;
    }

    public Integer getPercentAverageObservedTrainsOfExpected() {
        return percentAverageObservedTrainsOfExpected;
    }

    public Double getAverageObservedEightCarTrains() {
        return averageObservedEightCarTrains;
    }

    public Double getAverageExpectedEightCarTrains() {
        return averageExpectedEightCarTrains;
    }

    public Integer getPercentAverageObservedEightCarTrainsOfExpected() {
        return percentAverageObservedEightCarTrainsOfExpected;
    }

    public Double getAverageObservedTrainCars() {
        return averageObservedTrainCars;
    }

    public Double getAverageExpectedTrainCars() {
        return averageExpectedTrainCars;
    }

    public Integer getPercentAverageObservedTrainCarsOfExpected() {
        return percentAverageObservedTrainCarsOfExpected;
    }

    public Double getMaximumObservedDelayedTrains() {
        return maximumObservedDelayedTrains;
    }

    public Double getAverageObservedTrainDelays() {
        return averageObservedTrainDelays;
    }

    public Double getMedianObservedTrainDelays() {
        return medianObservedTrainDelays;
    }

    public Double getMinimumObservedTrainDelays() {
        return minimumObservedTrainDelays;
    }

    public Double getMaximumObservedTrainDelays() {
        return maximumObservedTrainDelays;
    }

    public Integer getNumOffloads() {
        return numOffloads;
    }

    public Integer getNumIncidents() {
        return numIncidents;
    }

    public Integer getNumNegativeTrainTags() {
        return numNegativeTrainTags;
    }

    public Integer getNumTimesTrainsExpressedStations() {
        return numTimesTrainsExpressedStations;
    }
}

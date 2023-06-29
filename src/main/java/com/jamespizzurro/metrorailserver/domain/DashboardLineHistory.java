package com.jamespizzurro.metrorailserver.domain;

public class DashboardLineHistory {

    private String lineCode;
    private String timestamps;
    private String avgNumCars;
    private String expNumCars;
    private String avgNumTrains;
    private String expNumTrains;
    private String avgNumDelayedTrains;
    private String avgNumEightCarTrains;
    private String expNumEightCarTrains;
    private String avgPercentTrainsDelayed;
    private String avgPercentEightCarTrains;
    private String expPercentEightCarTrains;
    private String avgTrainDelay;
    private String avgMinimumHeadways;
    private String avgTrainFrequency;
    private String expTrainFrequency;
    private String avgPlatformWaitTime;
    private String expPlatformWaitTime;
    private String avgHeadwayAdherence;
    private String avgScheduleAdherence;
    private String stdDevTrainFrequency;
    private String expStdDevTrainFrequency;

    public DashboardLineHistory(Object[] values) {
        this.lineCode = String.valueOf(values[0]);
        this.timestamps = String.valueOf(values[1]);
        this.avgNumCars = String.valueOf(values[2]);
        this.expNumCars = null; // maybe someday
        this.avgNumTrains = String.valueOf(values[3]);
        this.expNumTrains = String.valueOf(values[4]);
        this.avgNumDelayedTrains = String.valueOf(values[5]);
        this.avgNumEightCarTrains = String.valueOf(values[6]);
        this.expNumEightCarTrains = null;   // maybe someday
        this.avgPercentTrainsDelayed = String.valueOf(values[7]);
        this.avgPercentEightCarTrains = String.valueOf(values[8]);
        this.expPercentEightCarTrains = null;   // maybe someday
        this.avgTrainDelay = String.valueOf(values[9]);
        this.avgMinimumHeadways = String.valueOf(values[10]);
        this.avgTrainFrequency = String.valueOf(values[11]);
        this.expTrainFrequency = String.valueOf(values[12]);
        this.avgPlatformWaitTime = String.valueOf(values[13]);
        this.expPlatformWaitTime = String.valueOf(values[14]);
        this.avgHeadwayAdherence = String.valueOf(values[15]);
        this.avgScheduleAdherence = String.valueOf(values[16]);
        this.stdDevTrainFrequency = String.valueOf(values[17]);
        this.expStdDevTrainFrequency = String.valueOf(values[18]);
    }

    public String getLineCode() {
        return lineCode;
    }

    public void setLineCode(String lineCode) {
        this.lineCode = lineCode;
    }

    public String getTimestamps() {
        return timestamps;
    }

    public void setTimestamps(String timestamps) {
        this.timestamps = timestamps;
    }

    public String getAvgNumCars() {
        return avgNumCars;
    }

    public void setAvgNumCars(String avgNumCars) {
        this.avgNumCars = avgNumCars;
    }

    public String getExpNumCars() {
        return expNumCars;
    }

    public void setExpNumCars(String expNumCars) {
        this.expNumCars = expNumCars;
    }

    public String getAvgNumTrains() {
        return avgNumTrains;
    }

    public void setAvgNumTrains(String avgNumTrains) {
        this.avgNumTrains = avgNumTrains;
    }

    public String getExpNumTrains() {
        return expNumTrains;
    }

    public void setExpNumTrains(String expNumTrains) {
        this.expNumTrains = expNumTrains;
    }

    public String getAvgNumDelayedTrains() {
        return avgNumDelayedTrains;
    }

    public void setAvgNumDelayedTrains(String avgNumDelayedTrains) {
        this.avgNumDelayedTrains = avgNumDelayedTrains;
    }

    public String getAvgNumEightCarTrains() {
        return avgNumEightCarTrains;
    }

    public void setAvgNumEightCarTrains(String avgNumEightCarTrains) {
        this.avgNumEightCarTrains = avgNumEightCarTrains;
    }

    public String getExpNumEightCarTrains() {
        return expNumEightCarTrains;
    }

    public void setExpNumEightCarTrains(String expNumEightCarTrains) {
        this.expNumEightCarTrains = expNumEightCarTrains;
    }

    public String getAvgPercentTrainsDelayed() {
        return avgPercentTrainsDelayed;
    }

    public void setAvgPercentTrainsDelayed(String avgPercentTrainsDelayed) {
        this.avgPercentTrainsDelayed = avgPercentTrainsDelayed;
    }

    public String getAvgPercentEightCarTrains() {
        return avgPercentEightCarTrains;
    }

    public void setAvgPercentEightCarTrains(String avgPercentEightCarTrains) {
        this.avgPercentEightCarTrains = avgPercentEightCarTrains;
    }

    public String getExpPercentEightCarTrains() {
        return expPercentEightCarTrains;
    }

    public void setExpPercentEightCarTrains(String expPercentEightCarTrains) {
        this.expPercentEightCarTrains = expPercentEightCarTrains;
    }

    public String getAvgTrainDelay() {
        return avgTrainDelay;
    }

    public void setAvgTrainDelay(String avgTrainDelay) {
        this.avgTrainDelay = avgTrainDelay;
    }

    public String getAvgMinimumHeadways() {
        return avgMinimumHeadways;
    }

    public void setAvgMinimumHeadways(String avgMinimumHeadways) {
        this.avgMinimumHeadways = avgMinimumHeadways;
    }

    public String getAvgTrainFrequency() {
        return avgTrainFrequency;
    }

    public void setAvgTrainFrequency(String avgTrainFrequency) {
        this.avgTrainFrequency = avgTrainFrequency;
    }

    public String getExpTrainFrequency() {
        return expTrainFrequency;
    }

    public void setExpTrainFrequency(String expTrainFrequency) {
        this.expTrainFrequency = expTrainFrequency;
    }

    public String getAvgPlatformWaitTime() {
        return avgPlatformWaitTime;
    }

    public void setAvgPlatformWaitTime(String avgPlatformWaitTime) {
        this.avgPlatformWaitTime = avgPlatformWaitTime;
    }

    public String getExpPlatformWaitTime() {
        return expPlatformWaitTime;
    }

    public void setExpPlatformWaitTime(String expPlatformWaitTime) {
        this.expPlatformWaitTime = expPlatformWaitTime;
    }

    public String getAvgHeadwayAdherence() {
        return avgHeadwayAdherence;
    }

    public void setAvgHeadwayAdherence(String avgHeadwayAdherence) {
        this.avgHeadwayAdherence = avgHeadwayAdherence;
    }

    public String getAvgScheduleAdherence() {
        return avgScheduleAdherence;
    }

    public void setAvgScheduleAdherence(String avgScheduleAdherence) {
        this.avgScheduleAdherence = avgScheduleAdherence;
    }

    public String getStdDevTrainFrequency() {
        return stdDevTrainFrequency;
    }

    public void setStdDevTrainFrequency(String stdDevTrainFrequency) {
        this.stdDevTrainFrequency = stdDevTrainFrequency;
    }

    public String getExpStdDevTrainFrequency() {
        return expStdDevTrainFrequency;
    }

    public void setExpStdDevTrainFrequency(String expStdDevTrainFrequency) {
        this.expStdDevTrainFrequency = expStdDevTrainFrequency;
    }
}

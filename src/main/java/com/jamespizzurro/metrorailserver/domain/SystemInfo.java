package com.jamespizzurro.metrorailserver.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SystemInfo {

    public enum BetweenStationDelayStatus {
        OK_TO_OK,
        OK_TO_SLOW,
        OK_TO_DELAYED,
        SLOW_TO_OK,
        SLOW_TO_SLOW,
        SLOW_TO_DELAYED,
        DELAYED_TO_OK,
        DELAYED_TO_SLOW,
        DELAYED_TO_DELAYED
    }

    private List<TrainStatus> trainStatuses;
    private SystemMetrics systemMetrics;
    private Map<String, BetweenStationDelayStatus> betweenStationDelayStatuses;
    private Long lastUpdatedTimestamp;
    private List<RailIncident> lineRailIncidents;
    private Map<String, Long> stationNumPositiveTagsMap;
    private Map<String, Long> stationNumNegativeTagsMap;
    private List<SpeedRestriction> speedRestrictions;
    private Map<String, Boolean> stationHasRailIncidentsMap;
    private Map<String, Boolean> stationHasTwitterProblemMap;
    private Map<String, SavedTrip> savedTrips;
    private Map<String, Boolean> hasElevatorOutagesByStation;
    private Map<String, Boolean> hasEscalatorOutagesByStation;
    private RecentTrainFrequencyData recentTrainFrequencyData;

    public SystemInfo() {
        this.trainStatuses = new ArrayList<>();
        this.betweenStationDelayStatuses = new HashMap<>();
    }

    public List<TrainStatus> getTrainStatuses() {
        return trainStatuses;
    }

    public void setTrainStatuses(List<TrainStatus> trainStatuses) {
        this.trainStatuses = trainStatuses;
    }

    public SystemMetrics getSystemMetrics() {
        return systemMetrics;
    }

    public void setSystemMetrics(SystemMetrics systemMetrics) {
        this.systemMetrics = systemMetrics;
    }

    public Map<String, BetweenStationDelayStatus> getBetweenStationDelayStatuses() {
        return betweenStationDelayStatuses;
    }

    public void setBetweenStationDelayStatuses(Map<String, BetweenStationDelayStatus> betweenStationDelayStatuses) {
        this.betweenStationDelayStatuses = betweenStationDelayStatuses;
    }

    public Long getLastUpdatedTimestamp() {
        return lastUpdatedTimestamp;
    }

    public void setLastUpdatedTimestamp(Long lastUpdatedTimestamp) {
        this.lastUpdatedTimestamp = lastUpdatedTimestamp;
    }

    public List<RailIncident> getLineRailIncidents() {
        return lineRailIncidents;
    }

    public void setLineRailIncidents(List<RailIncident> lineRailIncidents) {
        this.lineRailIncidents = lineRailIncidents;
    }

    public Map<String, Long> getStationNumPositiveTagsMap() {
        return stationNumPositiveTagsMap;
    }

    public void setStationNumPositiveTagsMap(Map<String, Long> stationNumPositiveTagsMap) {
        this.stationNumPositiveTagsMap = stationNumPositiveTagsMap;
    }

    public Map<String, Long> getStationNumNegativeTagsMap() {
        return stationNumNegativeTagsMap;
    }

    public void setStationNumNegativeTagsMap(Map<String, Long> stationNumNegativeTagsMap) {
        this.stationNumNegativeTagsMap = stationNumNegativeTagsMap;
    }

    public List<SpeedRestriction> getSpeedRestrictions() {
        return speedRestrictions;
    }

    public void setSpeedRestrictions(List<SpeedRestriction> speedRestrictions) {
        this.speedRestrictions = speedRestrictions;
    }

    public Map<String, Boolean> getStationHasRailIncidentsMap() {
        return stationHasRailIncidentsMap;
    }

    public void setStationHasRailIncidentsMap(Map<String, Boolean> stationHasRailIncidentsMap) {
        this.stationHasRailIncidentsMap = stationHasRailIncidentsMap;
    }

    public Map<String, Boolean> getStationHasTwitterProblemMap() {
        return stationHasTwitterProblemMap;
    }

    public void setStationHasTwitterProblemMap(Map<String, Boolean> stationHasTwitterProblemMap) {
        this.stationHasTwitterProblemMap = stationHasTwitterProblemMap;
    }

    public Map<String, SavedTrip> getSavedTrips() {
        return savedTrips;
    }

    public void setSavedTrips(Map<String, SavedTrip> savedTrips) {
        this.savedTrips = savedTrips;
    }

    public Map<String, Boolean> getHasElevatorOutagesByStation() {
        return hasElevatorOutagesByStation;
    }

    public void setHasElevatorOutagesByStation(Map<String, Boolean> hasElevatorOutagesByStation) {
        this.hasElevatorOutagesByStation = hasElevatorOutagesByStation;
    }

    public Map<String, Boolean> getHasEscalatorOutagesByStation() {
        return hasEscalatorOutagesByStation;
    }

    public void setHasEscalatorOutagesByStation(Map<String, Boolean> hasEscalatorOutagesByStation) {
        this.hasEscalatorOutagesByStation = hasEscalatorOutagesByStation;
    }

    public RecentTrainFrequencyData getRecentTrainFrequencyData() {
        return recentTrainFrequencyData;
    }

    public void setRecentTrainFrequencyData(RecentTrainFrequencyData recentTrainFrequencyData) {
        this.recentTrainFrequencyData = recentTrainFrequencyData;
    }
}

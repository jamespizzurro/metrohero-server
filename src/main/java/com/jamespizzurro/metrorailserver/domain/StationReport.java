package com.jamespizzurro.metrorailserver.domain;

import java.util.List;

public class StationReport {

    private List<TrainStatus> trainStatuses;
    private ProblemTweetResponse tweetResponse;
    private List<RailIncident> railIncidents;
    private List<ElevatorEscalatorOutage> elevatorOutages;
    private List<ElevatorEscalatorOutage> escalatorOutages;
    private String crowdingStatus;

    public StationReport(List<TrainStatus> trainStatuses, ProblemTweetResponse tweetResponse, List<RailIncident> railIncidents, List<ElevatorEscalatorOutage> elevatorOutages, List<ElevatorEscalatorOutage> escalatorOutages, String crowdingStatus) {
        this.trainStatuses = trainStatuses;
        this.tweetResponse = tweetResponse;
        this.railIncidents = railIncidents;
        this.elevatorOutages = elevatorOutages;
        this.escalatorOutages = escalatorOutages;
        this.crowdingStatus = crowdingStatus;
    }

    public List<TrainStatus> getTrainStatuses() {
        return trainStatuses;
    }

    public ProblemTweetResponse getTweetResponse() {
        return tweetResponse;
    }

    public List<RailIncident> getRailIncidents() {
        return railIncidents;
    }

    public List<ElevatorEscalatorOutage> getElevatorOutages() {
        return elevatorOutages;
    }

    public List<ElevatorEscalatorOutage> getEscalatorOutages() {
        return escalatorOutages;
    }

    public String getCrowdingStatus() {
        return crowdingStatus;
    }
}

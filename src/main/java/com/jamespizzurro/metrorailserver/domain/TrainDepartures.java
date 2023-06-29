package com.jamespizzurro.metrorailserver.domain;

import java.util.List;

public class TrainDepartures {

    private List<TrainDepartureInfo> trainDepartures;
    private Integer totalNumTrainDepartures;

    public TrainDepartures(List<TrainDepartureInfo> trainDepartures, Integer totalNumTrainDepartures) {
        this.trainDepartures = trainDepartures;
        this.totalNumTrainDepartures = totalNumTrainDepartures;
    }

    public List<TrainDepartureInfo> getTrainDepartures() {
        return trainDepartures;
    }

    public void setTrainDepartures(List<TrainDepartureInfo> trainDepartures) {
        this.trainDepartures = trainDepartures;
    }

    public Integer getTotalNumTrainDepartures() {
        return totalNumTrainDepartures;
    }

    public void setTotalNumTrainDepartures(Integer totalNumTrainDepartures) {
        this.totalNumTrainDepartures = totalNumTrainDepartures;
    }
}

package com.jamespizzurro.metrorailserver.domain.marshallers;

import java.util.List;
import java.util.Objects;

public class TrainPredictions {

    private List<TrainPrediction> Trains;

    public TrainPredictions() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrainPredictions that = (TrainPredictions) o;
        return Objects.equals(Trains, that.Trains);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Trains);
    }

    public List<TrainPrediction> getTrains() {
        return Trains;
    }
}

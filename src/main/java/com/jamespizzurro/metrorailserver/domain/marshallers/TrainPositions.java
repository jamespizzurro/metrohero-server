package com.jamespizzurro.metrorailserver.domain.marshallers;

import java.util.List;
import java.util.Objects;

public class TrainPositions {

    private List<TrainPosition> TrainPositions;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrainPositions that = (TrainPositions) o;
        return Objects.equals(TrainPositions, that.TrainPositions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(TrainPositions);
    }

    public List<TrainPosition> getTrainPositions() {
        return TrainPositions;
    }
}

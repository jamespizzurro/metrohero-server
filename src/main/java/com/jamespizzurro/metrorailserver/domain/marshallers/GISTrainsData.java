package com.jamespizzurro.metrorailserver.domain.marshallers;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;

public class GISTrainsData {

    @SerializedName("features")
    private List<GISTrainData> trainsData;

    public GISTrainsData() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GISTrainsData that = (GISTrainsData) o;
        return Objects.equals(trainsData, that.trainsData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trainsData);
    }

    public List<GISTrainData> getTrainsData() {
        return trainsData;
    }
}

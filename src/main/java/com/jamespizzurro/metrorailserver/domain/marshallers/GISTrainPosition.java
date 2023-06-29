package com.jamespizzurro.metrorailserver.domain.marshallers;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class GISTrainPosition {

    @SerializedName("x")
    private Double x;

    @SerializedName("y")
    private Double y;

    private transient Double lat;
    private transient Double lon;

    public GISTrainPosition() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GISTrainPosition that = (GISTrainPosition) o;
        return Objects.equals(x, that.x) &&
                Objects.equals(y, that.y);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    public Double getX() {
        return x;
    }

    public Double getY() {
        return y;
    }

    public Double getLat() {
        return lat;
    }

    public Double getLon() {
        return lon;
    }
}

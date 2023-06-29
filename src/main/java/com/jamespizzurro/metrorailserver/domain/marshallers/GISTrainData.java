package com.jamespizzurro.metrorailserver.domain.marshallers;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class GISTrainData {

    @SerializedName("attributes")
    private GISTrainAttributes attributes;

    @SerializedName("geometry")
    private GISTrainPosition position;

    public GISTrainData() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GISTrainData that = (GISTrainData) o;
        return Objects.equals(attributes, that.attributes) &&
                Objects.equals(position, that.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attributes, position);
    }

    public GISTrainAttributes getAttributes() {
        return attributes;
    }

    public GISTrainPosition getPosition() {
        return position;
    }
}

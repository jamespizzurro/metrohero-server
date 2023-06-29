package com.jamespizzurro.metrorailserver.domain.marshallers;

import java.util.Objects;

public class TrainPrediction {

    private String Car;
    private String Destination;
    private String DestinationCode;
    private String DestinationName;
    private String Group;
    private String Line;
    private String LocationCode;
    private String LocationName;
    private String Min;

    public TrainPrediction() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrainPrediction that = (TrainPrediction) o;
        return Objects.equals(Car, that.Car) &&
                Objects.equals(Destination, that.Destination) &&
                Objects.equals(DestinationCode, that.DestinationCode) &&
                Objects.equals(DestinationName, that.DestinationName) &&
                Objects.equals(Group, that.Group) &&
                Objects.equals(Line, that.Line) &&
                Objects.equals(LocationCode, that.LocationCode) &&
                Objects.equals(LocationName, that.LocationName) &&
                Objects.equals(Min, that.Min);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Car, Destination, DestinationCode, DestinationName, Group, Line, LocationCode, LocationName, Min);
    }

    public String getCar() {
        return Car;
    }

    public String getDestination() {
        return Destination;
    }

    public String getDestinationCode() {
        return DestinationCode;
    }

    public String getDestinationName() {
        return DestinationName;
    }

    public String getGroup() {
        return Group;
    }

    public String getLine() {
        return Line;
    }

    public String getLocationCode() {
        return LocationCode;
    }

    public String getLocationName() {
        return LocationName;
    }

    public String getMin() {
        return Min;
    }
}

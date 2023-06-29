package com.jamespizzurro.metrorailserver.domain.marshallers;

import java.util.Objects;

public class TrainPosition {

    private String TrainId;
    private String TrainNumber;
    private Integer CarCount;
    private Integer DirectionNum;
    private Integer CircuitId;
    private String DestinationStationCode;
    private String LineCode;
    private Integer SecondsAtLocation;
    private String ServiceType;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrainPosition that = (TrainPosition) o;
        return Objects.equals(TrainId, that.TrainId) &&
                Objects.equals(TrainNumber, that.TrainNumber) &&
                Objects.equals(CarCount, that.CarCount) &&
                Objects.equals(DirectionNum, that.DirectionNum) &&
                Objects.equals(CircuitId, that.CircuitId) &&
                Objects.equals(DestinationStationCode, that.DestinationStationCode) &&
                Objects.equals(LineCode, that.LineCode) &&
                Objects.equals(ServiceType, that.ServiceType);
        // no SecondsAtLocation here intentionally; it is computed by WMATA API Support, not AIMS
    }

    @Override
    public int hashCode() {
        return Objects.hash(TrainId, TrainNumber, CarCount, DirectionNum, CircuitId, DestinationStationCode, LineCode, ServiceType);
        // no SecondsAtLocation here intentionally; it is computed by WMATA API Support, not AIMS
    }

    public String getTrainId() {
        return TrainId;
    }

    public String getTrainNumber() {
        return TrainNumber;
    }

    public Integer getCarCount() {
        return CarCount;
    }

    public Integer getDirectionNum() {
        return DirectionNum;
    }

    public Integer getCircuitId() {
        return CircuitId;
    }

    public String getDestinationStationCode() {
        return DestinationStationCode;
    }

    public String getLineCode() {
        return LineCode;
    }

    public Integer getSecondsAtLocation() {
        return SecondsAtLocation;
    }

    public String getServiceType() {
        return ServiceType;
    }
}

package com.jamespizzurro.metrorailserver.domain;

import java.util.List;

public class StandardRoute {

    private String LineCode;
    private Integer TrackNum;
    private List<StandardRouteTrackCircuit> TrackCircuits;

    public String getLineCode() {
        return LineCode;
    }

    public Integer getTrackNum() {
        return TrackNum;
    }

    public List<StandardRouteTrackCircuit> getTrackCircuits() {
        return TrackCircuits;
    }
}

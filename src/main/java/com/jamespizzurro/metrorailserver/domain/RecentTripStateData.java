package com.jamespizzurro.metrorailserver.domain;

public class RecentTripStateData {

    private String times;
    private String predictedRideTimes;
    private String expectedRideTimes;

    public RecentTripStateData(Object[] values) {
        this.times = String.valueOf(values[0]);
        this.predictedRideTimes = String.valueOf(values[1]);
        this.expectedRideTimes = String.valueOf(values[2]);
    }
}

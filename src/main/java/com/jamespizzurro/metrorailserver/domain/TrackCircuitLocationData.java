package com.jamespizzurro.metrorailserver.domain;

import java.util.Objects;

public class TrackCircuitLocationData {

    private Integer trackCircuitId;
    private Double lat;
    private Double lon;

    public TrackCircuitLocationData() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackCircuitLocationData that = (TrackCircuitLocationData) o;
        return Objects.equals(trackCircuitId, that.trackCircuitId) &&
                Objects.equals(lat, that.lat) &&
                Objects.equals(lon, that.lon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trackCircuitId, lat, lon);
    }

    public Integer getTrackCircuitId() {
        return trackCircuitId;
    }

    public void setTrackCircuitId(Integer trackCircuitId) {
        this.trackCircuitId = trackCircuitId;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }
}

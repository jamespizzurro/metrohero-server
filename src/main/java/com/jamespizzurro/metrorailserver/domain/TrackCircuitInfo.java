package com.jamespizzurro.metrorailserver.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "track_circuit")
public class TrackCircuitInfo {

    @Id
    private Integer apiId;

    @Column(nullable = false)
    private String trackId;

    @Column()
    private Double fromChainMarker;

    @Column()
    private Double toChainMarker;

    @Column(nullable = false)
    private Double length;

    @Column(nullable = false)
    private String trackName;

    public TrackCircuitInfo() {}

    public TrackCircuitInfo(Integer apiId, String trackId, Double fromChainMarker, Double toChainMarker, Double length, String trackName) {
        this.apiId = apiId;
        this.trackId = trackId;
        this.fromChainMarker = fromChainMarker;
        this.toChainMarker = toChainMarker;
        this.length = length;
        this.trackName = trackName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackCircuitInfo that = (TrackCircuitInfo) o;
        return Objects.equals(apiId, that.apiId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiId);
    }

    public Integer getApiId() {
        return apiId;
    }

    public String getTrackId() {
        return trackId;
    }

    public Double getFromChainMarker() {
        return fromChainMarker;
    }

    public Double getToChainMarker() {
        return toChainMarker;
    }

    public Double getLength() {
        return length;
    }

    public String getTrackName() {
        return trackName;
    }
}

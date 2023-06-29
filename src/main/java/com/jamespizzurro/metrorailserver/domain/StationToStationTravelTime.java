package com.jamespizzurro.metrorailserver.domain;

import javax.persistence.*;
import java.util.Calendar;
import java.util.Objects;

@Entity
public class StationToStationTravelTime {

    @Id
    private String stationCodesKey;

    @Column(nullable = false)
    private String fromStationCode;

    @Column(nullable = false)
    private String toStationCode;

    private Long distance;    // in feet

    @Column(nullable = false)
    private Calendar lastUpdated;

    public StationToStationTravelTime() {
    }

    public StationToStationTravelTime(String fromStationCode, String toStationCode, Long distance) {
        this.stationCodesKey = fromStationCode + "_" + toStationCode;
        this.fromStationCode = fromStationCode;
        this.toStationCode = toStationCode;
        this.distance = distance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StationToStationTravelTime that = (StationToStationTravelTime) o;
        return Objects.equals(fromStationCode, that.fromStationCode) &&
                Objects.equals(toStationCode, that.toStationCode) &&
                Objects.equals(lastUpdated, that.lastUpdated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromStationCode, toStationCode, lastUpdated);
    }

    @Override
    public String toString() {
        return "StationToStationTravelTime{" +
                "stationCodesKey='" + stationCodesKey + '\'' +
                ", fromStationCode='" + fromStationCode + '\'' +
                ", toStationCode='" + toStationCode + '\'' +
                ", lastUpdated=" + lastUpdated +
                '}';
    }

    @PrePersist
    public void onPrePersist() {
        updateLastUpdatedDate();
    }

    @PreUpdate
    public void onPreUpdate() {
        updateLastUpdatedDate();
    }

    private void updateLastUpdatedDate() {
        this.lastUpdated = Calendar.getInstance();
    }

    public String getStationCodesKey() {
        return stationCodesKey;
    }

    public void setStationCodesKey(String stationCodesKey) {
        this.stationCodesKey = stationCodesKey;
    }

    public String getFromStationCode() {
        return fromStationCode;
    }

    public void setFromStationCode(String fromStationCode) {
        this.fromStationCode = fromStationCode;
    }

    public String getToStationCode() {
        return toStationCode;
    }

    public void setToStationCode(String toStationCode) {
        this.toStationCode = toStationCode;
    }

    public Long getDistance() {
        return distance;
    }

    public void setDistance(Long distance) {
        this.distance = distance;
    }

    public Calendar getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Calendar lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}

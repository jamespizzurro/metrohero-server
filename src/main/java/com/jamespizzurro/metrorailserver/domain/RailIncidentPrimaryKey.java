package com.jamespizzurro.metrorailserver.domain;

import java.io.Serializable;
import java.util.Objects;

public class RailIncidentPrimaryKey implements Serializable {

    private String description;
    private Long timestamp;
    private String incidentId;

    public RailIncidentPrimaryKey() {
    }

    public RailIncidentPrimaryKey(String description, Long timestamp, String incidentId) {
        this.description = description;
        this.timestamp = timestamp;
        this.incidentId = incidentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RailIncidentPrimaryKey that = (RailIncidentPrimaryKey) o;
        return Objects.equals(description, that.description) &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(incidentId, that.incidentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, timestamp, incidentId);
    }
}

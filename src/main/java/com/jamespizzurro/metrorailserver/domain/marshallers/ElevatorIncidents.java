package com.jamespizzurro.metrorailserver.domain.marshallers;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;

public class ElevatorIncidents {

    @SerializedName("ElevatorIncidents")
    private List<ElevatorIncident> elevatorIncidents;

    public ElevatorIncidents() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ElevatorIncidents that = (ElevatorIncidents) o;
        return Objects.equals(elevatorIncidents, that.elevatorIncidents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elevatorIncidents);
    }

    public List<ElevatorIncident> getElevatorIncidents() {
        return elevatorIncidents;
    }
}

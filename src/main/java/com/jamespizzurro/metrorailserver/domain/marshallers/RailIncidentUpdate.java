package com.jamespizzurro.metrorailserver.domain.marshallers;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class RailIncidentUpdate {

    private String IncidentID;
    private String Description;
    private String IncidentType;
    private String LinesAffected;
    private String DateUpdated;

    public String getIncidentID() {
        return IncidentID;
    }

    public void setIncidentID(String incidentID) {
        IncidentID = incidentID;
    }

    public String getDescription() {
        return Description;
    }

    public void setDescription(String description) {
        Description = description;
    }

    public String getIncidentType() {
        return IncidentType;
    }

    public void setIncidentType(String incidentType) {
        IncidentType = incidentType;
    }

    public Set<String> getLinesAffected() {
        if (this.LinesAffected == null || this.LinesAffected.isEmpty()) {
            return null;
        }

        return new HashSet<>(Arrays.asList(this.LinesAffected.split(";[\\s]?")));
    }

    public void setLinesAffected(String linesAffected) {
        LinesAffected = linesAffected;
    }

    public Long getDateUpdated() {
        if (this.DateUpdated == null || this.DateUpdated.isEmpty()) {
            return null;
        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        Date date;
        try {
            date = dateFormat.parse(this.DateUpdated);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
        return date.getTime() / 1000;
    }

    public void setDateUpdated(String dateUpdated) {
        DateUpdated = dateUpdated;
    }
}

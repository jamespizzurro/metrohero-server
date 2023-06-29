package com.jamespizzurro.metrorailserver.domain;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "rail_incident",
        indexes = {
                @Index(name = "rail_incident_date_index", columnList = "date")
        }
)
@IdClass(RailIncidentPrimaryKey.class)
public class RailIncident {

    @Column(nullable = false)
    private Calendar date;

    @Column(name = "station_codes", nullable = false)
    @Type(type = "com.jamespizzurro.metrorailserver.StringArrayType")
    private String[] stationCodes;

    @Column(name = "line_codes", nullable = false)
    @Type(type = "com.jamespizzurro.metrorailserver.StringArrayType")
    private String[] lineCodes;

    @Deprecated
    @Column(name = "keywords")
    private String keywordsString;

    @Column(name = "keywords_array")
    @Type(type = "com.jamespizzurro.metrorailserver.StringArrayType")
    private String[] keywords;

    @Id
    private String description;

    @Id
    private Long timestamp;

    @Id
    private String incidentId;

    public RailIncident() {
    }

    public RailIncident(Set<String> stationCodes, Set<String> lineCodes, Set<String> keywords, String description, Long timestamp, String incidentId) {
        this.stationCodes = (stationCodes != null) ? stationCodes.toArray(new String[0]) : new String[0];
        this.lineCodes = (lineCodes != null) ? lineCodes.toArray(new String[0]) : new String[0];
        this.keywords = (keywords != null) ? keywords.toArray(new String[0]) : new String[0];
        this.description = description;
        this.timestamp = timestamp;
        this.incidentId = incidentId;

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp * 1000);
        this.date = calendar;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RailIncident that = (RailIncident) o;
        return Arrays.equals(stationCodes, that.stationCodes) &&
                Arrays.equals(lineCodes, that.lineCodes) &&
                Arrays.equals(keywords, that.keywords) &&
                Objects.equals(description, that.description) &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(incidentId, that.incidentId);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(description, timestamp, incidentId);
        result = 31 * result + Arrays.hashCode(stationCodes);
        result = 31 * result + Arrays.hashCode(lineCodes);
        result = 31 * result + Arrays.hashCode(keywords);
        return result;
    }

    public Calendar getDate() {
        return date;
    }

    public void setDate(Calendar date) {
        this.date = date;
    }

    public String[] getStationCodes() {
        return stationCodes;
    }

    public void setStationCodes(String[] stationCodes) {
        this.stationCodes = stationCodes;
    }

    public String[] getLineCodes() {
        return lineCodes;
    }

    public void setLineCodes(String[] lineCodes) {
        this.lineCodes = lineCodes;
    }

    public String[] getKeywords() {
        return keywords;
    }

    public void setKeywords(String[] keywords) {
        this.keywords = keywords;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }
}

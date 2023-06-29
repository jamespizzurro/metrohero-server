package com.jamespizzurro.metrorailserver.domain.marshallers;

import com.google.gson.annotations.SerializedName;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Objects;

public class GISTrainAttributes {

    @SerializedName("ITT")
    private String trainId;

    @SerializedName("DATE_TIME")
    private String dateTimeString;

    @SerializedName("CARNO")
    private Integer numCars;

    @SerializedName("TRACKLINE")
    private String lineName;

    @SerializedName("TRACKNAME")
    private String trackName;

    @SerializedName("DESTINATIONID")
    private String destinationId;

    @SerializedName("DEST_STATION")
    private String destinationStationName;

    @SerializedName("DESTSTATIONCODE")
    private String destinationStationCode;

    @SerializedName("DESCRIPTION")
    private String description;

    @SerializedName("DIRECTION")
    private Integer direction;  // in degrees

    @SerializedName("TRIP_DIRECTION")
    private String directionNumber;

    public GISTrainAttributes() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GISTrainAttributes that = (GISTrainAttributes) o;
        return Objects.equals(trainId, that.trainId) &&
                Objects.equals(dateTimeString, that.dateTimeString) &&
                Objects.equals(numCars, that.numCars) &&
                Objects.equals(lineName, that.lineName) &&
                Objects.equals(trackName, that.trackName) &&
                Objects.equals(destinationId, that.destinationId) &&
                Objects.equals(destinationStationName, that.destinationStationName) &&
                Objects.equals(destinationStationCode, that.destinationStationCode) &&
                Objects.equals(description, that.description) &&
                Objects.equals(direction, that.direction) &&
                Objects.equals(directionNumber, that.directionNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trainId, dateTimeString, numCars, lineName, trackName, destinationId, destinationStationName, destinationStationCode, description, direction, directionNumber);
    }

    public Calendar getObservedDate() {
        try {
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("M/dd/yyyy h:mm:ss a");
            calendar.setTime(sdf.parse(this.dateTimeString));
            return calendar;
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getTrainId() {
        return trainId;
    }

    public String getDateTimeString() {
        return dateTimeString;
    }

    public Integer getNumCars() {
        return numCars;
    }

    public String getLineName() {
        return lineName;
    }

    public String getTrackName() {
        return trackName;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public String getDestinationStationName() {
        return destinationStationName;
    }

    public String getDestinationStationCode() {
        return destinationStationCode;
    }

    public String getDescription() {
        return description;
    }

    public Integer getDirection() {
        return direction;
    }

    public String getDirectionNumber() {
        return directionNumber;
    }
}

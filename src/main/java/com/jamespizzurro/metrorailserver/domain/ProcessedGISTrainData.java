package com.jamespizzurro.metrorailserver.domain;

import com.jamespizzurro.metrorailserver.domain.marshallers.GISTrainData;
import org.springframework.util.StringUtils;

import java.util.Calendar;

public class ProcessedGISTrainData {

    // SOURCE: http://wiki.openstreetmap.org/wiki/Mercator#Java
    private static final double RADIUS = 6378137.0; /* in meters on the equator */

    private String id;
    private String destinationId;
    private String destinationStationCode;
    private boolean areDoorsOpenOnLeft;
    private boolean areDoorsOpenOnRight;
    private boolean isAdjustingOnPlatform;
    private boolean areDoorsOperatingManually;
    private Double lat;
    private Double lon;
    private Integer direction;
    private Calendar observedDate;

    public ProcessedGISTrainData(GISTrainData trainData) {
        if (trainData.getAttributes().getTrainId() != null) {
            switch (trainData.getAttributes().getTrainId().length()) {
                case 1:
                    this.id = "00" + trainData.getAttributes().getTrainId();
                    break;
                case 2:
                    this.id = "0" + trainData.getAttributes().getTrainId();
                    break;
                default:
                    this.id = trainData.getAttributes().getTrainId();
                    break;
            }
        }

        this.destinationId = trainData.getAttributes().getDestinationId();
        this.destinationStationCode = trainData.getAttributes().getDestinationStationCode();

        if (!StringUtils.isEmpty(trainData.getAttributes().getDescription())) {
            for (String element : trainData.getAttributes().getDescription().split(";")) {
                element = element.trim();

                if (element.equals("Doors open left")) {
                    this.areDoorsOpenOnLeft = true;
                }

                if (element.equals("Doors open right")) {
                    this.areDoorsOpenOnRight = true;
                }

                if (element.equals("motion")) {
                    this.isAdjustingOnPlatform = true;
                }

                if (element.equals("Doors operating manually")) {
                    this.areDoorsOperatingManually = true;
                }
            }
        }

        // SOURCE: http://wiki.openstreetmap.org/wiki/Mercator#Java
        this.lat = ((trainData.getPosition().getY() != null) && (Math.abs(trainData.getPosition().getY()) > 1)) ? Math.toDegrees(Math.atan(Math.exp(trainData.getPosition().getY() / RADIUS)) * 2 - Math.PI/2) : null;
        this.lon = ((trainData.getPosition().getX() != null) && (Math.abs(trainData.getPosition().getX()) > 1)) ? Math.toDegrees(trainData.getPosition().getX() / RADIUS) : null;

        this.direction = trainData.getAttributes().getDirection();

        this.observedDate = trainData.getAttributes().getObservedDate();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(String destinationId) {
        this.destinationId = destinationId;
    }

    public String getDestinationStationCode() {
        return destinationStationCode;
    }

    public void setDestinationStationCode(String destinationStationCode) {
        this.destinationStationCode = destinationStationCode;
    }

    public boolean areDoorsOpenOnLeft() {
        return areDoorsOpenOnLeft;
    }

    public void setAreDoorsOpenOnLeft(boolean areDoorsOpenOnLeft) {
        this.areDoorsOpenOnLeft = areDoorsOpenOnLeft;
    }

    public boolean areDoorsOpenOnRight() {
        return areDoorsOpenOnRight;
    }

    public void setAreDoorsOpenOnRight(boolean areDoorsOpenOnRight) {
        this.areDoorsOpenOnRight = areDoorsOpenOnRight;
    }

    public boolean isAdjustingOnPlatform() {
        return isAdjustingOnPlatform;
    }

    public void setAdjustingOnPlatform(boolean adjustingOnPlatform) {
        isAdjustingOnPlatform = adjustingOnPlatform;
    }

    public boolean areDoorsOperatingManually() {
        return areDoorsOperatingManually;
    }

    public void setAreDoorsOperatingManually(boolean areDoorsOperatingManually) {
        this.areDoorsOperatingManually = areDoorsOperatingManually;
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

    public Integer getDirection() {
        return direction;
    }

    public void setDirection(Integer direction) {
        this.direction = direction;
    }

    public Calendar getObservedDate() {
        return observedDate;
    }
}

package com.jamespizzurro.metrorailserver.domain.gtfs;

import com.jamespizzurro.metrorailserver.StationUtil;

public class GtfsTrip {

    private String routeId;
    private String serviceId;
    private String tripId;
    private String tripHeadsign;
    private Integer directionId;
    private String shapeId;
    private String scheduledTripId;

    public GtfsTrip(String routeId, String serviceId, String tripId, String tripHeadsign, Integer directionId, String shapeId, String scheduledTripId) {
        this.routeId = routeId;
        this.serviceId = serviceId;
        this.tripId = tripId;
        this.tripHeadsign = tripHeadsign.replace("\"", "");
        this.directionId = directionId;
        this.shapeId = shapeId;
        this.scheduledTripId = scheduledTripId;
    }

    public String getLineCode() {
        switch (routeId) {
            case "RED":
                return "RD";
            case "ORANGE":
                return "OR";
            case "SILVER":
                return "SV";
            case "BLUE":
                return "BL";
            case "YELLOW":
                return "YL";
            case "GREEN":
                return "GR";
            default:
                return "N/A";
        }
    }

    public String[] getDestinationStationCodes() {
        return StationUtil.getStationCodesFromText(tripHeadsign.toLowerCase(), true);
    }

    public String getRouteId() {
        return routeId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getTripId() {
        return tripId;
    }

    public String getTripHeadsign() {
        return tripHeadsign;
    }

    public Integer getDirectionId() {
        return directionId;
    }

    public String getShapeId() {
        return shapeId;
    }

    public String getScheduledTripId() {
        return scheduledTripId;
    }
}

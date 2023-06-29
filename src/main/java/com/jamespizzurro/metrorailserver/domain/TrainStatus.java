package com.jamespizzurro.metrorailserver.domain;

import com.jamespizzurro.metrorailserver.Exclude;

import javax.persistence.*;
import java.util.*;

@Entity
@Table(name = "train_status",
        indexes = {
                @Index(name = "train_status_observed_date_index", columnList = "observed_date")
        }
)
public class TrainStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false,
            columnDefinition = "bigserial not null")
    @Exclude
    private Long id;

    private String trainId;
    private String realTrainId;
    @Column(name = "num_cars")
    private String Car;
    @Column(name = "destination_station")
    private String Destination;
    @Column(name = "destination_station_code")
    private String DestinationCode;
    @Column(name = "destination_station_name")
    private String DestinationName;
    @Column(name = "group_number")
    private String Group;
    @Column(name = "line_code")
    private String Line;
    @Column(name = "location_station_code")
    private String LocationCode;
    @Column(name = "location_station_name")
    private String LocationName;
    @Column(name = "eta")
    private String Min;
    private Double minutesAway;
    private transient TrackCircuit currentTrackCircuit;
    private Integer directionNumber;
    private transient Calendar scheduledTime;
    private boolean isScheduled;
    private Double maxMinutesAway;
    private int numPositiveTags;
    private int numNegativeTags;
    private int trackNumber;
    private Integer trackCircuitId;

    @Column(name = "current_station_code")
    private String currentStationCode;
    @Column(name = "current_station_name")
    private String currentStationName;

    @Column(name = "previous_station_code")
    private String PreviousStationCode;
    @Column(name = "previous_station_name")
    private String previousStationName;
    @Deprecated
    @Column(name = "should_render_on_left", nullable = false)
    private boolean ShouldRenderOnLeft;

    @Column(name = "seconds_since_last_moved", nullable = false)
    private int secondsSinceLastMoved;
    @Column(name = "is_holding_or_slow", nullable = false)
    private boolean isCurrentlyHoldingOrSlow;
    @Deprecated
    @Column(name = "num_times_delayed", nullable = false)
    private int delayedCount = 0;
    @Column(name = "num_seconds_off_schedule", nullable = false)
    private int secondsOffSchedule;

    @Exclude private String lastVisitedStationCode;
    @Exclude private Date lastVisitedStation;

    private transient Date lastMovedCircuits;

    private Integer trainSpeed;
    private boolean isNotOnRevenueTrack;
    private boolean isKeyedDown;
    private boolean wasKeyedDown;

    private String parentMin;
    private Integer rawTrackCircuitId;
    private String circuitName;
    private Integer distanceFromNextStation;

    @Exclude private Integer secondsAtLastVisitedStation;

    @Column(name = "seconds_delayed")
    @Exclude private Integer secondsDelayed;

    @Column(name = "original_destination_code")
    @Exclude private String originalDestinationCode;
    @Column(name = "original_line_code")
    @Exclude private String originalLineCode;

    private String destinationId;
    @Deprecated private Boolean areDoorsOpen;
    private Boolean areDoorsOpenOnLeft;
    private Boolean areDoorsOpenOnRight;
    @Exclude private Boolean isAdjustingOnPlatform;
    @Exclude private Boolean areDoorsOperatingManually;
    private Double lat;
    private Double lon;
    private Integer direction;

    private transient Integer trackNumberAtLastVisitedStation;
    private transient Integer directionNumberAtLastVisitedStation;
    private transient String lineCodeAtLastVisitedStation;
    private transient String destinationCodeAtLastVisitedStation;
    private UUID tripId;
    @Exclude private Calendar firstObservedTrain;

    private String destinationStationAbbreviation;
    private Double estimatedMinutesAway;

    @Transient private Map<TrainTag.TrainTagType, Integer> numTagsByType;

    @Transient private ProblemTweetResponse recentTweets;

    @Column(name = "observed_date", nullable = false)
    private Date observedDate;

    public TrainStatus() {}

    public TrainStatus(String trainId) {
        this.trainId = trainId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrainStatus that = (TrainStatus) o;
        return Objects.equals(trainId, that.trainId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trainId);
    }

    public Integer getSecondsUntilDelayed() {
        if (this.currentTrackCircuit == null) {
            return null;
        }

        return this.currentTrackCircuit.isOrHasChildOrParentStationCircuit() ? 75 : 60;
    }

    public String getTrainId() {
        return trainId;
    }

    public void setTrainId(String trainId) {
        this.trainId = trainId;
    }

    public String getRealTrainId() {
        return realTrainId;
    }

    public void setRealTrainId(String realTrainId) {
        this.realTrainId = realTrainId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCar() {
        return Car;
    }

    public void setCar(String car) {
        Car = car;
    }

    public String getDestination() {
        return Destination;
    }

    public void setDestination(String destination) {
        Destination = destination;
    }

    public String getDestinationCode() {
        return DestinationCode;
    }

    public void setDestinationCode(String destinationCode) {
        DestinationCode = destinationCode;
    }

    public String getDestinationName() {
        return DestinationName;
    }

    public void setDestinationName(String destinationName) {
        DestinationName = destinationName;
    }

    public String getGroup() {
        return Group;
    }

    public void setGroup(String group) {
        Group = group;
    }

    public String getLine() {
        return Line;
    }

    public void setLine(String line) {
        Line = line;
    }

    public String getLocationCode() {
        return LocationCode;
    }

    public void setLocationCode(String locationCode) {
        LocationCode = locationCode;
    }

    public String getLocationName() {
        return LocationName;
    }

    public void setLocationName(String locationName) {
        LocationName = locationName;
    }

    public String getMin() {
        return Min;
    }

    public void setMin(String min) {
        Min = min;
    }

    public Double getMinutesAway() {
        return minutesAway;
    }

    public void setMinutesAway(Double minutesAway) {
        this.minutesAway = minutesAway;
    }

    public TrackCircuit getCurrentTrackCircuit() {
        return currentTrackCircuit;
    }

    public void setCurrentTrackCircuit(TrackCircuit currentTrackCircuit) {
        this.currentTrackCircuit = currentTrackCircuit;
    }

    public Integer getDirectionNumber() {
        return directionNumber;
    }

    public void setDirectionNumber(Integer directionNumber) {
        this.directionNumber = directionNumber;
    }

    public Calendar getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(Calendar scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public boolean isScheduled() {
        return isScheduled;
    }

    public void setIsScheduled(boolean isScheduled) {
        this.isScheduled = isScheduled;
    }

    public Double getMaxMinutesAway() {
        return maxMinutesAway;
    }

    public void setMaxMinutesAway(Double maxMinutesAway) {
        this.maxMinutesAway = maxMinutesAway;
    }

    public int getNumPositiveTags() {
        return numPositiveTags;
    }

    public void setNumPositiveTags(int numPositiveTags) {
        this.numPositiveTags = numPositiveTags;
    }

    public int getNumNegativeTags() {
        return numNegativeTags;
    }

    public void setNumNegativeTags(int numNegativeTags) {
        this.numNegativeTags = numNegativeTags;
    }

    public int getTrackNumber() {
        return trackNumber;
    }

    public void setTrackNumber(int trackNumber) {
        this.trackNumber = trackNumber;
    }

    public int getTrackCircuitId() {
        return trackCircuitId;
    }

    public void setTrackCircuitId(int trackCircuitId) {
        this.trackCircuitId = trackCircuitId;
    }

    public String getCurrentStationCode() {
        return currentStationCode;
    }

    public void setCurrentStationCode(String currentStationCode) {
        this.currentStationCode = currentStationCode;
    }

    public String getCurrentStationName() {
        return currentStationName;
    }

    public void setCurrentStationName(String currentStationName) {
        this.currentStationName = currentStationName;
    }

    public String getPreviousStationCode() {
        return PreviousStationCode;
    }

    public void setPreviousStationCode(String previousStationCode) {
        PreviousStationCode = previousStationCode;
    }

    public String getPreviousStationName() {
        return previousStationName;
    }

    public void setPreviousStationName(String previousStationName) {
        this.previousStationName = previousStationName;
    }

    public int getSecondsSinceLastMoved() {
        return secondsSinceLastMoved;
    }

    public void setSecondsSinceLastMoved(int secondsSinceLastMoved) {
        this.secondsSinceLastMoved = secondsSinceLastMoved;
    }

    public boolean isCurrentlyHoldingOrSlow() {
        return isCurrentlyHoldingOrSlow;
    }

    public void setIsCurrentlyHoldingOrSlow(boolean isCurrentlyHoldingOrSlow) {
        this.isCurrentlyHoldingOrSlow = isCurrentlyHoldingOrSlow;
    }

    public int getSecondsOffSchedule() {
        return secondsOffSchedule;
    }

    public void setSecondsOffSchedule(int secondsOffSchedule) {
        this.secondsOffSchedule = secondsOffSchedule;
    }

    public Date getObservedDate() {
        return observedDate;
    }

    public void setObservedDate(Date observedDate) {
        this.observedDate = observedDate;
    }

    public String getLastVisitedStationCode() {
        return lastVisitedStationCode;
    }

    public void setLastVisitedStationCode(String lastVisitedStationCode) {
        this.lastVisitedStationCode = lastVisitedStationCode;
    }

    public Date getLastVisitedStation() {
        return lastVisitedStation;
    }

    public void setLastVisitedStation(Date lastVisitedStation) {
        this.lastVisitedStation = lastVisitedStation;
    }

    public Date getLastMovedCircuits() {
        return lastMovedCircuits;
    }

    public void setLastMovedCircuits(Date lastMovedCircuits) {
        this.lastMovedCircuits = lastMovedCircuits;
    }

    public Integer getTrainSpeed() {
        return trainSpeed;
    }

    public void setTrainSpeed(Integer trainSpeed) {
        this.trainSpeed = trainSpeed;
    }

    public boolean isNotOnRevenueTrack() {
        return isNotOnRevenueTrack;
    }

    public void setNotOnRevenueTrack(boolean notOnRevenueTrack) {
        isNotOnRevenueTrack = notOnRevenueTrack;
    }

    public boolean isKeyedDown() {
        return isKeyedDown;
    }

    public void setKeyedDown(boolean keyedDown) {
        isKeyedDown = keyedDown;
    }

    public boolean wasKeyedDown() {
        return wasKeyedDown;
    }

    public void setWasKeyedDown(boolean wasKeyedDown) {
        this.wasKeyedDown = wasKeyedDown;
    }

    public String getParentMin() {
        return parentMin;
    }

    public void setParentMin(String parentMin) {
        this.parentMin = parentMin;
    }

    public Integer getRawTrackCircuitId() {
        return rawTrackCircuitId;
    }

    public void setRawTrackCircuitId(Integer rawTrackCircuitId) {
        this.rawTrackCircuitId = rawTrackCircuitId;
    }

    public String getCircuitName() {
        return circuitName;
    }

    public void setCircuitName(String circuitName) {
        this.circuitName = circuitName;
    }

    public Integer getSecondsAtLastVisitedStation() {
        return secondsAtLastVisitedStation;
    }

    public void setSecondsAtLastVisitedStation(Integer secondsAtLastVisitedStation) {
        this.secondsAtLastVisitedStation = secondsAtLastVisitedStation;
    }

    public Integer getDistanceFromNextStation() {
        return distanceFromNextStation;
    }

    public void setDistanceFromNextStation(Integer distanceFromNextStation) {
        this.distanceFromNextStation = distanceFromNextStation;
    }

    public Integer getSecondsDelayed() {
        return secondsDelayed;
    }

    public void setSecondsDelayed(Integer secondsDelayed) {
        this.secondsDelayed = secondsDelayed;
    }

    public String getOriginalDestinationCode() {
        return originalDestinationCode;
    }

    public void setOriginalDestinationCode(String originalDestinationCode) {
        this.originalDestinationCode = originalDestinationCode;
    }

    public String getOriginalLineCode() {
        return originalLineCode;
    }

    public void setOriginalLineCode(String originalLineCode) {
        this.originalLineCode = originalLineCode;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(String destinationId) {
        this.destinationId = destinationId;
    }

    public Boolean areDoorsOpenOnLeft() {
        return areDoorsOpenOnLeft;
    }

    public void setAreDoorsOpenOnLeft(Boolean areDoorsOpenOnLeft) {
        this.areDoorsOpenOnLeft = areDoorsOpenOnLeft;
    }

    public Boolean areDoorsOpenOnRight() {
        return areDoorsOpenOnRight;
    }

    public void setAreDoorsOpenOnRight(Boolean areDoorsOpenOnRight) {
        this.areDoorsOpenOnRight = areDoorsOpenOnRight;
    }

    public Boolean isAdjustingOnPlatform() {
        return isAdjustingOnPlatform;
    }

    public void setAdjustingOnPlatform(Boolean adjustingOnPlatform) {
        isAdjustingOnPlatform = adjustingOnPlatform;
    }

    public Boolean areDoorsOperatingManually() {
        return areDoorsOperatingManually;
    }

    public void setAreDoorsOperatingManually(Boolean areDoorsOperatingManually) {
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

    public Integer getTrackNumberAtLastVisitedStation() {
        return trackNumberAtLastVisitedStation;
    }

    public void setTrackNumberAtLastVisitedStation(Integer trackNumberAtLastVisitedStation) {
        this.trackNumberAtLastVisitedStation = trackNumberAtLastVisitedStation;
    }

    public Integer getDirectionNumberAtLastVisitedStation() {
        return directionNumberAtLastVisitedStation;
    }

    public void setDirectionNumberAtLastVisitedStation(Integer directionNumberAtLastVisitedStation) {
        this.directionNumberAtLastVisitedStation = directionNumberAtLastVisitedStation;
    }

    public String getLineCodeAtLastVisitedStation() {
        return lineCodeAtLastVisitedStation;
    }

    public void setLineCodeAtLastVisitedStation(String lineCodeAtLastVisitedStation) {
        this.lineCodeAtLastVisitedStation = lineCodeAtLastVisitedStation;
    }

    public String getDestinationCodeAtLastVisitedStation() {
        return destinationCodeAtLastVisitedStation;
    }

    public void setDestinationCodeAtLastVisitedStation(String destinationCodeAtLastVisitedStation) {
        this.destinationCodeAtLastVisitedStation = destinationCodeAtLastVisitedStation;
    }

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }

    public Calendar getFirstObservedTrain() {
        return firstObservedTrain;
    }

    public void setFirstObservedTrain(Calendar firstObservedTrain) {
        this.firstObservedTrain = firstObservedTrain;
    }

    public String getDestinationStationAbbreviation() {
        return destinationStationAbbreviation;
    }

    public void setDestinationStationAbbreviation(String destinationStationAbbreviation) {
        this.destinationStationAbbreviation = destinationStationAbbreviation;
    }

    public Double getEstimatedMinutesAway() {
        return estimatedMinutesAway;
    }

    public void setEstimatedMinutesAway(Double estimatedMinutesAway) {
        this.estimatedMinutesAway = estimatedMinutesAway;
    }

    public Map<TrainTag.TrainTagType, Integer> getNumTagsByType() {
        return numTagsByType;
    }

    public void setNumTagsByType(Map<TrainTag.TrainTagType, Integer> numTagsByType) {
        this.numTagsByType = numTagsByType;
    }

    public ProblemTweetResponse getRecentTweets() {
        return recentTweets;
    }

    public void setRecentTweets(ProblemTweetResponse recentTweets) {
        this.recentTweets = recentTweets;
    }
}

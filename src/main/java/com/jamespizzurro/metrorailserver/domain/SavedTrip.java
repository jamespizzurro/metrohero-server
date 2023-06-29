package com.jamespizzurro.metrorailserver.domain;

import com.jamespizzurro.metrorailserver.Exclude;

import javax.persistence.*;
import java.util.*;

@Entity
@Table(name = "trip_state",
        indexes = {
                @Index(name = "trip_state_from_station_code_to_station_code_date_index", columnList = "fromStationCode,toStationCode,date")
        }
)
public class SavedTrip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false,
            columnDefinition = "bigserial not null")
    @Exclude
    private Long id;

    @Transient
    private String fromStationName;

    @Column(nullable = false)
    private String fromStationCode;

    @Transient
    private String toStationName;

    @Column(nullable = false)
    private String toStationCode;

    @Transient
    private List<String> tripStationCodes;

    private Double predictedRideTime;

    private Double expectedRideTime;

    @Transient
    private Set<String> lineCodes;

    private Double timeSinceLastTrain;

    private Double timeUntilNextTrain;

    @Transient
    private List<RailIncident> metroAlerts;

    @Transient
    private Set<String> metroAlertKeywords;

    @Transient
    private List<StationProblem> tweets;

    @Transient
    private Set<String> tweetKeywords;

    @Transient
    private List<ElevatorEscalatorOutage> fromStationElevatorOutages;

    @Transient
    private List<ElevatorEscalatorOutage> toStationElevatorOutages;

    @Transient
    private List<ElevatorEscalatorOutage> fromStationEscalatorOutages;

    @Transient
    private List<ElevatorEscalatorOutage> toStationEscalatorOutages;

    @Transient
    private List<TrainStatus> fromStationTrainStatuses;

    @Transient
    private List<ServiceGap> serviceGaps;

    @Column(nullable = false)
    private Calendar date;

    @Transient
    private RecentTripStateData recentData;

    public SavedTrip() {

    }

    public SavedTrip(String fromStationName, String fromStationCode, String toStationName, String toStationCode, List<String> tripStationCodes, Double predictedRideTime, Double expectedRideTime, Set<String> lineCodes, Double timeSinceLastTrain, Double timeUntilNextTrain, List<RailIncident> metroAlerts, List<StationProblem> tweets, List<ElevatorEscalatorOutage> fromStationElevatorOutages, List<ElevatorEscalatorOutage> toStationElevatorOutages, List<ElevatorEscalatorOutage> fromStationEscalatorOutages, List<ElevatorEscalatorOutage> toStationEscalatorOutages, List<TrainStatus> fromStationTrainStatuses, List<ServiceGap> serviceGaps, Calendar date, RecentTripStateData recentData) {
        this.fromStationName = fromStationName;
        this.fromStationCode = fromStationCode;
        this.toStationName = toStationName;
        this.toStationCode = toStationCode;
        this.tripStationCodes = tripStationCodes;
        this.predictedRideTime = predictedRideTime;
        this.expectedRideTime = expectedRideTime;
        this.lineCodes = lineCodes;
        this.timeSinceLastTrain = timeSinceLastTrain;
        this.timeUntilNextTrain = timeUntilNextTrain;
        this.metroAlerts = metroAlerts;
        this.tweets = tweets;
        this.fromStationElevatorOutages = fromStationElevatorOutages;
        this.toStationElevatorOutages = toStationElevatorOutages;
        this.fromStationEscalatorOutages = fromStationEscalatorOutages;
        this.toStationEscalatorOutages = toStationEscalatorOutages;
        this.fromStationTrainStatuses = fromStationTrainStatuses;
        this.serviceGaps = serviceGaps;
        this.date = date;
        this.recentData = recentData;

        if (this.metroAlerts != null && !this.metroAlerts.isEmpty()) {
            Set<String> metroAlertKeywords = new LinkedHashSet<>();
            for (RailIncident railIncident : this.metroAlerts) {
                metroAlertKeywords.addAll(Arrays.asList(railIncident.getKeywords()));
            }
            this.metroAlertKeywords = metroAlertKeywords;
        }

        if (this.tweets != null && !this.tweets.isEmpty()) {
            Set<String> tweetKeywords = new LinkedHashSet<>();
            for (StationProblem stationProblem : this.tweets) {
                tweetKeywords.addAll(Arrays.asList(stationProblem.getKeywords()));
            }
            this.tweetKeywords = tweetKeywords;
        }
    }

    public Long getId() {
        return id;
    }

    public String getFromStationName() {
        return fromStationName;
    }

    public void setFromStationName(String fromStationName) {
        this.fromStationName = fromStationName;
    }

    public String getFromStationCode() {
        return fromStationCode;
    }

    public void setFromStationCode(String fromStationCode) {
        this.fromStationCode = fromStationCode;
    }

    public String getToStationName() {
        return toStationName;
    }

    public void setToStationName(String toStationName) {
        this.toStationName = toStationName;
    }

    public String getToStationCode() {
        return toStationCode;
    }

    public void setToStationCode(String toStationCode) {
        this.toStationCode = toStationCode;
    }

    public List<String> getTripStationCodes() {
        return tripStationCodes;
    }

    public void setTripStationCodes(List<String> tripStationCodes) {
        this.tripStationCodes = tripStationCodes;
    }

    public Double getPredictedRideTime() {
        return predictedRideTime;
    }

    public void setPredictedRideTime(Double predictedRideTime) {
        this.predictedRideTime = predictedRideTime;
    }

    public Double getExpectedRideTime() {
        return expectedRideTime;
    }

    public void setExpectedRideTime(Double expectedRideTime) {
        this.expectedRideTime = expectedRideTime;
    }

    public Set<String> getLineCodes() {
        return lineCodes;
    }

    public void setLineCodes(Set<String> lineCodes) {
        this.lineCodes = lineCodes;
    }

    public Double getTimeSinceLastTrain() {
        return timeSinceLastTrain;
    }

    public void setTimeSinceLastTrain(Double timeSinceLastTrain) {
        this.timeSinceLastTrain = timeSinceLastTrain;
    }

    public Double getTimeUntilNextTrain() {
        return timeUntilNextTrain;
    }

    public void setTimeUntilNextTrain(Double timeUntilNextTrain) {
        this.timeUntilNextTrain = timeUntilNextTrain;
    }

    public List<RailIncident> getMetroAlerts() {
        return metroAlerts;
    }

    public void setMetroAlerts(List<RailIncident> metroAlerts) {
        this.metroAlerts = metroAlerts;
    }

    public Set<String> getMetroAlertKeywords() {
        return metroAlertKeywords;
    }

    public void setMetroAlertKeywords(Set<String> metroAlertKeywords) {
        this.metroAlertKeywords = metroAlertKeywords;
    }

    public List<StationProblem> getTweets() {
        return tweets;
    }

    public void setTweets(List<StationProblem> tweets) {
        this.tweets = tweets;
    }

    public Set<String> getTweetKeywords() {
        return tweetKeywords;
    }

    public void setTweetKeywords(Set<String> tweetKeywords) {
        this.tweetKeywords = tweetKeywords;
    }

    public List<ElevatorEscalatorOutage> getFromStationElevatorOutages() {
        return fromStationElevatorOutages;
    }

    public void setFromStationElevatorOutages(List<ElevatorEscalatorOutage> fromStationElevatorOutages) {
        this.fromStationElevatorOutages = fromStationElevatorOutages;
    }

    public List<ElevatorEscalatorOutage> getToStationElevatorOutages() {
        return toStationElevatorOutages;
    }

    public void setToStationElevatorOutages(List<ElevatorEscalatorOutage> toStationElevatorOutages) {
        this.toStationElevatorOutages = toStationElevatorOutages;
    }

    public List<ElevatorEscalatorOutage> getFromStationEscalatorOutages() {
        return fromStationEscalatorOutages;
    }

    public void setFromStationEscalatorOutages(List<ElevatorEscalatorOutage> fromStationEscalatorOutages) {
        this.fromStationEscalatorOutages = fromStationEscalatorOutages;
    }

    public List<ElevatorEscalatorOutage> getToStationEscalatorOutages() {
        return toStationEscalatorOutages;
    }

    public void setToStationEscalatorOutages(List<ElevatorEscalatorOutage> toStationEscalatorOutages) {
        this.toStationEscalatorOutages = toStationEscalatorOutages;
    }

    public List<TrainStatus> getFromStationTrainStatuses() {
        return fromStationTrainStatuses;
    }

    public void setFromStationTrainStatuses(List<TrainStatus> fromStationTrainStatuses) {
        this.fromStationTrainStatuses = fromStationTrainStatuses;
    }

    public List<ServiceGap> getServiceGaps() {
        return serviceGaps;
    }

    public void setServiceGaps(List<ServiceGap> serviceGaps) {
        this.serviceGaps = serviceGaps;
    }

    public Calendar getDate() {
        return date;
    }

    public RecentTripStateData getRecentData() {
        return recentData;
    }

    public void setRecentData(RecentTripStateData recentData) {
        this.recentData = recentData;
    }
}

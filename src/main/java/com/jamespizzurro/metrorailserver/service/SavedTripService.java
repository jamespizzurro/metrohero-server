package com.jamespizzurro.metrorailserver.service;

import com.jamespizzurro.metrorailserver.StationUtil;
import com.jamespizzurro.metrorailserver.domain.*;
import com.jamespizzurro.metrorailserver.repository.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SavedTripService {

    private static final Logger logger = LoggerFactory.getLogger(SavedTripService.class);

    private final TrainService trainService;
    private final GtfsService gtfsService;
    private final TwitterService twitterService;
    private final RailIncidentService railIncidentService;
    private final ElevatorEscalatorService elevatorEscalatorService;
    private final MetricsService metricsService;
    private final TripRepository tripRepository;

    private volatile Map<String, SavedTrip> savedTripMap;

    @Autowired
    public SavedTripService(TrainService trainService, GtfsService gtfsService, TwitterService twitterService, RailIncidentService railIncidentService, ElevatorEscalatorService elevatorEscalatorService, MetricsService metricsService, TripRepository tripRepository) {
        this.trainService = trainService;
        this.gtfsService = gtfsService;
        this.twitterService = twitterService;
        this.railIncidentService = railIncidentService;
        this.elevatorEscalatorService = elevatorEscalatorService;
        this.metricsService = metricsService;
        this.tripRepository = tripRepository;
    }

    @PostConstruct
    private void init() {
        logger.info("Initializing saved trip service...");

        this.savedTripMap = new HashMap<>();

        logger.info("...train service initialized!");
    }

    @Scheduled(fixedDelay = 2000)
    private void updateSavedTripMap() {
        logger.info("Updating saved trip map...");

        if (this.trainService.isDataStale()) {
            logger.warn("Failed to update saved trip map! Train data from WMATA is stale.");
            return;
        }

        Map<String, SavedTrip> savedTripMap = new HashMap<>();

        Calendar now = Calendar.getInstance();
        for (String fromStationCode : this.trainService.getStationCodesSet()) {
            for (String toStationCode : this.trainService.getStationCodesSet()) {
                Double expectedRideTime = this.trainService.getStationToStationMedianDurationMap().get(String.join("_", fromStationCode, toStationCode));
                List<String> tripStationCodes = this.trainService.getStationToStationTripMap().get(String.join("_", fromStationCode, toStationCode));

                TrackCircuit fromStationTrackCircuit = this.trainService.getStationTrackCircuitMap().get(fromStationCode + "_1");  // HACK: it doesn't matter which track (1 or 2) we use here
                TrackCircuit toStationTrackCircuit = this.trainService.getStationTrackCircuitMap().get(toStationCode + "_1");  // HACK: it doesn't matter which track (1 or 2) we use here

                Set<String> lineCodes = new HashSet<>(fromStationTrackCircuit.getLineCodes());
                lineCodes.retainAll(toStationTrackCircuit.getLineCodes());

                String fromStationName = StationUtil.getStationName(fromStationCode);
                String toStationName = StationUtil.getStationName(toStationCode);

                if (expectedRideTime == null || tripStationCodes == null || tripStationCodes.isEmpty()) {
                    String savedTripKey = String.join("_", fromStationCode, toStationCode);
                    SavedTrip savedTrip = new SavedTrip(fromStationName, fromStationCode, toStationName, toStationCode, null, null, null, null, null, null, null, null, null, null, null, null, null, null, now, null);
                    savedTripMap.put(savedTripKey, savedTrip);
                    continue;
                }

                // use the train schedule to determine whether or not we should expect any trains to service this trip's origin station right now
                boolean shouldExpectTrains = false;
                for (String lineCode : Arrays.asList("RD", "OR", "SV", "BL", "YL", "GR")) {
                    for (Integer directionNumber : Arrays.asList(1, 2)) {
                        if (this.gtfsService.getExpectedTrainFrequency(lineCode, directionNumber, fromStationCode) != null) {
                            shouldExpectTrains = true;
                            break;
                        }
                    }
                }
                if (!shouldExpectTrains) {
                    expectedRideTime = null;
                } else {
                    expectedRideTime -= 0.5d;  // 30 seconds from duration map for first station in trip - 30 seconds = no boarding time
                }

                Set<String> possibleDestinationStationCodes = new HashSet<>();
                for (Set<String> stationCodesPastFromStation : fromStationTrackCircuit.getChildStationCodes().values()) {
                    if (stationCodesPastFromStation.contains(toStationCode)) {
                        possibleDestinationStationCodes.addAll(stationCodesPastFromStation);
                    }
                }
                for (Set<String> stationCodesPastFromStation : fromStationTrackCircuit.getParentStationCodes().values()) {
                    if (stationCodesPastFromStation.contains(toStationCode)) {
                        possibleDestinationStationCodes.addAll(stationCodesPastFromStation);
                    }
                }
                for (Set<String> stationCodesPastToStation : toStationTrackCircuit.getChildStationCodes().values()) {
                    if (!stationCodesPastToStation.contains(fromStationCode)) {
                        possibleDestinationStationCodes.retainAll(stationCodesPastToStation);
                    }
                }
                for (Set<String> stationCodesPastToStation : toStationTrackCircuit.getParentStationCodes().values()) {
                    if (!stationCodesPastToStation.contains(fromStationCode)) {
                        possibleDestinationStationCodes.retainAll(stationCodesPastToStation);
                    }
                }
                possibleDestinationStationCodes.add(toStationCode);

                /////
                // get time from last train that departed trip origin station platform

                Double timeSinceLastTrain = null;

                for (String possibleDestinationStationCode : possibleDestinationStationCodes) {
                    for (String possibleLineCode : Arrays.asList("RD", "OR", "SV", "BL", "YL", "GR")) {
                        DepartureInfo latestDepartureInfo = this.trainService.getLastStationDepartureMap().get(String.join("_", fromStationCode, possibleLineCode, possibleDestinationStationCode));
                        if (latestDepartureInfo == null) {
                            continue;
                        }

                        double timeSinceLastTrainSoFar = ((now.getTimeInMillis() - latestDepartureInfo.getDepartureTime().getTimeInMillis()) / 1000d) / 60d; // milliseconds -> minutes
                        if (timeSinceLastTrain == null || timeSinceLastTrainSoFar < timeSinceLastTrain) {
                            timeSinceLastTrain = timeSinceLastTrainSoFar;
                        }
                    }
                }

                /////
                // get time until next train arrives at trip origin station platform
                // and all next train predictions from origin station platform that will service this trip

                Double timeUntilNextTrain = null;
                List<TrainStatus> filteredFromStationTrainStatuses = null;
                Integer tripDirection = null;

                List<TrainStatus> fromStationTrainStatuses = this.trainService.getStationTrainStatusesMap().get(fromStationCode); // already sorted by ETA in ascending order
                if (fromStationTrainStatuses != null) {
                    filteredFromStationTrainStatuses = new ArrayList<>();

                    for (TrainStatus fromStationTrainStatus : fromStationTrainStatuses) {
                        if (possibleDestinationStationCodes.contains(fromStationTrainStatus.getDestinationCode())) {
                            if (tripDirection == null) {
                                tripDirection = fromStationTrainStatus.getDirectionNumber();
                            }

                            if (timeUntilNextTrain == null && !fromStationTrainStatus.isScheduled()) {
                                timeUntilNextTrain = fromStationTrainStatus.getMinutesAway();
                            }

                            filteredFromStationTrainStatuses.add(fromStationTrainStatus);
                            lineCodes.add(fromStationTrainStatus.getLine());
                        }
                    }
                }

                /////
                // get predicted trip time

                Double predictedRideTime = this.trainService.getPredictedRideTime(now, fromStationCode, toStationCode, null);

                /////
                // get relevant MetroAlerts

                List<RailIncident> tripMetroAlertsSorted = null;

                Set<RailIncident> tripMetroAlerts = new HashSet<>();

                // by lines that are scheduled to service this trip
                for (String lineCode : lineCodes) {
                    List<RailIncident> metroAlerts = this.railIncidentService.getRailIncidentsByLine().get(lineCode);
                    if (metroAlerts != null && !metroAlerts.isEmpty()) {
                        tripMetroAlerts.addAll(metroAlerts);
                    }
                }

                // by stations visited during the trip
                for (String stationCode : tripStationCodes) {
                    List<RailIncident> metroAlerts = this.railIncidentService.getStationRailIncidentsMap().get(stationCode);
                    if (metroAlerts != null && !metroAlerts.isEmpty()) {
                        tripMetroAlerts.addAll(metroAlerts);
                    }
                }

                if (!tripMetroAlerts.isEmpty()) {
                    tripMetroAlertsSorted = new ArrayList<>(tripMetroAlerts);
                    tripMetroAlertsSorted.sort(RailIncidentService.getRailIncidentDescendingOrderComparator());
                }

                /////
                // get relevant tweets

                List<StationProblem> relevantTweets = new ArrayList<>();

                List<StationProblem> mostRecentTweets = this.twitterService.getMostRecentTweets();  // already sorted by tweet time in descending order
                if (mostRecentTweets != null) {
                    for (StationProblem mostRecentTweet : mostRecentTweets) {
                        boolean isRelevant = false;

                        for (String lineCode : mostRecentTweet.getLineCodes()) {
                            if (lineCodes.contains(lineCode)) {
                                isRelevant = true;
                                break;
                            }
                        }
                        if (!isRelevant) {
                            for (String stationCode : mostRecentTweet.getStationCodes()) {
                                if (tripStationCodes.contains(stationCode)) {
                                    isRelevant = true;
                                    break;
                                }
                            }
                        }

                        if (isRelevant) {
                            relevantTweets.add(mostRecentTweet);
                        }
                    }
                }

                if (relevantTweets.isEmpty()) {
                    relevantTweets = null;
                }

                /////
                // get relevant service gaps

                List<ServiceGap> relevantServiceGaps = new ArrayList<>();

                if (tripDirection != null && this.metricsService.getSystemMetrics() != null && this.metricsService.getSystemMetrics().getLineMetricsByLine() != null) {
                    for (SystemMetrics.LineMetrics lineMetrics : this.metricsService.getSystemMetrics().getLineMetricsByLine().values()) {
                        if (lineMetrics.getServiceGaps() != null) {
                            for (ServiceGap serviceGap : lineMetrics.getServiceGaps()) {
                                if (!tripDirection.equals(serviceGap.getDirectionNumber())) {
                                    continue;
                                }

                                Set<String> serviceGapStationCodes = this.trainService.getStationCodes(serviceGap.getFromStationCode(), serviceGap.getToStationCode());
                                serviceGapStationCodes.retainAll(tripStationCodes);
                                if (serviceGapStationCodes.isEmpty()) {
                                    continue;
                                }

                                relevantServiceGaps.add(serviceGap);
                            }
                        }
                    }
                }

                /////
                // get elevator and escalator outages at origin and destination stations

                List<ElevatorEscalatorOutage> fromStationElevatorOutages = this.elevatorEscalatorService.getElevatorOutagesByStation().get(fromStationCode);
                List<ElevatorEscalatorOutage> toStationElevatorOutages = this.elevatorEscalatorService.getElevatorOutagesByStation().get(toStationCode);

                List<ElevatorEscalatorOutage> fromStationEscalatorOutages = this.elevatorEscalatorService.getEscalatorOutagesByStation().get(fromStationCode);
                List<ElevatorEscalatorOutage> toStationEscalatorOutages = this.elevatorEscalatorService.getEscalatorOutagesByStation().get(toStationCode);

                /////

                String savedTripKey = String.join("_", fromStationCode, toStationCode);
                SavedTrip savedTrip = new SavedTrip(fromStationName, fromStationCode, toStationName, toStationCode, tripStationCodes, predictedRideTime, expectedRideTime, lineCodes, timeSinceLastTrain, timeUntilNextTrain, tripMetroAlertsSorted, relevantTweets, fromStationElevatorOutages, toStationElevatorOutages, fromStationEscalatorOutages, toStationEscalatorOutages, filteredFromStationTrainStatuses, relevantServiceGaps, now, getRecentTripStateData(fromStationCode, toStationCode));
                savedTripMap.put(savedTripKey, savedTrip);
            }
        }

        this.savedTripMap = savedTripMap;

        logger.info("...successfully updated saved trip map!");
    }

    @Scheduled(fixedRate = 30000)  // every 30 seconds (independent of last run)
    private void persistSavedTripMap() {
        this.tripRepository.saveAll(this.savedTripMap.values().stream().filter(st -> st.getExpectedRideTime() != null).collect(Collectors.toList()));
    }

    private RecentTripStateData getRecentTripStateData(String fromStationCode, String toStationCode) {
        List<Object[]> results = this.tripRepository.getRecentData(fromStationCode, toStationCode);
        if (results == null || results.size() != 1) {
            return null;
        }

        return new RecentTripStateData(results.get(0));
    }

    public Map<String, SavedTrip> getSavedTripMap() {
        return savedTripMap;
    }
}

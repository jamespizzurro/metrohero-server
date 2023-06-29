package com.jamespizzurro.metrorailserver.web;

import com.jamespizzurro.metrorailserver.domain.*;
import com.jamespizzurro.metrorailserver.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@EnableAutoConfiguration
public class TrainController {

    private static final Logger logger = LoggerFactory.getLogger(TrainController.class);

    private final TrainService trainService;
    private final MetricsService metricsService;
    private final TwitterService twitterService;
    private final RailIncidentService railIncidentService;
    private final TrainTaggingService trainTaggingService;
    private final StationTaggingService stationTaggingService;
    private final SpeedRestrictionService speedRestrictionService;
    private final ElevatorEscalatorService elevatorEscalatorService;
    private final SavedTripService savedTripService;
    private final TrainDepartureService trainDepartureService;

    @Autowired
    public TrainController(TrainService trainService, MetricsService metricsService, TwitterService twitterService, RailIncidentService railIncidentService, TrainTaggingService trainTaggingService, StationTaggingService stationTaggingService, SpeedRestrictionService speedRestrictionService, ElevatorEscalatorService elevatorEscalatorService, SavedTripService savedTripService, TrainDepartureService trainDepartureService) {
        this.trainService = trainService;
        this.metricsService = metricsService;
        this.twitterService = twitterService;
        this.railIncidentService = railIncidentService;
        this.trainTaggingService = trainTaggingService;
        this.stationTaggingService = stationTaggingService;
        this.speedRestrictionService = speedRestrictionService;
        this.elevatorEscalatorService = elevatorEscalatorService;
        this.savedTripService = savedTripService;
        this.trainDepartureService = trainDepartureService;
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @RequestMapping(value = "/system", method = RequestMethod.GET)
    public SystemInfo getSystemInfo(
            @RequestParam(required = false) String[] tripStationCodesKeys
    ) {
        SystemInfo systemInfo = new SystemInfo();
        systemInfo.setTrainStatuses(new ArrayList<>(this.trainService.getTrainStatusesMap().values()));
        systemInfo.setBetweenStationDelayStatuses(this.trainService.getBetweenStationDelayStatuses());
        systemInfo.setSystemMetrics(this.metricsService.getSystemMetrics());
        systemInfo.setLastUpdatedTimestamp(this.trainService.getLastUpdatedTimestamp());
        systemInfo.setLineRailIncidents(this.railIncidentService.getLineRailIncidents());
        systemInfo.setStationNumPositiveTagsMap(this.stationTaggingService.getNumPositiveTagsByStation());
        systemInfo.setStationNumNegativeTagsMap(this.stationTaggingService.getNumNegativeTagsByStation());
        systemInfo.setSpeedRestrictions(this.speedRestrictionService.getSpeedRestrictions());
        systemInfo.setStationHasRailIncidentsMap(this.railIncidentService.getStationHasRailIncidentsMap());
        systemInfo.setStationHasTwitterProblemMap(this.twitterService.getStationHasTwitterProblemMap());
        systemInfo.setHasElevatorOutagesByStation(this.elevatorEscalatorService.getHasElevatorOutagesByStation());
        systemInfo.setHasEscalatorOutagesByStation(this.elevatorEscalatorService.getHasEscalatorOutagesByStation());
        systemInfo.setRecentTrainFrequencyData(this.metricsService.getRecentTrainFrequencyData());

        if (tripStationCodesKeys != null && this.savedTripService.getSavedTripMap() != null) {
            Map<String, SavedTrip> savedTrips = new LinkedHashMap<>();
            for (String tripStationCodesKey : tripStationCodesKeys) {
                SavedTrip savedTrip = this.savedTripService.getSavedTripMap().get(tripStationCodesKey);
                if (savedTrip == null) {
                    continue;
                }

                savedTrips.put(tripStationCodesKey, savedTrip);
            }
            systemInfo.setSavedTrips(savedTrips);
        }

        return systemInfo;
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @RequestMapping(value = "/trains/history", method = RequestMethod.GET)
    public List<TrainStatus> getTrainStatusesForTimestamp(
            @RequestParam long timestamp
    ) {
        return this.trainService.getTrainStatusesForTimestamp(timestamp);
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @RequestMapping(value = "/station/{stationCode}", method = RequestMethod.GET)
    public StationReport getStationTrainStatuses(
            @PathVariable("stationCode") String stationCode
    ) {
        return new StationReport(
                this.trainService.getStationTrainStatusesMap().get(stationCode),
                this.twitterService.getStationTwitterProblemMap().get(stationCode),
                this.railIncidentService.getStationRailIncidentsMap().get(stationCode),
                this.elevatorEscalatorService.getElevatorOutagesByStation().get(stationCode),
                this.elevatorEscalatorService.getEscalatorOutagesByStation().get(stationCode),
                this.trainService.getCrowdingStatusByStation().get(stationCode)
        );
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @RequestMapping(value = "/train/{trainId}", method = RequestMethod.GET)
    public TrainTagReport getTrainTagReport(
            @PathVariable("trainId") String trainId,
            @RequestParam String userId
    ) {
        TrainStatus trainStatus = this.trainService.getTrainStatusesMap().get(trainId);
        if (trainStatus == null || trainStatus.getScheduledTime() != null) {
            return null;
        }

        return this.trainTaggingService.generateTagReport(userId, trainId);
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @RequestMapping(value = "/train/{trainId}/tag", method = RequestMethod.POST)
    public TrainTagReport tagTrain(
            @PathVariable("trainId") String trainId,
            @RequestParam String userId,
            @RequestParam TrainTag.TrainTagType tagType
    ) {
        TrainStatus trainStatus = this.trainService.getTrainStatusesMap().get(trainId);
        if (trainStatus == null || trainStatus.getScheduledTime() != null) {
            return null;
        }

        if (userId == null || userId.isEmpty() || userId.equals("null")) {
            return this.trainTaggingService.generateTagReport(userId, trainId);
        }

        return this.trainTaggingService.tag(userId, trainId, trainStatus.getRealTrainId(), trainStatus.getLine(), trainStatus.getLocationCode(), tagType, null);
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @RequestMapping(value = "/train/{trainId}/untag", method = RequestMethod.POST)
    public TrainTagReport untagTrain(
            @PathVariable("trainId") String trainId,
            @RequestParam String userId,
            @RequestParam TrainTag.TrainTagType tagType
    ) {
        TrainStatus trainStatus = this.trainService.getTrainStatusesMap().get(trainId);
        if (trainStatus == null || trainStatus.getScheduledTime() != null) {
            return null;
        }

        if (userId == null || userId.isEmpty() || userId.equals("null")) {
            return this.trainTaggingService.generateTagReport(userId, trainId);
        }

        return this.trainTaggingService.untag(userId, trainId, tagType);
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @RequestMapping(value = "/station/{stationCode}/tags", method = RequestMethod.GET)
    public StationTagReport getStationTagReport(
            @PathVariable("stationCode") String stationCode,
            @RequestParam String userId
    ) {
        if (!this.trainService.getStationCodesSet().contains(stationCode)) {
            return null;
        }

        return this.stationTaggingService.generateTagReport(userId, stationCode);
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @RequestMapping(value = "/station/{stationCode}/tag", method = RequestMethod.POST)
    public StationTagReport tagStation(
            @PathVariable("stationCode") String stationCode,
            @RequestParam String userId,
            @RequestParam StationTag.StationTagType tagType
    ) {
        if (!this.trainService.getStationCodesSet().contains(stationCode)) {
            return null;
        }

        if (userId == null || userId.isEmpty() || userId.equals("null")) {
            return this.stationTaggingService.generateTagReport(userId, stationCode);
        }

        String[] lineCodes;
        TrackCircuit stationTrackCircuit = this.trainService.getStationTrackCircuitMap().get(stationCode + "_1");  // HACK: it doesn't matter which track (1 or 2) we use here
        if (stationTrackCircuit != null && stationTrackCircuit.getLineCodes() != null) {
            lineCodes = stationTrackCircuit.getLineCodes().toArray(new String[0]);
        } else {
            lineCodes = new String[0];
        }

        return this.stationTaggingService.tag(userId, lineCodes, stationCode, tagType, null);
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @RequestMapping(value = "/station/{stationCode}/untag", method = RequestMethod.POST)
    public StationTagReport untagStation(
            @PathVariable("stationCode") String stationCode,
            @RequestParam String userId,
            @RequestParam StationTag.StationTagType tagType
    ) {
        if (!this.trainService.getStationCodesSet().contains(stationCode)) {
            return null;
        }

        if (userId == null || userId.isEmpty() || userId.equals("null")) {
            return this.stationTaggingService.generateTagReport(userId, stationCode);
        }

        return this.stationTaggingService.untag(userId, stationCode, tagType);
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @RequestMapping(value = "/trip/{tripStationCodesKey}", method = RequestMethod.GET)
    public SavedTrip getSavedTrip(
            @PathVariable("tripStationCodesKey") String tripStationCodesKey
    ) {
        return this.savedTripService.getSavedTripMap().get(tripStationCodesKey);
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @RequestMapping(value = "/departuresByLine")
    public Map<String, List<TrainDepartureInfo>> getTrainDepartureInfoByLine(
            @RequestParam(required = false) String departureStationCode,
            @RequestParam(required = false) String lineCode,
            @RequestParam(required = false) Integer directionNumber,
            @RequestParam(required = false) String destinationStationCode
    ) {
        return this.trainDepartureService.getTrainDepartureInfoByLine(departureStationCode, lineCode, directionNumber, destinationStationCode);
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @RequestMapping(value = "/trains/mareydiagram", method = RequestMethod.GET)
    public List<TrainStatusForMareyDiagram> getTrainDataForMareyDiagram() {
        return this.trainService.getTrainDataOverLastHour();
    }
}

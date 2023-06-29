package com.jamespizzurro.metrorailserver.web;

import com.jamespizzurro.metrorailserver.StationUtil;
import com.jamespizzurro.metrorailserver.domain.*;
import com.jamespizzurro.metrorailserver.service.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@RestController
@EnableAutoConfiguration
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class PublicApiController {

    private static final Logger logger = LoggerFactory.getLogger(PublicApiController.class);

    private final TrainService trainService;
    private final TrainTaggingService trainTaggingService;
    private final StationTaggingService stationTaggingService;
    private final MetricsService metricsService;
    private final SavedTripService savedTripService;
    private final TwitterService twitterService;
    private final PublicApiService publicApiService;

    @Autowired
    public PublicApiController(TrainService trainService, TrainTaggingService trainTaggingService, StationTaggingService stationTaggingService, MetricsService metricsService, SavedTripService savedTripService, TwitterService twitterService, PublicApiService publicApiService) {
        this.trainService = trainService;
        this.trainTaggingService = trainTaggingService;
        this.stationTaggingService = stationTaggingService;
        this.metricsService = metricsService;
        this.savedTripService = savedTripService;
        this.twitterService = twitterService;
        this.publicApiService = publicApiService;
    }

    @RequestMapping(method = RequestMethod.GET, value = "/metrorail/metrics")
    public ResponseEntity<SystemMetrics> getSystemMetrics() {
        SystemMetrics systemMetrics = this.metricsService.getSystemMetrics();
        if (systemMetrics == null) {
            // we're not ready to accept requests yet
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null);
        }

        return ResponseEntity.status(HttpStatus.OK).body(systemMetrics);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/metrorail/trips/{fromStationCode}/{toStationCode}")
    public ResponseEntity<SavedTrip> getTripInfo(
            @PathVariable(value = "fromStationCode", required = false) String fromStationCode,
            @PathVariable(value = "toStationCode", required = false) String toStationCode
    ) {
        if (StringUtils.isEmpty(fromStationCode) || StringUtils.isEmpty(toStationCode)) {
            // no station code(s) specified
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        if (!StationUtil.getStationCodeMap().containsKey(fromStationCode) || !StationUtil.getStationCodeMap().containsKey(toStationCode)) {
            // invalid station code(s) specified
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        String tripStationCodesKey = fromStationCode + "_" + toStationCode;
        SavedTrip savedTrip = this.savedTripService.getSavedTripMap().get(tripStationCodesKey);
        if (savedTrip == null) {
            // invalid station code(s) or trip
            // note: trips involving a transfer are currently not directly supported and are considered invalid
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        return ResponseEntity.status(HttpStatus.OK).body(savedTrip);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/metrorail/tweets")
    public ResponseEntity<List<StationProblem>> getTweets() {
        List<StationProblem> systemMetrics = this.twitterService.getMostRecentTweets();
        if (systemMetrics == null) {
            // we're not ready to accept requests yet
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null);
        }

        return ResponseEntity.status(HttpStatus.OK).body(systemMetrics);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/metrorail/trains")
    public ResponseEntity<Collection<TrainStatus>> getTrains() {
        return ResponseEntity.status(HttpStatus.OK).body(this.trainService.getTrainStatusesMap().values());
    }

    @RequestMapping(method = RequestMethod.GET, value = "/metrorail/trains/tags")
    public ResponseEntity<Map<String, TrainTagReport>> getTagsByTrain() {
        return ResponseEntity.status(HttpStatus.OK).body(this.trainTaggingService.getTrainTagReports());
    }

    @RequestMapping(method = RequestMethod.GET, value = "/metrorail/trains/{trainId}/tags")
    public ResponseEntity<TrainTagReport> getTagsForTrain(
            @PathVariable(value = "trainId", required = false) String trainId
    ) {
        if (StringUtils.isEmpty(trainId)) {
            // no train id specified
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        if (!this.trainService.getTrainStatusesMap().containsKey(trainId)) {
            // invalid train id specified
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        return ResponseEntity.status(HttpStatus.OK).body(this.trainTaggingService.generateTagReport(null, trainId));
    }

    @RequestMapping(method = RequestMethod.GET, value = "/metrorail/stations/trains")
    public ResponseEntity<Map<String, List<TrainStatus>>> getTrainsByStation(
            @RequestParam(value = "includeScheduledPredictions", required = false, defaultValue = "false") boolean includeScheduledPredictions
    ) {
        Map<String, List<TrainStatus>> stationTrainStatusesMap = this.trainService.getStationTrainStatusesMap();

        Map<String, List<TrainStatus>> filteredStationTrainStatusesMap = new HashMap<>(stationTrainStatusesMap.size());

        for (Map.Entry<String, List<TrainStatus>> entry : stationTrainStatusesMap.entrySet()) {
            String stationCode = entry.getKey();
            List<TrainStatus> trainStatuses = entry.getValue();

            if (stationCode.contains("|")) {
                // filter out combined station train statuses, e,g. Metro Center: A01|C01
                continue;
            }

            if (trainStatuses != null && !includeScheduledPredictions) {
                List<TrainStatus> realtimeTrainStatuses = new ArrayList<>();

                for (TrainStatus trainStatus : trainStatuses) {
                    if (!trainStatus.isScheduled()) {
                        realtimeTrainStatuses.add(trainStatus);
                    }
                }

                trainStatuses = realtimeTrainStatuses;
            }

            filteredStationTrainStatusesMap.put(stationCode, trainStatuses);
        }

        return ResponseEntity.status(HttpStatus.OK).body(filteredStationTrainStatusesMap);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/metrorail/stations/tags")
    public ResponseEntity<Map<String, StationTagReport>> getTagsByStation() {
        return ResponseEntity.status(HttpStatus.OK).body(this.stationTaggingService.getStationTagReports());
    }

    @RequestMapping(method = RequestMethod.GET, value = "/metrorail/stations/{stationCode}/trains")
    public ResponseEntity<List<TrainStatus>> getTrainsForStation(
            @PathVariable(value = "stationCode", required = false) String stationCode,
            @RequestParam(value = "includeScheduledPredictions", required = false, defaultValue = "false") boolean includeScheduledPredictions
    ) {
        if (StringUtils.isEmpty(stationCode)) {
            // no station code specified
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        for (String splitStationCode : stationCode.split(",")) {
            if (!StationUtil.getStationCodeMap().containsKey(splitStationCode)) {
                // invalid station code specified
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }
        }

        List<TrainStatus> trainStatuses = this.trainService.getStationTrainStatusesMap().get(stationCode.replace(",", "|"));

        if (trainStatuses != null && !includeScheduledPredictions) {
            List<TrainStatus> realtimeTrainStatuses = new ArrayList<>();

            for (TrainStatus trainStatus : trainStatuses) {
                if (!trainStatus.isScheduled()) {
                    realtimeTrainStatuses.add(trainStatus);
                }
            }

            trainStatuses = realtimeTrainStatuses;
        }

        return ResponseEntity.status(HttpStatus.OK).body(trainStatuses);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/metrorail/stations/{stationCode}/tags")
    public ResponseEntity<StationTagReport> getTagsForStation(
            @PathVariable(value = "stationCode", required = false) String stationCode
    ) {
        if (StringUtils.isEmpty(stationCode)) {
            // no station code specified
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        for (String splitStationCode : stationCode.split(",")) {
            if (!StationUtil.getStationCodeMap().containsKey(splitStationCode)) {
                // invalid station code specified
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }
        }

        return ResponseEntity.status(HttpStatus.OK).body(this.stationTaggingService.generateTagReport(null, stationCode.replace(",", "|")));
    }

    @RequestMapping(method = RequestMethod.GET, value = "/metrobus/routes/{routeId}/metrics")
    public ResponseEntity<String> getMetricsForRoute(
            @PathVariable(value = "routeId", required = false) String routeId
    ) {
        return ResponseEntity.status(HttpStatus.GONE).body(null);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/metrobus/routes/{routeId}/buses")
    public ResponseEntity<String> getBusesForRoute(
            @PathVariable(value = "routeId", required = false) String routeId
    ) {
        return ResponseEntity.status(HttpStatus.GONE).body(null);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/metrobus/buses/tags")
    public ResponseEntity<String> getTagsByBus() {
        return ResponseEntity.status(HttpStatus.GONE).body(null);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/metrobus/buses/{busId}/tags")
    public ResponseEntity<String> getTagsForBus(
            @PathVariable(value = "busId", required = false) String busId
    ) {
        return ResponseEntity.status(HttpStatus.GONE).body(null);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/metrobus/stops/{stopId}/buses")
    public ResponseEntity<String> getBusesForStop(
            @PathVariable(value = "stopId", required = false) String stopId,
            @RequestParam(value = "includeScheduledPredictions", required = false, defaultValue = "false") boolean includeScheduledPredictions
    ) {
        return ResponseEntity.status(HttpStatus.GONE).body(null);
    }

    @RequestMapping(method = RequestMethod.GET, value = "/metrobus/stops/{stopId}/tags")
    public ResponseEntity<String> getTagsForStop(
            @PathVariable(value = "stopId", required = false) String stopId
    ) {
        return ResponseEntity.status(HttpStatus.GONE).body(null);
    }

    @Scheduled(cron = "* * * * * *")   // runs every second
    private void clearApiRateLimits() {
//        logger.info("Resetting public API rate limits...");

        this.publicApiService.getNumSecondlyApiRequestsByApiKey().clear();

        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime previousStartOfDay = this.publicApiService.getPreviousNumDailyApiRequestRateLimitResetStartOfDay();
        if (!startOfDay.equals(previousStartOfDay)) {
            logger.info("Resetting public API daily rate limits...");
            this.publicApiService.getNumDailyApiRequestsByApiKey().clear();
            this.publicApiService.setPreviousNumDailyApiRequestRateLimitResetStartOfDay(startOfDay);
            logger.info("...successfully reset public API daily rate limits!");
        }

//        logger.info("...successfully reset public API rate limits!");
    }
}

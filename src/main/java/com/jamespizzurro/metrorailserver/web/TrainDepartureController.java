package com.jamespizzurro.metrorailserver.web;

import com.jamespizzurro.metrorailserver.domain.TrainDepartureMetrics;
import com.jamespizzurro.metrorailserver.domain.TrainDepartures;
import com.jamespizzurro.metrorailserver.service.TrainDepartureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.bind.annotation.*;

import java.util.Calendar;

@RestController
@EnableAutoConfiguration
public class TrainDepartureController {

    private static final Logger logger = LoggerFactory.getLogger(TrainDepartureController.class);

    private final TrainDepartureService trainDepartureService;

    @Autowired
    public TrainDepartureController(TrainDepartureService trainDepartureService) {
        this.trainDepartureService = trainDepartureService;
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @RequestMapping(value = "/api/trainDepartures/earliestDepartureTime", method = RequestMethod.GET)
    public Calendar getEarliestTrainDepartureTime() {
        return this.trainDepartureService.getEarliestDepartureTimeString();
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @RequestMapping(value = "/api/trainDepartures/metrics", method = RequestMethod.GET)
    public TrainDepartureMetrics getTrainDepartureMetrics(
            @RequestParam Long fromDateUnixTimestamp,
            @RequestParam Long toDateUnixTimestamp,
            @RequestParam(required=false) String departureStationCode,
            @RequestParam(required=false) String lineCode,
            @RequestParam(required=false) Integer directionNumber
    ) {
        if (departureStationCode != null && departureStationCode.isEmpty()) {
            departureStationCode = null;
        }

        if (lineCode != null && lineCode.isEmpty()) {
            lineCode = null;
        }

        return this.trainDepartureService.getDepartureMetrics(fromDateUnixTimestamp, toDateUnixTimestamp, departureStationCode, lineCode, directionNumber);
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @RequestMapping(value = "/api/trainDepartures", method = RequestMethod.GET)
    public TrainDepartures getTrainDepartures(
            @RequestParam Long fromDateUnixTimestamp,
            @RequestParam Long toDateUnixTimestamp,
            @RequestParam(required=false) String departureStationCode,
            @RequestParam(required=false) String lineCode,
            @RequestParam(required=false) Integer directionNumber,
            @RequestParam(required=false) String sortByColumn,
            @RequestParam(required=false) String sortByOrder,
            @RequestParam(required=false) Integer maxResultCount,
            @RequestParam(required=false) Integer resultCountOffset
    ) {
        if (departureStationCode != null && departureStationCode.isEmpty()) {
            departureStationCode = null;
        }

        if (lineCode != null && lineCode.isEmpty()) {
            lineCode = null;
        }

        if (sortByColumn != null) {
            if (sortByColumn.isEmpty()) {
                sortByColumn = null;
            } else {
                // TODO: sorting options other than default mostly untested
                switch (sortByColumn) {
                    case "scheduledLineName":
                    case "scheduledLineCode":
                    case "observedLineName":
                    case "observedLineCode":
                        sortByColumn = "COALESCE(observed_line_code, scheduled_line_code)";
                        break;
                    case "departureStationName":
                    case "departureStationCode":
                        sortByColumn = "departure_station_code";
                        break;
                    case "scheduledDestinationStationName":
                    case "scheduledDestinationStationCode":
                    case "observedDestinationStationName":
                    case "observedDestinationStationCode":
                        sortByColumn = "COALESCE(observed_destination_station_code, scheduled_destination_station_code)";
                        break;
                    case "observedDepartureTime":
                    case "observedDepartureTimeString":
                        sortByColumn = "observed_departure_time";
                        break;
                    case "scheduledDepartureTime":
                    case "scheduledDepartureTimeString":
                        sortByColumn = "scheduled_departure_time";
                        break;
                    case "trainId":
                        sortByColumn = "train_id";
                        break;
                    case "scheduledHeadway":
                        sortByColumn = "scheduled_headway";
                        break;
                    case "minutesOffSchedule":
                        sortByColumn = "minutes_off_schedule";
                        break;
                }
            }
        }
        if (sortByColumn == null) {
            sortByColumn = "COALESCE(observed_departure_time, scheduled_departure_time)";  // default
        }

        if (sortByOrder != null) {
            if (sortByOrder.isEmpty()) {
                sortByOrder = null;
            } else {
                // TODO: sorting options other than default mostly untested
                switch (sortByColumn.toLowerCase()) {
                    case "asc":
                        sortByOrder = "ASC";
                        break;
                    case "desc":
                        sortByOrder = "DESC";
                        break;
                }
            }
        }
        if (sortByOrder == null) {
            sortByOrder = "ASC";    // default
        }

        if (maxResultCount != null) {
            maxResultCount = Math.max(Math.min(maxResultCount, 100), 0);    // clamp between 0 and 100
        } else {
            maxResultCount = 25;    // default
        }

        if (resultCountOffset != null) {
            resultCountOffset = Math.max(resultCountOffset, 0);    // clamp at 0
        } else {
            resultCountOffset = 0;    // default
        }

        return this.trainDepartureService.getDeparturesData(fromDateUnixTimestamp, toDateUnixTimestamp, departureStationCode, lineCode, directionNumber, sortByColumn, sortByOrder, maxResultCount, resultCountOffset);
    }
}

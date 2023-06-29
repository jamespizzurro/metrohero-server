package com.jamespizzurro.metrorailserver.service;

import com.jamespizzurro.metrorailserver.ConfigUtil;
import com.jamespizzurro.metrorailserver.StationUtil;
import com.jamespizzurro.metrorailserver.domain.TrainDeparture;
import com.jamespizzurro.metrorailserver.domain.TrainStatus;
import com.jamespizzurro.metrorailserver.domain.gtfs.GtfsTrip;
import com.jamespizzurro.metrorailserver.repository.TrainDepartureRepository;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.lang.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class GtfsService {

    private static final Logger logger = LoggerFactory.getLogger(GtfsService.class);

    private static final int SCHEDULE_ADHERENCE_THRESHOLD = 2;  // in minutes
    private static final int EXPECTED_TRAIN_FREQUENCY_THRESHOLD = 30;   // in minutes

    private final ConfigUtil configUtil;
    private final TrainDepartureRepository scheduledTrainDeparturesRepository;

    private volatile Map<String /* lineCode */, Map<Integer /* directionNumber */, Map<String /* dateString_tripId */, TreeSet<Calendar>>>> scheduledTrainArrivalTimesByLineAndDirectionAndTrip;
    private volatile Map<String /* lineCode */, Map<Integer /* directionNumber */, Map<String /* stationCode */, TreeMap<Calendar, Boolean /* hasAlreadyBeenObserved */>>>> scheduledTrainArrivalTimesByLineAndDirectionAndStation;
    private volatile Map<String /* lineCode */, Set<String /* stationCode */>> scheduledDestinationStationCodesByLine;
    private volatile Map<String /* lineCode_directionNumber */, Set<String /* stationCode */>> scheduledDestinationStationCodesByLineAndDirection;

    @Autowired
    public GtfsService(ConfigUtil configUtil, TrainDepartureRepository scheduledTrainDeparturesRepository) {
        this.configUtil = configUtil;
        this.scheduledTrainDeparturesRepository = scheduledTrainDeparturesRepository;

        this.scheduledDestinationStationCodesByLine = new HashMap<>(0);
        this.scheduledDestinationStationCodesByLineAndDirection = new HashMap<>(0);
    }

    public Map<String, List<TrainStatus>> buildStationScheduledTrainStatusesMap() {
        String currentDirectoryPath = Paths.get(".").toAbsolutePath().normalize().toString() + "/gtfs/";
        new File(currentDirectoryPath).mkdirs();    // create the gtfs directory if it doesn't exist already

        logger.info("...downloading and extracting latest GTFS data from WMATA...");
        try {
            URL url = new URL(this.configUtil.getWmataNewGTFSFeedUrl() + "?api_key=" + this.configUtil.getWmataApiKey());
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(60000);
            conn.setReadTimeout(60000);
            Path targetPath = new File(currentDirectoryPath + "gtfs.zip").toPath();
            Files.copy(conn.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            ZipFile zipFile = new ZipFile(currentDirectoryPath + "gtfs.zip");
            zipFile.extractAll(currentDirectoryPath);
        } catch (IOException e) {
            logger.warn("Failed to download or unzip latest GTFS data from WMATA! Falling back to using old data, if available...", e);
        }
        logger.info("...parsing GTFS data and actually building the map...");

        Date currentDate = new Date();
        Calendar now = Calendar.getInstance();

        Map<String, List<TrainStatus>> stationScheduledTrainStatusesMap = new HashMap<>();
        List<TrainDeparture> futureScheduledTrainDepartures = new ArrayList<>();
        Map<String, Map<Integer, Map<String, TreeSet<Calendar>>>> scheduledTrainArrivalTimesByLineAndDirectionAndTrip = new HashMap<>();
        Map<String, Map<Integer, Map<String, TreeMap<Calendar, Boolean>>>> scheduledTrainArrivalTimesByLineAndDirectionAndStation = new HashMap<>();
        Map<String, Set<String>> scheduledDestinationStationCodesByLine = new HashMap<>();
        Map<String, Set<String>> scheduledDestinationStationCodesByLineAndDirection = new HashMap<>();

        Scanner scanner;

        HashMap<String, Set<String>> dateStringsByServiceId = new HashMap<>();

        String fileName = "calendar_dates.txt";
        boolean skipLine = true;   // for skipping the first line of the file, which contains column headers
        try {
            scanner = new Scanner(new FileInputStream(new File(currentDirectoryPath + fileName)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        Calendar startOfYesterday = Calendar.getInstance();
        startOfYesterday.add(Calendar.DATE, -1);
        startOfYesterday = DateUtils.truncate(startOfYesterday, Calendar.DATE);
        Calendar endOfTomorrow = Calendar.getInstance();
        endOfTomorrow.add(Calendar.DATE, 1);
        endOfTomorrow.setTime(DateUtils.addMilliseconds(DateUtils.ceiling(endOfTomorrow.getTime(), Calendar.DATE), -1));
        while (scanner.hasNextLine()) {
            String row = scanner.nextLine();
            if (skipLine) {
                skipLine = false;
                continue;
            }

            String[] rowData = row.split(",", -1);
            if (rowData.length != 3) {
                logger.warn("Malformed row data in " + fileName + ": " + row);
                continue;
            }

            String serviceId = rowData[0];
            String dateString = rowData[1];

            Calendar date = Calendar.getInstance();
            try {
                date.setTime(new SimpleDateFormat("yyyyMMdd").parse(dateString));
            } catch (ParseException e) {
                logger.warn("Failed to parse date [" + dateString + "] using SimpleDateFormat [yyyyMMdd]! Skipping row: " + row, e);
                continue;
            }

            // filter out service schedules that aren't for today or tomorrow
            if (date.equals(startOfYesterday) || (date.after(startOfYesterday) && date.before(endOfTomorrow)) || date.equals(endOfTomorrow)) {
                Set<String> dateStrings = dateStringsByServiceId.computeIfAbsent(serviceId, k -> new HashSet<>());
                dateStrings.add(dateString);
            }
        }
        scanner.close();

        Map<String, GtfsTrip> tripMap = new HashMap<>();

        fileName = "trips.txt";
        skipLine = true;   // for skipping the first line of the file, which contains column headers
        try {
            scanner = new Scanner(new FileInputStream(new File(currentDirectoryPath + fileName)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        while (scanner.hasNextLine()) {
            String row = scanner.nextLine();
            if (skipLine) {
                skipLine = false;
                continue;
            }

            String[] rowData = row.split(",", -1);
            if (rowData.length != 8 && rowData.length != 9) {
                logger.warn("Malformed row data in " + fileName + ": " + row);
                continue;
            }

            String routeId = rowData[0];
            String serviceId = rowData[1];
            String tripId = rowData[2];
            String tripHeadsign = rowData[3];
            Integer directionId = Integer.parseInt(rowData[4]);
            String scheduledTripId = rowData[7];

            if (StringUtils.isEmpty(scheduledTripId)) {
                continue;
            }

            if (dateStringsByServiceId.get(serviceId) == null) {
                continue;
            }

            GtfsTrip trip = new GtfsTrip(routeId, serviceId, tripId, tripHeadsign, directionId, null, scheduledTripId);

            String lineCode = trip.getLineCode();
            if ("N/A".equals(lineCode)) {
                continue;
            }

            tripMap.put(trip.getTripId(), trip);
        }
        scanner.close();

        fileName = "stop_times.txt";
        skipLine = true;   // for skipping the first line of the file, which contains column headers
        try {
            scanner = new Scanner(new FileInputStream(new File(currentDirectoryPath + fileName)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
//        Map<Long, TrainStatus> firstTrainStatusByTripId = new HashMap<>(tripMap.size());
        while (scanner.hasNextLine()) {
            String row = scanner.nextLine();
            if (skipLine) {
                skipLine = false;
                continue;
            }

            String[] rowData = row.split(",", -1);
            if (rowData.length != 8) {
                logger.warn("Malformed row data in " + fileName + ": " + row);
                continue;
            }

            String tripId = rowData[0];
            String arrivalTimeString = rowData[1];
            String stopId = rowData[3];
            Integer stopSequence = Integer.parseInt(rowData[4]);

            GtfsTrip trip = tripMap.get(tripId);
            if (trip == null) {
                continue;
            }

            Set<String> dateStrings = dateStringsByServiceId.get(trip.getServiceId());
            if (dateStrings == null) {
                continue;
            }

            String lineCode = trip.getLineCode();

            // filter out stop IDs that aren't associated with a platform at a Metrorail station stop
            String stationCode = stopId.startsWith("PF_") ? stopId.split("_")[1] : null;
            if (stationCode == null) {
                logger.warn("Unrecognized stop ID [" + stopId + "] does not match any known station codes! Skipping row: " + row);
                continue;
            }

            String direction;
            if (trip.getDirectionId() == 0) {
                direction = "1";
            } else if (trip.getDirectionId() == 1) {
                direction = "2";
            } else {
                logger.warn("Unrecognized direction [" + trip.getDirectionId() + "]! Skipping row: " + row);
                continue;
            }
            int directionNum = Integer.parseInt(direction);

            String destinationStationCode = null;
            String[] possibleDestinationStationCodes = trip.getDestinationStationCodes();
            if (possibleDestinationStationCodes != null && possibleDestinationStationCodes.length > 0) {
                if (possibleDestinationStationCodes.length == 1) {
                    destinationStationCode = possibleDestinationStationCodes[0];
                } else {
                    for (String possibleDestinationStationCode : possibleDestinationStationCodes) {
                        if (
                            // L'Enfant Plaza
                            ("D03".equals(possibleDestinationStationCode) && ("OR".equals(lineCode) || "SV".equals(lineCode) || "BL".equals(lineCode))) ||
                            ("F03".equals(possibleDestinationStationCode) && ("YL".equals(lineCode) || "GR".equals(lineCode))) ||

                            // Fort Totten
                            ("E06".equals(possibleDestinationStationCode) && ("GR".equals(lineCode) || "YL".equals(lineCode))) ||
                            ("B06".equals(possibleDestinationStationCode) && "RD".equals(lineCode)) ||

                            // Gallery Place
                            ("B01".equals(possibleDestinationStationCode) && "RD".equals(lineCode)) ||
                            ("F01".equals(possibleDestinationStationCode) && ("GR".equals(lineCode) || "YL".equals(lineCode))) ||

                            // Metro Center
                            ("A01".equals(possibleDestinationStationCode) && "RD".equals(lineCode)) ||
                            ("C01".equals(possibleDestinationStationCode) && ("OR".equals(lineCode) || "SV".equals(lineCode) || "BL".equals(lineCode)))
                        ) {
                            destinationStationCode = possibleDestinationStationCode;
                            break;
                        }
                    }

                    if (destinationStationCode == null) {
                        logger.warn("Unable to derive a single destination station code from line [" + trip.getRouteId() + "] and headsign [" + trip.getTripHeadsign() + "]! Possible matches: " + Arrays.toString(possibleDestinationStationCodes) + ". Skipping stop...");
                        continue;
                    }
                }
            }
            if (destinationStationCode == null) {
                logger.warn("Unable to derive a destination station code from line [" + trip.getRouteId() + "] and headsign [" + trip.getTripHeadsign() + "]! Skipping stop...");
                continue;
            }

            String stationName = StationUtil.getStationName(stationCode);
            if (stationName == null) {
                logger.warn("Unable to derive a station name from station code [" + stationCode + "]! Skipping stop...");
                continue;
            }

            String destinationStationName = StationUtil.getStationName(destinationStationCode);
            if (destinationStationName == null) {
                logger.warn("Unable to derive a destination station name from destination station code [" + destinationStationCode + "]! Skipping stop...");
                continue;
            }

            for (String dateString : dateStrings) {
                Calendar departureTime = Calendar.getInstance();
                String dateTimeString = dateString + " " + arrivalTimeString;
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                    sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
                    departureTime.setTime(sdf.parse(dateTimeString));
                } catch (ParseException e) {
                    logger.warn("Failed to parse date time [" + dateString + "] using SimpleDateFormat [yyyyMMdd HH:mm:ss]! Skipping row: " + row, e);
                    continue;
                }

//                TrainStatus firstTrainStatus = firstTrainStatusByTripId.get(tripId);

                TrainStatus trainStatus = new TrainStatus(trip.getScheduledTripId());
                trainStatus.setRealTrainId(null);
                trainStatus.setCar("N/A");
                trainStatus.setDestination(destinationStationName);
                trainStatus.setDestinationCode(destinationStationCode);
                trainStatus.setDestinationStationAbbreviation(StationUtil.getStationAbbreviation(destinationStationCode));
                trainStatus.setDestinationName(destinationStationName);
                trainStatus.setGroup(direction);
                trainStatus.setLine(lineCode);
                trainStatus.setCurrentStationCode(stationCode);
                trainStatus.setCurrentStationName(stationName);
//                trainStatus.setCurrentStationCode((firstTrainStatus != null) ? firstTrainStatus.getCurrentStationCode() : stationCode);
//                trainStatus.setCurrentStationName((firstTrainStatus != null) ? StationUtil.getStationName(firstTrainStatus.getCurrentStationCode()) : stationName);
                trainStatus.setLocationCode(stationCode);
                trainStatus.setLocationName(stationName);
                trainStatus.setMin(new SimpleDateFormat("h:mm").format(departureTime.getTime()));   // important bit right here!
                trainStatus.setMinutesAway(null);   // this gets set later in TrainService.getCurrentScheduledTrainStatusesForStation
                trainStatus.setEstimatedMinutesAway(null);
                trainStatus.setNumPositiveTags(0);
                trainStatus.setNumNegativeTags(0);
                trainStatus.setTrackNumber(directionNum);

                // the track circuit stuff below is handled for us in TrainService, to avoid a circular dependency
//                trainStatus.setCurrentTrackCircuit(trackCircuit);
//                trainStatus.setTrackCircuitId(trackCircuit.getId());
//                trainStatus.setRawTrackCircuitId(trackCircuit.getId());

                trainStatus.setDirectionNumber(directionNum);
                trainStatus.setScheduledTime(departureTime);
                trainStatus.setIsScheduled(true);
                trainStatus.setMaxMinutesAway(null);
                trainStatus.setPreviousStationCode(null);
                trainStatus.setPreviousStationName(null);
                trainStatus.setSecondsSinceLastMoved(0);
                trainStatus.setIsCurrentlyHoldingOrSlow(false);
                trainStatus.setSecondsOffSchedule(0);
                trainStatus.setLastMovedCircuits(null);
                trainStatus.setCircuitName(null);
                trainStatus.setDistanceFromNextStation(null);
                trainStatus.setObservedDate(currentDate);   // this gets set later in TrainService.getCurrentScheduledTrainStatusesForStation

                List<TrainStatus> trainStatuses = stationScheduledTrainStatusesMap.computeIfAbsent(stationCode, k -> new ArrayList<>());
                trainStatuses.add(trainStatus);

                boolean isFirstStopInSequence = (stopSequence == 1);
                boolean isLastStopInSequence = (!scanner.hasNextLine() || (scanner.hasNext("(.+)") && scanner.hasNext("(.*),(.*),(.*),(.*),1,(.*),(.*),(.*)")));

//                if (isFirstStopInSequence) {
//                    firstTrainStatusByTripId.put(tripId, trainStatus);
//                }

                // skip the last stop in the sequence; we're only interested in scheduled train *departures*, not scheduled train arrivals at destination stations
                // we're also only interested in *future* scheduled train departures, as we assume anything in the past is already persisted in our db
                if (!isLastStopInSequence && departureTime.after(now)) {
                    futureScheduledTrainDepartures.add(new TrainDeparture(lineCode, directionNum, stationCode, stationName, destinationStationCode, destinationStationName, departureTime, trip.getScheduledTripId()));
                }

                Map<Integer, Map<String, TreeSet<Calendar>>> scheduledTrainArrivalTimesForLineByDirectionAndTrip = scheduledTrainArrivalTimesByLineAndDirectionAndTrip.computeIfAbsent(lineCode, k -> new HashMap<>());
                Map<String, TreeSet<Calendar>> scheduledTrainArrivalTimesForLineAndDirectionByTrip = scheduledTrainArrivalTimesForLineByDirectionAndTrip.computeIfAbsent(directionNum, k -> new HashMap<>());
                TreeSet<Calendar> scheduledTrainArrivalTimesForLineAndDirectionAndTrip = scheduledTrainArrivalTimesForLineAndDirectionByTrip.computeIfAbsent(String.join("_", dateString, trip.getScheduledTripId()), k -> new TreeSet<>());
                scheduledTrainArrivalTimesForLineAndDirectionAndTrip.add(departureTime);

                // skip the first stop in the sequence; we're only interested in arrival times, not departure times from the trip's origin station
                // (this is only true of scheduledTrainArrivalTimesByLineAndDirectionAndStation; we still want departure times from origin stations for scheduledTrainArrivalTimesByLineAndDirectionAndTrip)
                if (!isFirstStopInSequence) {
                    Map<Integer, Map<String, TreeMap<Calendar, Boolean>>> scheduledTrainArrivalTimesForLineByDirectionAndStation = scheduledTrainArrivalTimesByLineAndDirectionAndStation.computeIfAbsent(lineCode, k -> new HashMap<>());
                    Map<String, TreeMap<Calendar, Boolean>> scheduledTrainArrivalTimesForLineAndDirectionByStation = scheduledTrainArrivalTimesForLineByDirectionAndStation.computeIfAbsent(directionNum, k -> new HashMap<>());
                    TreeMap<Calendar, Boolean> scheduledTrainArrivalTimesForLineAndDirectionAndStation = scheduledTrainArrivalTimesForLineAndDirectionByStation.computeIfAbsent(stationCode, k -> new TreeMap<>());

                    // we could read from this.scheduledTrainArrivalTimesByLineAndDirectionAndStation now to determine whether or not this scheduled arrival already happened,
                    // but then we'd be missing out on any scheduled arrivals that occur from the time we're done here until the rest of this schedule data processing is done, and that could be a long time after this
                    // so, we fill this in at the very end of the schedule data processing
                    scheduledTrainArrivalTimesForLineAndDirectionAndStation.put(departureTime, null);
                }

                // use current scheduled destination station codes to inform real-time data, if necessary
                if (Math.abs(TimeUnit.MILLISECONDS.toMinutes(departureTime.getTimeInMillis() - now.getTimeInMillis())) <= 60) {
                    Set<String> scheduledDestinationStationCodesForLine = scheduledDestinationStationCodesByLine.computeIfAbsent(lineCode, k -> new HashSet<>());
                    scheduledDestinationStationCodesForLine.add(destinationStationCode);

                    Set<String> scheduledDestinationStationCodesForLineAndDirection = scheduledDestinationStationCodesByLineAndDirection.computeIfAbsent(String.join("_", lineCode, direction), k -> new HashSet<>());
                    scheduledDestinationStationCodesForLineAndDirection.add(destinationStationCode);
                }
            }
        }
        scanner.close();

        for (List<TrainStatus> trainStatuses : stationScheduledTrainStatusesMap.values()) {
            trainStatuses.sort(Comparator.comparing(TrainStatus::getScheduledTime));
        }

        // carry over any relevant scheduled arrivals that occurred since the last schedule data update
        // (we do this last to maximize carry-over, rather than doing it earlier and potentially missing out on scheduled arrivals that occur from then through the rest of the time we're processing schedule data)
        if (this.scheduledTrainArrivalTimesByLineAndDirectionAndStation != null) {
            for (Map.Entry<String, Map<Integer, Map<String, TreeMap<Calendar, Boolean>>>> scheduledTrainArrivalTimesForLineByDirectionAndStationEntry : scheduledTrainArrivalTimesByLineAndDirectionAndStation.entrySet()) {
                String lineCode = scheduledTrainArrivalTimesForLineByDirectionAndStationEntry.getKey();
                Map<Integer, Map<String, TreeMap<Calendar, Boolean>>> scheduledTrainArrivalTimesForLineByDirectionAndStation = scheduledTrainArrivalTimesForLineByDirectionAndStationEntry.getValue();

                Map<Integer, Map<String, TreeMap<Calendar, Boolean>>> previousScheduledTrainArrivalTimesForLineByDirectionAndStation = this.scheduledTrainArrivalTimesByLineAndDirectionAndStation.get(lineCode);
                if (previousScheduledTrainArrivalTimesForLineByDirectionAndStation != null) {
                    for (Map.Entry<Integer, Map<String, TreeMap<Calendar, Boolean>>> scheduledTrainArrivalTimesForLineAndDirectionByStationEntry : scheduledTrainArrivalTimesForLineByDirectionAndStation.entrySet()) {
                        Integer directionId = scheduledTrainArrivalTimesForLineAndDirectionByStationEntry.getKey();
                        Map<String, TreeMap<Calendar, Boolean>> scheduledTrainArrivalTimesForLineAndDirectionByStation = scheduledTrainArrivalTimesForLineAndDirectionByStationEntry.getValue();

                        Map<String, TreeMap<Calendar, Boolean>> previousScheduledTrainArrivalTimesForLineAndDirectionByStation = previousScheduledTrainArrivalTimesForLineByDirectionAndStation.get(directionId);
                        if (previousScheduledTrainArrivalTimesForLineAndDirectionByStation != null) {
                            for (Map.Entry<String, TreeMap<Calendar, Boolean>> scheduledTrainArrivalTimesForLineAndDirectionAndStationEntry : scheduledTrainArrivalTimesForLineAndDirectionByStation.entrySet()) {
                                String stopId = scheduledTrainArrivalTimesForLineAndDirectionAndStationEntry.getKey();
                                TreeMap<Calendar, Boolean> scheduledTrainArrivalTimesForLineAndDirectionAndStation = scheduledTrainArrivalTimesForLineAndDirectionAndStationEntry.getValue();

                                TreeMap<Calendar, Boolean> previousScheduledTrainArrivalTimesForLineAndDirectionAndStation = previousScheduledTrainArrivalTimesForLineAndDirectionByStation.get(stopId);
                                if (previousScheduledTrainArrivalTimesForLineAndDirectionAndStation != null) {
                                    for (Calendar visitTime : scheduledTrainArrivalTimesForLineAndDirectionAndStation.keySet()) {
                                        scheduledTrainArrivalTimesForLineAndDirectionAndStation.put(visitTime, previousScheduledTrainArrivalTimesForLineAndDirectionAndStation.get(visitTime));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        this.scheduledTrainArrivalTimesByLineAndDirectionAndTrip = scheduledTrainArrivalTimesByLineAndDirectionAndTrip;
        this.scheduledTrainArrivalTimesByLineAndDirectionAndStation = scheduledTrainArrivalTimesByLineAndDirectionAndStation;
        this.scheduledDestinationStationCodesByLine = scheduledDestinationStationCodesByLine;
        this.scheduledDestinationStationCodesByLineAndDirection = scheduledDestinationStationCodesByLineAndDirection;

        // purge any future scheduled departures from any earlier GTFS parsing, and store off the new ones
        this.scheduledTrainDeparturesRepository.removeFutureScheduled(now.getTimeInMillis());
        this.scheduledTrainDeparturesRepository.saveAll(futureScheduledTrainDepartures);

        return stationScheduledTrainStatusesMap;
    }

    public int getExpectedNumTrains(String lineCode) {
        if (this.scheduledTrainArrivalTimesByLineAndDirectionAndTrip == null) {
            return 0;
        }

        Map<Integer, Map<String, TreeSet<Calendar>>> scheduledTrainArrivalTimesForLineByDirectionAndTrip = this.scheduledTrainArrivalTimesByLineAndDirectionAndTrip.get(lineCode);
        if (scheduledTrainArrivalTimesForLineByDirectionAndTrip == null) {
            return 0;
        }

        int numTrains = 0;

        for (Integer directionNumber : scheduledTrainArrivalTimesForLineByDirectionAndTrip.keySet()) {
            numTrains += getExpectedNumTrains(lineCode, directionNumber);
        }

        return numTrains;
    }

    public int getExpectedNumTrains(String lineCode, Integer directionNumber) {
        if (this.scheduledTrainArrivalTimesByLineAndDirectionAndTrip == null) {
            return 0;
        }

        Map<Integer, Map<String, TreeSet<Calendar>>> scheduledTrainArrivalTimesForLineByDirectionAndTrip = this.scheduledTrainArrivalTimesByLineAndDirectionAndTrip.get(lineCode);
        if (scheduledTrainArrivalTimesForLineByDirectionAndTrip == null) {
            return 0;
        }

        Map<String, TreeSet<Calendar>> scheduledTrainArrivalTimesForLineAndDirectionByTrip = scheduledTrainArrivalTimesForLineByDirectionAndTrip.get(directionNumber);
        if (scheduledTrainArrivalTimesForLineAndDirectionByTrip == null) {
            return 0;
        }

        int numTrains = 0;

        Calendar now = Calendar.getInstance();
        for (TreeSet<Calendar> scheduledTrainArrivalTimesForLineAndDirectionAndTrip : scheduledTrainArrivalTimesForLineAndDirectionByTrip.values()) {
            Calendar nextLowest = scheduledTrainArrivalTimesForLineAndDirectionAndTrip.lower(now);
            if (nextLowest == null) {
                continue;
            }

            Calendar nextHighest = scheduledTrainArrivalTimesForLineAndDirectionAndTrip.higher(now);
            if (nextHighest == null) {
                continue;
            }

            double expectedTrainFrequency = ((nextHighest.getTimeInMillis() - nextLowest.getTimeInMillis()) / 1000d) / 60d;
            if (expectedTrainFrequency <= EXPECTED_TRAIN_FREQUENCY_THRESHOLD) {
                numTrains++;
            }
        }

        return numTrains;
    }

    public Double getExpectedTrainFrequency(String lineCode, Integer directionNumber, String stationCode) {
        return getExpectedTrainFrequency(Calendar.getInstance(), lineCode, directionNumber, stationCode);
    }

    public Double getExpectedTrainFrequency(Calendar time, String lineCode, Integer directionNumber, String stationCode) {
        if (this.scheduledTrainArrivalTimesByLineAndDirectionAndStation == null) {
            return null;
        }

        Map<Integer, Map<String, TreeMap<Calendar, Boolean>>> scheduledTrainArrivalTimesForLineByDirectionAndStation = this.scheduledTrainArrivalTimesByLineAndDirectionAndStation.get(lineCode);
        if (scheduledTrainArrivalTimesForLineByDirectionAndStation == null) {
            return null;
        }

        Map<String, TreeMap<Calendar, Boolean>> scheduledTrainArrivalTimesForLineAndDirectionByStation = scheduledTrainArrivalTimesForLineByDirectionAndStation.get(directionNumber);
        if (scheduledTrainArrivalTimesForLineAndDirectionByStation == null) {
            return null;
        }

        TreeMap<Calendar, Boolean> scheduledTrainArrivalTimesForLineAndDirectionAndStation = scheduledTrainArrivalTimesForLineAndDirectionByStation.get(stationCode);
        if (scheduledTrainArrivalTimesForLineAndDirectionAndStation == null) {
            return null;
        }

        Calendar nextLowest = scheduledTrainArrivalTimesForLineAndDirectionAndStation.lowerKey(time);
        if (nextLowest == null) {
            return null;
        }

        Calendar nextHighest = scheduledTrainArrivalTimesForLineAndDirectionAndStation.higherKey(time);
        if (nextHighest == null) {
            return null;
        }

        double expectedTrainFrequency = ((nextHighest.getTimeInMillis() - nextLowest.getTimeInMillis()) / 1000d) / 60d;
        return (expectedTrainFrequency <= EXPECTED_TRAIN_FREQUENCY_THRESHOLD) ? expectedTrainFrequency : null;
    }

    public void setAdherenceToSchedule(String lineCode, Integer directionNumber, String stationCode, Calendar calendar) {
        if (this.scheduledTrainArrivalTimesByLineAndDirectionAndStation == null) {
            return;
        }

        Map<Integer, Map<String, TreeMap<Calendar, Boolean>>> scheduledTrainArrivalTimesForLineByDirectionAndStation = this.scheduledTrainArrivalTimesByLineAndDirectionAndStation.get(lineCode);
        if (scheduledTrainArrivalTimesForLineByDirectionAndStation == null) {
            return;
        }

        Map<String, TreeMap<Calendar, Boolean>> scheduledTrainArrivalTimesForLineAndDirectionByStation = scheduledTrainArrivalTimesForLineByDirectionAndStation.get(directionNumber);
        if (scheduledTrainArrivalTimesForLineAndDirectionByStation == null) {
            return;
        }

        TreeMap<Calendar, Boolean> scheduledTrainArrivalTimesForLineAndDirectionAndStation = scheduledTrainArrivalTimesForLineAndDirectionByStation.get(stationCode);
        if (scheduledTrainArrivalTimesForLineAndDirectionAndStation == null) {
            return;
        }

        Map.Entry<Calendar, Boolean> nextLowest = scheduledTrainArrivalTimesForLineAndDirectionAndStation.lowerEntry(calendar);
        Map.Entry<Calendar, Boolean> nextHighest = scheduledTrainArrivalTimesForLineAndDirectionAndStation.higherEntry(calendar);

        Double minutesSinceLowest = (nextLowest != null) ? (((calendar.getTimeInMillis() - nextLowest.getKey().getTimeInMillis()) / 1000d) / 60d) : null;
        Double minutesUntilHighest = (nextHighest != null) ? (((nextHighest.getKey().getTimeInMillis() - calendar.getTimeInMillis()) / 1000d) / 60d) : null;

        if (minutesSinceLowest != null && (minutesUntilHighest == null || minutesSinceLowest <= minutesUntilHighest)) {
            if ((nextLowest.getValue() == null || !nextLowest.getValue()) && minutesSinceLowest <= SCHEDULE_ADHERENCE_THRESHOLD) {
                scheduledTrainArrivalTimesForLineAndDirectionAndStation.put(nextLowest.getKey(), true);
            } else if (nextHighest != null && (nextHighest.getValue() == null || !nextHighest.getValue()) && minutesUntilHighest <= SCHEDULE_ADHERENCE_THRESHOLD) {
                scheduledTrainArrivalTimesForLineAndDirectionAndStation.put(nextHighest.getKey(), true);
            }
        } else if (minutesUntilHighest != null) {
            if ((nextHighest.getValue() == null || !nextHighest.getValue()) && minutesUntilHighest <= SCHEDULE_ADHERENCE_THRESHOLD) {
                scheduledTrainArrivalTimesForLineAndDirectionAndStation.put(nextHighest.getKey(), true);
            } else if (nextLowest != null && (nextLowest.getValue() == null || !nextLowest.getValue()) && minutesSinceLowest <= SCHEDULE_ADHERENCE_THRESHOLD) {
                scheduledTrainArrivalTimesForLineAndDirectionAndStation.put(nextLowest.getKey(), true);
            }
        }
    }

    public Boolean isAdheringToSchedule(String lineCode, Integer directionNumber, String stationCode) {
        if (this.scheduledTrainArrivalTimesByLineAndDirectionAndStation == null) {
            return null;
        }

        Map<Integer, Map<String, TreeMap<Calendar, Boolean>>> scheduledTrainArrivalTimesForLineByDirectionAndStation = this.scheduledTrainArrivalTimesByLineAndDirectionAndStation.get(lineCode);
        if (scheduledTrainArrivalTimesForLineByDirectionAndStation == null) {
            return null;
        }

        Map<String, TreeMap<Calendar, Boolean>> scheduledTrainArrivalTimesForLineAndDirectionByStation = scheduledTrainArrivalTimesForLineByDirectionAndStation.get(directionNumber);
        if (scheduledTrainArrivalTimesForLineAndDirectionByStation == null) {
            return null;
        }

        TreeMap<Calendar, Boolean> scheduledTrainArrivalTimesForLineAndDirectionAndStation = scheduledTrainArrivalTimesForLineAndDirectionByStation.get(stationCode);
        if (scheduledTrainArrivalTimesForLineAndDirectionAndStation == null) {
            return null;
        }

        // get the last scheduled train arrival for a full SCHEDULE_ADHERENCE_THRESHOLD in the past
        // this ensures we are always looking just far back enough not to dock trains for not adhering to schedule even though they still have time to be considered adhering to schedule
        Calendar scheduleAdherenceThresholdAgo = Calendar.getInstance();
        scheduleAdherenceThresholdAgo.add(Calendar.MINUTE, -SCHEDULE_ADHERENCE_THRESHOLD);
        Map.Entry<Calendar, Boolean> nextLowest = scheduledTrainArrivalTimesForLineAndDirectionAndStation.lowerEntry(scheduleAdherenceThresholdAgo);
        if (nextLowest != null) {
            if (nextLowest.getValue() != null) {
                return nextLowest.getValue();
            } else {
                // assume scheduled train arrivals didn't occur if we didn't observe them
                return false;
            }
        }

        return null;
    }

    public Map<String, Set<String>> getScheduledDestinationStationCodesByLine() {
        return scheduledDestinationStationCodesByLine;
    }

    public Map<String, Set<String>> getScheduledDestinationStationCodesByLineAndDirection() {
        return scheduledDestinationStationCodesByLineAndDirection;
    }
}

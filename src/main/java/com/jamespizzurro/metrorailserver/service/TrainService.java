package com.jamespizzurro.metrorailserver.service;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.jamespizzurro.metrorailserver.*;
import com.jamespizzurro.metrorailserver.domain.*;
import com.jamespizzurro.metrorailserver.domain.marshallers.*;
import com.jamespizzurro.metrorailserver.repository.*;
import com.machinepublishers.jbrowserdriver.*;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.*;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TrainService {

    private static final Logger logger = LoggerFactory.getLogger(TrainService.class);

    private final ConfigUtil configUtil;
    private final TrainStatusRepository trainStatusRepository;
    private final TrainTaggingService trainTaggingService;
    private final StationToStationTravelTimeRepository stationToStationTravelTimeRepository;
    private final StationToStationTripRepository stationToStationTripRepository;
    private final TrackCircuitInfoRepository trackCircuitInfoRepository;
    private final TripRepository tripRepository;
    private final GtfsService gtfsService;
    private final TrainOffloadRepository trainOffloadRepository;
    private final TrainDisappearanceRepository trainDisappearanceRepository;
    private final TrainExpressedStationEventRepository trainExpressedStationEventRepository;
    private final DuplicateTrainEventRepository duplicateTrainEventRepository;
    private final TrainDepartureRepository trainDepartureRepository;
    private final TwitterBotService twitterBotService;
    private final DestinationCodeMappingRepository destinationCodeMappingRepository;
    private final TrackCircuitService trackCircuitService;
    private final TwitterService twitterService;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    // variables exposed outside of this service
    // (these still don't necessarily have to be volatile or thread safe; variables that are only ever modified before any other thread could possibly access them, e.g. during initialization, don't need to be)
    private volatile Map<String, TrainStatus> trainStatusesMap;
    private volatile Map<String, List<TrainStatus>> stationTrainStatusesMap;
    private volatile Map<String, SystemInfo.BetweenStationDelayStatus> betweenStationDelayStatuses;
    private Set<String> stationCodesSet;
    private Map<String, TrackCircuit> stationTrackCircuitMap;
    private Map<String /* departingStationCode_lineCode_destinationStationCode */, DepartureInfo> lastStationDepartureMap;
    private Map<String /* departingStationCode_lineCode_directionNumber */, ArrivalInfo> lastStationArrivalMap;
    private volatile Map<String, Double> stationToStationMedianDurationMap;
    private volatile Map<String, List<String>> stationToStationTripMap;
    private Set<Integer> terminalStationTrackCircuitIdSet;
    private AtomicLong lastUpdatedTimestamp;  // in epoch seconds
    private volatile List<TrainStatusForMareyDiagram> trainDataOverLastHour;

    // variables not exposed outside of this service
    private Map<Integer, TrackCircuitInfo> trackCircuitInfoMap;
    private Map<Integer, TrackCircuit> trackCircuitMap;
    private Map<String, StationToStationTravelTime> stationToStationInfoMap;    // only stations to neighboring stations
    private volatile Map<String, Double> stationToStationDurationMap;
    private Map<String, List<Integer>> stationToStationCircuitsMap;
    private volatile Map<String, List<TrainStatus>> stationScheduledTrainStatusesMap;
    private volatile Map<String, List<TrainStatus>> terminalStationScheduledTrainStatusesMap;
    private volatile Map<DestinationCodeMappingPrimaryKey, DestinationCodeMapping> destinationCodeMap;
    private TrainPositions lastTrainPositions;
    private BiMap<String, String> keptTrainIdByRemovedTrainId;
    private Map<String /* fromStationCode_toStationCode */, Double> lastStationToStationTripTimeMap;
    private Map<String /* fromStationCode_toStationCode */, Double> lastStationToStationTimeAtStationMap;
    private Map<String /* fromStationCode_toStationCode */, Calendar> lastStationToStationTripTimeCalendarMap;
    private Map<String, String> crowdingStatusByStation;
    private TrainPredictions previousTrainPredictions;
    private Map<String, String> derivedLineCodeByDestinationId;

    @Autowired
    public TrainService(ConfigUtil configUtil, TrainStatusRepository trainStatusRepository, TrainTaggingService trainTaggingService, StationToStationTravelTimeRepository stationToStationTravelTimeRepository, StationToStationTripRepository stationToStationTripRepository, TrackCircuitInfoRepository trackCircuitInfoRepository, TripRepository tripRepository, GtfsService gtfsService, TrainOffloadRepository trainOffloadRepository, TrainDisappearanceRepository trainDisappearanceRepository, TrainExpressedStationEventRepository trainExpressedStationEventRepository, DuplicateTrainEventRepository duplicateTrainEventRepository, TrainDepartureRepository trainDepartureRepository, TwitterBotService twitterBotService, DestinationCodeMappingRepository destinationCodeMappingRepository, TrackCircuitService trackCircuitService, @Lazy TwitterService twitterService, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.configUtil = configUtil;
        this.trainStatusRepository = trainStatusRepository;
        this.trainTaggingService = trainTaggingService;
        this.stationToStationTravelTimeRepository = stationToStationTravelTimeRepository;
        this.stationToStationTripRepository  = stationToStationTripRepository;
        this.trackCircuitInfoRepository = trackCircuitInfoRepository;
        this.tripRepository = tripRepository;
        this.gtfsService = gtfsService;
        this.trainOffloadRepository = trainOffloadRepository;
        this.trainDisappearanceRepository = trainDisappearanceRepository;
        this.trainExpressedStationEventRepository = trainExpressedStationEventRepository;
        this.duplicateTrainEventRepository = duplicateTrainEventRepository;
        this.trainDepartureRepository = trainDepartureRepository;
        this.twitterBotService = twitterBotService;
        this.destinationCodeMappingRepository = destinationCodeMappingRepository;
        this.trackCircuitService = trackCircuitService;
        this.twitterService = twitterService;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @PostConstruct
    private void init() {
        logger.info("Initializing train service...");

        this.trainStatusesMap = new HashMap<>();
        this.stationTrainStatusesMap = new HashMap<>();
        this.betweenStationDelayStatuses = new HashMap<>();
        this.stationCodesSet = buildStationCodesSet();
        this.lastStationDepartureMap = new ConcurrentHashMap<>();
        this.lastStationArrivalMap = new ConcurrentHashMap<>();
        this.lastStationToStationTripTimeMap = new ConcurrentHashMap<>();
        this.lastStationToStationTimeAtStationMap = new ConcurrentHashMap<>();
        this.lastStationToStationTripTimeCalendarMap = new ConcurrentHashMap<>();
        this.crowdingStatusByStation = new ConcurrentHashMap<>(StationUtil.getStationCodeMap().size());
        this.lastUpdatedTimestamp = null;
        this.trainDataOverLastHour = null;

        this.trackCircuitService.updateTrackCircuitLocationData();  // async; this will take a while...

        this.trackCircuitInfoMap = buildTrackCircuitInfoMap();
        this.trackCircuitMap = buildTrackCircuitMap();
        this.stationToStationCircuitsMap = buildStationToStationCircuitsMap();
        this.stationTrackCircuitMap = buildStationTrackCircuitMap(this.trackCircuitMap);
        this.buildStationScheduledTrainStatusesMap();   // buildStationScheduledTrainStatusesMap() is also automatically invoked at scheduled times of day
        this.buildStationToStationMaps(); // buildStationToStationMaps() is also automatically invoked on scheduled intervals and immediately after init() is done (it's called here for TrainService.update, which can be invoked *before* buildStationToStationMaps() after init() is done
        // buildTerminalStationTrackCircuitIdSet() is invoked once at the end of buildStationScheduledTrainStatusesMap() the first time it is executed
        this.lastTrainPositions = null;
        this.keptTrainIdByRemovedTrainId = HashBiMap.create();
        this.derivedLineCodeByDestinationId = new HashMap<>();

        logger.info("...train service initialized!");
    }

    public boolean isDataStale() {
        return (this.lastUpdatedTimestamp != null) && ((Instant.now().getEpochSecond() - this.lastUpdatedTimestamp.get()) > 30);
    }

    public List<TrainStatus> getTrainStatusesForTimestamp(long timestamp) {
        return trainStatusRepository.getByObservedDate(timestamp);
    }

    public Double getEstimatedTimeToStation(TrainStatus ts, String stationCode) {
        if (ts.getLocationCode() == null || stationCode == null || ts.getDestinationCode() == null || ts.getMinutesAway() == null) {
            return null;
        }

        int trackNumber = ts.getCurrentTrackCircuit().getTrackNumber();
        TrackCircuit fromCircuit = this.stationTrackCircuitMap.get(ts.getLocationCode() + "_" + trackNumber);
        TrackCircuit toCircuit = this.stationTrackCircuitMap.get(stationCode + "_" + trackNumber);
        TrackCircuit destinationCircuit = this.stationTrackCircuitMap.get(ts.getDestinationCode() + "_" + trackNumber);

        Collection<Set<String>> fromCircuitNextStationCodeSets;
        Collection<Set<String>> toCircuitNextStationCodeSets;
        if (ts.getDirectionNumber() == 1) {
            fromCircuitNextStationCodeSets = fromCircuit.getChildStationCodes().values();
            toCircuitNextStationCodeSets = toCircuit.getChildStationCodes().values();
        } else {
            fromCircuitNextStationCodeSets = fromCircuit.getParentStationCodes().values();
            toCircuitNextStationCodeSets = toCircuit.getParentStationCodes().values();
        }

        boolean fromCircuitConnectsToToCircuit = fromCircuit == toCircuit;
        if (!fromCircuitConnectsToToCircuit) {
            for (Set<String> stationCodes : fromCircuitNextStationCodeSets) {
                if (stationCodes.contains(stationCode)) {
                    fromCircuitConnectsToToCircuit = true;
                    break;
                }
            }
            if (!fromCircuitConnectsToToCircuit) {
                return null;
            }
        }

        boolean toCircuitConnectsToDestinationCircuit = toCircuit == destinationCircuit;
        if (!toCircuitConnectsToDestinationCircuit) {
            for (Set<String> stationCodes : toCircuitNextStationCodeSets) {
                if (stationCodes.contains(ts.getDestinationCode())) {
                    toCircuitConnectsToDestinationCircuit = true;
                    break;
                }
            }
            if (!toCircuitConnectsToDestinationCircuit) {
                return null;
            }
        }

        Double stationToStationTime = this.stationToStationDurationMap.get(ts.getLocationCode() + "_" + stationCode);
        if (stationToStationTime != null) {
            return ts.getMinutesAway() + stationToStationTime;
        } else {
            return ts.getMinutesAway();
        }
    }

    public Set<String> getStationCodes(String fromStationCode, String toStationCode) {
        if (fromStationCode.equals(toStationCode)) {
            return new HashSet<>(Collections.singletonList(fromStationCode));
        }

        Set<String> stationCodes = null;

        TrackCircuit fromCircuit = this.stationTrackCircuitMap.get(fromStationCode + "_1");  // HACK: it doesn't matter which track (1 or 2) we use here
        TrackCircuit toCircuit = this.stationTrackCircuitMap.get(toStationCode + "_1");  // HACK: it doesn't matter which track (1 or 2) we use here

        for (Set<String> fromCircuitChildStationCodes : fromCircuit.getChildStationCodes().values()) {
            for (Set<String> toCircuitParentStationCodes : toCircuit.getParentStationCodes().values()) {
                Set<String> intersection = new HashSet<>(fromCircuitChildStationCodes);
                intersection.retainAll(toCircuitParentStationCodes);
                if (intersection.size() > 0 || (fromCircuitChildStationCodes.contains(toStationCode) && toCircuitParentStationCodes.contains(fromStationCode))) {
                    stationCodes = new HashSet<>(intersection);

                    // make sure we include our 'from' and 'to' station codes
                    stationCodes.add(fromStationCode);
                    stationCodes.add(toStationCode);

                    // we assume there is only one valid path between the 'from' station and the 'to' station
                    break;
                }
            }
            if (stationCodes != null) {
                break;
            }
        }
        if (stationCodes == null) {
            for (Set<String> fromCircuitChildStationCodes : fromCircuit.getParentStationCodes().values()) {
                for (Set<String> toCircuitParentStationCodes : toCircuit.getChildStationCodes().values()) {
                    Set<String> intersection = new HashSet<>(fromCircuitChildStationCodes);
                    intersection.retainAll(toCircuitParentStationCodes);
                    if (intersection.size() > 0 || (fromCircuitChildStationCodes.contains(toStationCode) && toCircuitParentStationCodes.contains(fromStationCode))) {
                        stationCodes = new HashSet<>(intersection);

                        // make sure we include our 'from' and 'to' station codes
                        stationCodes.add(fromStationCode);
                        stationCodes.add(toStationCode);

                        // we assume there is only one valid path between the 'from' station and the 'to' station
                        break;
                    }
                }
                if (stationCodes != null) {
                    break;
                }
            }
        }

        return stationCodes;
    }

    // TODO: sort out a better strategy for fetching this data so that we can re-enable this someday
//    @Scheduled(fixedDelay = 60000)  // every minute
    private void fetchCrowdingStatuses() {
        if (this.configUtil.isDevelopmentMode()) {
            return;
        }

        logger.info("Updating station crowding statuses from Google...");

        int currentDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        boolean isRushHour = (currentDay == Calendar.MONDAY || currentDay == Calendar.TUESDAY || currentDay == Calendar.WEDNESDAY || currentDay == Calendar.THURSDAY || currentDay == Calendar.FRIDAY) && (StationUtil.isCurrentTimeBetween("05:00:00", "09:30:00") || StationUtil.isCurrentTimeBetween("15:00:00", "19:00:00"));
        if (isRushHour) {
            final String[] searchTerms = {"metro station", "wmata", "metro"};
            final UserAgent[] availableUserAgents = {UserAgent.CHROME, UserAgent.TOR};

            for (Map.Entry<String, String[]> entry : StationUtil.getStationCodeMap().entrySet()) {
                String stationCode = entry.getKey();
                String[] stationNames = entry.getValue();

                // try all the aliases we've got for this station at once
                List<String> query = new ArrayList<>();
                for (String stationName : stationNames) {
                    for (String searchTerm : searchTerms) {
                        try {
                            query.add(URLEncoder.encode("\"" + stationName + " " + searchTerm + "\" OR ", "UTF-8"));
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }
                }
                Collections.shuffle(query); // shuffle up each part of the query for some randomization
                String queryString = String.join("", query);
                queryString = queryString.substring(0, queryString.length() - 4); // get rid of tailing ' OR '

                String currentDirectoryPath = Paths.get(".").toAbsolutePath().normalize().toString() + "/JBrowserDriver_cache/";
                File cacheDirectory = new File(currentDirectoryPath);
                cacheDirectory.mkdirs();    // create the cache directory if it doesn't exist already

                String url = "https://www.google.com/#q=" + queryString;
                JBrowserDriver driver = new JBrowserDriver(
                        Settings.builder()
                                .timezone(Timezone.AMERICA_NEWYORK)
                                .userAgent(availableUserAgents[(int) Math.floor(Math.random() * availableUserAgents.length)])   // randomize user agent used
                                .cache(true)
                                .cacheDir(cacheDirectory)
                                .proxy(new ProxyConfig(ProxyConfig.Type.HTTP, "us-dc.proxymesh.com", 31280, "jpizzurro", "105417"))
                                .blockAds(true)
                                .quickRender(true)
                                .ajaxWait(10000)
                                .connectTimeout(10000)
                                .ajaxResourceTimeout(10000)
                                .connectionReqTimeout(10000)
                                .socketTimeout(10000)
                                .build()
                );
                driver.get(url);

                String prefix = "";
                String text = null;
                String postfix = "";

                if (driver.getStatusCode() == 200) {
                    try {
                        // attempt to fetch a real-time crowding status
                        WebElement webElement = driver.findElementByCssSelector("div._Eev > div > span:not([class])");

                        prefix = "currently ";
                        text = webElement.getText().trim().toLowerCase();
                    } catch (Exception ignored) {
                        // no real-time crowding status, so fall back to trying to fetch a "usual" one
                        try {
                            WebElement webElement = driver.findElementByCssSelector("div._XLv.lubh-sel div._Eev > div > span.eldaeC0zR5P__bs");

                            text = webElement.getText().trim().toLowerCase();
                            postfix = " around this time";
                        } catch (Exception ignored2) {
                            // no "usual" crowding status either
                        }
                    }
                } else {
                    logger.warn("Attempting to scrape " + url + " resulted in a " + driver.getStatusCode() + " status code!");
                }

                driver.quit();

                if (!StringUtils.isEmpty(text)) {
                    String status = prefix + text + postfix;
                    this.crowdingStatusByStation.put(stationCode, status);
                } else {
                    // failed to fetch any crowding status for this station
                    this.crowdingStatusByStation.remove(stationCode);
                }
            }
        } else {
            this.crowdingStatusByStation.clear();
        }

        logger.info("...successfully updated station crowding statuses from Google!");
    }

    @Scheduled(fixedDelay = 2000)
    private void update() {
        logger.info("Updating train data from WMATA...");

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setAccept(Collections.singletonList(new MediaType("application", "json")));
        requestHeaders.set("api_key", configUtil.getWmataApiKey());
        requestHeaders.set(HttpHeaders.ACCEPT_ENCODING, "gzip");
        HttpEntity<String> requestEntity = new HttpEntity<>("parameters", requestHeaders);
        RestTemplate restTemplate;
        ResponseEntity<TrainPositions> response;
        try {
            restTemplate = (new RequestHandler()).getRestTemplate();
            restTemplate.getMessageConverters().clear();
            restTemplate.getMessageConverters().add(new GzipGsonHttpMessageConverter());
            response = restTemplate.exchange(
                    configUtil.getWmataTrainPositionsApiUrl(),
                    HttpMethod.GET, requestEntity, TrainPositions.class);
        } catch (RestClientException e) {
            restTemplate = (new RequestHandler()).getRestTemplate();
            restTemplate.getMessageConverters().clear();
            restTemplate.getMessageConverters().add(new GsonHttpMessageConverter());
            requestEntity = new HttpEntity<>("parameters", new HttpHeaders());
            response = restTemplate.exchange(
                    configUtil.getWmataTrainPositionsApiUrl(),
                    HttpMethod.GET, requestEntity, TrainPositions.class);
            logger.warn("Response from WMATA Train Positions API not gzipped!");
        }

        Calendar now = Calendar.getInstance();
        Date observedDate = now.getTime();

        if (response == null || response.getBody() == null ||
                response.getBody().getTrainPositions() == null || response.getBody().getTrainPositions().size() <= 0 ||
                (this.lastTrainPositions != null && this.lastTrainPositions.equals(response.getBody()))) {
            // no data, or data is the same as when we last queried WMATA, so there's minimal work to be done

            boolean isDataStale = isDataStale();
            if (isDataStale) {
                this.twitterBotService.setIsDataStale(isDataStale);
            }

            for (TrainStatus trainStatus : this.trainStatusesMap.values()) {
                // update tags
                trainStatus.setNumPositiveTags(Math.toIntExact(this.trainTaggingService.getNumPositiveTags(trainStatus.getTrainId())));
                trainStatus.setNumNegativeTags(Math.toIntExact(this.trainTaggingService.getNumNegativeTags(trainStatus.getTrainId())));

                if (isDataStale) {
                    // reset "stopwatch" on any trip durations; the data is now invalid
                    trainStatus.setLastVisitedStation(null);
                    trainStatus.setLastVisitedStationCode(null);
                    trainStatus.setLastMovedCircuits(null);
                    trainStatus.setTrainSpeed(null);
                    trainStatus.setSecondsAtLastVisitedStation(null);
                    trainStatus.setSecondsDelayed(0);
                    trainStatus.setTrackNumberAtLastVisitedStation(null);
                    trainStatus.setDirectionNumberAtLastVisitedStation(null);
                    trainStatus.setLineCodeAtLastVisitedStation(null);
                    trainStatus.setDestinationCodeAtLastVisitedStation(null);
                }
            }
            for (List<TrainStatus> trainStatusesForStation : this.stationTrainStatusesMap.values()) {
                for (TrainStatus trainStatusForStation : trainStatusesForStation) {
                    // update tags
                    trainStatusForStation.setNumPositiveTags(Math.toIntExact(this.trainTaggingService.getNumPositiveTags(trainStatusForStation.getTrainId())));
                    trainStatusForStation.setNumNegativeTags(Math.toIntExact(this.trainTaggingService.getNumNegativeTags(trainStatusForStation.getTrainId())));

                    if (isDataStale) {
                        // reset "stopwatch" on any trip durations; the data is now invalid
                        trainStatusForStation.setLastVisitedStation(null);
                        trainStatusForStation.setLastVisitedStationCode(null);
                        trainStatusForStation.setLastMovedCircuits(null);
                        trainStatusForStation.setTrainSpeed(null);
                        trainStatusForStation.setSecondsAtLastVisitedStation(null);
                        trainStatusForStation.setSecondsDelayed(0);
                        trainStatusForStation.setTrackNumberAtLastVisitedStation(null);
                        trainStatusForStation.setDirectionNumberAtLastVisitedStation(null);
                        trainStatusForStation.setLineCodeAtLastVisitedStation(null);
                        trainStatusForStation.setDestinationCodeAtLastVisitedStation(null);
                    }
                }
            }

            logger.info("No train data, or all train data is the same as when we last queried WMATA's API.");
            return;
        }

        this.lastTrainPositions = response.getBody();
        this.twitterBotService.setIsDataStale(false);

        Map<String, ProcessedGISTrainData> processedGISTrainDataMap = new HashMap<>();

        try {
            HttpHeaders gisRequestHeaders = new HttpHeaders();
            gisRequestHeaders.set(HttpHeaders.ACCEPT_ENCODING, "gzip");
            HttpEntity<String> gisRequestEntity = new HttpEntity<>("parameters", gisRequestHeaders);
            RestTemplate gisRestTemplate;
            ResponseEntity<GISTrainsData> gisResponse;
            try {
                gisRestTemplate = (new RequestHandler()).getRestTemplate();
                gisRestTemplate.getMessageConverters().clear();
                gisRestTemplate.getMessageConverters().add(new GzipGsonHttpMessageConverter());
                gisResponse = gisRestTemplate.exchange(
                        "https://gisservices.wmata.com/gisservices/rest/services/Public/TRAIN_LOC_WMS_PUB/MapServer/0/query?f=json&where=ITT is not null&returnGeometry=true&outFields=*",
                        HttpMethod.GET, gisRequestEntity, GISTrainsData.class);
            } catch (Exception e) {
                gisRestTemplate = (new RequestHandler()).getRestTemplate();
                gisRestTemplate.getMessageConverters().clear();
                gisRestTemplate.getMessageConverters().add(new GsonHttpMessageConverter());
                gisRequestEntity = new HttpEntity<>("parameters", new HttpHeaders());
                gisResponse = gisRestTemplate.exchange(
                        "https://gisservices.wmata.com/gisservices/rest/services/Public/TRAIN_LOC_WMS_PUB/MapServer/0/query?f=json&where=ITT is not null&returnGeometry=true&outFields=*",
                        HttpMethod.GET, gisRequestEntity, GISTrainsData.class);
                logger.warn("Response from WMATA GIS TRAIN_LOC_WMS_PUB service not gzipped!");
            }
            if (gisResponse != null && gisResponse.getBody() != null && gisResponse.getBody().getTrainsData() != null) {
                for (GISTrainData trainData : gisResponse.getBody().getTrainsData()) {
                    ProcessedGISTrainData processedGISTrainData = new ProcessedGISTrainData(trainData);
                    processedGISTrainDataMap.put(processedGISTrainData.getId(), processedGISTrainData);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch data from WMATA GIS TRAIN_LOC_WMS_PUB service!", e);
        }

        List<TrainOffload> trainOffloads = new ArrayList<>();
        List<TrainDisappearance> trainDisappearances = new ArrayList<>();
        List<TrainDeparture> observedTrainDepartures = new ArrayList<>();
        List<TrainExpressedStationEvent> trainExpressedStationEvents = new ArrayList<>();
        List<DuplicateTrainEvent> duplicateTrainEvents = new ArrayList<>();

        List<StationToStationTrip> stationToStationTrips = new ArrayList<>();
        Map<String, TrainStatus> trainStatusesMap = new HashMap<>();
        for (TrainPosition trainPosition : response.getBody().getTrainPositions()) {
            if (trainPosition.getCarCount() == 0 && trainPosition.getServiceType().equals("Unknown")) {
                // filter out what we believe to be invalid data
                continue;
            }

            TrackCircuit trackCircuit = this.trackCircuitMap.get(trainPosition.getCircuitId());
            TrainStatus previousTrainStatus = this.trainStatusesMap.get(trainPosition.getTrainId());
            ProcessedGISTrainData extraTrainData = processedGISTrainDataMap.get(trainPosition.getTrainId());
            if (extraTrainData != null) {
                Calendar extraTrainDataObservedDate = extraTrainData.getObservedDate();
                if ((extraTrainDataObservedDate == null) || ((now.getTimeInMillis() - extraTrainDataObservedDate.getTimeInMillis()) > TimeUnit.SECONDS.toMillis(30))) {
                    // WMATA GIS data for this train is stale
                    extraTrainData = null;
                }
            }

            if (trackCircuit == null) {
                // train is probably not on revenue track

                if (previousTrainStatus != null) {
                    previousTrainStatus.setId(null);

                    TrackCircuitLocationData trackCircuitLocationData = (this.trackCircuitService.getTrackCircuitLocationDataByTrackCircuitId() != null) ? this.trackCircuitService.getTrackCircuitLocationDataByTrackCircuitId().get(trainPosition.getCircuitId()) : null;

                    if (extraTrainData != null) {
                        previousTrainStatus.setDestinationId(extraTrainData.getDestinationId());
                        previousTrainStatus.setAreDoorsOpenOnLeft(extraTrainData.areDoorsOpenOnLeft());
                        previousTrainStatus.setAreDoorsOpenOnRight(extraTrainData.areDoorsOpenOnRight());
                        previousTrainStatus.setAdjustingOnPlatform(extraTrainData.isAdjustingOnPlatform());
                        previousTrainStatus.setAreDoorsOperatingManually(extraTrainData.areDoorsOperatingManually());
                    } else {
                        previousTrainStatus.setDestinationId(null);
                        previousTrainStatus.setAreDoorsOpenOnLeft(null);
                        previousTrainStatus.setAreDoorsOpenOnRight(null);
                        previousTrainStatus.setAdjustingOnPlatform(null);
                        previousTrainStatus.setAreDoorsOperatingManually(null);
                    }

                    Double lat = null;
                    Double lon = null;
                    if (trackCircuitLocationData != null) {
                        lat = trackCircuitLocationData.getLat();
                        lon = trackCircuitLocationData.getLon();
                    }
                    if ((lat == null || lon == null) && extraTrainData != null) {
                        // fall back to using data from GIS feed
                        lat = extraTrainData.getLat();
                        lon = extraTrainData.getLon();
                    }
                    previousTrainStatus.setLat(lat);
                    previousTrainStatus.setLon(lon);

                    Integer direction = null;
                    if (trackCircuitLocationData != null) {
                        // calculate direction based on location of current and next track circuit
                        TrackCircuit currentTrackCircuit = previousTrainStatus.getCurrentTrackCircuit();
                        if (currentTrackCircuit != null) {
                            TrackCircuit nextTrackCircuit;
                            if (previousTrainStatus.getDirectionNumber() == 1) {
                                nextTrackCircuit = (currentTrackCircuit.getChildNeighbors().size() > 0) ? currentTrackCircuit.getChildNeighbors().iterator().next() : null;
                            } else {
                                nextTrackCircuit = (currentTrackCircuit.getParentNeighbors().size() > 0) ? currentTrackCircuit.getParentNeighbors().iterator().next() : null;
                            }
                            if (nextTrackCircuit != null) {
                                TrackCircuitLocationData nextTrackCircuitLocationData = (this.trackCircuitService.getTrackCircuitLocationDataByTrackCircuitId() != null) ? this.trackCircuitService.getTrackCircuitLocationDataByTrackCircuitId().get(nextTrackCircuit.getId()) : null;
                                if (nextTrackCircuitLocationData != null) {
                                    direction = this.trackCircuitService.calculateDirection(trackCircuitLocationData, nextTrackCircuitLocationData);
                                }
                            }
                        }
                    }
                    if (direction == null && extraTrainData != null) {
                        // fall back to using data from GIS feed
                        direction = extraTrainData.getDirection();
                    }
                    previousTrainStatus.setDirection(direction);

                    previousTrainStatus.setNumPositiveTags(Math.toIntExact(this.trainTaggingService.getNumPositiveTags(previousTrainStatus.getTrainId())));
                    previousTrainStatus.setNumNegativeTags(Math.toIntExact(this.trainTaggingService.getNumNegativeTags(previousTrainStatus.getTrainId())));

                    previousTrainStatus.setNotOnRevenueTrack(true);
                    previousTrainStatus.setTrainSpeed(null);
                    previousTrainStatus.setCircuitName((this.trackCircuitInfoMap.get(trainPosition.getCircuitId()) != null) ? this.trackCircuitInfoMap.get(trainPosition.getCircuitId()).getTrackId() : null);
                    previousTrainStatus.setSecondsSinceLastMoved(trainPosition.getSecondsAtLocation());
                    previousTrainStatus.setIsCurrentlyHoldingOrSlow(previousTrainStatus.getSecondsSinceLastMoved() > previousTrainStatus.getSecondsUntilDelayed());
                    previousTrainStatus.setWasKeyedDown(previousTrainStatus.isKeyedDown() || previousTrainStatus.wasKeyedDown());
                    previousTrainStatus.setKeyedDown(trainPosition.getSecondsAtLocation() >= TimeUnit.MINUTES.toSeconds(30));
                    previousTrainStatus.setDirectionNumber(trainPosition.getDirectionNum());
                    previousTrainStatus.setGroup(previousTrainStatus.getDirectionNumber().toString());
                    previousTrainStatus.setRealTrainId(trainPosition.getTrainNumber());

                    Date lastObservedDate = previousTrainStatus.getObservedDate();
                    previousTrainStatus.setObservedDate(observedDate);

                    // derive number of train cars
                    String numCars;
                    if (trainPosition.getCarCount() == 2) { // there is apparently a glitch where some 8 car trains are reported as 2...
                        numCars = "8";
                    } else if (trainPosition.getCarCount() == 4) {  // ...and another where 4 really means 6
                        numCars = "6";
                    } else if (trainPosition.getCarCount() == 0) {
                        numCars = "N/A";
                    } else {
                        numCars = trainPosition.getCarCount().toString();
                    }
                    if (!"N/A".equals(numCars)) {
                        previousTrainStatus.setCar(numCars);
                    }

                    // derive previous and next/current station codes
                    // derive ETA to next station and other related information
                    // (we only override this stuff when necessary, otherwise we implicitly use whatever data we had last tick or when this train was last on revenue track)

                    boolean wasAtStation = previousTrainStatus.getRawTrackCircuitId().equals(1331 /* C10 */) || previousTrainStatus.getRawTrackCircuitId().equals(3077 /* K06 */) || previousTrainStatus.getRawTrackCircuitId().equals(3078 /* K06 */);
                    boolean isAtStation = trainPosition.getCircuitId().equals(1331 /* C10 */) || trainPosition.getCircuitId().equals(3077 /* K06 */) || trainPosition.getCircuitId().equals(3078 /* K06 */);

                    if (isAtStation) {
                        String trainAtStation = trainPosition.getCircuitId().equals(1331 /* C10 */) ? "C10" : "K06";

                        previousTrainStatus.setCurrentStationCode(trainAtStation);
                        previousTrainStatus.setCurrentStationName(StationUtil.getStationName(previousTrainStatus.getCurrentStationCode()));
                        previousTrainStatus.setLocationCode(previousTrainStatus.getCurrentStationCode());
                        previousTrainStatus.setLocationName(previousTrainStatus.getCurrentStationName());

                        String previousStationCode = null;
                        if (trainPosition.getCircuitId().equals(1331 /* C10 */)) {
                            if (previousTrainStatus.getDirectionNumber().equals(1)) {
                                previousStationCode = "C12";
                            } else if (previousTrainStatus.getDirectionNumber().equals(2)) {
                                previousStationCode = "C09";
                            }
                        } else {
                            if (previousTrainStatus.getDirectionNumber().equals(1)) {
                                previousStationCode = "K07";
                            } else if (previousTrainStatus.getDirectionNumber().equals(2)) {
                                previousStationCode = "K05";
                            }
                        }
                        previousTrainStatus.setPreviousStationCode(previousStationCode);
                        previousTrainStatus.setPreviousStationName(StationUtil.getStationName(previousTrainStatus.getPreviousStationCode()));

                        // derive ETA to next station and other related information
                        Double maxEta = this.stationToStationDurationMap.get(previousTrainStatus.getPreviousStationCode() + "_" + previousTrainStatus.getLocationCode());
                        if (maxEta != null) {
                            maxEta -= 0.5d;
                        }

                        previousTrainStatus.setMin("BRD");
                        previousTrainStatus.setMinutesAway(0d);
                        previousTrainStatus.setEstimatedMinutesAway(0d);
                        previousTrainStatus.setMaxMinutesAway(maxEta);
                        previousTrainStatus.setDistanceFromNextStation(0);
                    } else {
                        if (wasAtStation) {
                            // not only is this train not at a station, it has just departed one

                            String locationCode = null;
                            if (previousTrainStatus.getRawTrackCircuitId().equals(1331 /* C10 */)) {
                                if (previousTrainStatus.getDirectionNumber().equals(1)) {
                                    locationCode = "C09";
                                } else if (previousTrainStatus.getDirectionNumber().equals(2)) {
                                    locationCode = "C12";
                                }
                            } else {
                                if (previousTrainStatus.getDirectionNumber().equals(1)) {
                                    locationCode = "K05";
                                } else if (previousTrainStatus.getDirectionNumber().equals(2)) {
                                    locationCode = "K07";
                                }
                            }
                            previousTrainStatus.setCurrentStationCode(locationCode);
                            previousTrainStatus.setCurrentStationName(StationUtil.getStationName(previousTrainStatus.getCurrentStationCode()));
                            previousTrainStatus.setLocationCode(previousTrainStatus.getCurrentStationCode());
                            previousTrainStatus.setLocationName(previousTrainStatus.getCurrentStationName());

                            previousTrainStatus.setPreviousStationCode(previousTrainStatus.getRawTrackCircuitId().equals(1331 /* C10 */) ? "C10" : "K06");
                            previousTrainStatus.setPreviousStationName(StationUtil.getStationName(previousTrainStatus.getPreviousStationCode()));

                            // derive ETA to next station and other related information
                            Double maxEta = this.stationToStationDurationMap.get(previousTrainStatus.getPreviousStationCode() + "_" + previousTrainStatus.getLocationCode());
                            if (maxEta != null) {
                                maxEta -= 0.5d;
                            }

                            String status;
                            long roundedEta = Math.round(maxEta);
                            if (maxEta == 0) {
                                status = "BRD";
                            } else if (roundedEta <= 0) {
                                status = "ARR";
                            } else {
                                status = String.valueOf(roundedEta);
                            }

                            previousTrainStatus.setMin(status);
                            previousTrainStatus.setMinutesAway(maxEta);
                            previousTrainStatus.setMaxMinutesAway(maxEta);
                            previousTrainStatus.setDistanceFromNextStation(null);

                            previousTrainStatus.setEstimatedMinutesAway(getPredictedRideTime(now, previousTrainStatus.getPreviousStationCode(), previousTrainStatus.getLocationCode(), previousTrainStatus));
                        }
                    }

                    // derive line code and destination station code

                    String lineCode;
                    if (trainPosition.getServiceType().equals("NoPassengers")) {
                        lineCode = "N/A";
                    } else {
                        lineCode = StationUtil.getLineCodeFromRealTrainId(previousTrainStatus.getRealTrainId());
                        if (lineCode == null) {
                            lineCode = (trainPosition.getLineCode() != null) ? trainPosition.getLineCode() : "N/A";
                        }
                    }
                    String destinationStationCode = trainPosition.getDestinationStationCode();

                    if (!trainPosition.getServiceType().equals("NoPassengers") && previousTrainStatus.getDestinationId() != null && previousTrainStatus.getDirectionNumber() != null) {
                        // WMATA isn't supplying us a line code for this train, but it should have one because it's not a No Passenger train, so try to fetch an assignment from our destination code mapping table
                        DestinationCodeMapping destinationCodeMapping = this.destinationCodeMap.get(new DestinationCodeMappingPrimaryKey(previousTrainStatus.getDestinationId(), previousTrainStatus.getDirectionNumber()));
                        if (destinationCodeMapping != null) {
                            if (destinationCodeMapping.getLineCode() != null) {
                                lineCode = destinationCodeMapping.getLineCode();
                            }
                            if (destinationCodeMapping.getDestinationStationCode() != null) {
                                destinationStationCode = destinationCodeMapping.getDestinationStationCode();
                            }
                        }
                    }

                    // check to make sure the destination station code makes sense given the train's current configuration
                    // if it doesn't, overwrite it if possible using train schedule data
                    if (!StringUtils.isEmpty(destinationStationCode) && !StringUtils.isEmpty(lineCode) && this.gtfsService.getScheduledDestinationStationCodesByLineAndDirection() != null) {
                        // use whatever revenue track circuit we have from last time we were on revenue track
                        TrackCircuit standInTrackCircuit = previousTrainStatus.getCurrentTrackCircuit();
                        if (standInTrackCircuit != null) {
                            Set<String> possibleDestinationCodes = null;
                            if (previousTrainStatus.getDirectionNumber() == 1) {
                                possibleDestinationCodes = new HashSet<>();
                                Collection<Set<String>> possibleDestinationStationCodeSets = standInTrackCircuit.getChildStationCodes().values();
                                for (Set<String> possibleDestinationStationCodeSet : possibleDestinationStationCodeSets) {
                                    possibleDestinationCodes.addAll(possibleDestinationStationCodeSet);
                                }
                            } else if (previousTrainStatus.getDirectionNumber() == 2) {
                                possibleDestinationCodes = new HashSet<>();
                                Collection<Set<String>> possibleDestinationStationCodeSets = standInTrackCircuit.getParentStationCodes().values();
                                for (Set<String> possibleDestinationStationCodeSet : possibleDestinationStationCodeSets) {
                                    possibleDestinationCodes.addAll(possibleDestinationStationCodeSet);
                                }
                            }
                            possibleDestinationCodes.add(previousTrainStatus.getLocationCode());
                            if (possibleDestinationCodes != null && !possibleDestinationCodes.contains(destinationStationCode)) {
                                // train appears to have an invalid destination code given its current direction
                                // let's try assigning it a different one that's more appropriate

                                Double shortestTripDuration = null;
                                String closestDestinationCode = null;

                                Set<String> destinationCodes = this.gtfsService.getScheduledDestinationStationCodesByLineAndDirection().get(String.join("_", lineCode, previousTrainStatus.getDirectionNumber().toString()));
                                if (destinationCodes != null) {
                                    for (String destinationCode : destinationCodes) {
                                        if (possibleDestinationCodes.contains(destinationCode)) {
                                            if (destinationCode.equals(previousTrainStatus.getLocationCode())) {
                                                closestDestinationCode = destinationCode;
                                                break;
                                            } else {
                                                Double tripDuration = this.stationToStationDurationMap.get(previousTrainStatus.getLocationCode() + "_" + destinationCode);
                                                if (tripDuration != null) {
                                                    if (shortestTripDuration == null || tripDuration < shortestTripDuration) {
                                                        shortestTripDuration = tripDuration;
                                                        closestDestinationCode = destinationCode;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (closestDestinationCode != null) {
                                    destinationStationCode = closestDestinationCode;
                                }
                            }
                        }
                    }

                    if (destinationStationCode == null && extraTrainData != null && extraTrainData.getDestinationStationCode() != null) {
                        destinationStationCode = extraTrainData.getDestinationStationCode();
                    }

                    // consider it a new trip if a train goes from non-revenue to revenue
                    if (previousTrainStatus.getLine().equals("N/A") && !lineCode.equals("N/A")) {
                        previousTrainStatus.setTripId(UUID.randomUUID());
                        previousTrainStatus.setSecondsDelayed(0);
                        previousTrainStatus.setSecondsOffSchedule(0);
                    }

                    previousTrainStatus.setLine(lineCode);
                    previousTrainStatus.setDestinationCode(destinationStationCode);

                    // derive destination station name
                    String destinationName;
                    if (lineCode.equals("N/A")) {
                        destinationName = "No Passenger";
                    } else if (previousTrainStatus.getDestinationCode() != null) {
                        destinationName = StationUtil.getStationName(previousTrainStatus.getDestinationCode());
                    } else {
                        destinationName = "N/A";
                    }
                    previousTrainStatus.setDestination(destinationName);
                    previousTrainStatus.setDestinationName(destinationName);
                    previousTrainStatus.setDestinationStationAbbreviation(StationUtil.getStationAbbreviation(previousTrainStatus.getDestinationCode()));

                    if (isAtStation) {
                        String trainAtStation = trainPosition.getCircuitId().equals(1331 /* C10 */) ? "C10" : "K06";

                        if (wasAtStation) {
                            // this train is still at the station it was last observed at

                            if (previousTrainStatus.getLastVisitedStation() != null) {
                                int additionalSecondsAtStation = (int) ((previousTrainStatus.getObservedDate().getTime() - previousTrainStatus.getLastVisitedStation().getTime()) / 1000d); // milliseconds => seconds;
                                previousTrainStatus.setSecondsAtLastVisitedStation((previousTrainStatus.getSecondsAtLastVisitedStation() != null) ? (previousTrainStatus.getSecondsAtLastVisitedStation() + additionalSecondsAtStation) : additionalSecondsAtStation);
                            }
                        } else {
                            // this train has just arrived at a station it wasn't last observed at

                            Calendar arrivingTime = Calendar.getInstance();
                            arrivingTime.setTime(previousTrainStatus.getObservedDate());

                            if (!"N/A".equals(previousTrainStatus.getLine()) && previousTrainStatus.getDirectionNumber() != null) {
                                String key = String.join("_", trainAtStation, previousTrainStatus.getLine(), previousTrainStatus.getDirectionNumber().toString());
                                ArrivalInfo lastArrival = this.lastStationArrivalMap.get(key);
                                if (lastArrival != null) {
                                    if (!lastArrival.getTrainId().equals(previousTrainStatus.getTrainId()) || !lastArrival.getDirectionNumber().equals(previousTrainStatus.getDirectionNumber())) {
                                        Double observedWaitingTime = ((arrivingTime.getTimeInMillis() - lastArrival.getArrivalTime().getTimeInMillis()) / 1000d) / 60d;   // milliseconds -> minutes
                                        lastArrival.setArrivalTime(arrivingTime);
                                        lastArrival.setTimeSinceLastArrival(observedWaitingTime);
                                        lastArrival.setTrainId(previousTrainStatus.getTrainId());
                                    }
                                } else {
                                    this.lastStationArrivalMap.put(key, new ArrivalInfo(arrivingTime, previousTrainStatus.getTrainId(), previousTrainStatus.getDirectionNumber()));
                                }

                                this.gtfsService.setAdherenceToSchedule(previousTrainStatus.getLine(), previousTrainStatus.getDirectionNumber(), trainAtStation, arrivingTime);
                            }

                            if (previousTrainStatus.getLastVisitedStationCode() != null && previousTrainStatus.getLastVisitedStation() != null) {
                                String stationCodesKey = previousTrainStatus.getLastVisitedStationCode() + "_" + trainAtStation;

                                // update track delays
                                Double expectedTripDuration = this.stationToStationMedianDurationMap.get(stationCodesKey);
                                if (expectedTripDuration != null) {
                                    expectedTripDuration += 0.5d;   // 30 seconds from duration map + 30 additional seconds = 60-second expected boarding time
                                    TrackCircuit stationCircuitByTrack = this.stationTrackCircuitMap.get(previousTrainStatus.getLastVisitedStationCode() + "_" + previousTrainStatus.getTrackNumber());
                                    if (stationCircuitByTrack != null) {
                                        double observedTripDuration = ((previousTrainStatus.getObservedDate().getTime() - previousTrainStatus.getLastVisitedStation().getTime()) / 1000d) / 60d;    // milliseconds => minutes
                                        observedTripDuration += (!this.terminalStationScheduledTrainStatusesMap.keySet().contains(previousTrainStatus.getLastVisitedStationCode()) && previousTrainStatus.getSecondsAtLastVisitedStation() != null) ? (previousTrainStatus.getSecondsAtLastVisitedStation() / 60d /* seconds => minutes */) : 0;

                                        int extraTripDuration = (int) Math.max(Math.round((observedTripDuration - expectedTripDuration) * 60d /* minutes => seconds */), 0);
                                        stationCircuitByTrack.setNumSecondsEstimatedTrackDelays(extraTripDuration);
                                    }
                                }

                                // log the trip

                                String lineCodeString = !"N/A".equals(previousTrainStatus.getLine()) ? previousTrainStatus.getLine() : null;
                                String destinationCodeString = !"N/A".equals(previousTrainStatus.getDestinationCode()) ? previousTrainStatus.getDestinationCode() : null;

                                Integer numCarsInt;
                                try {
                                    numCarsInt = Integer.parseInt(previousTrainStatus.getCar());
                                } catch (NumberFormatException e) {
                                    numCarsInt = null;
                                }

                                String departingStationCode = previousTrainStatus.getLastVisitedStationCode();

                                Calendar departingTime = Calendar.getInstance();
                                departingTime.setTime(previousTrainStatus.getLastVisitedStation());

                                String arrivingStationCode = trainAtStation;

                                double tripDuration = ((previousTrainStatus.getObservedDate().getTime() - previousTrainStatus.getLastVisitedStation().getTime()) / 1000d) / 60d;    // milliseconds => minutes

                                StationToStationTrip trip = new StationToStationTrip();
                                trip.setTrainId(previousTrainStatus.getTrainId());
                                trip.setRealTrainId(previousTrainStatus.getRealTrainId());
                                trip.setLineCode(lineCodeString);
                                trip.setDestinationStationCode(destinationCodeString);
                                trip.setNumCars(numCarsInt);
                                trip.setDepartingStationCode(departingStationCode);
                                trip.setDepartingTime(departingTime);
                                trip.setArrivingStationCode(arrivingStationCode);
                                trip.setArrivingTrackNumber(3);
                                trip.setArrivingTime(arrivingTime);
                                trip.setTripDuration(tripDuration);
                                trip.setSecondsAtDepartingStation(previousTrainStatus.getSecondsAtLastVisitedStation());
                                trip.setArrivingDirectionNumber(previousTrainStatus.getDirectionNumber());
                                trip.setTripId(previousTrainStatus.getTripId());
                                stationToStationTrips.add(trip);

                                if (!previousTrainStatus.isKeyedDown() && !previousTrainStatus.wasKeyedDown()) {
                                    String key = String.join("_", departingStationCode, arrivingStationCode);
                                    this.lastStationToStationTripTimeMap.put(key, tripDuration);
                                    this.lastStationToStationTimeAtStationMap.put(key, (previousTrainStatus.getSecondsAtLastVisitedStation() != null) ? (previousTrainStatus.getSecondsAtLastVisitedStation() / 60d /* seconds => minutes */) : 0d);
                                    this.lastStationToStationTripTimeCalendarMap.put(key, arrivingTime);
                                }
                            }

                            previousTrainStatus.setKeyedDown(false);
                            previousTrainStatus.setWasKeyedDown(false);
                            previousTrainStatus.setSecondsAtLastVisitedStation((int) TimeUnit.MILLISECONDS.toSeconds(previousTrainStatus.getObservedDate().getTime() - lastObservedDate.getTime()));    // worst case, this train has been at this station since immediately after the last time we observed it
                            previousTrainStatus.setSecondsDelayed(0);
                        }

                        if (previousTrainStatus.getDirectionNumberAtLastVisitedStation() != null && !previousTrainStatus.getDirectionNumber().equals(previousTrainStatus.getDirectionNumberAtLastVisitedStation())) {
                            // this train is going in a different direction since we last observed it at its last visited station (note: which could be this station)
                            // so, consider this a new trip and reset any train delays
                            previousTrainStatus.setTripId(UUID.randomUUID());
                            previousTrainStatus.setSecondsDelayed(0);
                            previousTrainStatus.setSecondsOffSchedule(0);
                        }

                        previousTrainStatus.setLastVisitedStation(previousTrainStatus.getObservedDate());
                        previousTrainStatus.setLastVisitedStationCode(trainAtStation);
                        previousTrainStatus.setTrackNumberAtLastVisitedStation(previousTrainStatus.getTrackNumber());
                        previousTrainStatus.setDirectionNumberAtLastVisitedStation(previousTrainStatus.getDirectionNumber());
                        previousTrainStatus.setLineCodeAtLastVisitedStation(previousTrainStatus.getLine());
                        previousTrainStatus.setDestinationCodeAtLastVisitedStation(previousTrainStatus.getDestinationCode());
                    } else {
                        if (wasAtStation) {
                            // not only is this train not at a station, it has just departed one
                            // log this station departure accordingly

                            if (previousTrainStatus.getLastVisitedStationCode() != null) {
                                Integer numCarsInteger = null;
                                try {
                                    numCarsInteger = Integer.parseInt(previousTrainStatus.getCar());
                                } catch (NumberFormatException ignored) {
                                }

                                String key = String.join("_", previousTrainStatus.getLastVisitedStationCode(), previousTrainStatus.getLine(), previousTrainStatus.getDestinationCode());
                                DepartureInfo lastDeparture = this.lastStationDepartureMap.get(key);

                                Calendar departureTime = Calendar.getInstance();
                                departureTime.setTime(previousTrainStatus.getObservedDate());

                                if (lastDeparture == null || (!lastDeparture.getTrainId().equals(previousTrainStatus.getTrainId()) || !lastDeparture.getDirectionNumber().equals(previousTrainStatus.getDirectionNumber()))) {
                                    observedTrainDepartures.add(new TrainDeparture(previousTrainStatus.getLine(), previousTrainStatus.getDirectionNumber(), previousTrainStatus.getLastVisitedStationCode(), StationUtil.getStationName(previousTrainStatus.getLastVisitedStationCode()), previousTrainStatus.getDestinationCode(), previousTrainStatus.getDestinationName(), departureTime, previousTrainStatus.getTrainId(), previousTrainStatus.getRealTrainId(), numCarsInteger));
                                }

                                if (previousTrainStatus.getLine() != null && previousTrainStatus.getDestinationCode() != null) {
                                    if (lastDeparture != null) {
                                        if (!lastDeparture.getTrainId().equals(previousTrainStatus.getTrainId()) || !lastDeparture.getDirectionNumber().equals(previousTrainStatus.getDirectionNumber())) {
                                            Double observedWaitingTime = ((departureTime.getTimeInMillis() - lastDeparture.getDepartureTime().getTimeInMillis()) / 1000d) / 60d;   // milliseconds -> minutes
                                            lastDeparture.setDepartureTime(departureTime);
                                            lastDeparture.setTimeSinceLastDeparture(observedWaitingTime);
                                            lastDeparture.setTrainId(previousTrainStatus.getTrainId());
                                        }
                                    } else {
                                        this.lastStationDepartureMap.put(key, new DepartureInfo(departureTime, previousTrainStatus.getTrainId(), previousTrainStatus.getDirectionNumber()));
                                    }
                                }
                            }
                        }
                    }

                    previousTrainStatus.setRawTrackCircuitId(trainPosition.getCircuitId());

                    trainStatusesMap.put(previousTrainStatus.getTrainId(), previousTrainStatus);
                }
                continue;
            } else {
                if (previousTrainStatus != null) {
                    previousTrainStatus.setNotOnRevenueTrack(false);
                }
            }

            // override tail track circuits at terminal stations as a kind of hack to always show trains on revenue track on line maps
            if ((trackCircuit.getChildNeighbors().isEmpty() && trackCircuit.getParentNeighbors().size() == 1) || (trackCircuit.getParentNeighbors().isEmpty() && trackCircuit.getChildNeighbors().size() == 1)) {
                if (trackCircuit.getParentNeighbors().size() == 1) {
                    trackCircuit = trackCircuit.getParentNeighbors().iterator().next();
                } else if (trackCircuit.getChildNeighbors().size() == 1) {
                    trackCircuit = trackCircuit.getChildNeighbors().iterator().next();
                }
            }

            TrainStatus trainStatus = new TrainStatus(trainPosition.getTrainId());
            trainStatus.setRecentTweets(this.twitterService.getTrainTwitterProblemMap().get(trainStatus.getTrainId()));
            trainStatus.setRealTrainId(trainPosition.getTrainNumber());

            TrackCircuitLocationData trackCircuitLocationData = (this.trackCircuitService.getTrackCircuitLocationDataByTrackCircuitId() != null) ? this.trackCircuitService.getTrackCircuitLocationDataByTrackCircuitId().get(trainPosition.getCircuitId()) : null;

            if (extraTrainData != null) {
                trainStatus.setDestinationId(extraTrainData.getDestinationId());
                trainStatus.setAreDoorsOpenOnLeft(extraTrainData.areDoorsOpenOnLeft());
                trainStatus.setAreDoorsOpenOnRight(extraTrainData.areDoorsOpenOnRight());
                trainStatus.setAdjustingOnPlatform(extraTrainData.isAdjustingOnPlatform());
                trainStatus.setAreDoorsOperatingManually(extraTrainData.areDoorsOperatingManually());
            } else {
                trainStatus.setDestinationId(null);
                trainStatus.setAreDoorsOpenOnLeft(null);
                trainStatus.setAreDoorsOpenOnRight(null);
                trainStatus.setAdjustingOnPlatform(null);
                trainStatus.setAreDoorsOperatingManually(null);
            }

            Double lat = null;
            Double lon = null;
            if (trackCircuitLocationData != null) {
                lat = trackCircuitLocationData.getLat();
                lon = trackCircuitLocationData.getLon();
            }
            if ((lat == null || lon == null) && extraTrainData != null) {
                // fall back to using data from GIS feed
                lat = extraTrainData.getLat();
                lon = extraTrainData.getLon();
            }
            trainStatus.setLat(lat);
            trainStatus.setLon(lon);

            Integer direction = null;
            if (trackCircuitLocationData != null) {
                // calculate direction based on location of current and next track circuit
                TrackCircuit currentTrackCircuit = trainStatus.getCurrentTrackCircuit();
                if (currentTrackCircuit != null) {
                    TrackCircuit nextTrackCircuit;
                    if (trainStatus.getDirectionNumber() == 1) {
                        nextTrackCircuit = (currentTrackCircuit.getChildNeighbors().size() > 0) ? currentTrackCircuit.getChildNeighbors().iterator().next() : null;
                    } else {
                        nextTrackCircuit = (currentTrackCircuit.getParentNeighbors().size() > 0) ? currentTrackCircuit.getParentNeighbors().iterator().next() : null;
                    }
                    if (nextTrackCircuit != null) {
                        TrackCircuitLocationData nextTrackCircuitLocationData = (this.trackCircuitService.getTrackCircuitLocationDataByTrackCircuitId() != null) ? this.trackCircuitService.getTrackCircuitLocationDataByTrackCircuitId().get(nextTrackCircuit.getId()) : null;
                        if (nextTrackCircuitLocationData != null) {
                            direction = this.trackCircuitService.calculateDirection(trackCircuitLocationData, nextTrackCircuitLocationData);
                        }
                    }
                }
            }
            if (direction == null && extraTrainData != null) {
                // fall back to using data from GIS feed
                direction = extraTrainData.getDirection();
            }
            trainStatus.setDirection(direction);

            trainStatus.setTrackCircuitId(trackCircuit.getId());
            trainStatus.setDirectionNumber(trainPosition.getDirectionNum());
            trainStatus.setCurrentTrackCircuit(trackCircuit);
            trainStatus.setObservedDate(observedDate);

            trainStatus.setLastMovedCircuits((previousTrainStatus != null) ? previousTrainStatus.getLastMovedCircuits() : null);
            trainStatus.setSecondsSinceLastMoved((previousTrainStatus != null && previousTrainStatus.getLastMovedCircuits() != null) ? (int) TimeUnit.MILLISECONDS.toSeconds(trainStatus.getObservedDate().getTime() - previousTrainStatus.getLastMovedCircuits().getTime()) : trainPosition.getSecondsAtLocation());
            trainStatus.setIsCurrentlyHoldingOrSlow(trainStatus.getSecondsSinceLastMoved() > trainStatus.getSecondsUntilDelayed());
            trainStatus.setTrainSpeed((previousTrainStatus != null && !trainStatus.isCurrentlyHoldingOrSlow() && !trainStatus.getCurrentTrackCircuit().isOrHasChildOrParentStationCircuit()) ? previousTrainStatus.getTrainSpeed() : null);

            trainStatusesMap.put(trainStatus.getTrainId(), trainStatus);

            // derive the direction this train is headed in (this is not always the same as the direction we get from WMATA) and some other stuff
            if (previousTrainStatus != null && previousTrainStatus.getTrackCircuitId() != trainStatus.getTrackCircuitId()) {
                boolean didMoveCircuits = false;

                if (trainStatus.getDirectionNumber().equals(previousTrainStatus.getDirectionNumber())) {
                    if (!trainStatus.getCurrentTrackCircuit().isOrHasChildOrParentStationCircuit() || !previousTrainStatus.getCurrentTrackCircuit().isOrHasChildOrParentStationCircuit()) {
                        didMoveCircuits = true;
                    }
                } else {
                    didMoveCircuits = true;
                }

                if (didMoveCircuits) {
                    // train moved from one circuit to another

                    trainStatus.setLastMovedCircuits(trainStatus.getObservedDate());
                    trainStatus.setSecondsSinceLastMoved(0);
                    trainStatus.setIsCurrentlyHoldingOrSlow(false);

                    // calculate estimated train speed
                    if (previousTrainStatus.getLastMovedCircuits() != null) {
                        double travelTime = ((trainStatus.getLastMovedCircuits().getTime() - previousTrainStatus.getLastMovedCircuits().getTime()) / 1000d) / 60d;   // milliseconds => minutes

                        Integer milesPerHour = null;
                        if (trainStatus.getCurrentTrackCircuit().getStationCode() == null && previousTrainStatus.getCurrentTrackCircuit().getStationCode() == null) {
                            TrackCircuit fromTrackCircuit = this.trackCircuitMap.get(previousTrainStatus.getTrackCircuitId());
                            TrackCircuit toTrackCircuit = this.trackCircuitMap.get(trainStatus.getTrackCircuitId());
                            Double minDistanceCovered = getMinDistanceCovered(fromTrackCircuit, toTrackCircuit);
                            if (minDistanceCovered != null) {
                                milesPerHour = (int) Math.round((minDistanceCovered /* feet */ * /* to miles */ 0.000189393939d) / (travelTime /* minutes */ * /* to hours */ 0.0166667d));
                                milesPerHour = (milesPerHour >= 0 && milesPerHour <= 75) ? milesPerHour : null; // final sanity check
                            }
                        }
                        trainStatus.setTrainSpeed(milesPerHour);
                    }
                }
            }

            // derive previous and next/current station codes
            String nextForwardStationCode;
            Set<String> nextForwardStationCodes;
            String nextBackwardStationCode;
            if (trainStatus.getDirectionNumber() == 1) {
                nextForwardStationCode = trackCircuit.getNextChildStationCode();
                nextForwardStationCodes = trackCircuit.getNextChildStationCodes();
                nextBackwardStationCode = trackCircuit.getNextParentStationCode();
            } else {
                // train is on the "wrong" side of the tracks, probably because of single-tracking or something
                nextForwardStationCode = trackCircuit.getNextParentStationCode();
                nextForwardStationCodes = trackCircuit.getNextParentStationCodes();
                nextBackwardStationCode = trackCircuit.getNextChildStationCode();
            }
            String locationStationCode = null;
            if (trackCircuit.getStationCode() != null) {
                locationStationCode = trackCircuit.getStationCode();
            } else if (TrackCircuit.isNextStationCodeValid(nextForwardStationCode)) {
                locationStationCode = nextForwardStationCode;
            } else if (TrackCircuit.isNextStationCodeIndeterminate(nextForwardStationCode)) {
                for (String stationCode : nextForwardStationCodes) {
                    Set<String> nextStationForwardStationCodes;
                    if (trainStatus.getDirectionNumber() == 1) {
                        nextStationForwardStationCodes = this.stationTrackCircuitMap.get(stationCode + "_" + trackCircuit.getTrackNumber()).findChildStationCodes();
                    } else {
                        nextStationForwardStationCodes = this.stationTrackCircuitMap.get(stationCode + "_" + trackCircuit.getTrackNumber()).findParentStationCodes();
                    }
                    if (nextStationForwardStationCodes.contains(trainPosition.getDestinationStationCode())) {
                        locationStationCode = stationCode;
                        break;
                    }
                }

                if (locationStationCode == null && extraTrainData != null && extraTrainData.getDestinationStationCode() != null && extraTrainData.getDestinationStationCode().length() > 0) {
                    // still haven't figured it out, so match based on GIS data, if available
                    // (this is particularly useful for No Passenger trains)
                    for (String stationCode : nextForwardStationCodes) {
                        if (stationCode.charAt(0) == extraTrainData.getDestinationStationCode().charAt(0)) {
                            locationStationCode = stationCode;
                            break;
                        }
                    }
                }
            }
            String previousStationCode = TrackCircuit.isNextStationCodeValid(nextBackwardStationCode) ? nextBackwardStationCode : (previousTrainStatus != null && !Objects.equals(previousTrainStatus.getPreviousStationCode(), locationStationCode)) ? previousTrainStatus.getPreviousStationCode() : null;

            // derive ETA to next station and other related information
            String status = "?";
            Double minutesAway = null;
            Double maxEta = this.stationToStationDurationMap.get(previousStationCode + "_" + locationStationCode);
            Double distanceLeft = null;
            if (maxEta != null) {
                maxEta -= 0.5d;
            }
            if (trackCircuit.getStationCode() != null) {
                // the train is at a station
                status = "BRD";
                minutesAway = 0d;
                distanceLeft = 0d;
            } else if (maxEta != null) {
                StationToStationTravelTime ststt = this.stationToStationInfoMap.get(previousStationCode + "_" + locationStationCode);
                if (ststt != null) {
                    distanceLeft = getMinDistanceCovered(trackCircuit, locationStationCode);
                    if (distanceLeft != null) {
                        double eta = Math.min((distanceLeft / ststt.getDistance()) * maxEta, maxEta);
                        minutesAway = eta;
                        long roundedEta = Math.round(eta);
                        if (roundedEta <= 0) {
                            status = "ARR";
                        } else {
                            status = String.valueOf(roundedEta);
                        }
                    }
                }
            }

            // derive number of train cars
            String numCars;
            if (trainPosition.getCarCount() == 2) { // there is apparently a glitch where some 8 car trains are reported as 2...
                numCars = "8";
            } else if (trainPosition.getCarCount() == 4) {  // ...and another where 4 really means 6
                numCars = "6";
            } else if (trainPosition.getCarCount() == 0) {
                numCars = (previousTrainStatus != null) ? previousTrainStatus.getCar() : "N/A";
            } else {
                numCars = trainPosition.getCarCount().toString();
            }

            // derive line code and destination station code

            String lineCode;
            if (trainPosition.getServiceType().equals("NoPassengers")) {
                lineCode = "N/A";
            } else {
                lineCode = StationUtil.getLineCodeFromRealTrainId(trainStatus.getRealTrainId());
                if (lineCode == null) {
                    lineCode = (trainPosition.getLineCode() != null) ? trainPosition.getLineCode() : "N/A";
                }
            }
            String destinationStationCode = trainPosition.getDestinationStationCode();

            // TODO: should we do this all the time, or just for trains without a line code?
            // derive line code from destination code from GIS feed, if available
            String derivedLineCodeFromDestinationId = this.derivedLineCodeByDestinationId.get(trainStatus.getDestinationId());
            if (derivedLineCodeFromDestinationId != null) {
                lineCode = this.derivedLineCodeByDestinationId.get(trainStatus.getDestinationId());
            }

            if (!trainPosition.getServiceType().equals("NoPassengers") && trainStatus.getDestinationId() != null && trainStatus.getDirectionNumber() != null) {
                // WMATA isn't supplying us a line code for this train, but it should have one because it's not a No Passenger train, so try to fetch an assignment from our destination code mapping table
                DestinationCodeMapping destinationCodeMapping = this.destinationCodeMap.get(new DestinationCodeMappingPrimaryKey(trainStatus.getDestinationId(), trainStatus.getDirectionNumber()));
                if (destinationCodeMapping != null) {
                    if (destinationCodeMapping.getLineCode() != null) {
                        lineCode = destinationCodeMapping.getLineCode();
                    }
                    if (destinationCodeMapping.getDestinationStationCode() != null) {
                        destinationStationCode = destinationCodeMapping.getDestinationStationCode();
                    }
                }
            }

            // check to make sure the destination station code makes sense given the train's current configuration
            // if it doesn't, overwrite it if possible using train schedule data
            if (!StringUtils.isEmpty(destinationStationCode) && !StringUtils.isEmpty(lineCode) && this.gtfsService.getScheduledDestinationStationCodesByLineAndDirection() != null) {
                Set<String> possibleDestinationCodes = null;
                if (trainStatus.getDirectionNumber() == 1) {
                    possibleDestinationCodes = new HashSet<>();
                    Collection<Set<String>> possibleDestinationStationCodeSets = trackCircuit.getChildStationCodes().values();
                    for (Set<String> possibleDestinationStationCodeSet : possibleDestinationStationCodeSets) {
                        possibleDestinationCodes.addAll(possibleDestinationStationCodeSet);
                    }
                } else if (trainStatus.getDirectionNumber() == 2) {
                    possibleDestinationCodes = new HashSet<>();
                    Collection<Set<String>> possibleDestinationStationCodeSets = trackCircuit.getParentStationCodes().values();
                    for (Set<String> possibleDestinationStationCodeSet : possibleDestinationStationCodeSets) {
                        possibleDestinationCodes.addAll(possibleDestinationStationCodeSet);
                    }
                }
                possibleDestinationCodes.add(locationStationCode);
                if (possibleDestinationCodes != null && !possibleDestinationCodes.contains(destinationStationCode)) {
                    // train appears to have an invalid destination code given its current direction
                    // let's try assigning it a different one that's more appropriate

                    Double shortestTripDuration = null;
                    String closestDestinationCode = null;

                    Set<String> destinationCodes = this.gtfsService.getScheduledDestinationStationCodesByLineAndDirection().get(String.join("_", lineCode, trainStatus.getDirectionNumber().toString()));
                    if (destinationCodes != null) {
                        for (String destinationCode : destinationCodes) {
                            if (possibleDestinationCodes.contains(destinationCode)) {
                                if (destinationCode.equals(locationStationCode)) {
                                    closestDestinationCode = destinationCode;
                                    break;
                                } else {
                                    Double tripDuration = this.stationToStationDurationMap.get(locationStationCode + "_" + destinationCode);
                                    if (tripDuration != null) {
                                        if (shortestTripDuration == null || tripDuration < shortestTripDuration) {
                                            shortestTripDuration = tripDuration;
                                            closestDestinationCode = destinationCode;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (closestDestinationCode != null) {
                        destinationStationCode = closestDestinationCode;
                    }
                }
            }

            if (destinationStationCode == null && extraTrainData != null && extraTrainData.getDestinationStationCode() != null) {
                destinationStationCode = extraTrainData.getDestinationStationCode();
            }

            // derive destination station name
            String destinationName;
            if (lineCode.equals("N/A")) {
                destinationName = "No Passenger";
            } else if (destinationStationCode != null) {
                destinationName = StationUtil.getStationName(destinationStationCode);
            } else {
                destinationName = "N/A";
            }

            // derive next/current station name
            String locationName = (locationStationCode != null) ? StationUtil.getStationName(locationStationCode) : "N/A";

            // consider it a new trip if a train goes from non-revenue to revenue
            boolean isNewTrip = (previousTrainStatus != null && previousTrainStatus.getLine().equals("N/A") && !lineCode.equals("N/A"));

            trainStatus.setCar(numCars);
            trainStatus.setDestination(destinationName);
            trainStatus.setDestinationCode(destinationStationCode);
            trainStatus.setOriginalLineCode(trainPosition.getLineCode());
            trainStatus.setOriginalDestinationCode(trainPosition.getDestinationStationCode());
            trainStatus.setDestinationName(destinationName);
            trainStatus.setGroup(trainStatus.getDirectionNumber().toString());
            trainStatus.setLine(lineCode);
            trainStatus.setCurrentStationCode(locationStationCode);
            trainStatus.setCurrentStationName(locationName);
            trainStatus.setLocationCode(locationStationCode);
            trainStatus.setLocationName(locationName);
            trainStatus.setMin(status);
            trainStatus.setMinutesAway(minutesAway);
            trainStatus.setNumPositiveTags(Math.toIntExact(this.trainTaggingService.getNumPositiveTags(trainStatus.getTrainId())));
            trainStatus.setNumNegativeTags(Math.toIntExact(this.trainTaggingService.getNumNegativeTags(trainStatus.getTrainId())));
            trainStatus.setTrackNumber(trackCircuit.getTrackNumber());
            trainStatus.setRawTrackCircuitId(trainPosition.getCircuitId());
            trainStatus.setScheduledTime(null);
            trainStatus.setIsScheduled(false);
            trainStatus.setMaxMinutesAway(maxEta);
            trainStatus.setPreviousStationCode(previousStationCode);
            trainStatus.setPreviousStationName(StationUtil.getStationName(previousStationCode));
            trainStatus.setLastVisitedStation((previousTrainStatus != null) ? previousTrainStatus.getLastVisitedStation() : null);
            trainStatus.setLastVisitedStationCode((previousTrainStatus != null) ? previousTrainStatus.getLastVisitedStationCode() : null);
            trainStatus.setCircuitName((this.trackCircuitInfoMap.get(trainPosition.getCircuitId()) != null) ? this.trackCircuitInfoMap.get(trainPosition.getCircuitId()).getTrackId() : null);
            trainStatus.setSecondsAtLastVisitedStation((previousTrainStatus != null) ? previousTrainStatus.getSecondsAtLastVisitedStation() : null);
            trainStatus.setDistanceFromNextStation((distanceLeft != null) ? ((int) Math.round(distanceLeft)) : null);
            trainStatus.setKeyedDown(trainStatus.getSecondsSinceLastMoved() >= TimeUnit.MINUTES.toSeconds(30));
            trainStatus.setWasKeyedDown(previousTrainStatus != null && (previousTrainStatus.isKeyedDown() || previousTrainStatus.wasKeyedDown()));
            trainStatus.setSecondsDelayed((previousTrainStatus != null && !isNewTrip) ? previousTrainStatus.getSecondsDelayed() : 0);
            trainStatus.setSecondsOffSchedule((previousTrainStatus != null && !isNewTrip) ? previousTrainStatus.getSecondsOffSchedule() : 0);
            trainStatus.setTrackNumberAtLastVisitedStation((previousTrainStatus != null) ? previousTrainStatus.getTrackNumberAtLastVisitedStation() : null);
            trainStatus.setDirectionNumberAtLastVisitedStation((previousTrainStatus != null) ? previousTrainStatus.getDirectionNumberAtLastVisitedStation() : null);
            trainStatus.setLineCodeAtLastVisitedStation((previousTrainStatus != null) ? previousTrainStatus.getLineCodeAtLastVisitedStation() : null);
            trainStatus.setDestinationCodeAtLastVisitedStation((previousTrainStatus != null) ? previousTrainStatus.getDestinationCodeAtLastVisitedStation() : null);
            trainStatus.setTripId((previousTrainStatus != null && !isNewTrip) ? previousTrainStatus.getTripId() : UUID.randomUUID());
            trainStatus.setFirstObservedTrain((previousTrainStatus != null) ? previousTrainStatus.getFirstObservedTrain() : now);
            trainStatus.setDestinationStationAbbreviation(StationUtil.getStationAbbreviation(destinationStationCode));

            if (previousTrainStatus != null) {
                // attempt to detect any recent train offloads or line changes
                if (TimeUnit.SECONDS.convert(trainStatus.getObservedDate().getTime() - previousTrainStatus.getObservedDate().getTime(), TimeUnit.MILLISECONDS) <= 30) {
                    if (!"N/A".equals(previousTrainStatus.getLine()) && previousTrainStatus.getDirectionNumber() != null && previousTrainStatus.getDestinationCode() != null && !"No Passenger".equals(previousTrainStatus.getDestinationName()) && !"N/A".equals(previousTrainStatus.getDestinationName())) {
                        String nearestStationCode;
                        if ("BRD".equals(previousTrainStatus.getMin())) {
                            // nearest station is the one the train is currently boarding at
                            nearestStationCode = previousTrainStatus.getLocationCode();
                        } else {
                            nearestStationCode = previousTrainStatus.getLastVisitedStationCode();
                            if (nearestStationCode == null) {
                                nearestStationCode = previousTrainStatus.getPreviousStationCode();
                            }
                        }

                        // we don't care about this stuff if it happens at terminal stations or at the train's destination station
                        if (this.terminalStationScheduledTrainStatusesMap != null && !this.terminalStationScheduledTrainStatusesMap.keySet().contains(trainStatus.getLastVisitedStationCode()) && !this.terminalStationScheduledTrainStatusesMap.keySet().contains(trainStatus.getLocationCode()) && !previousTrainStatus.getDestinationCode().equals(nearestStationCode)) {
                            if ("No Passenger".equals(trainStatus.getDestinationName())) {
                                // a train in active revenue service appears to have gone out of service recently
                                trainOffloads.add(new TrainOffload(now, previousTrainStatus.getTrainId(), previousTrainStatus.getRealTrainId(), previousTrainStatus.getLine(), previousTrainStatus.getDirectionNumber(), previousTrainStatus.getDestinationCode(), nearestStationCode));
                                this.twitterBotService.tweetTrainOutOfService(previousTrainStatus, nearestStationCode);
                            } else if (!previousTrainStatus.getLine().equals(trainStatus.getLine()) && !"N/A".equals(trainStatus.getLine()) && previousTrainStatus.getDestinationId() != null && trainStatus.getDestinationId() != null && !previousTrainStatus.getDestinationId().equals(trainStatus.getDestinationId())) {
                                // a train in active revenue service appears to have changed lines recently
                                this.twitterBotService.tweetTrainChangedDestination(trainStatus, previousTrainStatus, nearestStationCode);
                            }
                        }
                    }
                }

                String trainAtStation = null;
                if (trainStatus.getLocationCode() != null) {
                    // if a train is boarding at a station, or is one circuit away from a station,
                    // consider it boarding that station for the purposes of station arrival/departure and delay analysis
                    if ("BRD".equals(trainStatus.getMin())) {
                        trainAtStation = trainStatus.getLocationCode();
                    } else {
                        Set<TrackCircuit> expandedLastVisitedStationCircuits = new HashSet<>();

                        TrackCircuit lastVisitedStationTrack1Circuit = this.stationTrackCircuitMap.get(trainStatus.getLastVisitedStationCode() + "_1");
                        if (lastVisitedStationTrack1Circuit != null) {
                            expandedLastVisitedStationCircuits.add(lastVisitedStationTrack1Circuit);
                            expandedLastVisitedStationCircuits.addAll(lastVisitedStationTrack1Circuit.getChildNeighbors());
                            expandedLastVisitedStationCircuits.addAll(lastVisitedStationTrack1Circuit.getParentNeighbors());
                        }
                        TrackCircuit lastVisitedStationTrack2Circuit = this.stationTrackCircuitMap.get(trainStatus.getLastVisitedStationCode() + "_2");
                        if (lastVisitedStationTrack2Circuit != null) {
                            expandedLastVisitedStationCircuits.add(lastVisitedStationTrack2Circuit);
                            expandedLastVisitedStationCircuits.addAll(lastVisitedStationTrack2Circuit.getChildNeighbors());
                            expandedLastVisitedStationCircuits.addAll(lastVisitedStationTrack2Circuit.getParentNeighbors());
                        }

                        if (expandedLastVisitedStationCircuits.contains(trainStatus.getCurrentTrackCircuit())) {
                            trainAtStation = trainStatus.getLastVisitedStationCode();
                        } else {
                            Set<TrackCircuit> expandedPreviousStationCircuits = new HashSet<>();

                            TrackCircuit previousStationTrack1Circuit = this.stationTrackCircuitMap.get(trainStatus.getPreviousStationCode() + "_1");
                            if (previousStationTrack1Circuit != null) {
                                expandedPreviousStationCircuits.add(previousStationTrack1Circuit);
                                expandedPreviousStationCircuits.addAll(previousStationTrack1Circuit.getChildNeighbors());
                                expandedPreviousStationCircuits.addAll(previousStationTrack1Circuit.getParentNeighbors());
                            }
                            TrackCircuit previousStationTrack2Circuit = this.stationTrackCircuitMap.get(trainStatus.getPreviousStationCode() + "_2");
                            if (previousStationTrack2Circuit != null) {
                                expandedPreviousStationCircuits.add(previousStationTrack2Circuit);
                                expandedPreviousStationCircuits.addAll(previousStationTrack2Circuit.getChildNeighbors());
                                expandedPreviousStationCircuits.addAll(previousStationTrack2Circuit.getParentNeighbors());
                            }

                            if (expandedPreviousStationCircuits.contains(trainStatus.getCurrentTrackCircuit())) {
                                trainAtStation = trainStatus.getPreviousStationCode();
                            } else if (expandedLastVisitedStationCircuits.contains(previousTrainStatus.getCurrentTrackCircuit()) || expandedPreviousStationCircuits.contains(previousTrainStatus.getCurrentTrackCircuit())) {
                                // not only is this train not at a station, it has just departed one
                                // log this station departure accordingly

                                if (trainStatus.getLastVisitedStationCode() != null) {
                                    Integer numCarsInteger = null;
                                    try {
                                        numCarsInteger = Integer.parseInt(trainStatus.getCar());
                                    } catch (NumberFormatException ignored) {
                                    }

                                    String key = String.join("_", trainStatus.getLastVisitedStationCode(), trainStatus.getLine(), trainStatus.getDestinationCode());
                                    DepartureInfo lastDeparture = this.lastStationDepartureMap.get(key);

                                    Calendar departureTime = Calendar.getInstance();
                                    departureTime.setTime(previousTrainStatus.getObservedDate());

                                    if (lastDeparture == null || (!lastDeparture.getTrainId().equals(trainStatus.getTrainId()) || !lastDeparture.getDirectionNumber().equals(trainStatus.getDirectionNumber()))) {
                                        observedTrainDepartures.add(new TrainDeparture(trainStatus.getLine(), trainStatus.getDirectionNumber(), trainStatus.getLastVisitedStationCode(), StationUtil.getStationName(trainStatus.getLastVisitedStationCode()), trainStatus.getDestinationCode(), trainStatus.getDestinationName(), departureTime, trainStatus.getTrainId(), trainStatus.getRealTrainId(), numCarsInteger));
                                    }

                                    if (trainStatus.getLine() != null && trainStatus.getDestinationCode() != null) {
                                        if (lastDeparture != null) {
                                            if (!lastDeparture.getTrainId().equals(trainStatus.getTrainId()) || !lastDeparture.getDirectionNumber().equals(trainStatus.getDirectionNumber())) {
                                                Double observedWaitingTime = ((departureTime.getTimeInMillis() - lastDeparture.getDepartureTime().getTimeInMillis()) / 1000d) / 60d;   // milliseconds -> minutes
                                                lastDeparture.setDepartureTime(departureTime);
                                                lastDeparture.setTimeSinceLastDeparture(observedWaitingTime);
                                                lastDeparture.setTrainId(trainStatus.getTrainId());
                                            }
                                        } else {
                                            this.lastStationDepartureMap.put(key, new DepartureInfo(departureTime, trainStatus.getTrainId(), trainStatus.getDirectionNumber()));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (trainAtStation != null) {
                    if (!trainAtStation.equals(trainStatus.getLastVisitedStationCode())) {
                        // this train has just arrived at a station it wasn't last observed at

                        Calendar arrivingTime = Calendar.getInstance();
                        arrivingTime.setTime(trainStatus.getObservedDate());

                        if (!"N/A".equals(trainStatus.getLine()) && trainStatus.getDirectionNumber() != null) {
                            String key = String.join("_", trainAtStation, trainStatus.getLine(), trainStatus.getDirectionNumber().toString());
                            ArrivalInfo lastArrival = this.lastStationArrivalMap.get(key);
                            if (lastArrival != null) {
                                if (!lastArrival.getTrainId().equals(trainStatus.getTrainId()) || !lastArrival.getDirectionNumber().equals(trainStatus.getDirectionNumber())) {
                                    Double observedWaitingTime = ((arrivingTime.getTimeInMillis() - lastArrival.getArrivalTime().getTimeInMillis()) / 1000d) / 60d;   // milliseconds -> minutes
                                    lastArrival.setArrivalTime(arrivingTime);
                                    lastArrival.setTimeSinceLastArrival(observedWaitingTime);
                                    lastArrival.setTrainId(trainStatus.getTrainId());
                                }
                            } else {
                                this.lastStationArrivalMap.put(key, new ArrivalInfo(arrivingTime, trainStatus.getTrainId(), trainStatus.getDirectionNumber()));
                            }

                            this.gtfsService.setAdherenceToSchedule(trainStatus.getLine(), trainStatus.getDirectionNumber(), trainAtStation, arrivingTime);
                        }

                        if (trainStatus.getLastVisitedStationCode() != null && trainStatus.getLastVisitedStation() != null) {
                            String stationCodesKey = trainStatus.getLastVisitedStationCode() + "_" + trainAtStation;

                            Calendar departingTime = Calendar.getInstance();
                            departingTime.setTime(trainStatus.getLastVisitedStation());

                            // log train departure from previous station
                            // (we wait until the train arrives at another station before we log a train's departure from its previous station to mitigate false train departures)
                            if (trainStatus.getLineCodeAtLastVisitedStation() != null && trainStatus.getDestinationCodeAtLastVisitedStation() != null && !trainStatus.getDestinationCodeAtLastVisitedStation().equals(trainStatus.getLastVisitedStationCode())) {
                                if (trainStatus.getSecondsAtLastVisitedStation() <= 30 && TimeUnit.MILLISECONDS.toSeconds(departingTime.getTimeInMillis() - trainStatus.getFirstObservedTrain().getTimeInMillis()) >= 60) {
                                    // make sure this type of train is actually scheduled to service this station; if not, we can safety assume any expressing is expected
                                    if (this.gtfsService.getExpectedTrainFrequency(trainStatus.getLineCodeAtLastVisitedStation(), trainStatus.getDirectionNumberAtLastVisitedStation(), trainStatus.getLastVisitedStationCode()) != null) {
                                        // this train likely expressed the station it departed previously; it was only there for <= 30 seconds, and we've generally been observing this train for at least 60 seconds since before it departed
                                        TrainExpressedStationEvent expressedStationEvent = new TrainExpressedStationEvent(departingTime, trainStatus.getTrainId(), trainStatus.getRealTrainId(), trainStatus.getLineCodeAtLastVisitedStation(), trainStatus.getDirectionNumberAtLastVisitedStation(), trainStatus.getTrackNumberAtLastVisitedStation(), trainStatus.getDestinationCodeAtLastVisitedStation(), trainStatus.getLastVisitedStationCode(), trainStatus.getSecondsAtLastVisitedStation(), trainStatus.getCar());
                                        trainExpressedStationEvents.add(expressedStationEvent);
//                                        this.twitterBotService.tweetTrainExpressedStation(expressedStationEvent);  // TODO: uncomment once COVID-19-related station closures are over
                                    }
                                }
                            }

                            // update track delays
                            Double expectedTripDuration = this.stationToStationMedianDurationMap.get(stationCodesKey);
                            if (expectedTripDuration != null) {
                                expectedTripDuration += 0.5d;   // 30 seconds from duration map + 30 additional seconds = 60-second expected boarding time
                                TrackCircuit stationCircuitByTrack = this.stationTrackCircuitMap.get(trainStatus.getLastVisitedStationCode() + "_" + trainStatus.getTrackNumber());
                                if (stationCircuitByTrack != null) {
                                    double observedTripDuration = ((trainStatus.getObservedDate().getTime() - trainStatus.getLastVisitedStation().getTime()) / 1000d) / 60d;    // milliseconds => minutes
                                    observedTripDuration += (!this.terminalStationScheduledTrainStatusesMap.keySet().contains(trainStatus.getLastVisitedStationCode()) && trainStatus.getSecondsAtLastVisitedStation() != null) ? (trainStatus.getSecondsAtLastVisitedStation() / 60d /* seconds => minutes */) : 0;

                                    int extraTripDuration = (int) Math.max(Math.round((observedTripDuration - expectedTripDuration) * 60d /* minutes => seconds */), 0);
                                    stationCircuitByTrack.setNumSecondsEstimatedTrackDelays(extraTripDuration);
                                }
                            }

                            // log the trip

                            String lineCodeString = !"N/A".equals(trainStatus.getLine()) ? trainStatus.getLine() : null;
                            String destinationCodeString = !"N/A".equals(trainStatus.getDestinationCode()) ? trainStatus.getDestinationCode() : null;

                            Integer numCarsInt;
                            try {
                                numCarsInt = Integer.parseInt(trainStatus.getCar());
                            } catch (NumberFormatException e) {
                                numCarsInt = null;
                            }

                            String departingStationCode = trainStatus.getLastVisitedStationCode();

                            String arrivingStationCode = trainAtStation;

                            double tripDuration = ((trainStatus.getObservedDate().getTime() - trainStatus.getLastVisitedStation().getTime()) / 1000d) / 60d;    // milliseconds => minutes

                            StationToStationTrip trip = new StationToStationTrip();
                            trip.setTrainId(trainStatus.getTrainId());
                            trip.setRealTrainId(trainStatus.getRealTrainId());
                            trip.setLineCode(lineCodeString);
                            trip.setDestinationStationCode(destinationCodeString);
                            trip.setNumCars(numCarsInt);
                            trip.setDepartingStationCode(departingStationCode);
                            trip.setDepartingTime(departingTime);
                            trip.setArrivingStationCode(arrivingStationCode);
                            trip.setArrivingTrackNumber(trainStatus.getTrackNumber());
                            trip.setArrivingTime(arrivingTime);
                            trip.setTripDuration(tripDuration);
                            trip.setSecondsAtDepartingStation(trainStatus.getSecondsAtLastVisitedStation());
                            trip.setArrivingDirectionNumber(trainStatus.getDirectionNumber());
                            trip.setTripId(trainStatus.getTripId());
                            stationToStationTrips.add(trip);

                            if (!trainStatus.isKeyedDown() && !trainStatus.wasKeyedDown()) {
                                String key = String.join("_", departingStationCode, arrivingStationCode);
                                this.lastStationToStationTripTimeMap.put(key, tripDuration);
                                this.lastStationToStationTimeAtStationMap.put(key, (trainStatus.getSecondsAtLastVisitedStation() != null) ? (trainStatus.getSecondsAtLastVisitedStation() / 60d /* seconds => minutes */) : 0d);
                                this.lastStationToStationTripTimeCalendarMap.put(key, arrivingTime);
                            }
                        }

                        trainStatus.setKeyedDown(false);
                        trainStatus.setWasKeyedDown(false);
                        trainStatus.setSecondsAtLastVisitedStation((int) TimeUnit.MILLISECONDS.toSeconds(trainStatus.getObservedDate().getTime() - previousTrainStatus.getObservedDate().getTime()));   // worst case, this train has been at this station since immediately after the last time we observed it
                        trainStatus.setSecondsDelayed(0);
                    } else {
                        // this train is still at the station it was last observed at

                        if (trainStatus.getLastVisitedStation() != null) {
                            int additionalSecondsAtStation = (int) ((trainStatus.getObservedDate().getTime() - trainStatus.getLastVisitedStation().getTime()) / 1000d); // milliseconds => seconds;
                            trainStatus.setSecondsAtLastVisitedStation((trainStatus.getSecondsAtLastVisitedStation() != null) ? (trainStatus.getSecondsAtLastVisitedStation() + additionalSecondsAtStation) : additionalSecondsAtStation);

                            // train delays can accumulate while at non-destination stations
                            boolean isAtScheduledDestinationStation = this.gtfsService.getScheduledDestinationStationCodesByLine() != null && this.gtfsService.getScheduledDestinationStationCodesByLine().get(trainStatus.getLine()) != null && this.gtfsService.getScheduledDestinationStationCodesByLine().get(trainStatus.getLine()).contains(trainAtStation);
                            boolean isAtTerminalStation = this.terminalStationTrackCircuitIdSet != null && this.terminalStationTrackCircuitIdSet.contains(trainStatus.getTrackCircuitId());
                            if (!isAtScheduledDestinationStation && !isAtTerminalStation && trainStatus.getSecondsAtLastVisitedStation() > 75) {
                                trainStatus.setSecondsOffSchedule(trainStatus.getSecondsOffSchedule() + additionalSecondsAtStation);
                            } else {
                                trainStatus.setSecondsDelayed(0);
                            }
                        }
                    }

                    if (trainStatus.getDirectionNumberAtLastVisitedStation() != null && !trainStatus.getDirectionNumber().equals(trainStatus.getDirectionNumberAtLastVisitedStation())) {
                        // this train is going in a different direction since we last observed it at its last visited station (note: which could be this station)
                        // so, consider this a new trip and reset any train delays
                        trainStatus.setTripId(UUID.randomUUID());
                        trainStatus.setSecondsDelayed(0);
                        trainStatus.setSecondsOffSchedule(0);
                    }

                    trainStatus.setLastVisitedStation(trainStatus.getObservedDate());
                    trainStatus.setLastVisitedStationCode(trainAtStation);
                    trainStatus.setTrackNumberAtLastVisitedStation(trainStatus.getTrackNumber());
                    trainStatus.setDirectionNumberAtLastVisitedStation(trainStatus.getDirectionNumber());
                    trainStatus.setLineCodeAtLastVisitedStation(trainStatus.getLine());
                    trainStatus.setDestinationCodeAtLastVisitedStation(trainStatus.getDestinationCode());
                } else if (trainStatus.getLocationCode() != null && trainStatus.getPreviousStationCode() != null) {
                    // this train is between two stations

                    // train delays can accumulate while between two stations if not in approach to or having last departed from a destination station that is not also a terminal station
                    boolean isInApproachToScheduledDestinationStation = this.gtfsService.getScheduledDestinationStationCodesByLine() != null && this.gtfsService.getScheduledDestinationStationCodesByLine().get(trainStatus.getLine()) != null && this.gtfsService.getScheduledDestinationStationCodesByLine().get(trainStatus.getLine()).contains(trainAtStation);
                    boolean isInApproachToTerminalStation = this.terminalStationTrackCircuitIdSet != null && this.terminalStationTrackCircuitIdSet.contains(trainStatus.getTrackCircuitId());
                    boolean didLastVisitScheduledDestinationStation = trainStatus.getLastVisitedStationCode() != null && this.gtfsService.getScheduledDestinationStationCodesByLine() != null && this.gtfsService.getScheduledDestinationStationCodesByLine().get(trainStatus.getLine()) != null && this.gtfsService.getScheduledDestinationStationCodesByLine().get(trainStatus.getLine()).contains(trainStatus.getLastVisitedStationCode());
                    TrackCircuit lastVisitedStationTrackCircuit = this.stationTrackCircuitMap.get(trainStatus.getLastVisitedStationCode() + "_" + trainStatus.getDirectionNumber());
                    boolean didLastVisitTerminalStation = trainStatus.getLastVisitedStationCode() != null && lastVisitedStationTrackCircuit != null && this.terminalStationTrackCircuitIdSet != null && this.terminalStationTrackCircuitIdSet.contains(lastVisitedStationTrackCircuit.getId());
                    if ((!isInApproachToScheduledDestinationStation || isInApproachToTerminalStation) && (!didLastVisitScheduledDestinationStation || didLastVisitTerminalStation) && trainStatus.getLastVisitedStation() != null) {
                        Double expectedStationToStationTripTime = this.stationToStationMedianDurationMap.get(trainStatus.getLastVisitedStationCode() + "_" + trainStatus.getLocationCode());
                        if (expectedStationToStationTripTime != null) {
                            expectedStationToStationTripTime -= 0.5d;   // 30 seconds from duration map - 30 seconds = no expected boarding time (we're only interested in potential delays between stations here)

                            Double stationToStationTripTimeSoFar = ((trainStatus.getObservedDate().getTime() - trainStatus.getLastVisitedStation().getTime()) / 1000d) / 60d;   // milliseconds => minutes
                            trainStatus.setSecondsDelayed((int) Math.round(Math.max(stationToStationTripTimeSoFar - expectedStationToStationTripTime, 0) * 60d /* minutes => seconds */));
                        } else {
                            trainStatus.setSecondsDelayed(0);
                        }
                    } else {
                        trainStatus.setSecondsDelayed(0);
                    }
                    trainStatus.setSecondsOffSchedule(trainStatus.getSecondsOffSchedule() + Math.max(trainStatus.getSecondsDelayed() - ((previousTrainStatus.getSecondsDelayed() != null) ? previousTrainStatus.getSecondsDelayed() : 0), 0));
                }

                // don't (potentially) tweet about trains unless we're actively accumulating delays
                // this filters out trains hanging out at terminal stations, trains on non-revenue track, etc.
                if (!trainStatus.isKeyedDown() && !trainStatus.wasKeyedDown() && trainStatus.getSecondsDelayed() != null && previousTrainStatus.getSecondsDelayed() != null && trainStatus.getSecondsOffSchedule() > previousTrainStatus.getSecondsOffSchedule()) {
                    String currentOrNextStationCode = (trainAtStation != null) ? trainAtStation : trainStatus.getLocationCode();
                    Integer distanceFromNextStation = (trainAtStation != null) ? 0 : trainStatus.getDistanceFromNextStation();

                    long currentDelayLevel = trainStatus.getSecondsOffSchedule() / TimeUnit.MINUTES.toSeconds(15);
                    long previousDelayLevel = previousTrainStatus.getSecondsOffSchedule() / TimeUnit.MINUTES.toSeconds(15);
                    if (currentDelayLevel > previousDelayLevel) {
                        this.twitterBotService.tweetTrainDelayed(trainStatus, currentOrNextStationCode, distanceFromNextStation);
                    } else {
                        long currentHoldingLevel = trainStatus.getSecondsSinceLastMoved() / TimeUnit.MINUTES.toSeconds(5);
                        long previousHoldingLevel = previousTrainStatus.getSecondsSinceLastMoved() / TimeUnit.MINUTES.toSeconds(5);
                        if ((currentHoldingLevel > previousHoldingLevel) && (currentHoldingLevel == 1 || currentHoldingLevel == 2)) {
                            this.twitterBotService.tweetTrainHolding(trainStatus, currentOrNextStationCode, distanceFromNextStation);
                        }
                    }
                }
            }

            // no ETA, no way to calculate delays
            if ("?".equals(trainStatus.getMin())) {
                trainStatus.setSecondsDelayed(0);
                trainStatus.setSecondsOffSchedule(0);
                trainStatus.setSecondsAtLastVisitedStation(null);
                trainStatus.setLastVisitedStation(null);
                trainStatus.setLastVisitedStationCode(null);
                trainStatus.setTrackNumberAtLastVisitedStation(null);
                trainStatus.setDirectionNumberAtLastVisitedStation(null);
                trainStatus.setLineCodeAtLastVisitedStation(null);
                trainStatus.setDestinationCodeAtLastVisitedStation(null);
            }

            trainStatus.setEstimatedMinutesAway(getPredictedRideTime(now, trainStatus.getLastVisitedStationCode(), trainStatus.getLocationCode(), trainStatus));
        }

        // determine which train statuses are missing from the last tick
        for (TrainStatus previousTrainStatus : this.trainStatusesMap.values()) {
            if (!trainStatusesMap.containsKey(previousTrainStatus.getTrainId()) && previousTrainStatus.getPreviousStationCode() != null && previousTrainStatus.getLocationCode() != null && previousTrainStatus.getDestinationCode() != null && !previousTrainStatus.isNotOnRevenueTrack() && !this.terminalStationTrackCircuitIdSet.contains(previousTrainStatus.getTrackCircuitId()) && !"N/A".equals(previousTrainStatus.getLine())) {
                trainDisappearances.add(new TrainDisappearance(now, previousTrainStatus.getTrainId(), previousTrainStatus.getRealTrainId(), previousTrainStatus.getLine(), previousTrainStatus.getDirectionNumber(), previousTrainStatus.getDestinationCode(), previousTrainStatus.getLocationCode(), previousTrainStatus.getTrackCircuitId()));
//                this.disappearedTrainStatus = previousTrainStatus;
            }

            if (!trainStatusesMap.containsKey(previousTrainStatus.getTrainId())) {
                String removedTrainId = this.keptTrainIdByRemovedTrainId.inverse().get(previousTrainStatus.getTrainId());
                if (removedTrainId != null) {
                    TrainStatus removedTrain = trainStatusesMap.get(removedTrainId);
                    if (removedTrain != null) {
                        // seems we made the wrong choice of train to remove; the one we chose is now gone, and the other remains
                        // whoops. move the data back!

                        TrainStatus keptTrain = previousTrainStatus;

                        duplicateTrainEvents.add(new DuplicateTrainEvent(now, removedTrain.getRealTrainId(), removedTrain.getTrainId(), keptTrain.getTrainId(), removedTrain.getLine(), removedTrain.getDirectionNumber(), keptTrain.getDirectionNumber(), removedTrain.getOriginalDestinationCode(), removedTrain.getLocationCode()));

                        removedTrain.setSecondsOffSchedule(keptTrain.getSecondsOffSchedule());
                        if (removedTrain.getLastVisitedStation() == null) {
                            removedTrain.setLastVisitedStation(keptTrain.getLastVisitedStation());
                        }
                        if (removedTrain.getLastVisitedStationCode() == null) {
                            removedTrain.setLastVisitedStationCode(keptTrain.getLastVisitedStationCode());
                        }
                        if (removedTrain.getSecondsAtLastVisitedStation() == null) {
                            removedTrain.setSecondsAtLastVisitedStation(keptTrain.getSecondsAtLastVisitedStation());
                        }
                        if (removedTrain.getSecondsDelayed() == null) {
                            removedTrain.setSecondsDelayed(keptTrain.getSecondsDelayed());
                        }
                        if (removedTrain.getTrackNumberAtLastVisitedStation() == null) {
                            removedTrain.setTrackNumberAtLastVisitedStation(keptTrain.getTrackNumberAtLastVisitedStation());
                        }
                        if (removedTrain.getDirectionNumberAtLastVisitedStation() == null) {
                            removedTrain.setDirectionNumberAtLastVisitedStation(keptTrain.getDirectionNumberAtLastVisitedStation());
                        }
                        if (removedTrain.getLineCodeAtLastVisitedStation() == null) {
                            removedTrain.setLineCodeAtLastVisitedStation(keptTrain.getLineCodeAtLastVisitedStation());
                        }
                        if (removedTrain.getDestinationCodeAtLastVisitedStation() == null) {
                            removedTrain.setDestinationCodeAtLastVisitedStation(keptTrain.getDestinationCodeAtLastVisitedStation());
                        }
                        removedTrain.setTripId(keptTrain.getTripId());

                        this.trainTaggingService.migrateTrainTags(keptTrain, removedTrain);

                        this.keptTrainIdByRemovedTrainId.remove(removedTrainId);
                        this.keptTrainIdByRemovedTrainId.forcePut(keptTrain.getTrainId(), removedTrain.getTrainId());
                    }
                }
            }
        }

        // remove/merge duplicate trains
        for (TrainStatus trainStatus : trainStatusesMap.values()) {
            // we're only interested in potentially merging:
            // - trains that have already been merged (they may need to be merged again with some other train);
            // - trains that have not been merged that we haven't observed yet

            TrainStatus previousTrainStatus = this.trainStatusesMap.get(trainStatus.getTrainId());
            if (!this.keptTrainIdByRemovedTrainId.containsKey(trainStatus.getTrainId()) && previousTrainStatus != null) {
                continue;
            }

            for (TrainStatus trainStatus2 : trainStatusesMap.values()) {
                if (trainStatus2.equals(trainStatus)) {
                    // don't merge ourselves
                    continue;
                }

                if (trainStatus2.getTrainId().equals(this.keptTrainIdByRemovedTrainId.get(trainStatus.getTrainId()))) {
                    // don't re-merge with the train we last merged with
                    continue;
                }

                TrainStatus previousTrainStatus2 = this.trainStatusesMap.get(trainStatus2.getTrainId());
                if (previousTrainStatus2 == null) {
                    // make sure the potential train to merge into has been observed already
                    continue;
                }

                if (trainStatus.getRealTrainId() != null && trainStatus.getRealTrainId().equals(trainStatus2.getRealTrainId())) {
                    Double minDistanceCovered = getMinDistanceCovered(trainStatus.getCurrentTrackCircuit(), trainStatus2.getCurrentTrackCircuit());
                    if (minDistanceCovered != null && minDistanceCovered <= 1800 /* 600 feet = length of station platforms, x3 to account for latency */ ) {
                        Double trainStatusDistanceToDestination = getMinDistanceCovered(trainStatus.getCurrentTrackCircuit(), trainStatus.getOriginalDestinationCode());
                        Double trainStatus2DistanceToDestination = getMinDistanceCovered(trainStatus2.getCurrentTrackCircuit(), trainStatus2.getOriginalDestinationCode());
                        if (trainStatusDistanceToDestination != null && trainStatus2DistanceToDestination != null) {
                            TrainStatus trainStatusToKeep = null;
                            TrainStatus trainStatusToRemove = null;

                            if (trainStatusDistanceToDestination > trainStatus2DistanceToDestination) {
                                trainStatusToKeep = trainStatus2;
                                trainStatusToRemove = trainStatus;
                            } else if (trainStatusDistanceToDestination < trainStatus2DistanceToDestination) {
                                trainStatusToKeep = trainStatus;
                                trainStatusToRemove = trainStatus2;
                            }

                            if (trainStatusToKeep != null && trainStatusToRemove != null) {
                                duplicateTrainEvents.add(new DuplicateTrainEvent(now, trainStatusToKeep.getRealTrainId(), trainStatusToKeep.getTrainId(), trainStatusToRemove.getTrainId(), trainStatusToKeep.getLine(), trainStatusToKeep.getDirectionNumber(), trainStatusToRemove.getDirectionNumber(), trainStatusToKeep.getOriginalDestinationCode(), trainStatusToKeep.getLocationCode()));

                                if (trainStatusToRemove.equals(trainStatus2)) {
                                    // we have some data about this train contained in the train status that we're about to delete
                                    // move that data over to the train status we're going to keep, otherwise we'd lose this data

                                    trainStatusToKeep.setSecondsOffSchedule(trainStatusToRemove.getSecondsOffSchedule());
                                    if (trainStatusToKeep.getLastVisitedStation() == null) {
                                        trainStatusToKeep.setLastVisitedStation(trainStatusToRemove.getLastVisitedStation());
                                    }
                                    if (trainStatusToKeep.getLastVisitedStationCode() == null) {
                                        trainStatusToKeep.setLastVisitedStationCode(trainStatusToRemove.getLastVisitedStationCode());
                                    }
                                    if (trainStatusToKeep.getSecondsAtLastVisitedStation() == null) {
                                        trainStatusToKeep.setSecondsAtLastVisitedStation(trainStatusToRemove.getSecondsAtLastVisitedStation());
                                    }
                                    if (trainStatusToKeep.getSecondsDelayed() == null) {
                                        trainStatusToKeep.setSecondsDelayed(trainStatusToRemove.getSecondsDelayed());
                                    }
                                    if (trainStatusToKeep.getTrackNumberAtLastVisitedStation() == null) {
                                        trainStatusToKeep.setTrackNumberAtLastVisitedStation(trainStatusToRemove.getTrackNumberAtLastVisitedStation());
                                    }
                                    if (trainStatusToKeep.getDirectionNumberAtLastVisitedStation() == null) {
                                        trainStatusToKeep.setDirectionNumberAtLastVisitedStation(trainStatusToRemove.getDirectionNumberAtLastVisitedStation());
                                    }
                                    if (trainStatusToKeep.getLineCodeAtLastVisitedStation() == null) {
                                        trainStatusToKeep.setLineCodeAtLastVisitedStation(trainStatusToRemove.getLineCodeAtLastVisitedStation());
                                    }
                                    if (trainStatusToKeep.getDestinationCodeAtLastVisitedStation() == null) {
                                        trainStatusToKeep.setDestinationCodeAtLastVisitedStation(trainStatusToRemove.getDestinationCodeAtLastVisitedStation());
                                    }
                                    trainStatusToKeep.setTripId(trainStatusToRemove.getTripId());

                                    this.trainTaggingService.migrateTrainTags(trainStatusToRemove, trainStatusToKeep);
                                }

                                this.keptTrainIdByRemovedTrainId.remove(trainStatusToKeep.getTrainId());
                                this.keptTrainIdByRemovedTrainId.forcePut(trainStatusToRemove.getTrainId(), trainStatusToKeep.getTrainId());
                            }
                        }
                    }
                }
            }
        }
        this.keptTrainIdByRemovedTrainId.keySet().removeIf(removedTrainId -> trainStatusesMap.remove(removedTrainId) == null);

        // construct a list of train statuses for each station by deriving global train statuses

        Map<String, List<TrainStatus>> stationTrainStatusesMap = new HashMap<>();
        for (String stationCode : this.stationCodesSet) {
            String stationName = StationUtil.getStationName(stationCode);
            for (TrainStatus ts : trainStatusesMap.values()) {
                if (ts.isKeyedDown()) {
                    continue;
                }

                if ("No Passenger".equals(ts.getDestinationName())) {
                    // filter out No Passenger trains
                    continue;
                }

                // if this train is at a terminal station, we should filter out this train status
                if (this.terminalStationTrackCircuitIdSet != null && this.terminalStationTrackCircuitIdSet.contains(ts.getTrackCircuitId())) {
                    continue;
                }

                // get ETA to this station
                String status = "N/A";
                Double eta = getEstimatedTimeToStation(ts, stationCode);
                Calendar adjustedScheduleTime = null;
                if (eta == null) {
                    // filter out trains that physically could not ever reach this station directly
                    // this check is directional, so this will also filter out trains in the same direction who have passed this station already

                    // ...except trains that have tripped the next circuit beyond a station circuit for a given direction,
                    // as those trains may have done so without actually leaving the station corresponding to that circuit and thus should still show as BRD

                    boolean shouldSkipTrain = true;
                    if (ts.getMin() != null && !"?".equals(ts.getMin()) && ts.getLine() != null && ts.getDestinationCode() != null && ts.getLastVisitedStationCode() != null && ts.getDirectionNumber() != null && ts.getLastVisitedStationCode().equals(stationCode)) {
                        TrackCircuit lastVisitedStationTrackCircuit = this.stationTrackCircuitMap.get(ts.getLastVisitedStationCode() + "_" + ts.getDirectionNumber());
                        if (lastVisitedStationTrackCircuit != null) {
                            if ((ts.getDirectionNumber() == 1 && lastVisitedStationTrackCircuit.getChildNeighbors().contains(ts.getCurrentTrackCircuit())) ||
                                    (ts.getDirectionNumber() == 2 && lastVisitedStationTrackCircuit.getParentNeighbors().contains(ts.getCurrentTrackCircuit()))) {
                                status = "BRD";
                                eta = 0d;
                                shouldSkipTrain = false;
                            }
                        }
                    }
                    if (shouldSkipTrain) {
                        continue;
                    }
                } else {
                    long roundedEta = Math.round(eta);
                    if (ts.getScheduledTime() != null) {
                        long additionalMillis = TimeUnit.MINUTES.toMillis(Math.round(eta - ts.getMinutesAway()));
                        adjustedScheduleTime = Calendar.getInstance();
                        adjustedScheduleTime.setTimeInMillis(ts.getScheduledTime().getTimeInMillis() + additionalMillis);
                        status = new SimpleDateFormat("h:mm").format(adjustedScheduleTime.getTime());
                    } else if (eta == 0) {
                        status = "BRD";
                    } else if (roundedEta <= 0) {
                        status = "ARR";
                    } else {
                        status = String.valueOf(roundedEta);
                    }
                }

                TrainStatus newTrainStatus = new TrainStatus(ts.getTrainId());
                newTrainStatus.setRecentTweets(ts.getRecentTweets());
                newTrainStatus.setRealTrainId(ts.getRealTrainId());
                newTrainStatus.setId(ts.getId());
                newTrainStatus.setCar(ts.getCar());
                newTrainStatus.setDestination(ts.getDestination());
                newTrainStatus.setDestinationCode(ts.getDestinationCode());
                newTrainStatus.setOriginalDestinationCode(ts.getOriginalLineCode());
                newTrainStatus.setOriginalDestinationCode(ts.getOriginalDestinationCode());
                newTrainStatus.setDestinationName(ts.getDestinationName());
                newTrainStatus.setGroup(ts.getGroup());
                newTrainStatus.setLine(ts.getLine());
                newTrainStatus.setCurrentStationCode(ts.getCurrentStationCode());
                newTrainStatus.setCurrentStationName(ts.getCurrentStationName());
                newTrainStatus.setLocationCode(stationCode); // important bit right here! (that's not just a wrote copy)
                newTrainStatus.setLocationName(stationName); // important bit right here! (that's not just a wrote copy)
                newTrainStatus.setMin(status); // important bit right here! (that's not just a wrote copy)
                newTrainStatus.setParentMin(ts.getMin());
                newTrainStatus.setMinutesAway(eta); // important bit right here! (that's not just a wrote copy)
                newTrainStatus.setNumPositiveTags(ts.getNumPositiveTags());
                newTrainStatus.setNumNegativeTags(ts.getNumNegativeTags());
                newTrainStatus.setTrackNumber(ts.getTrackNumber());
                newTrainStatus.setCurrentTrackCircuit(ts.getCurrentTrackCircuit());
                newTrainStatus.setTrackCircuitId(ts.getTrackCircuitId());
                newTrainStatus.setRawTrackCircuitId(ts.getRawTrackCircuitId());
                newTrainStatus.setNotOnRevenueTrack(ts.isNotOnRevenueTrack());
                newTrainStatus.setDirectionNumber(ts.getDirectionNumber());
                newTrainStatus.setScheduledTime(adjustedScheduleTime);  // important bit right here! (that's not just a wrote copy)
                newTrainStatus.setIsScheduled(adjustedScheduleTime != null);    // important bit right here! (that's not just a wrote copy)
                newTrainStatus.setMaxMinutesAway(this.stationToStationDurationMap.get(ts.getPreviousStationCode() + "_" + ts.getLocationCode()) != null ? this.stationToStationDurationMap.get(ts.getPreviousStationCode() + "_" + ts.getLocationCode()) - 0.5d : null);
                newTrainStatus.setPreviousStationCode(ts.getPreviousStationCode());
                newTrainStatus.setPreviousStationName(ts.getPreviousStationName());
                newTrainStatus.setSecondsSinceLastMoved(ts.getSecondsSinceLastMoved());
                newTrainStatus.setIsCurrentlyHoldingOrSlow(ts.isCurrentlyHoldingOrSlow());
                newTrainStatus.setSecondsOffSchedule(ts.getSecondsOffSchedule());
                newTrainStatus.setSecondsDelayed(ts.getSecondsDelayed());
                newTrainStatus.setLastMovedCircuits(ts.getLastMovedCircuits());
                newTrainStatus.setTrainSpeed(ts.getTrainSpeed());
                newTrainStatus.setCircuitName(ts.getCircuitName());
                newTrainStatus.setDistanceFromNextStation(ts.getDistanceFromNextStation());
                newTrainStatus.setKeyedDown(ts.isKeyedDown());
                newTrainStatus.setWasKeyedDown(ts.wasKeyedDown());
                newTrainStatus.setLastVisitedStation(ts.getLastVisitedStation());
                newTrainStatus.setLastVisitedStationCode(ts.getLastVisitedStationCode());
                newTrainStatus.setSecondsAtLastVisitedStation(ts.getSecondsAtLastVisitedStation());
                newTrainStatus.setTripId(ts.getTripId());
                newTrainStatus.setFirstObservedTrain(ts.getFirstObservedTrain());
                newTrainStatus.setDestinationStationAbbreviation(ts.getDestinationStationAbbreviation());
                newTrainStatus.setObservedDate(ts.getObservedDate());

                newTrainStatus.setDestinationId(ts.getDestinationId());
                newTrainStatus.setAreDoorsOpenOnLeft(ts.areDoorsOpenOnLeft());
                newTrainStatus.setAreDoorsOpenOnRight(ts.areDoorsOpenOnRight());
                newTrainStatus.setAdjustingOnPlatform(ts.isAdjustingOnPlatform());
                newTrainStatus.setAreDoorsOperatingManually(ts.areDoorsOperatingManually());
                newTrainStatus.setLat(ts.getLat());
                newTrainStatus.setLon(ts.getLon());
                newTrainStatus.setDirection(ts.getDirection());

                newTrainStatus.setNumTagsByType(this.trainTaggingService.getNumTrainTagsByType(ts.getTrainId()));

                newTrainStatus.setEstimatedMinutesAway(getPredictedRideTime(now, newTrainStatus.getLastVisitedStationCode(), newTrainStatus.getLocationCode(), newTrainStatus));

                List<TrainStatus> stationTrainStatuses = stationTrainStatusesMap.get(stationCode);
                if (stationTrainStatuses == null) {
                    stationTrainStatuses = new ArrayList<>();
                    stationTrainStatuses.add(newTrainStatus);
                    stationTrainStatusesMap.put(stationCode, stationTrainStatuses);
                } else {
                    stationTrainStatuses.add(newTrainStatus);
                }
            }
        }

        // sprinkle in scheduled train statuses derived from GTFS data
        if (this.stationScheduledTrainStatusesMap != null) {
            for (String stationCode : this.stationScheduledTrainStatusesMap.keySet()) {
                List<TrainStatus> realTimeTrainStatuses = stationTrainStatusesMap.get(stationCode);
                List<TrainStatus> scheduledTrainStatuses = getCurrentScheduledTrainStatusesForStation(now, stationCode, realTimeTrainStatuses);
                if (scheduledTrainStatuses != null) {
                    if (realTimeTrainStatuses != null) {
                        realTimeTrainStatuses.addAll(scheduledTrainStatuses);
                    } else {
                        stationTrainStatusesMap.put(stationCode, scheduledTrainStatuses);
                    }
                }
            }
        }

        // below are stations with two levels, whose lists are a sum of their parts
        List<TrainStatus> fortTotten = new ArrayList<>();
        if (stationTrainStatusesMap.get("B06") != null) {
            fortTotten.addAll(stationTrainStatusesMap.get("B06"));
        }
        if (stationTrainStatusesMap.get("E06") != null) {
            fortTotten.addAll(stationTrainStatusesMap.get("E06"));
        }
        if (fortTotten.size() > 0) {
            stationTrainStatusesMap.put("B06|E06", fortTotten);
            stationTrainStatusesMap.put("E06|B06", fortTotten);
        }
        List<TrainStatus> galleryPlace = new ArrayList<>();
        if (stationTrainStatusesMap.get("B01") != null) {
            galleryPlace.addAll(stationTrainStatusesMap.get("B01"));
        }
        if (stationTrainStatusesMap.get("F01") != null) {
            galleryPlace.addAll(stationTrainStatusesMap.get("F01"));
        }
        if (galleryPlace.size() > 0) {
            stationTrainStatusesMap.put("B01|F01", galleryPlace);
            stationTrainStatusesMap.put("F01|B01", galleryPlace);
        }
        List<TrainStatus> lenfantPlaza = new ArrayList<>();
        if (stationTrainStatusesMap.get("D03") != null) {
            lenfantPlaza.addAll(stationTrainStatusesMap.get("D03"));
        }
        if (stationTrainStatusesMap.get("F03") != null) {
            lenfantPlaza.addAll(stationTrainStatusesMap.get("F03"));
        }
        if (lenfantPlaza.size() > 0) {
            stationTrainStatusesMap.put("D03|F03", lenfantPlaza);
            stationTrainStatusesMap.put("F03|D03", lenfantPlaza);
        }
        List<TrainStatus> metroCenter = new ArrayList<>();
        if (stationTrainStatusesMap.get("C01") != null) {
            metroCenter.addAll(stationTrainStatusesMap.get("C01"));
        }
        if (stationTrainStatusesMap.get("A01") != null) {
            metroCenter.addAll(stationTrainStatusesMap.get("A01"));
        }
        if (metroCenter.size() > 0) {
            stationTrainStatusesMap.put("C01|A01", metroCenter);
            stationTrainStatusesMap.put("A01|C01", metroCenter);
        }

        // sort train statuses by ETA for each station (in ascending order)
        for (List<TrainStatus> trainStatuses : stationTrainStatusesMap.values()) {
            trainStatuses.sort((ts1, ts2) -> {
                Double ts1Min;
                try {
                    ts1Min = (ts1.getMinutesAway() != null) ? ts1.getMinutesAway() : Double.parseDouble(ts1.getMin());
                } catch (NumberFormatException e) {
                    switch (ts1.getMin()) {
                        case "BRD":
                            ts1Min = -1d;
                            break;
                        case "ARR":
                            ts1Min = 0d;
                            break;
                        default:
                            // trains with unknown durations should be considered equally indefinitely far away
                            ts1Min = 999d;
                            break;
                    }
                }

                Double ts2Min;
                try {
                    ts2Min = (ts2.getMinutesAway() != null) ? ts2.getMinutesAway() : Double.parseDouble(ts2.getMin());
                } catch (NumberFormatException e) {
                    switch (ts2.getMin()) {
                        case "BRD":
                            ts2Min = -1d;
                            break;
                        case "ARR":
                            ts2Min = 0d;
                            break;
                        default:
                            // trains with unknown durations should be considered equally indefinitely far away
                            ts2Min = 999d;
                            break;
                    }
                }

                return ts1Min.compareTo(ts2Min);
            });
        }

        // tweet about long times between train arrivals compared to schedule
        for (Map.Entry<String, ArrivalInfo> lastStationArrivalEntry : this.lastStationArrivalMap.entrySet()) {
            String key = lastStationArrivalEntry.getKey();
            String[] splitKey = key.split("_");
            String stationCode = splitKey[0];
            String lineCode = splitKey[1];
            int directionNumber = Integer.parseInt(splitKey[2]);

            ArrivalInfo arrivalInfo = lastStationArrivalEntry.getValue();

            Double expectedTimeSinceLastArrival = this.gtfsService.getExpectedTrainFrequency(lineCode, directionNumber, stationCode);
            if (expectedTimeSinceLastArrival == null) {
                continue;
            }

            double timeSinceLastArrival = ((now.getTimeInMillis() - arrivalInfo.getArrivalTime().getTimeInMillis()) / 1000d) / 60d;

            String nextTrainMin = null;
            List<TrainStatus> trainStatuses = stationTrainStatusesMap.get(stationCode);
            if (trainStatuses != null) {
                for (TrainStatus trainStatus : trainStatuses) {
                    if (trainStatus.isScheduled()) {
                        continue;
                    }

                    if (!lineCode.equals(trainStatus.getLine())) {
                        continue;
                    }

                    if (directionNumber != trainStatus.getDirectionNumber()) {
                        continue;
                    }

                    nextTrainMin = trainStatus.getMin();
                    break;
                }
            }

            this.twitterBotService.tweetLongTimeSinceLastTrainArrived(arrivalInfo, lineCode, directionNumber, stationCode, timeSinceLastArrival, expectedTimeSinceLastArrival, nextTrainMin);
        }

        // prevent scheduled trains from displaying on line maps (kind of a hack)
        Iterator trainStatusesMapItr = trainStatusesMap.entrySet().iterator();
        while (trainStatusesMapItr.hasNext()) {
            Map.Entry entry = (Map.Entry) trainStatusesMapItr.next();
            TrainStatus trainStatus = (TrainStatus) entry.getValue();
            if (trainStatus.getScheduledTime() == null) {
                continue;
            }

            trainStatusesMapItr.remove();
        }

        // calculate delay statuses between stations
        Map<String, SystemInfo.BetweenStationDelayStatus> betweenStationDelayStatuses = new HashMap<>();
        for (String fromStationCode : this.stationCodesSet) {
            // for track 1
            TrackCircuit fromStationCircuit = this.stationTrackCircuitMap.get(fromStationCode + "_1");
            if (fromStationCircuit != null) {
                for (String toStationCode : fromStationCircuit.getNextChildStationCodes()) {
                    TrackCircuit toStationCircuit = this.stationTrackCircuitMap.get(toStationCode + "_1");
                    if (toStationCircuit != null) {
                        SystemInfo.BetweenStationDelayStatus status = null;
                        if (fromStationCircuit.getNumSecondsEstimatedTrackDelays() >= 120) {
                            if (toStationCircuit.getNumSecondsEstimatedTrackDelays() >= 120) {
                                status = SystemInfo.BetweenStationDelayStatus.DELAYED_TO_DELAYED;
                            } else if (toStationCircuit.getNumSecondsEstimatedTrackDelays() >= 60) {
                                status = SystemInfo.BetweenStationDelayStatus.SLOW_TO_DELAYED;
                            } else {
                                status = SystemInfo.BetweenStationDelayStatus.OK_TO_DELAYED;
                            }
                        } else if (fromStationCircuit.getNumSecondsEstimatedTrackDelays() >= 60) {
                            if (toStationCircuit.getNumSecondsEstimatedTrackDelays() >= 120) {
                                status = SystemInfo.BetweenStationDelayStatus.DELAYED_TO_SLOW;
                            } else if (toStationCircuit.getNumSecondsEstimatedTrackDelays() >= 60) {
                                status = SystemInfo.BetweenStationDelayStatus.SLOW_TO_SLOW;
                            } else {
                                status = SystemInfo.BetweenStationDelayStatus.OK_TO_SLOW;
                            }
                        } else {
                            if (toStationCircuit.getNumSecondsEstimatedTrackDelays() >= 120) {
                                status = SystemInfo.BetweenStationDelayStatus.DELAYED_TO_OK;
                            } else if (toStationCircuit.getNumSecondsEstimatedTrackDelays() >= 60) {
                                status = SystemInfo.BetweenStationDelayStatus.SLOW_TO_OK;
                            }
                        }
                        if (status != null) {
                            betweenStationDelayStatuses.put(fromStationCode + "_" + toStationCode, status);
                        }
                    }
                }
            }

            fromStationCircuit = this.stationTrackCircuitMap.get(fromStationCode + "_2");
            if (fromStationCircuit != null) {
                for (String toStationCode : fromStationCircuit.getNextParentStationCodes()) {
                    TrackCircuit toStationCircuit = this.stationTrackCircuitMap.get(toStationCode + "_2");
                    if (toStationCircuit != null) {
                        SystemInfo.BetweenStationDelayStatus status = null;
                        if (fromStationCircuit.getNumSecondsEstimatedTrackDelays() >= 120) {
                            if (toStationCircuit.getNumSecondsEstimatedTrackDelays() >= 120) {
                                status = SystemInfo.BetweenStationDelayStatus.DELAYED_TO_DELAYED;
                            } else if (toStationCircuit.getNumSecondsEstimatedTrackDelays() >= 60) {
                                status = SystemInfo.BetweenStationDelayStatus.DELAYED_TO_SLOW;
                            } else {
                                status = SystemInfo.BetweenStationDelayStatus.DELAYED_TO_OK;
                            }
                        } else if (fromStationCircuit.getNumSecondsEstimatedTrackDelays() >= 60) {
                            if (toStationCircuit.getNumSecondsEstimatedTrackDelays() >= 120) {
                                status = SystemInfo.BetweenStationDelayStatus.SLOW_TO_DELAYED;
                            } else if (toStationCircuit.getNumSecondsEstimatedTrackDelays() >= 60) {
                                status = SystemInfo.BetweenStationDelayStatus.SLOW_TO_SLOW;
                            } else {
                                status = SystemInfo.BetweenStationDelayStatus.SLOW_TO_OK;
                            }
                        } else {
                            if (toStationCircuit.getNumSecondsEstimatedTrackDelays() >= 120) {
                                status = SystemInfo.BetweenStationDelayStatus.OK_TO_DELAYED;
                            } else if (toStationCircuit.getNumSecondsEstimatedTrackDelays() >= 60) {
                                status = SystemInfo.BetweenStationDelayStatus.OK_TO_SLOW;
                            }
                        }
                        if (status != null) {
                            betweenStationDelayStatuses.put(fromStationCode + "_" + toStationCode, status);
                        }
                    }
                }
            }
        }

        // persist changes to our database
        this.trainStatusRepository.saveAll(trainStatusesMap.values());
        this.stationToStationTripRepository.saveAll(stationToStationTrips);
        this.trainOffloadRepository.saveAll(trainOffloads);
        this.trainDisappearanceRepository.saveAll(trainDisappearances);
        this.trainExpressedStationEventRepository.saveAll(trainExpressedStationEvents);
        this.duplicateTrainEventRepository.saveAll(duplicateTrainEvents);
        this.trainDepartureRepository.saveAll(observedTrainDepartures);

        this.trainStatusesMap = trainStatusesMap;
        this.stationTrainStatusesMap = stationTrainStatusesMap;
        this.betweenStationDelayStatuses = betweenStationDelayStatuses;
        if (this.lastUpdatedTimestamp == null) {
            this.lastUpdatedTimestamp = new AtomicLong();
        }
        this.lastUpdatedTimestamp.set(TimeUnit.MILLISECONDS.toSeconds(observedDate.getTime()));

        // TODO: disabled for now as it can make mistakes with regards to at least No Passenger destination codes
        // consider either fixing in the future or removing entirely, depending on how things go
//        updateDerivedLineCodeByDestinationIdMap();

        logger.info("...successfully updated train data from WMATA!");
    }

    @Scheduled(fixedDelay = 30000)  // every 30 seconds
    private void updateTrainDataOverLastHour() {
        logger.info("Updating train data over last hour...");

        String query = (
                "SELECT * " +
                "FROM ( " +
                "  SELECT DISTINCT ON (trip_id, track_circuit_id || '_' || lead(track_circuit_id) OVER (PARTITION BY trip_id ORDER BY observed_date)) " +
                "    line_code, direction_number, previous_station_code, location_station_code, distance_from_next_station, destination_station_name, real_train_id, eta, location_station_name, observed_date, trip_id " +
                "  FROM train_status " +
                "  WHERE observed_date >= (now() - INTERVAL '1 hour') " +
                "  ORDER BY trip_id, track_circuit_id || '_' || lead(track_circuit_id) OVER (PARTITION BY trip_id ORDER BY observed_date), observed_date " +
                ") AS t " +
                "ORDER BY observed_date"
        );

        this.trainDataOverLastHour = this.namedParameterJdbcTemplate.query(query, new BeanPropertyRowMapper<>(TrainStatusForMareyDiagram.class));

        logger.info("..successfully updated train data over last hour!");
    }

    @Scheduled(cron="0 10 * * * *")  // 10 minutes after the top of every hour
    // this should be invoked *after* buildStationTrackCircuitMap has been invoked
    // (due to dependency on this.stationTrackCircuitMap)
    private void buildStationScheduledTrainStatusesMap() {
        logger.info("Building station scheduled train statuses map...");

        Map<String, List<TrainStatus>> stationScheduledTrainStatusesMap = this.gtfsService.buildStationScheduledTrainStatusesMap();

        final Map<String, Integer> terminalStationDepartureDirectionMap = new HashMap<>();
        terminalStationDepartureDirectionMap.put("K08", 1);
        terminalStationDepartureDirectionMap.put("N12", 1);
        terminalStationDepartureDirectionMap.put("A15", 1);
        terminalStationDepartureDirectionMap.put("B11", 2);
        terminalStationDepartureDirectionMap.put("E10", 2);
        terminalStationDepartureDirectionMap.put("D13", 2);
        terminalStationDepartureDirectionMap.put("G05", 2);
        terminalStationDepartureDirectionMap.put("F11", 1);
        terminalStationDepartureDirectionMap.put("C15", 1);
        terminalStationDepartureDirectionMap.put("J03", 1);

        Map<String, List<TrainStatus>> terminalStationScheduledTrainStatusesMap = new HashMap<>(terminalStationDepartureDirectionMap.size());
        for (String terminalStationCode : terminalStationDepartureDirectionMap.keySet()) {
            terminalStationScheduledTrainStatusesMap.computeIfAbsent(terminalStationCode, k -> new ArrayList<>());
        }

        for (Map.Entry<String, List<TrainStatus>> entry : stationScheduledTrainStatusesMap.entrySet()) {
            String stationCode = entry.getKey();
            List<TrainStatus> trainStatuses = entry.getValue();

            for (TrainStatus trainStatus : trainStatuses) {
                TrackCircuit trackCircuit = this.stationTrackCircuitMap.get(trainStatus.getCurrentStationCode() + "_" + trainStatus.getDirectionNumber());

                trainStatus.setCurrentTrackCircuit(trackCircuit);
                trainStatus.setTrackCircuitId(trackCircuit.getId());
                trainStatus.setRawTrackCircuitId(trackCircuit.getId());

                if (terminalStationScheduledTrainStatusesMap.containsKey(stationCode)) {
                    int directionNumber = terminalStationDepartureDirectionMap.get(stationCode);
                    if (trainStatus.getDirectionNumber() == directionNumber) {
                        terminalStationScheduledTrainStatusesMap.get(stationCode).add(trainStatus);
                    }
                }
            }
        }

        this.stationScheduledTrainStatusesMap = stationScheduledTrainStatusesMap;
        this.terminalStationScheduledTrainStatusesMap = terminalStationScheduledTrainStatusesMap;

        logger.info("...station scheduled train statuses map built successfully!");

        if (this.terminalStationTrackCircuitIdSet == null) {
            this.terminalStationTrackCircuitIdSet = buildTerminalStationTrackCircuitIdSet(this.terminalStationScheduledTrainStatusesMap, this.stationTrackCircuitMap, this.stationToStationCircuitsMap);
        }
    }

    @Scheduled(fixedDelay = 3600000)    // every hour
    private void deleteOldTrainStatuses() {
        logger.info("Deleting any old train statuses...");
        this.trainStatusRepository.removeOld();
        logger.info("...deleted any old train statuses!");
    }

    @Scheduled(fixedDelay = 3600000)    // every hour
    private void deleteOldStationToStationTrips() {
        logger.info("Deleting any old station-to-station trips...");
        this.stationToStationTripRepository.removeOld();
        logger.info("...deleted any old station-to-station trips!");
    }

    @Scheduled(fixedDelay = 3600000)    // every hour
    private void deleteOldTripStates() {
        logger.info("Deleting any old trip states...");
        this.tripRepository.removeOld();
        logger.info("...deleted any old trip states!");
    }

    @Scheduled(fixedDelay = 60000)  // every minute
    private void deleteOldLastStationDepartureAndTripTimes() {
        logger.info("Hiding any old last station departures and trip times...");

        // after a certain amount of time of not seeing trains going from one station to another,
        // we have to assume trains are no longer servicing that trip

        Calendar anHourAgo = Calendar.getInstance();
        anHourAgo.add(Calendar.HOUR_OF_DAY, -1);

        Iterator<Map.Entry<String, Calendar>> itr2 = this.lastStationToStationTripTimeCalendarMap.entrySet().iterator();
        while (itr2.hasNext()) {
            Map.Entry<String, Calendar> entry = itr2.next();
            String key = entry.getKey();
            Calendar observed = entry.getValue();

            if (observed != null && observed.before(anHourAgo)) {
                itr2.remove();
                this.lastStationToStationTripTimeMap.remove(key);
                this.lastStationToStationTimeAtStationMap.remove(key);
            }
        }

        logger.info("...successfully hid any old last station departures and trip times!");
    }

    private List<TrainStatus> getCurrentScheduledTrainStatusesForStation(Calendar now, String stationCode, List<TrainStatus> realTimeTrainStatusesForStation) {
        List<TrainStatus> scheduledTrainStatuses = this.stationScheduledTrainStatusesMap.get(stationCode);
        if (scheduledTrainStatuses == null) {
            return null;
        }

        Map<String, Double> maximumRealTimeEtaByLineAndDirectionAndDestination = new HashMap<>();

        if (realTimeTrainStatusesForStation != null) {
            for (TrainStatus realTimeTrainStatusForStation : realTimeTrainStatusesForStation) {
                String key = String.join("_", realTimeTrainStatusForStation.getLine(), realTimeTrainStatusForStation.getDirectionNumber().toString(), realTimeTrainStatusForStation.getDestinationCode());
                Double maximumRealTimeEtaForLineAndDirection = maximumRealTimeEtaByLineAndDirectionAndDestination.get(key);
                if (maximumRealTimeEtaForLineAndDirection == null || realTimeTrainStatusForStation.getMinutesAway() > maximumRealTimeEtaForLineAndDirection) {
                    maximumRealTimeEtaByLineAndDirectionAndDestination.put(key, realTimeTrainStatusForStation.getMinutesAway());
                }
            }
        }

        List<TrainStatus> currentScheduledTrainStatuses = new ArrayList<>();

        Map<String, Double> previousScheduledTrainStatusMinutesBeforeDepartureByLineAndDirection = new HashMap<>();
        Map<String, Boolean> thresholdCaseHandledByLineAndDirection = new HashMap<>();
        for (TrainStatus trainStatus : scheduledTrainStatuses) {
            double minutesBeforeDeparture = ((trainStatus.getScheduledTime().getTimeInMillis() - now.getTimeInMillis()) / 1000d) / 60d;
            if (minutesBeforeDeparture < -60) {
                continue;
            }
            if (minutesBeforeDeparture > 60) {
                // scheduledTrainStatuses is sorted in ascending order by departure time, so we don't need to check the rest of the list
                break;
            }

            String key = String.join("_", trainStatus.getLine(), trainStatus.getDirectionNumber().toString(), trainStatus.getDestinationCode());

            boolean isDesiredScheduledTrainStatus;
            if (minutesBeforeDeparture < 0) {
                // don't show scheduled train departures that should have already happened
                isDesiredScheduledTrainStatus = false;
            } else {
                Double maximumRealTimeEtaForLineAndDirection = maximumRealTimeEtaByLineAndDirectionAndDestination.get(key);
                if (maximumRealTimeEtaForLineAndDirection == null) {
                    // show all future scheduled train departures if there are no real-time ETAs
                    isDesiredScheduledTrainStatus = true;
                } else if (minutesBeforeDeparture <= maximumRealTimeEtaForLineAndDirection) {
                    // don't show scheduled train departures for which we may have corresponding real-time ETAs
                    isDesiredScheduledTrainStatus = false;
                } else if (thresholdCaseHandledByLineAndDirection.get(key) != null && thresholdCaseHandledByLineAndDirection.get(key)) {
                    // show future scheduled train departures after the threshold case, if present, has been handled
                    isDesiredScheduledTrainStatus = true;
                } else {
                    Double previousScheduledTrainStatusMinutesBeforeDepartureForLineAndDirection = previousScheduledTrainStatusMinutesBeforeDepartureByLineAndDirection.get(key);
                    if (previousScheduledTrainStatusMinutesBeforeDepartureByLineAndDirection.get(key) != null) {
                        // the threshold case:
                        // don't show a scheduled train departure if it is too soon after a real-time ETA to be distinguished from that real-time ETA
                        double headway = minutesBeforeDeparture - previousScheduledTrainStatusMinutesBeforeDepartureForLineAndDirection;
                        double threshold = previousScheduledTrainStatusMinutesBeforeDepartureForLineAndDirection + (headway / 2);
                        isDesiredScheduledTrainStatus = (maximumRealTimeEtaForLineAndDirection < threshold);
                        thresholdCaseHandledByLineAndDirection.put(key, true);
                    } else {
                        // don't show the first scheduled train departure since the last 60 minutes (?)
                        isDesiredScheduledTrainStatus = false;
                    }
                }
            }
            if (isDesiredScheduledTrainStatus) {
                trainStatus.setMinutesAway(minutesBeforeDeparture);
                trainStatus.setObservedDate(now.getTime());
                currentScheduledTrainStatuses.add(trainStatus);
            }

            previousScheduledTrainStatusMinutesBeforeDepartureByLineAndDirection.put(key, minutesBeforeDeparture);
        }

        return currentScheduledTrainStatuses;
    }

    @Scheduled(fixedDelay = 300000)  // every 5 minutes
    // this should be invoked *after* buildStationToStationCircuitsMap has been invoked
    // (due to dependency on this.stationTrackCircuitMap)
    private void buildStationToStationMaps() {
        logger.info("Building station-to-station duration and trip maps...");

        Set<String> stationCodes = new HashSet<>();

        // load static defaults of neighboring station trip durations
        Set<StationToStationTravelTime> stationToStationTravelTimes = new HashSet<>();
        Scanner scanner = new Scanner(Thread.currentThread().getContextClassLoader().getResourceAsStream("station_durations.csv"));
        while (scanner.hasNextLine()) {
            String row = scanner.nextLine();
            String[] rowData = row.split(",");
            if (rowData.length != 3) {
                logger.error("Malformed row data in station_durations.csv: " + row);
                break;
            }

            String stationCode1 = rowData[0];
            String stationCode2 = rowData[1];
            Double duration;
            try {
                duration = Double.valueOf(rowData[2]);
            } catch (NumberFormatException e) {
                e.printStackTrace();
                break;
            }
            if (stationCode1 == null || stationCode2 == null) {
                break;
            }

            stationCodes.addAll(Arrays.asList(stationCode1, stationCode2));

            if (this.stationToStationTravelTimeRepository.findById(stationCode1 + "_" + stationCode2).orElse(null) == null) {
                stationToStationTravelTimes.add(new StationToStationTravelTime(stationCode1, stationCode2, null));
            }
            if (this.stationToStationTravelTimeRepository.findById(stationCode2 + "_" + stationCode1).orElse(null) == null) {
                stationToStationTravelTimes.add(new StationToStationTravelTime(stationCode2, stationCode1, null));
            }
        }
        scanner.close();
        this.stationToStationTravelTimeRepository.saveAll(stationToStationTravelTimes);

        // cache database table for easy access
        Map<String, StationToStationTravelTime> stationToStationInfoMap = new HashMap<>();
        Map<String, Double> stationToStationDurationMap = new HashMap<>();
        Map<String, Double> stationToStationMedianDurationMap = new HashMap<>();
        for (StationToStationTravelTime ststt : this.stationToStationTravelTimeRepository.findAll()) {
            stationToStationInfoMap.put(ststt.getStationCodesKey(), ststt);

            BigDecimal rawRecentAverageTripDuration = this.stationToStationTripRepository.getRecentAverageTripDuration(ststt.getFromStationCode(), ststt.getToStationCode());
            stationToStationDurationMap.put(ststt.getStationCodesKey(), (rawRecentAverageTripDuration != null) ? rawRecentAverageTripDuration.doubleValue() : 999d);

            BigDecimal rawMedianTripDuration = this.stationToStationTripRepository.getMedianTripDuration(ststt.getFromStationCode(), ststt.getToStationCode());
            stationToStationMedianDurationMap.put(ststt.getStationCodesKey(), (rawMedianTripDuration != null) ? rawMedianTripDuration.doubleValue() : 999d);
        }

        // derive trip durations for every combination of valid to/from stations using cached neighboring station trip durations
        Map<String, Double> derivedStationToStationDurationMap = new HashMap<>(stationToStationDurationMap);
        Map<String, Double> derivedStationToStationMedianDurationMap = new HashMap<>(stationToStationMedianDurationMap);
        Map<String, List<String>> stationToStationTripMap = new HashMap<>();
        for (String stationCode1 : stationCodes) {
            for (String stationCode2 : stationCodes) {
                Set<String> tripStationCodes = getStationCodes(stationCode1, stationCode2);
                Double duration = getStationToStationTripDuration(stationToStationDurationMap, tripStationCodes, stationCode1, stationCode2);
                if (duration == null) {
                    continue;
                }
                derivedStationToStationDurationMap.put(stationCode1 + "_" + stationCode2, duration);
                Double medianDuration = getStationToStationTripDuration(stationToStationMedianDurationMap, tripStationCodes, stationCode1, stationCode2);
                if (medianDuration == null) {
                    continue;
                }
                derivedStationToStationMedianDurationMap.put(stationCode1 + "_" + stationCode2, medianDuration);

                List<String> stationToStationTrip = getStationToStationTrip(stationToStationDurationMap, stationCode1, stationCode2, tripStationCodes);
                if (stationToStationTrip == null || stationToStationTrip.isEmpty()) {
                    // this should never happen
                    continue;
                }
                stationToStationTripMap.put(stationCode1 + "_" + stationCode2, stationToStationTrip);
            }
        }

        this.stationToStationInfoMap = stationToStationInfoMap;
        this.stationToStationDurationMap = derivedStationToStationDurationMap;
        this.stationToStationMedianDurationMap = derivedStationToStationMedianDurationMap;
        this.stationToStationTripMap = stationToStationTripMap;

        logger.info("...successfully built station-to-station duration and trip maps!");
    }
    private Double getStationToStationTripDuration(Map<String, Double> stationToStationDurationMap, Set<String> availableStationCodes, String fromStationCode, String toStationCode) {
        if (StringUtils.isEmpty(fromStationCode) || StringUtils.isEmpty(toStationCode) || fromStationCode.equals(toStationCode) || availableStationCodes == null || availableStationCodes.isEmpty()) {
            return null;
        }

        return getStationToStationTripDuration(stationToStationDurationMap, availableStationCodes, fromStationCode, toStationCode, 0, new HashSet<>());
    }
    private Double getStationToStationTripDuration(Map<String, Double> stationToStationDurationMap, Set<String> availableStationCodes, String fromStationCode, String toStationCode, double tripDuration, Set<String> visitedStations) {
        if (fromStationCode.equals(toStationCode)) {
            return tripDuration;
        }

        Set<String> newVisitedStations = new HashSet<>(visitedStations);
        newVisitedStations.add(fromStationCode);

        for (String stationCode : availableStationCodes) {
            if (newVisitedStations.contains(stationCode)) {
                continue;
            }

            Double additionalTripDuration = stationToStationDurationMap.get(fromStationCode + "_" + stationCode);
            if (additionalTripDuration == null) {
                continue;
            }

            Set<String> newAvailableStationCodes = new HashSet<>(availableStationCodes);
            newAvailableStationCodes.remove(stationCode);

            // factor in estimated station boarding time
            if (newVisitedStations.size() > 1) {
                additionalTripDuration += 1d;
            } else {
                // at any given time, we can expect a train boarding at its current station to be there for about
                // half the time we normally factor in for boarding time (assuming a normal distribution)
                additionalTripDuration += 0.5d;
            }

            Double totalTripDuration = getStationToStationTripDuration(stationToStationDurationMap, newAvailableStationCodes, stationCode, toStationCode, tripDuration + additionalTripDuration, newVisitedStations);
            if (totalTripDuration == null) {
                continue;
            }

            return totalTripDuration;
        }

        return null;
    }
    private List<String> getStationToStationTrip(Map<String, Double> stationToStationDurationMap, String fromStationCode, String toStationCode, Set<String> availableStationCodes) {
        if (StringUtils.isEmpty(fromStationCode) || StringUtils.isEmpty(toStationCode) || fromStationCode.equals(toStationCode) || availableStationCodes == null || availableStationCodes.isEmpty()) {
            return null;
        }

        return getStationToStationTrip(stationToStationDurationMap, fromStationCode, toStationCode, availableStationCodes, new ArrayList<>());
    }
    private List<String> getStationToStationTrip(Map<String, Double> stationToStationDurationMap, String fromStationCode, String toStationCode, Set<String> availableStationCodes, List<String> visitedStationCodes) {
        List<String> newVisitedStationCodes = new ArrayList<>(visitedStationCodes);
        newVisitedStationCodes.add(fromStationCode);

        if (fromStationCode.equals(toStationCode)) {
            return newVisitedStationCodes;
        }

        for (String stationCode : availableStationCodes) {
            if (newVisitedStationCodes.contains(stationCode)) {
                continue;
            }

            Double additionalTripDuration = stationToStationDurationMap.get(fromStationCode + "_" + stationCode);
            if (additionalTripDuration == null) {
                continue;
            }

            Set<String> newAvailableStationCodes = new HashSet<>(availableStationCodes);
            newAvailableStationCodes.remove(stationCode);

            List<String> stationToStationTrip = getStationToStationTrip(stationToStationDurationMap, stationCode, toStationCode, newAvailableStationCodes, newVisitedStationCodes);
            if (stationToStationTrip == null) {
                continue;
            }

            return stationToStationTrip;
        }

        return null;
    }

    @Scheduled(fixedDelay = 300000)  // every 5 minutes
    private void buildDestinationCodeMap() {
        logger.info("Building destination code map...");

        Map<DestinationCodeMappingPrimaryKey, DestinationCodeMapping> destinationCodeMap = new HashMap<>();

        for (DestinationCodeMapping destinationCodeMapping : this.destinationCodeMappingRepository.findAll()) {
            destinationCodeMap.put(new DestinationCodeMappingPrimaryKey(destinationCodeMapping.getDestinationCode(), destinationCodeMapping.getDirectionNumber()), destinationCodeMapping);
        }

        this.destinationCodeMap = destinationCodeMap;

        logger.info("...successfully built destination code map!");
    }

    private Map<Integer, TrackCircuitInfo> buildTrackCircuitInfoMap() {
        logger.info("Building track circuit info map...");
        this.trackCircuitInfoRepository.deleteAll();

        Map<Integer, TrackCircuitInfo> trackCircuitInfoMap = new HashMap<>();
        String fileName = "Track_Circuits.csv";
        Scanner scanner = new Scanner(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName)));
        scanner.next();  // skip first row, which are just column headers
        while (scanner.hasNextLine()) {
            String row = scanner.nextLine();
            if (StringUtils.isEmpty(row)) {
                continue;
            }

            String[] rowData = row.split(",", -1);
            if (rowData.length != 12) {
                logger.error("Malformed row data in " + fileName + ": " + row);
                continue;
            }

            if (StringUtils.isEmpty(rowData[0])) {
                logger.warn("Missing API ID in " + fileName + ": " + row);
                continue;
            }

            if (StringUtils.isEmpty(rowData[10])) {
                logger.warn("Skipping processing of track circuit data with no length: " + row);
                continue;
            }

            Integer apiId = Integer.valueOf(rowData[0]);
            String trackId = rowData[1];
            Double fromChainMarker = null;  // maybe someday we'll get these back
            Double toChainMarker = null;  // maybe someday we'll get these back
            Double length = Double.valueOf(rowData[10]);
            String trackName = rowData[8] + rowData[2];

            TrackCircuitInfo trackCircuitInfo = new TrackCircuitInfo(apiId, trackId, fromChainMarker, toChainMarker, length, trackName);
            trackCircuitInfoMap.put(trackCircuitInfo.getApiId(), trackCircuitInfo);
        }
        scanner.close();

        this.trackCircuitInfoRepository.saveAll(trackCircuitInfoMap.values());
        logger.info("...track circuit info map built successfully!");
        return trackCircuitInfoMap;
    }

    private Map<Integer, TrackCircuit> buildTrackCircuitMap() {
        logger.info("Building track circuit map...");

        // parse source JSON file
        Gson gson = new Gson();
        JsonReader reader = new JsonReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/StandardRoutes.json"))));
        StandardRoutes standardRoutes = gson.fromJson(reader, StandardRoutes.class);

        // actually build it

        Map<Integer, TrackCircuit> trackCircuitMap = new HashMap<>();

        for (StandardRoute sr : standardRoutes.getStandardRoutes()) {
            String lineCode = sr.getLineCode();
            if (lineCode.equals("YLRP")) {
                continue;
            }

            for (int i = 0, j = 1, k = 2; i == (j - 1) && j == (k - 1) && k < sr.getTrackCircuits().size(); i++, j++, k++) {
                StandardRouteTrackCircuit parentSrtc = sr.getTrackCircuits().get(i);
                Integer parentTrackCircuitId = parentSrtc.getCircuitId();
                TrackCircuit parentTrackCircuit = trackCircuitMap.get(parentTrackCircuitId);
                if (parentTrackCircuit == null) {
                    parentTrackCircuit = new TrackCircuit(parentTrackCircuitId, sr.getTrackNum(), parentSrtc.getStationCode(), new HashSet<>(Collections.singletonList(lineCode)));
                    trackCircuitMap.put(parentTrackCircuit.getId(), parentTrackCircuit);
                }
                parentTrackCircuit.getLineCodes().add(lineCode);

                StandardRouteTrackCircuit currentSrtc = sr.getTrackCircuits().get(j);
                Integer currentTrackCircuitId = currentSrtc.getCircuitId();
                TrackCircuit currentTrackCircuit = trackCircuitMap.get(currentTrackCircuitId);
                if (currentTrackCircuit == null) {
                    currentTrackCircuit = new TrackCircuit(currentTrackCircuitId, sr.getTrackNum(), currentSrtc.getStationCode(), new HashSet<>(Collections.singletonList(lineCode)));
                    trackCircuitMap.put(currentTrackCircuit.getId(), currentTrackCircuit);
                }
                currentTrackCircuit.getLineCodes().add(lineCode);

                StandardRouteTrackCircuit childSrtc = sr.getTrackCircuits().get(k);
                Integer childTrackCircuitId = childSrtc.getCircuitId();
                TrackCircuit childTrackCircuit = trackCircuitMap.get(childTrackCircuitId);
                if (childTrackCircuit == null) {
                    childTrackCircuit = new TrackCircuit(childTrackCircuitId, sr.getTrackNum(), childSrtc.getStationCode(), new HashSet<>(Collections.singletonList(lineCode)));
                    trackCircuitMap.put(childTrackCircuit.getId(), childTrackCircuit);
                }
                childTrackCircuit.getLineCodes().add(lineCode);

                parentTrackCircuit.getChildNeighbors().add(currentTrackCircuit);
                currentTrackCircuit.getParentNeighbors().add(parentTrackCircuit);
                currentTrackCircuit.getChildNeighbors().add(childTrackCircuit);
                childTrackCircuit.getParentNeighbors().add(currentTrackCircuit);
            }
        }

        FileWriter fileWriter;
        try {
            fileWriter = new FileWriter("track-circuit-diagram.gv.txt");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        PrintWriter printWriter = new PrintWriter(fileWriter);
        printWriter.println(
                "digraph mygraph {\n" +
                "  fontname=\"Helvetica,Arial,sans-serif\"\n" +
                "  node [fontname=\"Helvetica,Arial,sans-serif\"]\n" +
                "  edge [fontname=\"Helvetica,Arial,sans-serif\"]\n" +
                "  node [shape=box];"
        );

        // now that the circuit map is built, cache information about neighboring stations
        for (TrackCircuit tc : trackCircuitMap.values()) {
            printWriter.println("  \"" + tc.getId() + ((tc.getStationCode() != null) ? " (" + tc.getStationCode() + ")" : "") + "\"");

            for (TrackCircuit parentNeighbor : tc.getParentNeighbors()) {
                tc.getParentStationCodes().put(parentNeighbor, parentNeighbor.findParentStationCodes());
                tc.getNextParentStationCodes().addAll(parentNeighbor.findNextParentStationCodes());
            }
            for (TrackCircuit childNeighbor : tc.getChildNeighbors()) {
                printWriter.println("  \"" + tc.getId() + ((tc.getStationCode() != null) ? " (" + tc.getStationCode() + ")" : "") + "\"" + " -> " + "\"" + childNeighbor.getId() + ((childNeighbor.getStationCode() != null) ? " (" + childNeighbor.getStationCode() + ")" : "") + "\"");

                tc.getChildStationCodes().put(childNeighbor, childNeighbor.findChildStationCodes());
                tc.getNextChildStationCodes().addAll(childNeighbor.findNextChildStationCodes());
            }
        }

        printWriter.println("}");
        printWriter.close();

        // example of generating a SVG from track-circuit-diagram.gv.txt using graphviz:

        // sudo apt install graphviz*
        // dot -Tsvg /home/jpizzurro/metrohero-server/track-circuit-diagram.gv.txt > /home/jpizzurro/metrohero-server/track-circuit-diagram.svg

        logger.info("...track circuit map built successfully!");
        return trackCircuitMap;
    }

    private Map<String, List<Integer>> buildStationToStationCircuitsMap() {
        logger.info("Building station-to-station circuits map...");

        // parse source JSON file
        Gson gson = new Gson();
        JsonReader reader = new JsonReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/StandardRoutes.json"))));
        StandardRoutes standardRoutes = gson.fromJson(reader, StandardRoutes.class);

        // actually build it

        Map<String, List<Integer>> stationToStationCircuitsMap = new HashMap<>();

        for (StandardRoute sr : standardRoutes.getStandardRoutes()) {
            String lastStationCode = null;
            List<Integer> trackCircuitsSinceLastStation = new ArrayList<>();
            for (StandardRouteTrackCircuit srtc : sr.getTrackCircuits()) {
                if (srtc.getStationCode() == null) {
                    trackCircuitsSinceLastStation.add(srtc.getCircuitId());
                } else {
                    if (lastStationCode != null) {
                        stationToStationCircuitsMap.put(lastStationCode + "_" + srtc.getStationCode() + "_" + sr.getTrackNum(), new ArrayList<>(trackCircuitsSinceLastStation));
                        List<Integer> reversedList = new ArrayList<>(trackCircuitsSinceLastStation);
                        Collections.reverse(reversedList);
                        stationToStationCircuitsMap.put(srtc.getStationCode() + "_" + lastStationCode + "_" + sr.getTrackNum(), reversedList);
                    }

                    lastStationCode = srtc.getStationCode();
                    trackCircuitsSinceLastStation = new ArrayList<>();
                }
            }
        }

        logger.info("...station-to-station circuits map built successfully!");
        return stationToStationCircuitsMap;
    }

    private Map<String, TrackCircuit> buildStationTrackCircuitMap(Map<Integer, TrackCircuit> trackCircuitMap) {
        logger.info("Building station track circuit map...");

        // parse source JSON file
        Gson gson = new Gson();
        JsonReader reader = new JsonReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/StandardRoutes.json"))));
        StandardRoutes standardRoutes = gson.fromJson(reader, StandardRoutes.class);

        // actually build it

        Map<String, TrackCircuit> stationTrackCircuitMap = new HashMap<>();

        for (StandardRoute sr : standardRoutes.getStandardRoutes()) {
            sr.getTrackCircuits().stream().filter(srtc -> srtc.getStationCode() != null).forEach(srtc -> stationTrackCircuitMap.put(srtc.getStationCode() + "_" + sr.getTrackNum(), trackCircuitMap.get(srtc.getCircuitId())));
        }

        logger.info("...station track circuit map built successfully!");
        return stationTrackCircuitMap;
    }

    private Set<String> buildStationCodesSet() {
        logger.info("Building station codes set...");

        // parse source JSON file
        Gson gson = new Gson();
        JsonReader reader = new JsonReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/StandardRoutes.json"))));
        StandardRoutes standardRoutes = gson.fromJson(reader, StandardRoutes.class);

        // actually build it

        Set<String> stationCodesSet = new HashSet<>();

        for (StandardRoute sr : standardRoutes.getStandardRoutes()) {
            sr.getTrackCircuits().stream().filter(srtc -> srtc.getStationCode() != null).forEach(srtc -> stationCodesSet.add(srtc.getStationCode()));
        }

        logger.info("...station codes set built successfully!");
        return stationCodesSet;
    }

    private Set<Integer> buildTerminalStationTrackCircuitIdSet(Map<String, List<TrainStatus>> terminalStationScheduledTrainStatusesMap, Map<String, TrackCircuit> stationTrackCircuitMap, Map<String, List<Integer>> stationToStationCircuitsMap) {
        // there is a known issue where sometimes, trains appear to occupy the circuit one circuit beyond that of the station they're boarding at
        // we want to consider those circuits terminal station circuits as well, so that's what this function is for

        logger.info("Building terminal station track circuit id set...");

        Set<Integer> terminalStationTrackCircuitIdSet = new HashSet<>();

        Set<String> terminalStationCodes = terminalStationScheduledTrainStatusesMap.keySet();
        for (String terminalStationCode : terminalStationCodes) {
            TrackCircuit terminalStationTrack1Circuit = stationTrackCircuitMap.get(terminalStationCode + "_1");
            if (terminalStationTrack1Circuit != null) {
                terminalStationTrackCircuitIdSet.add(terminalStationTrack1Circuit.getId());
                terminalStationTrack1Circuit.getChildNeighbors().forEach(k -> terminalStationTrackCircuitIdSet.add(k.getId()));
                terminalStationTrack1Circuit.getParentNeighbors().forEach(k -> terminalStationTrackCircuitIdSet.add(k.getId()));
            }
            TrackCircuit terminalStationTrack2Circuit = stationTrackCircuitMap.get(terminalStationCode + "_2");
            if (terminalStationTrack2Circuit != null) {
                terminalStationTrackCircuitIdSet.add(terminalStationTrack2Circuit.getId());
                terminalStationTrack2Circuit.getChildNeighbors().forEach(k -> terminalStationTrackCircuitIdSet.add(k.getId()));
                terminalStationTrack2Circuit.getParentNeighbors().forEach(k -> terminalStationTrackCircuitIdSet.add(k.getId()));
            }
        }

        logger.info("...terminal station track circuit id set built successfully!");
        return terminalStationTrackCircuitIdSet;
    }

    private Double getMinDistanceCovered(TrackCircuit fromTrackCircuit, String nextStationCode) {
        if (fromTrackCircuit == null || nextStationCode == null) {
            return null;
        }

        TrackCircuit toTrackCircuit = this.stationTrackCircuitMap.get(nextStationCode + "_" + fromTrackCircuit.getTrackNumber());
        return getMinDistanceCovered(fromTrackCircuit, toTrackCircuit);
    }
    private Double getMinDistanceCovered(TrackCircuit fromTrackCircuit, TrackCircuit toTrackCircuit) {
        if (fromTrackCircuit == null || toTrackCircuit == null) {
            return null;
        }

        Double minDistanceCovered = getMinDistanceCovered(fromTrackCircuit, toTrackCircuit, true, new HashSet<>(), new HashSet<>(), 0);
        if (minDistanceCovered == null) {
            minDistanceCovered = getMinDistanceCovered(fromTrackCircuit, toTrackCircuit, false, new HashSet<>(), new HashSet<>(), 0);
        }

        return minDistanceCovered;
    }
    private Double getMinDistanceCovered(TrackCircuit fromTrackCircuit, TrackCircuit toTrackCircuit, boolean visitByChildren, Set<Integer> visitedTrackCircuitApiIds, Set<String> visitedTrackCircuitTrackIds, double distanceCovered) {
        if (fromTrackCircuit == null || toTrackCircuit == null) {
            return null;
        }

        if (fromTrackCircuit.equals(toTrackCircuit)) {
            return distanceCovered;
        }

        // traverse track circuits by API ID, but only cover distance by track ID
        // (this is because different API IDs can map to the same physical track ID)

        Set<Integer> newVisitedTrackCircuitApiIds = new HashSet<>(visitedTrackCircuitApiIds);
        newVisitedTrackCircuitApiIds.add(fromTrackCircuit.getId());

        Set<String> newVisitedTrackCircuitTrackIds = new HashSet<>(visitedTrackCircuitTrackIds);
        Double newDistanceCovered = distanceCovered;
        TrackCircuitInfo fromTrackCircuitInfo = this.trackCircuitInfoMap.get(fromTrackCircuit.getId());
        if (fromTrackCircuitInfo != null && !newVisitedTrackCircuitTrackIds.contains(fromTrackCircuitInfo.getTrackId())) {
            newVisitedTrackCircuitTrackIds.add(fromTrackCircuitInfo.getTrackId());
            newDistanceCovered += fromTrackCircuitInfo.getLength();
        }

        Set<TrackCircuit> neighbors;
        if (visitByChildren) {
            neighbors = fromTrackCircuit.getChildNeighbors();
        } else {
            neighbors = fromTrackCircuit.getParentNeighbors();
        }
        for (TrackCircuit neighboringTrackCircuit : neighbors) {
            if (newVisitedTrackCircuitApiIds.contains(neighboringTrackCircuit.getId())) {
                continue;
            }

            Double totalDistanceCovered = getMinDistanceCovered(neighboringTrackCircuit, toTrackCircuit, visitByChildren, newVisitedTrackCircuitApiIds, newVisitedTrackCircuitTrackIds, newDistanceCovered);
            if (totalDistanceCovered == null) {
                continue;
            }

            return totalDistanceCovered;
        }

        return null;
    }

    public Double getPredictedRideTime(Calendar now, String fromStationCode, String toStationCode, TrainStatus trainStatus) {
        if (now == null || fromStationCode == null || toStationCode == null) {
            return null;
        }

        if (trainStatus != null && (trainStatus.getMinutesAway() == null || trainStatus.getMaxMinutesAway() == null)) {
            return null;
        }

        List<String> tripStationCodes = this.stationToStationTripMap.get(String.join("_", fromStationCode, toStationCode));
        if (tripStationCodes == null) {
            return null;
        }

        Double predictedRideTime = null;
        Double predictedRideTimeToFirstStationStop = null;

        for (int i = 0, j = 1; j < tripStationCodes.size(); i++, j++) {
            String tripStation1Code = tripStationCodes.get(i);
            String tripStation2Code = tripStationCodes.get(j);

            String key = String.join("_", tripStation1Code, tripStation2Code);
            Double lastTripTime = this.lastStationToStationTripTimeMap.get(key);
            if (lastTripTime != null) {
                if (predictedRideTime == null) {
                    predictedRideTime = 0d;
                }

                Double lastTimeAtStation = (i != 0) ? this.lastStationToStationTimeAtStationMap.get(key) : 0d;
                if (lastTimeAtStation == null) {
                    lastTimeAtStation = 0d;
                }

                Double currentRunningTripTime = null;

                List<TrainStatus> tripStation2TrainStatuses = this.stationTrainStatusesMap.get(tripStation2Code);   // already sorted by ETA in ascending order
                if (tripStation2TrainStatuses != null) {
                    for (TrainStatus tripStation2TrainStatus : tripStation2TrainStatuses) {
                        if (tripStation2TrainStatus.isKeyedDown() || tripStation2TrainStatus.wasKeyedDown() || tripStation2TrainStatus.getLastVisitedStation() == null) {
                            continue;
                        }

                        if (tripStation1Code.equals(tripStation2TrainStatus.getLastVisitedStationCode()) && (tripStation1Code.equals(tripStation2TrainStatus.getLocationCode()) || tripStation2Code.equals(tripStation2TrainStatus.getLocationCode()))) {
                            // a train headed to tripStation2 is either at tripStation1 or between tripStation1 and tripStation2
                            Double minutesAtLastVisitedStation = (i != 0 && tripStation2TrainStatus.getSecondsAtLastVisitedStation() != null) ? (tripStation2TrainStatus.getSecondsAtLastVisitedStation() / 60d /* seconds -> minutes */) : 0d;
                            Double minutesSinceLastVisitedStation = ((now.getTimeInMillis() - tripStation2TrainStatus.getLastVisitedStation().getTime()) / 1000d) / 60d;    // milliseconds -> minutes
                            currentRunningTripTime = minutesAtLastVisitedStation + minutesSinceLastVisitedStation;
                            break;
                        }
                    }
                }

                Double additionalRideTime = Math.max(lastTripTime + lastTimeAtStation, (currentRunningTripTime != null) ? currentRunningTripTime : 0);

                if (trainStatus != null && predictedRideTimeToFirstStationStop == null) {
                    predictedRideTimeToFirstStationStop = additionalRideTime;
                }

                predictedRideTime += additionalRideTime;
            } else {
                predictedRideTime = null;
                break;
            }
        }

        if (trainStatus != null && predictedRideTime != null) {
            // this train is already some of the way to its destination, so linearly interpolate away some of the predicted ride time we had previously calculated
            predictedRideTime -= (1 - Math.min(Math.max(trainStatus.getMinutesAway() / trainStatus.getMaxMinutesAway(), 0), 1)) * predictedRideTimeToFirstStationStop;
        }

        return predictedRideTime;
    }

    private void updateDerivedLineCodeByDestinationIdMap() {
        try {
            // fetch train predictions from WMATA for analysis and comparison to our own train predictions

            ResponseEntity<TrainPredictions> responseEntity;
            String url = "https://api.wmata.com/beta/StationPrediction.svc/json/GetPrediction/All";
            HttpEntity<String> requestEntity = NetworkUtil.createNewHttpEntity(this.configUtil.getWmataApiKey());
            RestTemplate restTemplate;
            try {
                restTemplate = NetworkUtil.createNewRestTemplate(true);
                responseEntity = restTemplate.exchange(url, HttpMethod.GET, requestEntity, TrainPredictions.class);
            } catch (RestClientException e) {
                restTemplate = NetworkUtil.createNewRestTemplate(false);
                responseEntity = restTemplate.exchange(url, HttpMethod.GET, requestEntity, TrainPredictions.class);
                logger.warn("Response from WMATA Train Predictions API not gzipped!");
            }

            if (responseEntity.getBody() == null || responseEntity.getBody().getTrains() == null || responseEntity.getBody().getTrains().isEmpty()) {
                // no train predictions available from WMATA
                return;
            }

            if (responseEntity.getBody().equals(this.previousTrainPredictions)) {
                // WMATA's train predictions are the same as the last time we requested them
                return;
            }

            this.previousTrainPredictions = responseEntity.getBody();

            // match up each train that is boarding at a station in WMATA's train predictions to a train that is boarding at that same station in our train predictions; assume these are two different predictions for the same train
            // for every match, globally assign the destination ID from the train in our train predictions to the line code from the train in WMATA's train predictions

            Map<String, String> derivedLineCodeByDestinationId = new HashMap<>();

            for (TrainPrediction trainPrediction : responseEntity.getBody().getTrains()) {
                if (!"BRD".equals(trainPrediction.getMin())) {
                    continue;
                }

                if (trainPrediction.getLocationCode() == null || !StationUtil.getStationCodeMap().keySet().contains(trainPrediction.getLocationCode())) {
                    continue;
                }

                if (trainPrediction.getLine() == null || !Arrays.asList("RD", "OR", "SV", "BL", "YL", "GR").contains(trainPrediction.getLine())) {
                    continue;
                }

                List<TrainStatus> trainStatuses = this.stationTrainStatusesMap.get(trainPrediction.getLocationCode());
                if (trainStatuses == null) {
                    continue;
                }

                for (TrainStatus trainStatus : trainStatuses) {
                    if (!"BRD".equals(trainStatus.getMin())) {
                        continue;
                    }

                    if (!trainStatus.getDirectionNumber().toString().equals(trainPrediction.getGroup())) {
                        continue;
                    }

                    // found a match

                    if (trainStatus.getDestinationId() == null) {
                        break;
                    }

                    if (derivedLineCodeByDestinationId.containsKey(trainStatus.getDestinationId())) {
                        // TODO: what if the destination ID of one train maps to a different line code than another train with the same destination ID in the same tick? in such a scenario, which mapping should be used? is this scenario even plausible in practice?
                        // right now, for consistency between ticks, we're always taking the first mapping in the natural iteration order of both prediction lists
                        break;
                    }

                    derivedLineCodeByDestinationId.put(trainStatus.getDestinationId(), trainPrediction.getLine());
                    break;
                }
            }

            this.derivedLineCodeByDestinationId.putAll(derivedLineCodeByDestinationId);
        } catch (Exception e) {
            logger.warn("Failed to update derived line code by destination ID map!", e);
        }
    }

    public Map<String, TrainStatus> getTrainStatusesMap() {
        return trainStatusesMap;
    }

    public Map<String, List<TrainStatus>> getStationTrainStatusesMap() {
        return stationTrainStatusesMap;
    }

    public Map<String, SystemInfo.BetweenStationDelayStatus> getBetweenStationDelayStatuses() {
        return betweenStationDelayStatuses;
    }

    public Set<String> getStationCodesSet() {
        return stationCodesSet;
    }

    public Map<String, TrackCircuit> getStationTrackCircuitMap() {
        return stationTrackCircuitMap;
    }

    public Map<String, ArrivalInfo> getLastStationArrivalMap() {
        return lastStationArrivalMap;
    }

    public Map<String, String> getCrowdingStatusByStation() {
        return crowdingStatusByStation;
    }

    public Long getLastUpdatedTimestamp() {
        return (lastUpdatedTimestamp != null) ? lastUpdatedTimestamp.get() : null;
    }

    public List<TrainStatusForMareyDiagram> getTrainDataOverLastHour() {
        return trainDataOverLastHour;
    }

    public Set<Integer> getTerminalStationTrackCircuitIdSet() {
        return terminalStationTrackCircuitIdSet;
    }

    public Map<String, Double> getStationToStationMedianDurationMap() {
        return stationToStationMedianDurationMap;
    }

    public Map<String, List<String>> getStationToStationTripMap() {
        return stationToStationTripMap;
    }

    public Map<String, DepartureInfo> getLastStationDepartureMap() {
        return lastStationDepartureMap;
    }
}

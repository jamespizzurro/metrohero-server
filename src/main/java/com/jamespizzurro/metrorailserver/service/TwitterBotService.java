package com.jamespizzurro.metrorailserver.service;

import com.jamespizzurro.metrorailserver.ConfigUtil;
import com.jamespizzurro.metrorailserver.StationUtil;
import com.jamespizzurro.metrorailserver.domain.ArrivalInfo;
import com.jamespizzurro.metrorailserver.domain.ServiceGap;
import com.jamespizzurro.metrorailserver.domain.TrainExpressedStationEvent;
import com.jamespizzurro.metrorailserver.domain.TrainStatus;
import com.jamespizzurro.metrorailserver.repository.SystemMetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import twitter4j.*;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class TwitterBotService {

    private static final Logger logger = LoggerFactory.getLogger(TwitterBotService.class);
    private static final int TWEET_CHARACTER_LIMIT = 280;
    private static final int REMAINING_TWEET_THRESHOLD = 15;  // defines how close to Twitter's tweet limit we get before we stop tweeting temporarily

    private final ConfigUtil configUtil;
    private final TrainTaggingService trainTaggingService;
    private final GtfsService gtfsService;
    private final SystemMetricsRepository systemMetricsRepository;

    private AtomicBoolean isDataStale;
    private AtomicLong timeDataLastWentStale;
    private AtomicBoolean isQueueSuspended;
    private Deque<TweetData> tweetQueue;
    private AtomicReference<RateLimitStatus> postingRateLimitStatus;

    private Map<String, ArrivalInfo> tweetedArrivalInfoByStationAndLineAndDirection;
    private Map<ServiceGap, Calendar> recentlyTweetedServiceGaps;

    @Autowired
    public TwitterBotService(ConfigUtil configUtil, TrainTaggingService trainTaggingService, GtfsService gtfsService, SystemMetricsRepository systemMetricsRepository) {
        this.configUtil = configUtil;
        this.trainTaggingService = trainTaggingService;
        this.gtfsService = gtfsService;
        this.systemMetricsRepository = systemMetricsRepository;
    }

    @PostConstruct
    private void init() {
        logger.info("Initializing twitter bot service...");

        this.isDataStale = new AtomicBoolean(false);
        this.timeDataLastWentStale = new AtomicLong(0);
        this.isQueueSuspended = new AtomicBoolean(false);
        this.tweetQueue = new ConcurrentLinkedDeque<>();
        this.postingRateLimitStatus = new AtomicReference<>();

        this.tweetedArrivalInfoByStationAndLineAndDirection = new HashMap<>();
        this.recentlyTweetedServiceGaps = new ConcurrentHashMap<>();

        logger.info("...twitter bot service initialized!");
    }

    private String getOutageDurationString(long timeDataLastWentStale) {
        long timeDataLastWentStaleInSeconds = Math.round(timeDataLastWentStale / 1000.0);
        if ((timeDataLastWentStaleInSeconds / 60.0) < 1) {
            return "less than a minute";
        } else {
            long timeDataLastWentStaleInMinutes = Math.round(timeDataLastWentStale / 1000.0 / 60.0);
            return "about " + timeDataLastWentStaleInMinutes + " minute" + ((timeDataLastWentStaleInMinutes == 1) ? "" : "s");
        }
    }

    private boolean shouldExpectTrains() {
        boolean shouldExpectTrains = false;

        for (String lineCode : Arrays.asList("RD", "OR", "SV", "BL", "YL", "GR")) {
            for (Integer directionNumber : Arrays.asList(1, 2)) {
                for (String stationCode : StationUtil.getStationCodeMap().keySet()) {
                    if (this.gtfsService.getExpectedTrainFrequency(lineCode, directionNumber, stationCode) != null) {
                        shouldExpectTrains = true;
                        break;
                    }
                }
            }
        }

        return shouldExpectTrains;
    }

    public void setIsDataStale(Boolean isDataStale) {
        Calendar now = Calendar.getInstance();
        boolean wasDataStale = this.isDataStale.get();
        if (!wasDataStale && isDataStale) {
            this.timeDataLastWentStale.set(now.getTimeInMillis());

            if (shouldExpectTrains()) {
                sendTweet("MetroHero is unable to connect to WMATA's real-time data feeds. Tweets will automatically resume once this is resolved. (" + getTimestamp(now.getTime()) + ")");
            }
        } else if (wasDataStale && !isDataStale) {
            if (shouldExpectTrains()) {
                long timeDataLastWentStale = this.timeDataLastWentStale.get();
                sendTweet("MetroHero's connection to WMATA's real-time data feeds has been re-established after an outage that lasted " + getOutageDurationString(now.getTimeInMillis() - timeDataLastWentStale) + ". Tweets will resume now. (" + getTimestamp(now.getTime()) + ")");
            }
        }

        this.isDataStale.set(isDataStale);
    }

    public void tweetTrainHolding(TrainStatus trainStatus, String currentOrNextStationCode, Integer distanceFromNextStation) {
        if (this.isDataStale.get()) {
            return;
        }

        if (trainStatus == null || StringUtils.isEmpty(currentOrNextStationCode) || distanceFromNextStation == null) {
            return;
        }

        if ("N/A".equals(trainStatus.getLine()) || "N/A".equals(trainStatus.getDestination()) || "No Passenger".equals(trainStatus.getDestination())) {
            // don't tweet about trains with insufficient data
            return;
        }

        String distanceRemainingText = (distanceFromNextStation <= 0) ? "at" : "about " + NumberFormat.getIntegerInstance().format(distanceFromNextStation) + "ft from";
        String direction = StationUtil.getDirectionName(trainStatus.getLine(), trainStatus.getDirectionNumber());
        String tweetTextPre = ((direction != null) ? (direction + " ") : "") + ((trainStatus.getCar() != null && !trainStatus.getCar().equals("N/A")) ? (trainStatus.getCar() + "-car ") : "") + trainStatus.getLine() + "/" + trainStatus.getDestination() + " Train " + trainStatus.getRealTrainId() + " has been holding " + distanceRemainingText + " ";
        String tweetTextPost = " on track " + trainStatus.getTrackNumber() + " for at least " + TimeUnit.SECONDS.toMinutes(trainStatus.getSecondsSinceLastMoved()) + " mins. (" + getTimestamp(trainStatus.getObservedDate()) + ")";

        int maxLength = TWEET_CHARACTER_LIMIT - (tweetTextPre.length() + tweetTextPost.length());
        String optimalStationName = StationUtil.getOptimalStationName(currentOrNextStationCode, maxLength);
        if (optimalStationName == null) {
            logger.warn("Unable to tweet about Train " + trainStatus.getRealTrainId() + " holding near " + currentOrNextStationCode + " due to insufficient available characters!");
            return;
        }

        queueTweet(tweetTextPre + optimalStationName + tweetTextPost, trainStatus.getLine());
    }

    public void tweetTrainDelayed(TrainStatus trainStatus, String currentOrNextStationCode, Integer distanceFromNextStation) {
        if (this.isDataStale.get()) {
            return;
        }

        if (trainStatus == null || StringUtils.isEmpty(currentOrNextStationCode) || distanceFromNextStation == null) {
            return;
        }

        if ("N/A".equals(trainStatus.getLine()) || "N/A".equals(trainStatus.getDestination()) || "No Passenger".equals(trainStatus.getDestination())) {
            // don't tweet about trains with insufficient data
            return;
        }

        String distanceRemainingText = (distanceFromNextStation <= 0) ? "at" : "about " + NumberFormat.getIntegerInstance().format(distanceFromNextStation) + "ft from";
        String direction = StationUtil.getDirectionName(trainStatus.getLine(), trainStatus.getDirectionNumber());
        String tweetTextPre = ((direction != null) ? (direction + " ") : "") + ((trainStatus.getCar() != null && !trainStatus.getCar().equals("N/A")) ? (trainStatus.getCar() + "-car ") : "") + trainStatus.getLine() + "/" + trainStatus.getDestination() + " Train " + trainStatus.getRealTrainId() + " has accumulated at least " + TimeUnit.SECONDS.toMinutes(trainStatus.getSecondsOffSchedule()) + " mins of delays during its trip so far; it's currently " + distanceRemainingText + " ";
        String optionalHoldingText = (trainStatus.getSecondsSinceLastMoved() >= TimeUnit.MINUTES.toSeconds(3)) ? ", and has been holding there for at least " + TimeUnit.SECONDS.toMinutes(trainStatus.getSecondsSinceLastMoved()) + " mins" : "";
        String tweetTextPost = " on track " + trainStatus.getTrackNumber() + optionalHoldingText + ". (" + getTimestamp(trainStatus.getObservedDate()) + ") #wmata";

        int maxLength = TWEET_CHARACTER_LIMIT - (tweetTextPre.length() + tweetTextPost.length());
        String optimalStationName = StationUtil.getOptimalStationName(currentOrNextStationCode, maxLength);
        if (optimalStationName == null) {
            logger.warn("Unable to tweet about Train " + trainStatus.getRealTrainId() + " being delayed near " + currentOrNextStationCode + " due to insufficient available characters!");
            return;
        }

        queueTweet(tweetTextPre + optimalStationName + tweetTextPost, trainStatus.getLine());
    }

    public void tweetTrainOutOfService(TrainStatus trainStatus, String nearestStationCode) {
        if (this.isDataStale.get()) {
            return;
        }

        if (trainStatus == null || StringUtils.isEmpty(nearestStationCode)) {
            return;
        }

        if ("N/A".equals(trainStatus.getLine()) || "N/A".equals(trainStatus.getDestination()) || "No Passenger".equals(trainStatus.getDestination())) {
            // don't tweet about trains with insufficient data
            return;
        }

        String direction = StationUtil.getDirectionName(trainStatus.getLine(), trainStatus.getDirectionNumber());
        String tweetTextPre = ((direction != null) ? (direction + " ") : "") + ((trainStatus.getCar() != null && !trainStatus.getCar().equals("N/A")) ? (trainStatus.getCar() + "-car ") : "") + trainStatus.getLine() + "/" + trainStatus.getDestination() + " Train " + trainStatus.getRealTrainId() + " may have offloaded and gone out of service at or near ";
        String tweetTextPost = " on track " + trainStatus.getTrackNumber() + ". (" + getTimestamp(trainStatus.getObservedDate()) + ") #wmata";

        int maxLength = TWEET_CHARACTER_LIMIT - (tweetTextPre.length() + tweetTextPost.length());
        String optimalStationName = StationUtil.getOptimalStationName(nearestStationCode, maxLength);
        if (optimalStationName == null) {
            logger.warn("Unable to tweet about Train " + trainStatus.getRealTrainId() + " going out of service neat " + nearestStationCode + " due to insufficient available characters!");
            return;
        }

        queueTweet(tweetTextPre + optimalStationName + tweetTextPost, trainStatus.getLine());
    }

    public void tweetTrainChangedDestination(TrainStatus trainStatus, TrainStatus previousTrainStatus, String nearestStationCode) {
        if (this.isDataStale.get()) {
            return;
        }

        if (trainStatus == null || previousTrainStatus == null || StringUtils.isEmpty(nearestStationCode)) {
            return;
        }

        if ("N/A".equals(previousTrainStatus.getLine()) || "N/A".equals(previousTrainStatus.getDestination()) || "No Passenger".equals(previousTrainStatus.getDestination())) {
            // don't tweet about trains with insufficient data
            return;
        }

        if ("N/A".equals(trainStatus.getLine()) || "N/A".equals(trainStatus.getDestination()) || "No Passenger".equals(trainStatus.getDestination())) {
            // don't tweet about trains with insufficient data
            return;
        }

        String direction = StationUtil.getDirectionName(previousTrainStatus.getLine(), previousTrainStatus.getDirectionNumber());
        String tweetTextPre = ((direction != null) ? (direction + " ") : "") + ((trainStatus.getCar() != null && !trainStatus.getCar().equals("N/A")) ? (trainStatus.getCar() + "-car ") : "") + previousTrainStatus.getLine() + "/" + previousTrainStatus.getDestination() + " Train " + previousTrainStatus.getRealTrainId() + " changed its destination to " + trainStatus.getLine() + "/" + trainStatus.getDestination() + " at or near ";
        String tweetTextPost = " on track " + previousTrainStatus.getTrackNumber() + ". (" + getTimestamp(previousTrainStatus.getObservedDate()) + ")";

        int maxLength = TWEET_CHARACTER_LIMIT - (tweetTextPre.length() + tweetTextPost.length());
        String optimalStationName = StationUtil.getOptimalStationName(nearestStationCode, maxLength);
        if (optimalStationName == null) {
            logger.warn("Unable to tweet about Train " + previousTrainStatus.getRealTrainId() + " changing destinations due to insufficient available characters!");
            return;
        }

        queueTweet(tweetTextPre + optimalStationName + tweetTextPost, previousTrainStatus.getLine());
    }

    public void tweetTrainExpressedStation(TrainExpressedStationEvent expressedStationEvent) {
        if (this.isDataStale.get()) {
            return;
        }

        if (expressedStationEvent == null) {
            return;
        }

        String direction = StationUtil.getDirectionName(expressedStationEvent.getLineCode(), expressedStationEvent.getDirectionNumber());
        String tweetTextPre = ((direction != null) ? (direction + " ") : "") + ((expressedStationEvent.getNumCars() != null && !expressedStationEvent.getNumCars().equals("N/A")) ? (expressedStationEvent.getNumCars() + "-car ") : "") + expressedStationEvent.getLineCode() + "/" + StationUtil.getStationName(expressedStationEvent.getDestinationStationCode()) + " Train " + expressedStationEvent.getRealTrainId() + " may have passed through ";
        String tweetTextPost = " without picking up passengers on the track " + expressedStationEvent.getTrackNumber() + " platform. (" + getTimestamp(expressedStationEvent.getDate().getTime()) + ") #wmata";

        int maxLength = TWEET_CHARACTER_LIMIT - (tweetTextPre.length() + tweetTextPost.length());
        String optimalStationName = StationUtil.getOptimalStationName(expressedStationEvent.getStationCode(), maxLength);
        if (optimalStationName == null) {
            logger.warn("Unable to tweet about Train " + expressedStationEvent.getRealTrainId() + " expressing " + expressedStationEvent.getStationCode() + " due to insufficient available characters!");
            return;
        }

        queueTweet(tweetTextPre + optimalStationName + tweetTextPost, expressedStationEvent.getLineCode());
    }

    public void tweetLongTimeSinceLastTrainArrived(ArrivalInfo arrivalInfo, String lineCode, Integer directionNumber, String stationCode, Double observedTrainFrequency, Double expectedTrainFrequency, String nextTrainMin) {
        if (this.isDataStale.get()) {
            return;
        }

        if (observedTrainFrequency < expectedTrainFrequency * 2) {
            // not enough time has elapsed between trains compared to schedule to warrant a tweet (yet)
            return;
        }

        String key = String.join("_", stationCode, lineCode, String.valueOf(directionNumber));
        if (arrivalInfo.equals(this.tweetedArrivalInfoByStationAndLineAndDirection.get(key))) {
            return;
        }

        double diffTimeBetweenTrains = observedTrainFrequency - expectedTrainFrequency;

        int numMinutesLongerThanExpected = (int) Math.round(diffTimeBetweenTrains);
        int percentLongerThanExpected = (int) Math.round((diffTimeBetweenTrains / expectedTrainFrequency) * 100);

        String timeUntilNextTrainString;
        if (nextTrainMin != null) {
            timeUntilNextTrainString = "; the next train is ";

            switch (nextTrainMin) {
                case "BRD":
                    timeUntilNextTrainString += "boarding now";
                    break;
                case "ARR":
                    timeUntilNextTrainString += "arriving now";
                    break;
                case "1":
                    timeUntilNextTrainString += nextTrainMin + " min away";
                    break;
                default:
                    timeUntilNextTrainString += nextTrainMin + " mins away";
                    break;
            }
        } else {
            timeUntilNextTrainString = "; an ETA for the next train is not available";
        }

        String tweetTextPre = "The last " + StationUtil.getDirectionName(lineCode, directionNumber).toLowerCase() + " " + StationUtil.getLineName(lineCode) + " Line train to arrive at ";
        String tweetTextPost = " may have been as long as " + Math.round(observedTrainFrequency) + " mins ago, " + numMinutesLongerThanExpected + " mins (" + percentLongerThanExpected + "%) longer than scheduled" + timeUntilNextTrainString + ". (" + getTimestamp(Calendar.getInstance().getTime()) + ") #wmata";

        int maxLength = TWEET_CHARACTER_LIMIT - (tweetTextPre.length() + tweetTextPost.length());
        String optimalStationName = StationUtil.getOptimalStationName(stationCode, maxLength);
        if (optimalStationName == null) {
            logger.warn("Unable to tweet about long time between trains at " + StationUtil.getStationName(stationCode) + " due to insufficient available characters!");
            return;
        }

        // TODO: consider tweeting instead of logging to console (primary unresolved concern is volume and frequency of tweets)
        String tweetText = tweetTextPre + optimalStationName + tweetTextPost;
//        queueTweet(tweetText, lineCode);
        logger.warn(tweetText);

        this.tweetedArrivalInfoByStationAndLineAndDirection.put(key, arrivalInfo);
    }

    public void tweetServiceGap(ServiceGap serviceGap) {
        if (this.isDataStale.get()) {
            return;
        }

        if (serviceGap == null) {
            return;
        }

        if (this.recentlyTweetedServiceGaps.containsKey(serviceGap)) {
            return;
        }

        if (serviceGap.getTimeBetweenTrains() < serviceGap.getScheduledTimeBetweenTrains() * 2) {
            // not severe enough of a service gap for us to tweet about it
            return;
        }

        double diffTimeBetweenTrains = serviceGap.getTimeBetweenTrains() - serviceGap.getScheduledTimeBetweenTrains();

        int numMinutesLongerThanExpected = (int) Math.round(diffTimeBetweenTrains);
        int percentLongerThanExpected = (int) Math.round((diffTimeBetweenTrains / serviceGap.getScheduledTimeBetweenTrains()) * 100);

        String tweetText = "There may be a " + Math.round(serviceGap.getTimeBetweenTrains()) + "-minute service gap for " + serviceGap.getDirection().toLowerCase() + " "  + StationUtil.getLineName(serviceGap.getLineCode()) + " Line trains from " + serviceGap.getFromStationName() + " to " + serviceGap.getToStationName() + ", " + numMinutesLongerThanExpected + " mins (" + percentLongerThanExpected + "%) longer than scheduled. (" + getTimestamp(serviceGap.getObservedDate().getTime()) + ") #wmata";

        // TODO: consider tweeting instead of logging to console (primary unresolved concern is volume and frequency of tweets)
//        queueTweet(tweetText, serviceGap.getLineCode());
        logger.warn(tweetText);

        this.recentlyTweetedServiceGaps.put(serviceGap, serviceGap.getObservedDate());
    }

    @Scheduled(fixedDelay=60000)    // every minute
    private void expireOldServiceGapsFromBlacklist() {
        Calendar now = Calendar.getInstance();

        // after at least 30 minutes of being blacklisted, we can tweet about the same service gap again
        this.recentlyTweetedServiceGaps.values().removeIf(tweetDate -> TimeUnit.MILLISECONDS.toMinutes(now.getTimeInMillis() - tweetDate.getTimeInMillis()) >= 30);
    }

    /////
    // RUSH HOUR PERFORMANCE REPORTS

    @Scheduled(cron = "0 0 5 1/1 * MON-FRI")   // at 5am every weekday
    private void tweetAMRushStarting() {
        if (this.configUtil.isDevelopmentMode()) {
            return;
        }

        sendTweet("Assuming a normal weekday schedule, today's AM rush has started, and peak #WMATA Metrorail fares are now being charged.");
    }

    @Scheduled(cron = "0 31 9 1/1 * MON-FRI")   // at 9:31am every weekday
    private void tweetAMRushPerformanceSummary() {
        if (this.configUtil.isDevelopmentMode()) {
            return;
        }

        logger.info("Tweeting out AM rush performance summary...");

        if (tweetRushHourSummary("AM", "05:00:00", "09:30:00")) {
            logger.info("...successfully tweeted out AM rush performance summary!");
        }
    }

    @Scheduled(cron = "0 0 15 1/1 * MON-FRI")   // at 3pm every weekday
    private void tweetPMRushStarting() {
        if (this.configUtil.isDevelopmentMode()) {
            return;
        }

        sendTweet("Assuming a normal weekday schedule, today's PM rush has started, and peak #WMATA Metrorail fares are now being charged.");
    }

    @Scheduled(cron = "0 1 19 1/1 * MON-FRI")   // at 7:01pm every weekday
    private void tweetPMRushPerformanceSummary() {
        if (this.configUtil.isDevelopmentMode()) {
            return;
        }

        logger.info("Tweeting out PM rush performance summary...");

        if (tweetRushHourSummary("PM", "15:00:00", "19:00:00")) {
            logger.info("...successfully tweeted out PM rush performance summary!");
        }
    }

    private boolean tweetRushHourSummary(String rushType, String startTime, String endTime) {
        // NOTE: this function manages its own Twitter4J instances and tweets separate from this service's tweet queue system

        DateTimeFormatter psqlDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String date = psqlDateFormatter.format(LocalDateTime.now());
        String startDateTime = date + " " + startTime;
        String endDateTime = date + " " + endTime;
        List<Object[]> linePerformanceSummaryDataByLine = this.systemMetricsRepository.getLinePerformanceSummaryDataByLine(startDateTime, endDateTime);
        if (linePerformanceSummaryDataByLine == null || linePerformanceSummaryDataByLine.size() <= 0) {
            return false;
        }

        Twitter twitter = TwitterFactory.getSingleton();

        Status lastStatus;
        try {
            lastStatus = twitter.updateStatus("Assuming a normal weekday schedule, today's " + rushType + " rush has concluded. The following tweets show how each #WMATA Metrorail line performed...");
            this.postingRateLimitStatus.set(lastStatus.getRateLimitStatus());
        } catch (TwitterException e) {
            logger.error("Failed to send initial rush hour summary tweet!", e);
            return false;
        }

        DateTimeFormatter displayDateFormatter = DateTimeFormatter.ofPattern("M/d/yy");
        String displayDate = displayDateFormatter.format(LocalDateTime.now());
        for (Object[] linePerformanceSummaryDataForLine : linePerformanceSummaryDataByLine) {
            try {
                Thread.sleep(15000);    // try not to spam Twitter
            } catch (InterruptedException e) {
                logger.error("Failed to wait before sending next rush hour summary tweet!", e);
            }

            String lineCode = (String) linePerformanceSummaryDataForLine[0];
            String lineName;
            switch (lineCode) {
                case "RD":
                    lineName = "Red";
                    break;
                case "OR":
                    lineName = "Orange";
                    break;
                case "SV":
                    lineName = "Silver";
                    break;
                case "BL":
                    lineName = "Blue";
                    break;
                case "YL":
                    lineName = "Yellow";
                    break;
                case "GR":
                    lineName = "Green";
                    break;
                default:
                    lineName = "N/A";
                    break;
            }

            String avgHeadwayAdherence = (linePerformanceSummaryDataForLine[1] != null) ? linePerformanceSummaryDataForLine[1].toString() : null;
            if (avgHeadwayAdherence == null || "null".equals(avgHeadwayAdherence)) {
                avgHeadwayAdherence = "N/A";
            }

            String avgScheduleAdherence = (linePerformanceSummaryDataForLine[2] != null) ? linePerformanceSummaryDataForLine[2].toString() : null;
            if (avgScheduleAdherence == null || "null".equals(avgScheduleAdherence)) {
                avgScheduleAdherence = "N/A";
            }

            String avgTrainFrequency = (linePerformanceSummaryDataForLine[3] != null) ? linePerformanceSummaryDataForLine[3].toString() : null;
            if (avgTrainFrequency == null || "null".equals(avgTrainFrequency)) {
                avgTrainFrequency = "N/A";
            }

            String avgExpectedTrainFrequency = (linePerformanceSummaryDataForLine[4] != null) ? linePerformanceSummaryDataForLine[4].toString() : null;
            if (avgExpectedTrainFrequency == null || "null".equals(avgExpectedTrainFrequency)) {
                avgExpectedTrainFrequency = "N/A";
            }

            String avgTrainSpacingConsistency = (linePerformanceSummaryDataForLine[5] != null) ? linePerformanceSummaryDataForLine[5].toString() : null;
            if (avgTrainSpacingConsistency == null || "null".equals(avgTrainSpacingConsistency)) {
                avgTrainSpacingConsistency = "N/A";
            }

            String avgExpectedTrainSpacingConsistency = (linePerformanceSummaryDataForLine[6] != null) ? linePerformanceSummaryDataForLine[6].toString() : null;
            if (avgExpectedTrainSpacingConsistency == null || "null".equals(avgExpectedTrainSpacingConsistency)) {
                avgExpectedTrainSpacingConsistency = "N/A";
            }

            String avgPlatformWaitTime = (linePerformanceSummaryDataForLine[7] != null) ? linePerformanceSummaryDataForLine[7].toString() : null;
            if (avgPlatformWaitTime == null || "null".equals(avgPlatformWaitTime)) {
                avgPlatformWaitTime = "N/A";
            }

            String avgExpectedPlatformWaitTime = (linePerformanceSummaryDataForLine[8] != null) ? linePerformanceSummaryDataForLine[8].toString() : null;
            if (avgExpectedPlatformWaitTime == null || "null".equals(avgExpectedPlatformWaitTime)) {
                avgExpectedPlatformWaitTime = "N/A";
            }

            String numServiceIncidents = (linePerformanceSummaryDataForLine[9] != null) ? linePerformanceSummaryDataForLine[9].toString() : null;
            if (numServiceIncidents == null || "null".equals(numServiceIncidents)) {
                numServiceIncidents = "N/A";
            }

            String numTrainOffloads = (linePerformanceSummaryDataForLine[10] != null) ? linePerformanceSummaryDataForLine[10].toString() : null;
            if (numTrainOffloads == null || "null".equals(numTrainOffloads)) {
                numTrainOffloads = "N/A";
            }

            String numTimesTrainsExpressedStations = (linePerformanceSummaryDataForLine[11] != null) ? linePerformanceSummaryDataForLine[11].toString() : null;
            if (numTimesTrainsExpressedStations == null || "null".equals(numTimesTrainsExpressedStations)) {
                numTimesTrainsExpressedStations = "N/A";
            }

            String numTrainProblems = (linePerformanceSummaryDataForLine[12] != null) ? linePerformanceSummaryDataForLine[12].toString() : null;
            if (numTrainProblems == null || "null".equals(numTrainProblems)) {
                numTrainProblems = "N/A";
            }

            String avgNumTrains = (linePerformanceSummaryDataForLine[13] != null) ? linePerformanceSummaryDataForLine[13].toString() : null;
            if (avgNumTrains == null || "null".equals(avgNumTrains)) {
                avgNumTrains = "N/A";
            }

            String avgExpectedNumTrains = (linePerformanceSummaryDataForLine[14] != null) ? linePerformanceSummaryDataForLine[14].toString() : null;
            if (avgExpectedNumTrains == null || "null".equals(avgExpectedNumTrains)) {
                avgExpectedNumTrains = "N/A";
            }

            String avgNumEightCarTrains = (linePerformanceSummaryDataForLine[15] != null) ? linePerformanceSummaryDataForLine[15].toString() : null;
            if (avgNumEightCarTrains == null || "null".equals(avgNumEightCarTrains)) {
                avgNumEightCarTrains = "N/A";
            }

            String percentEightCarTrains = (linePerformanceSummaryDataForLine[16] != null) ? linePerformanceSummaryDataForLine[16].toString() : null;
            if (percentEightCarTrains == null || "null".equals(percentEightCarTrains)) {
                percentEightCarTrains = "N/A";
            }

            String maxMdnTrainDelay = (linePerformanceSummaryDataForLine[17] != null) ? linePerformanceSummaryDataForLine[17].toString() : null;
            if (maxMdnTrainDelay == null || "null".equals(maxMdnTrainDelay)) {
                maxMdnTrainDelay = "N/A";
            }

            String maxMdnTrainDelayTime = (linePerformanceSummaryDataForLine[18] != null) ? linePerformanceSummaryDataForLine[18].toString() : null;
            if (maxMdnTrainDelayTime == null || "null".equals(maxMdnTrainDelayTime)) {
                maxMdnTrainDelayTime = "N/A";
            }

            File imageFile = generateSummaryImage(lineCode, lineName, displayDate, rushType, avgHeadwayAdherence, avgScheduleAdherence, avgTrainFrequency, avgExpectedTrainFrequency, avgTrainSpacingConsistency, avgExpectedTrainSpacingConsistency, avgPlatformWaitTime, avgExpectedPlatformWaitTime, numServiceIncidents, numTrainOffloads, numTimesTrainsExpressedStations, numTrainProblems, avgNumTrains, avgExpectedNumTrains, avgNumEightCarTrains, percentEightCarTrains, maxMdnTrainDelay, maxMdnTrainDelayTime);
            if (imageFile != null) {
                String tweetText = lineName + " Line, " + rushType + " Rush Performance Report for " + displayDate + " (see attached)";

                StatusUpdate statusUpdate = new StatusUpdate(tweetText + " #wmata");
                statusUpdate.setMedia(imageFile);
                statusUpdate.setInReplyToStatusId(lastStatus.getId());

                try {
                    lastStatus = twitter.updateStatus(statusUpdate);
                    this.postingRateLimitStatus.set(lastStatus.getRateLimitStatus());
                } catch (TwitterException e) {
                    logger.error("Failed to send a rush hour summary tweet: " + statusUpdate.getStatus(), e);
                }

                Twitter twitter2;
                switch (lineCode) {
                    case "RD":
                        twitter2 = new TwitterFactory("/rd").getInstance();
                        break;
                    case "OR":
                        twitter2 = new TwitterFactory("/or").getInstance();
                        break;
                    case "SV":
                        twitter2 = new TwitterFactory("/sv").getInstance();
                        break;
                    case "BL":
                        twitter2 = new TwitterFactory("/bl").getInstance();
                        break;
                    case "YL":
                        twitter2 = new TwitterFactory("/yl").getInstance();
                        break;
                    case "GR":
                        twitter2 = new TwitterFactory("/gr").getInstance();
                        break;
                    default:
                        twitter2 = null;
                        break;
                }
                if (twitter2 != null) {
                    try {
                        StatusUpdate statusUpdate2 = new StatusUpdate(tweetText);
                        statusUpdate2.setMedia(imageFile);
                        twitter2.updateStatus(statusUpdate2);
                    } catch (TwitterException e) {
                        logger.error("Failed to send a line-specific rush hour summary tweet for the " + lineCode + " line: " + tweetText, e);
                    }
                }
            }
        }

        return true;
    }

    private File generateSummaryImage(String lineCode, String lineName, String displayDate, String rushType, String avgHeadwayAdherence, String avgScheduleAdherence, String avgTrainFrequency, String avgExpectedTrainFrequency, String avgTrainSpacingConsistency, String avgExpectedTrainSpacingConsistency, String avgPlatformWaitTime, String avgExpectedPlatformWaitTime, String numServiceIncidents, String numTrainOffloads, String numTimesTrainsExpressedStations, String numTrainProblems, String avgNumTrains, String avgExpectedNumTrains, String avgNumEightCarTrains, String percentEightCarTrains, String maxMdnTrainDelay, String maxMdnTrainDelayTime) {
        String trainFrequencyComparisonText = null;
        try {
            Double expected = Double.parseDouble(avgExpectedTrainFrequency);

            if (expected == 0) {
                trainFrequencyComparisonText = " (expected 0 minutes)";
            } else {
                Double observed = Double.parseDouble(avgTrainFrequency);
                Integer percentDifference = Math.toIntExact(Math.round(((observed - expected) / expected) * 100));

                trainFrequencyComparisonText = " (" + ((percentDifference == 0) ? ("on target; " + avgExpectedTrainFrequency + " minutes expected") : (Math.abs(percentDifference) + "% " + (((percentDifference < 0) ? "better" : "worse")) + " than expected " + avgExpectedTrainFrequency + " minutes")) + ")";
            }
        } catch (Exception ignored) {}

        String trainSpacingConsistencyComparisonText = null;
        try {
            Double expected = Double.parseDouble(avgExpectedTrainSpacingConsistency);

            if (expected == 0) {
                trainSpacingConsistencyComparisonText = " (expected 0 minutes)";
            } else {
                Double observed = Double.parseDouble(avgTrainSpacingConsistency);
                Integer percentDifference = Math.toIntExact(Math.round(((observed - expected) / expected) * 100));

                trainSpacingConsistencyComparisonText = " (" + ((percentDifference == 0) ? ("on target; " + avgExpectedTrainSpacingConsistency + " minutes expected") : (Math.abs(percentDifference) + "% " + (((percentDifference < 0) ? "better" : "worse")) + " than expected " + avgExpectedTrainSpacingConsistency + " minutes")) + ")";
            }
        } catch (Exception ignored) {}

        String platformWaitTimeComparisonText = null;
        try {
            Double expected = Double.parseDouble(avgExpectedPlatformWaitTime);

            if (expected == 0) {
                platformWaitTimeComparisonText = " (expected 0 minutes)";
            } else {
                Double observed = Double.parseDouble(avgPlatformWaitTime);
                Integer percentDifference = Math.toIntExact(Math.round(((observed - expected) / expected) * 100));

                platformWaitTimeComparisonText = " (" + ((percentDifference == 0) ? ("on target; " + avgExpectedPlatformWaitTime + " minutes expected") : (Math.abs(percentDifference) + "% " + (((percentDifference < 0) ? "better" : "worse")) + " than expected " + avgExpectedPlatformWaitTime + " minutes")) + ")";
            }
        } catch (Exception ignored) {}

        String numTrainsComparisonText = null;
        try {
            Double expected = Double.parseDouble(avgExpectedNumTrains);

            if (expected == 0) {
                numTrainsComparisonText = " (expected 0)";
            } else {
                Double observed = Double.parseDouble(avgNumTrains);
                Integer percentDifference = Math.toIntExact(Math.round(((observed - expected) / expected) * 100));

                numTrainsComparisonText = " (" + ((percentDifference == 0) ? ("on target; " + avgExpectedNumTrains + " expected") : (Math.abs(percentDifference) + "% " + (((percentDifference > 0) ? "better" : "worse")) + " than expected " + avgExpectedNumTrains)) + ")";
            }
        } catch (Exception ignored) {}

        String numEightCarTrainsComparisonText = null;
        if (!"N/A".equals(percentEightCarTrains)) {
            numEightCarTrainsComparisonText = " (" + percentEightCarTrains + "% of trains)";
        }

        String numTrainProblemsText = numTrainProblems;
        try {
            // include currently active negative train tags, none of which have not persisted to the database yet (by design)
            // note that this means we're assuming this report is generated as soon as possible after the end date/time for the report
            numTrainProblemsText = String.valueOf(Integer.parseInt(numTrainProblems) + this.trainTaggingService.getNumActiveNegativeTrainTags(lineCode));
        } catch (Exception ignored) {}

        String html = (
                "<div style='padding:8px;'>" +
                "  <strong>" + lineName + " Line, " + rushType + " Rush Performance Report for " + displayDate + "</strong>" +
                "  <ul>" +
                "    <li>avg headway adherence: " + avgHeadwayAdherence + "%</li>" +
                "    <li>avg schedule adherence: " + avgScheduleAdherence + "%</li>" +
                "    <li>avg train frequency: " + avgTrainFrequency + " minutes" + (trainFrequencyComparisonText != null ? trainFrequencyComparisonText : "") + "</li>" +
                "    <li>avg train spacing consistency: " + avgTrainSpacingConsistency + " minutes" + (trainSpacingConsistencyComparisonText != null ? trainSpacingConsistencyComparisonText : "") + "</li>" +
                "    <li>avg platform wait time: " + avgPlatformWaitTime + " minutes" + (platformWaitTimeComparisonText != null ? platformWaitTimeComparisonText : "") + "</li>" +
                "    <li>estimated number of service incidents: " + numServiceIncidents + "</li>" +
                "    <li>estimated number of train offloads: " + numTrainOffloads + "</li>" +
                "    <li>estimated number of times trains expressed stations: " + numTimesTrainsExpressedStations + "</li>" +
                "    <li>number of train problem reports from MetroHero users: " + numTrainProblemsText + "</li>" +
                "    <li>avg number of trains: " + avgNumTrains + (numTrainsComparisonText != null ? numTrainsComparisonText : "") + "</li>" +
                "    <li>avg number of eight-car trains: " + avgNumEightCarTrains + (numEightCarTrainsComparisonText != null ? numEightCarTrainsComparisonText : "") + "</li>" +
                "    <li>at " + maxMdnTrainDelayTime + ", a majority of trains were delayed by at least " + maxMdnTrainDelay + " minutes</li>" +
                "  </ul>" +
                "</div>"
        );

        System.setProperty("java.awt.headless", "true");

        JEditorPane jep = new JEditorPane("text/html", html);
        jep.setSize(Short.MAX_VALUE, Short.MAX_VALUE);
        jep.setSize(jep.getPreferredSize().width, jep.getPreferredSize().height);   // resize to fit contents
        BufferedImage image = new BufferedImage(jep.getWidth(), jep.getHeight(), BufferedImage.TYPE_INT_ARGB);
        jep.print(image.createGraphics());

        File file = new File(lineName.toLowerCase() + "-" + rushType + ".png");
        try {
            ImageIO.write(image, "png", file);
        } catch (IOException e) {
            logger.error("Failed to create a rush hour summary image for the " + lineCode + " line!", e);
            return null;
        }

        return file;
    }

    /////

    private void queueTweet(String text) {
        queueTweet(text, (Set<String>) null);
    }
    private void queueTweet(String text, String lineCode) {
        queueTweet(text, Collections.singleton(lineCode));
    }
    private void queueTweet(String text, Set<String> lineCodes) {
        if (StringUtils.isEmpty(text)) {
            return;
        }

        if (this.isQueueSuspended.get()) {
            // we're not allowed to add any new tweets to the queue right now
            return;
        }

        TweetData tweetData = new TweetData(text, lineCodes);
        this.tweetQueue.addLast(tweetData);
    }

    @Scheduled(cron = "*/15 * * * * *")    // run every 15 seconds
    private void tweet() {
        if (this.isDataStale.get()) {
            return;
        }

        RateLimitStatus postingRateLimitStatus = this.postingRateLimitStatus.get();
        if (this.isQueueSuspended.get()) {
            if (postingRateLimitStatus != null && (postingRateLimitStatus.getSecondsUntilReset() <= 0)) {
                // allow us to start tweeting again
                this.isQueueSuspended.set(false);

                sendTweet("BEEP BOOP: Twitter is letting us tweet again, so we're back! Apologies for the delay. Tweets will resume now. 🤖");
            }
            return;
        } else {
            if (postingRateLimitStatus != null && (postingRateLimitStatus.getRemaining() <= REMAINING_TWEET_THRESHOLD)) {
                // suspend tweeting and accepting new tweets, and also clear our tweet queue
                this.isQueueSuspended.set(true);
                this.tweetQueue.clear();

                int numMinutesRemaining = (int) Math.round(postingRateLimitStatus.getSecondsUntilReset() / 60d);
                String numMinutesRemainingString = (numMinutesRemaining > 0) ? String.valueOf(numMinutesRemaining) : "<1";
                numMinutesRemainingString += (numMinutesRemaining <= 1) ? " minute" : " minutes";

                sendTweet("BEEP BOOP: Whew, it's been an interesting past few hours on Metrorail, huh? We're fast approaching Twitter's tweet limit, so tweets from us will be limited temporarily. We'll be back in " + numMinutesRemainingString + ". Apologies for the inconvenience! 🤖");
                return;
            }
        }

        TweetData tweetData = this.tweetQueue.pollFirst();
        if (tweetData == null || StringUtils.isEmpty(tweetData.getText())) {
            // nothing to tweet about
            return;
        }

        sendTweet(tweetData.getText(), tweetData.getLineCodes());
    }

    private void sendTweet(String text) {
        sendTweet(text, null);
    }
    private void sendTweet(String text, Set<String> lineCodes) {
        if (text.length() > TWEET_CHARACTER_LIMIT) {
            logger.warn("Unable to send tweet greater than " + TWEET_CHARACTER_LIMIT + " characters: " + text);
            return;
        }

        if (this.configUtil.isDevelopmentMode()) {
            logger.warn(text);
        } else {
            try {
                Twitter twitter = TwitterFactory.getSingleton();
                Status status = twitter.updateStatus(text);
                this.postingRateLimitStatus.set(status.getRateLimitStatus());

                if (lineCodes != null) {
                    for (String lineCode : lineCodes) {
                        switch (lineCode) {
                            case "RD":
                                twitter = new TwitterFactory("/rd").getInstance();
                                break;
                            case "OR":
                                twitter = new TwitterFactory("/or").getInstance();
                                break;
                            case "SV":
                                twitter = new TwitterFactory("/sv").getInstance();
                                break;
                            case "BL":
                                twitter = new TwitterFactory("/bl").getInstance();
                                break;
                            case "YL":
                                twitter = new TwitterFactory("/yl").getInstance();
                                break;
                            case "GR":
                                twitter = new TwitterFactory("/gr").getInstance();
                                break;
                            default:
                                continue;
                        }
                        if (twitter != null) {
                            twitter.updateStatus(text.replaceAll(" #wmata", ""));
                        }
                    }
                }
            } catch (TwitterException e) {
                logger.error("Unable to send tweet: " + text, e);
            }
        }
    }

    private static String getTimestamp(Date date) {
        return (new SimpleDateFormat("h:mma")).format(date).toLowerCase();
    }

    private class TweetData {
        private String text;
        private Set<String> lineCodes;

        public TweetData(String text) {
            this.text = text;
        }

        public TweetData(String text, Set<String> lineCodes) {
            this.text = text;
            this.lineCodes = lineCodes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TweetData tweetData = (TweetData) o;
            return Objects.equals(text, tweetData.text) &&
                    Objects.equals(lineCodes, tweetData.lineCodes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(text, lineCodes);
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Set<String> getLineCodes() {
            return lineCodes;
        }

        public void setLineCodes(Set<String> lineCodes) {
            this.lineCodes = lineCodes;
        }
    }
}

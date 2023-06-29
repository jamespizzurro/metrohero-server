package com.jamespizzurro.metrorailserver.service;

import com.jamespizzurro.metrorailserver.StationUtil;
import com.jamespizzurro.metrorailserver.domain.*;
import com.jamespizzurro.metrorailserver.repository.SystemMetricsRepository;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MetricsService {

    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);

    private final TrainService trainService;
    private final GtfsService gtfsService;
    private final TwitterBotService twitterBotService;
    private final SystemMetricsRepository systemMetricsRepository;

    private volatile SystemMetrics systemMetrics;
    private volatile RecentTrainFrequencyData recentTrainFrequencyData;
    private volatile List<PerformanceSummary> performanceSummaryData;

    @Autowired
    public MetricsService(TrainService trainService, GtfsService gtfsService, TwitterBotService twitterBotService, SystemMetricsRepository systemMetricsRepository) {
        this.trainService = trainService;
        this.gtfsService = gtfsService;
        this.twitterBotService = twitterBotService;
        this.systemMetricsRepository = systemMetricsRepository;
    }

    public List<PerformanceSummary> getHourlyPerformanceSummary(Long fromUnixTimestamp, Long toUnixTimestamp) {
        List<Object[]> results = this.systemMetricsRepository.getHourlyPerformanceSummary(fromUnixTimestamp, toUnixTimestamp);
        if (results == null || results.size() <= 0) {
            return null;
        }

        List<PerformanceSummary> performanceSummaries = new ArrayList<>();
        for (Object[] performanceSummaryData : results) {
            performanceSummaries.add(new PerformanceSummary(performanceSummaryData));
        }
        return performanceSummaries;
    }

    public DashboardHistoryResponse getDashboardHistory(int interval, long observedDateTimestampMin, long observedDateTimestampMax) {
        List<Object[]> results = this.systemMetricsRepository.getHistory(interval, observedDateTimestampMin, observedDateTimestampMax);
        if (results == null || results.size() <= 0) {
            return null;
        }

        List<Object[]> avgResults = this.systemMetricsRepository.getAvgHistory(interval, observedDateTimestampMin, observedDateTimestampMax);
        if (avgResults == null || avgResults.size() <= 0) {
            return null;
        }

        results.addAll(avgResults);
        return new DashboardHistoryResponse(results, this.systemMetricsRepository.getEarliestTimestamp());
    }

    @Scheduled(fixedRate = 30000)  // every 30 seconds (independent of last run)
    private void update() {
        if (this.trainService.getStationTrainStatusesMap() == null || this.trainService.getStationTrainStatusesMap().size() <= 0) {
            // no data to process yet
            return;
        }

        logger.info("Updating Dashboard metrics...");

        if (this.trainService.isDataStale()) {
            logger.warn("Failed to update Dashboard metrics! Train data from WMATA is stale.");
            return;
        }

        Calendar now = Calendar.getInstance();

        Map<String, SystemMetrics.LineMetrics> lineMetricsByLine = new LinkedHashMap<>();

        lineMetricsByLine.put("RD", new SystemMetrics.LineMetrics("RD", now));
        lineMetricsByLine.put("OR", new SystemMetrics.LineMetrics("OR", now));
        lineMetricsByLine.put("SV", new SystemMetrics.LineMetrics("SV", now));
        lineMetricsByLine.put("BL", new SystemMetrics.LineMetrics("BL", now));
        lineMetricsByLine.put("YL", new SystemMetrics.LineMetrics("YL", now));
        lineMetricsByLine.put("GR", new SystemMetrics.LineMetrics("GR", now));

        int currentDay = now.get(Calendar.DAY_OF_WEEK);
        boolean isRushHour = (currentDay == Calendar.MONDAY || currentDay == Calendar.TUESDAY || currentDay == Calendar.WEDNESDAY || currentDay == Calendar.THURSDAY || currentDay == Calendar.FRIDAY) && (StationUtil.isCurrentTimeBetween("05:00:00", "09:30:00") || StationUtil.isCurrentTimeBetween("15:00:00", "19:00:00"));
        if (isRushHour) {
            lineMetricsByLine.get("RD").setBudgetedNumTrains(34);
            lineMetricsByLine.get("OR").setBudgetedNumTrains(20);
            lineMetricsByLine.get("SV").setBudgetedNumTrains(20);
            lineMetricsByLine.get("BL").setBudgetedNumTrains(19);
            lineMetricsByLine.get("YL").setBudgetedNumTrains(9);
            lineMetricsByLine.get("GR").setBudgetedNumTrains(17);

            lineMetricsByLine.get("RD").setBudgetedNumEightCarTrains(18);
            lineMetricsByLine.get("OR").setBudgetedNumEightCarTrains(12);
            lineMetricsByLine.get("SV").setBudgetedNumEightCarTrains(2);
            lineMetricsByLine.get("BL").setBudgetedNumEightCarTrains(12);
            lineMetricsByLine.get("YL").setBudgetedNumEightCarTrains(0);
            lineMetricsByLine.get("GR").setBudgetedNumEightCarTrains(10);

            for (String lineCode : lineMetricsByLine.keySet()) {
                lineMetricsByLine.get(lineCode).setBudgetedNumCars(((lineMetricsByLine.get(lineCode).getBudgetedNumTrains() - lineMetricsByLine.get(lineCode).getBudgetedNumEightCarTrains()) * 6) + (lineMetricsByLine.get(lineCode).getBudgetedNumEightCarTrains() * 8));
            }
        }

        Map<String, List<SystemMetrics.DirectionMetrics>> stationDirectionMetricsListByLineAndDirection = new HashMap<>();

        Set<String> trainIdsAlreadyCounted = new HashSet<>();
        for (String stationCode : this.trainService.getStationCodesSet()) {
            Map<String, List<TrainStatus>> trainStatusesByLineAndDirection = new HashMap<>();

            List<TrainStatus> stationTrainStatuses = this.trainService.getStationTrainStatusesMap().get(stationCode);
            if (stationTrainStatuses != null) {
                // bucket each station's train statuses by line and direction
                for (TrainStatus stationTrainStatus : stationTrainStatuses) {
                    if ("N/A".equals(stationTrainStatus.getLine()) || stationTrainStatus.getDirectionNumber() == null) {
                        // trains with an unknown line (e.g. No Passenger trains) are not active trains
                        continue;
                    }

                    if (stationTrainStatus.isKeyedDown() || stationTrainStatus.wasKeyedDown()) {
                        // keyed down trains are not active trains
                        continue;
                    }

                    if (stationTrainStatus.getScheduledTime() != null) {
                        // scheduled trains are not active trains (yet)
                        continue;
                    }

                    if (stationTrainStatus.getLocationCode() == null || stationTrainStatus.getPreviousStationCode() == null) {
                        // trains on non-revenue track (not including pocket tracks) are not active trains
                        continue;
                    }

                    if (this.trainService.getTerminalStationTrackCircuitIdSet().contains(stationTrainStatus.getTrackCircuitId())) {
                        // trains at terminal stations are not active trains (yet)
                        continue;
                    }

                    String stationTrainStatusLineAndDirection = String.join("_", stationTrainStatus.getLine(), stationTrainStatus.getDirectionNumber().toString());
                    List<TrainStatus> stationTrainStatusesForLineAndDirection = trainStatusesByLineAndDirection.computeIfAbsent(stationTrainStatusLineAndDirection, k -> new ArrayList<>());
                    stationTrainStatusesForLineAndDirection.add(stationTrainStatus);
                }
            }

            for (String lineCode : Arrays.asList("RD", "OR", "SV", "BL", "YL", "GR")) {
                for (Integer directionNumber : Arrays.asList(1, 2)) {
                    SystemMetrics.DirectionMetrics stationDirectionMetrics = new SystemMetrics.DirectionMetrics(lineCode, directionNumber, now);

                    String stationCodeAndLineCodeAndDirectionNumber = String.join("_", stationCode, lineCode, directionNumber.toString());

                    Double expectedTrainFrequency = this.gtfsService.getExpectedTrainFrequency(lineCode, directionNumber, stationCode);
                    if (expectedTrainFrequency == null) {
                        // no scheduled train arriving at this station with this line and in this direction for a while
                        // reset station arrival map for this combo to avoid picking up old, stale data later should this combo get scheduled in the future
                        this.trainService.getLastStationArrivalMap().remove(stationCodeAndLineCodeAndDirectionNumber);
                    }

                    ArrivalInfo lastArrival = this.trainService.getLastStationArrivalMap().get(stationCodeAndLineCodeAndDirectionNumber);

                    if (expectedTrainFrequency != null) {
                        stationDirectionMetrics.setExpectedTrainFrequency(expectedTrainFrequency);

                        if (lastArrival != null) {
                            Boolean isAdheringToSchedule = this.gtfsService.isAdheringToSchedule(lineCode, directionNumber, stationCode);
                            if (isAdheringToSchedule != null) {
                                stationDirectionMetrics.setAverageScheduleAdherence(isAdheringToSchedule ? 100d : 0d);
                            }

                            if (lastArrival.getTimeSinceLastArrival() != null) {
                                double timeSinceLastArrival = ((now.getTimeInMillis() - lastArrival.getArrivalTime().getTimeInMillis()) / 1000d) / 60d;
                                double stationTrainFrequency = Math.max(timeSinceLastArrival, lastArrival.getTimeSinceLastArrival());
                                boolean isAdheringToHeadways = (stationTrainFrequency <= (expectedTrainFrequency + 2d));
                                stationDirectionMetrics.setAverageHeadwayAdherence(isAdheringToHeadways ? 100d : 0d);
                            }
                        }
                    }

                    // calculate remaining station-specific direction metrics using train statuses, if available
                    String lineCodeAndDirectionNumber = String.join("_", lineCode, directionNumber.toString());
                    List<TrainStatus> stationTrainStatusesForLineAndDirection = trainStatusesByLineAndDirection.get(lineCodeAndDirectionNumber);
                    if (stationTrainStatusesForLineAndDirection != null && !stationTrainStatusesForLineAndDirection.isEmpty()) {
                        boolean shouldGetOnTimePerformance = true;
                        for (TrainStatus stationTrainStatus : stationTrainStatusesForLineAndDirection) {
                            stationDirectionMetrics.getEtas().add(stationTrainStatus.getMinutesAway());

                            if (shouldGetOnTimePerformance) {
                                if (stationCode.equals(stationTrainStatus.getLocationCode()) && stationTrainStatus.getPreviousStationCode() != null && lastArrival != null && expectedTrainFrequency != null) {
                                    double runningTimeSinceLastDeparturePlusMinutesAway = (((now.getTimeInMillis() - lastArrival.getArrivalTime().getTimeInMillis()) / 1000d) / 60d) + stationTrainStatus.getMinutesAway();
                                    double stationTrainFrequency = Math.max(runningTimeSinceLastDeparturePlusMinutesAway, (lastArrival.getTimeSinceLastArrival() != null) ? lastArrival.getTimeSinceLastArrival() : 0);
                                    double onTimeThreshold = isRushHour ? (expectedTrainFrequency + 2d) : (expectedTrainFrequency * 1.5d);
                                    stationDirectionMetrics.getOnTimePerformances().add((stationTrainFrequency <= onTimeThreshold) ? 100d : 0d);
                                    shouldGetOnTimePerformance = false;
                                }
                            }

                            if (!trainIdsAlreadyCounted.contains(stationTrainStatus.getTrainId())) {
                                stationDirectionMetrics.setNumTrains(stationDirectionMetrics.getNumTrains() + 1);

                                if (Objects.equals(stationTrainStatus.getCar(), "8")) {
                                    stationDirectionMetrics.setNumEightCarTrains(stationDirectionMetrics.getNumEightCarTrains() + 1);
                                }

                                int secondsDelayed = stationTrainStatus.getSecondsOffSchedule();
                                if (secondsDelayed >= 300) {
                                    stationDirectionMetrics.setNumDelayedTrains(stationDirectionMetrics.getNumDelayedTrains() + 1);
                                }
                                stationDirectionMetrics.getDelayTimes().add(secondsDelayed);

                                trainIdsAlreadyCounted.add(stationTrainStatus.getTrainId());
                            }
                        }

                        if (stationDirectionMetrics.getEtas().size() > 1) {
                            Collections.sort(stationDirectionMetrics.getEtas());

                            SummaryStatistics minimumHeadways = new SummaryStatistics();
                            for (int i = 0, j = 1; j < stationDirectionMetrics.getEtas().size(); i++, j++) {
                                double minimumHeadway = stationDirectionMetrics.getEtas().get(j) - stationDirectionMetrics.getEtas().get(i);
                                minimumHeadways.addValue(minimumHeadway);
                            }
                            stationDirectionMetrics.setAverageMinimumHeadways(minimumHeadways.getMean());
                        }

                        if (lastArrival != null && lastArrival.getTimeSinceLastArrival() != null) {
                            double timeSinceLastArrival = ((now.getTimeInMillis() - lastArrival.getArrivalTime().getTimeInMillis()) / 1000d) / 60d;
                            double stationTrainFrequency = Math.max(timeSinceLastArrival, lastArrival.getTimeSinceLastArrival());
                            stationDirectionMetrics.setAverageTrainFrequency(stationTrainFrequency);
                        }
                    }

                    List<SystemMetrics.DirectionMetrics> stationDirectionMetricsListForLineAndDirection = stationDirectionMetricsListByLineAndDirection.computeIfAbsent(lineCodeAndDirectionNumber, k -> new ArrayList<>());
                    stationDirectionMetricsListForLineAndDirection.add(stationDirectionMetrics);
                }
            }
        }

        Map<String, SystemMetrics.TrendStatus> platformWaitTimeTrendStatusByLineAndDirection = null;

        List<Object[]> platformWaitTimeTrendStatusByLineAndDirectionResults = this.systemMetricsRepository.getPlatformWaitTimeTrendStatusDataByLineAndDirection();
        if (platformWaitTimeTrendStatusByLineAndDirectionResults != null) {
            platformWaitTimeTrendStatusByLineAndDirection = new HashMap<>();
            for (Object[] platformWaitTimeTrendStatusByLineAndDirectionResult : platformWaitTimeTrendStatusByLineAndDirectionResults) {
                String lineCode = (String) platformWaitTimeTrendStatusByLineAndDirectionResult[0];
                Integer directionNumber = (Integer) platformWaitTimeTrendStatusByLineAndDirectionResult[1];
                Boolean trendingDown = (Boolean) platformWaitTimeTrendStatusByLineAndDirectionResult[2];
                Boolean trendingUp = (Boolean) platformWaitTimeTrendStatusByLineAndDirectionResult[3];

                SystemMetrics.TrendStatus trendStatus;
                if (trendingDown) {
                    trendStatus = SystemMetrics.TrendStatus.DECREASING;
                } else if (trendingUp) {
                    trendStatus = SystemMetrics.TrendStatus.INCREASING;
                } else {
                    trendStatus = SystemMetrics.TrendStatus.NEUTRAL;
                }
                platformWaitTimeTrendStatusByLineAndDirection.put(lineCode + "_" + directionNumber, trendStatus);
            }
        }

        // calculate direction metrics using station-specific direction metrics and assign to line metrics
        for (Map.Entry<String, List<SystemMetrics.DirectionMetrics>> entry : stationDirectionMetricsListByLineAndDirection.entrySet()) {
            String lineCodeAndDirectionNumber = entry.getKey();
            List<SystemMetrics.DirectionMetrics> stationDirectionMetricsListForLineAndDirection = entry.getValue();

            String[] splitLineCodeAndDirectionNumber = lineCodeAndDirectionNumber.split("_");
            String lineCode = splitLineCodeAndDirectionNumber[0];
            Integer directionNumber = Integer.valueOf(splitLineCodeAndDirectionNumber[1]);

            String direction = null;
            String towardsStationName = null;
            switch (lineCode) {
                case "RD":
                    if (directionNumber == 1) {
                        direction = "Eastbound";
                        towardsStationName = "Glenmont";
                    } else if (directionNumber == 2) {
                        direction = "Westbound";
                        towardsStationName = "Shady Grove";
                    }
                    break;
                case "OR":
                    if (directionNumber == 1) {
                        direction = "Eastbound";
                        towardsStationName = "New Carrollton";
                    } else if (directionNumber == 2) {
                        direction = "Westbound";
                        towardsStationName = "Vienna";
                    }
                    break;
                case "SV":
                    if (directionNumber == 1) {
                        direction = "Eastbound";
                        towardsStationName = "Downtown Largo";
                    } else if (directionNumber == 2) {
                        direction = "Westbound";
                        towardsStationName = "Ashburn";
                    }
                    break;
                case "BL":
                    if (directionNumber == 1) {
                        direction = "Eastbound";
                        towardsStationName = "Downtown Largo";
                    } else if (directionNumber == 2) {
                        direction = "Westbound";
                        towardsStationName = "Franconia-Springfield";
                    }
                    break;
                case "YL":
                    if (directionNumber == 1) {
                        direction = "Northbound";
                        towardsStationName = "Greenbelt";
                    } else if (directionNumber == 2) {
                        direction = "Southbound";
                        towardsStationName = "Huntington";
                    }
                    break;
                case "GR":
                    if (directionNumber == 1) {
                        direction = "Northbound";
                        towardsStationName = "Greenbelt";
                    } else if (directionNumber == 2) {
                        direction = "Southbound";
                        towardsStationName = "Branch Avenue";
                    }
                    break;
            }
            if (direction == null) {
                continue;
            }

            SystemMetrics.DirectionMetrics directionMetricsForLineAndDirection = new SystemMetrics.DirectionMetrics(lineCode, directionNumber, now);

            directionMetricsForLineAndDirection.setDirection(direction);
            directionMetricsForLineAndDirection.setTowardsStationName(towardsStationName);

            DescriptiveStatistics delayTimes = new DescriptiveStatistics();
            SummaryStatistics minimumHeadways = new SummaryStatistics();
            SummaryStatistics trainFrequencies = new SummaryStatistics();
            SummaryStatistics onTimePerformances = new SummaryStatistics();
            SummaryStatistics headwayAdherences = new SummaryStatistics();
            SummaryStatistics expectedTrainFrequency = new SummaryStatistics();
            SummaryStatistics scheduleAdherences = new SummaryStatistics();

            for (SystemMetrics.DirectionMetrics stationDirectionMetricsForLineAndDirection : stationDirectionMetricsListForLineAndDirection) {
                directionMetricsForLineAndDirection.setNumTrains(directionMetricsForLineAndDirection.getNumTrains() + stationDirectionMetricsForLineAndDirection.getNumTrains());
                directionMetricsForLineAndDirection.setNumEightCarTrains(directionMetricsForLineAndDirection.getNumEightCarTrains() + stationDirectionMetricsForLineAndDirection.getNumEightCarTrains());
                directionMetricsForLineAndDirection.setNumDelayedTrains(directionMetricsForLineAndDirection.getNumDelayedTrains() + stationDirectionMetricsForLineAndDirection.getNumDelayedTrains());

                for (Integer delayTime : stationDirectionMetricsForLineAndDirection.getDelayTimes()) {
                    delayTimes.addValue(delayTime);
                }

                for (Double onTimePerformance : stationDirectionMetricsForLineAndDirection.getOnTimePerformances()) {
                    onTimePerformances.addValue(onTimePerformance);
                }

                Double stationHeadwayAdherence = stationDirectionMetricsForLineAndDirection.getAverageHeadwayAdherence();
                if (stationHeadwayAdherence != null && !stationHeadwayAdherence.isNaN()) {
                    headwayAdherences.addValue(stationHeadwayAdherence);
                }

                Double stationAverageMinimumHeadways = stationDirectionMetricsForLineAndDirection.getAverageMinimumHeadways();
                if (stationAverageMinimumHeadways != null && !stationAverageMinimumHeadways.isNaN()) {
                    minimumHeadways.addValue(stationAverageMinimumHeadways);
                }

                Double stationTrainFrequency = stationDirectionMetricsForLineAndDirection.getAverageTrainFrequency();
                if (stationTrainFrequency != null) {
                    trainFrequencies.addValue(stationTrainFrequency);
                }

                Double stationExpectedTrainFrequency = stationDirectionMetricsForLineAndDirection.getExpectedTrainFrequency();
                if (stationExpectedTrainFrequency != null && !stationExpectedTrainFrequency.isNaN()) {
                    expectedTrainFrequency.addValue(stationExpectedTrainFrequency);
                }

                Double stationScheduleAdherence = stationDirectionMetricsForLineAndDirection.getAverageScheduleAdherence();
                if (stationScheduleAdherence != null && !stationScheduleAdherence.isNaN()) {
                    scheduleAdherences.addValue(stationScheduleAdherence);
                }
            }

            int numEightCarTrains = directionMetricsForLineAndDirection.getNumEightCarTrains();
            int numNonEightCarTrains = directionMetricsForLineAndDirection.getNumTrains() - numEightCarTrains;
            directionMetricsForLineAndDirection.setNumCars((numNonEightCarTrains * 6) + (numEightCarTrains * 8));

            directionMetricsForLineAndDirection.setExpectedNumTrains(this.gtfsService.getExpectedNumTrains(lineCode, directionNumber));

            directionMetricsForLineAndDirection.setMinimumTrainDelay((delayTimes.getN() <= 0 || Double.isNaN(delayTimes.getMin())) ? null : (int) Math.round(delayTimes.getMin()));
            directionMetricsForLineAndDirection.setMaximumTrainDelay((delayTimes.getN() <= 0 || Double.isNaN(delayTimes.getMax())) ? null : (int) Math.round(delayTimes.getMax()));
            directionMetricsForLineAndDirection.setMedianTrainDelay((delayTimes.getN() <= 0 || Double.isNaN(delayTimes.getPercentile(50))) ? null : (int) Math.round(delayTimes.getMean()));
            directionMetricsForLineAndDirection.setAverageTrainDelay((delayTimes.getN() <= 0 || Double.isNaN(delayTimes.getMean())) ? null : (int) Math.round(delayTimes.getMean()));
            directionMetricsForLineAndDirection.setAverageMinimumHeadways((minimumHeadways.getN() <= 0 || Double.isNaN(minimumHeadways.getMean())) ? null : minimumHeadways.getMean());

            double averageTrainFrequency = trainFrequencies.getMean();
            if (trainFrequencies.getN() > 0 && !Double.isNaN(averageTrainFrequency)) {
                directionMetricsForLineAndDirection.setAverageTrainFrequency(averageTrainFrequency);

                double trainFrequencyStandardDeviation = trainFrequencies.getStandardDeviation();
                if (!Double.isNaN(trainFrequencyStandardDeviation)) {
                    directionMetricsForLineAndDirection.setStandardDeviationTrainFrequency(trainFrequencyStandardDeviation);

                    double waitTimesCalculation = averageTrainFrequency * (1d + (trainFrequencyStandardDeviation * trainFrequencyStandardDeviation) / (averageTrainFrequency * averageTrainFrequency)) / 2d;
                    directionMetricsForLineAndDirection.setAveragePlatformWaitTime(waitTimesCalculation);
                }
            }

            double averageExpectedTrainFrequency = expectedTrainFrequency.getMean();
            if (expectedTrainFrequency.getN() > 0 && !Double.isNaN(averageExpectedTrainFrequency)) {
                directionMetricsForLineAndDirection.setExpectedTrainFrequency(averageExpectedTrainFrequency);

                double expectedTrainFrequencyStandardDeviation = expectedTrainFrequency.getStandardDeviation();
                if (!Double.isNaN(expectedTrainFrequencyStandardDeviation)) {
                    directionMetricsForLineAndDirection.setExpectedStandardDeviationTrainFrequency(expectedTrainFrequencyStandardDeviation);

                    double expectedWaitTimesCalculation = averageExpectedTrainFrequency * (1d + (expectedTrainFrequencyStandardDeviation * expectedTrainFrequencyStandardDeviation) / (averageExpectedTrainFrequency * averageExpectedTrainFrequency)) / 2d;
                    directionMetricsForLineAndDirection.setExpectedPlatformWaitTime(expectedWaitTimesCalculation);
                }
            }

            double averageOnTimePerformance = onTimePerformances.getMean();
            if (onTimePerformances.getN() > 0 && !Double.isNaN(averageOnTimePerformance)) {
                directionMetricsForLineAndDirection.setAverageOnTimePerformance(averageOnTimePerformance);
            }

            double averageHeadwayAdherence = headwayAdherences.getMean();
            if (headwayAdherences.getN() > 0 && !Double.isNaN(averageHeadwayAdherence)) {
                directionMetricsForLineAndDirection.setAverageHeadwayAdherence(averageHeadwayAdherence);
            }

            double averageScheduleAdherence = scheduleAdherences.getMean();
            if (scheduleAdherences.getN() > 0 && !Double.isNaN(averageScheduleAdherence)) {
                directionMetricsForLineAndDirection.setAverageScheduleAdherence(averageScheduleAdherence);
            }

            directionMetricsForLineAndDirection.calculateWaitTimeStatus();

            if (platformWaitTimeTrendStatusByLineAndDirection != null) {
                directionMetricsForLineAndDirection.setPlatformWaitTimeTrendStatus(platformWaitTimeTrendStatusByLineAndDirection.get(lineCodeAndDirectionNumber));
            }

            SystemMetrics.LineMetrics lineMetrics = lineMetricsByLine.get(lineCode);
            lineMetrics.getDirectionMetricsByDirection().put(directionNumber, directionMetricsForLineAndDirection);
        }

        Map<String, SystemMetrics.TrendStatus> platformWaitTimeTrendStatusByLine = null;

        List<Object[]> platformWaitTimeTrendStatusByLineResults = this.systemMetricsRepository.getPlatformWaitTimeTrendStatusDataByLine();
        if (platformWaitTimeTrendStatusByLineResults != null) {
            platformWaitTimeTrendStatusByLine = new HashMap<>();
            for (Object[] platformWaitTimeTrendStatusByLineResult : platformWaitTimeTrendStatusByLineResults) {
                String lineCode = (String) platformWaitTimeTrendStatusByLineResult[0];
                Boolean trendingDown = (Boolean) platformWaitTimeTrendStatusByLineResult[1];
                Boolean trendingUp = (Boolean) platformWaitTimeTrendStatusByLineResult[2];

                SystemMetrics.TrendStatus trendStatus;
                if (trendingDown) {
                    trendStatus = SystemMetrics.TrendStatus.DECREASING;
                } else if (trendingUp) {
                    trendStatus = SystemMetrics.TrendStatus.INCREASING;
                } else {
                    trendStatus = SystemMetrics.TrendStatus.NEUTRAL;
                }
                platformWaitTimeTrendStatusByLine.put(lineCode, trendStatus);
            }
        }

        // calculate line metrics using direction metrics
        for (Map.Entry<String, SystemMetrics.LineMetrics> entry : lineMetricsByLine.entrySet()) {
            String lineCode = entry.getKey();
            SystemMetrics.LineMetrics lineMetricsForLine = entry.getValue();

            for (SystemMetrics.DirectionMetrics directionMetricsForLine : lineMetricsForLine.getDirectionMetricsByDirection().values()) {
                lineMetricsForLine.setNumTrains(lineMetricsForLine.getNumTrains() + directionMetricsForLine.getNumTrains());
                lineMetricsForLine.setNumEightCarTrains(lineMetricsForLine.getNumEightCarTrains() + directionMetricsForLine.getNumEightCarTrains());
                lineMetricsForLine.setNumDelayedTrains(lineMetricsForLine.getNumDelayedTrains() + directionMetricsForLine.getNumDelayedTrains());
            }

            int numEightCarTrains = lineMetricsForLine.getNumEightCarTrains();
            int numNonEightCarTrains = lineMetricsForLine.getNumTrains() - numEightCarTrains;
            lineMetricsForLine.setNumCars((numNonEightCarTrains * 6) + (numEightCarTrains * 8));

            Integer minimumDelayTime = null;
            Integer maximumDelayTime = null;
            SummaryStatistics averageDelayTimes = new SummaryStatistics();
            DescriptiveStatistics medianDelayTimes = new DescriptiveStatistics();
            SummaryStatistics averageMinimumHeadways = new SummaryStatistics();
            SummaryStatistics averageTrainFrequencies = new SummaryStatistics();
            SummaryStatistics averagePlatformWaitTimes = new SummaryStatistics();
            SummaryStatistics expectedTrainFrequencies = new SummaryStatistics();
            SummaryStatistics expectedPlatformWaitTime = new SummaryStatistics();
            SummaryStatistics averageOnTimePerformance = new SummaryStatistics();
            SummaryStatistics averageHeadwayAdherence = new SummaryStatistics();
            SummaryStatistics averageScheduleAdherence = new SummaryStatistics();
            SummaryStatistics standardDeviationTrainFrequencies = new SummaryStatistics();
            SummaryStatistics expectedStandardDeviationTrainFrequencies = new SummaryStatistics();

            for (SystemMetrics.DirectionMetrics directionMetricsForLine : lineMetricsForLine.getDirectionMetricsByDirection().values()) {
                if (directionMetricsForLine.getMinimumTrainDelay() != null && (minimumDelayTime == null || directionMetricsForLine.getMinimumTrainDelay() < minimumDelayTime)) {
                    minimumDelayTime = directionMetricsForLine.getMinimumTrainDelay();
                }

                if (directionMetricsForLine.getMaximumTrainDelay() != null && (maximumDelayTime == null || directionMetricsForLine.getMaximumTrainDelay() > maximumDelayTime)) {
                    maximumDelayTime = directionMetricsForLine.getMaximumTrainDelay();
                }

                if (directionMetricsForLine.getAverageTrainDelay() != null) {
                    averageDelayTimes.addValue(directionMetricsForLine.getAverageTrainDelay());
                }

                if (directionMetricsForLine.getMedianTrainDelay() != null) {
                    medianDelayTimes.addValue(directionMetricsForLine.getMedianTrainDelay());
                }

                if (directionMetricsForLine.getAverageMinimumHeadways() != null) {
                    averageMinimumHeadways.addValue(directionMetricsForLine.getAverageMinimumHeadways());
                }

                if (directionMetricsForLine.getAverageTrainFrequency() != null) {
                    averageTrainFrequencies.addValue(directionMetricsForLine.getAverageTrainFrequency());
                }

                if (directionMetricsForLine.getAveragePlatformWaitTime() != null) {
                    averagePlatformWaitTimes.addValue(directionMetricsForLine.getAveragePlatformWaitTime());
                }

                if (directionMetricsForLine.getExpectedTrainFrequency() != null) {
                    expectedTrainFrequencies.addValue(directionMetricsForLine.getExpectedTrainFrequency());
                }

                if (directionMetricsForLine.getExpectedPlatformWaitTime() != null) {
                    expectedPlatformWaitTime.addValue(directionMetricsForLine.getExpectedPlatformWaitTime());
                }

                if (directionMetricsForLine.getAverageOnTimePerformance() != null) {
                    averageOnTimePerformance.addValue(directionMetricsForLine.getAverageOnTimePerformance());
                }

                if (directionMetricsForLine.getAverageHeadwayAdherence() != null) {
                    averageHeadwayAdherence.addValue(directionMetricsForLine.getAverageHeadwayAdherence());
                }

                if (directionMetricsForLine.getAverageScheduleAdherence() != null) {
                    averageScheduleAdherence.addValue(directionMetricsForLine.getAverageScheduleAdherence());
                }

                if (directionMetricsForLine.getStandardDeviationTrainFrequency() != null) {
                    standardDeviationTrainFrequencies.addValue(directionMetricsForLine.getStandardDeviationTrainFrequency());
                }

                if (directionMetricsForLine.getExpectedStandardDeviationTrainFrequency() != null) {
                    expectedStandardDeviationTrainFrequencies.addValue(directionMetricsForLine.getExpectedStandardDeviationTrainFrequency());
                }
            }

            lineMetricsForLine.setMinimumTrainDelay(minimumDelayTime);
            lineMetricsForLine.setMaximumTrainDelay(maximumDelayTime);
            lineMetricsForLine.setAverageTrainDelay((averageDelayTimes.getN() <= 0 || Double.isNaN(averageDelayTimes.getMean())) ? null : (int) Math.round(averageDelayTimes.getMean()));
            lineMetricsForLine.setMedianTrainDelay((medianDelayTimes.getN() <= 0 || Double.isNaN(medianDelayTimes.getPercentile(50))) ? null : (int) Math.round(medianDelayTimes.getPercentile(50)));
            lineMetricsForLine.setAverageMinimumHeadways((averageMinimumHeadways.getN() <= 0 || Double.isNaN(averageMinimumHeadways.getMean())) ? null : averageMinimumHeadways.getMean());
            lineMetricsForLine.setAverageTrainFrequency((averageTrainFrequencies.getN() <= 0 || Double.isNaN(averageTrainFrequencies.getMean())) ? null : averageTrainFrequencies.getMean());
            lineMetricsForLine.setAveragePlatformWaitTime((averagePlatformWaitTimes.getN() <= 0 || Double.isNaN(averagePlatformWaitTimes.getMean())) ? null : averagePlatformWaitTimes.getMean());
            lineMetricsForLine.setExpectedTrainFrequency((expectedTrainFrequencies.getN() <= 0 || Double.isNaN(expectedTrainFrequencies.getMean())) ? null : expectedTrainFrequencies.getMean());
            lineMetricsForLine.setExpectedPlatformWaitTime((expectedPlatformWaitTime.getN() <= 0 || Double.isNaN(expectedPlatformWaitTime.getMean())) ? null : expectedPlatformWaitTime.getMean());
            lineMetricsForLine.setAverageOnTimePerformance((averageOnTimePerformance.getN() <= 0 || Double.isNaN(averageOnTimePerformance.getMean())) ? null : averageOnTimePerformance.getMean());
            lineMetricsForLine.setAverageHeadwayAdherence((averageHeadwayAdherence.getN() <= 0 || Double.isNaN(averageHeadwayAdherence.getMean())) ? null : averageHeadwayAdherence.getMean());
            lineMetricsForLine.setAverageScheduleAdherence((averageScheduleAdherence.getN() <= 0 || Double.isNaN(averageScheduleAdherence.getMean())) ? null : averageScheduleAdherence.getMean());
            lineMetricsForLine.setStandardDeviationTrainFrequency((standardDeviationTrainFrequencies.getN() <= 0 || Double.isNaN(standardDeviationTrainFrequencies.getMean())) ? null : standardDeviationTrainFrequencies.getMean());
            lineMetricsForLine.setExpectedStandardDeviationTrainFrequency((expectedStandardDeviationTrainFrequencies.getN() <= 0 || Double.isNaN(expectedStandardDeviationTrainFrequencies.getMean())) ? null : expectedStandardDeviationTrainFrequencies.getMean());

            lineMetricsForLine.setExpectedNumTrains(this.gtfsService.getExpectedNumTrains(lineCode));
            lineMetricsForLine.calculateWaitTimeStatus();

            if (platformWaitTimeTrendStatusByLine != null) {
                lineMetricsForLine.setPlatformWaitTimeTrendStatus(platformWaitTimeTrendStatusByLine.get(lineCode));
            }
        }

        // sort direction metrics map for each line in descending alphabetical order
        for (SystemMetrics.LineMetrics lineMetrics : lineMetricsByLine.values()) {
            List<Map.Entry<Integer, SystemMetrics.DirectionMetrics>> entries = new ArrayList<>(lineMetrics.getDirectionMetricsByDirection().entrySet());
            lineMetrics.getDirectionMetricsByDirection().clear();
            entries.stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.comparing(SystemMetrics.DirectionMetrics::getDirection)))
                    .forEachOrdered(e -> lineMetrics.getDirectionMetricsByDirection().put(e.getKey(), e.getValue()));
        }

        // calculate service gaps by line and direction
        for (String lineCode : lineMetricsByLine.keySet()) {
            SystemMetrics.LineMetrics lineMetrics = lineMetricsByLine.get(lineCode);

            Set<ServiceGap> serviceGaps = new HashSet<>();

            for (Integer directionNumber : Arrays.asList(1, 2)) {
                HashMap<String, Double> timeBetweenTrainsByFromTrainId = new HashMap<>();
                HashMap<String, ServiceGap> serviceGapByFromTrainId = new HashMap<>();

                // for every destination station, calculate possible service gaps
                for (String stationCode : this.trainService.getStationCodesSet()) {
                    List<TrainStatus> stationTrainStatuses = this.trainService.getStationTrainStatusesMap().get(stationCode);
                    if (stationTrainStatuses == null) {
                        continue;
                    }

                    for (int i = 0; i < stationTrainStatuses.size(); i++) {
                        TrainStatus trainStatus1 = stationTrainStatuses.get(i);

                        if (!lineCode.equals(trainStatus1.getLine())) {
                            continue;
                        }

                        if (!directionNumber.equals(trainStatus1.getDirectionNumber())) {
                            continue;
                        }

                        if (trainStatus1.isScheduled()) {
                            continue;
                        }

                        for (int j = i + 1; j < stationTrainStatuses.size(); j++) {
                            TrainStatus trainStatus2 = stationTrainStatuses.get(j);

                            if (Objects.equals(trainStatus1.getTrainId(), trainStatus2.getTrainId())) {
                                // sanity check
                                continue;
                            }

                            if (!Objects.equals(trainStatus1.getLine(), trainStatus2.getLine())) {
                                continue;
                            }

                            if (!Objects.equals(trainStatus1.getDirectionNumber(), trainStatus2.getDirectionNumber())) {
                                continue;
                            }

                            if (trainStatus2.isScheduled()) {
                                continue;
                            }

                            // found a pair of trains of the same type
                            // set up the next pair of trains for analysis, starting with this 'to' train as the next 'from' train
                            i = j - 1;  // (the for loop for i later increments i by 1, so setting i = j-1 here => i = j)

                            double timeBetweenTrains = trainStatus2.getMinutesAway() - trainStatus1.getMinutesAway();

                            Double previouslyCalculatedTimeBetweenTrains = timeBetweenTrainsByFromTrainId.get(trainStatus2.getTrainId());
                            if (previouslyCalculatedTimeBetweenTrains != null) {
                                // we've previously evaluated this 'from' train and (possibly) a different 'to' train for a service gap...
                                if (timeBetweenTrains < previouslyCalculatedTimeBetweenTrains) {
                                    // ...but our previous evaluation was incorrect; this 'from' train actually has a closer 'to' train in front on it
                                    // so, let's re-evaluate using this closer 'to' train, which may or may not reveal a service gap
                                    serviceGapByFromTrainId.remove(trainStatus2.getTrainId());
                                } else {
                                    // ...and that previous evaluation appears to have been correct, so skip this pair of trains
                                    break;
                                }
                            }

                            timeBetweenTrainsByFromTrainId.put(trainStatus2.getTrainId(), timeBetweenTrains);

                            if (Objects.equals(trainStatus1.getCurrentStationCode(), trainStatus2.getCurrentStationCode())) {
                                // service gaps whose length fits entirely between two stations is technically possible, but would be confusing to present to users
                                // (such service gaps don't occur frequently, if at all, in practice anyway)
                                break;
                            }

                            Calendar estimatedToTrainArrival = Calendar.getInstance();
                            estimatedToTrainArrival.add(Calendar.SECOND, (int) Math.round(trainStatus1.getMinutesAway() * 60));
                            Double expectedTimeBetweenTrains = this.gtfsService.getExpectedTrainFrequency(estimatedToTrainArrival, trainStatus1.getLine(), trainStatus1.getDirectionNumber(), trainStatus1.getCurrentStationCode());
                            if (expectedTimeBetweenTrains == null) {
                                // we don't know how frequently to expect these type of trains based on the train schedule,
                                // so we don't know if there's a service gap between these two trains or not
                                break;
                            }

                            double timeBetweenTrainsThreshold = expectedTimeBetweenTrains * 1.5;
                            if (timeBetweenTrains < timeBetweenTrainsThreshold) {
                                // there is no significant service gap between these two trains
                                break;
                            }

                            String direction = StationUtil.getDirectionName(trainStatus1.getLine(), trainStatus1.getDirectionNumber());
                            if (direction == null) {
                                // sanity check
                                break;
                            }

                            ServiceGap serviceGap = new ServiceGap(trainStatus1.getLine(), trainStatus1.getDirectionNumber(), direction, trainStatus2.getCurrentStationCode(), StationUtil.getStationName(trainStatus2.getCurrentStationCode()), trainStatus1.getCurrentStationCode(), StationUtil.getStationName(trainStatus1.getCurrentStationCode()), trainStatus2.getTrainId(), trainStatus1.getTrainId(), timeBetweenTrains, expectedTimeBetweenTrains, now);
                            serviceGapByFromTrainId.put(serviceGap.getFromTrainId(), serviceGap);
                            break;
                        }
                    }
                }

                for (ServiceGap serviceGap : serviceGapByFromTrainId.values()) {
                    this.twitterBotService.tweetServiceGap(serviceGap);
                }

                serviceGaps.addAll(serviceGapByFromTrainId.values());
            }

            lineMetrics.getServiceGaps().addAll(serviceGaps);
            lineMetrics.getServiceGaps().sort(Comparator.comparingDouble(ServiceGap::getTimeBetweenTrains).reversed());
        }

        // persist to database
        SystemMetrics systemMetrics = new SystemMetrics(lineMetricsByLine, now);
        this.systemMetrics = systemMetrics;
        this.systemMetricsRepository.save(systemMetrics);

        updateRecentTrainFrequencyData();

        logger.info("...successfully updated Dashboard metrics!");
    }

    @Scheduled(fixedDelay = 30000)  // every 30 seconds
    private void updatePerformanceSummaryData() {
        List<Object[]> results = this.systemMetricsRepository.getPerformanceSummary();
        if (results == null || results.size() <= 0) {
            return;
        }

        List<PerformanceSummary> performanceSummaries = new ArrayList<>();
        for (Object[] performanceSummaryData : results) {
            performanceSummaries.add(new PerformanceSummary(performanceSummaryData));
        }

        this.performanceSummaryData = performanceSummaries;
    }

    private void updateRecentTrainFrequencyData() {
        List<Object[]> results = this.systemMetricsRepository.getRecentTrainFrequencyData();
        if (results == null || results.size() != 1) {
            return;
        }

        this.recentTrainFrequencyData = new RecentTrainFrequencyData(results.get(0));
    }

    public SystemMetrics getSystemMetrics() {
        return systemMetrics;
    }

    public RecentTrainFrequencyData getRecentTrainFrequencyData() {
        return recentTrainFrequencyData;
    }

    public List<PerformanceSummary> getPerformanceSummary() {
        if (this.performanceSummaryData == null) {
            // periodic update hasn't run yet, so force update now and return that
            updatePerformanceSummaryData();
        }

        return this.performanceSummaryData;
    }
}

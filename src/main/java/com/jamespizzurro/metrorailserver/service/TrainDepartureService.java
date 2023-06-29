package com.jamespizzurro.metrorailserver.service;

import com.jamespizzurro.metrorailserver.BeanPropertyRowMapper;
import com.jamespizzurro.metrorailserver.domain.TrainDepartureInfo;
import com.jamespizzurro.metrorailserver.domain.TrainDepartureMetrics;
import com.jamespizzurro.metrorailserver.domain.TrainDepartures;
import com.jamespizzurro.metrorailserver.repository.TrainDepartureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toCollection;

@Service
public class TrainDepartureService {

    private static final Logger logger = LoggerFactory.getLogger(TrainDepartureService.class);

    private final EntityManager entityManager;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final TrainDepartureRepository trainDepartureRepository;

    private volatile Map<String, List<TrainDepartureInfo>> recentTrainDepartureInfoByLine;

    @Autowired
    public TrainDepartureService(EntityManager entityManager, NamedParameterJdbcTemplate namedParameterJdbcTemplate, TrainDepartureRepository trainDepartureRepository) {
        this.entityManager = entityManager;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.trainDepartureRepository = trainDepartureRepository;
    }

    @PostConstruct
    private void init() {
        logger.info("Initializing train departure service...");

        this.recentTrainDepartureInfoByLine = new HashMap<>(0);

        logger.info("...train departure service initialized!");
    }

    public Map<String, List<TrainDepartureInfo>> getTrainDepartureInfoByLine(String departureStationCode, String lineCode, Integer directionNumber, String destinationStationCode) {
        if (StringUtils.isEmpty(departureStationCode) && StringUtils.isEmpty(lineCode) && (directionNumber == null) && StringUtils.isEmpty(destinationStationCode)) {
            // nothing to filter, so just return the cached results as is
            return this.recentTrainDepartureInfoByLine;
        }

        return this.recentTrainDepartureInfoByLine.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .filter(trainDepartureInfo ->
                                        (StringUtils.isEmpty(departureStationCode) || departureStationCode.equals(trainDepartureInfo.getDepartureStationCode())) &&
                                        (StringUtils.isEmpty(lineCode) || lineCode.equals(trainDepartureInfo.getLineCode())) &&
                                        ((directionNumber == null) || directionNumber.equals(trainDepartureInfo.getDirectionNumber())) &&
                                        (StringUtils.isEmpty(destinationStationCode) || (destinationStationCode.equals(trainDepartureInfo.getObservedDestinationStationCode()) || destinationStationCode.equals(trainDepartureInfo.getScheduledDestinationStationCode())))
                                )
                                .collect(Collectors.toList())
                )
        );
    }

    public Calendar getEarliestDepartureTimeString() {
        String query = (
                "SELECT coalesce(observed_departure_time, scheduled_departure_time) " +
                "FROM train_departure_info " +
                "ORDER BY coalesce(observed_departure_time, scheduled_departure_time) " +
                "LIMIT 1"
        );

        Object earliestDepartureTime = this.entityManager.createNativeQuery(query).getSingleResult();
        if (earliestDepartureTime == null) {
            return null;
        }

        Calendar earliestDepartureTimeCalendar = Calendar.getInstance();
        earliestDepartureTimeCalendar.setTime(new Date(((Timestamp) earliestDepartureTime).getTime()));
        return earliestDepartureTimeCalendar;
    }

    public TrainDepartureMetrics getDepartureMetrics(@NotNull Long fromDateUnixTimestamp, @NotNull Long toDateUnixTimestamp, String departureStationCode, String lineCode, Integer directionNumber) {
        String query = (
                "SELECT " +
                "  num_observed_departures, " +
                "  num_scheduled_departures, " +
                "  num_missed_departures, " +
                "  pct_missed_departures, " +
                "  num_unscheduled_departures, " +
                "  pct_unscheduled_departures, " +
                "  avg_observed_train_frequency, " +
                "  avg_scheduled_train_frequency, " +
                "  avg_train_frequency_percent_variance, " +
                "  stddev_observed_train_frequency AS observed_train_frequency_consistency, " +
                "  stddev_scheduled_train_frequency AS scheduled_train_frequency_consistency, " +
                "  stddev_train_frequency_percent_variance AS train_frequency_consistency_percent_variance, " +
                "  avg_observed_platform_wait_time, " +
                "  avg_scheduled_platform_wait_time, " +
                "  avg_platform_wait_time_percent_variance, " +
                "  num_on_time_or_early_departures_by_headway_adherence AS pct_headway_adherence, " +
                "  avg_headway_deviation, " +
                "  num_on_time_or_early_departures_by_headway_adherence, " +
                "  pct_on_time_or_early_departures_by_headway_adherence, " +
                "  num_late_departures_by_headway_adherence, " +
                "  pct_late_departures_by_headway_adherence, " +
                "  num_very_late_departures_by_headway_adherence, " +
                "  pct_very_late_departures_by_headway_adherence, " +
                "  num_on_time_departures_by_schedule_adherence AS pct_schedule_adherence, " +
                "  avg_schedule_deviation, " +
                "  num_on_time_departures_by_schedule_adherence, " +
                "  pct_on_time_departures_by_schedule_adherence, " +
                "  num_off_schedule_departures_by_schedule_adherence, " +
                "  pct_off_schedule_departures_by_schedule_adherence, " +
                "  num_very_off_schedule_departures_by_schedule_adherence, " +
                "  pct_very_off_schedule_departures_by_schedule_adherence " +
                "FROM ( " +
                "  SELECT " +
                "    *, " +
                "    (((avg_observed_platform_wait_time - avg_scheduled_platform_wait_time) / nullif(avg_scheduled_platform_wait_time, 0)) * 100) AS avg_platform_wait_time_percent_variance " +
                "  FROM ( " +
                "    SELECT " +
                "      *, " +
                "      ((num_missed_departures / nullif(CAST(num_scheduled_departures AS NUMERIC), 0)) * 100) AS pct_missed_departures, " +
                "      ((num_unscheduled_departures / nullif(CAST(num_observed_departures AS NUMERIC), 0)) * 100) AS pct_unscheduled_departures, " +
                "      (((avg_observed_train_frequency - avg_scheduled_train_frequency) / nullif(avg_scheduled_train_frequency, 0)) * 100) AS avg_train_frequency_percent_variance, " +
                "      (((stddev_observed_train_frequency - stddev_scheduled_train_frequency) / nullif(stddev_scheduled_train_frequency, 0)) * 100) AS stddev_train_frequency_percent_variance, " +
                "      (avg_observed_train_frequency * (1 + (stddev_observed_train_frequency * stddev_observed_train_frequency) / nullif(avg_observed_train_frequency * avg_observed_train_frequency, 0)) / 2) AS avg_observed_platform_wait_time, " +
                "      (avg_scheduled_train_frequency * (1 + (stddev_scheduled_train_frequency * stddev_scheduled_train_frequency) / nullif(avg_scheduled_train_frequency * avg_scheduled_train_frequency, 0)) / 2) AS avg_scheduled_platform_wait_time, " +
                "      ((num_on_time_or_early_departures_by_headway_adherence / nullif(CAST(num_observed_departures AS NUMERIC), 0)) * 100) AS pct_on_time_or_early_departures_by_headway_adherence, " +
                "      ((num_late_departures_by_headway_adherence / nullif(CAST(num_observed_departures AS NUMERIC), 0)) * 100) AS pct_late_departures_by_headway_adherence, " +
                "      ((num_very_late_departures_by_headway_adherence / nullif(CAST(num_observed_departures AS NUMERIC), 0)) * 100) AS pct_very_late_departures_by_headway_adherence, " +
                "      ((num_on_time_departures_by_schedule_adherence / nullif(CAST(num_observed_departures AS NUMERIC), 0)) * 100) AS pct_on_time_departures_by_schedule_adherence, " +
                "      ((num_off_schedule_departures_by_schedule_adherence / nullif(CAST(num_observed_departures AS NUMERIC), 0)) * 100) AS pct_off_schedule_departures_by_schedule_adherence, " +
                "      ((num_very_off_schedule_departures_by_schedule_adherence / nullif(CAST(num_observed_departures AS NUMERIC), 0)) * 100) AS pct_very_off_schedule_departures_by_schedule_adherence " +
                "    FROM ( " +
                "      SELECT " +
                "        count(CASE WHEN (observed_departure_time IS NOT NULL) THEN 1 END) AS num_observed_departures, " +
                "        count(CASE WHEN (scheduled_departure_time IS NOT NULL) THEN 1 END) AS num_scheduled_departures, " +
                "        count(CASE WHEN (scheduled_departure_time IS NOT NULL AND observed_departure_time IS NULL) THEN 1 END) AS num_missed_departures, " +
                "        count(CASE WHEN (observed_departure_time IS NOT NULL AND scheduled_departure_time IS NULL) THEN 1 END) AS num_unscheduled_departures, " +
                "        avg(observed_time_since_last_departure) AS avg_observed_train_frequency, " +
                "        avg(scheduled_time_since_last_departure) AS avg_scheduled_train_frequency, " +
                "        stddev(observed_time_since_last_departure) AS stddev_observed_train_frequency, " +
                "        stddev(scheduled_time_since_last_departure) AS stddev_scheduled_train_frequency, " +
                "        avg(headway_deviation) AS avg_headway_deviation, " +
                "        count(CASE WHEN (headway_deviation <= 2) THEN 1 END) AS num_on_time_or_early_departures_by_headway_adherence, " +
                "        count(CASE WHEN (headway_deviation > 2 AND headway_deviation <= 4) THEN 1 END) AS num_late_departures_by_headway_adherence, " +
                "        count(CASE WHEN (headway_deviation > 4) THEN 1 END) AS num_very_late_departures_by_headway_adherence, " +
                "        avg(schedule_deviation) AS avg_schedule_deviation, " +
                "        count(CASE WHEN (abs(schedule_deviation) <= 2) THEN 1 END) AS num_on_time_departures_by_schedule_adherence, " +
                "        count(CASE WHEN (abs(schedule_deviation) > 2 AND abs(schedule_deviation) <= 4) THEN 1 END) AS num_off_schedule_departures_by_schedule_adherence, " +
                "        count(CASE WHEN (abs(schedule_deviation) > 4) THEN 1 END) AS num_very_off_schedule_departures_by_schedule_adherence " +
                "      FROM train_departure_info " +
                "      WHERE " +
                "        coalesce(observed_departure_time, scheduled_departure_time) BETWEEN to_timestamp(cast(:fromDateUnixTimestamp AS FLOAT)) AND to_timestamp(cast(:toDateUnixTimestamp AS FLOAT)) AND " +
                "        (cast(:departureStationCode AS TEXT) IS NULL OR departure_station_code = cast(:departureStationCode AS TEXT)) AND " +
                "        (cast(:lineCode AS TEXT) IS NULL OR (CASE WHEN (line_code IS NOT NULL) THEN (line_code = cast(:lineCode AS TEXT)) ELSE (cast(:lineCode AS TEXT) = 'N/A') END)) AND " +
                "        (cast(:directionNumber AS INT) IS NULL OR direction_number = cast(:directionNumber AS INT)) " +
                "    ) AS t " +
                "  ) AS t " +
                ") AS t"
        );

        Map<String, Object> params = new HashMap<>();
        params.put("fromDateUnixTimestamp", fromDateUnixTimestamp);
        params.put("toDateUnixTimestamp", toDateUnixTimestamp);
        params.put("departureStationCode", departureStationCode);
        params.put("lineCode", lineCode);
        params.put("directionNumber", directionNumber);

        return (TrainDepartureMetrics) this.namedParameterJdbcTemplate.queryForObject(query, params, new BeanPropertyRowMapper<>(TrainDepartureMetrics.class));
    }

    public TrainDepartures getDeparturesData(@NotNull Long fromDateUnixTimestamp, @NotNull Long toDateUnixTimestamp, String departureStationCode, String lineCode, Integer directionNumber, @NotNull String sortByColumn, @NotNull String sortByOrder, @NotNull Integer maxResultCount, @NotNull Integer resultCountOffset) {
        Integer totalNumDepartures = getTotalNumDepartures(fromDateUnixTimestamp, toDateUnixTimestamp, departureStationCode, lineCode, directionNumber);
        if (totalNumDepartures <= 0) {
            return new TrainDepartures(new ArrayList<>(), 0);
        }

        List<TrainDepartureInfo> getDepartures = getDepartures(fromDateUnixTimestamp, toDateUnixTimestamp, departureStationCode, lineCode, directionNumber, sortByColumn, sortByOrder, maxResultCount, resultCountOffset);

        return new TrainDepartures(getDepartures, totalNumDepartures);
    }

    @Scheduled(fixedDelay = 5000)   // runs every 5 seconds
    public void updateTrainDepartureInfo() {
        logger.info("Updating train departure info...");

        this.trainDepartureRepository.updateTrainDepartureInfo();

        // next, update our recent train departure info cache

        String query = (
                "SELECT * " +
                "FROM train_departure_info " +
                "WHERE coalesce(observed_departure_time, scheduled_departure_time) BETWEEN (now() - INTERVAL '1 hour') AND now() " +
                "ORDER BY coalesce(observed_departure_time, scheduled_departure_time)"
        );

        // group results by line name
        List<TrainDepartureInfo> trainDepartureInfoList = this.namedParameterJdbcTemplate.query(query, new BeanPropertyRowMapper<>(TrainDepartureInfo.class));
        this.recentTrainDepartureInfoByLine = trainDepartureInfoList.stream().collect(groupingBy(TrainDepartureInfo::getLineNameNonNull, toCollection(ArrayList::new)));

        logger.info("...successfully updated train departure info!");
    }

    @Scheduled(cron = "0 0 3 * * *")  // runs at 3am every day
    private void reindexTables() {
        logger.info("Reindexing train departure tables...");
        this.trainDepartureRepository.reindexTrainDepartureTable();
        this.trainDepartureRepository.reindexTrainDepartureInfoTable();
        logger.info("...successfully reindexed train departure tables!");
    }

    private Integer getTotalNumDepartures(@NotNull Long fromDateUnixTimestamp, @NotNull Long toDateUnixTimestamp, String departureStationCode, String lineCode, Integer directionNumber) {
        String query = (
                "SELECT count(*) " +
                "FROM train_departure_info " +
                "WHERE " +
                "  coalesce(observed_departure_time, scheduled_departure_time) BETWEEN to_timestamp(cast(:fromDateUnixTimestamp AS FLOAT)) AND to_timestamp(cast(:toDateUnixTimestamp AS FLOAT)) AND " +
                "  (:departureStationCode IS NULL OR departure_station_code = cast(:departureStationCode AS TEXT)) AND " +
                "  (:lineCode IS NULL OR (CASE WHEN (line_code IS NOT NULL) THEN (line_code = cast(:lineCode AS TEXT)) ELSE (cast(:lineCode AS TEXT) = 'N/A') END)) AND " +
                "  (:directionNumber = -1 OR direction_number = cast(:directionNumber AS INT))"
        );

        Query q = this.entityManager.createNativeQuery(query);
        q.setParameter("fromDateUnixTimestamp", fromDateUnixTimestamp);
        q.setParameter("toDateUnixTimestamp", toDateUnixTimestamp);
        q.setParameter("departureStationCode", departureStationCode);
        q.setParameter("lineCode", lineCode);
        q.setParameter("directionNumber", (directionNumber != null) ? directionNumber : -1);

        return ((BigInteger) q.getSingleResult()).intValue();
    }

    private List<TrainDepartureInfo> getDepartures(@NotNull Long fromDateUnixTimestamp, @NotNull Long toDateUnixTimestamp, String departureStationCode, String lineCode, Integer directionNumber, @NotNull String sortByColumn, @NotNull String sortByOrder, @NotNull Integer maxResultCount, @NotNull Integer resultCountOffset) {
        String query = (
                "SELECT * " +
                "FROM train_departure_info " +
                "WHERE" +
                "  coalesce(observed_departure_time, scheduled_departure_time) BETWEEN to_timestamp(cast(:fromDateUnixTimestamp AS FLOAT)) AND to_timestamp(cast(:toDateUnixTimestamp AS FLOAT)) AND " +
                "  (cast(:departureStationCode AS TEXT) IS NULL OR departure_station_code = cast(:departureStationCode AS TEXT)) AND " +
                "  (cast(:lineCode AS TEXT) IS NULL OR (CASE WHEN (line_code IS NOT NULL) THEN (line_code = cast(:lineCode AS TEXT)) ELSE (cast(:lineCode AS TEXT) = 'N/A') END)) AND " +
                "  (cast(:directionNumber AS INT) IS NULL OR direction_number = cast(:directionNumber AS INT)) " +
                "ORDER BY " + sortByColumn + " " + sortByOrder + " " +
                "LIMIT " + maxResultCount + " OFFSET " + resultCountOffset
        );

        Map<String, Object> params = new HashMap<>();
        params.put("fromDateUnixTimestamp", fromDateUnixTimestamp);
        params.put("toDateUnixTimestamp", toDateUnixTimestamp);
        params.put("departureStationCode", departureStationCode);
        params.put("lineCode", lineCode);
        params.put("directionNumber", directionNumber);

        return this.namedParameterJdbcTemplate.query(query, params, new BeanPropertyRowMapper<>(TrainDepartureInfo.class));
    }
}

package com.jamespizzurro.metrorailserver.repository;

import com.jamespizzurro.metrorailserver.domain.SystemMetrics;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemMetricsRepository extends CrudRepository<SystemMetrics, Long> {

    @Query(nativeQuery = true, value = "SELECT CAST(min(extract(EPOCH FROM date)) AS text) FROM system_metrics")
    String getEarliestTimestamp();

    @Query(nativeQuery = true, value =
            "SELECT " +
            "  result.line_code AS lineCode, " +
            "  array_to_string(array_agg(extract(epoch from result.timestamp) ORDER BY result.timestamp ASC), ',') AS timestamps, " +
            "  array_to_string(array_agg(coalesce(round(result.num_cars, 2), -1) ORDER BY result.timestamp ASC), ',') AS avgNumCars, " +
            "  array_to_string(array_agg(coalesce(round(result.num_trains, 2), -1) ORDER BY result.timestamp ASC), ',') AS avgNumTrains, " +
            "  array_to_string(array_agg(coalesce(round(result.exp_num_trains, 2), -1) ORDER BY result.timestamp ASC), ',') AS expNumTrains, " +
            "  array_to_string(array_agg(coalesce(round(result.num_delayed_trains, 2), -1) ORDER BY result.timestamp ASC), ',') AS avgNumDelayedTrains, " +
            "  array_to_string(array_agg(coalesce(round(result.num_eight_car_trains, 2), -1) ORDER BY result.timestamp ASC), ',') AS avgNumEightCarTrains, " +
            "  array_to_string(array_agg(coalesce(round(100 * (cast(result.num_delayed_trains AS NUMERIC) / nullif(cast(result.num_trains AS NUMERIC), 0)), 2), -1) ORDER BY result.timestamp ASC), ',') AS avgPercentTrainsDelayed, " +
            "  array_to_string(array_agg(coalesce(round(100 * (cast(result.num_eight_car_trains AS NUMERIC) / nullif(cast(result.num_trains AS NUMERIC), 0)), 2), -1) ORDER BY result.timestamp ASC), ',') AS avgPercentEightCarTrains, " +
            "  array_to_string(array_agg(coalesce(round(result.avg_train_delay, 2), -1) ORDER BY result.timestamp ASC), ',') AS avgTrainDelay, " +
            "  array_to_string(array_agg(coalesce(round(cast(result.avg_minimum_headways AS NUMERIC), 2), -1) ORDER BY result.timestamp ASC), ',') AS avgMinimumHeadways, " +
            "  array_to_string(array_agg(coalesce(round(cast(result.avg_train_frequency AS NUMERIC), 2), -1) ORDER BY result.timestamp ASC), ',') AS avgTrainFrequency, " +
            "  array_to_string(array_agg(coalesce(round(cast(result.exp_train_frequency AS NUMERIC), 2), -1) ORDER BY result.timestamp ASC), ',') AS expTrainFrequency, " +
            "  array_to_string(array_agg(coalesce(round(cast(result.avg_platform_wait_time AS NUMERIC), 2), -1) ORDER BY result.timestamp ASC), ',') AS avgPlatformWaitTime, " +
            "  array_to_string(array_agg(coalesce(round(cast(result.exp_platform_wait_time AS NUMERIC), 2), -1) ORDER BY result.timestamp ASC), ',') AS expPlatformWaitTime, " +
            "  array_to_string(array_agg(coalesce(round(cast(result.avg_headway_adherence AS NUMERIC), 2), -1) ORDER BY result.timestamp ASC), ',') AS avgHeadwayAdherence, " +
            "  array_to_string(array_agg(coalesce(round(cast(result.avg_schedule_adherence AS NUMERIC), 2), -1) ORDER BY result.timestamp ASC), ',') AS avgScheduleAdherence, " +
            "  array_to_string(array_agg(coalesce(round(cast(result.stddev_train_frequency AS NUMERIC), 2), -1) ORDER BY result.timestamp ASC), ',') AS stdDevTrainFrequency, " +
            "  array_to_string(array_agg(coalesce(round(cast(result.exp_stddev_train_frequency AS NUMERIC), 2), -1) ORDER BY result.timestamp ASC), ',') AS expStdDevTrainFrequency " +
            "FROM ( " +
            "  SELECT " +
            "    lm.line_code, " +
            "    ts_round(lm.date, cast(?1 AS INTEGER) * 60) AS timestamp, " +
            "    avg(lm.num_cars) AS num_cars, " +
            "    avg(lm.num_trains) AS num_trains, " +
            "    avg(lm.expected_num_trains) AS exp_num_trains, " +
            "    avg(lm.num_delayed_trains) AS num_delayed_trains, " +
            "    avg(lm.num_eight_car_trains) AS num_eight_car_trains, " +
            "    avg(lm.average_train_delay) AS avg_train_delay, " +
            "    avg(lm.average_minimum_headways) AS avg_minimum_headways, " +
            "    avg(lm.average_train_frequency) AS avg_train_frequency, " +
            "    avg(lm.expected_train_frequency) AS exp_train_frequency, " +
            "    avg(lm.average_platform_wait_time) AS avg_platform_wait_time, " +
            "    avg(lm.expected_platform_wait_time) AS exp_platform_wait_time, " +
            "    avg(lm.average_headway_adherence) AS avg_headway_adherence, " +
            "    avg(lm.average_schedule_adherence) AS avg_schedule_adherence, " +
            "    avg(lm.standard_deviation_train_frequency) AS stddev_train_frequency, " +
            "    avg(lm.expected_standard_deviation_train_frequency) AS exp_stddev_train_frequency " +
            "  FROM line_metrics lm " +
            "  WHERE lm.date BETWEEN to_timestamp(cast(?2 AS FLOAT)) AND to_timestamp(cast(?3 AS FLOAT))" +
            "  GROUP BY timestamp, lm.line_code ORDER BY timestamp ASC " +
            ") AS result " +
            " GROUP BY line_code"
    )
    List<Object[]> getHistory(Integer interval, Long observedDateUnixMin, Long observedDateUnixMax);

    @Query(nativeQuery = true, value =
            "SELECT " +
            "  upper('ALL') AS lineCode, " +
            "  array_to_string(array_agg(extract(epoch from result.timestamp) ORDER BY result.timestamp ASC), ',') AS timestamps, " +
            "  array_to_string(array_agg(coalesce(round(result.num_cars, 2), -1) ORDER BY result.timestamp ASC), ',') AS avgNumCars, " +
            "  array_to_string(array_agg(coalesce(round(result.num_trains, 2), -1) ORDER BY result.timestamp ASC), ',') AS avgNumTrains, " +
            "  array_to_string(array_agg(coalesce(round(result.exp_num_trains, 2), -1) ORDER BY result.timestamp ASC), ',') AS expNumTrains, " +
            "  array_to_string(array_agg(coalesce(round(result.num_delayed_trains, 2), -1) ORDER BY result.timestamp ASC), ',') AS avgNumDelayedTrains, " +
            "  array_to_string(array_agg(coalesce(round(result.num_eight_car_trains, 2), -1) ORDER BY result.timestamp ASC), ',') AS avgNumEightCarTrains, " +
            "  array_to_string(array_agg(coalesce(round(100 * (cast(result.num_delayed_trains AS NUMERIC) / nullif(cast(result.num_trains AS NUMERIC), 0)), 2), -1) ORDER BY result.timestamp ASC), ',') AS avgPercentTrainsDelayed, " +
            "  array_to_string(array_agg(coalesce(round(100 * (cast(result.num_eight_car_trains AS NUMERIC) / nullif(cast(result.num_trains AS NUMERIC), 0)), 2), -1) ORDER BY result.timestamp ASC), ',') AS avgPercentEightCarTrains, " +
            "  array_to_string(array_agg(coalesce(round(result.avg_train_delay, 2), -1) ORDER BY result.timestamp ASC), ',') AS avgTrainDelay, " +
            "  array_to_string(array_agg(coalesce(round(cast(result.avg_minimum_headways AS NUMERIC), 2), -1) ORDER BY result.timestamp ASC), ',') AS avgMinimumHeadways, " +
            "  array_to_string(array_agg(coalesce(round(cast(result.avg_train_frequency AS NUMERIC), 2), -1) ORDER BY result.timestamp ASC), ',') AS avgTrainFrequency, " +
            "  array_to_string(array_agg(coalesce(round(cast(result.exp_train_frequency AS NUMERIC), 2), -1) ORDER BY result.timestamp ASC), ',') AS expTrainFrequency, " +
            "  array_to_string(array_agg(coalesce(round(cast(result.avg_platform_wait_time AS NUMERIC), 2), -1) ORDER BY result.timestamp ASC), ',') AS avgPlatformWaitTime, " +
            "  array_to_string(array_agg(coalesce(round(cast(result.exp_platform_wait_time AS NUMERIC), 2), -1) ORDER BY result.timestamp ASC), ',') AS expPlatformWaitTime, " +
            "  array_to_string(array_agg(coalesce(round(cast(result.avg_headway_adherence AS NUMERIC), 2), -1) ORDER BY result.timestamp ASC), ',') AS avgHeadwayAdherence, " +
            "  array_to_string(array_agg(coalesce(round(cast(result.avg_schedule_adherence AS NUMERIC), 2), -1) ORDER BY result.timestamp ASC), ',') AS avgScheduleAdherence, " +
            "  array_to_string(array_agg(coalesce(round(cast(result.stddev_train_frequency AS NUMERIC), 2), -1) ORDER BY result.timestamp ASC), ',') AS stdDevTrainFrequency, " +
            "  array_to_string(array_agg(coalesce(round(cast(result.exp_stddev_train_frequency AS NUMERIC), 2), -1) ORDER BY result.timestamp ASC), ',') AS expStdDevTrainFrequency " +
            "FROM ( " +
            "  SELECT " +
            "    t.timestamp, " +
            "    sum(t.num_cars) AS num_cars, " +
            "    sum(t.num_trains) AS num_trains, " +
            "    sum(t.expected_num_trains) AS exp_num_trains, " +
            "    sum(t.num_delayed_trains) AS num_delayed_trains, " +
            "    sum(t.num_eight_car_trains) AS num_eight_car_trains, " +
            "    avg(t.average_train_delay) AS avg_train_delay, " +
            "    avg(t.average_minimum_headways) AS avg_minimum_headways, " +
            "    avg(t.average_train_frequency) AS avg_train_frequency, " +
            "    avg(t.expected_train_frequency) AS exp_train_frequency, " +
            "    avg(t.average_platform_wait_time) AS avg_platform_wait_time, " +
            "    avg(t.expected_platform_wait_time) AS exp_platform_wait_time, " +
            "    avg(t.average_headway_adherence) AS avg_headway_adherence, " +
            "    avg(t.average_schedule_adherence) AS avg_schedule_adherence, " +
            "    avg(t.standard_deviation_train_frequency) AS stddev_train_frequency, " +
            "    avg(t.expected_standard_deviation_train_frequency) AS exp_stddev_train_frequency " +
            "  FROM ( " +
            "    SELECT " +
            "      lm.line_code, " +
            "      ts_round(lm.date, cast(?1 AS INTEGER) * 60) AS timestamp, " +
            "      avg(lm.num_cars) AS num_cars, " +
            "      avg(lm.num_trains) AS num_trains, " +
            "      avg(lm.expected_num_trains) AS expected_num_trains, " +
            "      avg(lm.num_delayed_trains) AS num_delayed_trains, " +
            "      avg(lm.num_eight_car_trains) AS num_eight_car_trains, " +
            "      avg(lm.average_train_delay) AS average_train_delay, " +
            "      avg(lm.average_minimum_headways) AS average_minimum_headways, " +
            "      avg(lm.average_train_frequency) AS average_train_frequency, " +
            "      avg(lm.expected_train_frequency) AS expected_train_frequency, " +
            "      avg(lm.average_platform_wait_time) AS average_platform_wait_time, " +
            "      avg(lm.expected_platform_wait_time) AS expected_platform_wait_time, " +
            "      avg(lm.average_headway_adherence) AS average_headway_adherence, " +
            "      avg(lm.average_schedule_adherence) AS average_schedule_adherence, " +
            "      avg(lm.standard_deviation_train_frequency) AS standard_deviation_train_frequency, " +
            "      avg(lm.expected_standard_deviation_train_frequency) AS expected_standard_deviation_train_frequency " +
            "    FROM line_metrics lm " +
            "    WHERE lm.date BETWEEN to_timestamp(cast(?2 AS FLOAT)) AND to_timestamp(cast(?3 AS FLOAT))" +
            "    GROUP BY timestamp, lm.line_code ORDER BY timestamp ASC " +
            "  ) t" +
            "  GROUP BY timestamp" +
            ") AS result"
    )
    List<Object[]> getAvgHistory(Integer interval, Long observedDateUnixMin, Long observedDateUnixMax);

    @Query(nativeQuery = true, value =
            "SELECT " +
            "  dm.line_code AS line_code, " +
            "  dm.direction_number AS direction_number, " +
            "  CASE WHEN (avg(CASE WHEN dm.date > now() - INTERVAL '15 minutes' THEN cast(dm.average_platform_wait_time AS NUMERIC) END) " +
            "             > avg(CASE WHEN dm.date > now() - INTERVAL '10 minutes' THEN cast(dm.average_platform_wait_time AS NUMERIC) END)) " +
            "            AND (avg(CASE WHEN dm.date > now() - INTERVAL '10 minutes' THEN cast(dm.average_platform_wait_time AS NUMERIC) END) " +
            "                 > avg(CASE WHEN dm.date > now() - INTERVAL '5 minutes' THEN cast(dm.average_platform_wait_time AS NUMERIC) END)) THEN TRUE ELSE FALSE END AS average_platform_wait_time_trending_down, " +
            "  CASE WHEN (avg(CASE WHEN dm.date > now() - INTERVAL '5 minutes' THEN cast(dm.average_platform_wait_time AS NUMERIC) END) " +
            "             > avg(CASE WHEN dm.date > now() - INTERVAL '10 minutes' THEN cast(dm.average_platform_wait_time AS NUMERIC) END)) " +
            "            AND (avg(CASE WHEN dm.date > now() - INTERVAL '10 minutes' THEN cast(dm.average_platform_wait_time AS NUMERIC) END) " +
            "                 > avg(CASE WHEN dm.date > now() - INTERVAL '15 minutes' THEN cast(dm.average_platform_wait_time AS NUMERIC) END)) THEN TRUE ELSE FALSE END AS average_platform_wait_time_trending_up " +
            "FROM direction_metrics dm " +
            "WHERE dm.date > now() - INTERVAL '15 minutes' AND dm.average_platform_wait_time > 0 " +
            "GROUP BY dm.line_code, dm.direction_number")
    List<Object[]> getPlatformWaitTimeTrendStatusDataByLineAndDirection();

    @Query(nativeQuery = true, value =
            "SELECT " +
            "  lm.line_code AS line_code, " +
            "  CASE WHEN (avg(CASE WHEN lm.date > now() - INTERVAL '15 minutes' THEN cast(lm.average_platform_wait_time AS NUMERIC) END) " +
            "             > avg(CASE WHEN lm.date > now() - INTERVAL '10 minutes' THEN cast(lm.average_platform_wait_time AS NUMERIC) END)) " +
            "            AND (avg(CASE WHEN lm.date > now() - INTERVAL '10 minutes' THEN cast(lm.average_platform_wait_time AS NUMERIC) END) " +
            "                 > avg(CASE WHEN lm.date > now() - INTERVAL '5 minutes' THEN cast(lm.average_platform_wait_time AS NUMERIC) END)) THEN TRUE ELSE FALSE END AS average_platform_wait_time_trending_down, " +
            "  CASE WHEN (avg(CASE WHEN lm.date > now() - INTERVAL '5 minutes' THEN cast(lm.average_platform_wait_time AS NUMERIC) END) " +
            "             > avg(CASE WHEN lm.date > now() - INTERVAL '10 minutes' THEN cast(lm.average_platform_wait_time AS NUMERIC) END)) " +
            "            AND (avg(CASE WHEN lm.date > now() - INTERVAL '10 minutes' THEN cast(lm.average_platform_wait_time AS NUMERIC) END) " +
            "                 > avg(CASE WHEN lm.date > now() - INTERVAL '15 minutes' THEN cast(lm.average_platform_wait_time AS NUMERIC) END)) THEN TRUE ELSE FALSE END AS average_platform_wait_time_trending_up " +
            "FROM line_metrics lm " +
            "WHERE lm.date > now() - INTERVAL '15 minutes' AND lm.average_platform_wait_time > 0 " +
            "GROUP BY lm.line_code")
    List<Object[]> getPlatformWaitTimeTrendStatusDataByLine();

    @Query(nativeQuery = true, value =
            "SELECT " +
            "  array_to_string(array_replace(array_agg(time), (array_agg(time))[array_length(array_agg(time), 1)], 'now'), ',') AS times, " +
            "  array_to_string(array_agg(all_average_train_frequency), ',') AS all_average_train_frequencies, " +
            "  array_to_string(array_agg(rd_average_train_frequency), ',') AS rd_average_train_frequencies, " +
            "  array_to_string(array_agg(or_average_train_frequency), ',') AS or_average_train_frequencies, " +
            "  array_to_string(array_agg(sv_average_train_frequency), ',') AS sv_average_train_frequencies, " +
            "  array_to_string(array_agg(bl_average_train_frequency), ',') AS bl_average_train_frequencies, " +
            "  array_to_string(array_agg(yl_average_train_frequency), ',') AS yl_average_train_frequencies, " +
            "  array_to_string(array_agg(gr_average_train_frequency), ',') AS gr_average_train_frequencies, " +
            "  array_to_string(array_agg(all_expected_train_frequency), ',') AS all_expected_train_frequencies, " +
            "  array_to_string(array_agg(rd_expected_train_frequency), ',') AS rd_expected_train_frequencies, " +
            "  array_to_string(array_agg(or_expected_train_frequency), ',') AS or_expected_train_frequencies, " +
            "  array_to_string(array_agg(sv_expected_train_frequency), ',') AS sv_expected_train_frequencies, " +
            "  array_to_string(array_agg(bl_expected_train_frequency), ',') AS bl_expected_train_frequencies, " +
            "  array_to_string(array_agg(yl_expected_train_frequency), ',') AS yl_expected_train_frequencies, " +
            "  array_to_string(array_agg(gr_expected_train_frequency), ',') AS gr_expected_train_frequencies " +
            "FROM ( " +
            "  SELECT " +
            "    trim(LEADING '0' FROM to_char(ts_round(date, 120), 'HH:MIam')) AS time, " +
            "    coalesce(round(cast(avg(average_train_frequency) AS NUMERIC), 2), -1) AS all_average_train_frequency, " +
            "    coalesce(round(cast(avg(CASE WHEN line_code = 'RD' THEN average_train_frequency END) AS NUMERIC), 2), -1) AS rd_average_train_frequency, " +
            "    coalesce(round(cast(avg(CASE WHEN line_code = 'OR' THEN average_train_frequency END) AS NUMERIC), 2), -1) AS or_average_train_frequency, " +
            "    coalesce(round(cast(avg(CASE WHEN line_code = 'SV' THEN average_train_frequency END) AS NUMERIC), 2), -1) AS sv_average_train_frequency, " +
            "    coalesce(round(cast(avg(CASE WHEN line_code = 'BL' THEN average_train_frequency END) AS NUMERIC), 2), -1) AS bl_average_train_frequency, " +
            "    coalesce(round(cast(avg(CASE WHEN line_code = 'YL' THEN average_train_frequency END) AS NUMERIC), 2), -1) AS yl_average_train_frequency, " +
            "    coalesce(round(cast(avg(CASE WHEN line_code = 'GR' THEN average_train_frequency END) AS NUMERIC), 2), -1) AS gr_average_train_frequency, " +
            "    coalesce(round(cast(avg(expected_train_frequency) AS NUMERIC), 2), -1) AS all_expected_train_frequency, " +
            "    coalesce(round(cast(avg(CASE WHEN line_code = 'RD' THEN expected_train_frequency END) AS NUMERIC), 2), -1) AS rd_expected_train_frequency, " +
            "    coalesce(round(cast(avg(CASE WHEN line_code = 'OR' THEN expected_train_frequency END) AS NUMERIC), 2), -1) AS or_expected_train_frequency, " +
            "    coalesce(round(cast(avg(CASE WHEN line_code = 'SV' THEN expected_train_frequency END) AS NUMERIC), 2), -1) AS sv_expected_train_frequency, " +
            "    coalesce(round(cast(avg(CASE WHEN line_code = 'BL' THEN expected_train_frequency END) AS NUMERIC), 2), -1) AS bl_expected_train_frequency, " +
            "    coalesce(round(cast(avg(CASE WHEN line_code = 'YL' THEN expected_train_frequency END) AS NUMERIC), 2), -1) AS yl_expected_train_frequency, " +
            "    coalesce(round(cast(avg(CASE WHEN line_code = 'GR' THEN expected_train_frequency END) AS NUMERIC), 2), -1) AS gr_expected_train_frequency " +
            "  FROM ( " +
            "    SELECT " +
            "      lm.date, " +
            "      lm.line_code, " +
            "      lm.average_train_frequency, " +
            "      lm.expected_train_frequency " +
            "    FROM line_metrics lm " +
            "    WHERE " +
            "      lm.date >= (now() - INTERVAL '1 hour') " +
            "  ) AS t1 " +
            "  GROUP BY ts_round(date, 120) " +
            "  ORDER BY ts_round(date, 120) ASC " +
            ") AS t2"
    )
    List<Object[]> getRecentTrainFrequencyData();

    @Query(nativeQuery = true, value =
            "WITH filtered_line_metrics AS ( " +
            "  SELECT * " +
            "  FROM line_metrics " +
            "  WHERE (date BETWEEN cast(?1 AS TIMESTAMP) AND cast(?2 AS TIMESTAMP)) " +
            ") " +
            "SELECT " +
            "  lm.line_code, " +
            "  round(cast(avg(lm.average_headway_adherence) AS NUMERIC)) AS avg_headway_adherence, " +
            "  round(cast(avg(lm.average_schedule_adherence) AS NUMERIC)) AS avg_schedule_adherence, " +
            "  round(cast(avg(lm.average_train_frequency) AS NUMERIC), 2) AS avg_train_frequency, " +
            "  round(cast(avg(lm.expected_train_frequency) AS NUMERIC), 2) AS avg_expected_train_frequency, " +
            "  round(cast(avg(lm.standard_deviation_train_frequency) AS NUMERIC), 2) AS avg_train_spacing_consistency, " +
            "  round(cast(avg(lm.expected_standard_deviation_train_frequency) AS NUMERIC), 2) AS avg_expected_train_spacing_consistency, " +
            "  round(cast(avg(lm.average_platform_wait_time) AS NUMERIC), 2) AS avg_platform_wait_time, " +
            "  round(cast(avg(lm.expected_platform_wait_time) AS NUMERIC), 2) AS avg_expected_platform_wait_time, " +
            "  count(DISTINCT incident.incident_id) AS num_service_incidents, " +
            "  count(DISTINCT offload.id) AS num_train_offloads, " +
            "  count(DISTINCT expressed.id) AS num_times_trains_expressed_stations, " +
            "  count(DISTINCT tag.id) AS num_train_problems, " +
            "  round(avg(lm.num_trains), 2) AS avg_num_trains, " +
            "  round(avg(lm.expected_num_trains), 2) AS avg_expected_num_trains, " +
            "  round(avg(lm.num_eight_car_trains), 2) AS avg_num_eight_car_trains, " +
            "  round((round(avg(lm.num_eight_car_trains), 2) / round(nullif(avg(lm.num_trains), 0), 2)) * 100.0) AS percent_eight_car_trains, " +
            "  lm2.max_mdn_train_delay, " +
            "  lm2.max_mdn_train_delay_time " +
            "FROM filtered_line_metrics lm " +
            "  INNER JOIN ( " +
            "    SELECT DISTINCT ON (lm2.line_code) " +
            "      lm2.line_code, " +
            "      round(cast(lm2.median_train_delay AS NUMERIC) / 60, 2) AS max_mdn_train_delay, " +
            "      trim(LEADING '0' FROM to_char(lm2.date, 'HH:MIam')) AS max_mdn_train_delay_time " +
            "    FROM filtered_line_metrics lm2 " +
            "    ORDER BY lm2.line_code, lm2.median_train_delay DESC NULLS LAST " +
            "  ) lm2 ON lm.line_code = lm2.line_code " +
            "  LEFT JOIN rail_incident incident ON " +
            "    (incident.date BETWEEN cast(?1 AS TIMESTAMP) AND cast(?2 AS TIMESTAMP)) AND " +
            "    (incident.line_codes @> cast(ARRAY[lm.line_code] AS TEXT[])) " +
            "  LEFT JOIN train_offload offload ON " +
            "    (offload.date BETWEEN cast(?1 AS TIMESTAMP) AND cast(?2 AS TIMESTAMP)) AND " +
            "    (offload.line_code = lm.line_code) " +
            "  LEFT JOIN train_tag tag ON " +
            "    (tag.date BETWEEN cast(?1 AS TIMESTAMP) AND cast(?2 AS TIMESTAMP)) AND " +
            "    (tag.line_code = lm.line_code) AND " +
            "    (tag.type IN ('BAD_OPERATOR', 'CROWDED', 'UNCOMFORTABLE_TEMPS', 'RECENTLY_OFFLOADED', 'UNCOMFORTABLE_RIDE', 'ISOLATED_CARS', 'WRONG_NUM_CARS', 'WRONG_DESTINATION', 'NEEDS_WORK', 'BROKEN_INTERCOM', 'DISRUPTIVE_PASSENGER')) " +
            "  LEFT JOIN train_expressed_station_event expressed ON " +
            "    (expressed.date BETWEEN cast(?1 AS TIMESTAMP) AND cast(?2 AS TIMESTAMP)) AND " +
            "    (expressed.line_code = lm.line_code) " +
            "GROUP BY lm.line_code, lm2.max_mdn_train_delay, lm2.max_mdn_train_delay_time " +
            "ORDER BY avg_headway_adherence ASC NULLS LAST"
    )
    List<Object[]> getLinePerformanceSummaryDataByLine(String startDateTime, String endDateTime);

    @Query(nativeQuery = true, value =
            "SELECT " +
            "  trim(LEADING '0' FROM to_char(cast(lm.date AS DATE), 'MM/DD/YY')) AS date, " +
            "  CASE WHEN (cast(lm.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) THEN 'AM Rush' ELSE 'PM Rush' END AS rush_hour, " +
            "  lm.line_code AS line_code, " +
            "  round(cast(avg(lm.average_headway_adherence) AS NUMERIC)) AS average_calculated_headway_adherence, " +
            "  round(cast(avg(lm.average_schedule_adherence) AS NUMERIC)) AS average_calculated_schedule_adherence, " +
            "  round(cast(avg(lm.average_train_frequency) AS NUMERIC), 2) AS average_observed_train_frequency, " +
            "  round(cast(avg(lm.expected_train_frequency) AS NUMERIC), 2) AS average_expected_train_frequency, " +
            "  round((avg(lm.expected_train_frequency) / nullif(avg(lm.average_train_frequency), 0)) * 100.0) AS percent_average_observed_train_frequency_of_expected, " +
            "  round(cast(avg(lm.standard_deviation_train_frequency) AS NUMERIC), 2) AS standard_deviation_observed_train_frequency, " +
            "  round(cast(avg(lm.expected_standard_deviation_train_frequency) AS NUMERIC), 2) AS standard_deviation_expected_train_frequency, " +
            "  round((avg(lm.expected_standard_deviation_train_frequency) / nullif(avg(lm.standard_deviation_train_frequency), 0)) * 100.0) AS percent_standard_deviation_observed_train_frequency_of_expected, " +
            "  round(cast(avg(lm.average_platform_wait_time) AS NUMERIC), 2) AS average_calculated_platform_wait_time, " +
            "  round(cast(avg(lm.expected_platform_wait_time) AS NUMERIC), 2) AS average_expected_platform_wait_time, " +
            "  round((avg(lm.expected_platform_wait_time) / nullif(avg(lm.average_platform_wait_time), 0)) * 100.0) AS percent_average_calculated_platform_wait_time_of_expected, " +
            "  round(avg(lm.num_trains), 2) AS average_observed_trains, " +
            "  round(avg(lm.expected_num_trains), 2) AS average_expected_trains, " +
            "  round((avg(lm.num_trains) / nullif(avg(lm.expected_num_trains), 0)) * 100.0) AS percent_average_observed_trains_of_expected, " +
            "  round(avg(lm.num_eight_car_trains), 2) AS average_observed_eight_car_trains, " +
            "  round(avg(lm.num_cars), 2) AS average_observed_train_cars, " +
            "  round(max(lm.num_delayed_trains), 2) AS maximum_observed_delayed_trains, " +
            "  round(avg(lm.average_train_delay / 60.0), 2) AS average_observed_train_delays, " +
            "  round(cast(percentile_cont(0.5) WITHIN GROUP (ORDER BY median_train_delay) AS NUMERIC) / 60.0, 2) AS median_observed_train_delays, " +
            "  round(min(lm.minimum_train_delay / 60.0), 2) AS minimum_observed_train_delays, " +
            "  round(max(lm.maximum_train_delay / 60.0), 2) AS maximum_observed_train_delays, " +
            "  count(DISTINCT offload.id) AS num_offloads, " +
            "  count(DISTINCT incident.incident_id) AS num_incidents, " +
            "  count(DISTINCT tag.id) AS num_negative_train_tags, " +
            "  count(DISTINCT expressed.id) AS num_times_trains_expressed_stations " +
            "FROM line_metrics lm " +
            "  LEFT JOIN train_offload offload ON" +
            "    (date_trunc('day', lm.date) = date_trunc('day', offload.date)) AND" +
            "    (CASE WHEN (cast(lm.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) THEN 'AM Rush' ELSE 'PM Rush' END = CASE WHEN (cast(offload.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) THEN 'AM Rush' WHEN (cast(offload.date AS TIME) BETWEEN cast('15:00:00' AS TIME) AND cast('19:00:00' AS TIME)) THEN 'PM Rush' END) AND" +
            "    (lm.line_code = offload.line_code) " +
            "  LEFT JOIN rail_incident incident ON" +
            "    (date_trunc('day', lm.date) = date_trunc('day', incident.date)) AND" +
            "    (CASE WHEN (cast(lm.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) THEN 'AM Rush' ELSE 'PM Rush' END = CASE WHEN (cast(incident.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) THEN 'AM Rush' WHEN (cast(incident.date AS TIME) BETWEEN cast('15:00:00' AS TIME) AND cast('19:00:00' AS TIME)) THEN 'PM Rush' END) AND" +
            "    (incident.line_codes @> cast(ARRAY[lm.line_code] AS TEXT[])) " +
            "  LEFT JOIN train_tag tag ON" +
            "    (date_trunc('day', lm.date) = date_trunc('day', tag.date)) AND" +
            "    (CASE WHEN (cast(lm.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) THEN 'AM Rush' ELSE 'PM Rush' END = CASE WHEN (cast(tag.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) THEN 'AM Rush' WHEN (cast(tag.date AS TIME) BETWEEN cast('15:00:00' AS TIME) AND cast('19:00:00' AS TIME)) THEN 'PM Rush' END) AND" +
            "    (tag.line_code = lm.line_code) AND" +
            "    (tag.type IN ('BAD_OPERATOR', 'CROWDED', 'UNCOMFORTABLE_TEMPS', 'RECENTLY_OFFLOADED', 'UNCOMFORTABLE_RIDE', 'ISOLATED_CARS', 'WRONG_NUM_CARS', 'WRONG_DESTINATION', 'NEEDS_WORK', 'BROKEN_INTERCOM', 'DISRUPTIVE_PASSENGER')) " +
            "  LEFT JOIN train_expressed_station_event expressed ON" +
            "    (date_trunc('day', lm.date) = date_trunc('day', expressed.date)) AND" +
            "    (CASE WHEN (cast(lm.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) THEN 'AM Rush' ELSE 'PM Rush' END = CASE WHEN (cast(expressed.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) THEN 'AM Rush' WHEN (cast(expressed.date AS TIME) BETWEEN cast('15:00:00' AS TIME) AND cast('19:00:00' AS TIME)) THEN 'PM Rush' END) AND" +
            "    (lm.line_code = expressed.line_code) " +
            "WHERE " +
            "  (lm.should_exclude_from_reports IS NULL OR lm.should_exclude_from_reports = FALSE) AND " +
            "  (lm.date >= '2017-06-25 00:00:00') AND " +
            "  ((extract(DOW FROM lm.date) >= 1) AND (extract(DOW FROM lm.date) <= 5)) AND " +
            "  ((cast(lm.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) OR (cast(lm.date AS TIME) BETWEEN cast('15:00:00' AS TIME) AND cast('19:00:00' AS TIME))) " +
            "GROUP BY cast(lm.date AS DATE), rush_hour, lm.line_code " +
            "ORDER BY cast(lm.date AS DATE) ASC, rush_hour, lm.line_code;"
    )
    List<Object[]> getPerformanceSummary();

    @Query(nativeQuery = true, value =
            "SELECT " +
            "  trim(LEADING '0' FROM to_char(lm.date, 'HH:MIam')) AS time, " +
            "  CASE WHEN (cast(lm.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) THEN 'AM Rush' ELSE 'PM Rush' END AS rush_hour, " +
            "  lm.line_code AS line_code, " +
            "  round(cast(avg(lm.average_headway_adherence) AS NUMERIC)) AS average_calculated_headway_adherence, " +
            "  round(cast(avg(lm.average_schedule_adherence) AS NUMERIC)) AS average_calculated_schedule_adherence, " +
            "  round(cast(avg(lm.average_train_frequency) AS NUMERIC), 2) AS average_observed_train_frequency, " +
            "  round(cast(avg(lm.expected_train_frequency) AS NUMERIC), 2) AS average_expected_train_frequency, " +
            "  round((avg(lm.expected_train_frequency) / nullif(avg(lm.average_train_frequency), 0)) * 100.0) AS percent_average_observed_train_frequency_of_expected, " +
            "  round(cast(avg(lm.standard_deviation_train_frequency) AS NUMERIC), 2) AS standard_deviation_observed_train_frequency, " +
            "  round(cast(avg(lm.expected_standard_deviation_train_frequency) AS NUMERIC), 2) AS standard_deviation_expected_train_frequency, " +
            "  round((avg(lm.expected_standard_deviation_train_frequency) / nullif(avg(lm.standard_deviation_train_frequency), 0)) * 100.0) AS percent_standard_deviation_observed_train_frequency_of_expected, " +
            "  round(cast(avg(lm.average_platform_wait_time) AS NUMERIC), 2) AS average_calculated_platform_wait_time, " +
            "  round(cast(avg(lm.expected_platform_wait_time) AS NUMERIC), 2) AS average_expected_platform_wait_time, " +
            "  round((avg(lm.expected_platform_wait_time) / nullif(avg(lm.average_platform_wait_time), 0)) * 100.0) AS percent_average_calculated_platform_wait_time_of_expected, " +
            "  round(avg(lm.num_trains), 2) AS average_observed_trains, " +
            "  round(avg(lm.expected_num_trains), 2) AS average_expected_trains, " +
            "  round((avg(lm.num_trains) / nullif(avg(lm.expected_num_trains), 0)) * 100.0) AS percent_average_observed_trains_of_expected, " +
            "  round(avg(lm.num_eight_car_trains), 2) AS average_observed_eight_car_trains, " +
            "  round(avg(lm.num_cars), 2) AS average_observed_train_cars, " +
            "  round(max(lm.num_delayed_trains), 2) AS maximum_observed_delayed_trains, " +
            "  round(avg(lm.average_train_delay / 60.0), 2) AS average_observed_train_delays, " +
            "  round(cast(percentile_cont(0.5) WITHIN GROUP (ORDER BY median_train_delay) AS NUMERIC) / 60.0, 2) AS median_observed_train_delays, " +
            "  round(min(lm.minimum_train_delay / 60.0), 2) AS minimum_observed_train_delays, " +
            "  round(max(lm.maximum_train_delay / 60.0), 2) AS maximum_observed_train_delays, " +
            "  count(DISTINCT offload.id) AS num_offloads, " +
            "  count(DISTINCT incident.incident_id) AS num_incidents, " +
            "  count(DISTINCT tag.id) AS num_negative_train_tags, " +
            "  count(DISTINCT expressed.id) AS num_times_trains_expressed_stations " +
            "FROM line_metrics lm " +
            "  LEFT JOIN train_offload offload ON" +
            "    (date_trunc('day', lm.date) = date_trunc('day', offload.date)) AND" +
            "    ((date_part('hour', lm.date) = date_part('hour', offload.date)) AND (date_part('minute', lm.date) = date_part('minute', offload.date))) AND" +
            "    (CASE WHEN (cast(lm.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) THEN 'AM Rush' ELSE 'PM Rush' END = CASE WHEN (cast(offload.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) THEN 'AM Rush' ELSE 'PM Rush' END) AND" +
            "    (lm.line_code = offload.line_code) " +
            "  LEFT JOIN rail_incident incident ON" +
            "    (date_trunc('day', lm.date) = date_trunc('day', incident.date)) AND" +
            "    ((date_part('hour', lm.date) = date_part('hour', incident.date)) AND (date_part('minute', lm.date) = date_part('minute', incident.date))) AND" +
            "    (CASE WHEN (cast(lm.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) THEN 'AM Rush' ELSE 'PM Rush' END = CASE WHEN (cast(incident.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) THEN 'AM Rush' WHEN (cast(incident.date AS TIME) BETWEEN cast('15:00:00' AS TIME) AND cast('19:00:00' AS TIME)) THEN 'PM Rush' END) AND" +
            "    (incident.line_codes @> cast(ARRAY[lm.line_code] AS TEXT[])) " +
            "  LEFT JOIN train_tag tag ON" +
            "    (date_trunc('day', lm.date) = date_trunc('day', tag.date)) AND" +
            "    ((date_part('hour', lm.date) = date_part('hour', tag.date)) AND (date_part('minute', lm.date) = date_part('minute', tag.date))) AND" +
            "    (CASE WHEN (cast(lm.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) THEN 'AM Rush' ELSE 'PM Rush' END = CASE WHEN (cast(tag.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) THEN 'AM Rush' WHEN (cast(tag.date AS TIME) BETWEEN cast('15:00:00' AS TIME) AND cast('19:00:00' AS TIME)) THEN 'PM Rush' END) AND" +
            "    (tag.line_code = lm.line_code) AND" +
            "    (tag.type IN ('BAD_OPERATOR', 'CROWDED', 'UNCOMFORTABLE_TEMPS', 'RECENTLY_OFFLOADED', 'UNCOMFORTABLE_RIDE', 'ISOLATED_CARS', 'WRONG_NUM_CARS', 'WRONG_DESTINATION', 'NEEDS_WORK', 'BROKEN_INTERCOM', 'DISRUPTIVE_PASSENGER')) " +
            "  LEFT JOIN train_expressed_station_event expressed ON" +
            "    (date_trunc('day', lm.date) = date_trunc('day', expressed.date)) AND" +
            "    ((date_part('hour', lm.date) = date_part('hour', expressed.date)) AND (date_part('minute', lm.date) = date_part('minute', expressed.date))) AND" +
            "    (CASE WHEN (cast(lm.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) THEN 'AM Rush' ELSE 'PM Rush' END = CASE WHEN (cast(expressed.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) THEN 'AM Rush' ELSE 'PM Rush' END) AND" +
            "    (lm.line_code = expressed.line_code) " +
            "WHERE " +
            "  (lm.should_exclude_from_reports IS NULL OR lm.should_exclude_from_reports = FALSE) AND " +
            "  (lm.date >= '2017-06-25 00:00:00' AND lm.date BETWEEN to_timestamp(cast(?1 AS FLOAT)) AND to_timestamp(cast(?2 AS FLOAT))) AND " +
            "  ((extract(DOW FROM lm.date) >= 1) AND (extract(DOW FROM lm.date) <= 5)) AND " +
            "  ((cast(lm.date AS TIME) BETWEEN cast('05:00:00' AS TIME) AND cast('09:30:00' AS TIME)) OR (cast(lm.date AS TIME) BETWEEN cast('15:00:00' AS TIME) AND cast('19:00:00' AS TIME))) " +
            "GROUP BY to_char(lm.date, 'HH24:MIam'), time, rush_hour, lm.line_code " +
            "ORDER BY to_char(lm.date, 'HH24:MIam') ASC, rush_hour, lm.line_code;"
    )
    List<Object[]> getHourlyPerformanceSummary(Long fromUnixTimestamp, Long toUnixTimestamp);
}

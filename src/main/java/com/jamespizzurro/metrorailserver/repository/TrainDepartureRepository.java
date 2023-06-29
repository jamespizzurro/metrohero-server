package com.jamespizzurro.metrorailserver.repository;

import com.jamespizzurro.metrorailserver.domain.TrainDeparture;
import com.jamespizzurro.metrorailserver.domain.TrainDeparturePrimaryKey;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface TrainDepartureRepository extends CrudRepository<TrainDeparture, TrainDeparturePrimaryKey> {

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM train_departure WHERE departure_time > to_timestamp(cast(?1 AS DOUBLE PRECISION) / 1000) AND type = 'SCHEDULED'")
    void removeFutureScheduled(long timestampInMilliseconds);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "REINDEX TABLE train_departure")
    void reindexTrainDepartureTable();

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "REINDEX TABLE train_departure_info")
    void reindexTrainDepartureInfoTable();

    @Modifying
    @Transactional
    @Query(nativeQuery = true,
            value = "WITH new_value ( " +
                    "  train_id, " +
                    "  real_train_id, " +
                    "  departure_station_name, " +
                    "  departure_station_code, " +
                    "  line_name, " +
                    "  line_code, " +
                    "  direction_name, " +
                    "  direction_number, " +
                    "  scheduled_destination_station_name, " +
                    "  scheduled_destination_station_code, " +
                    "  observed_destination_station_name, " +
                    "  observed_destination_station_code, " +
                    "  observed_num_cars, " +
                    "  observed_departure_time, " +
                    "  scheduled_departure_time, " +
                    "  observed_time_since_last_departure, " +
                    "  scheduled_time_since_last_departure, " +
                    "  headway_deviation, " +
                    "  schedule_deviation " +
                    ") AS ( " +
                    "  SELECT " +
                    "    coalesce(scheduled.train_id, observed.train_id) AS train_id, " +
                    "    coalesce(scheduled.real_train_id, observed.real_train_id) AS real_train_id, " +
                    "    coalesce(scheduled.departure_station_name, observed.departure_station_name) AS departure_station_name, " +
                    "    coalesce(scheduled.departure_station_code, observed.departure_station_code) AS departure_station_code, " +
                    "    coalesce(scheduled.line_name, observed.line_name) AS line_name, " +
                    "    coalesce(scheduled.line_code, observed.line_code) AS line_code, " +
                    "    coalesce(scheduled.direction_name, observed.direction_name) AS direction_name, " +
                    "    coalesce(scheduled.direction_number, observed.direction_number) AS direction_number, " +
                    "    coalesce(scheduled.scheduled_destination_station_name, observed.scheduled_destination_station_name) AS scheduled_destination_station_name, " +
                    "    coalesce(scheduled.scheduled_destination_station_code, observed.scheduled_destination_station_code) AS scheduled_destination_station_code, " +
                    "    coalesce(scheduled.observed_destination_station_name, observed.observed_destination_station_name) AS observed_destination_station_name, " +
                    "    coalesce(scheduled.observed_destination_station_code, observed.observed_destination_station_code) AS observed_destination_station_code, " +
                    "    coalesce(scheduled.observed_num_cars, observed.observed_num_cars) AS observed_num_cars, " +
                    "    coalesce(scheduled.observed_departure_time, observed.observed_departure_time) AS observed_departure_time, " +
                    "    scheduled.scheduled_departure_time AS scheduled_departure_time, " +
                    "    coalesce(scheduled.observed_time_since_last_departure, observed.observed_time_since_last_departure) AS observed_time_since_last_departure, " +
                    "    coalesce(scheduled.scheduled_time_since_last_departure, observed.scheduled_time_since_last_departure) AS scheduled_time_since_last_departure, " +
                    "    coalesce(scheduled.headway_deviation, observed.headway_deviation) AS headway_deviation, " +
                    "    scheduled.schedule_deviation AS schedule_deviation " +
                    "  FROM ( " +
                    "    SELECT DISTINCT ON (scheduled.departure_station_code, scheduled.line_code, scheduled.direction_number, scheduled.departure_time) " +
                    "      (CASE WHEN row_number() OVER ( " +
                    "        PARTITION BY " +
                    "          scheduled.departure_station_code, " +
                    "          scheduled.line_code, " +
                    "          scheduled.direction_number, " +
                    "          observed.departure_time " +
                    "        ORDER BY " +
                    "          scheduled.departure_time <-> observed.departure_time " +
                    "       ) = 1 THEN observed.train_id END) AS train_id, " +
                    "      (CASE WHEN row_number() OVER ( " +
                    "        PARTITION BY " +
                    "          scheduled.departure_station_code, " +
                    "          scheduled.line_code, " +
                    "          scheduled.direction_number, " +
                    "          observed.departure_time " +
                    "        ORDER BY " +
                    "          scheduled.departure_time <-> observed.departure_time " +
                    "       ) = 1 THEN observed.real_train_id END) AS real_train_id, " +
                    "      scheduled.departure_station_name AS departure_station_name, " +
                    "      scheduled.departure_station_code AS departure_station_code, " +
                    "      scheduled.line_name AS line_name, " +
                    "      scheduled.line_code AS line_code, " +
                    "      scheduled.direction_name AS direction_name, " +
                    "      scheduled.direction_number AS direction_number, " +
                    "      scheduled.destination_station_name AS scheduled_destination_station_name, " +
                    "      scheduled.destination_station_code AS scheduled_destination_station_code, " +
                    "      (CASE WHEN row_number() OVER ( " +
                    "        PARTITION BY " +
                    "          scheduled.departure_station_code, " +
                    "          scheduled.line_code, " +
                    "          scheduled.direction_number, " +
                    "          observed.departure_time " +
                    "        ORDER BY " +
                    "          scheduled.departure_time <-> observed.departure_time " +
                    "       ) = 1 THEN observed.destination_station_name END) AS observed_destination_station_name, " +
                    "      (CASE WHEN row_number() OVER ( " +
                    "        PARTITION BY " +
                    "          scheduled.departure_station_code, " +
                    "          scheduled.line_code, " +
                    "          scheduled.direction_number, " +
                    "          observed.departure_time " +
                    "        ORDER BY " +
                    "          scheduled.departure_time <-> observed.departure_time " +
                    "       ) = 1 THEN observed.destination_station_code END) AS observed_destination_station_code, " +
                    "      (CASE WHEN row_number() OVER ( " +
                    "        PARTITION BY " +
                    "          scheduled.departure_station_code, " +
                    "          scheduled.line_code, " +
                    "          scheduled.direction_number, " +
                    "          observed.departure_time " +
                    "        ORDER BY " +
                    "          scheduled.departure_time <-> observed.departure_time " +
                    "       ) = 1 THEN observed.num_cars END) AS observed_num_cars, " +
                    "      (CASE WHEN row_number() OVER ( " +
                    "        PARTITION BY " +
                    "          scheduled.departure_station_code, " +
                    "          scheduled.line_code, " +
                    "          scheduled.direction_number, " +
                    "          observed.departure_time " +
                    "        ORDER BY " +
                    "          scheduled.departure_time <-> observed.departure_time " +
                    "       ) = 1 THEN observed.departure_time END) AS observed_departure_time, " +
                    "      scheduled.departure_time AS scheduled_departure_time, " +
                    "      (CASE WHEN row_number() OVER ( " +
                    "        PARTITION BY " +
                    "          scheduled.departure_station_code, " +
                    "          scheduled.line_code, " +
                    "          scheduled.direction_number, " +
                    "          observed.departure_time " +
                    "        ORDER BY " +
                    "          scheduled.departure_time <-> observed.departure_time " +
                    "       ) = 1 THEN observed.time_since_last_departure END) AS observed_time_since_last_departure, " +
                    "      scheduled.time_since_last_departure AS scheduled_time_since_last_departure, " +
                    "      (CASE WHEN row_number() OVER ( " +
                    "        PARTITION BY " +
                    "          scheduled.departure_station_code, " +
                    "          scheduled.line_code, " +
                    "          scheduled.direction_number, " +
                    "          observed.departure_time " +
                    "        ORDER BY " +
                    "          scheduled.departure_time <-> observed.departure_time " +
                    "       ) = 1 THEN (observed.time_since_last_departure - scheduled.time_since_last_departure) END) AS headway_deviation, " +
                    "      (CASE WHEN row_number() OVER ( " +
                    "        PARTITION BY " +
                    "          scheduled.departure_station_code, " +
                    "          scheduled.line_code, " +
                    "          scheduled.direction_number, " +
                    "          observed.departure_time " +
                    "        ORDER BY " +
                    "          scheduled.departure_time <-> observed.departure_time " +
                    "       ) = 1 THEN (interval_to_seconds(observed.departure_time - scheduled.departure_time) / 60) END) AS schedule_deviation " +
                    "    FROM ( " +
                    "      SELECT " +
                    "        scheduled.departure_station_name, " +
                    "        scheduled.departure_station_code, " +
                    "        scheduled.line_name, " +
                    "        scheduled.line_code, " +
                    "        scheduled.direction_name, " +
                    "        scheduled.direction_number, " +
                    "        scheduled.destination_station_name, " +
                    "        scheduled.destination_station_code, " +
                    "        scheduled.departure_time, " +
                    "        nullif(least(cast(interval_to_seconds(scheduled.departure_time - lag(scheduled.departure_time, 1) OVER ( " +
                    "          PARTITION BY " +
                    "            scheduled.departure_station_code, " +
                    "            scheduled.line_code, " +
                    "            scheduled.direction_number " +
                    "          ORDER BY " +
                    "            scheduled.departure_time " +
                    "          )) / 60 AS NUMERIC), 180 /* 3 hours */), 180 /* 3 hours */) AS time_since_last_departure " +
                    "      FROM " +
                    "        train_departure AS scheduled " +
                    "      WHERE " +
                    "        scheduled.departure_time BETWEEN (now() - INTERVAL '1 hour' - INTERVAL '3 hours') AND (now() + INTERVAL '3 hours') AND " +
                    "        scheduled.type = 'SCHEDULED' " +
                    "    ) AS scheduled " +
                    "    LEFT JOIN LATERAL ( " +
                    "      SELECT " +
                    "        observed.train_id, " +
                    "        observed.real_train_id, " +
                    "        observed.departure_station_name, " +
                    "        observed.departure_station_code, " +
                    "        observed.line_name, " +
                    "        observed.line_code, " +
                    "        observed.direction_name, " +
                    "        observed.direction_number, " +
                    "        observed.destination_station_name, " +
                    "        observed.destination_station_code, " +
                    "        observed.num_cars, " +
                    "        observed.departure_time, " +
                    "        nullif(least(cast(interval_to_seconds(observed.departure_time - lag(observed.departure_time, 1) OVER ( " +
                    "          PARTITION BY " +
                    "            observed.departure_station_code, " +
                    "            observed.line_code, " +
                    "            observed.direction_number " +
                    "          ORDER BY " +
                    "            observed.departure_time " +
                    "          )) / 60 AS NUMERIC), 180 /* 3 hours */), 180 /* 3 hours */) AS time_since_last_departure " +
                    "      FROM " +
                    "        train_departure AS observed " +
                    "      WHERE " +
                    "        observed.departure_station_code = scheduled.departure_station_code AND " +
                    "        observed.line_code = scheduled.line_code AND " +
                    "        observed.direction_number = scheduled.direction_number AND " +
                    "        observed.departure_time BETWEEN (scheduled.departure_time - INTERVAL '3 hours') AND (scheduled.departure_time + INTERVAL '3 hours') AND " +
                    "        observed.type = 'OBSERVED' " +
                    "      ORDER BY " +
                    "        observed.departure_time <-> scheduled.departure_time " +
                    "      LIMIT 1 " +
                    "    ) AS observed ON TRUE " +
                    "    ORDER BY " +
                    "      scheduled.departure_station_code, " +
                    "      scheduled.line_code, " +
                    "      scheduled.direction_number, " +
                    "      scheduled.departure_time, " +
                    "      scheduled.departure_time <-> observed.departure_time " +
                    "  ) AS scheduled " +
                    "  FULL OUTER JOIN ( " +
                    "    SELECT " +
                    "      observed.train_id AS train_id, " +
                    "      observed.real_train_id AS real_train_id, " +
                    "      observed.departure_station_name AS departure_station_name, " +
                    "      observed.departure_station_code AS departure_station_code, " +
                    "      observed.line_name AS line_name, " +
                    "      observed.line_code AS line_code, " +
                    "      observed.direction_name AS direction_name, " +
                    "      observed.direction_number AS direction_number, " +
                    "      NULL AS scheduled_destination_station_name, " +
                    "      NULL AS scheduled_destination_station_code, " +
                    "      observed.destination_station_name AS observed_destination_station_name, " +
                    "      observed.destination_station_code AS observed_destination_station_code, " +
                    "      observed.num_cars AS observed_num_cars, " +
                    "      observed.departure_time AS observed_departure_time, " +
                    "      NULL AS scheduled_departure_time, " +
                    "      observed.time_since_last_departure AS observed_time_since_last_departure, " +
                    "      scheduled.time_since_last_departure AS scheduled_time_since_last_departure, " +
                    "      (observed.time_since_last_departure - scheduled.time_since_last_departure) AS headway_deviation, " +
                    "      NULL AS schedule_deviation " +
                    "    FROM ( " +
                    "      SELECT " +
                    "        observed.train_id, " +
                    "        observed.real_train_id, " +
                    "        observed.departure_station_name, " +
                    "        observed.departure_station_code, " +
                    "        observed.line_name, " +
                    "        observed.line_code, " +
                    "        observed.direction_name, " +
                    "        observed.direction_number, " +
                    "        observed.destination_station_name, " +
                    "        observed.destination_station_code, " +
                    "        observed.num_cars, " +
                    "        observed.departure_time, " +
                    "        nullif(least(cast(interval_to_seconds(observed.departure_time - lag(observed.departure_time, 1) OVER ( " +
                    "          PARTITION BY " +
                    "            observed.departure_station_code, " +
                    "            observed.line_code, " +
                    "            observed.direction_number " +
                    "          ORDER BY " +
                    "            observed.departure_time " +
                    "          )) / 60 AS NUMERIC), 180 /* 3 hours */), 180 /* 3 hours */) AS time_since_last_departure " +
                    "      FROM " +
                    "        train_departure AS observed " +
                    "      WHERE " +
                    "        observed.departure_time BETWEEN (now() - INTERVAL '1 hour' - INTERVAL '3 hours') AND now() AND " +
                    "        observed.type = 'OBSERVED' " +
                    "    ) AS observed " +
                    "    LEFT JOIN LATERAL ( " +
                    "      SELECT " +
                    "        nullif(least(cast(interval_to_seconds(scheduled.departure_time - lag(scheduled.departure_time, 1) OVER ( " +
                    "          PARTITION BY " +
                    "            scheduled.departure_station_code, " +
                    "            scheduled.line_code, " +
                    "            scheduled.direction_number " +
                    "          ORDER BY " +
                    "            scheduled.departure_time " +
                    "          )) / 60 AS NUMERIC), 180 /* 3 hours */), 180 /* 3 hours */) AS time_since_last_departure " +
                    "      FROM " +
                    "        train_departure AS scheduled " +
                    "      WHERE " +
                    "        scheduled.departure_station_code = observed.departure_station_code AND " +
                    "        scheduled.line_code = observed.line_code AND " +
                    "        scheduled.direction_number = observed.direction_number AND " +
                    "        scheduled.departure_time BETWEEN (observed.departure_time - INTERVAL '3 hours') AND (observed.departure_time + INTERVAL '3 hours') AND " +
                    "        scheduled.type = 'SCHEDULED' " +
                    "      ORDER BY " +
                    "        scheduled.departure_time <-> observed.departure_time " +
                    "      LIMIT 1 " +
                    "    ) AS scheduled ON TRUE " +
                    "  ) AS observed ON ( " +
                    "    observed.departure_station_code = scheduled.departure_station_code AND " +
                    "    observed.line_code = scheduled.line_code AND " +
                    "    observed.direction_number = scheduled.direction_number AND " +
                    "    observed.observed_departure_time = scheduled.observed_departure_time " +
                    "  ) " +
                    "  WHERE " +
                    "    coalesce(observed.observed_departure_time, scheduled.scheduled_departure_time) BETWEEN (now() - INTERVAL '1 hour') AND now() " +
                    ") " +
                    "SELECT merge_train_departure_info(" +
                    "  train_id, " +
                    "  real_train_id, " +
                    "  departure_station_name, " +
                    "  departure_station_code, " +
                    "  line_name, " +
                    "  line_code, " +
                    "  direction_name, " +
                    "  direction_number, " +
                    "  scheduled_destination_station_name, " +
                    "  scheduled_destination_station_code, " +
                    "  observed_destination_station_name, " +
                    "  observed_destination_station_code, " +
                    "  observed_num_cars, " +
                    "  observed_departure_time, " +
                    "  scheduled_departure_time, " +
                    "  observed_time_since_last_departure, " +
                    "  scheduled_time_since_last_departure, " +
                    "  headway_deviation, " +
                    "  schedule_deviation" +
                    ") " +
                    "FROM new_value;"
    )
    List<Boolean> updateTrainDepartureInfo();
}

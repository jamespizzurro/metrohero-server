package com.jamespizzurro.metrorailserver.repository;

import com.jamespizzurro.metrorailserver.domain.SavedTrip;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface TripRepository extends CrudRepository<SavedTrip, Long> {

    @Query(nativeQuery = true, value =
            "SELECT" +
            "  array_to_string(array_replace(array_agg(time), (array_agg(time))[array_length(array_agg(time), 1)], 'now'), ',') AS times," +
            "  array_to_string(array_agg(predicted_ride_time), ',') AS predicted_ride_times," +
            "  array_to_string(array_agg(expected_ride_time), ',') AS expected_ride_times" +
            " FROM (" +
            "  SELECT" +
            "    trim(LEADING '0' FROM to_char(ts_round(date, 120), 'HH:MIam')) AS time," +
            "    coalesce(round(cast(avg(ts.predicted_ride_time) AS NUMERIC), 2), -1) AS predicted_ride_time," +
            "    coalesce(round(cast(avg(ts.expected_ride_time) AS NUMERIC), 2), -1) AS expected_ride_time" +
            "  FROM trip_state ts" +
            "  WHERE ts.from_station_code = cast(?1 AS TEXT) AND ts.to_station_code = cast(?2 AS TEXT) AND ts.date >= (now() - INTERVAL '1 hour')" +
            "  GROUP BY ts.from_station_code, ts.to_station_code, ts_round(date, 120)" +
            "  ORDER BY ts_round(date, 120) ASC" +
            ") AS t;"
    )
    List<Object[]> getRecentData(String fromStationCode, String toStationCode);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM trip_state WHERE date < (NOW() - INTERVAL '24 hours')")
    void removeOld();
}

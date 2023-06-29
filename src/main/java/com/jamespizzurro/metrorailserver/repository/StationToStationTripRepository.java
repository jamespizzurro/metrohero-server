package com.jamespizzurro.metrorailserver.repository;

import com.jamespizzurro.metrorailserver.domain.StationToStationTrip;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Repository
public interface StationToStationTripRepository extends CrudRepository<StationToStationTrip, Integer> {

    @Query(nativeQuery = true,
            value = "SELECT avg(t.trip_duration) " +
                    "FROM ( " +
                    "  SELECT stst.trip_duration " +
                    "  FROM station_to_station_trip stst " +
                    "  WHERE stst.departing_station_code = ?1 AND stst.arriving_station_code = ?2 " +
                    "  ORDER BY stst.departing_time DESC " +
                    "  LIMIT 100 " +
                    ") AS t"
    )
    BigDecimal getRecentAverageTripDuration(String fromStationCode, String toStationCode);

    @Query(nativeQuery = true,
            value = "SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY trip_duration) " +
                    "FROM station_to_station_trip stst " +
                    "WHERE stst.departing_station_code = ?1 AND stst.arriving_station_code = ?2"
    )
    BigDecimal getMedianTripDuration(String fromStationCode, String toStationCode);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM station_to_station_trip WHERE arriving_time < (NOW() - INTERVAL '24 months')")
    void removeOld();
}

package com.jamespizzurro.metrorailserver.service;

import com.jamespizzurro.metrorailserver.BeanPropertyRowMapper;
import com.jamespizzurro.metrorailserver.domain.TrackCircuitLocationData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TrackCircuitService {

    private static final Logger logger = LoggerFactory.getLogger(TrackCircuitService.class);

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private volatile Map<Integer, TrackCircuitLocationData> trackCircuitLocationDataByTrackCircuitId;

    @Autowired
    public TrackCircuitService(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Async
    public void updateTrackCircuitLocationData() {
        logger.info("Updating track circuit location data...");

        Map<Integer, TrackCircuitLocationData> trackCircuitLocationDataByTrackCircuitId = new HashMap<>();

        String query = (
                "SELECT " +
                "  raw_track_circuit_id AS track_circuit_id, " +
                "  lat, " +
                "  lon " +
                "FROM ( " +
                "  SELECT " +
                "    raw_track_circuit_id, " +
                "    lat, " +
                "    lon, " +
                "    row_number() OVER (PARTITION BY raw_track_circuit_id ORDER BY count(*) DESC) AS row_number " +
                "  FROM train_status " +
                "  WHERE " +
                "    observed_date >= '2018-03-12 00:00:00' AND " +
                "    destination_id IS NOT NULL AND " +
                "    lat IS NOT NULL AND " +
                "    lon IS NOT NULL " +
                "  GROUP BY raw_track_circuit_id, lat, lon " +
                ") AS t " +
                "WHERE row_number = 1"
        );

        List<TrackCircuitLocationData> trackCircuitLocationData = this.namedParameterJdbcTemplate.query(query, new BeanPropertyRowMapper<>(TrackCircuitLocationData.class));
        for (TrackCircuitLocationData data : trackCircuitLocationData) {
            trackCircuitLocationDataByTrackCircuitId.put(data.getTrackCircuitId(), data);
        }

        this.trackCircuitLocationDataByTrackCircuitId = trackCircuitLocationDataByTrackCircuitId;

        logger.info("...successfully updated track circuit location data!");
    }

    // BASED ON: https://stackoverflow.com/a/18738281/1072621
    public int calculateDirection(TrackCircuitLocationData loc1, TrackCircuitLocationData loc2) {
        double dLon = (loc2.getLon() - loc1.getLon());

        double y = Math.sin(dLon) * Math.cos(loc2.getLat());
        double x = Math.cos(loc1.getLat()) * Math.sin(loc2.getLat()) - Math.sin(loc1.getLat()) * Math.cos(loc2.getLat()) * Math.cos(dLon);

        double brng = Math.atan2(y, x);

        brng = Math.toDegrees(brng);
        brng = (brng + 360) % 360;

        return (int) Math.round(brng);
    }

    public Map<Integer, TrackCircuitLocationData> getTrackCircuitLocationDataByTrackCircuitId() {
        return trackCircuitLocationDataByTrackCircuitId;
    }
}

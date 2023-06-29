package com.jamespizzurro.metrorailserver.service;

import com.jamespizzurro.metrorailserver.ConfigUtil;
import com.jamespizzurro.metrorailserver.NetworkUtil;
import com.jamespizzurro.metrorailserver.domain.ElevatorEscalatorOutage;
import com.jamespizzurro.metrorailserver.domain.marshallers.ElevatorIncident;
import com.jamespizzurro.metrorailserver.domain.marshallers.ElevatorIncidents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
public class ElevatorEscalatorService {

    private static final Logger logger = LoggerFactory.getLogger(ElevatorEscalatorService.class);

    private final ConfigUtil configUtil;

    // exposed member variables (that therefore need to be thread-safe)
    private volatile Map<String, List<ElevatorEscalatorOutage>> elevatorOutagesByStation;
    private volatile Map<String, Boolean> hasElevatorOutagesByStation;
    private volatile Map<String, List<ElevatorEscalatorOutage>> escalatorOutagesByStation;
    private volatile Map<String, Boolean> hasEscalatorOutagesByStation;

    @Autowired
    public ElevatorEscalatorService(ConfigUtil configUtil) {
        this.configUtil = configUtil;
    }

    @PostConstruct
    private void init() {
        logger.info("Initializing elevator/escalator service...");

        updateElevatorEscalatorOutages();

        logger.info("...elevator/escalator service initialized!");
    }

    @Scheduled(fixedDelay = 60000)   // runs every minute
    private void updateElevatorEscalatorOutages() {
        logger.info("Updating elevator/escalator outages from WMATA...");

        HttpEntity<String> requestEntity = NetworkUtil.createNewHttpEntity(configUtil.getWmataApiKey());
        RestTemplate restTemplate;
        ResponseEntity<ElevatorIncidents> response;
        try {
            restTemplate = NetworkUtil.createNewRestTemplate(true);
            response = restTemplate.exchange(
                    configUtil.getWmataElevatorEscalatorOutagesApiUrl(),
                    HttpMethod.GET, requestEntity, ElevatorIncidents.class);
        } catch (RestClientException e) {
            restTemplate = NetworkUtil.createNewRestTemplate(false);
            response = restTemplate.exchange(
                    configUtil.getWmataElevatorEscalatorOutagesApiUrl(),
                    HttpMethod.GET, requestEntity, ElevatorIncidents.class);
            logger.info("Response from WMATA Elevator/Escalator Outages API not gzipped!");
        } catch (Exception e) {
            logger.warn("Failed to get elevator/escalator outages from WMATA's API!", e);
            return;
        }

        if (response.getBody() == null) {
            logger.warn("Failed to get elevator/escalator outages from WMATA's API! (response body is null)");
            return;
        }

        Map<String, List<ElevatorEscalatorOutage>> elevatorOutagesByStation;
        Map<String, Boolean> hasElevatorOutagesByStation;
        Map<String, List<ElevatorEscalatorOutage>> escalatorOutagesByStation;
        Map<String, Boolean> hasEscalatorOutagesByStation;

        ElevatorIncidents elevatorIncidents = response.getBody();
        if (elevatorIncidents.getElevatorIncidents() != null) {
            elevatorOutagesByStation = new HashMap<>();
            hasElevatorOutagesByStation = new HashMap<>();
            escalatorOutagesByStation = new HashMap<>();
            hasEscalatorOutagesByStation = new HashMap<>();

            for (ElevatorIncident elevatorIncident : elevatorIncidents.getElevatorIncidents()) {
                String stationCode = elevatorIncident.getStationCode();
                ElevatorEscalatorOutage elevatorEscalatorOutage = new ElevatorEscalatorOutage(elevatorIncident);

                if ("ELEVATOR".equals(elevatorIncident.getUnitType())) {
                    List<ElevatorEscalatorOutage> elevatorOutagesForStation = elevatorOutagesByStation.computeIfAbsent(stationCode, k -> new ArrayList<>());
                    elevatorOutagesForStation.add(elevatorEscalatorOutage);
                    hasElevatorOutagesByStation.put(stationCode, true);
                } else if ("ESCALATOR".equals(elevatorIncident.getUnitType())) {
                    List<ElevatorEscalatorOutage> escalatorOutagesForStation = escalatorOutagesByStation.computeIfAbsent(stationCode, k -> new ArrayList<>());
                    escalatorOutagesForStation.add(elevatorEscalatorOutage);
                    hasEscalatorOutagesByStation.put(stationCode, true);
                }
            }

            // sort lists of outages by updated date in descending chronological order
            for (List<ElevatorEscalatorOutage> elevatorOutagesForStation : elevatorOutagesByStation.values()) {
                elevatorOutagesForStation.sort(Comparator.comparing(ElevatorEscalatorOutage::getUpdatedDate).reversed());
            }
            for (List<ElevatorEscalatorOutage> escalatorOutagesForStation : escalatorOutagesByStation.values()) {
                escalatorOutagesForStation.sort(Comparator.comparing(ElevatorEscalatorOutage::getUpdatedDate).reversed());
            }
        } else {
            elevatorOutagesByStation = null;
            hasElevatorOutagesByStation = null;
            escalatorOutagesByStation = null;
            hasEscalatorOutagesByStation = null;
        }

        this.elevatorOutagesByStation = elevatorOutagesByStation;
        this.hasElevatorOutagesByStation = hasElevatorOutagesByStation;
        this.escalatorOutagesByStation = escalatorOutagesByStation;
        this.hasEscalatorOutagesByStation = hasEscalatorOutagesByStation;

        logger.info("...successfully updated elevator/escalator outages from WMATA!");
    }

    public Map<String, List<ElevatorEscalatorOutage>> getElevatorOutagesByStation() {
        return elevatorOutagesByStation;
    }

    public Map<String, Boolean> getHasElevatorOutagesByStation() {
        return hasElevatorOutagesByStation;
    }

    public Map<String, List<ElevatorEscalatorOutage>> getEscalatorOutagesByStation() {
        return escalatorOutagesByStation;
    }

    public Map<String, Boolean> getHasEscalatorOutagesByStation() {
        return hasEscalatorOutagesByStation;
    }
}

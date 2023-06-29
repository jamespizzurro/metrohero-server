package com.jamespizzurro.metrorailserver.service;

import com.google.common.collect.Multimap;
import com.jamespizzurro.metrorailserver.ConfigUtil;
import com.jamespizzurro.metrorailserver.GzipGsonHttpMessageConverter;
import com.jamespizzurro.metrorailserver.IncidentTextParser;
import com.jamespizzurro.metrorailserver.RequestHandler;
import com.jamespizzurro.metrorailserver.domain.RailIncident;
import com.jamespizzurro.metrorailserver.domain.marshallers.RailIncidentUpdate;
import com.jamespizzurro.metrorailserver.domain.marshallers.RailIncidentUpdates;
import com.jamespizzurro.metrorailserver.repository.RailIncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
public class RailIncidentService {

    private static final Logger logger = LoggerFactory.getLogger(RailIncidentService.class);

    private final ConfigUtil configUtil;
    private final IncidentTextParser incidentTextParser;
    private final RailIncidentRepository railIncidentRepository;

    private volatile Map<String, List<RailIncident>> stationRailIncidentsMap;
    private volatile Map<String, Boolean> stationHasRailIncidentsMap;
    private volatile List<RailIncident> lineRailIncidents;
    private volatile Map<String, List<RailIncident>> railIncidentsByLine;

    @Autowired
    public RailIncidentService(ConfigUtil configUtil, IncidentTextParser incidentTextParser, RailIncidentRepository railIncidentRepository) {
        this.configUtil = configUtil;
        this.incidentTextParser = incidentTextParser;
        this.railIncidentRepository = railIncidentRepository;
    }

    @PostConstruct
    private void init() {
        logger.info("Initializing rail incident service...");

        update();

        logger.info("...rail incident service initialized!");
    }

    @Scheduled(fixedDelay = 30000)  // every 30 seconds
    private void update() {
        logger.info("Updating rail incidents from WMATA...");

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setAccept(Collections.singletonList(new MediaType("application", "json")));
        requestHeaders.set("api_key", configUtil.getWmataApiKey());
        requestHeaders.set(HttpHeaders.ACCEPT_ENCODING, "gzip");
        HttpEntity<String> requestEntity = new HttpEntity<>("parameters", requestHeaders);

        RestTemplate restTemplate;
        ResponseEntity<RailIncidentUpdates> response;
        try {
            restTemplate = (new RequestHandler()).getRestTemplate();
            restTemplate.getMessageConverters().clear();
            restTemplate.getMessageConverters().add(new GzipGsonHttpMessageConverter());
            response = restTemplate.exchange(
                    this.configUtil.getWmataRailIncidentsApiUrl(),
                    HttpMethod.GET, requestEntity, RailIncidentUpdates.class);
        } catch (RestClientException e) {
            restTemplate = (new RequestHandler()).getRestTemplate();
            restTemplate.getMessageConverters().clear();
            restTemplate.getMessageConverters().add(new GsonHttpMessageConverter());
            response = restTemplate.exchange(
                    this.configUtil.getWmataRailIncidentsApiUrl(),
                    HttpMethod.GET, requestEntity, RailIncidentUpdates.class);
            logger.warn("Response from WMATA Rail Incidents API not gzipped!");
        }

        Map<String, List<RailIncident>> stationRailIncidentsMap = new HashMap<>();
        Map<String, Boolean> stationHasRailIncidentsMap = new HashMap<>();
        List<RailIncident> lineRailIncidents = new ArrayList<>();
        Map<String, List<RailIncident>> railIncidentsByLine = new HashMap<>();

        // process any and all rail incidents

        if (response.getBody() != null) {
            for (RailIncidentUpdate riu : response.getBody().getIncidents()) {
                if (riu.getDescription() == null || riu.getDescription().isEmpty()) {
                    continue;
                }

                Set<String> totalStationCodes = new HashSet<>();
                Set<String> totalKeywords = new HashSet<>();

                Multimap<Set<String>, Set<String>> incidentMap = this.incidentTextParser.parseTextForIncidents(riu.getDescription(), null);
                for (Map.Entry<Set<String>, Set<String>> incident : incidentMap.entries()) {
                    Set<String> stationCodes = incident.getKey();
                    Set<String> keywords = incident.getValue();

                    totalStationCodes.addAll(stationCodes);
                    totalKeywords.addAll(keywords);
                }

                RailIncident railIncident = new RailIncident(totalStationCodes, riu.getLinesAffected(), totalKeywords, riu.getDescription(), riu.getDateUpdated(), riu.getIncidentID());

                for (String stationCode : totalStationCodes) {
                    List<RailIncident> railIncidents = stationRailIncidentsMap.computeIfAbsent(stationCode, l -> new ArrayList<>());
                    railIncidents.add(railIncident);

                    stationHasRailIncidentsMap.put(stationCode, true);
                }

                // process lines mentioned

                if (riu.getLinesAffected() != null) {
                    lineRailIncidents.add(railIncident);

                    for (String lineCode : riu.getLinesAffected()) {
                        List<RailIncident> railIncidentsForLine = railIncidentsByLine.computeIfAbsent(lineCode, k-> new ArrayList<>());
                        railIncidentsForLine.add(railIncident);
                    }
                }
            }
        }

        for (List<RailIncident> stationRailIncidents : stationRailIncidentsMap.values()) {
            stationRailIncidents.sort(getRailIncidentDescendingOrderComparator());
        }
        lineRailIncidents.sort(getRailIncidentDescendingOrderComparator());
        for (List<RailIncident> railIncidentForLine : railIncidentsByLine.values()) {
            railIncidentForLine.sort(getRailIncidentDescendingOrderComparator());
        }

        this.stationRailIncidentsMap = stationRailIncidentsMap;
        this.stationHasRailIncidentsMap = stationHasRailIncidentsMap;
        this.lineRailIncidents = lineRailIncidents;
        this.railIncidentsByLine = railIncidentsByLine;

        this.railIncidentRepository.saveAll(lineRailIncidents);

        logger.info("...successfully updated rail incidents from WMATA!");
    }

    public static Comparator<RailIncident> getRailIncidentDescendingOrderComparator() {
        return (ri1, ri2) -> ri2.getTimestamp().compareTo(ri1.getTimestamp());
    }

    public Map<String, List<RailIncident>> getStationRailIncidentsMap() {
        return stationRailIncidentsMap;
    }

    public Map<String, Boolean> getStationHasRailIncidentsMap() {
        return stationHasRailIncidentsMap;
    }

    public List<RailIncident> getLineRailIncidents() {
        return lineRailIncidents;
    }

    public Map<String, List<RailIncident>> getRailIncidentsByLine() {
        return railIncidentsByLine;
    }
}

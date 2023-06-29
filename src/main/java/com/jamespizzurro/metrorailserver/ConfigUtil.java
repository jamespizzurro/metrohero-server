package com.jamespizzurro.metrorailserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = "singleton")
public class ConfigUtil {

    @Value("${developmentmode}")
    private Boolean developmentmode;

    @Value("${wmata.production.apikey}")
    private String wmataProductionApiKey;

    @Value("${wmata.development.apikey}")
    private String wmataDevelopmentApiKey;

    @Value("${wmata.api.railincidents}")
    private String wmataRailIncidentsApiUrl;

    @Value("${wmata.api.trainpositions}")
    private String wmataTrainPositionsApiUrl;

    @Value("${wmata.api.elevatorescalatoroutages}")
    private String wmataElevatorEscalatorOutagesApiUrl;

    @Value("${wmata.api.newgtfsfeed}")
    private String wmataNewGTFSFeedUrl;

    public boolean isDevelopmentMode() {
        return developmentmode;
    }

    public String getWmataApiKey() {
        if (isDevelopmentMode()) {
            return wmataDevelopmentApiKey;
        } else {
            return wmataProductionApiKey;
        }
    }

    public String getWmataRailIncidentsApiUrl() {
        return wmataRailIncidentsApiUrl;
    }

    public String getWmataTrainPositionsApiUrl() {
        return wmataTrainPositionsApiUrl;
    }

    public String getWmataElevatorEscalatorOutagesApiUrl() {
        return wmataElevatorEscalatorOutagesApiUrl;
    }

    public String getWmataNewGTFSFeedUrl() {
        return wmataNewGTFSFeedUrl;
    }
}

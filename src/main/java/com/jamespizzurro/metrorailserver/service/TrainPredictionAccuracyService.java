package com.jamespizzurro.metrorailserver.service;

import com.jamespizzurro.metrorailserver.ConfigUtil;
import com.jamespizzurro.metrorailserver.NetworkUtil;
import com.jamespizzurro.metrorailserver.domain.TrainPredictionAccuracyMeasurement;
import com.jamespizzurro.metrorailserver.domain.TrainStatus;
import com.jamespizzurro.metrorailserver.repository.TrainPredictionAccuracyMeasurementRepository;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class TrainPredictionAccuracyService {

    // TODO: re-enable scheduled jobs if we decide this stuff is worthwhile to start using again

    private static final Logger logger = LoggerFactory.getLogger(TrainPredictionAccuracyService.class);

    private static final Set<String> validLineCodes = new HashSet<>();
    static {
        validLineCodes.addAll(Arrays.asList("RD", "OR", "SV", "BL", "YL", "GR"));
    }

    private ConfigUtil configUtil;
    private TrainService trainService;
    private TrainPredictionAccuracyMeasurementRepository trainPredictionAccuracyMeasurementRepository;

    private HashMap<String, List<TrainPrediction>> wmataTrainPredictionsByPredictionOriginStationAndLineAndDestination;
    private HashMap<String, List<TrainPrediction>> mhTrainPredictionsByPredictionOriginStationAndLineAndDestination;

    @Autowired
    public TrainPredictionAccuracyService(ConfigUtil configUtil, TrainService trainService, TrainPredictionAccuracyMeasurementRepository trainPredictionAccuracyMeasurementRepository) {
        this.configUtil = configUtil;
        this.trainService = trainService;
        this.trainPredictionAccuracyMeasurementRepository = trainPredictionAccuracyMeasurementRepository;

        this.wmataTrainPredictionsByPredictionOriginStationAndLineAndDestination = new HashMap<>();
        this.mhTrainPredictionsByPredictionOriginStationAndLineAndDestination = new HashMap<>();
    }

//    @Scheduled(fixedDelay = 2000)
    public void fetchTrainPredictions() {
        if (this.configUtil.isDevelopmentMode()) {
            return;
        }

        ResponseEntity<WmataTrainPredictions> response;
        String url = "https://api.wmata.com/beta/StationPrediction.svc/json/GetPrediction/All";
        HttpEntity<String> requestEntity = NetworkUtil.createNewHttpEntity(this.configUtil.getWmataApiKey());
        RestTemplate restTemplate;
        try {
            restTemplate = NetworkUtil.createNewRestTemplate(true);
            response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, WmataTrainPredictions.class);
        } catch (RestClientException e) {
            restTemplate = NetworkUtil.createNewRestTemplate(false);
            response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, WmataTrainPredictions.class);
        }

        Calendar now = Calendar.getInstance();

        if (response.getBody() == null || response.getBody().getTrains() == null || response.getBody().getTrains().isEmpty()) {
            return;
        }

        if (this.trainService.getStationTrainStatusesMap() == null || this.trainService.getStationTrainStatusesMap().isEmpty()) {
            return;
        }

        // parse WMATA predictions
        Set<String> parsedWmataPredictionOriginStationAndLineAndDestinationKeys = new HashSet<>();
        for (TrainPrediction wmataTrainPrediction : response.getBody().getTrains()) {
            wmataTrainPrediction.setPredictionTime(now);
            wmataTrainPrediction.setPredictionOriginStationCode(wmataTrainPrediction.getLocationCode());
            wmataTrainPrediction.setHasCorrespondingWmataPrediction(true);  // I mean, yeah, this *is* a WMATA train prediction

            if (!isTrainInRevenueService(wmataTrainPrediction)) {
                continue;
            }

            Double wmataMinutesPrediction = wmataTrainPrediction.getMinutesFromMin();
            if (wmataMinutesPrediction == null) {
                // discard "blank" predictions
                continue;
            }

            String predictionOriginStationAndLineAndDestinationKey = wmataTrainPrediction.getKey();

            if (parsedWmataPredictionOriginStationAndLineAndDestinationKeys.contains(predictionOriginStationAndLineAndDestinationKey)) {
                // we're only interested in the most recent train prediction per line and destination
                continue;
            }

            List<TrainPrediction> wmataTrainPredictions = this.wmataTrainPredictionsByPredictionOriginStationAndLineAndDestination.computeIfAbsent(predictionOriginStationAndLineAndDestinationKey, k -> new ArrayList<>());
            wmataTrainPredictions.add(wmataTrainPrediction);

            parsedWmataPredictionOriginStationAndLineAndDestinationKeys.add(predictionOriginStationAndLineAndDestinationKey);
        }

        // fetch and parse MH predictions
        Set<String> parsedMhPredictionOriginStationAndLineAndDestinationKeys = new HashSet<>();
        for (String stationCode : this.trainService.getStationCodesSet()) {
            if (this.trainService.getStationTrainStatusesMap().get(stationCode) == null) {
                continue;
            }

            for (TrainStatus trainStatus : this.trainService.getStationTrainStatusesMap().get(stationCode)) {
                TrainPrediction mhTrainPrediction = new TrainPrediction(trainStatus, now, stationCode);

                if (!isTrainInRevenueService(mhTrainPrediction)) {
                    continue;
                }

                Double mhMinutesPrediction = mhTrainPrediction.getMinutesFromMin();
                if (mhMinutesPrediction == null) {
                    // discard "blank" predictions
                    continue;
                }

                String predictionOriginStationAndLineAndDestinationKey = mhTrainPrediction.getKey();
                mhTrainPrediction.setHasCorrespondingWmataPrediction(parsedWmataPredictionOriginStationAndLineAndDestinationKeys.contains(predictionOriginStationAndLineAndDestinationKey));

                if (parsedMhPredictionOriginStationAndLineAndDestinationKeys.contains(predictionOriginStationAndLineAndDestinationKey)) {
                    // we're only interested in the most recent train prediction per line and destination
                    continue;
                }

                List<TrainPrediction> mhTrainPredictions = this.mhTrainPredictionsByPredictionOriginStationAndLineAndDestination.computeIfAbsent(predictionOriginStationAndLineAndDestinationKey, k -> new ArrayList<>());
                mhTrainPredictions.add(mhTrainPrediction);

                parsedMhPredictionOriginStationAndLineAndDestinationKeys.add(predictionOriginStationAndLineAndDestinationKey);
            }
        }

        List<TrainPredictionAccuracyMeasurement> trainPredictionAccuracyMeasurements = new ArrayList<>();

        // analyze WMATA predictions
        for (Map.Entry<String, List<TrainPrediction>> entry : this.wmataTrainPredictionsByPredictionOriginStationAndLineAndDestination.entrySet()) {
            String predictionOriginStationAndlineCodeAndDestinationCode = entry.getKey();
            List<TrainPrediction> wmataTrainPredictionsForPredictionOriginStationAndLineAndDestination = entry.getValue();
            if (wmataTrainPredictionsForPredictionOriginStationAndLineAndDestination == null || wmataTrainPredictionsForPredictionOriginStationAndLineAndDestination.isEmpty()) {
                continue;
            }

            TrainPrediction mostRecentWmataTrainPredictionForPredictionOriginStationAndLineAndDestination = wmataTrainPredictionsForPredictionOriginStationAndLineAndDestination.get(wmataTrainPredictionsForPredictionOriginStationAndLineAndDestination.size() - 1);
            if ("BRD".equals(mostRecentWmataTrainPredictionForPredictionOriginStationAndLineAndDestination.getMin())) {
                if (wmataTrainPredictionsForPredictionOriginStationAndLineAndDestination.size() == 1) {
                    wmataTrainPredictionsForPredictionOriginStationAndLineAndDestination.clear();
                    continue;
                }

                TrainPrediction leastRecentWmataTrainPredictionForPredictionOriginStationAndLineAndDestination = wmataTrainPredictionsForPredictionOriginStationAndLineAndDestination.get(0);

                double wmataTotalPredictionTime = (mostRecentWmataTrainPredictionForPredictionOriginStationAndLineAndDestination.getPredictionTime().getTimeInMillis() - leastRecentWmataTrainPredictionForPredictionOriginStationAndLineAndDestination.getPredictionTime().getTime().getTime()) / 1000d / 60d;

                SummaryStatistics wmataLocalPredictionError = new SummaryStatistics();

                for (TrainPrediction wmataTrainPredictionForPredictionOriginStationAndLineAndDestination : wmataTrainPredictionsForPredictionOriginStationAndLineAndDestination) {
                    Double wmataMinutesPrediction = wmataTrainPredictionForPredictionOriginStationAndLineAndDestination.getMinutesFromMin();
                    double wmataPredictionTimeSinceStart = (wmataTrainPredictionForPredictionOriginStationAndLineAndDestination.getPredictionTime().getTimeInMillis() - leastRecentWmataTrainPredictionForPredictionOriginStationAndLineAndDestination.getPredictionTime().getTime().getTime()) / 1000d / 60d;
                    double wmataPredictionTimeRemaining = wmataTotalPredictionTime - wmataPredictionTimeSinceStart;
                    double wmataPredictionError = wmataPredictionTimeRemaining - wmataMinutesPrediction;

                    wmataLocalPredictionError.addValue(wmataPredictionError);
                }

                String[] predictionOriginStationAndlineCodeAndDestinationCodeArray = predictionOriginStationAndlineCodeAndDestinationCode.split("_");
                String predictionOriginStationCode = predictionOriginStationAndlineCodeAndDestinationCodeArray[0];
                String lineCode = predictionOriginStationAndlineCodeAndDestinationCodeArray[1];
                String destinationStationCode = predictionOriginStationAndlineCodeAndDestinationCodeArray[2];

                TrainPredictionAccuracyMeasurement trainPredictionAccuracyMeasurement = new TrainPredictionAccuracyMeasurement("WMATA", leastRecentWmataTrainPredictionForPredictionOriginStationAndLineAndDestination.getPredictionTime(), mostRecentWmataTrainPredictionForPredictionOriginStationAndLineAndDestination.getPredictionTime(), predictionOriginStationCode, lineCode, destinationStationCode, leastRecentWmataTrainPredictionForPredictionOriginStationAndLineAndDestination.getMin(), wmataLocalPredictionError.getMean(), wmataLocalPredictionError.getN(), mostRecentWmataTrainPredictionForPredictionOriginStationAndLineAndDestination.hasCorrespondingWmataPrediction());
                trainPredictionAccuracyMeasurements.add(trainPredictionAccuracyMeasurement);

                wmataTrainPredictionsForPredictionOriginStationAndLineAndDestination.clear();
            }
        }

        // analyze MH predictions
        for (Map.Entry<String, List<TrainPrediction>> entry : this.mhTrainPredictionsByPredictionOriginStationAndLineAndDestination.entrySet()) {
            String predictionOriginStationAndlineCodeAndDestinationCode = entry.getKey();
            List<TrainPrediction> mhTrainPredictionsForPredictionOriginStationAndLineAndDestination = entry.getValue();
            if (mhTrainPredictionsForPredictionOriginStationAndLineAndDestination == null || mhTrainPredictionsForPredictionOriginStationAndLineAndDestination.isEmpty()) {
                continue;
            }

            TrainPrediction mostRecentMhTrainPredictionForPredictionOriginStationAndLineAndDestination = mhTrainPredictionsForPredictionOriginStationAndLineAndDestination.get(mhTrainPredictionsForPredictionOriginStationAndLineAndDestination.size() - 1);
            if ("BRD".equals(mostRecentMhTrainPredictionForPredictionOriginStationAndLineAndDestination.getMin())) {
                if (mhTrainPredictionsForPredictionOriginStationAndLineAndDestination.size() == 1) {
                    mhTrainPredictionsForPredictionOriginStationAndLineAndDestination.clear();
                    continue;
                }

                TrainPrediction leastRecentMhTrainPredictionForPredictionOriginStationAndLineAndDestination = mhTrainPredictionsForPredictionOriginStationAndLineAndDestination.get(0);

                double mhTotalPredictionTime = (mostRecentMhTrainPredictionForPredictionOriginStationAndLineAndDestination.getPredictionTime().getTimeInMillis() - leastRecentMhTrainPredictionForPredictionOriginStationAndLineAndDestination.getPredictionTime().getTimeInMillis()) / 1000d / 60d;

                SummaryStatistics mhLocalPredictionError = new SummaryStatistics();

                for (TrainPrediction mhTrainPredictionForPredictionOriginStationAndLineAndDestination : mhTrainPredictionsForPredictionOriginStationAndLineAndDestination) {
                    Double mhMinutesPrediction = mhTrainPredictionForPredictionOriginStationAndLineAndDestination.getMinutesFromMin();
                    double mhPredictionTimeSinceStart = (mhTrainPredictionForPredictionOriginStationAndLineAndDestination.getPredictionTime().getTimeInMillis() - leastRecentMhTrainPredictionForPredictionOriginStationAndLineAndDestination.getPredictionTime().getTime().getTime()) / 1000d / 60d;
                    double mhPredictionTimeRemaining = mhTotalPredictionTime - mhPredictionTimeSinceStart;
                    double mhPredictionError = mhPredictionTimeRemaining - mhMinutesPrediction;

                    mhLocalPredictionError.addValue(mhPredictionError);
                }

                String[] predictionOriginStationAndlineCodeAndDestinationCodeArray = predictionOriginStationAndlineCodeAndDestinationCode.split("_");
                String predictionOriginStationCode = predictionOriginStationAndlineCodeAndDestinationCodeArray[0];
                String lineCode = predictionOriginStationAndlineCodeAndDestinationCodeArray[1];
                String destinationStationCode = predictionOriginStationAndlineCodeAndDestinationCodeArray[2];

                TrainPredictionAccuracyMeasurement trainPredictionAccuracyMeasurement = new TrainPredictionAccuracyMeasurement("MetroHero", leastRecentMhTrainPredictionForPredictionOriginStationAndLineAndDestination.getPredictionTime(), mostRecentMhTrainPredictionForPredictionOriginStationAndLineAndDestination.getPredictionTime(), predictionOriginStationCode, lineCode, destinationStationCode, leastRecentMhTrainPredictionForPredictionOriginStationAndLineAndDestination.getMin(), mhLocalPredictionError.getMean(), mhLocalPredictionError.getN(), mostRecentMhTrainPredictionForPredictionOriginStationAndLineAndDestination.hasCorrespondingWmataPrediction());
                trainPredictionAccuracyMeasurements.add(trainPredictionAccuracyMeasurement);

                mhTrainPredictionsForPredictionOriginStationAndLineAndDestination.clear();
            }
        }

        this.trainPredictionAccuracyMeasurementRepository.saveAll(trainPredictionAccuracyMeasurements);
    }

//    @Scheduled(fixedDelay = 3600000)    // every hour
    private void deleteOldTrainStatuses() {
        logger.info("Deleting any old train prediction accuracy measurements...");
        this.trainPredictionAccuracyMeasurementRepository.removeOld();
        logger.info("...deleted any old train prediction accuracy measurements!");
    }

    private boolean isTrainInRevenueService(TrainPrediction trainPrediction) {
        return (validLineCodes.contains(trainPrediction.getLine()) && this.trainService.getStationCodesSet().contains(trainPrediction.getDestinationCode()));
    }

    private class WmataTrainPredictions {
        private List<TrainPrediction> Trains;

        public List<TrainPrediction> getTrains() {
            return Trains;
        }
    }

    private class TrainPrediction {
        private String Car;
        private String Destination;
        private String DestinationCode;
        private String DestinationName;
        private String Group;
        private String Line;
        private String LocationCode;
        private String LocationName;
        private String Min;

        private Calendar predictionTime;
        private String predictionOriginStationCode;
        private boolean hasCorrespondingWmataPrediction;

        public TrainPrediction(TrainStatus trainStatus, Calendar predictionTime, String predictionOriginStationCode) {
            this.Car = trainStatus.getCar();
            this.Destination = trainStatus.getDestination();
            this.DestinationCode = trainStatus.getDestinationCode();
            this.DestinationName = trainStatus.getDestinationName();
            this.Group = trainStatus.getGroup();
            this.Line = trainStatus.getLine();
            this.LocationCode = trainStatus.getLocationCode();
            this.LocationName = trainStatus.getLocationName();
            this.Min = trainStatus.getMin();

            this.predictionTime = predictionTime;
            this.predictionOriginStationCode = predictionOriginStationCode;
        }

        public String getKey() {
            return predictionOriginStationCode + "_" + Line + "_" + DestinationCode;
        }

        private Double getMinutesFromMin() {
            Double minutes = null;
            try {
                minutes = Double.parseDouble(Min);
            } catch (NumberFormatException e) {
                switch (Min) {
                    case "ARR":
                        minutes = 0.5d;
                        break;
                    case "BRD":
                        minutes = 0d;
                        break;
                }
            }
            return minutes;
        }

        public String getCar() {
            return Car;
        }

        public String getDestination() {
            return Destination;
        }

        public String getDestinationCode() {
            return DestinationCode;
        }

        public String getDestinationName() {
            return DestinationName;
        }

        public String getGroup() {
            return Group;
        }

        public String getLine() {
            return Line;
        }

        public String getLocationCode() {
            return LocationCode;
        }

        public String getLocationName() {
            return LocationName;
        }

        public String getMin() {
            return Min;
        }

        public void setPredictionTime(Calendar predictionTime) {
            this.predictionTime = predictionTime;
        }

        public Calendar getPredictionTime() {
            return predictionTime;
        }

        public String getPredictionOriginStationCode() {
            return predictionOriginStationCode;
        }

        public void setPredictionOriginStationCode(String predictionOriginStationCode) {
            this.predictionOriginStationCode = predictionOriginStationCode;
        }

        public boolean hasCorrespondingWmataPrediction() {
            return hasCorrespondingWmataPrediction;
        }

        public void setHasCorrespondingWmataPrediction(boolean hasCorrespondingWmataPrediction) {
            this.hasCorrespondingWmataPrediction = hasCorrespondingWmataPrediction;
        }
    }
}

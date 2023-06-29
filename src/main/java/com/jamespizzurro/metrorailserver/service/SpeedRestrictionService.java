package com.jamespizzurro.metrorailserver.service;

import com.google.common.collect.Lists;
import com.jamespizzurro.metrorailserver.domain.SpeedRestriction;
import com.jamespizzurro.metrorailserver.repository.SpeedRestrictionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SpeedRestrictionService {

    private static final Logger logger = LoggerFactory.getLogger(SpeedRestrictionService.class);

    @Autowired
    private SpeedRestrictionRepository speedRestrictionRepository;

    private volatile List<SpeedRestriction> speedRestrictions = new ArrayList<>();

    @Scheduled(fixedDelay = 60000) // every minute
    private void update() {
        logger.info("Updating speed restrictions from database...");
        this.speedRestrictions = Lists.newArrayList(speedRestrictionRepository.findAll());
        logger.info("...successfully updated speed restrictions from database!");
    }

    public List<SpeedRestriction> getSpeedRestrictions() {
        return speedRestrictions;
    }
}

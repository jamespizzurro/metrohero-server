package com.jamespizzurro.metrorailserver.web;

import com.jamespizzurro.metrorailserver.domain.DashboardHistoryResponse;
import com.jamespizzurro.metrorailserver.domain.PerformanceSummary;
import com.jamespizzurro.metrorailserver.service.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@EnableAutoConfiguration
public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    private final MetricsService metricsService;

    @Autowired
    public DashboardController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @RequestMapping(value = "/history/dashboard", method = RequestMethod.POST)
    public DashboardHistoryResponse getDashboardHistory(
            @RequestParam int interval,
            @RequestParam long observedDateTimestampMin,
            @RequestParam long observedDateTimestampMax
    ) {
        return metricsService.getDashboardHistory(interval, observedDateTimestampMin, observedDateTimestampMax);
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @RequestMapping(value = "/history/performance", method = RequestMethod.POST)
    public List<PerformanceSummary> getPerformanceSummary() {
        return this.metricsService.getPerformanceSummary();
    }

    @CrossOrigin(origins = "http://localhost:3000")
    @RequestMapping(value = "/history/performance/byTimeOfDay", method = RequestMethod.POST)
    public List<PerformanceSummary> getHourlyPerformanceSummary(
            @RequestParam Long fromUnixTimestamp,
            @RequestParam Long toUnixTimestamp
    ) {
        return this.metricsService.getHourlyPerformanceSummary(fromUnixTimestamp, toUnixTimestamp);
    }
}

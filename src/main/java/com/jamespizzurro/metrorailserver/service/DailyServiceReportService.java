package com.jamespizzurro.metrorailserver.service;

import com.jamespizzurro.metrorailserver.domain.DailyServiceReport;
import com.jamespizzurro.metrorailserver.repository.DailyServiceReportRepository;
import com.machinepublishers.jbrowserdriver.JBrowserDriver;
import com.machinepublishers.jbrowserdriver.Settings;
import com.machinepublishers.jbrowserdriver.Timezone;
import com.machinepublishers.jbrowserdriver.UserAgent;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DailyServiceReportService {

    private static final Logger logger = LoggerFactory.getLogger(DailyServiceReportService.class);
    private static final String DAILY_SERVICE_REPORT_URL = "https://www.wmata.com/service/daily-report/";

    private final DailyServiceReportRepository dailyServiceReportRepository;

    @Autowired
    public DailyServiceReportService(DailyServiceReportRepository dailyServiceReportRepository) {
        this.dailyServiceReportRepository = dailyServiceReportRepository;
    }

    // disabling this for now; it needs work to catch more than just the current day's reports,
    // since WMATA can go a few days without uploading DSRs, then upload a bunch of them all at once,
    // which this method doesn't support (it will only catch the last, most recent one)
//    @Scheduled(fixedDelay = 3600000)    // runs every hour
    private void fetchDailyServiceReport() {
        logger.info("Fetching and parsing today's DSR from WMATA...");

        String currentDirectoryPath = Paths.get(".").toAbsolutePath().normalize().toString() + "/JBrowserDriver_cache/";
        File cacheDirectory = new File(currentDirectoryPath);
        cacheDirectory.mkdirs();    // create the cache directory if it doesn't exist already

        JBrowserDriver driver = null;
        try {
            driver = new JBrowserDriver(
                    Settings.builder()
                            .timezone(Timezone.AMERICA_NEWYORK)
                            .userAgent(UserAgent.CHROME)
                            .cache(true)
                            .cacheDir(cacheDirectory)
                            .blockAds(true)
                            .quickRender(true)
                            .ajaxWait(10000)
                            .connectTimeout(10000)
                            .ajaxResourceTimeout(10000)
                            .connectionReqTimeout(10000)
                            .socketTimeout(10000)
                            .build()
            );
            driver.get(DAILY_SERVICE_REPORT_URL);

            if (driver.getStatusCode() != 200) {
                logger.error("Failed to load DSR page! Encountered a " + driver.getStatusCode() + " status code.");
                return;
            }

            WebElement reportTitleWebElement;
            try {
                reportTitleWebElement = driver.findElementByCssSelector("div.cs_control.CS_Element_Custom h3:first-of-type");
            } catch (Exception e) {
                logger.error("Failed to parse DSR page! Failed to select the tile with the report's date in it.", e);
                return;
            }

            Calendar reportDate = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d, y");
            String dateString = reportTitleWebElement.getText().trim().replaceAll("Daily Service Report for ", "");
            reportDate.setTime(sdf.parse(dateString));

            if (this.dailyServiceReportRepository.existsById(reportDate)) {
                // we've already successfully parsed today's DSR
                logger.info("...today's daily service report has already been fetched and parsed! Skipped.");
                return;
            }

            List<WebElement> rushHourPromiseWebElements;
            try {
                rushHourPromiseWebElements = driver.findElementsByCssSelector("div.cs_control.CS_Element_Custom p:first-of-type span[style*='font-size: 150%']:not(:first-child)");
                if (rushHourPromiseWebElements.size() <= 0) {
                    try {
                        rushHourPromiseWebElements = driver.findElementsByCssSelector("div.cs_control.CS_Element_Custom p:first-of-type font:not(:first-child)");
                    } catch (Exception e) {
                        logger.error("Failed to parse DSR page! Failed to select web elements in 'Rush Hour Promise' section.", e);
                        return;
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to parse DSR page! Failed to select web elements in 'Rush Hour Promise' section.", e);
                return;
            }
            if (rushHourPromiseWebElements.size() != 3) {
                logger.error("Failed to parse DSR page! Expected 3 web elements to be selected using 'Rush Hour Promise' section selector, found " + rushHourPromiseWebElements.size() + ".");
                return;
            }

            Double percentCustomersOnTime = null;
            Double percentCustomersWithinFiveMinutesExpectedTime = null;
            Integer numCustomersRefunded = null;

            for (int i = 0; i < rushHourPromiseWebElements.size(); i++) {
                WebElement rushHourPromiseWebElement = rushHourPromiseWebElements.get(i);
                String valueString = rushHourPromiseWebElement.getText().trim();

                switch (i) {
                    case 0:
                        try {
                            percentCustomersOnTime = Double.valueOf(valueString.replaceAll("%", "")) / 100;
                        } catch (NumberFormatException e) {
                            logger.error("Failed to parse out percent of customers on time from DSR page!", e);
                        }
                        break;
                    case 1:
                        try {
                            percentCustomersWithinFiveMinutesExpectedTime = Double.valueOf(valueString.replaceAll("%", "")) / 100;
                        } catch (NumberFormatException e) {
                            logger.error("Failed to parse out percent of customers within five minutes of expected time from DSR page!", e);
                        }
                        break;
                    case 2:
                        try {
                            numCustomersRefunded = Integer.parseInt(valueString.replaceAll(",", ""));
                        } catch (NumberFormatException e) {
                            logger.error("Failed to parse out number of customers refunded from DSR page!", e);
                        }
                        break;
                }
            }

            Double percentCustomersRefunded = null;
            String rushHourPromiseText = driver.findElementByCssSelector("div.cs_control.CS_Element_Custom p:first-of-type").getText().trim();
            Matcher percentCustomersRefundedStringMatcher = Pattern.compile("\\(or (.*)% of customers\\)").matcher(rushHourPromiseText);
            while (percentCustomersRefundedStringMatcher.find()) {
                String percentCustomersRefundedString = percentCustomersRefundedStringMatcher.group(1).trim();
                percentCustomersRefunded = Double.parseDouble(percentCustomersRefundedString) / 100;
                break;
            }

            DailyServiceReport dailyServiceReport = new DailyServiceReport(reportDate, percentCustomersOnTime, percentCustomersWithinFiveMinutesExpectedTime, numCustomersRefunded, percentCustomersRefunded);
            this.dailyServiceReportRepository.save(dailyServiceReport);

            logger.info("...successfully fetched and parsed today's DSR!");
        } catch (Exception e) {
            logger.error("Failed to parse DSR page!", e);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }
}

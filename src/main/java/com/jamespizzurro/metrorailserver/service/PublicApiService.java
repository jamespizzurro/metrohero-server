package com.jamespizzurro.metrorailserver.service;

import com.jamespizzurro.metrorailserver.domain.ApiRequest;
import com.jamespizzurro.metrorailserver.repository.ApiRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PublicApiService {

    private static final Logger logger = LoggerFactory.getLogger(PublicApiService.class);

    private final ApiRequestRepository apiRequestRepository;

    private Map<String, Integer> numSecondlyApiRequestsByApiKey;
    private Map<String, Integer> numDailyApiRequestsByApiKey;
    private volatile LocalDateTime previousNumDailyApiRequestRateLimitResetStartOfDay;

    @Autowired
    public PublicApiService(ApiRequestRepository apiRequestRepository) {
        this.apiRequestRepository = apiRequestRepository;
    }

    @PostConstruct
    private void init() {
        logger.info("Initializing public API service...");

        this.numDailyApiRequestsByApiKey = new ConcurrentHashMap<>();
        this.numSecondlyApiRequestsByApiKey = new ConcurrentHashMap<>();
        this.previousNumDailyApiRequestRateLimitResetStartOfDay = null;

        logger.info("...initialized public API service!");
    }

    @Scheduled(fixedDelay = 3600000)  // every hour
    protected void deleteOldApiRequests() {
        logger.info("Deleting any old API requests...");

        this.apiRequestRepository.removeOld();

        logger.info("...deleted any old API requests!");
    }

    public void logApiRequest(String apiKey, HttpServletRequest request, Calendar requestProcessedTime) {
        this.apiRequestRepository.save(new ApiRequest(apiKey, getRemoteAddress(request), request.getRequestURI(), getRequestParameterMap(request), requestProcessedTime));
    }

    /**
     * Gets the remote address from a HttpServletRequest object. It prefers the
     * `X-Forwarded-For` header, as this is the recommended way to do it (user
     * may be behind one or more proxies).
     *
     * Taken from https://stackoverflow.com/a/38468051/778272
     *
     * @param request - the request object where to get the remote address from
     * @return a string corresponding to the IP address of the remote machine
     */
    private static String getRemoteAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-FORWARDED-FOR");
        if (ipAddress != null) {
            // cares only about the first IP if there is a list
            ipAddress = ipAddress.replaceFirst(",.*", "");
        } else {
            ipAddress = request.getRemoteAddr();
        }
        return ipAddress;
    }

    private static Map<String, String> getRequestParameterMap(HttpServletRequest request) {
        Map<String, String> parameterMap = new HashMap<>();

        try {
            for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
                String key = entry.getKey();
                String[] values = entry.getValue();

                if (key != null) {
                    String value = (values != null && values.length > 0) ? !StringUtils.isEmpty(values[values.length - 1]) ? values[values.length - 1] : "true" : "true";
                    parameterMap.put(key, value);
                }
            }
        } catch (ConcurrentModificationException ignored) {
            // it's unclear to me why this type of exception is sometimes being thrown,
            // but when it happens, just ignore it
        }

        return parameterMap;
    }

    public Map<String, Integer> getNumDailyApiRequestsByApiKey() {
        return numDailyApiRequestsByApiKey;
    }

    public Map<String, Integer> getNumSecondlyApiRequestsByApiKey() {
        return numSecondlyApiRequestsByApiKey;
    }

    public LocalDateTime getPreviousNumDailyApiRequestRateLimitResetStartOfDay() {
        return previousNumDailyApiRequestRateLimitResetStartOfDay;
    }

    public void setPreviousNumDailyApiRequestRateLimitResetStartOfDay(LocalDateTime previousNumDailyApiRequestRateLimitResetStartOfDay) {
        this.previousNumDailyApiRequestRateLimitResetStartOfDay = previousNumDailyApiRequestRateLimitResetStartOfDay;
    }
}

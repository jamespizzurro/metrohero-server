package com.jamespizzurro.metrorailserver.web;

import com.jamespizzurro.metrorailserver.domain.ApiUser;
import com.jamespizzurro.metrorailserver.repository.ApiUserRepository;
import com.jamespizzurro.metrorailserver.service.PublicApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Calendar;

@Component
public class PublicApiFilter extends GenericFilterBean {

    private static final Logger logger = LoggerFactory.getLogger(PublicApiFilter.class);

    private final PublicApiService publicApiService;
    private final ApiUserRepository apiUserRepository;

    @Autowired
    public PublicApiFilter(PublicApiService publicApiService, ApiUserRepository apiUserRepository) {
        this.publicApiService = publicApiService;
        this.apiUserRepository = apiUserRepository;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        if ("OPTIONS".equals(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String apiKey = httpRequest.getHeader("apiKey");
        if (StringUtils.isEmpty(apiKey)) {
            apiKey = httpRequest.getHeader("apikey");
            if (StringUtils.isEmpty(apiKey)) {
                ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "No API key specified! Please specify the unique API key we assigned to you. If you don't have one yet, please email us at contact@dcmetrohero.com.");
                return;
            }
        }

        ApiUser apiUser = this.apiUserRepository.findById(apiKey).orElse(null);;
        if (apiUser == null || apiUser.isBlocked()) {
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "The API key you specified [" + apiKey + "] is invalid! Please verify it is the unique API key we assigned to you, then try again. If you don't have an API key yet, or if you require further assistance, please email us at contact@dcmetrohero.com.");
            return;
        }

        Integer maxCallsPerSecond = apiUser.getMaxCallsPerSecond();
        if (maxCallsPerSecond != null && this.publicApiService.getNumSecondlyApiRequestsByApiKey().getOrDefault(apiKey, 0) >= maxCallsPerSecond) {
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Secondly rate limit exceeded! Your API key [" + apiKey + "] has been used to make at least " + maxCallsPerSecond + " calls to our APIs this second. Secondly rate limits reset at the start of every second. Please try again shortly, or email us at contact@dcmetrohero.com for assistance.");
            return;
        }

        Integer maxCallsPerDay = apiUser.getMaxCallsPerDay();
        if (maxCallsPerDay != null && this.publicApiService.getNumDailyApiRequestsByApiKey().getOrDefault(apiKey, 0) >= maxCallsPerDay) {
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Daily rate limit exceeded! Your API key [" + apiKey + "] has been used to make at least " + maxCallsPerDay + " calls to our APIs today. Daily rate limits reset at midnight EST/EDT. Please try again tomorrow, or email us at contact@dcmetrohero.com for assistance.");
            return;
        }

        this.publicApiService.getNumSecondlyApiRequestsByApiKey().put(apiKey, this.publicApiService.getNumSecondlyApiRequestsByApiKey().getOrDefault(apiKey, 0) + 1);
        this.publicApiService.getNumDailyApiRequestsByApiKey().put(apiKey, this.publicApiService.getNumDailyApiRequestsByApiKey().getOrDefault(apiKey, 0) + 1);

        chain.doFilter(request, response);

        this.publicApiService.logApiRequest(apiKey, httpRequest, Calendar.getInstance());
    }
}

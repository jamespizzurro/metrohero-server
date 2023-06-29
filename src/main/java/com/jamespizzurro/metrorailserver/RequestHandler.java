package com.jamespizzurro.metrorailserver;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

public class RequestHandler {

    private final RestTemplate restTemplate;

    public RequestHandler() {
        this.restTemplate = new RestTemplate(clientHttpRequestFactory());
    }

    private ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(5));
        factory.setReadTimeout((int) TimeUnit.SECONDS.toMillis(5));
        return factory;
    }

    public RestTemplate getRestTemplate() {
        return this.restTemplate;
    }
}

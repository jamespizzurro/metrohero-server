package com.jamespizzurro.metrorailserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

public class NetworkUtil {

    private static final Logger logger = LoggerFactory.getLogger(NetworkUtil.class);

    public static HttpEntity<String> createNewHttpEntity(String apiKey) {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setAccept(Collections.singletonList(new MediaType("application", "json")));
        requestHeaders.set("api_key", apiKey);
        requestHeaders.set(HttpHeaders.ACCEPT_ENCODING, "gzip");
        return new HttpEntity<>("parameters", requestHeaders);
    }

    public static RestTemplate createNewRestTemplate(boolean useGzip) {
        RestTemplate restTemplate = (new RequestHandler()).getRestTemplate();
        restTemplate.getMessageConverters().clear();
        if (useGzip) {
            restTemplate.getMessageConverters().add(new GzipGsonHttpMessageConverter());
        } else {
            restTemplate.getMessageConverters().add(new GsonHttpMessageConverter());
        }
        return restTemplate;
    }
}

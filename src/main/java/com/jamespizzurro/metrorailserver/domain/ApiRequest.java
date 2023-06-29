package com.jamespizzurro.metrorailserver.domain;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Calendar;
import java.util.Map;

@Entity
@Table(name = "api_request",
        indexes = {
                @Index(name = "api_request_api_key_index", columnList = "apiKey"),
                @Index(name = "api_request_date_index", columnList = "date")
        }
)
public class ApiRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false,
            columnDefinition = "bigserial not null")
    private Long id;

    @Column(nullable = false)
    private String apiKey;

    private String originatingIpAddress;

    @Column()
    private String endpoint;

    @Type(type = "com.jamespizzurro.metrorailserver.JsonType")
    private Map<String, String> params;

    @Column(nullable = false)
    private Calendar date;

    public ApiRequest() {
    }

    public ApiRequest(String apiKey, String originatingIpAddress, String endpoint, Map<String, String> params, Calendar date) {
        this.apiKey = apiKey;
        this.originatingIpAddress = originatingIpAddress;
        this.endpoint = endpoint;
        this.params = params;
        this.date = date;
    }
}

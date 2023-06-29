package com.jamespizzurro.metrorailserver.domain;

import javax.persistence.*;
import java.util.Calendar;

@Entity
@Table(name = "api_user")
public class ApiUser {

    @Id
    private String apiKey;

    private String name;

    private String emailAddress;

    private Calendar activationDate;

    @Column(nullable = false)
    private Boolean isBlocked;

    private Integer maxCallsPerSecond;

    private Integer maxCallsPerDay;

    public ApiUser() {
    }

    @PrePersist
    void preInsert() {
        this.activationDate = Calendar.getInstance();
        this.isBlocked = false;
        this.maxCallsPerSecond = 10;
        this.maxCallsPerDay = 50000;
    }

    public Boolean isBlocked() {
        return isBlocked;
    }

    public Integer getMaxCallsPerSecond() {
        return maxCallsPerSecond;
    }

    public Integer getMaxCallsPerDay() {
        return maxCallsPerDay;
    }
}

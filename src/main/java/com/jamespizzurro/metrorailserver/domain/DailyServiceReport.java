package com.jamespizzurro.metrorailserver.domain;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Calendar;

@Entity
public class DailyServiceReport {

    @Id
    private Calendar date;

    private Double percentCustomersOnTime;
    private Double percentCustomersWithinFiveMinutesExpectedTime;
    private Integer numCustomersRefunded;
    private Double percentCustomersRefunded;

    public DailyServiceReport() {
    }

    public DailyServiceReport(Calendar date, Double percentCustomersOnTime, Double percentCustomersWithinFiveMinutesExpectedTime, Integer numCustomersRefunded, Double percentCustomersRefunded) {
        this.date = date;
        this.percentCustomersOnTime = percentCustomersOnTime;
        this.percentCustomersWithinFiveMinutesExpectedTime = percentCustomersWithinFiveMinutesExpectedTime;
        this.numCustomersRefunded = numCustomersRefunded;
        this.percentCustomersRefunded = percentCustomersRefunded;
    }

    public Calendar getDate() {
        return date;
    }

    public void setDate(Calendar date) {
        this.date = date;
    }

    public Double getPercentCustomersOnTime() {
        return percentCustomersOnTime;
    }

    public void setPercentCustomersOnTime(Double percentCustomersOnTime) {
        this.percentCustomersOnTime = percentCustomersOnTime;
    }

    public Double getPercentCustomersWithinFiveMinutesExpectedTime() {
        return percentCustomersWithinFiveMinutesExpectedTime;
    }

    public void setPercentCustomersWithinFiveMinutesExpectedTime(Double percentCustomersWithinFiveMinutesExpectedTime) {
        this.percentCustomersWithinFiveMinutesExpectedTime = percentCustomersWithinFiveMinutesExpectedTime;
    }

    public Integer getNumCustomersRefunded() {
        return numCustomersRefunded;
    }

    public void setNumCustomersRefunded(Integer numCustomersRefunded) {
        this.numCustomersRefunded = numCustomersRefunded;
    }

    public Double getPercentCustomersRefunded() {
        return percentCustomersRefunded;
    }

    public void setPercentCustomersRefunded(Double percentCustomersRefunded) {
        this.percentCustomersRefunded = percentCustomersRefunded;
    }
}

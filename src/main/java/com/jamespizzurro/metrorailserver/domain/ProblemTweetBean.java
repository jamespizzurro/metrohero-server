package com.jamespizzurro.metrorailserver.domain;

import java.util.Objects;

public class ProblemTweetBean {

    private Long twitterId;
    private String twitterIdString;
    private Long userId;
    private Long timestamp;
    private String text;

    public ProblemTweetBean(Long twitterId, Long userId, Integer timestamp, String text) {
        this.twitterId = twitterId;
        this.twitterIdString = String.valueOf(twitterId);
        this.userId = userId;
        this.timestamp = Long.valueOf(timestamp);
        this.text = String.valueOf(text);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProblemTweetBean that = (ProblemTweetBean) o;
        return Objects.equals(twitterId, that.twitterId) &&
                Objects.equals(twitterIdString, that.twitterIdString) &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(twitterId, twitterIdString, userId, timestamp, text);
    }

    public Long getTwitterId() {
        return twitterId;
    }

    public void setTwitterId(Long twitterId) {
        this.twitterId = twitterId;
    }

    public String getTwitterIdString() {
        return twitterIdString;
    }

    public void setTwitterIdString(String twitterIdString) {
        this.twitterIdString = twitterIdString;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}

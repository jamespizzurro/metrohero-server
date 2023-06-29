package com.jamespizzurro.metrorailserver.domain;

import com.jamespizzurro.metrorailserver.Exclude;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Calendar;
import java.util.Set;

@Entity
@Table(name = "station_problem_tweet",
        indexes = {
                @Index(name = "station_problem_tweet_twitter_id_index", columnList = "twitter_id"),
                @Index(name = "station_problem_tweet_date_index", columnList = "date")
        }
)
public class StationProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false,
            columnDefinition = "bigserial not null")
    @Exclude
    private Long id;

    @Column(name = "twitter_id", nullable = false)
    private Long twitterId;

    @Column(name = "text", columnDefinition = "text", nullable = false)
    private String text;

    @Column(name = "date", nullable = false)
    private Calendar date;

    @Column(name = "line_codes")
    @Type(type = "com.jamespizzurro.metrorailserver.StringArrayType")
    private String[] lineCodes;

    @Column(name = "station_codes", nullable = false)
    @Type(type = "com.jamespizzurro.metrorailserver.StringArrayType")
    private String[] stationCodes;

    @Column(name = "keywords")
    @Type(type = "com.jamespizzurro.metrorailserver.StringArrayType")
    private String[] keywords;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_name")
    private String userName;

    @Transient
    private String twitterIdString;

    @Transient
    private String url;

    public StationProblem() {

    }

    public StationProblem(Long twitterId, String text, Calendar date, Set<String> lineCodes, Set<String> stationCodes, Set<String> keywords, Long userId, String userName) {
        this.twitterId = twitterId;
        this.text = text;
        this.date = date;
        this.lineCodes = lineCodes.toArray(new String[0]);
        this.stationCodes = stationCodes.toArray(new String[0]);
        this.keywords = keywords.toArray(new String[0]);
        this.userId = userId;
        this.userName = userName;

        this.twitterIdString = String.valueOf(twitterId);
        this.url = "https://twitter.com/" + this.userId + "/status/" + this.twitterId;
    }

    public Long getTwitterId() {
        return twitterId;
    }

    public void setTwitterId(Long twitterId) {
        this.twitterId = twitterId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Calendar getDate() {
        return date;
    }

    public void setDate(Calendar date) {
        this.date = date;
    }

    public String[] getLineCodes() {
        return lineCodes;
    }

    public void setLineCodes(String[] lineCodes) {
        this.lineCodes = lineCodes;
    }

    public String[] getStationCodes() {
        return stationCodes;
    }

    public void setStationCodes(String[] stationCodes) {
        this.stationCodes = stationCodes;
    }

    public String[] getKeywords() {
        return keywords;
    }

    public void setKeywords(String[] keywords) {
        this.keywords = keywords;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getTwitterIdString() {
        return twitterIdString;
    }

    public void setTwitterIdString(String twitterIdString) {
        this.twitterIdString = twitterIdString;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}

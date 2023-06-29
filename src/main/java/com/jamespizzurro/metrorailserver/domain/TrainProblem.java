package com.jamespizzurro.metrorailserver.domain;

import com.jamespizzurro.metrorailserver.Exclude;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Calendar;
import java.util.Set;

@Entity
@Table(name = "train_problem_tweet",
        indexes = {
                @Index(name = "train_problem_tweet_twitter_id_index", columnList = "twitter_id"),
                @Index(name = "train_problem_tweet_date_index", columnList = "date")
        }
)
public class TrainProblem {

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

    @Column(name = "train_id", nullable = false)
    private String trainId;

    @Column(name = "real_train_id", nullable = false)
    private String realTrainId;

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

    public TrainProblem() {

    }

    public TrainProblem(Long twitterId, String text, Calendar date, Set<String> lineCodes, String trainId, String realTrainId, Set<String> keywords, Long userId, String userName) {
        this.twitterId = twitterId;
        this.text = text;
        this.date = date;
        this.lineCodes = lineCodes.toArray(new String[0]);
        this.trainId = trainId;
        this.realTrainId = realTrainId;
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

    public String getTrainId() {
        return trainId;
    }

    public void setTrainId(String trainId) {
        this.trainId = trainId;
    }

    public String getRealTrainId() {
        return realTrainId;
    }

    public void setRealTrainId(String realTrainId) {
        this.realTrainId = realTrainId;
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
